package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.lore.LoreWorldData;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.*;

/**
 * 0.55.0: a fenda da Morte sobre a Bateria Central de uma Tropa, e a defesa dela.
 *
 * Depois da fuga, o Necron abre uma fenda no céu do planeta da Tropa do jogador (a cena do alerta). Ela fica lá,
 * girando, até alguém defender a bateria. Quem volta ao planeta com dois anéis (missão "A Defesa da Tropa") começa a
 * batalha: três ondas de servos caem da fenda; os Lanternas da Tropa descem para ajudar; os servos que chegam perto
 * da bateria sugam a força dela (a barra no alto). Vencendo as três ondas, a fenda se fecha. Se a bateria chegar a
 * zero, os servos recuam e a batalha recomeça depois de um tempo (a Tropa do jogador nunca cai).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Defesa {
    private static final String FENDA = "saga:fenda:", DEFENDIDA = "saga:defendida:";
    public static final String ONDA_TAG = "lanterna_onda_necron", DEFENSOR = "lanterna_defensor";
    private static final int ONDAS = 3, RAIO_BATALHA = 110, ALCANCE_BATERIA = 9;

    private static final class Batalha {
        final ServerLevel l;
        final PlanetLore planeta;
        final ServerBossEvent barra;
        int onda = 0, faltaNascer, recarga = 60, pausa, ticks;
        float forca = 100;
        final Set<UUID> servos = new HashSet<>();
        final List<UUID> defensores = new ArrayList<>();

        Batalha(ServerLevel l, PlanetLore planeta) {
            this.l = l;
            this.planeta = planeta;
            this.barra = new ServerBossEvent(Component.translatable("necron.lanterna.defesa_barra",
                    Component.translatable("corps.lanterna." + planeta.corps.id().getPath())), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        }
    }

    private static final Map<PlanetLore, Batalha> BATALHAS = new EnumMap<>(PlanetLore.class);
    private static final Map<PlanetLore, Long> ESPERA = new EnumMap<>(PlanetLore.class);

    // ===================== a fenda =====================

    /** Onde fica a fenda: alto, sobre a Bateria Central (o centro da cidade). */
    public static BlockPos fenda(ServerLevel l) {
        BlockPos h = PlanetLore.HUB;
        l.getChunk(h.getX() >> 4, h.getZ() >> 4);
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, h.getX(), h.getZ());
        return new BlockPos(h.getX(), y + 18, h.getZ());
    }

    public static BlockPos bateria(ServerLevel l) {
        BlockPos h = PlanetLore.HUB;
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, h.getX() + 3, h.getZ() + 3);
        return new BlockPos(h.getX(), y, h.getZ());
    }

    public static boolean fendaAberta(MinecraftServer s, PlanetLore p) {
        return LoreWorldData.get(s).siteBuilt(FENDA + p.planet);
    }

    public static boolean defendida(MinecraftServer s, PlanetLore p) {
        return LoreWorldData.get(s).siteBuilt(DEFENDIDA + p.planet);
    }

    /** O Necron abre a fenda sobre a Bateria Central da Tropa (se ela ainda não foi defendida). */
    public static void abrirFenda(ServerLevel l, BuiltinCorps tropa) {
        PlanetLore p = PlanetLore.byCorps(tropa.id());
        if (p == null || defendida(l.getServer(), p)) return;
        LoreWorldData.get(l.getServer()).markSite(FENDA + p.planet);
    }

    // ===================== tick =====================

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
        int tc = s.getTickCount();
        // a fenda girando no céu (só onde há gente)
        if (tc % 3 == 0) {
            for (PlanetLore p : PlanetLore.values()) {
                if (!fendaAberta(s, p)) continue;
                ServerLevel l = nivel(s, p);
                if (l == null || l.players().isEmpty()) continue;
                desenharFenda(l, fenda(l), tc, BATALHAS.containsKey(p) ? 1.6f : 1f);
            }
        }
        if (tc % 20 == 11) comecarSeAlguemChegou(s);
        for (Iterator<Batalha> it = BATALHAS.values().iterator(); it.hasNext(); ) {
            Batalha b = it.next();
            if (!passo(b)) {
                b.barra.removeAllPlayers();
                for (UUID u : b.defensores) { Entity d = b.l.getEntity(u); if (d != null) d.discard(); }
                it.remove();
            }
        }
    }

    @Nullable
    private static ServerLevel nivel(MinecraftServer s, PlanetLore p) {
        var corpo = com.lanterna.space.SpaceBodies.byId(p.planet);
        return corpo == null || corpo.dimension() == null ? null : s.getLevel(corpo.dimension());
    }

    /** Um redemoinho escuro com borda roxa, raios caindo de vez em quando (visto de longe: partículas de longa distância). */
    private static void desenharFenda(ServerLevel l, BlockPos f, int tc, float forca) {
        DustParticleOptions negro = new DustParticleOptions(new Vector3f(0.05f, 0.0f, 0.09f), 4f);
        DustParticleOptions roxo = new DustParticleOptions(new Vector3f(0.55f, 0.15f, 0.8f), 3f);
        double gira = tc * 0.05;
        List<ServerPlayer> ver = new ArrayList<>();
        for (ServerPlayer q : l.players()) if (q.position().distanceTo(Vec3.atCenterOf(f)) < 220) ver.add(q);
        if (ver.isEmpty()) return;
        for (int i = 0; i < 40 * forca; i++) {
            double a = gira * (1 + (i % 3) * 0.3) + i * 0.73, r = 1.5 + (i % 20) * 0.65;
            boolean borda = r > 11;
            double x = f.getX() + 0.5 + Math.cos(a) * r, y = f.getY() + Math.sin(a * 3) * 0.4 - r * 0.08, z = f.getZ() + 0.5 + Math.sin(a) * r;
            for (ServerPlayer q : ver) l.sendParticles(q, borda || i % 5 == 0 ? roxo : negro, true, x, y, z, 1, 0, 0, 0, 0);
        }
        for (ServerPlayer q : ver) l.sendParticles(q, ParticleTypes.REVERSE_PORTAL, true, f.getX() + 0.5, f.getY(), f.getZ() + 0.5, 10, 5, 0.4, 5, 0.05);
        if (tc % 120 == 0) {
            l.playSound(null, f, SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), SoundSource.AMBIENT, 3f, 0.5f);
            for (int k = 0; k < 24; k++) for (ServerPlayer q : ver) l.sendParticles(q, roxo, true, f.getX() + 0.5, f.getY() - k * 1.4, f.getZ() + 0.5, 2, 0.15, 0.2, 0.15, 0);
        }
    }

    // ===================== a batalha =====================

    private static String missao(BuiltinCorps t) { return "necron_4_" + t.id().getPath(); }

    private static void comecarSeAlguemChegou(MinecraftServer s) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            PlanetLore aqui = PlanetLore.byDimension(p.level().dimension());
            if (aqui == null || !fendaAberta(s, aqui) || BATALHAS.containsKey(aqui)) continue;
            LoreData d = MissionManager.data(p);
            if (d == null || !d.active().containsKey(missao(aqui.corps))) continue;
            if (!Cratera.doisAneis(p)) {
                if (p.tickCount % 200 < 20) p.displayClientMessage(Component.translatable("necron.lanterna.defesa_precisa_dois").withStyle(ChatFormatting.DARK_PURPLE), true);
                continue;
            }
            ServerLevel l = p.serverLevel();
            if (p.position().distanceTo(Vec3.atCenterOf(bateria(l))) > 70) continue;
            if (s.overworld().getGameTime() < ESPERA.getOrDefault(aqui, 0L)) continue;
            Batalha b = new Batalha(l, aqui);
            BATALHAS.put(aqui, b);
            defensores(b);
            for (ServerPlayer q : l.players()) {
                if (q.position().distanceTo(Vec3.atCenterOf(bateria(l))) > RAIO_BATALHA) continue;
                titulo(q, "necron.lanterna.defesa_titulo", "necron.lanterna.defesa_sub", 0xFF4060);
            }
            l.playSound(null, fenda(l), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3f, 0.6f);
            LanternaMod.LOGGER.info("Lanterna: começou a defesa da Bateria Central de {}", aqui.planet);
        }
    }

    private static void titulo(ServerPlayer q, String t, String sub, int cor) {
        q.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.translatable(t).withStyle(st -> st.withColor(cor))));
        q.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.translatable(sub).withStyle(ChatFormatting.GRAY)));
    }

    /** Três Lanternas da Tropa descem para defender a bateria junto. */
    private static void defensores(Batalha b) {
        BlockPos bat = bateria(b.l);
        for (int i = 0; i < 3; i++) {
            LanternaEntity d = LanternaEntity.lanterna(b.l, b.planeta.corps, i == 0);
            if (d == null) continue;
            double a = Math.PI * 2 * i / 3;
            d.moveTo(bat.getX() + Math.cos(a) * 7 + 0.5, bat.getY() + 1, bat.getZ() + Math.sin(a) * 7 + 0.5, (float) Math.toDegrees(a), 0);
            d.addTag(DEFENSOR);
            b.l.addFreshEntity(d);
            b.defensores.add(d.getUUID());
        }
    }

    /** Um passo da batalha. Devolve false quando acabou. */
    private static boolean passo(Batalha b) {
        b.ticks++;
        ServerLevel l = b.l;
        BlockPos bat = bateria(l), f = fenda(l);
        List<ServerPlayer> perto = new ArrayList<>();
        for (ServerPlayer q : l.players()) if (!q.isSpectator() && q.position().distanceTo(Vec3.atCenterOf(bat)) < RAIO_BATALHA) perto.add(q);
        if (perto.isEmpty()) {
            // todo mundo foi embora: os servos voltam para a fenda
            limparServos(b);
            return false;
        }
        for (ServerPlayer q : perto) if (!b.barra.getPlayers().contains(q)) b.barra.addPlayer(q);
        for (ServerPlayer q : new ArrayList<>(b.barra.getPlayers())) if (!perto.contains(q)) b.barra.removePlayer(q);

        // servos vivos desta onda
        b.servos.removeIf(u -> { Entity e = l.getEntity(u); return e == null || !e.isAlive(); });

        if (b.pausa > 0) { b.pausa--; return true; }
        if (b.onda == 0 || (b.faltaNascer == 0 && b.servos.isEmpty())) {
            if (b.onda >= ONDAS) { vitoria(b, perto); return false; }
            if (b.onda > 0) {
                for (ServerPlayer q : perto) q.displayClientMessage(Component.translatable("necron.lanterna.defesa_onda_vencida", b.onda).withStyle(ChatFormatting.GOLD), true);
                b.pausa = 100;
            }
            b.onda++;
            b.faltaNascer = 5 + 3 * b.onda;
            b.recarga = b.pausa + 20;
            if (b.onda > 1) b.pausa += 0;
            for (ServerPlayer q : perto) q.sendSystemMessage(Component.translatable("necron.lanterna.defesa_onda", b.onda, ONDAS).withStyle(ChatFormatting.DARK_PURPLE));
            return true;
        }
        // servos caindo da fenda
        if (b.faltaNascer > 0 && --b.recarga <= 0) {
            b.recarga = 14;
            b.faltaNascer--;
            boolean arauto = b.onda == ONDAS && b.faltaNascer == 0;
            Mob m = arauto ? LanternaEntity.lanterna(l, BuiltinCorps.BLACK, true) : Cratera.novoServo(l);
            if (m != null) {
                double a = l.random.nextDouble() * Math.PI * 2, r = 4 + l.random.nextDouble() * 8;
                m.moveTo(f.getX() + Math.cos(a) * r + 0.5, f.getY() - 2, f.getZ() + Math.sin(a) * r + 0.5, l.random.nextFloat() * 360, 0);
                m.addTag(Cratera.SERVO);
                m.addTag(ONDA_TAG);
                m.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 200, 0, false, false));
                if (arauto) {
                    m.setCustomName(Component.translatable("necron.lanterna.arauto").withStyle(ChatFormatting.DARK_PURPLE));
                    var hp = m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                    if (hp != null) { hp.setBaseValue(hp.getBaseValue() * 3); m.setHealth(m.getMaxHealth()); }
                }
                l.addFreshEntity(m);
                if (m instanceof LanternaEntity le) le.setTarget(perto.get(l.random.nextInt(perto.size())));
                b.servos.add(m.getUUID());
                l.sendParticles(ParticleTypes.SOUL, m.getX(), m.getY(), m.getZ(), 15, 0.3, 0.5, 0.3, 0.02);
            }
        }
        // quem chega perto da bateria suga a força dela
        if (b.ticks % 20 == 0) {
            int sugando = 0;
            for (UUID u : b.servos) {
                Entity e = l.getEntity(u);
                if (e != null && e.position().distanceTo(Vec3.atCenterOf(bat)) < ALCANCE_BATERIA) {
                    sugando++;
                    l.sendParticles(new DustParticleOptions(new Vector3f(0.2f, 0.02f, 0.3f), 1.4f), e.getX(), e.getY() + 1, e.getZ(), 6, 0.2, 0.4, 0.2, 0);
                }
            }
            b.forca = Math.max(0, b.forca - sugando * 1.5f);
            if (sugando > 0 && b.ticks % 60 == 0) l.playSound(null, bat, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1f, 0.5f);
        }
        b.barra.setProgress(b.forca / 100f);
        if (b.forca <= 0) { derrota(b, perto); return false; }
        return true;
    }

    private static void limparServos(Batalha b) {
        for (UUID u : b.servos) {
            Entity e = b.l.getEntity(u);
            if (e != null) {
                b.l.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 0.5, e.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
                e.discard();
            }
        }
        b.servos.clear();
    }

    private static void vitoria(Batalha b, List<ServerPlayer> perto) {
        MinecraftServer s = b.l.getServer();
        LoreWorldData w = LoreWorldData.get(s);
        w.resetSite(FENDA + b.planeta.planet);
        w.markSite(DEFENDIDA + b.planeta.planet);
        BlockPos f = fenda(b.l);
        int cor = b.planeta.corps.color();
        DustParticleOptions luz = new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 3f);
        for (int k = 0; k < 30; k++) b.l.sendParticles(luz, f.getX() + 0.5, f.getY() - k * 1.1, f.getZ() + 0.5, 12, 1.5, 0.3, 1.5, 0);
        b.l.playSound(null, f, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3f, 0.8f);
        b.l.playSound(null, f, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 1.5f, 1.2f);
        for (ServerPlayer q : perto) {
            titulo(q, "necron.lanterna.defesa_vitoria", "necron.lanterna.defesa_vitoria_sub", cor);
            q.sendSystemMessage(Component.translatable("necron.lanterna.defesa_fala").withStyle(st -> st.withColor(cor)));
            com.lanterna.fonte.FonteDados.chamado(q, "necron:defesa_" + b.planeta.corps.id().getPath(), 1);
            Pedidos.defendeu(q, b.planeta);
        }
        LanternaMod.LOGGER.info("Lanterna: a Bateria Central de {} foi defendida", b.planeta.planet);
    }

    private static void derrota(Batalha b, List<ServerPlayer> perto) {
        limparServos(b);
        ESPERA.put(b.planeta, b.l.getServer().overworld().getGameTime() + 20 * 30);
        for (ServerPlayer q : perto) {
            titulo(q, "necron.lanterna.defesa_derrota", "necron.lanterna.defesa_derrota_sub", 0x9A6AFF);
        }
    }

    /** Teste: fecha tudo deste planeta (a fenda volta a ficar aberta). */
    public static void zerar(MinecraftServer s, PlanetLore p) {
        Batalha b = BATALHAS.remove(p);
        if (b != null) { limparServos(b); b.barra.removeAllPlayers(); for (UUID u : b.defensores) { Entity d = b.l.getEntity(u); if (d != null) d.discard(); } }
        ESPERA.remove(p);
        LoreWorldData.get(s).resetSite(DEFENDIDA + p.planet);
        LoreWorldData.get(s).resetSite(FENDA + p.planet);
    }

    public static boolean emBatalha(PlanetLore p) { return BATALHAS.containsKey(p); }

    private Defesa() {}
}
