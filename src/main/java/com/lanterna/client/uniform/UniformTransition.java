package com.lanterna.client.uniform;

import com.lanterna.client.ClientVisibleRings;
import com.lanterna.item.PowerRingItem;
import com.lanterna.particle.GlowOrbOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Progresso da transformação de cada jogador (0 = roupa normal, 1 = uniforme completo).
 * Ligar o anel: o uniforme se forma pixel por pixel, subindo dos pés até a cabeça.
 * Desligar: ele se desfaz da cabeça para os pés.
 */
public final class UniformTransition {
    /** Velocidade da transformação por tick (1 / 0.028 ≈ 36 ticks ≈ 1,8 s). */
    private static final float SPEED = 0.028f;

    private static final Map<Integer, Float> NOW = new HashMap<>();
    private static final Map<Integer, Float> OLD = new HashMap<>();

    public static void tick(Player player) {
        int id = player.getId();
        ClientVisibleRings.Visible ring = ClientVisibleRings.of(id);
        float target = ring.active() ? 1f : 0f;
        float old = NOW.getOrDefault(id, target == 1f ? 0f : 0f);
        OLD.put(id, old);
        if (old == target) {
            if (target == 0f) { NOW.remove(id); OLD.remove(id); }
            return;
        }

        // Começando a transformar: som metálico + energia
        if ((target == 1f && old == 0f) || (target == 0f && old == 1f)) {
            player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                    target == 1f ? SoundEvents.ARMOR_EQUIP_NETHERITE : SoundEvents.ARMOR_EQUIP_CHAIN,
                    SoundSource.PLAYERS, 1.0f, 1.3f, false);
            player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5f, 1.8f, false);
        }

        float next = target > old ? Math.min(1f, old + SPEED) : Math.max(0f, old - SPEED);
        NOW.put(id, next);
        spawnFront(player, next, ring.color());
    }

    public static float progress(Player player, float partialTick) {
        Float now = NOW.get(player.getId());
        if (now == null) return 0f;
        return Mth.lerp(partialTick, OLD.getOrDefault(player.getId(), now), now);
    }

    public static boolean transitioning(Player player) {
        Float now = NOW.get(player.getId());
        return now != null && now > 0f && now < 1f;
    }

    /** Anel de faíscas subindo junto com a frente de energia (na altura onde os pixels estão se formando). */
    private static void spawnFront(Player player, float p, int color) {
        // em primeira pessoa as bolinhas nasceriam dentro da câmera (quadrados enormes): a tela mostra a transformação
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (player == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        var rnd = player.getRandom();
        // faíscas onde o líquido está chegando agora (na beirada da frente)
        var pts = FluxoUniforme.pontos();
        double yaw = Math.toRadians(player.yBodyRot);
        // direita e frente do jogador no mundo
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw), fx = -Math.sin(yaw), fz = Math.cos(yaw);
        int feitos = 0;
        for (int tent = 0; tent < 60 && feitos < 5; tent++) {
            float[] q = pts.get(rnd.nextInt(pts.size()));
            if (Math.abs(q[3] - p) > 0.035f) continue;
            // modelo (Blockbench): +x é a esquerda do jogador, -z é a frente
            double esq = q[0] / 16.0 * 1.06, frente = -q[2] / 16.0 * 1.06, ly = q[1] / 16.0;
            double wx = -esq * rx + frente * fx, wz = -esq * rz + frente * fz;
            player.level().addParticle(GlowOrbOptions.of(color, 0.04f + rnd.nextFloat() * 0.05f),
                    player.getX() + wx, player.getY() + ly * 0.9375 * (player.isCrouching() ? 0.85 : 1), player.getZ() + wz, 0, 0.003, 0);
            feitos++;
        }
    }

    public static String corpsOf(Player player) {
        var ring = ClientVisibleRings.of(player.getId()).ring();
        return ring.isEmpty() ? "green" : PowerRingItem.getCorps(ring).getPath();
    }

    public static void clear() {
        UniformDissolve.clear();
        PeleTraje.limpar();
        UniformDissolve3D.clear();
        NOW.clear();
        OLD.clear();
    }

    private UniformTransition() {}
}
