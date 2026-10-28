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
 * Ligar o anel: o uniforme se espalha a partir da mão do anel (estilo nanotecnologia).
 * Desligar: ele recolhe de volta para o anel, na ordem inversa.
 */
public final class UniformTransition {
    /** Velocidade da transformação por tick (1 / 0.055 ≈ 18 ticks ≈ 0,9 s). */
    private static final float SPEED = 0.055f;

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

    /** Partículas na "frente" da onda: começa na mão, sobe pelo braço, desce pelo corpo. */
    private static void spawnFront(Player player, float p, int color) {
        var rnd = player.getRandom();
        double height = p < 0.35 ? Mth.lerp(p / 0.35, 0.7, 1.4) : Mth.lerp((p - 0.35) / 0.65, 1.6, 0.0);
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        for (int i = 0; i < 5; i++) {
            double sideways = p < 0.35 ? 0.35 : (rnd.nextDouble() - 0.5) * 0.7;
            Vec3 pos = player.position().add(right.scale(sideways)).add(0, height + (rnd.nextDouble() - 0.5) * 0.15, 0)
                    .add((rnd.nextDouble() - 0.5) * 0.3, 0, (rnd.nextDouble() - 0.5) * 0.3);
            player.level().addParticle(GlowOrbOptions.of(color, 0.08f + rnd.nextFloat() * 0.08f),
                    pos.x, pos.y, pos.z, 0, 0.01, 0);
        }
    }

    public static String corpsOf(Player player) {
        var ring = ClientVisibleRings.of(player.getId()).ring();
        return ring.isEmpty() ? "green" : PowerRingItem.getCorps(ring).getPath();
    }

    public static void clear() {
        NOW.clear();
        OLD.clear();
    }

    private UniformTransition() {}
}
