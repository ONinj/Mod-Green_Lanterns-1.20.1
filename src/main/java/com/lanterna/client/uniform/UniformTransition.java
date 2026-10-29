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
    /** Velocidade da transformação por tick (1 / 0.045 ≈ 22 ticks ≈ 1,1 s). */
    private static final float SPEED = 0.045f;

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
        var rnd = player.getRandom();
        double height = p * 1.95;
        for (int i = 0; i < 6; i++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            double radius = 0.32 + rnd.nextDouble() * 0.08;
            player.level().addParticle(GlowOrbOptions.of(color, 0.05f + rnd.nextFloat() * 0.06f),
                    player.getX() + Math.cos(angle) * radius, player.getY() + height + (rnd.nextDouble() - 0.5) * 0.1,
                    player.getZ() + Math.sin(angle) * radius, 0, 0.004, 0);
        }
    }

    public static String corpsOf(Player player) {
        var ring = ClientVisibleRings.of(player.getId()).ring();
        return ring.isEmpty() ? "green" : PowerRingItem.getCorps(ring).getPath();
    }

    public static void clear() {
        UniformDissolve.clear();
        UniformDissolve3D.clear();
        NOW.clear();
        OLD.clear();
    }

    private UniformTransition() {}
}
