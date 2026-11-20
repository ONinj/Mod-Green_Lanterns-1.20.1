package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.fonte.FonteDados;
import com.lanterna.lore.LoreWorldData;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import com.lanterna.vida.Emocoes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.*;

/**
 * 0.57.0: a linha corrompida (o segundo final): quem aceitou a Morte serve o Necron, e o Necron vence.
 *
 *  necron_c1  O Juramento da Morte: ajoelhar diante do Necron, na cratera. Ele fala, e o jogador vira o Arauto dele.
 *  necron_c2  Apagar as Luzes: em três Tropas que ainda resistem, ficar junto à Bateria Central enquanto ela se apaga
 *             (30 s), com os Lanternas da Tropa defendendo. Cada bateria apagada derruba aquela Tropa (para o mundo todo).
 *  necron_c3  O Coração da Vida: descer ao Santuário, vencer os oito últimos guardiões do Embrião e envolver o Embrião
 *             na escuridão até ele se apagar.
 *  necron_c4  A Noite Eterna: levar o Embrião apagado ao Necron. Ele bebe a luz da Vida, uma onda escura cobre a
 *             Terra, todas as Tropas caem e o céu fica roxo-escuro para sempre. Epílogo: poucos Lanternas resistem,
 *             escondidos, e juram purificar o Arauto (gancho para a próxima saga, em que eles vêm por ele).
 *
 * Os Lanternas das Tropas atacam quem escolheu a Morte (a trégua não vale para ele); os servos do Necron, não.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LinhaCorrompida {
    public static final String PRESO = "saga:embriao_preso", VENCEU = "saga:necron_venceu", T_NOITE = "saga:noite_t:";
    public static final String DEFENSOR = "lanterna_defensor_luz", GUARDIAO = "lanterna_guardiao_embriao";
    public static final int APAGAR = 600, GUARDIOES = 8, ESCURECER = 200;
    private static final Map<UUID, int[]> PROGRESSO = new HashMap<>();
    private static final Map<UUID, Integer> FALA = new HashMap<>();

    private static boolean feito(MinecraftServer s, String k) { return LoreWorldData.get(s).siteBuilt(k); }
    private static void marcar(MinecraftServer s, String k) { LoreWorldData.get(s).markSite(k); }

    public static boolean venceu(MinecraftServer s) { return feito(s, VENCEU); }

    public static long noite(MinecraftServer s) {
        for (String id : LoreWorldData.get(s).sites())
            if (id.startsWith(T_NOITE)) try { return Long.parseLong(id.substring(T_NOITE.length())); } catch (NumberFormatException ignored) {}
        return -1;
    }

    /** Para o cliente: a onda escura e a noite que não acaba. */
    public static String paraCliente(MinecraftServer s) {
        long t = noite(s);
        return t >= 0 ? "noite:" + t + ":0:0" : "";
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || !Tentacao.corrompido(p)) return;
        LoreData d = MissionManager.data(p);
        if (d == null) return;
        Integer f = FALA.get(p.getUUID());
        if (f != null) { juramento(p, f + 1); return; }
        if (p.tickCount % 10 != 3) return;
        if (d.isActive("necron_c1")) perto(p, 14, () -> FALA.put(p.getUUID(), 0));
        if (d.isActive("necron_c2")) apagar(p);
        if (d.isActive("necron_c3")) coracao(p, d);
        if (d.isActive("necron_c4") && !venceu(p.server)) perto(p, 20, () -> FALA.put(p.getUUID(), 1000));
        if (d.isActive("necron_c4") && venceu(p.server) && FonteDados.chamado(p, "necron:noite") < 1) FonteDados.chamado(p, "necron:noite", 1);
    }

    /** Perto do Necron (na Terra). */
    private static void perto(ServerPlayer p, double raio, Runnable faz) {
        if (p.level().dimension() != Level.OVERWORLD) return;
        NecronEntity n = p.serverLevel().getEntitiesOfClass(NecronEntity.class, p.getBoundingBox().inflate(raio)).stream().findFirst().orElse(null);
        if (n != null) faz.run();
    }

    private static void dizer(ServerPlayer p, String chave, ChatFormatting cor) {
        p.sendSystemMessage(Component.translatable(chave).withStyle(cor, ChatFormatting.ITALIC));
    }

    /** As falas do Necron no Juramento (t de 0 a ~220) e na Noite Eterna (t a partir de 1000). */
    private static void juramento(ServerPlayer p, int t) {
        FALA.put(p.getUUID(), t);
        ServerLevel l = p.serverLevel();
        if (t < 1000) {
            p.setDeltaMovement(Vec3.ZERO);
            if (t == 1) { dizer(p, "necron.lanterna.c1_fala_1", ChatFormatting.DARK_PURPLE); l.playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2f, 0.5f); }
            if (t == 80) dizer(p, "necron.lanterna.c1_fala_2", ChatFormatting.DARK_PURPLE);
            if (t == 160) dizer(p, "necron.lanterna.c1_fala_3", ChatFormatting.DARK_PURPLE);
            if (t == 220) {
                FALA.remove(p.getUUID());
                FonteDados.chamado(p, "necron:juramento", 1);
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.translatable("necron.lanterna.c1_titulo").withStyle(ChatFormatting.DARK_PURPLE)));
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.translatable("necron.lanterna.c1_sub").withStyle(ChatFormatting.GRAY)));
                l.sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 1, p.getZ(), 100, 0.6, 1.2, 0.6, 0.05);
                l.playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1f, 0.6f);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 60 * 20, 0, false, false));
            }
            return;
        }
        int u = t - 1000;
        if (u == 1) dizer(p, "necron.lanterna.c4_fala_1", ChatFormatting.DARK_PURPLE);
        if (u == 80) dizer(p, "necron.lanterna.c4_fala_2", ChatFormatting.DARK_PURPLE);
        if (u == 160) noiteEterna(l, p);
        if (u == 400) dizer(p, "necron.lanterna.c4_epilogo_1", ChatFormatting.GRAY);
        if (u == 500) dizer(p, "necron.lanterna.c4_epilogo_2", ChatFormatting.GRAY);
        if (u >= 500) FALA.remove(p.getUUID());
    }

    // ===================== apagar as luzes =====================

    private static void apagar(ServerPlayer p) {
        PlanetLore pl = PlanetLore.byDimension(p.level().dimension());
        if (pl == null || !Emocoes.aspecto(pl.corps.id().getPath()) || Queda.caida(p.server, pl)) { PROGRESSO.remove(p.getUUID()); return; }
        BlockPos h = PlanetLore.HUB;
        double dx = p.getX() - h.getX(), dz = p.getZ() - h.getZ(), dist = Math.sqrt(dx * dx + dz * dz);
        ServerLevel l = p.serverLevel();
        if (dist > 18) { PROGRESSO.remove(p.getUUID()); return; }
        int[] pr = PROGRESSO.computeIfAbsent(p.getUUID(), u -> new int[1]);
        if (pr[0] == 0) p.sendSystemMessage(Component.translatable("necron.lanterna.c2_comeca", Component.translatable("necron.lanterna.tropa." + pl.corps.id().getPath()))
                .withStyle(ChatFormatting.DARK_PURPLE));
        pr[0] += 10;
        int cor = pl.corps.color();
        // a luz da bateria vai sendo puxada para o anel negro
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, h.getX() + 14, h.getZ());
        DustParticleOptions dd = new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 1.5f);
        Vec3 de = new Vec3(h.getX() + 0.5, y + 12, h.getZ() + 0.5), ate = p.position().add(0, 1.2, 0);
        for (int k = 0; k < 12; k++) {
            Vec3 q = de.lerp(ate, (k + l.random.nextDouble()) / 12);
            l.sendParticles(dd, q.x, q.y, q.z, 1, 0.1, 0.1, 0.1, 0);
        }
        l.sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1, p.getZ(), 2, 0.3, 0.5, 0.3, 0.01);
        p.displayClientMessage(Component.translatable("necron.lanterna.c2_progresso", Math.min(100, pr[0] * 100 / APAGAR)).withStyle(ChatFormatting.DARK_PURPLE), true);
        // a Tropa defende a bateria
        String tag = DEFENSOR + ":" + pl.planet;
        if (pr[0] % 100 == 10 && l.getEntitiesOfClass(LanternaEntity.class, p.getBoundingBox().inflate(40), x -> x.getTags().contains(tag)).size() < 5) {
            for (int i = 0; i < 2; i++) {
                LanternaEntity a = LanternaEntity.lanterna(l, pl.corps, true);
                if (a == null) continue;
                double ang = l.random.nextDouble() * Math.PI * 2;
                a.moveTo(p.getX() + Math.cos(ang) * 10, p.getY() + 6, p.getZ() + Math.sin(ang) * 10, 0, 0);
                a.addTag(DEFENSOR);
                a.addTag(tag);
                a.setPersistenceRequired();
                a.setCustomName(Component.translatable("necron.lanterna.defensor", Component.translatable("necron.lanterna.tropa." + pl.corps.id().getPath()))
                        .withStyle(st -> st.withColor(cor)));
                l.addFreshEntity(a);
                a.setTarget(p);
            }
        }
        if (pr[0] < APAGAR) return;
        PROGRESSO.remove(p.getUUID());
        Queda.cair(p.server, pl);
        int n = 0;
        for (int i = 1; i <= 3; i++) if (FonteDados.chamado(p, "necron:apagou_" + i) >= 1) n = i;
        FonteDados.chamado(p, "necron:apagou_" + Math.min(3, n + 1), 1);
        l.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 3f, 0.5f);
        for (ServerPlayer q : p.server.getPlayerList().getPlayers())
            q.sendSystemMessage(Component.translatable("necron.lanterna.c2_apagou", Component.translatable("necron.lanterna.tropa." + pl.corps.id().getPath()), p.getName())
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
    }

    // ===================== o coração da vida =====================

    private static void coracao(ServerPlayer p, LoreData d) {
        if (p.level().dimension() != Level.OVERWORLD) return;
        ServerLevel l = p.serverLevel();
        BlockPos b = Escurece.santuario();
        if (b == null) return;
        double dx = p.getX() - b.getX(), dz = p.getZ() - b.getZ();
        if (dx * dx + dz * dz > 140 * 140) return;
        Escurece.construirSantuario(l);
        BlockPos c = Escurece.camara(p.server);
        if (c == null) return;
        MinecraftServer s = p.server;
        // a entrada: uma coluna escura
        if (p.tickCount % 20 == 3) {
            DustParticleOptions dd = new DustParticleOptions(new Vector3f(0.35f, 0.15f, 0.5f), 3f);
            int topo = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, b.getX() + 8, b.getZ());
            for (int n = 0; n < 20; n++) l.sendParticles(p, dd, true, b.getX() + 8.5, topo + 2 + n * 2.5, b.getZ() + 0.5, 1, 0.1, 0.3, 0.1, 0);
        }
        double dist = Math.sqrt(p.distanceToSqr(Vec3.atCenterOf(c)));
        // os oito últimos guardiões
        if (FonteDados.chamado(p, "necron:guardioes") < 1) {
            if (dist > 24) return;
            if (!feito(s, "saga:guardioes")) {
                marcar(s, "saga:guardioes");
                List<BuiltinCorps> de = new ArrayList<>();
                for (String t : Emocoes.ASPECTOS) {
                    PlanetLore pl = null;
                    for (PlanetLore x : PlanetLore.values()) if (x.corps.id().getPath().equals(t)) pl = x;
                    if (pl != null && !Queda.caida(s, pl)) for (BuiltinCorps bc : BuiltinCorps.values()) if (bc.id().getPath().equals(t)) de.add(bc);
                }
                if (de.isEmpty()) de.add(BuiltinCorps.GREEN);
                for (int i = 0; i < GUARDIOES; i++) {
                    BuiltinCorps bc = de.get(i % de.size());
                    LanternaEntity a = LanternaEntity.lanterna(l, bc, true);
                    if (a == null) continue;
                    double ang = Math.PI * 2 * i / GUARDIOES;
                    a.moveTo(c.getX() + 0.5 + Math.cos(ang) * 4.5, c.getY() - 4, c.getZ() + 0.5 + Math.sin(ang) * 4.5, 0, 0);
                    a.addTag(GUARDIAO);
                    a.setPersistenceRequired();
                    a.setCustomName(Component.translatable("necron.lanterna.guardiao", Component.translatable("necron.lanterna.tropa." + bc.id().getPath()))
                            .withStyle(st -> st.withColor(bc.color())));
                    l.addFreshEntity(a);
                    a.setTarget(p);
                }
                dizer(p, "necron.lanterna.c3_guardioes", ChatFormatting.GOLD);
            }
            int vivos = l.getEntitiesOfClass(LanternaEntity.class, new AABB(c).inflate(40), x -> x.getTags().contains(GUARDIAO)).size();
            p.displayClientMessage(Component.translatable("necron.lanterna.c3_restam", vivos, GUARDIOES).withStyle(ChatFormatting.GOLD), true);
            if (vivos == 0) {
                FonteDados.chamado(p, "necron:guardioes", 1);
                dizer(p, "necron.lanterna.c3_guardioes_fim", ChatFormatting.DARK_PURPLE);
            }
            return;
        }
        // envolver o Embrião na escuridão
        if (feito(s, PRESO)) { if (FonteDados.chamado(p, "necron:embriao_preso") < 1) FonteDados.chamado(p, "necron:embriao_preso", 1); return; }
        int[] pr = PROGRESSO.computeIfAbsent(p.getUUID(), u -> new int[1]);
        if (dist > 7) { pr[0] = 0; return; }
        pr[0] += 10;
        l.sendParticles(ParticleTypes.SCULK_SOUL, c.getX() + 0.5, c.getY() - 1.5, c.getZ() + 0.5, 6, 0.8, 0.8, 0.8, 0.01);
        if (pr[0] % 40 == 10) l.playSound(null, c, SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 2f, 1.2f - pr[0] / (float) ESCURECER * 0.6f);
        p.displayClientMessage(Component.translatable("necron.lanterna.c3_escurece", Math.min(100, pr[0] * 100 / ESCURECER)).withStyle(ChatFormatting.DARK_PURPLE), true);
        if (pr[0] < ESCURECER) return;
        PROGRESSO.remove(p.getUUID());
        marcar(s, PRESO);
        FonteDados.chamado(p, "necron:embriao_preso", 1);
        dizer(p, "necron.lanterna.c3_preso", ChatFormatting.DARK_PURPLE);
        l.playSound(null, c, SoundEvents.BEACON_DEACTIVATE, SoundSource.AMBIENT, 3f, 0.5f);
        for (ServerPlayer q : s.getPlayerList().getPlayers()) SagaNecron.mandarMundo(q);
    }

    // ===================== a noite eterna =====================

    private static void noiteEterna(ServerLevel l, ServerPlayer quem) {
        MinecraftServer s = l.getServer();
        marcar(s, VENCEU);
        marcar(s, T_NOITE + l.getGameTime());
        if (Cratera.estagio(s) < TerraEscurece.ETAPAS) Cratera.crescer(s, TerraEscurece.ETAPAS);
        for (PlanetLore pl : PlanetLore.values())
            if (Emocoes.aspecto(pl.corps.id().getPath()) && !Queda.caida(s, pl)) Queda.cair(s, pl);
        for (ServerPlayer q : s.getPlayerList().getPlayers()) {
            q.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.translatable("necron.lanterna.c4_titulo").withStyle(ChatFormatting.DARK_PURPLE)));
            q.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.translatable("necron.lanterna.c4_sub").withStyle(ChatFormatting.GRAY)));
            q.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8f, 0.4f);
            SagaNecron.mandarMundo(q);
        }
        BlockPos c = Cratera.centro(s);
        if (c != null) {
            l.sendParticles(ParticleTypes.SCULK_SOUL, c.getX() + 0.5, c.getY() + 20, c.getZ() + 0.5, 300, 10, 15, 10, 0.1);
            l.playSound(null, c, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 4f, 0.5f);
        }
        FonteDados.chamado(quem, "necron:noite", 1);
        LanternaMod.LOGGER.info("Lanterna: o Necron venceu (linha corrompida, por {})", quem.getGameProfile().getName());
    }

    /** Teste: volta a linha (as marcas do mundo). */
    public static void zerar(MinecraftServer s) {
        LoreWorldData w = LoreWorldData.get(s);
        for (String k : new ArrayList<>(w.sites()))
            if (k.equals(PRESO) || k.equals(VENCEU) || k.startsWith(T_NOITE) || k.equals("saga:guardioes")) w.resetSite(k);
    }

    public static void zerar(ServerPlayer p) {
        for (String k : new String[]{"necron:juramento", "necron:apagou_1", "necron:apagou_2", "necron:apagou_3", "necron:guardioes", "necron:embriao_preso", "necron:noite"})
            FonteDados.chamado(p, k, 0);
        PROGRESSO.remove(p.getUUID());
        FALA.remove(p.getUUID());
    }

    private LinhaCorrompida() {}
}
