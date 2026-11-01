package com.lanterna.fonte;

import com.lanterna.LanternaMod;
import com.lanterna.baterias.Tropa;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.lore.PlanetLore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Evento de servidor "A Bateria Enfraquece": de tempos em tempos, piratas atacam a Bateria Central de um planeta onde
 * há jogadores. Enquanto o ataque dura, os anéis daquela Tropa recarregam pela metade. Quem defende ganha Essência da
 * Fonte; se a Bateria resistir, ela brilha mais forte por 30 minutos (recarga +50%); se cair, fica fraca por 30 minutos.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BateriaEnfraquece {
    /** Intervalo entre ataques (~2 h reais com alguém num planeta com Bateria). */
    private static final int INTERVALO = 20 * 60 * 120;
    private static final int ONDAS = 4;
    private static final int DURACAO_MAX = 20 * 60 * 6;
    private static final int EFEITO = 20 * 60 * 30;

    private static final class Ataque {
        final Tropa tropa;
        final ServerLevel level;
        final BlockPos bateria;
        final ServerBossEvent barra;
        final List<Mob> piratas = new ArrayList<>();
        final Set<UUID> defensores = new HashSet<>();
        int ticks, onda, integridade = 100;

        Ataque(Tropa t, ServerLevel l, BlockPos b) {
            tropa = t; level = l; bateria = b;
            barra = new ServerBossEvent(Component.translatable("evento.lanterna.bateria.barra", Component.translatable("bateria.lanterna." + t.nome)),
                    BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
        }
    }

    @Nullable private static Ataque atual;
    private static int espera = INTERVALO / 2;
    /** Tropa → (game time do fim do efeito, multiplicador de recarga). */
    private static final Map<Tropa, long[]> EFEITOS = new HashMap<>();

    /** Multiplicador da recarga automática do anel desta Tropa agora (ataque em curso / bônus / Bateria fraca). */
    public static float recarga(Player p) {
        if (p.level().isClientSide) return 1f;
        Tropa t = tropaDe(p);
        if (t == null) return 1f;
        if (atual != null && atual.tropa == t) return 0.5f;
        long[] ef = EFEITOS.get(t);
        if (ef != null && p.level().getGameTime() < ef[0]) return ef[1] / 100f;
        return 1f;
    }

    @Nullable
    private static Tropa tropaDe(Player p) {
        String c = com.lanterna.construct.fonte.ArvoreDaLuz.corps(p);
        if (c == null) return null;
        for (Tropa t : Tropa.values()) if (t.corps().id().getPath().equals(c)) return t;
        return null;
    }

    public static boolean ativo() { return atual != null; }

    /** Começa um ataque no planeta onde o jogador está (comando de teste) ou num planeta sorteado com jogadores. */
    public static boolean comecar(MinecraftServer server, @Nullable ServerPlayer onde) {
        if (atual != null) return false;
        List<ServerLevel> opcoes = new ArrayList<>();
        for (ServerLevel l : server.getAllLevels()) {
            PlanetLore pl = PlanetLore.byDimension(l.dimension());
            if (pl == null || Tropa.doPlaneta(pl) == null || l.players().isEmpty()) continue;
            if (onde != null && onde.serverLevel() != l) continue;
            opcoes.add(l);
        }
        if (opcoes.isEmpty()) return false;
        ServerLevel l = opcoes.get(l0(server).random.nextInt(opcoes.size()));
        Tropa t = Tropa.doPlaneta(PlanetLore.byDimension(l.dimension()));
        BlockPos p = FonteChamado.PRACA;
        l.getChunkAt(p);
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ() - 13);
        atual = new Ataque(t, l, new BlockPos(p.getX(), y, p.getZ() - 13));
        atual.barra.setColor(t == Tropa.VERDE ? BossEvent.BossBarColor.GREEN : t == Tropa.AMARELO || t == Tropa.LARANJA ? BossEvent.BossBarColor.YELLOW
                : t == Tropa.VERMELHO ? BossEvent.BossBarColor.RED : t == Tropa.AZUL ? BossEvent.BossBarColor.BLUE : t == Tropa.INDIGO ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.PINK);
        Component nome = Component.translatable("bateria.lanterna." + t.nome);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            sp.sendSystemMessage(Component.translatable("evento.lanterna.bateria.comeca", nome, l.dimension().location().getPath(), atual.bateria.getX(), atual.bateria.getZ())
                    .withStyle(s -> s.withColor(t.cor).withBold(true)));
            if (sp.serverLevel() == l) l.playSound(null, sp.blockPosition(), SoundEvents.RAID_HORN.get(), SoundSource.HOSTILE, 1f, 0.9f);
        }
        return true;
    }

    private static ServerLevel l0(MinecraftServer s) { return s.overworld(); }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        if (atual == null) {
            if (--espera <= 0) {
                espera = INTERVALO + server.overworld().random.nextInt(INTERVALO / 2);
                comecar(server, null);
            }
            return;
        }
        Ataque a = atual;
        a.ticks++;
        ServerLevel l = a.level;
        // barra para quem está perto
        for (ServerPlayer sp : l.players()) {
            boolean perto = sp.blockPosition().distSqr(a.bateria) < 160 * 160;
            if (perto && !a.barra.getPlayers().contains(sp)) a.barra.addPlayer(sp);
            else if (!perto && a.barra.getPlayers().contains(sp)) a.barra.removePlayer(sp);
            if (sp.blockPosition().distSqr(a.bateria) < 64 * 64) a.defensores.add(sp.getUUID());
        }
        a.piratas.removeIf(m -> !m.isAlive());
        if (a.piratas.isEmpty() && a.ticks > 40) {
            if (a.onda >= ONDAS) { terminar(server, true); return; }
            onda(a);
        }
        if (a.ticks % 20 == 0) {
            int perto = 0;
            for (Mob m : a.piratas) {
                if (m.blockPosition().distSqr(a.bateria) < 7 * 7) perto++;
                else if (m.getTarget() == null || m.tickCount % 100 == 0) m.getNavigation().moveTo(a.bateria.getX() + 0.5, a.bateria.getY(), a.bateria.getZ() + 0.5, 1.1);
            }
            a.integridade = Math.max(0, a.integridade - perto);
            if (perto > 0) l.sendParticles(ParticleTypes.SMOKE, a.bateria.getX() + 0.5, a.bateria.getY() + 3, a.bateria.getZ() + 0.5, 6 * perto, 1, 1.5, 1, 0.02);
            if (a.integridade <= 0 || a.ticks > DURACAO_MAX || l.players().isEmpty()) { terminar(server, false); return; }
        }
        a.barra.setProgress(a.integridade / 100f);
    }

    private static void onda(Ataque a) {
        a.onda++;
        ServerLevel l = a.level;
        int jogadores = (int) l.players().stream().filter(p -> p.blockPosition().distSqr(a.bateria) < 160 * 160).count();
        int n = 3 + a.onda + 2 * Math.max(0, jogadores - 1);
        for (int i = 0; i < n; i++) {
            boolean capitao = a.onda == ONDAS && i == 0;
            Mob m = LanternaEntity.pirata(l, capitao);
            if (m == null) continue;
            double ang = l.random.nextDouble() * Math.PI * 2, r = 18 + l.random.nextDouble() * 8;
            int x = (int) Math.round(a.bateria.getX() + Math.cos(ang) * r), z = (int) Math.round(a.bateria.getZ() + Math.sin(ang) * r);
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            m.moveTo(x + 0.5, y, z + 0.5, l.random.nextFloat() * 360, 0);
            m.finalizeSpawn(l, l.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null);
            m.getPersistentData().putBoolean("lanterna_evento", true);
            l.addFreshEntity(m);
            a.piratas.add(m);
            l.sendParticles(ParticleTypes.REVERSE_PORTAL, m.getX(), m.getY() + 1, m.getZ(), 30, 0.4, 0.8, 0.4, 0.1);
        }
        for (ServerPlayer sp : a.barra.getPlayers())
            sp.displayClientMessage(Component.translatable("evento.lanterna.bateria.onda", a.onda, ONDAS), true);
    }

    private static void terminar(MinecraftServer server, boolean venceu) {
        Ataque a = atual;
        atual = null;
        if (a == null) return;
        a.barra.removeAllPlayers();
        for (Mob m : a.piratas) if (m.isAlive()) m.discard();
        EFEITOS.put(a.tropa, new long[]{a.level.getGameTime() + EFEITO, venceu ? 150 : 50});
        Component nome = Component.translatable("bateria.lanterna." + a.tropa.nome);
        for (ServerPlayer sp : server.getPlayerList().getPlayers())
            sp.sendSystemMessage(Component.translatable(venceu ? "evento.lanterna.bateria.venceu" : "evento.lanterna.bateria.caiu", nome)
                    .withStyle(s -> s.withColor(a.tropa.cor).withBold(true)));
        if (!venceu) return;
        a.level.playSound(null, a.bateria, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5f, 1f);
        for (UUID id : a.defensores) {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp == null) continue;
            int ganho = a.integridade >= 75 ? 12 : 8;
            FonteDados.essencia(sp, FonteDados.essencia(sp) + ganho);
            com.lanterna.construct.Affinity.add(sp, 80);
            sp.sendSystemMessage(Component.translatable("evento.lanterna.bateria.premio", ganho).withStyle(s -> s.withColor(0xFFE6A0)));
        }
    }

    /** Quem mata um pirata do ataque conta como defensor mesmo longe da praça. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (atual == null || !e.getEntity().getPersistentData().getBoolean("lanterna_evento")) return;
        if (e.getSource().getEntity() instanceof ServerPlayer sp) atual.defensores.add(sp.getUUID());
    }

    private BateriaEnfraquece() {}
}
