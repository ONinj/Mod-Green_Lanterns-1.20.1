package com.lanterna.client.escolha;

import com.lanterna.client.render.LanternaRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * Formas de luz para as cerimônias: anel (toro), tubos, anéis no chão, fitas e raios virados para a câmera, brilhos,
 * cristais, moedas, olhos. Tudo desenhado como geometria (não partículas) com sombreamento falso pela normal.
 * Coordenadas: origem nos pés do escolhido, eixos do mundo; {@link #cam} é a câmera nesse mesmo espaço.
 * Cada função pede o buffer na hora (pedir outro tipo fecha o anterior, então ninguém guarda o VertexConsumer).
 */
final class Geo {
    static MultiBufferSource buf;
    static PoseStack ps;
    static Vector3f cam = new Vector3f();
    static float tempo;

    static VertexConsumer luz() {
        return buf.getBuffer(LanternaRenderTypes.BRILHO);
    }

    static VertexConsumer corpo() {
        return buf.getBuffer(LanternaRenderTypes.ENERGIA);
    }

    static float r(int c) { return ((c >> 16) & 255) / 255f; }
    static float g(int c) { return ((c >> 8) & 255) / 255f; }
    static float b(int c) { return (c & 255) / 255f; }

    static int mistura(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255), g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int c, float a, float u, float w) {
        vc.vertex(m, x, y, z).color(r(c), g(c), b(c), Mth.clamp(a, 0, 1)).uv(u, w).endVertex();
    }

    static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, float cr, float cg, float cb, float a, float u, float w) {
        vc.vertex(m, x, y, z).color(Mth.clamp(cr, 0, 1), Mth.clamp(cg, 0, 1), Mth.clamp(cb, 0, 1), Mth.clamp(a, 0, 1)).uv(u, w).endVertex();
    }

    // ---------------- brilhos virados para a câmera (só no espaço base, sem rotação extra) ----------------

    /** Brilho redondo: centro forte, borda some. */
    static void bola(float x, float y, float z, float raio, int c, float a) {
        if (a <= 0.005f || raio <= 0) return;
        Vector3f p = new Vector3f(x, y, z), d = new Vector3f(cam).sub(p);
        if (d.lengthSquared() < 1e-4f) return;
        d.normalize();
        Vector3f dir = new Vector3f(d).cross(0, 1, 0);
        if (dir.lengthSquared() < 1e-4f) dir.set(1, 0, 0);
        dir.normalize();
        Vector3f cima = new Vector3f(dir).cross(d).normalize();
        VertexConsumer vc = luz();
        Matrix4f m = ps.last().pose();
        int seg = 20;
        float cr = Math.min(1, r(c) + 0.35f), cg = Math.min(1, g(c) + 0.35f), cb = Math.min(1, b(c) + 0.35f);
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            float x0 = (dir.x * Mth.cos(a0) + cima.x * Mth.sin(a0)) * raio, y0 = (dir.y * Mth.cos(a0) + cima.y * Mth.sin(a0)) * raio, z0 = (dir.z * Mth.cos(a0) + cima.z * Mth.sin(a0)) * raio;
            float x1 = (dir.x * Mth.cos(a1) + cima.x * Mth.sin(a1)) * raio, y1 = (dir.y * Mth.cos(a1) + cima.y * Mth.sin(a1)) * raio, z1 = (dir.z * Mth.cos(a1) + cima.z * Mth.sin(a1)) * raio;
            v(vc, m, x, y, z, cr, cg, cb, a, 0.5f, 0.5f);
            v(vc, m, x, y, z, cr, cg, cb, a, 0.5f, 0.5f);
            v(vc, m, x + x1, y + y1, z + z1, r(c), g(c), b(c), 0, 0.5f, 0.5f);
            v(vc, m, x + x0, y + y0, z + z0, r(c), g(c), b(c), 0, 0.5f, 0.5f);
        }
    }

    /** Brilho em cruz (estrela): duas faixas finas e longas. */
    static void estrela(float x, float y, float z, float tam, int c, float a, float giro) {
        Vector3f p = new Vector3f(x, y, z), d = new Vector3f(cam).sub(p).normalize();
        Vector3f dir = new Vector3f(d).cross(0, 1, 0);
        if (dir.lengthSquared() < 1e-4f) dir.set(1, 0, 0);
        dir.normalize();
        Vector3f cima = new Vector3f(dir).cross(d).normalize();
        for (int k = 0; k < 2; k++) {
            float ang = giro + k * Mth.HALF_PI;
            Vector3f eixo = new Vector3f(dir).mul(Mth.cos(ang)).add(new Vector3f(cima).mul(Mth.sin(ang)));
            fita(List.of(new Vector3f(p).sub(new Vector3f(eixo).mul(tam)), new Vector3f(p), new Vector3f(p).add(new Vector3f(eixo).mul(tam))),
                    new float[]{0, tam * 0.06f, 0}, c, new float[]{0, a, 0});
        }
    }

    /** Fita virada para a câmera passando pelos pontos (miolo claro, bordas somem). */
    static void fita(List<Vector3f> pts, float[] largura, int c, float[] alfa) {
        fita(pts, largura, c, alfa, true);
    }

    /** aditivo=false: fita escura/opaca (tentáculos de sombra), sem miolo claro. */
    static void fita(List<Vector3f> pts, float[] largura, int c, float[] alfa, boolean aditivo) {
        if (pts.size() < 2) return;
        VertexConsumer vc = aditivo ? luz() : corpo();
        Matrix4f m = ps.last().pose();
        Vector3f[] lado = new Vector3f[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            Vector3f a = pts.get(Math.max(0, i - 1)), b = pts.get(Math.min(pts.size() - 1, i + 1));
            Vector3f t = new Vector3f(b).sub(a);
            Vector3f olho = new Vector3f(cam).sub(pts.get(i));
            Vector3f s = t.cross(olho);
            if (s.lengthSquared() < 1e-8f) s.set(0, 1, 0);
            lado[i] = s.normalize().mul(largura[i] * 0.5f);
        }
        float cr = aditivo ? Math.min(1, r(c) + 0.4f) : r(c), cg = aditivo ? Math.min(1, g(c) + 0.4f) : g(c), cb = aditivo ? Math.min(1, b(c) + 0.4f) : b(c);
        float borda = aditivo ? 0 : 1;
        for (int i = 0; i + 1 < pts.size(); i++) {
            Vector3f p0 = pts.get(i), p1 = pts.get(i + 1), l0 = lado[i], l1 = lado[i + 1];
            float a0 = alfa[i], a1 = alfa[i + 1];
            v(vc, m, p0.x - l0.x, p0.y - l0.y, p0.z - l0.z, r(c), g(c), b(c), a0 * borda, 0.5f, 0.5f);
            v(vc, m, p1.x - l1.x, p1.y - l1.y, p1.z - l1.z, r(c), g(c), b(c), a1 * borda, 0.5f, 0.5f);
            v(vc, m, p1.x, p1.y, p1.z, cr, cg, cb, a1, 0.5f, 0.5f);
            v(vc, m, p0.x, p0.y, p0.z, cr, cg, cb, a0, 0.5f, 0.5f);
            v(vc, m, p0.x, p0.y, p0.z, cr, cg, cb, a0, 0.5f, 0.5f);
            v(vc, m, p1.x, p1.y, p1.z, cr, cg, cb, a1, 0.5f, 0.5f);
            v(vc, m, p1.x + l1.x, p1.y + l1.y, p1.z + l1.z, r(c), g(c), b(c), a1 * borda, 0.5f, 0.5f);
            v(vc, m, p0.x + l0.x, p0.y + l0.y, p0.z + l0.z, r(c), g(c), b(c), a0 * borda, 0.5f, 0.5f);
        }
    }

    /** Linha deitada no chão (rachadura, raio do símbolo). */
    static void linhaChao(float x0, float z0, float x1, float z1, float larg, float y, int c, float a) {
        float dx = x1 - x0, dz = z1 - z0, l = Mth.sqrt(dx * dx + dz * dz);
        if (l < 1e-4f) return;
        float nx = -dz / l * larg / 2, nz = dx / l * larg / 2;
        VertexConsumer vc = luz();
        Matrix4f m = ps.last().pose();
        v(vc, m, x0 - nx * 3, y, z0 - nz * 3, c, 0, 0.5f, 0.5f); v(vc, m, x1 - nx * 3, y, z1 - nz * 3, c, 0, 0.5f, 0.5f);
        v(vc, m, x1, y, z1, c, a, 0.5f, 0.5f); v(vc, m, x0, y, z0, c, a, 0.5f, 0.5f);
        v(vc, m, x0, y, z0, c, a, 0.5f, 0.5f); v(vc, m, x1, y, z1, c, a, 0.5f, 0.5f);
        v(vc, m, x1 + nx * 3, y, z1 + nz * 3, c, 0, 0.5f, 0.5f); v(vc, m, x0 + nx * 3, y, z0 + nz * 3, c, 0, 0.5f, 0.5f);
        float wr = Math.min(1, r(c) + 0.5f), wg = Math.min(1, g(c) + 0.5f), wb = Math.min(1, b(c) + 0.5f);
        v(vc, m, x0 - nx, y + 0.002f, z0 - nz, wr, wg, wb, a, 0.5f, 0.5f); v(vc, m, x1 - nx, y + 0.002f, z1 - nz, wr, wg, wb, a, 0.5f, 0.5f);
        v(vc, m, x1 + nx, y + 0.002f, z1 + nz, wr, wg, wb, a, 0.5f, 0.5f); v(vc, m, x0 + nx, y + 0.002f, z0 + nz, wr, wg, wb, a, 0.5f, 0.5f);
    }

    /** Raio em zigue-zague de a até b (com galhos), semente fixa para não tremer a cada quadro. */
    static void raio(Vector3f a, Vector3f b, long semente, float desvio, int c, float alfa, float espessura) {
        java.util.Random rnd = new java.util.Random(semente);
        int n = 14;
        java.util.List<Vector3f> pts = new java.util.ArrayList<>();
        Vector3f d = new Vector3f(b).sub(a);
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            Vector3f p = new Vector3f(a).add(new Vector3f(d).mul(t));
            if (i > 0 && i < n) p.add((rnd.nextFloat() - 0.5f) * desvio, (rnd.nextFloat() - 0.5f) * desvio, (rnd.nextFloat() - 0.5f) * desvio);
            pts.add(p);
        }
        float[] w = new float[n + 1], al = new float[n + 1], wg = new float[n + 1], alg = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            w[i] = espessura;
            al[i] = alfa;
            wg[i] = espessura * 6;
            alg[i] = alfa * 0.35f;
        }
        fita(pts, wg, c, alg);
        fita(pts, w, 0xFFFFFF, al);
        // galhos
        for (int k = 0; k < 3; k++) {
            int i = 2 + rnd.nextInt(n - 4);
            Vector3f o = pts.get(i);
            Vector3f fim = new Vector3f(o).add((rnd.nextFloat() - 0.5f) * desvio * 3, -rnd.nextFloat() * desvio * 2, (rnd.nextFloat() - 0.5f) * desvio * 3);
            Vector3f meio = new Vector3f(o).lerp(fim, 0.5f).add((rnd.nextFloat() - 0.5f) * desvio, 0, (rnd.nextFloat() - 0.5f) * desvio);
            fita(List.of(o, meio, fim), new float[]{espessura * 0.7f, espessura * 0.5f, 0}, c, new float[]{alfa * 0.8f, alfa * 0.5f, 0});
        }
    }

    // ---------------- formas no pose atual ----------------

    /** Anel (toro) deitado no plano XZ. corpo + brilho em volta. */
    static void toro(float R, float rr, int c, float a, boolean brilho) {
        int su = 36, sv = 10;
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = corpo();
        for (int i = 0; i < su; i++) for (int j = 0; j < sv; j++) {
            float[][] q = new float[4][];
            int[][] ij = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
            for (int k = 0; k < 4; k++) {
                float u = Mth.TWO_PI * ij[k][0] / su, w = Mth.TWO_PI * ij[k][1] / sv;
                float cx = Mth.cos(u), cz = Mth.sin(u);
                float nx = cx * Mth.cos(w), ny = Mth.sin(w), nz = cz * Mth.cos(w);
                q[k] = new float[]{cx * R + nx * rr, ny * rr, cz * R + nz * rr, ny, nx};
            }
            for (float[] p : q) {
                float sh = 0.55f + 0.45f * Math.max(0, p[3] * 0.8f + p[4] * 0.3f);
                float spec = (float) Math.pow(Math.max(0, p[3]), 12) * 0.6f;
                v(vc, m, p[0], p[1], p[2], r(c) * sh + spec, g(c) * sh + spec, b(c) * sh + spec, a, p[0] * 4, p[2] * 4);
            }
        }
        if (!brilho) return;
        VertexConsumer lz = luz();
        // aro de luz por fora e por dentro (faixa plana)
        for (int i = 0; i < su; i++) {
            float u0 = Mth.TWO_PI * i / su, u1 = Mth.TWO_PI * (i + 1) / su;
            for (float[] f : new float[][]{{R + rr, R + rr * 4}, {R - rr, Math.max(0, R - rr * 3)}}) {
                v(lz, m, Mth.cos(u0) * f[0], 0, Mth.sin(u0) * f[0], c, a * 0.8f, 0.5f, 0.5f);
                v(lz, m, Mth.cos(u1) * f[0], 0, Mth.sin(u1) * f[0], c, a * 0.8f, 0.5f, 0.5f);
                v(lz, m, Mth.cos(u1) * f[1], 0, Mth.sin(u1) * f[1], c, 0, 0.5f, 0.5f);
                v(lz, m, Mth.cos(u0) * f[1], 0, Mth.sin(u0) * f[1], c, 0, 0.5f, 0.5f);
            }
        }
    }

    /** O anel de poder: toro com uma joia em cima, girando; com halo. */
    static void anel(float x, float y, float z, float tam, int c, float a, float giro, float incl) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(giro));
        ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(incl));
        ps.scale(tam, tam, tam);
        toro(1f, 0.22f, c, a, true);
        // joia: octaedro achatado na borda do aro
        ps.translate(0, 0, -1.05f);
        ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
        cristal(0.5f, 0.38f, mistura(c, 0xFFFFFF, 0.3f), a, 6, true);
        ps.popPose();
        bola(x, y, z, tam * 3.5f, c, a * 0.45f);
    }

    private static final java.util.Map<String, net.minecraft.world.item.ItemStack> ANEIS = new java.util.HashMap<>();

    /**
     * O anel de verdade: o mesmo modelo 3D do item (o de cada Tropa), girando, com halo de luz em volta.
     * O modelo fica de pé (furo para a frente); incl inclina, giro roda em volta do eixo vertical.
     */
    static void anelModelo(String corps, float x, float y, float z, float tam, int c, float a, float giro, float incl) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (a <= 0.02f || mc.level == null) return;
        net.minecraft.world.item.ItemStack st = ANEIS.computeIfAbsent(corps, k ->
                com.lanterna.item.PowerRingItem.create(new net.minecraft.resources.ResourceLocation(k), c, 1));
        bola(x, y, z, tam * 3.2f, c, a * 0.4f);
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(giro));
        ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(incl));
        float s = tam / 0.23f * Math.min(1, a * 1.2f);
        ps.scale(s, s, s);
        ps.translate(0, -0.08f, 0); // o modelo tem a joia em cima; centra o aro
        mc.getItemRenderer().renderStatic(st, net.minecraft.world.item.ItemDisplayContext.FIXED, 0xF000F0,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, ps, buf, mc.level, 0);
        ps.popPose();
        // aro de luz fino em volta do anel, no plano dele
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(giro));
        ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(incl + 90));
        ps.scale(tam, tam, tam);
        VertexConsumer lz = luz();
        Matrix4f m = ps.last().pose();
        int su = 36;
        for (int i = 0; i < su; i++) {
            float u0 = Mth.TWO_PI * i / su, u1 = Mth.TWO_PI * (i + 1) / su;
            v(lz, m, Mth.cos(u0) * 1.05f, 0, Mth.sin(u0) * 1.05f, c, a * 0.55f, 0.5f, 0.5f);
            v(lz, m, Mth.cos(u1) * 1.05f, 0, Mth.sin(u1) * 1.05f, c, a * 0.55f, 0.5f, 0.5f);
            v(lz, m, Mth.cos(u1) * 1.9f, 0, Mth.sin(u1) * 1.9f, c, 0, 0.5f, 0.5f);
            v(lz, m, Mth.cos(u0) * 1.9f, 0, Mth.sin(u0) * 1.9f, c, 0, 0.5f, 0.5f);
        }
        ps.popPose();
    }

    /** Tubo vertical aberto (coluna de luz, chama, feixe), alfa de baixo para cima, com ondulação. */
    static void tubo(float raio, float y0, float y1, int c, float aBaixo, float aCima, float onda, boolean aditivo) {
        int seg = 28, an = 10;
        VertexConsumer vc = aditivo ? luz() : corpo();
        Matrix4f m = ps.last().pose();
        for (int k = 0; k < an; k++) {
            float t0 = k / (float) an, t1 = (k + 1) / (float) an;
            float ya = Mth.lerp(t0, y0, y1), yb = Mth.lerp(t1, y0, y1);
            float aa = Mth.lerp(t0, aBaixo, aCima), ab = Mth.lerp(t1, aBaixo, aCima);
            for (int i = 0; i < seg; i++) {
                float u0 = Mth.TWO_PI * i / seg, u1 = Mth.TWO_PI * (i + 1) / seg;
                float ra0 = raio * (1 + onda * Mth.sin(u0 * 3 + ya * 1.7f - tempo * 0.3f)), ra1 = raio * (1 + onda * Mth.sin(u1 * 3 + ya * 1.7f - tempo * 0.3f));
                float rb0 = raio * (1 + onda * Mth.sin(u0 * 3 + yb * 1.7f - tempo * 0.3f)), rb1 = raio * (1 + onda * Mth.sin(u1 * 3 + yb * 1.7f - tempo * 0.3f));
                float uu0 = i / (float) seg * 3, uu1 = (i + 1) / (float) seg * 3;
                v(vc, m, Mth.cos(u0) * ra0, ya, Mth.sin(u0) * ra0, c, aa, uu0, ya * 0.25f - tempo * 0.03f);
                v(vc, m, Mth.cos(u1) * ra1, ya, Mth.sin(u1) * ra1, c, aa, uu1, ya * 0.25f - tempo * 0.03f);
                v(vc, m, Mth.cos(u1) * rb1, yb, Mth.sin(u1) * rb1, c, ab, uu1, yb * 0.25f - tempo * 0.03f);
                v(vc, m, Mth.cos(u0) * rb0, yb, Mth.sin(u0) * rb0, c, ab, uu0, yb * 0.25f - tempo * 0.03f);
            }
        }
    }

    /** Anel deitado (onda no chão, círculo de luz). */
    static void anelChao(float r0, float r1, float y, int c, float aDentro, float aFora, boolean aditivo) {
        if (r1 <= 0) return;
        int seg = 48;
        VertexConsumer vc = aditivo ? luz() : corpo();
        Matrix4f m = ps.last().pose();
        for (int i = 0; i < seg; i++) {
            float u0 = Mth.TWO_PI * i / seg, u1 = Mth.TWO_PI * (i + 1) / seg;
            v(vc, m, Mth.cos(u0) * r0, y, Mth.sin(u0) * r0, c, aDentro, Mth.cos(u0) * r0 * 0.3f, Mth.sin(u0) * r0 * 0.3f);
            v(vc, m, Mth.cos(u1) * r0, y, Mth.sin(u1) * r0, c, aDentro, Mth.cos(u1) * r0 * 0.3f, Mth.sin(u1) * r0 * 0.3f);
            v(vc, m, Mth.cos(u1) * r1, y, Mth.sin(u1) * r1, c, aFora, Mth.cos(u1) * r1 * 0.3f, Mth.sin(u1) * r1 * 0.3f);
            v(vc, m, Mth.cos(u0) * r1, y, Mth.sin(u0) * r1, c, aFora, Mth.cos(u0) * r1 * 0.3f, Mth.sin(u0) * r1 * 0.3f);
        }
    }

    /** Onda que abre: faixa brilhante com rastro para dentro. */
    static void onda(float raio, float largura, float y, int c, float a) {
        anelChao(Math.max(0, raio - largura), raio, y, c, 0, a, true);
        anelChao(raio, raio + largura * 0.25f, y, c, a, 0, true);
    }

    /** Cristal: prisma de n lados com ponta, ao longo de +Y (alt), raio r. corpo translúcido sombreado por face. */
    static void cristal(float alt, float r, int c, float a, int n, boolean duasPontas) {
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = corpo();
        float corpoAlt = alt * 0.68f, base = duasPontas ? -alt * 0.32f : 0;
        for (int i = 0; i < n; i++) {
            float u0 = Mth.TWO_PI * i / n, u1 = Mth.TWO_PI * (i + 1) / n;
            float sh = 0.55f + 0.45f * Math.max(0, Mth.cos((u0 + u1) / 2 - 0.8f));
            float cr = r(c) * sh, cg = g(c) * sh, cb = b(c) * sh;
            float x0 = Mth.cos(u0) * r, z0 = Mth.sin(u0) * r, x1 = Mth.cos(u1) * r, z1 = Mth.sin(u1) * r;
            // lado
            v(vc, m, x0, 0, z0, cr, cg, cb, a, 0, 0); v(vc, m, x1, 0, z1, cr, cg, cb, a, 1, 0);
            v(vc, m, x1, corpoAlt, z1, cr, cg, cb, a, 1, 1); v(vc, m, x0, corpoAlt, z0, cr, cg, cb, a, 0, 1);
            // ponta de cima (mais clara)
            float sp = Math.min(1, sh + 0.25f);
            v(vc, m, x0, corpoAlt, z0, r(c) * sp, g(c) * sp, b(c) * sp, a, 0, 0); v(vc, m, x1, corpoAlt, z1, r(c) * sp, g(c) * sp, b(c) * sp, a, 1, 0);
            v(vc, m, 0, alt, 0, 1, 1, 1, a, 0.5f, 1); v(vc, m, 0, alt, 0, 1, 1, 1, a, 0.5f, 1);
            // ponta de baixo
            if (duasPontas) {
                v(vc, m, x0, 0, z0, cr * 0.7f, cg * 0.7f, cb * 0.7f, a, 0, 0); v(vc, m, x1, 0, z1, cr * 0.7f, cg * 0.7f, cb * 0.7f, a, 1, 0);
                v(vc, m, 0, base, 0, cr * 0.5f, cg * 0.5f, cb * 0.5f, a, 0.5f, 1); v(vc, m, 0, base, 0, cr * 0.5f, cg * 0.5f, cb * 0.5f, a, 0.5f, 1);
            }
        }
        // arestas de luz
        VertexConsumer lz = luz();
        for (int i = 0; i < n; i++) {
            float u0 = Mth.TWO_PI * i / n;
            float x0 = Mth.cos(u0) * r, z0 = Mth.sin(u0) * r, w = r * 0.08f;
            v(lz, m, x0 - w, 0, z0, c, a * 0.5f, 0.5f, 0.5f); v(lz, m, x0 + w, 0, z0, c, a * 0.5f, 0.5f, 0.5f);
            v(lz, m, x0 + w, corpoAlt, z0, c, a * 0.7f, 0.5f, 0.5f); v(lz, m, x0 - w, corpoAlt, z0, c, a * 0.7f, 0.5f, 0.5f);
        }
    }

    /** Moeda: disco curto em pé (eixo Z), com bordas. */
    static void moeda(float raio, float esp, int c, float a) {
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = corpo();
        int seg = 14;
        for (int i = 0; i < seg; i++) {
            float u0 = Mth.TWO_PI * i / seg, u1 = Mth.TWO_PI * (i + 1) / seg;
            float x0 = Mth.cos(u0) * raio, y0 = Mth.sin(u0) * raio, x1 = Mth.cos(u1) * raio, y1 = Mth.sin(u1) * raio;
            for (float z : new float[]{-esp / 2, esp / 2}) {
                float sh = z > 0 ? 1f : 0.75f;
                v(vc, m, 0, 0, z, r(c) * sh + 0.2f, g(c) * sh + 0.2f, b(c) * sh + 0.1f, a, 0.5f, 0.5f);
                v(vc, m, 0, 0, z, r(c) * sh + 0.2f, g(c) * sh + 0.2f, b(c) * sh + 0.1f, a, 0.5f, 0.5f);
                v(vc, m, x1, y1, z, r(c) * sh, g(c) * sh, b(c) * sh, a, 0, 0);
                v(vc, m, x0, y0, z, r(c) * sh, g(c) * sh, b(c) * sh, a, 0, 0);
            }
            float sh = 0.6f;
            v(vc, m, x0, y0, -esp / 2, r(c) * sh, g(c) * sh, b(c) * sh, a, 0, 0); v(vc, m, x1, y1, -esp / 2, r(c) * sh, g(c) * sh, b(c) * sh, a, 0, 0);
            v(vc, m, x1, y1, esp / 2, r(c) * sh, g(c) * sh, b(c) * sh, a, 0, 0); v(vc, m, x0, y0, esp / 2, r(c) * sh, g(c) * sh, b(c) * sh, a, 0, 0);
        }
    }

    /** Olho na escuridão, virado para a câmera: amêndoa brilhante com pupila em fenda. abertura 0..1. */
    static void olho(float x, float y, float z, float larg, float abertura, int c, float a) {
        if (abertura <= 0.02f || a <= 0.01f) return;
        Vector3f p = new Vector3f(x, y, z), d = new Vector3f(cam).sub(p).normalize();
        Vector3f dir = new Vector3f(d).cross(0, 1, 0).normalize();
        Vector3f cima = new Vector3f(dir).cross(d).normalize();
        Matrix4f m = ps.last().pose();
        int seg = 16;
        float alt = larg * 0.42f * abertura;
        VertexConsumer lz = luz();
        // amêndoa
        for (int i = 0; i < seg; i++) {
            float t0 = i / (float) seg, t1 = (i + 1) / (float) seg;
            for (int lado = -1; lado <= 1; lado += 2) {
                float x0 = (t0 * 2 - 1) * larg, x1 = (t1 * 2 - 1) * larg;
                float h0 = lado * alt * Mth.sin(t0 * Mth.PI), h1 = lado * alt * Mth.sin(t1 * Mth.PI);
                Vector3f a0 = new Vector3f(p).add(new Vector3f(dir).mul(x0)), a1 = new Vector3f(p).add(new Vector3f(dir).mul(x1));
                Vector3f b0 = new Vector3f(a0).add(new Vector3f(cima).mul(h0)), b1 = new Vector3f(a1).add(new Vector3f(cima).mul(h1));
                v(lz, m, a0.x, a0.y, a0.z, c, a, 0.5f, 0.5f); v(lz, m, a1.x, a1.y, a1.z, c, a, 0.5f, 0.5f);
                v(lz, m, b1.x, b1.y, b1.z, c, a * 0.6f, 0.5f, 0.5f); v(lz, m, b0.x, b0.y, b0.z, c, a * 0.6f, 0.5f, 0.5f);
            }
        }
        // pupila (fenda escura)
        VertexConsumer vc = corpo();
        float pw = larg * 0.12f, ph = alt * 0.95f;
        Vector3f off = new Vector3f(d).mul(0.02f);
        Vector3f c0 = new Vector3f(p).add(off);
        for (int i = 0; i < 2; i++) {
            float s = i == 0 ? 1 : -1;
            Vector3f top = new Vector3f(c0).add(new Vector3f(cima).mul(ph * s));
            Vector3f l = new Vector3f(c0).add(new Vector3f(dir).mul(-pw)), rr = new Vector3f(c0).add(new Vector3f(dir).mul(pw));
            v(vc, m, l.x, l.y, l.z, 0.05f, 0.03f, 0f, a, 0.5f, 0.5f); v(vc, m, rr.x, rr.y, rr.z, 0.05f, 0.03f, 0f, a, 0.5f, 0.5f);
            v(vc, m, top.x, top.y, top.z, 0.05f, 0.03f, 0f, a, 0.5f, 0.5f); v(vc, m, top.x, top.y, top.z, 0.05f, 0.03f, 0f, a, 0.5f, 0.5f);
        }
        bola(x, y, z, larg * 2.2f, c, a * 0.3f);
    }

    /** Pétala: lâmina curva (2 quads) ao longo de +Y, no pose atual. */
    static void petala(float comp, float larg, int c, float a) {
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = corpo();
        float[] ys = {0, comp * 0.35f, comp * 0.75f, comp};
        float[] ws = {0, larg, larg * 0.75f, 0};
        float[] zs = {0, comp * 0.08f, comp * 0.12f, comp * 0.05f};
        for (int i = 0; i < 3; i++) {
            float sh0 = 0.7f + 0.3f * i / 3f, sh1 = 0.7f + 0.3f * (i + 1) / 3f;
            v(vc, m, -ws[i], ys[i], zs[i], r(c) * sh0, g(c) * sh0, b(c) * sh0, a, 0, 0);
            v(vc, m, ws[i], ys[i], zs[i], r(c) * sh0, g(c) * sh0, b(c) * sh0, a, 1, 0);
            v(vc, m, ws[i + 1], ys[i + 1], zs[i + 1], r(c) * sh1, g(c) * sh1, b(c) * sh1, a, 1, 1);
            v(vc, m, -ws[i + 1], ys[i + 1], zs[i + 1], r(c) * sh1, g(c) * sh1, b(c) * sh1, a, 0, 1);
        }
    }

    private Geo() {}
}
