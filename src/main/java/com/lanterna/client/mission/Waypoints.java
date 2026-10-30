package com.lanterna.client.mission;

import com.lanterna.client.flight.Destinations;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.Mission;
import com.lanterna.mission.Objective;
import com.lanterna.space.SpaceBodies;
import com.lanterna.space.SpaceTravel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Para onde a missão acompanhada manda o jogador agora: um ponto no mundo atual (base, arena),
 * o planeta no espaço, ou "suba até o espaço" quando o objetivo está em outro planeta.
 * Também projeta pontos do mundo na tela (para o marcador no HUD).
 */
public final class Waypoints {

    /**
     * @param pos      ponto no mundo atual (onde desenhar o marcador)
     * @param distance distância até o objetivo (no espaço, até a superfície do planeta)
     * @param hint     o que fazer agora ("Suba até o espaço", "Siga até Odym"...)
     */
    public record Target(Vec3 pos, double distance, Component hint, Component label, int color, @Nullable String planet) {}

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJ = new Matrix4f();
    private static Vec3 cam = Vec3.ZERO;
    private static boolean captured;

    public static void capture(Matrix4f view, Matrix4f proj, Vec3 camera) {
        VIEW.set(view);
        PROJ.set(proj);
        cam = camera;
        captured = true;
    }

    /** Ponto na tela (x, y) ou null se não der para projetar. O terceiro valor é 1 se está atrás da câmera. */
    @Nullable
    public static float[] project(Vec3 world, int screenW, int screenH) {
        if (!captured) return null;
        Vector4f v = new Vector4f((float) (world.x - cam.x), (float) (world.y - cam.y), (float) (world.z - cam.z), 1f);
        VIEW.transform(v);
        PROJ.transform(v);
        boolean behind = v.w <= 0.05f;
        float w = Math.abs(v.w) < 1e-4f ? 1e-4f : Math.abs(v.w);
        float nx = v.x / w, ny = v.y / w;
        if (behind) {
            nx = -nx;
            ny = -ny;
        }
        return new float[]{(nx * 0.5f + 0.5f) * screenW, (1f - (ny * 0.5f + 0.5f)) * screenH, behind ? 1 : 0};
    }

    /** O objetivo atual da missão acompanhada (o primeiro que ainda não foi feito e tem lugar). */
    @Nullable
    public static Target current() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Mission m = ClientLoreData.tracked();
        if (player == null || m == null) return null;
        for (int i = 0; i < m.objectives().size(); i++) {
            Objective o = m.objectives().get(i);
            if (ClientLoreData.DATA.progress(m.id(), i) >= o.count()) continue;
            if (o.planet() == null) continue;
            return resolve(player, o, m.color());
        }
        return null;
    }

    @Nullable
    public static Target resolve(LocalPlayer player, Objective o, int color) {
        if ("space".equals(o.planet()) && o.pos() != null) {
            // Ponto no espaço (patrulha de setor)
            Vec3 at = Vec3.atCenterOf(o.pos());
            Component nome = com.lanterna.tropa.Contratos.nomeSetor(o.pos());
            if (player.level().dimension().equals(SpaceBodies.SPACE)) {
                return new Target(at, at.distanceTo(player.position()), Component.translatable("waypoint.lanterna.setor", nome), o.label(0), color, "space");
            }
            Vec3 up = new Vec3(player.getX(), SpaceTravel.EXIT_HEIGHT, player.getZ());
            double total = Destinations.spacePosition(player).distanceTo(at) + Math.max(0, SpaceTravel.EXIT_HEIGHT - player.getY());
            return new Target(up, total, Component.translatable("waypoint.lanterna.go_space", nome), o.label(0), color, "space");
        }
        SpaceBodies.Body body = SpaceBodies.byId(o.planet());
        if (body == null) return null;
        Component planetName = body.displayName();
        Component label = o.label(0);
        var dim = player.level().dimension();
        boolean onPlanet = body.dimension() != null && dim.equals(body.dimension());
        if (onPlanet) {
            if (o.pos() == null) {
                return null;
            }
            BlockPos p = o.pos();
            int y = player.level().hasChunk(p.getX() >> 4, p.getZ() >> 4)
                    ? player.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()) : (int) player.getY();
            Vec3 at = new Vec3(p.getX() + 0.5, y + 1.5, p.getZ() + 0.5);
            String place = p.equals(PlanetLore.HUB) ? "waypoint.lanterna.hub" : p.equals(PlanetLore.arena(PlanetLore.byPlanet(o.planet())))
                    ? "waypoint.lanterna.arena" : "waypoint.lanterna.local";
            return new Target(at, at.distanceTo(player.position()), Component.translatable(place, planetName), label, color, o.planet());
        }
        if (dim.equals(SpaceBodies.SPACE)) {
            Vec3 c = body.center();
            double dist = Math.max(0, c.distanceTo(player.position()) - body.radius());
            return new Target(c, dist, Component.translatable("waypoint.lanterna.fly_to", planetName), label, color, o.planet());
        }
        // Em outro mundo: primeiro subir até o espaço
        Vec3 up = new Vec3(player.getX(), SpaceTravel.EXIT_HEIGHT, player.getZ());
        Vec3 space = Destinations.spacePosition(player);
        double total = Math.max(0, space.distanceTo(body.center()) - body.radius()) + Math.max(0, SpaceTravel.EXIT_HEIGHT - player.getY());
        return new Target(up, total, Component.translatable("waypoint.lanterna.go_space", planetName), label, color, o.planet());
    }

    private Waypoints() {}
}
