package com.lanterna.client.flight;

import com.lanterna.client.ClientRingData;
import com.lanterna.client.ClientVisibleRings;
import com.lanterna.client.render.LanternaRenderTypes;
import com.lanterna.space.SpaceBodies;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Efeitos de energia do voo em altíssima velocidade:
 *
 *  - PILOTO AUTOMÁTICO: uma cúpula redonda de energia (constructo) em volta do jogador,
 *    com anéis correndo de frente para trás, e um TUBO de energia atrás dele que segue
 *    exatamente o caminho feito (curvas incluídas), afinando e sumindo no fim.
 *  - HIPERPROPULSÃO no controle: sem cúpula. Um cone de choque na frente (a barreira se
 *    rompendo), riscos de vento passando pelo corpo e um rastro de energia mais fino.
 *  - Em primeira pessoa no espaço: riscos de estrelas passando (efeito "dobra espacial").
 */
public final class HyperEffects {
    private static float flash, flashOld;

    public static void flash(float amount) {
        flash = Math.max(flash, amount);
    }

    public static float flashAlpha(float partialTick) {
        return Mth.lerp(partialTick, flashOld, flash);
    }

    public static void tick() {
        flashOld = flash;
        flash = flash * 0.82f;
        if (flash < 0.01f) flash = 0f;
    }

    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 cam, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Matrix4f m = poseStack.last().pose();
        VertexConsumer vc = buffers.getBuffer(LanternaRenderTypes.ENERGY);
        List<Runnable> glowPass = new ArrayList<>();
        float time = mc.level.getGameTime() + pt;
        boolean any = false;

        for (Player p : mc.level.players()) {
            if (p.isInvisible() || !ClientFlight.isFlying(p)) continue;
            boolean auto = ClientFlight.isAutopilot(p);
            boolean hyper = ClientFlight.isHyper(p);
            Deque<Vec3> path = ClientFlight.path(p);
            if (!auto && !hyper && path == null) continue;
            any = true;

            int color = p == mc.player ? ClientRingData.color : ClientVisibleRings.of(p.getId()).color();
            Vec3 center = p.getPosition(pt).add(0, 0.9, 0);
            Vec3 move = p.position().subtract(p.xo, p.yo, p.zo);
            Vec3 dir = move.lengthSqr() > 1.0E-4 ? move.normalize() : p.getViewVector(pt);
            double speed = move.length();
            boolean firstPerson = p == mc.player && cam.distanceTo(p.getEyePosition(pt)) < 0.6;

            if (auto) {
                // De dentro da cúpula (câmera em primeira pessoa) ela fica bem sutil, para não tingir a tela
                boolean inside = cam.distanceTo(center) < 1.7;
                dome(vc, m, cam, center, dir, color, time, inside ? 0.12f : 1f);
                if (path != null) tube(vc, m, cam, center, path, color, time, 1.0, 0.32f, true);
            } else if (hyper) {
                cone(vc, m, cam, center, dir, color, time, cam.distanceTo(center) < 2.5 ? 0.3f : 1f);
                final int c2 = color; final Vec3 cc = center, dd = dir; final int id = p.getId();
                if (!firstPerson) glowPass.add(() -> windLines(buffers.getBuffer(LanternaRenderTypes.GLOW), m, cam, cc, dd, c2, time, id));
                if (path != null) tube(vc, m, cam, center, path, color, time, 0.45, 0.4f, false);
            } else if (path != null) {
                tube(vc, m, cam, center, path, color, time, 0.3, 0.25f, false);
            }

            if (p == mc.player && firstPerson && p.level().dimension().equals(SpaceBodies.SPACE) && speed > 3) {
                final int c3 = color; final Vec3 d3 = dir; final double sp = speed;
                glowPass.add(() -> warpStreaks(buffers.getBuffer(LanternaRenderTypes.GLOW), m, cam, d3, c3, time, sp));
            }
        }
        if (any) buffers.endBatch(LanternaRenderTypes.ENERGY);
        if (!glowPass.isEmpty()) {
            glowPass.forEach(Runnable::run);
            buffers.endBatch(LanternaRenderTypes.GLOW);
        }
    }

    // ===================== Cúpula do piloto automático =====================

    private static void dome(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 c, Vec3 dir, int color, float time, float base) {
        final int LAT = 18, LON = 36;
        float radius = 1.55f + 0.04f * Mth.sin(time * 0.3f);
        Vec3[] basis = basis(dir);
        int line = ClientFlight.lighten(color, 0.35f);
        for (int i = 0; i < LAT; i++) {
            for (int j = 0; j < LON; j++) {
                domeVertex(vc, m, cam, c, basis, radius, i, j, LAT, LON, color, line, time, base);
                domeVertex(vc, m, cam, c, basis, radius, i + 1, j, LAT, LON, color, line, time, base);
                domeVertex(vc, m, cam, c, basis, radius, i + 1, j + 1, LAT, LON, color, line, time, base);
                domeVertex(vc, m, cam, c, basis, radius, i, j + 1, LAT, LON, color, line, time, base);
            }
        }
    }

    private static void domeVertex(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 c, Vec3[] b, float radius,
                                   int i, int j, int lat, int lon, int color, int lineColor, float time, float base) {
        double theta = Math.PI * i / lat;          // 0 = frente, PI = trás
        double phi = Math.PI * 2 * j / lon;
        Vec3 n = b[0].scale(Math.cos(theta)).add(b[1].scale(Math.cos(phi) * Math.sin(theta))).add(b[2].scale(Math.sin(phi) * Math.sin(theta)));
        Vec3 pos = c.add(n.scale(radius));
        Vec3 toCam = cam.subtract(pos).normalize();
        double facing = Math.abs(n.dot(toCam));
        double rim = (1 - facing) * (1 - facing);
        // Anéis correndo da frente para trás e linhas de "constructo"
        double ring = smooth(0.82, 1.0, Math.sin(theta * 14 - time * 0.45));
        double meridian = smooth(0.92, 1.0, Math.cos(phi * 8)) * 0.35;
        double front = Math.pow(Math.max(0, Math.cos(theta)), 3) * 0.3;
        double a = (0.04 + 0.45 * rim + 0.3 * ring + 0.18 * meridian + front * 0.6) * base;
        int col = ring > 0.3 || meridian > 0.1 ? lineColor : color;
        put(vc, m, cam, pos, col, (float) Math.min(a, 0.9));
    }

    // ===================== Tubo de energia (rastro físico) =====================

    private static void tube(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 head, Deque<Vec3> path, int color,
                             float time, double radius, float alpha, boolean spiral) {
        List<Vec3> pts = new ArrayList<>(path.size() + 1);
        pts.add(head);
        for (Vec3 v : path) {
            if (v.distanceToSqr(pts.get(pts.size() - 1)) > 0.01) pts.add(v);
        }
        int n = pts.size();
        if (n < 2) return;
        final int SEG = 14;

        // Moldura que acompanha o caminho sem torcer (transporte paralelo)
        Vec3[] normal = new Vec3[n];
        Vec3[] binormal = new Vec3[n];
        Vec3 ref = new Vec3(0, 1, 0);
        for (int k = 0; k < n; k++) {
            Vec3 t = tangent(pts, k);
            Vec3 nn = ref.subtract(t.scale(ref.dot(t)));
            if (nn.lengthSqr() < 1.0E-6) nn = new Vec3(1, 0, 0).subtract(t.scale(t.x));
            nn = nn.normalize();
            normal[k] = nn;
            binormal[k] = t.cross(nn).normalize();
            ref = nn;
        }

        int core = ClientFlight.lighten(color, 0.6f);
        for (int pass = 0; pass < 2; pass++) {
            double rBase = pass == 0 ? radius : radius * 0.18;
            float aBase = pass == 0 ? alpha : alpha * 1.4f;
            int col = pass == 0 ? color : core;
            for (int k = 0; k < n - 1; k++) {
                for (int j = 0; j < SEG; j++) {
                    tubeVertex(vc, m, cam, pts, normal, binormal, k, j, n, SEG, rBase, aBase, col, time, spiral);
                    tubeVertex(vc, m, cam, pts, normal, binormal, k + 1, j, n, SEG, rBase, aBase, col, time, spiral);
                    tubeVertex(vc, m, cam, pts, normal, binormal, k + 1, j + 1, n, SEG, rBase, aBase, col, time, spiral);
                    tubeVertex(vc, m, cam, pts, normal, binormal, k, j + 1, n, SEG, rBase, aBase, col, time, spiral);
                }
            }
        }
    }

    private static void tubeVertex(VertexConsumer vc, Matrix4f m, Vec3 cam, List<Vec3> pts, Vec3[] nrm, Vec3[] bin,
                                   int k, int j, int n, int seg, double radius, float alpha, int color, float time,
                                   boolean spiral) {
        double t = (double) k / (n - 1);
        double phi = Math.PI * 2 * j / seg;
        double r = radius * Math.pow(1 - t, 0.9) + 0.03;
        Vec3 dirOut = nrm[k].scale(Math.cos(phi)).add(bin[k].scale(Math.sin(phi)));
        Vec3 pos = pts.get(k).add(dirOut.scale(r));
        Vec3 toCam = cam.subtract(pos).normalize();
        double rim = 1 - Math.abs(dirOut.dot(toCam));
        double stripes = spiral ? 0.55 + 0.45 * Math.sin(phi * 3 + k * 1.1 - time * 0.6) : 1.0;
        double a = alpha * Math.pow(1 - t, 1.6) * stripes * (0.2 + 1.0 * rim * rim);
        // Some perto da câmera (em terceira pessoa a câmera fica em cima do rastro)
        a *= Mth.clamp((cam.distanceTo(pos) - 2.0) / 10.0, 0, 1);
        put(vc, m, cam, pos, color, (float) Math.min(a, 0.95));
    }

    private static Vec3 tangent(List<Vec3> pts, int k) {
        Vec3 a = pts.get(Math.max(0, k - 1));
        Vec3 b = pts.get(Math.min(pts.size() - 1, k + 1));
        Vec3 t = a.subtract(b);
        return t.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, 1) : t.normalize();
    }

    // ===================== Cone da barreira (hiperpropulsão no controle) =====================

    private static void cone(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 c, Vec3 dir, int color, float time, float base) {
        final int S = 12, SEG = 24;
        Vec3[] b = basis(dir);
        Vec3 tip = c.add(dir.scale(2.4));
        Vec3 back = c.subtract(dir.scale(1.1));
        int col = ClientFlight.lighten(color, 0.55f);
        for (int s = 0; s < S; s++) {
            for (int j = 0; j < SEG; j++) {
                coneVertex(vc, m, cam, tip, back, b, s, j, S, SEG, col, time, base);
                coneVertex(vc, m, cam, tip, back, b, s + 1, j, S, SEG, col, time, base);
                coneVertex(vc, m, cam, tip, back, b, s + 1, j + 1, S, SEG, col, time, base);
                coneVertex(vc, m, cam, tip, back, b, s, j + 1, S, SEG, col, time, base);
            }
        }
    }

    private static void coneVertex(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 tip, Vec3 back, Vec3[] b,
                                   int si, int j, int slices, int seg, int color, float time, float base) {
        double s = (double) si / slices;
        double phi = Math.PI * 2 * j / seg;
        Vec3 radial = b[1].scale(Math.cos(phi)).add(b[2].scale(Math.sin(phi)));
        Vec3 pos = tip.add(back.subtract(tip).scale(s)).add(radial.scale(1.4 * Math.pow(s, 0.75)));
        // Corpo do cone: forte no meio, some na ponta e na boca
        double body = Math.sqrt(s) * Math.pow(1 - s, 0.8) * 1.6;
        double shimmer = 0.6 + 0.4 * Math.sin(phi * 6 + time * 1.7 + s * 12);
        // Ondas de choque correndo da ponta para trás
        double shock = 0;
        for (int k = 0; k < 3; k++) {
            double sp = frac(time * 0.07 + k / 3.0);
            double d = (s - sp) / 0.06;
            shock += Math.exp(-d * d) * (1 - sp);
        }
        double a = (body * shimmer * 0.35 + shock * 0.5) * base;
        put(vc, m, cam, pos, color, (float) Math.min(a, 0.85));
    }

    /** Riscos de vento passando pelo corpo (vistos de fora). */
    private static void windLines(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 c, Vec3 dir, int color, float time, int seed) {
        Vec3[] b = basis(dir);
        int col = ClientFlight.lighten(color, 0.7f);
        for (int i = 0; i < 26; i++) {
            double r1 = hash(seed * 31 + i * 7), r2 = hash(seed * 17 + i * 13), r3 = hash(seed * 5 + i * 29);
            double phi = r1 * Math.PI * 2;
            double radius = 0.9 + r2 * 1.8;
            double z = 3 - frac(r3 + time * 0.22) * 12;
            double len = 2.5 + r2 * 4;
            Vec3 radial = b[1].scale(Math.cos(phi) * radius).add(b[2].scale(Math.sin(phi) * radius));
            Vec3 p0 = c.add(dir.scale(z)).add(radial);
            Vec3 p1 = p0.subtract(dir.scale(len));
            line(vc, m, cam, p0, p1, 0.035, col, 0.45f, 0f);
        }
    }

    /** Estrelas riscando a tela em primeira pessoa (dobra espacial). */
    private static void warpStreaks(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 dir, int color, float time, double speed) {
        Vec3[] b = basis(dir);
        float intensity = (float) Mth.clamp((speed - 3) / 25.0, 0, 1);
        double len = Mth.clamp(speed * 0.9, 1.5, 34);
        int tint = ClientFlight.lighten(color, 0.8f);
        for (int i = 0; i < 170; i++) {
            double r1 = hash(i * 7 + 1), r2 = hash(i * 13 + 3), r3 = hash(i * 29 + 5);
            double phi = r1 * Math.PI * 2;
            double radius = 4 + r2 * r2 * 42;
            double z = frac(r3 - time * speed / 220.0) * 220 - 60;
            Vec3 p0 = cam.add(dir.scale(z)).add(b[1].scale(Math.cos(phi) * radius)).add(b[2].scale(Math.sin(phi) * radius));
            Vec3 p1 = p0.subtract(dir.scale(len));
            float a = intensity * (float) (0.25 + 0.5 * hash(i * 3 + 11));
            line(vc, m, cam, p0, p1, 0.05 + radius * 0.004, i % 5 == 0 ? tint : 0xFFFFFF, a, 0f);
        }
    }

    // ===================== Utilidades =====================

    private static void line(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 p0, Vec3 p1, double width, int color,
                             float a0, float a1) {
        Vec3 d = p1.subtract(p0);
        Vec3 toCam = cam.subtract(p0);
        Vec3 side = d.cross(toCam);
        if (side.lengthSqr() < 1.0E-8) return;
        side = side.normalize().scale(width);
        put(vc, m, cam, p0.add(side), color, a0);
        put(vc, m, cam, p0.subtract(side), color, a0);
        put(vc, m, cam, p1.subtract(side), color, a1);
        put(vc, m, cam, p1.add(side), color, a1);
    }

    private static void put(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 pos, int color, float alpha) {
        float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;
        vc.vertex(m, (float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z))
                .color(r, g, b, Mth.clamp(alpha, 0f, 1f)).endVertex();
    }

    /** Três eixos: [0] = direção, [1] e [2] = perpendiculares. */
    private static Vec3[] basis(Vec3 dir) {
        Vec3 a = dir.cross(new Vec3(0, 1, 0));
        if (a.lengthSqr() < 1.0E-4) a = dir.cross(new Vec3(1, 0, 0));
        a = a.normalize();
        Vec3 b = dir.cross(a).normalize();
        return new Vec3[]{dir, a, b};
    }

    private static double smooth(double e0, double e1, double x) {
        double t = Mth.clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static double frac(double x) {
        return x - Math.floor(x);
    }

    private static double hash(int n) {
        n = (n << 13) ^ n;
        n = n * (n * n * 15731 + 789221) + 1376312589;
        return ((n & 0x7fffffff) / (double) 0x7fffffff);
    }

    public static void clear() {
        flash = flashOld = 0;
    }

    private HyperEffects() {}
}
