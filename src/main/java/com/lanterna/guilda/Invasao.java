package com.lanterna.guilda;

import com.lanterna.LanternaConfig;
import com.lanterna.LanternaMod;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.space.SpaceBodies;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Invasão de um planeta conquistado (estilo raid).
 *
 * Quem é de outra guilda entra no território (raio em volta da bandeira) com o escudo desligado: 10 s de aviso e a
 * invasão começa. As tropas de defesa saem da praça da bandeira; as tropas de ataque guardadas pela guilda invasora
 * chegam junto do invasor; todos os membros da guilda invasora que estiverem no planeta entram na briga.
 * Vitória do invasor: derrotar todas as tropas de defesa e derrubar a bandeira (ela fica LIBERADA quando a defesa cai).
 * Vitória da defesa: o tempo acaba, ou não sobra invasor (nem tropa de ataque) no território por 30 s.
 * Funciona com a guilda dona offline. Depois, ganhe quem ganhar, o planeta fica com escudo por algumas horas.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Invasao {
    public static final String TAG_PAPEL = "lanterna_guilda_papel";
    public static final String TAG_ALVO = "lanterna_guilda_alvo";
    public static final String TAG_PLANETA = "lanterna_guilda_planeta";
    private static final int AVISO = 10;

    private static final Map<String, Invasao> ATIVAS = new HashMap<>();
    /** Jogador -> (planeta, tick em que entrou no território). */
    private static final Map<UUID, Map.Entry<String, Long>> CHEGANDO = new HashMap<>();

    final Conquista c;
    final ServerLevel level;
    final UUID defensora, atacante;
    final long fim;
    final Set<UUID> defensores = new HashSet<>();
    final Set<UUID> tropas = new HashSet<>();
    final Set<UUID> jogadores = new HashSet<>();
    final int totalDefesa;
    final ServerBossEvent barra;
    int semAtacante;
    boolean liberada;

    private Invasao(Conquista c, ServerLevel level, UUID atacante, int totalDefesa) {
        this.c = c;
        this.level = level;
        this.defensora = c.guilda;
        this.atacante = atacante;
        this.totalDefesa = totalDefesa;
        this.fim = level.getGameTime() + LanternaConfig.GUILDA_MIN_INVASAO.get() * 60L * 20L;
        this.barra = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    }

    @Nullable
    public static Invasao ativa(String planeta) {
        return ATIVAS.get(planeta);
    }

    public String debug() {
        StringBuilder b = new StringBuilder(defensores.size() + " [");
        for (UUID u : defensores) { Entity e = level.getEntity(u); b.append(e == null ? "null" : e.isAlive() + "@" + e.blockPosition().toShortString() + "/" + e.level().dimension().location()).append(' '); }
        return b + "] liberada=" + liberada;
    }

    public Set<UUID> defensoresIds() {
        return Collections.unmodifiableSet(defensores);
    }

    public boolean participa(Player p) {
        return Guildas.membro(atacante, p);
    }

    // ===================== Começo =====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 20 != 3) return;
        if (p.isSpectator() || p.isDeadOrDying()) return;
        Conquista c = Conquistas.territorio(p.level(), p.blockPosition());
        if (c == null) {
            CHEGANDO.remove(p.getUUID());
            return;
        }
        Guildas gs = Guildas.get(p.server);
        Invasao inv = ATIVAS.get(c.planeta);
        if (inv != null) {
            if (inv.participa(p)) inv.jogadores.add(p.getUUID());
            if (inv.participa(p) || Guildas.membro(c.guilda, p)) inv.barra.addPlayer(p);
            return;
        }
        if (Guildas.membro(c.guilda, p) || p.isCreative()) return;
        long agora = System.currentTimeMillis();
        Guilda dona = gs.guildas.get(c.guilda);
        if (c.protegido(agora)) {
            if (p.tickCount % 100 == 3) p.displayClientMessage(Component.translatable("guilda.lanterna.protegido",
                    dona == null ? "?" : dona.nome, tempo(c.escudoAte - agora)).withStyle(ChatFormatting.AQUA), true);
            return;
        }
        Guilda minha = gs.daJogador(p.getUUID());
        if (minha == null) {
            if (p.tickCount % 100 == 3) p.displayClientMessage(Component.translatable("guilda.lanterna.precisa_guilda").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        if (gs.planetasDe(minha.id).size() >= LanternaConfig.GUILDA_MAX_PLANETAS.get()) {
            if (p.tickCount % 100 == 3) p.displayClientMessage(Component.translatable("guilda.lanterna.erro.max_planetas",
                    LanternaConfig.GUILDA_MAX_PLANETAS.get()).withStyle(ChatFormatting.GOLD), true);
            return;
        }
        long t = p.level().getGameTime();
        var e = CHEGANDO.get(p.getUUID());
        if (e == null || !e.getKey().equals(c.planeta)) {
            CHEGANDO.put(p.getUUID(), Map.entry(c.planeta, t));
            e = CHEGANDO.get(p.getUUID());
        }
        int falta = AVISO - (int) ((t - e.getValue()) / 20);
        if (falta > 0) {
            p.displayClientMessage(Component.translatable("guilda.lanterna.aviso_invasao", dona == null ? "?" : dona.nome, falta)
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            return;
        }
        CHEGANDO.remove(p.getUUID());
        comecar(p, c, minha);
    }

    public static void comecar(ServerPlayer p, Conquista c, Guilda minha) {
        ServerLevel level = p.serverLevel();
        Guildas gs = Guildas.get(p.server);
        Guilda dona = gs.guildas.get(c.guilda);
        if (dona == null) return;
        level.getChunk(c.bandeira.getX() >> 4, c.bandeira.getZ() >> 4);
        int nDef = Math.min(c.defesa, LanternaConfig.GUILDA_MAX_DEFESA.get());
        Invasao inv = new Invasao(c, level, minha.id, nDef);
        ATIVAS.put(c.planeta, inv);
        // Defesa sai da praça da bandeira
        for (int i = 0; i < nDef; i++) {
            double a = i * Math.PI * 2 / Math.max(1, nDef);
            BlockPos q = c.bandeira.offset((int) Math.round(Math.cos(a) * 4), 0, (int) Math.round(Math.sin(a) * 4));
            LanternaEntity e = tropa(level, dona, q, "defesa", null, c.planeta, i % 4 == 0, inv.defensores);
            e.setTarget(p);
        }
        // Tropas de ataque guardadas chegam junto do invasor
        int nAtk = Math.min(minha.ataque, 20);
        minha.ataque -= nAtk;
        for (int i = 0; i < nAtk; i++) {
            double a = i * Math.PI * 2 / Math.max(1, nAtk);
            BlockPos q = BlockPos.containing(p.getX() + Math.cos(a) * 3, p.getY(), p.getZ() + Math.sin(a) * 3);
            tropa(level, minha, q, "ataque", dona.id, c.planeta, i % 4 == 0, inv.tropas);
        }
        inv.jogadores.add(p.getUUID());
        inv.barra.addPlayer(p);
        if (nDef == 0) inv.liberar();
        inv.atualizarBarra();
        gs.mudou();
        SpaceBodies.Body b = SpaceBodies.byId(c.planeta);
        Component nome = b == null ? Component.literal(c.planeta) : b.displayName();
        Conquistas.anunciar(p.server, Component.translatable("guilda.lanterna.invasao_comecou", minha.nome, nome, dona.nome, nDef, nAtk)
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        level.playSound(null, c.bandeira, SoundEvents.RAID_HORN.get(), SoundSource.HOSTILE, 64f, 1f);
        level.playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.get(), SoundSource.HOSTILE, 4f, 1f);
    }

    /** Um Lanterna da guilda (cor da Tropa dela) para defender ou atacar. */
    static LanternaEntity tropa(ServerLevel level, Guilda g, BlockPos at, String papel, @Nullable UUID alvo, String planeta, boolean oficial, Set<UUID> lista) {
        LanternaEntity e = LanternaEntity.lanterna(level, BaseGuilda.corps(g), oficial);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        e.moveTo(at.getX() + 0.5, Math.max(y, at.getY()), at.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        e.getPersistentData().putString(Conquistas.TAG_GUILDA, g.id.toString());
        e.getPersistentData().putString(TAG_PAPEL, papel);
        e.getPersistentData().putString(TAG_PLANETA, planeta);
        if (alvo != null) e.getPersistentData().putString(TAG_ALVO, alvo.toString());
        e.setCustomName(Component.literal("[" + g.nome + "] ").withStyle(s -> s.withColor(g.cor()))
                .append(Component.translatable(papel.equals("defesa") ? "guilda.lanterna.defensor" : "guilda.lanterna.atacante").withStyle(ChatFormatting.WHITE)));
        lista.add(e.getUUID()); // antes de entrar no mundo (senão onJoin acha que é sobra)
        level.addFreshEntity(e);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, e.getX(), e.getY() + 1, e.getZ(), 12, 0.3, 0.8, 0.3, 0.05);
        return e;
    }

    // ===================== Andamento =====================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ATIVAS.isEmpty() || event.getServer().getTickCount() % 20 != 11) return;
        for (Invasao inv : new ArrayList<>(ATIVAS.values())) inv.tick(event.getServer());
    }

    private void tick(MinecraftServer server) {
        // Quem ainda está de pé
        defensores.removeIf(u -> { Entity e = level.getEntity(u); return e != null && !e.isAlive(); });
        tropas.removeIf(u -> { Entity e = level.getEntity(u); return e != null && !e.isAlive(); });
        if (defensores.isEmpty() && !liberada) liberar();

        // Invasores presentes (jogadores no território ou tropas de ataque vivas)
        int r = LanternaConfig.GUILDA_RAIO.get() + 32;
        List<LivingEntity> invasores = new ArrayList<>();
        for (UUID u : jogadores) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null && p.isAlive() && p.level() == level && p.blockPosition().distSqr(c.bandeira) < (double) r * r) invasores.add(p);
        }
        for (UUID u : tropas) if (level.getEntity(u) instanceof LivingEntity le && le.isAlive()) invasores.add(le);
        semAtacante = invasores.isEmpty() ? semAtacante + 1 : 0;

        // Defesa vai atrás dos invasores; ataque vai atrás da defesa
        for (UUID u : defensores) if (level.getEntity(u) instanceof LanternaEntity d && (d.getTarget() == null || !d.getTarget().isAlive())) {
            LivingEntity alvo = maisPerto(d, invasores);
            if (alvo != null) d.setTarget(alvo);
        }
        List<LivingEntity> defs = new ArrayList<>();
        for (UUID u : defensores) if (level.getEntity(u) instanceof LivingEntity le) defs.add(le);
        for (UUID u : tropas) if (level.getEntity(u) instanceof LanternaEntity t && (t.getTarget() == null || !t.getTarget().isAlive())) {
            LivingEntity alvo = maisPerto(t, defs);
            if (alvo != null) t.setTarget(alvo);
            else if (t.distanceToSqr(c.bandeira.getCenter()) > 64) t.getNavigation().moveTo(c.bandeira.getX() + 0.5, c.bandeira.getY(), c.bandeira.getZ() + 0.5, 1.0);
        }
        // Barra
        for (ServerPlayer p : new ArrayList<>(barra.getPlayers()))
            if (p.level() != level || p.blockPosition().distSqr(c.bandeira) > (double) (r + 64) * (r + 64)) barra.removePlayer(p);
        atualizarBarra();

        if (level.getGameTime() >= fim) defesaVence(server, "guilda.lanterna.fim_tempo");
        else if (semAtacante >= 30) defesaVence(server, "guilda.lanterna.fim_recuo");
    }

    @Nullable
    private static LivingEntity maisPerto(Entity de, List<LivingEntity> l) {
        LivingEntity best = null;
        double bd = 96 * 96;
        for (LivingEntity e : l) {
            double d = e.distanceToSqr(de);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    private void liberar() {
        liberada = true;
        BlockState st = level.getBlockState(c.bandeira);
        if (st.is(GuildaItems.BANDEIRA.get())) level.setBlock(c.bandeira, st.setValue(BandeiraBlock.LIBERADA, true), 3);
        for (UUID u : jogadores) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(u);
            if (p != null) p.sendSystemMessage(Component.translatable("guilda.lanterna.defesa_caiu").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
        level.playSound(null, c.bandeira, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3f, 1.4f);
    }

    private void atualizarBarra() {
        Guildas gs = Guildas.get(level.getServer());
        Guilda a = gs.guildas.get(atacante), d = gs.guildas.get(defensora);
        long seg = Math.max(0, (fim - level.getGameTime()) / 20);
        String t = String.format("%d:%02d", seg / 60, seg % 60);
        if (liberada) {
            barra.setName(Component.translatable("guilda.lanterna.barra_bandeira", a == null ? "?" : a.nome, t));
            barra.setColor(BossEvent.BossBarColor.YELLOW);
            barra.setProgress(1f);
        } else {
            barra.setName(Component.translatable("guilda.lanterna.barra", a == null ? "?" : a.nome, d == null ? "?" : d.nome,
                    defensores.size(), totalDefesa, tropas.size(), t));
            barra.setProgress(totalDefesa == 0 ? 0f : Math.min(1f, defensores.size() / (float) totalDefesa));
        }
    }

    // ===================== Fim =====================

    /** Um invasor derrubou a bandeira liberada: o planeta é da guilda dele. */
    void bandeiraCaiu(ServerPlayer p) {
        if (!liberada) return;
        MinecraftServer server = p.server;
        Guildas gs = Guildas.get(server);
        Guilda nova = gs.guildas.get(atacante);
        if (nova == null) return;
        devolverTropas(gs);
        terminar();
        level.playSound(null, c.bandeira, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 4f, 1f);
        Conquistas.transferir(server, c, nova);
    }

    private void defesaVence(MinecraftServer server, String motivo) {
        Guildas gs = Guildas.get(server);
        int vivos = 0;
        for (UUID u : defensores) {
            Entity e = level.getEntity(u);
            if (e == null || e.isAlive()) vivos++;
            if (e != null) e.discard();
        }
        c.defesa = vivos;
        devolverTropas(gs);
        c.escudoAte = System.currentTimeMillis() + (long) (LanternaConfig.GUILDA_ESCUDO_INVASAO.get() * 3_600_000L);
        BlockState st = level.getBlockState(c.bandeira);
        if (st.is(GuildaItems.BANDEIRA.get())) level.setBlock(c.bandeira, st.setValue(BandeiraBlock.LIBERADA, false), 3);
        terminar();
        Guilda d = gs.guildas.get(defensora);
        SpaceBodies.Body b = SpaceBodies.byId(c.planeta);
        Conquistas.anunciar(server, Component.translatable("guilda.lanterna.defendeu", d == null ? "?" : d.nome,
                b == null ? Component.literal(c.planeta) : b.displayName(), Component.translatable(motivo), vivos).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        gs.mudou();
    }

    /** Tropas de ataque que sobreviveram voltam para a guilda invasora. */
    private void devolverTropas(Guildas gs) {
        Guilda a = gs.guildas.get(atacante);
        int vivas = 0;
        for (UUID u : tropas) {
            Entity e = level.getEntity(u);
            if (e != null && e.isAlive()) vivas++;
            if (e != null) e.discard();
        }
        if (a != null) a.ataque = Math.min(LanternaConfig.GUILDA_MAX_ATAQUE.get(), a.ataque + vivas);
        tropas.clear();
    }

    private void terminar() {
        barra.removeAllPlayers();
        ATIVAS.remove(c.planeta);
        Guildas.get(level.getServer()).mudou();
    }

    // ===================== Mortes e sobras =====================

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        String planeta = event.getEntity().getPersistentData().getString(TAG_PLANETA);
        if (planeta.isEmpty()) return;
        Invasao inv = ATIVAS.get(planeta);
        if (inv == null) return;
        inv.defensores.remove(event.getEntity().getUUID());
        inv.tropas.remove(event.getEntity().getUUID());
    }

    /** Tropas de invasão que sobraram de antes (servidor reiniciou no meio): somem. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof LanternaEntity e)) return;
        String planeta = e.getPersistentData().getString(TAG_PLANETA);
        if (planeta.isEmpty()) return;
        Invasao inv = ATIVAS.get(planeta);
        if (inv == null || !(inv.defensores.contains(e.getUUID()) || inv.tropas.contains(e.getUUID()))) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent event) {
        // invasão interrompida: a defesa fica como estava (ninguém perde nada) e ganha escudo
        Guildas gs = Guildas.get(event.getServer());
        for (Invasao inv : new ArrayList<>(ATIVAS.values())) {
            Guilda a = gs.guildas.get(inv.atacante);
            if (a != null) a.ataque = Math.min(LanternaConfig.GUILDA_MAX_ATAQUE.get(), a.ataque + inv.tropas.size());
            inv.barra.removeAllPlayers();
            BlockState st = inv.level.getBlockState(inv.c.bandeira);
            if (st.is(GuildaItems.BANDEIRA.get())) inv.level.setBlock(inv.c.bandeira, st.setValue(BandeiraBlock.LIBERADA, false), 3);
        }
        ATIVAS.clear();
        CHEGANDO.clear();
        gs.setDirty();
    }

    public static String tempo(long ms) {
        long m = ms / 60000;
        return m >= 60 ? String.format("%dh%02dm", m / 60, m % 60) : String.format("%dm%02ds", m, (ms / 1000) % 60);
    }
}
