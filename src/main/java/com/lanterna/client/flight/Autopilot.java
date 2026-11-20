package com.lanterna.client.flight;

import com.lanterna.client.ClientRingData;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.ToggleFlightPacket;
import com.lanterna.space.SpaceBodies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Piloto automático do anel.
 *
 * Etapas:
 *   SUBIR    (num mundo com chão) sobe até sair da atmosfera
 *   VIAJAR   (no espaço) aponta para o destino, entra em hiperpropulsão, desvia de outros planetas
 *            e desacelera ao chegar
 *   DESCER   (no mundo do destino) desce até perto do chão e devolve o controle
 *
 * Durante o piloto o anel controla a direção. Para cancelar: S, a tecla do piloto (J) ou desligar o voo.
 */
public final class Autopilot {
    public enum Phase { WAIT_FLIGHT, ASCEND, CRUISE, DESCEND }

    /** O que o piloto "aperta" neste tick. */
    public record Control(float forward, boolean boost, boolean up, boolean down, boolean hyperAllowed) {}

    private static Destinations.Destination target;
    private static Phase phase;
    private static int phaseTicks;
    private static int stuckTicks;
    private static double lastY;
    /** 0.52.3: a rota traçada no espaço (pontos que faltam; o último é o destino). */
    private static java.util.List<Vec3> rota = java.util.List.of();
    private static int rotaIdade;
    /** Só para os testes: desliga a rota (piloto antigo, reto). */
    public static boolean semRota;

    /** Os pontos da rota que faltam (para o mapa estelar). */
    public static java.util.List<Vec3> rota() {
        return target != null && phase == Phase.CRUISE ? rota : java.util.List.of();
    }

    /** 0.54.1: o chamado de Ryut trava o piloto (não dá para cancelar nem mudar o destino). */
    private static boolean travado;

    public static void travar() { travado = true; }

    public static boolean travado() { return travado && target != null; }

    /** Solta o piloto travado (o chamado acabou). */
    public static void destravar() {
        if (!travado) return;
        travado = false;
        stop(null);
    }

    public static boolean active() {
        return target != null;
    }

    public static Destinations.Destination target() {
        return target;
    }

    public static Phase phase() {
        return phase;
    }

    /** Começa a viagem. Devolve uma mensagem de erro, ou null se deu certo. */
    public static Component start(Destinations.Destination dest) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || dest == null) return Component.translatable("autopilot.lanterna.error.no_target");
        if (!ClientRingData.hasRing || !ClientRingData.active) return Component.translatable("autopilot.lanterna.error.no_ring");
        if (ClientRingData.charge <= 0) return Component.translatable("autopilot.lanterna.error.no_charge");
        if (Destinations.isHere(player, dest)) return Component.translatable("autopilot.lanterna.error.here");
        if (travado && target != null && !target.id().equals(dest.id())) return Component.translatable("necron.lanterna.anel_nao_obedece");

        target = dest;
        rota = java.util.List.of();
        phaseTicks = 0;
        stuckTicks = 0;
        phase = Phase.WAIT_FLIGHT;
        if (!ClientRingData.flight) ModNetwork.sendToServer(new ToggleFlightPacket());
        player.playSound(SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.6f);
        player.displayClientMessage(Component.translatable("autopilot.lanterna.started", dest.name()), true);
        return null;
    }

    public static void stop(Component message) {
        if (target == null) return;
        if (travado) {
            // o anel não obedece: se o voo foi desligado, ele liga de novo
            if (!ClientRingData.flight) ModNetwork.sendToServer(new ToggleFlightPacket());
            LocalPlayer pl = Minecraft.getInstance().player;
            if (pl != null && message != null) pl.displayClientMessage(Component.translatable("necron.lanterna.anel_nao_obedece"), true);
            return;
        }
        target = null;
        phase = null;
        rota = java.util.List.of();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && message != null) {
            player.displayClientMessage(message, true);
            player.playSound(SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.5f);
        }
    }

    /** Roda no começo do tick de voo. Devolve os comandos do piloto (ou null se ele desligou). */
    public static Control control(LocalPlayer player) {
        if (target == null) return null;
        phaseTicks++;

        if (phase == Phase.WAIT_FLIGHT) {
            if (ClientRingData.flight) {
                phase = nextPhase(player);
                phaseTicks = 0;
            } else if (phaseTicks > 60) {
                stop(Component.translatable("autopilot.lanterna.error.no_flight"));
                return null;
            }
            return new Control(0, false, true, false, false);
        }

        Phase now = nextPhase(player);
        if (now != phase) {
            phase = now;
            rota = java.util.List.of();
            phaseTicks = 0;
            stuckTicks = 0;
        }

        return switch (phase) {
            case ASCEND -> ascend(player);
            case CRUISE -> cruise(player);
            case DESCEND -> descend(player);
            default -> null;
        };
    }

    private static Phase nextPhase(LocalPlayer player) {
        var dim = player.level().dimension();
        if (dim.equals(target.dimension())) {
            // Mundos de planetas gerados são compartilhados: confere se é a região do destino
            SpaceBodies.Body here = SpaceBodies.bodyAt(dim, player.getX(), player.getZ());
            if (here != null && here.id().equals(target.id())) return Phase.DESCEND;
            if (dim.equals(SpaceBodies.SPACE)) return Phase.CRUISE;
            return Phase.ASCEND;
        }
        if (dim.equals(SpaceBodies.SPACE)) return Phase.CRUISE;
        return Phase.ASCEND;
    }

    // ===================== Etapas =====================

    private static Control ascend(LocalPlayer player) {
        steer(player, player.getYRot(), -78f, 5f);
        // Preso embaixo de um teto?
        if (player.getY() - lastY < 0.05) stuckTicks++; else stuckTicks = 0;
        lastY = player.getY();
        if (stuckTicks > 80) {
            stop(Component.translatable("autopilot.lanterna.error.blocked"));
            return null;
        }
        return new Control(1, true, true, false, false);
    }

    private static Control cruise(LocalPlayer player) {
        Vec3 pos = player.position();
        Vec3 dir = target.center().subtract(pos);
        double dist = dir.length() - target.radius();
        dir = dir.normalize();

        // 0.52.3: segue a rota que contorna planetas, sóis e buracos negros longe o bastante para a hiperpropulsão
        // não frear. Ela é refeita a cada 4 s (o caminho muda pouco, mas o embalo e os puxões tiram um pouco do lugar).
        if (semRota) rota = new java.util.ArrayList<>(java.util.List.of(target.center()));
        else if (rota.isEmpty() || ++rotaIdade > 80) {
            rota = new java.util.ArrayList<>(Rota.tracar(pos, target.center(), target.id(), ClientFlight.hyperMax()));
            rotaIdade = 0;
        }
        // passa para o próximo ponto um pouco antes de chegar (cortar demais a curva entraria na zona dos freios)
        double antecipa = Math.max(300, ClientFlight.localSpeed() * 5);
        while (rota.size() > 1) {
            Vec3 w = rota.get(0), prox = rota.get(1);
            boolean passou = prox.subtract(w).dot(pos.subtract(w)) > 0;
            if (pos.distanceTo(w) < antecipa || passou) rota.remove(0); else break;
        }
        boolean desvio = rota.size() > 1;
        if (desvio) dir = rota.get(0).subtract(pos).normalize();

        // Desvia de planetas no caminho (senão pousaria no planeta errado)
        for (SpaceBodies.Body b : SpaceBodies.near(pos, 3)) {
            if (!b.landable() || b.id().equals(target.id())) continue;
            // 0.50: já perto demais de outro planeta (por exemplo, logo depois de sair da Terra): afasta primeiro
            Vec3 de = pos.subtract(b.center());
            if (de.length() < b.radius() + 200) {
                dir = dir.add(de.normalize().scale(2.0)).normalize();
                continue;
            }
            Vec3 to = b.center().subtract(pos);
            double along = to.dot(dir);
            if (along <= 0 || along > dist + target.radius()) continue;
            Vec3 closest = pos.add(dir.scale(along));
            Vec3 away = closest.subtract(b.center());
            double clearance = b.radius() + 250;
            double miss = away.length();
            if (miss < clearance) {
                if (miss < 1) away = new Vec3(0, 1, 0);
                double push = (clearance - miss) / clearance;
                dir = dir.add(away.normalize().scale(push * 1.4)).normalize();
            }
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1, 1)));
        boolean fast = ClientFlight.localSpeed() > 6;
        steer(player, yaw, pitch, fast ? 2.2f : 6f);

        // Só entra em hiperpropulsão com o nariz alinhado e longe do destino
        Vec3 look = player.getLookAngle();
        // nas curvas da rota a hiperpropulsão continua (ela mesma vira aos poucos)
        // (a própria hiperpropulsão vira aos poucos para onde o anel olha; antes, a cada curva ela se apagava)
        boolean aligned = !semRota && ClientFlight.hyperOn() ? look.dot(dir) > 0.2 : look.dot(dir) > 0.985;
        // Já dentro do planeta: segura a posição até o pouso acontecer
        if (dist < 0) return new Control(-1, false, false, false, false);
        // Chegando: sem impulso, para entrar devagar
        if (dist < 80) return new Control(1, false, false, false, false);
        return new Control(1, true, false, false, aligned && dist > 900);
    }

    private static Control descend(LocalPlayer player) {
        var level = player.level();
        int bx = player.getBlockX(), bz = player.getBlockZ();
        int cx = bx >> 4, cz = bz >> 4;
        // Só desce com o chão em volta já carregado (senão poderia atravessar o terreno)
        boolean ready = true;
        for (int dx = -1; dx <= 1 && ready; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.hasChunk(cx + dx, cz + dz)) { ready = false; break; }
            }
        }
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        if (!ready || ground <= level.getMinBuildHeight()) {
            steer(player, player.getYRot(), 30f, 4f);
            return new Control(0, false, false, false, false);
        }
        double height = player.getY() - ground;
        // Chegou: algo (chão ou água) logo abaixo dos pés
        boolean near = player.isInWater() || player.onGround();
        var below = player.blockPosition().mutable();
        for (int i = 1; i <= 4 && !near; i++) {
            below.setY(player.getBlockY() - i);
            if (!level.getBlockState(below).isAir() || !level.getFluidState(below).isEmpty()) near = true;
        }
        if (near || height < 4 || phaseTicks > 20 * 60) {
            ClientFlight.brakeHard();
            stop(Component.translatable("autopilot.lanterna.arrived", target.name()));
            return null;
        }
        // Desce quase na vertical: rápido lá do alto, devagar perto do chão
        steer(player, player.getYRot(), 85f, 4f);
        if (height > 30) return new Control(1, true, false, false, false);
        return new Control(0, false, false, true, false);
    }

    /** Vira o jogador aos poucos para a direção desejada. */
    private static void steer(LocalPlayer player, float yaw, float pitch, float maxStep) {
        float dy = Mth.clamp(Mth.wrapDegrees(yaw - player.getYRot()), -maxStep, maxStep);
        float dp = Mth.clamp(pitch - player.getXRot(), -maxStep, maxStep);
        player.setYRot(player.getYRot() + dy);
        player.setXRot(Mth.clamp(player.getXRot() + dp, -90f, 90f));
        player.setYHeadRot(player.getYRot());
    }

    /** Distância que falta (para o HUD), em blocos. */
    public static double remaining(LocalPlayer player) {
        if (target == null) return 0;
        if (phase == Phase.DESCEND) {
            return Math.max(0, player.getY() - player.level().getHeight(Heightmap.Types.MOTION_BLOCKING,
                    player.getBlockX(), player.getBlockZ()));
        }
        if (phase == Phase.ASCEND) return Math.max(0, com.lanterna.space.SpaceTravel.EXIT_HEIGHT - player.getY());
        return Math.max(0, Destinations.spacePosition(player).distanceTo(target.center()) - target.radius());
    }

    public static void clear() {
        rota = java.util.List.of();
        target = null;
        phase = null;
    }

    private Autopilot() {}
}
