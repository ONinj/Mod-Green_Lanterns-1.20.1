package com.lanterna.client.construct;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Formas de energia dos construtos ("luz sólida"): corpo translúcido na cor da Tropa, casca de brilho somado, núcleo
 * claro e contornos brilhantes nas arestas. O brilho tremula de leve com o tempo (definido por quem desenha em
 * {@link #tempo}). Cores muito escuras (o Preto) brilham num cinza-violeta, senão o brilho somado sumiria.
 */
public final class EnergyShapes {
    /** Tempo da animação (ticks + fração) do construto que está sendo desenhado. */
    public static float tempo;

    /** Cor do brilho: cores escuras ganham um contorno claro. */
    public static int brilho(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        float lum = (0.3f * r + 0.59f * g + 0.11f * b) / 255f;
        return lum < 0.28f ? 0x9C8CC0 : rgb;
    }

    public static int clarear(int rgb, float k) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r += (int) ((255 - r) * k);
        g += (int) ((255 - g) * k);
        b += (int) ((255 - b) * k);
        return (r << 16) | (g << 8) | b;
    }

    private static float tremor(float x, float y, float z) {
        return 0.88f + 0.12f * Mth.sin(tempo * 0.35f + (x + y * 1.7f + z * 2.3f) * 3f);
    }

    /** Caixa alinhada ao PoseStack atual, com contorno brilhante. */
    public static void box(PoseStack pose, VertexConsumer body, VertexConsumer glow, float x0, float y0, float z0,
                           float x1, float y1, float z1, int rgb, float alpha) {
        Matrix4f m = pose.last().pose();
        int gc = brilho(rgb);
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float gr = ((gc >> 16) & 0xFF) / 255f, gg = ((gc >> 8) & 0xFF) / 255f, gb = (gc & 0xFF) / 255f;
        float tr = tremor(x0, y0, z1);
        cube(body, m, x0, y0, z0, x1, y1, z1, r, g, b, alpha * 0.62f * tr);
        float size = Math.max(Math.max(x1 - x0, y1 - y0), z1 - z0);
        float e = 0.035f + 0.025f * size / 4f;
        cube(glow, m, x0 - e, y0 - e, z0 - e, x1 + e, y1 + e, z1 + e, gr, gg, gb, alpha * 0.13f * tr);
        // núcleo claro
        float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, cz = (z0 + z1) / 2;
        float sx = (x1 - x0) * 0.3f, sy = (y1 - y0) * 0.3f, sz = (z1 - z0) * 0.3f;
        cube(glow, m, cx - sx, cy - sy, cz - sz, cx + sx, cy + sy, cz + sz, 1f, 1f, 1f, alpha * 0.05f);
        // arestas de luz
        float w = Math.max(0.012f, Math.min(0.045f, size * 0.022f));
        int ec = clarear(gc, 0.1f);
        float er = ((ec >> 16) & 0xFF) / 255f, eg = ((ec >> 8) & 0xFF) / 255f, eb = (ec & 0xFF) / 255f;
        float ea = alpha * 0.42f * tr;
        float[] xs = {x0, x1}, ys = {y0, y1}, zs = {z0, z1};
        for (float y : ys) for (float z : zs) cube(glow, m, x0, y - w, z - w, x1, y + w, z + w, er, eg, eb, ea);
        for (float x : xs) for (float z : zs) cube(glow, m, x - w, y0, z - w, x + w, y1, z + w, er, eg, eb, ea);
        for (float x : xs) for (float y : ys) cube(glow, m, x - w, y - w, z0, x + w, y + w, z1, er, eg, eb, ea);
    }

    public static void cube(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                            float r, float g, float b, float a) {
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

    /** Tubo de seção quadrada entre dois pontos. */
    public static void tube(PoseStack pose, VertexConsumer vc, Vec3 from, Vec3 to, float half, int rgb, float alpha) {
        tube(pose, vc, from, to, half, half, rgb, alpha, 4);
    }

    /** Tubo redondo (n lados) que pode afinar ou engrossar do começo ao fim. */
    public static void tube(PoseStack pose, VertexConsumer vc, Vec3 from, Vec3 to, float r0, float r1, int rgb, float alpha, int lados) {
        Vec3 d = to.subtract(from);
        if (d.lengthSqr() < 1e-6) return;
        Vec3 dir = d.normalize();
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dir.cross(up).normalize();
        Vec3 v = dir.cross(u).normalize();
        Matrix4f m = pose.last().pose();
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        for (int i = 0; i < lados; i++) {
            double a0 = Math.PI * 2 * i / lados + Math.PI / lados, a1 = Math.PI * 2 * (i + 1) / lados + Math.PI / lados;
            Vec3 p0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
            Vec3 p1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
            vert(vc, m, from.add(p0.scale(r0)), r, g, b, alpha);
            vert(vc, m, to.add(p0.scale(r1)), r, g, b, alpha);
            vert(vc, m, to.add(p1.scale(r1)), r, g, b, alpha);
            vert(vc, m, from.add(p1.scale(r0)), r, g, b, alpha);
        }
    }

    /** Esfera (bolha) com faixas que brilham e correm, e uma malha de luz por cima. */
    public static void sphere(PoseStack pose, VertexConsumer vc, float radius, int rgb, float alpha, float time) {
        Matrix4f m = pose.last().pose();
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        int lat = 14, lon = 24;
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

    /** Malha de linhas de luz sobre uma esfera (escudo, nova). */
    public static void grade(PoseStack pose, VertexConsumer glow, float radius, int rgb, float alpha, float giro) {
        int lat = 7, lon = 12;
        float w = Math.max(0.015f, radius * 0.012f);
        for (int i = 1; i < lat; i++) {
            double t = Math.PI * i / lat - Math.PI / 2;
            for (int j = 0; j < lon * 2; j++) {
                double p0 = Math.PI * 2 * j / (lon * 2) + giro, p1 = Math.PI * 2 * (j + 1) / (lon * 2) + giro;
                tube(pose, glow, sp(t, p0, radius), sp(t, p1, radius), w, w, rgb, alpha, 4);
            }
        }
        for (int j = 0; j < lon; j++) {
            double p = Math.PI * 2 * j / lon + giro;
            for (int i = 0; i < lat * 2; i++) {
                double t0 = Math.PI * i / (lat * 2) - Math.PI / 2, t1 = Math.PI * (i + 1) / (lat * 2) - Math.PI / 2;
                tube(pose, glow, sp(t0, p, radius), sp(t1, p, radius), w, w, rgb, alpha, 4);
            }
        }
    }

    /** Anel achatado no chão (onda de choque). */
    public static void anel(PoseStack pose, VertexConsumer vc, float raio, float largura, float altura, int rgb, float alpha) {
        Matrix4f m = pose.last().pose();
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        int seg = Math.max(24, (int) (raio * 10));
        float ri = Math.max(0, raio - largura);
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            // topo
            vc.vertex(m, c0 * ri, altura, s0 * ri).color(r, g, b, alpha * 0.2f).endVertex();
            vc.vertex(m, c0 * raio, altura, s0 * raio).color(r, g, b, alpha).endVertex();
            vc.vertex(m, c1 * raio, altura, s1 * raio).color(r, g, b, alpha).endVertex();
            vc.vertex(m, c1 * ri, altura, s1 * ri).color(r, g, b, alpha * 0.2f).endVertex();
            // parede de fora
            vc.vertex(m, c0 * raio, 0, s0 * raio).color(r, g, b, alpha).endVertex();
            vc.vertex(m, c0 * raio, altura, s0 * raio).color(r, g, b, alpha).endVertex();
            vc.vertex(m, c1 * raio, altura, s1 * raio).color(r, g, b, alpha).endVertex();
            vc.vertex(m, c1 * raio, 0, s1 * raio).color(r, g, b, alpha).endVertex();
        }
    }

    /** Disco no chão com o centro mais forte (sombra de energia). */
    public static void disco(PoseStack pose, VertexConsumer vc, float raio, int rgb, float centro, float borda) {
        Matrix4f m = pose.last().pose();
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        int seg = 32;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            vc.vertex(m, 0, 0.02f, 0).color(r, g, b, centro).endVertex();
            vc.vertex(m, 0, 0.02f, 0).color(r, g, b, centro).endVertex();
            vc.vertex(m, (float) Math.cos(a1) * raio, 0.02f, (float) Math.sin(a1) * raio).color(r, g, b, borda).endVertex();
            vc.vertex(m, (float) Math.cos(a0) * raio, 0.02f, (float) Math.sin(a0) * raio).color(r, g, b, borda).endVertex();
        }
    }

    /** Clarão virado para a câmera: miolo forte que se apaga para fora, com raios girando. */
    public static void clarao(PoseStack pose, VertexConsumer glow, Vec3 c, float tam, int rgb, float alpha, int raios) {
        Quaternionf cam = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        pose.pushPose();
        pose.translate(c.x, c.y, c.z);
        pose.mulPose(cam);
        Matrix4f m = pose.last().pose();
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        int seg = 20;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            glow.vertex(m, 0, 0, 0).color(1f, 1f, 1f, alpha).endVertex();
            glow.vertex(m, 0, 0, 0).color(1f, 1f, 1f, alpha).endVertex();
            glow.vertex(m, (float) Math.cos(a1) * tam, (float) Math.sin(a1) * tam, 0).color(r, g, b, 0).endVertex();
            glow.vertex(m, (float) Math.cos(a0) * tam, (float) Math.sin(a0) * tam, 0).color(r, g, b, 0).endVertex();
        }
        for (int k = 0; k < raios; k++) {
            float a = tempo * 0.06f + k * Mth.TWO_PI / raios;
            float len = tam * (1.6f + 0.5f * Mth.sin(tempo * 0.4f + k));
            float wd = tam * 0.07f;
            float cx = Mth.cos(a), sx = Mth.sin(a);
            glow.vertex(m, -sx * wd, cx * wd, 0).color(r, g, b, alpha * 0.8f).endVertex();
            glow.vertex(m, cx * len, sx * len, 0).color(r, g, b, 0).endVertex();
            glow.vertex(m, cx * len, sx * len, 0).color(r, g, b, 0).endVertex();
            glow.vertex(m, sx * wd, -cx * wd, 0).color(r, g, b, alpha * 0.8f).endVertex();
        }
        pose.popPose();
    }

    private static Vec3 sp(double t, double p, float r) {
        return new Vec3(Math.cos(t) * Math.cos(p) * r, Math.sin(t) * r, Math.cos(t) * Math.sin(p) * r);
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).endVertex();
    }

    /** Sai pulando um pouco além do tamanho e assenta (entrada dos construtos). */
    public static float entrada(float age, float dur) {
        float t = Mth.clamp(age / dur, 0, 1);
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    @SuppressWarnings("unused")
    private static Vector3f v(Vec3 p) {
        return new Vector3f((float) p.x, (float) p.y, (float) p.z);
    }

    private EnergyShapes() {}
}
