package com.lanterna.client.flight;

import com.lanterna.client.ClientRingData;
import com.lanterna.client.ClientVisibleRings;
import com.lanterna.network.FlightModePacket;
import com.lanterna.network.ModNetwork;
import com.lanterna.particle.GlowOrbOptions;
import com.lanterna.space.SpaceBodies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Voo do anel, no cliente.
 *
 * Física (feita para "sentir" o voo):
 *  - EMBALO: o anel empurra para onde você olha; a velocidade vai crescendo, não é instantânea.
 *  - CURVAS: o embalo continua indo para o lado antigo e vai sendo puxado aos poucos para a nova
 *    direção (derrapagem). Quanto mais rápido, mais larga a curva.
 *  - MERGULHO: olhando para baixo você ganha velocidade; subindo, perde um pouco.
 *  - FREIO AERODINÂMICO: S em alta velocidade "abre" o corpo contra o vento e freia forte.
 *  - PARAFUSO: dois toques rápidos em A ou D giram o corpo e esquivam para o lado.
 *  - IMPACTO: bater numa parede em alta velocidade faz quicar, tremer e levantar pedaços do bloco.
 *  - ÁGUA: dentro d'água o voo fica pesado; sair dela rápido levanta um jato d'água.
 *  - TURBULÊNCIA: no ar, em alta velocidade, o vento sacode de leve.
 *  - ESPAÇO: sem ar não há freio — você continua deslizando (inércia), e os planetas puxam de leve.
 *  - HIPERPROPULSÃO (só no espaço): segurando Ctrl+W em velocidade máxima por um instante,
 *    o anel rompe a barreira e dispara a quase 2.000 blocos por segundo. Freia sozinho perto dos planetas.
 *
 * Controles: W/S frente e freio · A/D desliza (dois toques: parafuso) · Espaço sobe · Shift desce · Ctrl impulso.
 */
public final class ClientFlight {
    // Velocidades em blocos por tick (x20 = blocos por segundo)
    private static final double MAX_CRUISE = 0.75;   // 15 blocos/s
    private static final double MAX_BOOST = 1.6;     // 32 blocos/s
    private static final double SPACE_CRUISE = 3.0;  // 60 blocos/s
    private static final double SPACE_BOOST = 6.4;   // 128 blocos/s
    public static final double HYPER_MAX = 90.0;     // 1.800 blocos/s
    private static final double THRUST = 0.045;
    private static final double THRUST_BOOST = 0.085;
    private static final double STRAFE = 0.03;
    private static final double LIFT = 0.045;
    private static final double DRAG = 0.985;        // resistência do ar com o anel empurrando
    private static final double IDLE_DRAG = 0.9;     // sem apertar nada: vai freando até pairar
    private static final double SPACE_IDLE_DRAG = 0.993; // no vácuo quase não freia
    private static final double TAKEOFF = 0.75;      // arrancada ao ligar o voo no chão
    private static final int HYPER_CHARGE_TICKS = 30;

    /** Velocidade que o anel está dando (guardada aqui para a gravidade não interferir). */
    private static Vec3 velocity = Vec3.ZERO;
    private static boolean wasFlying;
    private static int landTicks;
    private static double lastLocalSpeed;
    private static boolean wasInWater;
    private static FlightWindSound wind;

    // Hiperpropulsão (jogador local)
    private static float hyperCharge;
    private static boolean hyperOn;
    private static double hyperSpeed;
    private static boolean sentHyper, sentAutopilot;

    // Parafuso (barrel roll)
    private static int rollTicks;
    private static float rollDir;
    private static boolean leftWas, rightWas;
    private static int lastLeftTap = -100, lastRightTap = -100, rollCooldown;
    private static final int ROLL_TIME = 12;

    private static final Map<Integer, Double> LAST_SPEED = new HashMap<>();
    private static final Map<Integer, Float> TILT = new HashMap<>();
    private static final Map<Integer, Float> TILT_OLD = new HashMap<>();
    private static final Map<Integer, Float> BANK = new HashMap<>();
    private static final Map<Integer, Float> BANK_OLD = new HashMap<>();
    private static final Map<Integer, Float> LAST_YAW = new HashMap<>();
    /** Caminho recente de cada jogador (para o rastro de energia em alta velocidade). */
    private static final Map<Integer, Deque<Vec3>> PATH = new HashMap<>();
    public static final int PATH_LENGTH = 28;

    // ===================== Física do jogador local =====================

    public static void tickLocalPlayer(LocalPlayer player) {
        boolean flying = ClientRingData.hasRing && ClientRingData.flight && ClientRingData.active
                && !player.isSpectator() && !player.isPassenger();

        // Mundo novo ainda carregando: fica parado até o chão chegar (senão a tela de carregamento não fecha)
        if (flying && Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen) {
            velocity = Vec3.ZERO;
            player.setDeltaMovement(Vec3.ZERO);
            return;
        }

        // O piloto pode estar esperando o voo ligar
        Autopilot.Control auto = null;
        if (Autopilot.active()) {
            if (player.input.forwardImpulse < 0 && Autopilot.phase() != Autopilot.Phase.WAIT_FLIGHT) {
                Autopilot.stop(Component.translatable("autopilot.lanterna.cancelled"));
            } else if (!ClientRingData.hasRing || !ClientRingData.active || player.isDeadOrDying()) {
                Autopilot.stop(Component.translatable("autopilot.lanterna.cancelled"));
            } else {
                auto = Autopilot.control(player);
            }
        }

        if (!flying) {
            velocity = player.getDeltaMovement(); // ao ligar o voo, começa com o embalo atual
            wasFlying = false;
            dropHyper();
            sendMode();
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
        boolean boost = Minecraft.getInstance().options.keySprint.isDown() && forward > 0;
        boolean hyperAllowed = true;
        if (auto != null) {
            forward = auto.forward();
            strafe = 0;
            up = auto.up();
            down = auto.down();
            boost = auto.boost();
            hyperAllowed = auto.hyperAllowed();
        }

        // Pousar: segurando Shift encostado no chão por meio segundo, o voo desliga sozinho
        if (down && player.onGround() && auto == null) {
            if (++landTicks == 10) ModNetwork.sendToServer(new com.lanterna.network.ToggleFlightPacket());
        } else {
            landTicks = 0;
        }

        boolean inSpace = player.level().dimension().equals(SpaceBodies.SPACE);
        Vec3 look = player.getLookAngle();
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));

        Vec3 v = velocity;
        double speedBefore = v.length();

        // ---- Impacto contra parede em alta velocidade ----
        if (player.horizontalCollision) {
            if (lastLocalSpeed > 0.9 && !hyperOn) impact(player, v);
            v = new Vec3(v.x * 0.2, v.y, v.z * 0.2);
        }
        // Só zera a vertical se estiver indo CONTRA o chão ou o teto (senão a arrancada não sai)
        if (player.verticalCollision && ((v.y < 0 && player.onGround()) || (v.y > 0 && !player.onGround()))) {
            if (v.y < -0.9) impact(player, v);
            v = new Vec3(v.x, 0, v.z);
        }

        // ---- Parafuso: dois toques em A ou D ----
        tickRoll(player);
        if (rollTicks > 0) {
            v = v.add(left.scale(rollDir * (rollTicks > ROLL_TIME - 4 ? 0.22 : 0.0)));
        }

        // ---- Hiperpropulsão (espaço) ----
        double maxBoost = inSpace ? SPACE_BOOST : MAX_BOOST;
        double maxCruise = inSpace ? SPACE_CRUISE : MAX_CRUISE;
        boolean wantHyper = inSpace && boost && forward > 0 && hyperAllowed;
        if (!inSpace) dropHyper();

        if (inSpace && !hyperOn) {
            if (wantHyper && speedBefore > maxBoost * 0.85) {
                hyperCharge += 1f / (auto != null ? HYPER_CHARGE_TICKS / 2f : HYPER_CHARGE_TICKS);
                chargeEffects(player, hyperCharge);
                if (hyperCharge >= 1f) engageHyper(player, v);
            } else {
                hyperCharge = Math.max(0f, hyperCharge - 0.08f);
            }
        }

        if (hyperOn) {
            v = hyperPhysics(player, v, look, left, strafe, up, down, wantHyper);
        } else {
            v = normalPhysics(player, v, look, left, forward, strafe, up, down, boost, inSpace, maxCruise, maxBoost, auto);
        }

        // Teto do espaço: não deixa subir para sempre
        if (inSpace && player.getY() > 1500 && v.y > 0) v = new Vec3(v.x, v.y * 0.5, v.z);

        // A gravidade do Minecraft só é aplicada depois do movimento deste tick,
        // e no próximo tick a gente recomeça da velocidade guardada, então ela não puxa para baixo
        velocity = v;
        player.setDeltaMovement(v);
        lastLocalSpeed = v.length();
        updateWind(Math.min(lastLocalSpeed, 4.0));
        sendMode();
    }

    private static Vec3 normalPhysics(LocalPlayer player, Vec3 v, Vec3 look, Vec3 left, float forward, float strafe,
                                      boolean up, boolean down, boolean boost, boolean inSpace,
                                      double maxCruise, double maxBoost, Autopilot.Control auto) {
        double speed = v.length();
        double maxSpeed = boost ? maxBoost : maxCruise;
        // Piloto automático saindo da atmosfera: sobe como foguete
        if (auto != null && auto.boost() && (Autopilot.phase() == Autopilot.Phase.ASCEND
                || Autopilot.phase() == Autopilot.Phase.DESCEND)) maxSpeed = 2.6;
        boolean anyInput = forward != 0 || strafe != 0 || up || down;
        double thrustScale = inSpace ? 3.0 : 1.0;

        if (forward > 0) {
            double thrust = (boost ? THRUST_BOOST : THRUST) * thrustScale;
            // Mergulho dá velocidade; subir custa um pouco (só onde há gravidade)
            if (!inSpace) thrust += -look.y * 0.02;
            // Empurra forte parado e vai suavizando perto da velocidade máxima (sem "bater no teto")
            double ease = Math.max(0.12, 1 - Math.pow(Mth.clamp(speed / maxSpeed, 0, 1), 2));

            // Curva: o embalo GIRA para onde você olha (como um arco), sem perder a velocidade de uma vez.
            // Devagar vira rápido; muito rápido, a curva fica mais aberta e escorrega um pouco.
            if (speed > 0.04) {
                Vec3 dirV = v.scale(1 / speed);
                double ang = Math.acos(Mth.clamp(dirV.dot(look), -1, 1));
                double fast = Mth.clamp(speed / maxBoost, 0, 1);
                double maxTurn = Math.toRadians(Mth.lerp(fast, 14, 4.5)) * (inSpace ? 0.75 : 1);
                if (ang > 1.0E-4) {
                    double k = Math.min(1, maxTurn / ang);
                    // suaviza o começo e o fim da curva
                    k = k * (0.55 + 0.45 * Math.min(1, ang / Math.toRadians(25)));
                    Vec3 nd = dirV.lerp(look, k).normalize();
                    double loss = 1 - Math.min(0.06, ang * 0.025) * fast;
                    v = nd.scale(speed * loss);
                }
            }
            v = v.add(look.scale(thrust * forward * ease));
            v = v.scale(inSpace ? 0.997 : DRAG);
        } else if (forward < 0) {
            // S: freio. Em alta velocidade é o freio aerodinâmico (corpo contra o vento)
            if (speed > 0.6 && !inSpace) {
                v = v.scale(0.84);
                brakeEffects(player, v);
            } else {
                v = v.scale(inSpace ? 0.9 : 0.88).add(look.scale(-0.012));
            }
        }

        if (strafe != 0) v = v.add(left.scale(STRAFE * strafe * (inSpace ? 2 : 1)));
        // Subir/descer: chega na velocidade vertical aos poucos (sem tranco) e para suave ao soltar
        double vMax = (inSpace ? 1.4 : 0.7) * (boost ? 1.6 : 1);
        if (auto != null) {
            // piloto automático: sobe e desce como antes (rápido, como foguete)
            if (up) v = v.add(0, LIFT * (inSpace ? 2 : 1), 0);
            if (down) v = v.add(0, -LIFT * (inSpace ? 2 : 1), 0);
        } else if (up && !down) v = new Vec3(v.x, v.y + (vMax - v.y) * (inSpace ? 0.09 : 0.12) + LIFT * 0.25, v.z);
        else if (down && !up) v = new Vec3(v.x, v.y + (-vMax - v.y) * (inSpace ? 0.09 : 0.12) - LIFT * 0.25, v.z);
        if (!up && !down && forward == 0 && !inSpace) v = new Vec3(v.x, v.y * 0.86, v.z);
        if (!anyInput) v = v.scale(inSpace ? SPACE_IDLE_DRAG : IDLE_DRAG);
        if (forward <= 0 && !inSpace) v = v.scale(0.97);

        if (inSpace) {
            v = v.add(gravityWell(player.position(), anyInput));
        } else {
            // Água: o voo fica pesado
            if (player.isInWater()) {
                v = v.scale(0.86);
                if (player.tickCount % 2 == 0) bubbles(player);
            } else if (wasInWater && speed > 0.35) {
                waterBurst(player, v);
            }
            // Turbulência: em alta velocidade o vento sacode de leve
            if (speed > 1.0) {
                // ruído suave (ondas), não um tranco aleatório a cada tick
                float tt = player.tickCount;
                double k = (speed - 1.0) * 0.006;
                v = v.add(Mth.sin(tt * 0.31f) * Mth.sin(tt * 0.13f) * k, Mth.sin(tt * 0.23f + 1.7f) * k, Mth.cos(tt * 0.27f) * Mth.sin(tt * 0.11f + 0.6f) * k);
            }
            // Rasante: perto do chão descendo rápido, o ar "amortece" e vira embalo para frente
            if (v.y < -0.4 && forward > 0 && nearGround(player, 3)) {
                double cushion = -v.y * 0.25;
                v = new Vec3(v.x, v.y + cushion, v.z).add(look.multiply(1, 0, 1).scale(cushion * 0.6));
            }
        }
        wasInWater = player.isInWater();

        // Limite suave (o mergulho pode passar um pouco do limite)
        double limit = maxSpeed + (inSpace ? 0 : Math.max(0, -look.y) * 0.35);
        double len = v.length();
        if (len > limit) v = v.scale(Mth.lerp(0.12, len, limit) / len);

        // Pairando parado: um balanço bem leve, para parecer vivo
        if (v.lengthSqr() < 0.002) {
            v = new Vec3(v.x, v.y + Mth.sin(player.tickCount * 0.1f) * 0.0035, v.z);
        }
        return v;
    }

    // ===================== Hiperpropulsão =====================

    private static Vec3 hyperPhysics(LocalPlayer player, Vec3 v, Vec3 look, Vec3 left, float strafe,
                                     boolean up, boolean down, boolean want) {
        double brake = brakeLimit(player.position());
        if (want) {
            double target = Math.min(HYPER_MAX, brake);
            double rate = target > hyperSpeed ? 0.035 : 0.3;
            hyperSpeed += (target - hyperSpeed) * rate + (target > hyperSpeed ? 0.15 : 0);
        } else {
            hyperSpeed *= 0.84;
        }
        hyperSpeed = Math.min(hyperSpeed, brake * 1.1 + 2);

        // Curvas bem abertas: a direção vai virando aos poucos para onde você olha
        Vec3 dir = v.lengthSqr() > 1.0E-4 ? v.normalize() : look;
        double turn = Mth.lerp(Mth.clamp(hyperSpeed / HYPER_MAX, 0, 1), 0.12, 0.045);
        dir = dir.lerp(look, turn).normalize();
        Vec3 out = dir.scale(hyperSpeed);
        if (strafe != 0) out = out.add(left.scale(strafe * 0.6));
        if (up) out = out.add(0, 0.6, 0);
        if (down) out = out.add(0, -0.6, 0);

        if (hyperSpeed < SPACE_BOOST * 0.95) {
            hyperOn = false;
            hyperCharge = 0;
            exitHyper(player);
            return dir.scale(Math.min(hyperSpeed, SPACE_BOOST));
        }
        return out;
    }

    /** Velocidade máxima segura perto dos planetas: freia sozinho ao se aproximar. */
    private static double brakeLimit(Vec3 pos) {
        double limit = HYPER_MAX;
        for (SpaceBodies.Body b : SpaceBodies.near(pos, 1)) {
            if (!b.landable()) continue;
            double surface = pos.distanceTo(b.center()) - b.radius();
            limit = Math.min(limit, Math.max(SPACE_BOOST * 0.9, surface / 14.0));
        }
        return limit;
    }

    /** Puxão leve dos planetas no espaço (só quando você não está acelerando). */
    private static Vec3 gravityWell(Vec3 pos, boolean thrusting) {
        if (thrusting) return Vec3.ZERO;
        Vec3 pull = Vec3.ZERO;
        for (SpaceBodies.Body b : SpaceBodies.near(pos, 1)) {
            if (!b.landable()) continue;
            Vec3 to = b.center().subtract(pos);
            double d = to.length();
            if (d > b.radius() * 4 || d < 1) continue;
            double strength = 0.006 * (b.radius() * b.radius()) / (d * d);
            pull = pull.add(to.scale(strength / d));
        }
        return pull;
    }

    private static void engageHyper(LocalPlayer player, Vec3 v) {
        hyperOn = true;
        hyperCharge = 1f;
        hyperSpeed = Math.max(v.length(), SPACE_BOOST);
        Vec3 center = player.position().add(0, 0.9, 0);
        Vec3 look = player.getLookAngle();
        int color = ClientRingData.color;
        shockRing(player, center.add(look.scale(1.5)), look, color, 64, 0.9);
        player.level().playLocalSound(center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2f, 0.55f, false);
        player.level().playLocalSound(center.x, center.y, center.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 0.6f, false);
        player.level().playLocalSound(center.x, center.y, center.z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.5f, 1.6f, false);
        shake = 14;
        HyperEffects.flash(1f);
        player.displayClientMessage(Component.translatable("flight.lanterna.hyper_on"), true);
    }

    private static void exitHyper(LocalPlayer player) {
        Vec3 c = player.position().add(0, 0.9, 0);
        player.level().playLocalSound(c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 1.4f, false);
        shockRing(player, c, player.getLookAngle(), ClientRingData.color, 32, 0.4);
        HyperEffects.flash(0.5f);
    }

    private static void dropHyper() {
        hyperOn = false;
        hyperCharge = 0;
        hyperSpeed = 0;
    }

    private static void chargeEffects(LocalPlayer player, float charge) {
        var rnd = player.getRandom();
        Vec3 look = player.getLookAngle();
        Vec3 front = player.position().add(0, 0.9, 0).add(look.scale(2.0));
        int color = lighten(ClientRingData.color, 0.3f);
        // Energia se juntando na frente, cada vez mais forte
        for (int i = 0; i < 2 + (int) (charge * 6); i++) {
            Vec3 off = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5).normalize().scale(1.2);
            Vec3 p = front.add(off);
            Vec3 vel = off.scale(-0.12).add(player.getDeltaMovement());
            player.level().addParticle(GlowOrbOptions.of(color, 0.08f + charge * 0.1f), p.x, p.y, p.z, vel.x, vel.y, vel.z);
        }
        if (player.tickCount % 6 == 0) {
            player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT,
                    SoundSource.PLAYERS, 0.6f, 0.8f + charge * 1.2f, false);
        }
    }

    public static boolean hyperOn() { return hyperOn; }

    /** Para quase na hora (fim do pouso do piloto automático). */
    public static void brakeHard() {
        velocity = velocity.scale(0.05);
        dropHyper();
    }

    /**
     * No espaço, em alta velocidade, o jogador continua se movendo mesmo onde os chunks ainda não chegaram
     * (o espaço é vazio). Devagar, ou enquanto o mundo está carregando, segue a regra normal do jogo.
     */
    public static boolean allowUnloadedMove() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.level().dimension().equals(SpaceBodies.SPACE)) return false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen) return false;
        return wasFlying && (hyperOn || velocity.length() > 2.0);
    }
    public static float hyperCharge() { return hyperCharge; }
    public static double hyperFraction() { return hyperOn ? Mth.clamp(hyperSpeed / HYPER_MAX, 0, 1) : 0; }

    private static void sendMode() {
        boolean auto = Autopilot.active();
        if (hyperOn != sentHyper || auto != sentAutopilot) {
            sentHyper = hyperOn;
            sentAutopilot = auto;
            ModNetwork.sendToServer(new FlightModePacket(hyperOn, auto));
        }
    }

    // ===================== Manobras e impactos =====================

    private static void tickRoll(LocalPlayer player) {
        if (rollCooldown > 0) rollCooldown--;
        if (rollTicks > 0) rollTicks--;
        boolean l = player.input.left, r = player.input.right;
        int now = player.tickCount;
        if (l && !leftWas) {
            if (now - lastLeftTap < 7) startRoll(player, 1f);
            lastLeftTap = now;
        }
        if (r && !rightWas) {
            if (now - lastRightTap < 7) startRoll(player, -1f);
            lastRightTap = now;
        }
        leftWas = l;
        rightWas = r;
    }

    private static void startRoll(LocalPlayer player, float dir) {
        if (rollCooldown > 0 || player.onGround()) return;
        rollTicks = ROLL_TIME;
        rollDir = dir;
        rollCooldown = ROLL_TIME + 8;
        lastLeftTap = lastRightTap = -100;
        player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_1,
                SoundSource.PLAYERS, 0.6f, 1.5f, false);
    }

    /** Ângulo extra do parafuso (graus), só para o jogador local. */
    public static float rollAngle(float partialTick) {
        if (rollTicks <= 0) return 0f;
        float t = 1f - (rollTicks - partialTick) / ROLL_TIME;
        t = Mth.clamp(t, 0f, 1f);
        float eased = t * t * (3 - 2 * t);
        return -rollDir * 360f * eased;
    }

    private static void impact(LocalPlayer player, Vec3 v) {
        var level = player.level();
        Vec3 dir = v.normalize();
        BlockPos hit = BlockPos.containing(player.position().add(0, 0.9, 0).add(dir.scale(1.2)));
        var state = level.getBlockState(hit);
        if (state.isAir()) state = level.getBlockState(hit.below());
        var rnd = player.getRandom();
        if (!state.isAir()) {
            var particle = new BlockParticleOption(ParticleTypes.BLOCK, state);
            for (int i = 0; i < 40; i++) {
                level.addParticle(particle, hit.getX() + rnd.nextDouble(), hit.getY() + rnd.nextDouble(), hit.getZ() + rnd.nextDouble(),
                        -dir.x * 0.3 + (rnd.nextDouble() - 0.5) * 0.6, 0.2 + rnd.nextDouble() * 0.3, -dir.z * 0.3 + (rnd.nextDouble() - 0.5) * 0.6);
            }
        }
        shockRing(player, player.position().add(0, 0.9, 0), dir, ClientRingData.color, 24, 0.35);
        level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 0.5f, false);
        level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.4f, 1.4f, false);
        shake = 10;
        // Quica para trás
        velocity = dir.scale(-Math.min(v.length(), 2.0) * 0.35).add(0, 0.2, 0);
    }

    private static void brakeEffects(LocalPlayer player, Vec3 v) {
        if (player.tickCount % 2 != 0) return;
        var rnd = player.getRandom();
        Vec3 front = player.position().add(0, 0.9, 0).add(v.normalize().scale(0.9));
        for (int i = 0; i < 4; i++) {
            Vec3 side = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5).scale(1.4);
            Vec3 p = front.add(side);
            player.level().addParticle(ParticleTypes.CLOUD, p.x, p.y, p.z, side.x * 0.1, side.y * 0.1, side.z * 0.1);
        }
    }

    private static void bubbles(LocalPlayer player) {
        var rnd = player.getRandom();
        for (int i = 0; i < 4; i++) {
            player.level().addParticle(ParticleTypes.BUBBLE, player.getX() + (rnd.nextDouble() - 0.5),
                    player.getY() + rnd.nextDouble() * 1.8, player.getZ() + (rnd.nextDouble() - 0.5), 0, 0.1, 0);
        }
    }

    private static void waterBurst(LocalPlayer player, Vec3 v) {
        var rnd = player.getRandom();
        for (int i = 0; i < 50; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            player.level().addParticle(ParticleTypes.SPLASH, player.getX() + Math.cos(a) * 0.8, player.getY(),
                    player.getZ() + Math.sin(a) * 0.8, Math.cos(a) * 0.3 + v.x * 0.3, 0.4 + rnd.nextDouble() * 0.4, Math.sin(a) * 0.3 + v.z * 0.3);
        }
        player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED,
                SoundSource.PLAYERS, 1.0f, 1.0f, false);
    }

    private static boolean nearGround(Player player, int blocks) {
        BlockPos.MutableBlockPos pos = player.blockPosition().mutable();
        for (int dy = 1; dy <= blocks; dy++) {
            pos.setY(player.blockPosition().getY() - dy);
            if (!player.level().getBlockState(pos).isAir()) return true;
        }
        return false;
    }

    private static void shockRing(Player player, Vec3 center, Vec3 axis, int color, int count, double speed) {
        Vec3 a = axis.cross(new Vec3(0, 1, 0));
        if (a.lengthSqr() < 1.0E-4) a = new Vec3(1, 0, 0);
        a = a.normalize();
        Vec3 b = a.cross(axis).normalize();
        Vec3 drift = player.getDeltaMovement();
        for (int i = 0; i < count; i++) {
            double ang = i * (Math.PI * 2 / count);
            Vec3 dir = a.scale(Math.cos(ang)).add(b.scale(Math.sin(ang)));
            Vec3 p = center.add(dir.scale(0.6));
            Vec3 vel = dir.scale(speed).add(drift);
            player.level().addParticle(GlowOrbOptions.of(lighten(color, 0.35f), 0.3f), p.x, p.y, p.z, vel.x, vel.y, vel.z);
        }
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

    /** Velocidade atual do voo local (blocos por tick). */
    public static double localSpeed() {
        return wasFlying ? velocity.length() : 0;
    }

    public static boolean isFlying(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player) return ClientRingData.hasRing && ClientRingData.flight && ClientRingData.active;
        return ClientVisibleRings.of(player.getId()).flying();
    }

    public static boolean isHyper(Player player) {
        if (player == Minecraft.getInstance().player) return hyperOn;
        return ClientVisibleRings.of(player.getId()).hyper();
    }

    public static boolean isAutopilot(Player player) {
        if (player == Minecraft.getInstance().player) return Autopilot.active() && isFlying(player);
        return ClientVisibleRings.of(player.getId()).autopilot();
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

        Vec3 move = player.position().subtract(player.xo, player.yo, player.zo);
        double speed = move.length();
        double lastSpeed = LAST_SPEED.getOrDefault(id, 0.0);

        if (isFlying(player) && !player.onGround()) {
            Vec3 look = player.getLookAngle();
            // Só deita quando está indo PARA FRENTE (subindo reto com Espaço fica em pé)
            double forwardSpeed = Math.max(0, move.dot(look));
            target = (float) Mth.clamp((forwardSpeed - 0.03) / 0.4, 0, 1);

            // Freio aerodinâmico: perdendo velocidade rápido, o corpo "abre" contra o vento
            if (speed > 0.25 && speed < 6 && lastSpeed - speed > 0.06) target = -0.45f;

            // Inclina de lado nas curvas, mais forte em alta velocidade
            float fast = (float) Mth.clamp(speed / 1.0, 0.2, 1.3);
            targetBank = Mth.clamp(-yawRate * 2.6f * fast, -60f, 60f) * Math.max(0, target);

            // Deslizar de lado (A/D) também inclina o corpo, como quem corta o vento
            Vec3 side = move.subtract(look.scale(move.dot(look)));
            Vec3 right = look.cross(new Vec3(0, 1, 0));
            double lateral = right.lengthSqr() > 1.0E-4 ? side.dot(right.normalize()) : 0;
            targetBank += (float) Mth.clamp(lateral * 70.0, -30, 30);

            effects(player, move, look);

            // O corpo acompanha para onde a cabeça olha
            player.yBodyRot = player.getYHeadRot();
            player.yBodyRotO = player.yHeadRotO;

            if (speed < 3.5) spawnTrail(player, target, speed);
        }
        LAST_SPEED.put(id, speed);
        recordPath(player, speed);

        float next = old + (target - old) * 0.18f;
        float nextBank = oldBank + (targetBank - oldBank) * 0.2f;
        if (Math.abs(next) < 0.001f && target == 0f && Math.abs(nextBank) < 0.1f) {
            TILT.remove(id);
            BANK.remove(id);
        } else {
            TILT.put(id, next);
            BANK.put(id, nextBank);
        }
    }

    /** Guarda o caminho do jogador enquanto ele voa muito rápido (para o rastro de energia). */
    private static void recordPath(Player player, double speed) {
        int id = player.getId();
        boolean trail = isFlying(player) && (isHyper(player) || isAutopilot(player) || speed > 2.5);
        Deque<Vec3> path = PATH.get(id);
        if (trail) {
            if (path == null) PATH.put(id, path = new ArrayDeque<>());
            path.addFirst(player.position().add(0, 0.9, 0));
            while (path.size() > PATH_LENGTH) path.removeLast();
        } else if (path != null) {
            // Some aos poucos, de trás para frente
            path.pollLast();
            path.pollLast();
            if (path.isEmpty()) PATH.remove(id);
        }
    }

    public static Deque<Vec3> path(Player player) {
        return PATH.get(player.getId());
    }

    /** 0 = em pé, 1 = deitado, negativo = inclinado para trás (freio). Suavizado entre ticks. */
    public static float tilt(Player player, float partialTick) {
        Float now = TILT.get(player.getId());
        if (now == null) return 0f;
        return Mth.lerp(partialTick, TILT_OLD.getOrDefault(player.getId(), now), now);
    }

    /** Inclinação lateral nas curvas, em graus (suavizada entre ticks), com o parafuso do jogador local. */
    public static float bank(Player player, float partialTick) {
        float roll = player == Minecraft.getInstance().player ? rollAngle(partialTick) : 0f;
        return cameraBank(player, partialTick) + roll;
    }

    /** Inclinação lateral sem o parafuso (para a câmera não dar uma volta inteira). */
    public static float cameraBank(Player player, float partialTick) {
        Float now = BANK.get(player.getId());
        if (now == null) return 0f;
        return Mth.lerp(partialTick, BANK_OLD.getOrDefault(player.getId(), now), now);
    }

    // ===================== Efeitos do voo (todos os jogadores) =====================

    private static final double SONIC_SPEED = 1.45;

    private static void effects(Player player, Vec3 move, Vec3 look) {
        double speed = move.length();
        double last = LAST_SPEED.getOrDefault(player.getId(), 0.0);
        Minecraft mc = Minecraft.getInstance();
        int color = player == mc.player ? ClientRingData.color : ClientVisibleRings.of(player.getId()).color();
        var rnd = player.getRandom();
        boolean inSpace = player.level().dimension().equals(SpaceBodies.SPACE);

        // Estrondo sônico: ao passar da velocidade do som, um anel de energia e um "BUM" (só onde há ar)
        if (!inSpace && speed >= SONIC_SPEED && last < SONIC_SPEED) {
            Vec3 center = player.position().add(0, 0.9, 0);
            shockRing(player, center, look, color, 40, 0.35);
            player.level().playLocalSound(center.x, center.y, center.z,
                    SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 2.0f, 0.55f, false);
            if (player == mc.player) shake = 8;
        }

        // Outros jogadores entrando em hiperpropulsão: o estrondo também aparece para quem vê
        if (player != mc.player && inSpace && speed > SPACE_BOOST * 1.2 && last <= SPACE_BOOST * 1.2) {
            shockRing(player, player.position().add(0, 0.9, 0), look, color, 48, 0.7);
            player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE,
                    SoundSource.PLAYERS, 1.0f, 0.55f, false);
        }

        // Rasante: perto do chão em alta velocidade, levanta poeira (ou espirra água)
        if (!inSpace && speed > 0.55 && player.tickCount % 2 == 0) {
            BlockPos.MutableBlockPos pos = player.blockPosition().mutable();
            for (int dy = 0; dy < 5; dy++) {
                pos.setY(player.blockPosition().getY() - dy - 1);
                var state = player.level().getBlockState(pos);
                var fluid = player.level().getFluidState(pos);
                if (!fluid.isEmpty()) {
                    for (int i = 0; i < 6; i++) {
                        player.level().addParticle(ParticleTypes.SPLASH,
                                player.getX() + (rnd.nextDouble() - 0.5) * 2, pos.getY() + 1.05, player.getZ() + (rnd.nextDouble() - 0.5) * 2,
                                -move.x * 0.3, 0.25, -move.z * 0.3);
                    }
                    break;
                }
                if (!state.isAir()) {
                    var particle = new BlockParticleOption(ParticleTypes.BLOCK, state);
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
        float base = hyperOn ? 0.25f + (float) hyperFraction() * 0.2f
                : (float) Mth.clamp((Math.min(localSpeed(), 2.0) - 1.2) * 0.6, 0, 0.35);
        return base + (shake > 0 ? shake * 0.12f : 0) + hyperCharge * 0.3f;
    }

    public static void tickShake() {
        if (shake > 0) shake--;
    }

    // ===================== Rastro de partículas (velocidades normais) =====================

    /**
     * Rastro de energia: bolinhas brilhantes saindo ao longo do corpo inteiro
     * (cabeça até os pés) e ficando para trás conforme o jogador avança.
     */
    private static void spawnTrail(Player player, float tilt, double speed) {
        if (tilt < 0.25f) return;
        Minecraft mc = Minecraft.getInstance();
        int color = player == mc.player ? ClientRingData.color : ClientVisibleRings.of(player.getId()).color();

        Vec3 look = player.getLookAngle();
        // Parte de onde o jogador ESTAVA no começo do tick: o corpo é desenhado entre os ticks, então a posição
        // atual fica na frente dele em alta velocidade (as bolinhas apareciam à frente do jogador)
        Vec3 center = new Vec3(player.xo, player.yo + 0.9, player.zo);
        int count = speed > 0.9 ? 9 : 5;
        var rnd = player.getRandom();

        for (int i = 0; i < count; i++) {
            double along = -1.1 + Math.pow(rnd.nextDouble(), 1.6) * 1.2;
            Vec3 p = center.add(look.scale(along)).add(
                    (rnd.nextDouble() - 0.5) * 0.45, (rnd.nextDouble() - 0.5) * 0.45, (rnd.nextDouble() - 0.5) * 0.45);
            int c = rnd.nextInt(6) == 0 ? lighten(color, 0.45f) : color;
            float size = 0.10f + rnd.nextFloat() * 0.16f;
            Vec3 drift = look.scale(-0.015).add(0, 0.003, 0);
            player.level().addParticle(GlowOrbOptions.of(c, size), p.x, p.y, p.z, drift.x, drift.y, drift.z);
        }
    }

    static int lighten(int rgb, float t) {
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
        PATH.clear();
        velocity = Vec3.ZERO;
        wasFlying = false;
        dropHyper();
        sentHyper = sentAutopilot = false;
        rollTicks = 0;
        Autopilot.clear();
        if (wind != null) wind.finish();
        wind = null;
    }

    private ClientFlight() {}
}
