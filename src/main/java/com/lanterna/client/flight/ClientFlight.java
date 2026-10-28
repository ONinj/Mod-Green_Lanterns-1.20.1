package com.lanterna.client.flight;

import com.lanterna.client.ClientRingData;
import com.lanterna.client.ClientVisibleRings;
import com.lanterna.particle.GlowOrbOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Voo do anel, no cliente.
 *
 * Física (feita para "sentir" o voo):
 *  - EMBALO: o anel empurra para onde você olha; a velocidade vai crescendo, não é instantânea.
 *  - CURVAS: ao virar o mouse, o corpo vira na hora, mas o embalo continua indo para o lado antigo
 *    e vai sendo puxado aos poucos para a nova direção (derrapagem). Quanto mais rápido, mais larga a curva.
 *  - MERGULHO: olhando para baixo você ganha velocidade extra; subindo, perde um pouco.
 *  - SOLTAR o W: você vai freando no ar até pairar.
 *  - INCLINAÇÃO: nas curvas o corpo deita de lado (como um avião), e a câmera inclina junto.
 *
 * Controles: W/S frente e freio · A/D desliza · Espaço sobe · Shift desce · Ctrl impulso.
 */
public final class ClientFlight {
    // Velocidades em blocos por tick (x20 = blocos por segundo)
    private static final double MAX_CRUISE = 0.75;   // 15 blocos/s
    private static final double MAX_BOOST = 1.6;     // 32 blocos/s
    private static final double THRUST = 0.045;
    private static final double THRUST_BOOST = 0.085;
    private static final double STRAFE = 0.03;
    private static final double LIFT = 0.045;
    private static final double DRAG = 0.985;        // resistência do ar com o anel empurrando
    private static final double IDLE_DRAG = 0.9;     // sem apertar nada: vai freando até pairar
    private static final double TAKEOFF = 0.75;      // arrancada ao ligar o voo no chão

    /** Velocidade que o anel está dando (guardada aqui para a gravidade não interferir). */
    private static Vec3 velocity = Vec3.ZERO;
    private static boolean wasFlying;
    private static int landTicks;
    private static final Map<Integer, Double> LAST_SPEED = new HashMap<>();
    private static FlightWindSound wind;

    private static final Map<Integer, Float> TILT = new HashMap<>();
    private static final Map<Integer, Float> TILT_OLD = new HashMap<>();
    private static final Map<Integer, Float> BANK = new HashMap<>();
    private static final Map<Integer, Float> BANK_OLD = new HashMap<>();
    private static final Map<Integer, Float> LAST_YAW = new HashMap<>();

    // ===================== Física do jogador local =====================

    public static void tickLocalPlayer(LocalPlayer player) {
        boolean flying = ClientRingData.hasRing && ClientRingData.flight && ClientRingData.active
                && !player.isSpectator() && !player.isPassenger();
        if (!flying) {
            velocity = player.getDeltaMovement(); // ao ligar o voo, começa com o embalo atual
            wasFlying = false;
            return;
        }

        // Nada de voo do criativo: o anel controla tudo
        if (player.getAbilities().flying) player.getAbilities().flying = false;
        player.fallDistance = 0;

        // Arrancada ao ligar o voo
        if (!wasFlying) {
            wasFlying = true;
            velocity = player.getDeltaMovement();
            if (player.onGround()) {
                velocity = new Vec3(velocity.x, TAKEOFF, velocity.z);
                burst(player);
            }
            startWind(player);
        }

        float forward = player.input.forwardImpulse;
        float strafe = player.input.leftImpulse;
        boolean up = player.input.jumping;
        boolean down = player.input.shiftKeyDown;

        // Pousar: segurando Shift encostado no chão por meio segundo, o voo desliga sozinho
        if (down && player.onGround()) {
            if (++landTicks == 10) {
                com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.network.ToggleFlightPacket());
            }
        } else {
            landTicks = 0;
        }
        boolean boost = Minecraft.getInstance().options.keySprint.isDown() && forward > 0;

        Vec3 look = player.getLookAngle();
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));

        Vec3 v = velocity;
        if (player.horizontalCollision) v = new Vec3(v.x * 0.2, v.y, v.z * 0.2);
        // Só zera a vertical se estiver indo CONTRA o chão ou o teto (senão a arrancada não sai)
        if (player.verticalCollision && ((v.y < 0 && player.onGround()) || (v.y > 0 && !player.onGround()))) {
            v = new Vec3(v.x, 0, v.z);
        }

        // No espaço não há ar: o anel voa 4x mais rápido
        double spaceBoost = player.level().dimension().equals(com.lanterna.space.SpaceBodies.SPACE) ? 4.0 : 1.0;
        double speed = v.length();
        double maxSpeed = (boost ? MAX_BOOST : MAX_CRUISE) * spaceBoost;
        boolean anyInput = forward != 0 || strafe != 0 || up || down;

        if (forward > 0) {
            // Empurra para onde está olhando
            double thrust = (boost ? THRUST_BOOST : THRUST) * (spaceBoost > 1 ? 3.0 : 1.0);
            // Mergulho dá velocidade; subir custa um pouco
            thrust += -look.y * 0.02;
            v = v.add(look.scale(thrust * forward));

            // Curva: parte do embalo lateral é "puxada" para a nova direção, o resto escorrega
            Vec3 along = look.scale(v.dot(look));
            Vec3 lateral = v.subtract(along);
            double fast = Mth.clamp(speed / (MAX_BOOST * spaceBoost), 0, 1);
            double grip = Mth.lerp(fast, 0.16, 0.06);          // devagar: vira fácil; rápido: curva larga
            Vec3 carried = look.scale(lateral.length() * grip * 0.75); // parte vira embalo para frente
            v = along.add(lateral.scale(1 - grip)).add(carried);
            v = v.scale(DRAG);
        } else if (forward < 0) {
            // S: freio forte (e um leve recuo quando quase parado)
            v = v.scale(0.88).add(look.scale(-0.012));
        }

        if (strafe != 0) v = v.add(left.scale(STRAFE * strafe));
        if (up) v = v.add(0, LIFT, 0);
        if (down) v = v.add(0, -LIFT, 0);
        if (!anyInput) v = v.scale(IDLE_DRAG);
        if (forward <= 0) v = v.scale(0.97);

        // Limite suave (o mergulho pode passar um pouco do limite)
        double limit = maxSpeed + Math.max(0, -look.y) * 0.35;
        double len = v.length();
        if (len > limit) v = v.scale(Mth.lerp(0.12, len, limit) / len);

        // Pairando parado: um balanço bem leve, para parecer vivo
        if (v.lengthSqr() < 0.002) {
            v = new Vec3(v.x, v.y + Mth.sin(player.tickCount * 0.1f) * 0.0035, v.z);
        }

        // A gravidade do Minecraft só é aplicada depois do movimento deste tick,
        // e no próximo tick a gente recomeça da velocidade guardada, então ela não puxa para baixo
        velocity = v;
        player.setDeltaMovement(v);
        updateWind(v.length());
    }

    private static void burst(Player player) {
        int color = ClientRingData.color;
        var rnd = player.getRandom();
        for (int i = 0; i < 24; i++) {
            double a = i * (Math.PI * 2 / 24);
            player.level().addParticle(GlowOrbOptions.of(color, 0.18f + rnd.nextFloat() * 0.12f),
                    player.getX() + Math.cos(a) * 0.4, player.getY() + 0.1, player.getZ() + Math.sin(a) * 0.4,
                    Math.cos(a) * 0.18, 0.02, Math.sin(a) * 0.18);
        }
    }

    private static void startWind(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (wind == null || wind.isStopped()) {
            wind = new FlightWindSound((LocalPlayer) player);
            mc.getSoundManager().play(wind);
        }
    }

    private static void updateWind(double speed) {
        if (wind != null) wind.setSpeed(speed);
    }

    /** Velocidade atual do voo local (para o FOV). */
    public static double localSpeed() {
        return wasFlying ? velocity.length() : 0;
    }

    public static boolean isFlying(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player) return ClientRingData.hasRing && ClientRingData.flight && ClientRingData.active;
        return ClientVisibleRings.of(player.getId()).flying();
    }

    // ===================== Pose: inclinação e "asa" (todos os jogadores) =====================

    /** Roda uma vez por tick no cliente para cada jogador do mundo. */
    public static void tickPose(Player player) {
        int id = player.getId();
        float old = TILT.getOrDefault(id, 0f);
        float oldBank = BANK.getOrDefault(id, 0f);
        TILT_OLD.put(id, old);
        BANK_OLD.put(id, oldBank);

        float target = 0f;
        float targetBank = 0f;
        float yaw = player.getYRot();
        float yawRate = Mth.wrapDegrees(yaw - LAST_YAW.getOrDefault(id, yaw));
        LAST_YAW.put(id, yaw);

        if (isFlying(player) && !player.onGround()) {
            Vec3 move = player.position().subtract(player.xo, player.yo, player.zo);
            Vec3 look = player.getLookAngle();
            // Só deita quando está indo PARA FRENTE (subindo reto com Espaço fica em pé)
            double forwardSpeed = Math.max(0, move.dot(look));
            // Inclina aos poucos com a velocidade (devagar: só um leve inclinar para frente)
            target = (float) Mth.clamp((forwardSpeed - 0.03) / 0.4, 0, 1);

            // Inclina de lado nas curvas, mais forte em alta velocidade
            float fast = (float) Mth.clamp(move.length() / 1.0, 0.2, 1.3);
            targetBank = Mth.clamp(-yawRate * 2.6f * fast, -60f, 60f) * target;

            // Deslizar de lado (A/D) também inclina o corpo, como quem corta o vento
            Vec3 side = move.subtract(look.scale(move.dot(look)));
            Vec3 right = look.cross(new Vec3(0, 1, 0));
            double lateral = right.lengthSqr() > 1.0E-4 ? side.dot(right.normalize()) : 0;
            targetBank += (float) Mth.clamp(lateral * 70.0, -30, 30);

            effects(player, move, look);

            // O corpo acompanha para onde a cabeça olha
            player.yBodyRot = player.getYHeadRot();
            player.yBodyRotO = player.yHeadRotO;

            spawnTrail(player, target, move.length());
        }

        float next = old + (target - old) * 0.18f;
        float nextBank = oldBank + (targetBank - oldBank) * 0.2f;
        if (next < 0.001f && target == 0f && Math.abs(nextBank) < 0.1f) {
            TILT.remove(id);
            BANK.remove(id);
        } else {
            TILT.put(id, next);
            BANK.put(id, nextBank);
        }
    }

    /** 0 = em pé, 1 = deitado (suavizado entre ticks). */
    public static float tilt(Player player, float partialTick) {
        Float now = TILT.get(player.getId());
        if (now == null) return 0f;
        return Mth.lerp(partialTick, TILT_OLD.getOrDefault(player.getId(), now), now);
    }

    /** Inclinação lateral nas curvas, em graus (suavizada entre ticks). */
    public static float bank(Player player, float partialTick) {
        Float now = BANK.get(player.getId());
        if (now == null) return 0f;
        return Mth.lerp(partialTick, BANK_OLD.getOrDefault(player.getId(), now), now);
    }

    // ===================== Efeitos do voo (todos os jogadores) =====================

    private static final double SONIC_SPEED = 1.45;

    private static void effects(Player player, Vec3 move, Vec3 look) {
        double speed = move.length();
        double last = LAST_SPEED.getOrDefault(player.getId(), 0.0);
        LAST_SPEED.put(player.getId(), speed);
        Minecraft mc = Minecraft.getInstance();
        int color = player == mc.player ? ClientRingData.color : ClientVisibleRings.of(player.getId()).color();
        var rnd = player.getRandom();

        // Estrondo sônico: ao passar da velocidade do som, um anel de energia e um "BUM"
        if (speed >= SONIC_SPEED && last < SONIC_SPEED) {
            Vec3 center = player.position().add(0, 0.9, 0);
            Vec3 a = look.cross(new Vec3(0, 1, 0));
            if (a.lengthSqr() < 1.0E-4) a = new Vec3(1, 0, 0);
            a = a.normalize();
            Vec3 b = a.cross(look).normalize();
            for (int i = 0; i < 40; i++) {
                double ang = i * (Math.PI * 2 / 40);
                Vec3 dir = a.scale(Math.cos(ang)).add(b.scale(Math.sin(ang)));
                Vec3 p = center.add(dir.scale(0.6));
                player.level().addParticle(GlowOrbOptions.of(lighten(color, 0.3f), 0.3f),
                        p.x, p.y, p.z, dir.x * 0.35, dir.y * 0.35, dir.z * 0.35);
            }
            player.level().playLocalSound(center.x, center.y, center.z,
                    net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, net.minecraft.sounds.SoundSource.PLAYERS,
                    2.0f, 0.55f, false);
            if (player == mc.player) shake = 8;
        }

        // Rasante: perto do chão em alta velocidade, levanta poeira (ou espirra água)
        if (speed > 0.55 && player.tickCount % 2 == 0) {
            net.minecraft.core.BlockPos.MutableBlockPos pos = player.blockPosition().mutable();
            for (int dy = 0; dy < 5; dy++) {
                pos.setY(player.blockPosition().getY() - dy - 1);
                var state = player.level().getBlockState(pos);
                var fluid = player.level().getFluidState(pos);
                if (!fluid.isEmpty()) {
                    for (int i = 0; i < 6; i++) {
                        player.level().addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH,
                                player.getX() + (rnd.nextDouble() - 0.5) * 2, pos.getY() + 1.05, player.getZ() + (rnd.nextDouble() - 0.5) * 2,
                                -move.x * 0.3, 0.25, -move.z * 0.3);
                    }
                    break;
                }
                if (!state.isAir()) {
                    var particle = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, state);
                    for (int i = 0; i < 5; i++) {
                        player.level().addParticle(particle,
                                player.getX() + (rnd.nextDouble() - 0.5) * 2.5, pos.getY() + 1.1, player.getZ() + (rnd.nextDouble() - 0.5) * 2.5,
                                (rnd.nextDouble() - 0.5) * 0.4 - move.x * 0.2, 0.15 + rnd.nextDouble() * 0.2, (rnd.nextDouble() - 0.5) * 0.4 - move.z * 0.2);
                    }
                    break;
                }
            }
        }
    }

    /** Tremor da câmera (estrondo e altíssima velocidade). */
    private static int shake;

    public static float shakeAmount() {
        float base = (float) Mth.clamp((localSpeed() - 1.2) * 0.6, 0, 0.35);
        return base + (shake > 0 ? shake * 0.12f : 0);
    }

    public static void tickShake() {
        if (shake > 0) shake--;
    }

    // ===================== Rastro =====================

    /**
     * Rastro de energia: bolinhas brilhantes saindo ao longo do corpo inteiro
     * (cabeça até os pés) e ficando para trás conforme o jogador avança.
     */
    private static void spawnTrail(Player player, float tilt, double speed) {
        if (tilt < 0.25f) return;
        Minecraft mc = Minecraft.getInstance();
        int color = player == mc.player ? ClientRingData.color : ClientVisibleRings.of(player.getId()).color();

        Vec3 look = player.getLookAngle();
        Vec3 center = player.position().add(0, 0.9, 0);
        int count = speed > 0.9 ? 9 : 5;
        var rnd = player.getRandom();

        for (int i = 0; i < count; i++) {
            // Ponto ao longo do corpo deitado (-0.9 = pés, +0.9 = cabeça), mais concentrado nos pés
            double along = -0.95 + Math.pow(rnd.nextDouble(), 1.6) * 1.8;
            Vec3 p = center.add(look.scale(along)).add(
                    (rnd.nextDouble() - 0.5) * 0.45, (rnd.nextDouble() - 0.5) * 0.45, (rnd.nextDouble() - 0.5) * 0.45);
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
        BANK.clear();
        BANK_OLD.clear();
        LAST_YAW.clear();
        LAST_SPEED.clear();
        velocity = Vec3.ZERO;
        wasFlying = false;
        if (wind != null) wind.finish();
        wind = null;
    }

    private ClientFlight() {}
}
