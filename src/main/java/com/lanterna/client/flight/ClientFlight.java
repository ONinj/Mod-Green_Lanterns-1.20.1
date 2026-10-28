package com.lanterna.client.flight;

import com.lanterna.client.ClientRingData;
import com.lanterna.client.ClientVisibleRings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import com.lanterna.particle.GlowOrbOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Voo do anel, no cliente.
 *
 * Controles:
 *   W / S        -> voa para onde está olhando (inclusive para cima e para baixo)
 *   A / D        -> desliza para os lados
 *   Espaço       -> sobe
 *   Shift        -> desce
 *   Correr (Ctrl)-> velocidade máxima
 *   Parado       -> fica pairando no ar
 *
 * Também guarda, para cada jogador visível, o quanto ele está "deitado" (0 = em pé, 1 = deitado),
 * usado pela pose de voo.
 */
public final class ClientFlight {
    // Velocidades em blocos por tick (x20 = blocos por segundo)
    private static final double CRUISE_SPEED = 0.6;   // 12 blocos/s
    private static final double BOOST_SPEED = 1.35;   // 27 blocos/s
    private static final double VERTICAL_SPEED = 0.45;
    private static final double ACCELERATION = 0.12;
    private static final double BRAKING = 0.2;

    /** Velocidade que o anel está dando (guardada aqui para a gravidade não interferir). */
    private static Vec3 velocity = Vec3.ZERO;

    private static final Map<Integer, Float> TILT = new HashMap<>();
    private static final Map<Integer, Float> TILT_OLD = new HashMap<>();

    // ===================== Física do jogador local =====================

    public static void tickLocalPlayer(LocalPlayer player) {
        if (!ClientRingData.hasRing || !ClientRingData.flight || !ClientRingData.active
                || player.isSpectator() || player.isPassenger()) {
            velocity = player.getDeltaMovement(); // ao ligar o voo, começa com o embalo atual
            return;
        }

        // Nada de voo do criativo: o anel controla tudo
        if (player.getAbilities().flying && !player.isCreative()) {
            player.getAbilities().flying = false;
        }
        player.fallDistance = 0;

        float forward = player.input.forwardImpulse;
        float strafe = player.input.leftImpulse;
        boolean up = player.input.jumping;
        boolean down = player.input.shiftKeyDown;
        boolean boost = Minecraft.getInstance().options.keySprint.isDown();

        Vec3 look = player.getLookAngle();
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));

        Vec3 dir = look.scale(forward).add(left.scale(strafe));
        double speed = boost ? BOOST_SPEED : CRUISE_SPEED;
        Vec3 target = dir.lengthSqr() > 1.0E-4 ? dir.normalize().scale(speed) : Vec3.ZERO;
        double vertical = (up ? VERTICAL_SPEED : 0) - (down ? VERTICAL_SPEED : 0);
        target = target.add(0, vertical, 0);

        // Bateu em algo: zera a velocidade naquela direção
        Vec3 current = velocity;
        if (player.horizontalCollision) current = new Vec3(0, current.y, 0);
        if (player.verticalCollision) current = new Vec3(current.x, 0, current.z);

        double rate = target.lengthSqr() > 1.0E-4 ? ACCELERATION : BRAKING;
        Vec3 next = current.add(target.subtract(current).scale(rate));

        // Pairando parado: um balanço bem leve
        if (target.lengthSqr() < 1.0E-4) {
            next = new Vec3(next.x, next.y + Mth.sin(player.tickCount * 0.1f) * 0.004, next.z);
        }
        // A gravidade do Minecraft só é aplicada depois do movimento deste tick,
        // e no próximo tick a gente recomeça da velocidade guardada, então ela não puxa para baixo
        velocity = next;
        player.setDeltaMovement(next);
    }

    public static boolean isFlying(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player) return ClientRingData.hasRing && ClientRingData.flight && ClientRingData.active;
        return ClientVisibleRings.of(player.getId()).flying();
    }

    // ===================== Inclinação do corpo (todos os jogadores) =====================

    /** Roda uma vez por tick no cliente para cada jogador do mundo. */
    public static void tickPose(Player player) {
        int id = player.getId();
        float old = TILT.getOrDefault(id, 0f);
        TILT_OLD.put(id, old);

        float target = 0f;
        if (isFlying(player) && !player.onGround()) {
            double dx = player.getX() - player.xo;
            double dy = player.getY() - player.yo;
            double dz = player.getZ() - player.zo;
            double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
            target = (float) Mth.clamp((speed - 0.12) / 0.3, 0, 1);

            // O corpo acompanha para onde a cabeça olha
            player.yBodyRot = player.getYHeadRot();
            player.yBodyRotO = player.yHeadRotO;

            spawnTrail(player, target);
        }

        float next = old + (target - old) * 0.18f;
        if (next < 0.001f && target == 0f) {
            TILT.remove(id);
        } else {
            TILT.put(id, next);
        }
    }

    /** 0 = em pé, 1 = deitado (suavizado entre ticks). */
    public static float tilt(Player player, float partialTick) {
        int id = player.getId();
        Float now = TILT.get(id);
        if (now == null) return 0f;
        return Mth.lerp(partialTick, TILT_OLD.getOrDefault(id, now), now);
    }

    /**
     * Rastro de energia: bolinhas brilhantes saindo ao longo do corpo inteiro
     * (cabeça até os pés) e ficando para trás conforme o jogador avança.
     */
    private static void spawnTrail(Player player, float tilt) {
        if (tilt < 0.25f) return;
        Minecraft mc = Minecraft.getInstance();
        int color = player == mc.player ? ClientRingData.color : ClientVisibleRings.of(player.getId()).color();

        Vec3 look = player.getLookAngle();
        Vec3 center = player.position().add(0, 0.9, 0);
        double speed = player.position().subtract(player.xo, player.yo, player.zo).length();
        int count = speed > 0.9 ? 9 : 5;
        var rnd = player.getRandom();

        for (int i = 0; i < count; i++) {
            // Ponto ao longo do corpo deitado (-0.9 = pés, +0.9 = cabeça), mais concentrado nos pés
            double along = -0.95 + Math.pow(rnd.nextDouble(), 1.6) * 1.8;
            Vec3 p = center.add(look.scale(along)).add(
                    (rnd.nextDouble() - 0.5) * 0.45, (rnd.nextDouble() - 0.5) * 0.45, (rnd.nextDouble() - 0.5) * 0.45);
            // Variação de brilho: algumas mais claras (quase brancas), outras na cor pura
            int c = rnd.nextInt(6) == 0 ? lighten(color, 0.45f) : color;
            float size = 0.10f + rnd.nextFloat() * 0.16f;
            Vec3 drift = look.scale(-0.015).add(0, 0.003, 0);
            player.level().addParticle(GlowOrbOptions.of(c, size), p.x, p.y, p.z, drift.x, drift.y, drift.z);
        }
    }

    private static int lighten(int rgb, float t) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r = (int) (r + (255 - r) * t);
        g = (int) (g + (255 - g) * t);
        b = (int) (b + (255 - b) * t);
        return (r << 16) | (g << 8) | b;
    }

    public static void clear() {
        TILT.clear();
        TILT_OLD.clear();
    }

    private ClientFlight() {}
}
