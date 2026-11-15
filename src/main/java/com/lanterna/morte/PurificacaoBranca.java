package com.lanterna.morte;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.combate.Supremos;
import com.lanterna.mission.LoreCapability;
import com.lanterna.mission.LoreData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 0.52: o dom do anel da Vida, "Purificar". O portador do Anel Branco aponta o anel para um jogador corrompido (ou já
 * Portador Negro): os dois ficam presos numa cena só, que todos em volta veem. Um fio de luz branca sai do anel até o
 * peito do outro, que é erguido do chão; uma espiral branca sobe em volta dele enquanto a fumaça da Morte sai; e, numa
 * explosão branca, ele volta ao normal. Recarga de 10 minutos (contada no jogo, não some ao sair).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PurificacaoBranca {
    public static final int RECARGA = 20 * 60 * 10;
    private static final int DURACAO = 110;
    private static final String PRONTO_EM = "lanterna_purificar_pronto_em";
    private static final Vector3f BRANCO = new Vector3f(1f, 1f, 1f);

    private record Cena(UUID alvo, Vec3 posDono, Vec3 posAlvo, ServerPlayer donoRef, ServerPlayer alvoRef) {}

    private static final Map<UUID, Cena> CENAS = new HashMap<>();
    private static final Map<UUID, Integer> TEMPO = new HashMap<>();

    /** Usado pela roda (construto "purificar"). true = começou (o construto gasta carga e entra em recarga). */
    public static boolean comecar(ServerPlayer dono) {
        if (CENAS.containsKey(dono.getUUID())) return false;
        long agora = dono.server.overworld().getGameTime();
        long pronto = dono.getPersistentData().getLong(PRONTO_EM);
        if (agora < pronto && !dono.isCreative()) {
            long s = (pronto - agora) / 20;
            dono.displayClientMessage(Component.translatable("purificar.lanterna.recarga", s / 60, String.format("%02d", s % 60)).withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        ServerPlayer alvo = mirar(dono);
        if (alvo == null) {
            dono.displayClientMessage(Component.translatable("purificar.lanterna.sem_alvo").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        LoreData d = LoreCapability.get(alvo).orElse(null);
        if (d == null || !(d.morte() == 2 || d.morte() == Corrupcao.PORTADOR)) {
            dono.displayClientMessage(Component.translatable(d != null && d.morte() == 3 ? "purificar.lanterna.arauto" : "purificar.lanterna.limpo", alvo.getDisplayName())
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        for (Cena c : CENAS.values()) if (c.alvo.equals(alvo.getUUID())) return false;
        CENAS.put(dono.getUUID(), new Cena(alvo.getUUID(), dono.position(), alvo.position(), dono, alvo));
        TEMPO.put(dono.getUUID(), 0);
        dono.getPersistentData().putLong(PRONTO_EM, agora + RECARGA);
        ServerLevel l = dono.serverLevel();
        l.playSound(null, dono.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 1.4f);
        dono.sendSystemMessage(Component.translatable("purificar.lanterna.dono", alvo.getDisplayName()).withStyle(s -> s.withColor(0xF4F4FF).withItalic(true)));
        alvo.sendSystemMessage(Component.translatable("purificar.lanterna.alvo", dono.getDisplayName()).withStyle(s -> s.withColor(0xF4F4FF).withItalic(true)));
        LanternaMod.LOGGER.info("Lanterna: {} começou a purificar {}", dono.getGameProfile().getName(), alvo.getGameProfile().getName());
        return true;
    }

    /** O jogador para onde o anel aponta (até 16 blocos, num cone estreito), ou o corrompido mais perto em 6 blocos. */
    @Nullable
    private static ServerPlayer mirar(ServerPlayer dono) {
        Vec3 olho = dono.getEyePosition(), look = dono.getViewVector(1f);
        ServerPlayer melhor = null, perto = null;
        double nota = Double.MAX_VALUE, pd = 36;
        java.util.List<ServerPlayer> todos = new java.util.ArrayList<>(dono.serverLevel().players());
        for (ServerPlayer f : dono.serverLevel().getEntitiesOfClass(ServerPlayer.class, dono.getBoundingBox().inflate(16))) if (!todos.contains(f)) todos.add(f);
        for (ServerPlayer o : todos) {
            if (o == dono || o.isSpectator() || !o.isAlive() || o.distanceToSqr(dono) > 16 * 16 || !dono.hasLineOfSight(o)) continue;
            Vec3 to = o.position().add(0, o.getBbHeight() * 0.5, 0).subtract(olho);
            double dist = to.length(), cos = to.normalize().dot(look);
            if (cos > 0.93 && dist * (2 - cos) < nota) { nota = dist * (2 - cos); melhor = o; }
            boolean corrompido = LoreCapability.get(o).map(x -> x.morte() == 2 || x.morte() == Corrupcao.PORTADOR).orElse(false);
            if (corrompido && o.distanceToSqr(dono) < pd) { pd = o.distanceToSqr(dono); perto = o; }
        }
        return melhor != null ? melhor : perto;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || CENAS.isEmpty()) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (UUID id : new java.util.ArrayList<>(CENAS.keySet())) {
            Cena c = CENAS.get(id);
            ServerPlayer dono = c.donoRef, alvo = c.alvoRef;
            if (dono.isRemoved() || alvo.hasDisconnected() || dono.hasDisconnected() || !dono.isAlive() || !alvo.isAlive() || dono.level() != alvo.level() || dono.distanceToSqr(alvo) > 30 * 30) {
                acabar(id);
                continue;
            }
            int t = TEMPO.merge(id, 1, Integer::sum);
            passo(dono, alvo, c, t);
            if (t >= DURACAO) acabar(id);
        }
    }

    private static void passo(ServerPlayer dono, ServerPlayer alvo, Cena c, int t) {
        ServerLevel l = dono.serverLevel();
        // os dois ficam presos na cena, frente a frente; o alvo é erguido do chão aos poucos
        double sobe = Math.min(1.0, t / 50.0) * 0.9;
        Vec3 pa = c.posAlvo.add(0, sobe, 0);
        Vec3 dir = pa.subtract(c.posDono);
        float yawDono = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitchDono = (float) -Math.toDegrees(Math.atan2(dir.y + 0.2, Math.sqrt(dir.x * dir.x + dir.z * dir.z)));
        if (dono.position().distanceToSqr(c.posDono) > 0.04 || t % 10 == 1) dono.connection.teleport(c.posDono.x, c.posDono.y, c.posDono.z, yawDono, pitchDono);
        alvo.connection.teleport(pa.x, pa.y, pa.z, yawDono + 180, 10);
        alvo.setDeltaMovement(Vec3.ZERO);
        alvo.fallDistance = 0;
        if (t == 1) {
            dono.addEffect(new MobEffectInstance(MobEffects.GLOWING, DURACAO + 20, 0, false, false));
            alvo.addEffect(new MobEffectInstance(MobEffects.GLOWING, DURACAO + 20, 0, false, false));
            alvo.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, DURACAO + 40, 0, false, false));
        }
        // o braço do anel estendido (balanço de tempos em tempos)
        if (t % 12 == 1) dono.swing(InteractionHand.MAIN_HAND, true);
        // o fio de luz branca do anel até o peito
        Vec3 mao = c.posDono.add(0, 1.35, 0).add(Vec3.directionFromRotation(0, yawDono + 35).scale(0.45));
        Vec3 peito = pa.add(0, 1.1, 0);
        int n = 14;
        for (int i = 0; i <= n; i++) {
            if ((i + t) % 2 != 0) continue;
            Vec3 q = mao.lerp(peito, i / (double) n);
            l.sendParticles(new DustParticleOptions(BRANCO, 1.3f), q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
        }
        if (t % 3 == 0) {
            Vec3 q = mao.lerp(peito, (t % 15) / 15.0);
            l.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0, 0, 0, 0.01);
        }
        if (t >= 20 && t < 95) {
            // espiral branca subindo em volta do alvo, e a Morte saindo
            for (int k = 0; k < 3; k++) {
                double a = t * 0.4 + k * Math.PI * 2 / 3, r = 0.9;
                double y = (t - 20) % 25 / 25.0 * 2.2;
                l.sendParticles(new DustParticleOptions(BRANCO, 1.6f), pa.x + Math.cos(a) * r, pa.y + y, pa.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            if (t % 3 == 0) {
                l.sendParticles(ParticleTypes.SMOKE, pa.x, pa.y + 1.0, pa.z, 4, 0.25, 0.5, 0.25, 0.03);
                l.sendParticles(ParticleTypes.SCULK_SOUL, pa.x, pa.y + 1.2, pa.z, 1, 0.2, 0.4, 0.2, 0.03);
            }
            if (t % 15 == 0) l.playSound(null, alvo.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.4f, 0.8f + (t - 20) / 90f);
        }
        if (t == 30) titulo(alvo, "purificar.lanterna.t1", "purificar.lanterna.s1");
        if (t == 95) {
            // a explosão branca
            l.playSound(null, alvo.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.6f);
            l.playSound(null, alvo.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1f, 1.2f);
            l.playSound(null, alvo.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5f, 1.5f);
            l.sendParticles(ParticleTypes.FLASH, pa.x, pa.y + 1, pa.z, 2, 0, 0, 0, 0);
            l.sendParticles(ParticleTypes.END_ROD, pa.x, pa.y + 1, pa.z, 220, 0.3, 0.6, 0.3, 0.45);
            l.sendParticles(new DustParticleOptions(BRANCO, 2.5f), pa.x, pa.y + 1, pa.z, 160, 1.6, 1.4, 1.6, 0);
            l.sendParticles(ParticleTypes.CLOUD, pa.x, pa.y + 0.5, pa.z, 60, 1.2, 0.3, 1.2, 0.12);
            LoreData d = LoreCapability.get(alvo).orElse(null);
            if (d != null && (d.morte() == 2 || d.morte() == Corrupcao.PORTADOR)) {
                // o anel volta à Tropa e à cor de antes (o do Portador Negro também)
                if (d.morte() == Corrupcao.PORTADOR) d.setMorte(2);
                Corrupcao.purificar(alvo, d);
            }
            titulo(alvo, "purificar.lanterna.t2", "purificar.lanterna.s2");
            for (ServerPlayer o : dono.server.getPlayerList().getPlayers())
                if (o != alvo) o.sendSystemMessage(Component.translatable("purificar.lanterna.servidor", dono.getDisplayName(), alvo.getDisplayName()).withStyle(s -> s.withColor(0xF4F4FF).withItalic(true)));
        }
    }

    private static void acabar(UUID dono) {
        Cena c = CENAS.remove(dono);
        TEMPO.remove(dono);
        if (c == null) return;
        c.alvoRef.removeEffect(MobEffects.GLOWING);
        c.alvoRef.fallDistance = 0;
        c.donoRef.removeEffect(MobEffects.GLOWING);
    }

    public static boolean emCena(ServerPlayer p) {
        if (CENAS.containsKey(p.getUUID())) return true;
        for (Cena c : CENAS.values()) if (c.alvo.equals(p.getUUID())) return true;
        return false;
    }

    @SubscribeEvent
    public static void saiu(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID u = e.getEntity().getUUID();
        acabar(u);
        for (var en : new java.util.ArrayList<>(CENAS.entrySet())) if (en.getValue().alvo.equals(u)) acabar(en.getKey());
    }

    private static void titulo(ServerPlayer p, String t, String s) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(8, 40, 15));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(s).withStyle(st -> st.withColor(0xBFC6D0).withItalic(true))));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(t).withStyle(st -> st.withColor(0xFFFFFF).withBold(true))));
    }

    /** Quem pode ser purificado pelo Anel Branco (para a roda avisar). */
    public static boolean podeSerPurificado(ServerPlayer o) {
        if (ModCapabilities.get(o).map(r -> r.hasRing() && Supremos.supremo(r.corps()) && !Supremos.preto(r.corps())).orElse(false)) return false;
        return LoreCapability.get(o).map(x -> x.morte() == 2 || x.morte() == Corrupcao.PORTADOR).orElse(false);
    }

    private PurificacaoBranca() {}
}
