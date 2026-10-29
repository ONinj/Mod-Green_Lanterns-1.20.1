package com.lanterna.client.construct;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Formas de energia dos construtos (caixas, tubos, esferas), desenhadas com dois passes:
 * o corpo translúcido na cor da Tropa e uma casca de brilho somado em volta.
 */
public final class EnergyShapes {

    /** Caixa alinhada ao PoseStack atual. */
    public static void box(PoseStack pose, VertexConsumer body, VertexConsumer glow, float x0, float y0, float z0,
                           float x1, float y1, float z1, int rgb, float alpha) {
        Matrix4f m = pose.last().pose();
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        cube(body, m, x0, y0, z0, x1, y1, z1, r, g, b, alpha * 0.55f);
        float e = 0.04f + 0.03f * Math.max(Math.max(x1 - x0, y1 - y0), z1 - z0) / 4f;
        cube(glow, m, x0 - e, y0 - e, z0 - e, x1 + e, y1 + e, z1 + e, r, g, b, alpha * 0.22f);
        // núcleo claro
        float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, cz = (z0 + z1) / 2;
        float sx = (x1 - x0) * 0.35f, sy = (y1 - y0) * 0.35f, sz = (z1 - z0) * 0.35f;
        cube(glow, m, cx - sx, cy - sy, cz - sz, cx + sx, cy + sy, cz + sz, 1f, 1f, 1f, alpha * 0.12f);
    }

    public static void cube(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                            float r, float g, float b, float a) {
        // baixo, cima, norte, sul, oeste, leste
        quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
        quad(vc, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
        quad(vc, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a);
        quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a);
        quad(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a);
        quad(vc, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float r, float g, float b, float a) {
        vc.vertex(m, ax, ay, az).color(r, g, b, a).endVertex();
        vc.vertex(m, bx, by, bz).color(r, g, b, a).endVertex();
        vc.vertex(m, cx, cy, cz).color(r, g, b, a).endVertex();
        vc.vertex(m, dx, dy, dz).color(r, g, b, a).endVertex();
    }

    /** Tubo de seção quadrada entre dois pontos (coordenadas relativas ao PoseStack). */
    public static void tube(PoseStack pose, VertexConsumer vc, Vec3 from, Vec3 to, float half, int rgb, float alpha) {
        Vec3 d = to.subtract(from);
        if (d.lengthSqr() < 1e-6) return;
        Vec3 dir = d.normalize();
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dir.cross(up).normalize().scale(half);
        Vec3 v = dir.cross(u).normalize().scale(half);
        Matrix4f m = pose.last().pose();
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        Vec3[] c = {u.add(v), u.subtract(v), u.scale(-1).subtract(v), u.scale(-1).add(v)};
        for (int i = 0; i < 4; i++) {
            Vec3 p0 = c[i], p1 = c[(i + 1) % 4];
            vert(vc, m, from.add(p0), r, g, b, alpha);
            vert(vc, m, to.add(p0), r, g, b, alpha);
            vert(vc, m, to.add(p1), r, g, b, alpha);
            vert(vc, m, from.add(p1), r, g, b, alpha);
        }
    }

    /** Esfera (bolha) com faixas que brilham e correm. */
    public static void sphere(PoseStack pose, VertexConsumer vc, float radius, int rgb, float alpha, float time) {
        Matrix4f m = pose.last().pose();
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        int lat = 12, lon = 20;
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat - Math.PI / 2, t1 = Math.PI * (i + 1) / lat - Math.PI / 2;
            for (int j = 0; j < lon; j++) {
                double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
                float band = 0.55f + 0.45f * (float) Math.sin(i * 1.3 + time * 0.15 + j * 0.5);
                float a = alpha * band;
                vert(vc, m, sp(t0, p0, radius), r, g, b, a);
                vert(vc, m, sp(t1, p0, radius), r, g, b, a);
                vert(vc, m, sp(t1, p1, radius), r, g, b, a);
                vert(vc, m, sp(t0, p1, radius), r, g, b, a);
            }
        }
    }

    private static Vec3 sp(double t, double p, float r) {
        return new Vec3(Math.cos(t) * Math.cos(p) * r, Math.sin(t) * r, Math.cos(t) * Math.sin(p) * r);
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).endVertex();
    }

    private EnergyShapes() {}
}
