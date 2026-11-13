package com.lanterna.client.space;

import com.lanterna.LanternaMod;
import com.lanterna.space.Perigos;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Desenho dos sóis e buracos negros no céu do espaço (mesmo truque dos planetas: o corpo inteiro é encolhido para
 * caber no alcance da câmera, mantendo o tamanho aparente).
 *  - Sol: esfera brilhante com a textura do sol tingida na cor da estrela, coroa e um halo grande.
 *  - Buraco negro: esfera preta, disco de acreção girando (com a metade da frente passando na frente do buraco),
 *    anel de fótons e um brilho fraco de lente em volta.
 */
final class PerigosRender {
    private static final ResourceLocation SOL = LanternaMod.id("textures/space/sun.png");

    static void desenhar(Matrix4f pose, Vec3 cam, Perigos.Perigo h, float time) {
        Vec3 rel = h.centro().subtract(cam);
        double dist = rel.length();
        if (dist <= h.raio()) return;
        double apparent = h.raioVisual() / dist;
        if (apparent < (h.tipo() == Perigos.Tipo.SOL ? 0.0012 : 0.002)) return;
        double scale = Math.min(1.0, SpaceSkyEffects.DRAW_LIMIT / (dist + h.raioVisual()));
        Vec3 pos = rel.scale(scale);
        float r = (float) (h.raio() * scale);
        if (h.tipo() == Perigos.Tipo.SOL) sol(pose, pos, r, h, time, apparent);
        else buraco(pose, pos, r, h, time);
    }

    // ------------------------------------------------------------------ sol

    private static void sol(Matrix4f pose, Vec3 pos, float r, Perigos.Perigo h, float time, double apparent) {
        int c = h.cor();
        float cr = ((c >> 16) & 0xFF) / 255f, cg = ((c >> 8) & 0xFF) / 255f, cb = (c & 0xFF) / 255f;
        // halo grande primeiro (atrás de tudo)
        halo(pose, pos, r * 3.2f, cr, cg, cb, 0.35f);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, SOL);
        RenderSystem.defaultBlendFunc();
        int lat = apparent > 0.05 ? 24 : 12, lon = lat * 2;
        float spin = time * 0.00004f;
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < lat; i++)
            for (int j = 0; j < lon; j++) {
                sv(b, pose, pos, r, i, j, lat, lon, spin, cr, cg, cb);
                sv(b, pose, pos, r, i, j + 1, lat, lon, spin, cr, cg, cb);
                sv(b, pose, pos, r, i + 1, j + 1, lat, lon, spin, cr, cg, cb);
                sv(b, pose, pos, r, i + 1, j, lat, lon, spin, cr, cg, cb);
            }
        BufferUploader.drawWithShader(b.end());
        float pulso = 0.9f + 0.1f * Mth.sin(time * 0.05f);
        SpaceSkyEffects.drawAtmosphere(pose, pos, r * 1.08f, c, 1.4f * pulso);
        SpaceSkyEffects.drawAtmosphere(pose, pos, r * 1.3f, c, 0.8f * pulso);
    }

    private static void sv(BufferBuilder b, Matrix4f pose, Vec3 c, float radius, int lat, int lon, int latN, int lonN,
                           float spin, float cr, float cg, float cb) {
        float theta = (float) Math.PI * lat / latN, phi = (float) Math.PI * 2 * lon / lonN;
        float nx = Mth.sin(theta) * Mth.cos(phi), ny = Mth.cos(theta), nz = Mth.sin(theta) * Mth.sin(phi);
        // clareia a cor (o miolo da estrela é quase branco)
        b.vertex(pose, (float) (c.x + nx * radius), (float) (c.y + ny * radius), (float) (c.z + nz * radius))
                .uv((float) lon / lonN + spin, (float) lat / latN)
                .color(Math.min(1f, 0.55f + cr * 0.6f), Math.min(1f, 0.55f + cg * 0.6f), Math.min(1f, 0.55f + cb * 0.6f), 1f).endVertex();
    }

    /** Disco virado para a câmera, forte no meio e sumindo na borda (luz espalhada). */
    private static void halo(Matrix4f pose, Vec3 c, float raio, float r, float g, float bl, float alpha) {
        Vec3 toCam = c.scale(-1).normalize();
        Vec3 right = new Vec3(0, 1, 0).cross(toCam);
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = toCam.cross(right).normalize();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int n = 48;
        for (int k = 0; k < n; k++) {
            double a0 = Math.PI * 2 * k / n, a1 = Math.PI * 2 * (k + 1) / n;
            b.vertex(pose, (float) c.x, (float) c.y, (float) c.z).color(r, g, bl, alpha).endVertex();
            Vec3 p0 = c.add(right.scale(Math.cos(a0) * raio)).add(up.scale(Math.sin(a0) * raio));
            Vec3 p1 = c.add(right.scale(Math.cos(a1) * raio)).add(up.scale(Math.sin(a1) * raio));
            b.vertex(pose, (float) p0.x, (float) p0.y, (float) p0.z).color(r, g, bl, 0f).endVertex();
            b.vertex(pose, (float) p1.x, (float) p1.y, (float) p1.z).color(r, g, bl, 0f).endVertex();
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    // ------------------------------------------------------------------ buraco negro

    private static void buraco(Matrix4f pose, Vec3 pos, float r, Perigos.Perigo h, float time) {
        int c = h.cor();
        float cr = ((c >> 16) & 0xFF) / 255f, cg = ((c >> 8) & 0xFF) / 255f, cb = (c & 0xFF) / 255f;
        // eixo do disco: inclinado (cada buraco do seu jeito)
        double inc = h.inclinacao();
        Vec3 n = new Vec3(Math.sin(inc), Math.cos(inc), Math.sin(inc * 0.7) * 0.4).normalize();
        Vec3 u = n.cross(new Vec3(0, 0, 1));
        if (u.lengthSqr() < 1e-6) u = n.cross(new Vec3(1, 0, 0));
        u = u.normalize();
        Vec3 w = n.cross(u).normalize();
        Vec3 toCam = pos.scale(-1).normalize();

        halo(pose, pos, r * 9f, 0.55f, 0.35f, 0.75f, 0.12f);   // lente: um brilho fraco arroxeado
        disco(pose, pos, r, u, w, toCam, time, cr, cg, cb, 0);      // metade de trás
        // o buraco: preto, por cima do disco de trás
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.defaultBlendFunc();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int lat = 16, lon = 32;
        for (int i = 0; i < lat; i++)
            for (int j = 0; j < lon; j++) {
                bv(b, pose, pos, r, i, j, lat, lon);
                bv(b, pose, pos, r, i, j + 1, lat, lon);
                bv(b, pose, pos, r, i + 1, j + 1, lat, lon);
                bv(b, pose, pos, r, i + 1, j, lat, lon);
            }
        BufferUploader.drawWithShader(b.end());
        // anel de fótons colado na borda
        SpaceSkyEffects.drawAtmosphere(pose, pos, r * 1.12f, 0xFFF0D0, 3.5f);
        disco(pose, pos, r, u, w, toCam, time, cr, cg, cb, 1);      // metade da frente
    }

    private static void bv(BufferBuilder b, Matrix4f pose, Vec3 c, float radius, int lat, int lon, int latN, int lonN) {
        float theta = (float) Math.PI * lat / latN, phi = (float) Math.PI * 2 * lon / lonN;
        float nx = Mth.sin(theta) * Mth.cos(phi), ny = Mth.cos(theta), nz = Mth.sin(theta) * Mth.sin(phi);
        b.vertex(pose, (float) (c.x + nx * radius), (float) (c.y + ny * radius), (float) (c.z + nz * radius)).color(0f, 0f, 0f, 1f).endVertex();
    }

    /** Disco de acreção (anel achatado de gás quente girando); metade 0 = de trás, 1 = da frente. */
    private static void disco(Matrix4f pose, Vec3 c, float r, Vec3 u, Vec3 w, Vec3 toCam, float time,
                              float cr, float cg, float cb, int metade) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int seg = 96, aneis = 7;
        float r0 = r * 1.6f, r1 = r * 7f;
        for (int a = 0; a < aneis; a++) {
            float ra = r0 + (r1 - r0) * a / aneis, rb = r0 + (r1 - r0) * (a + 1) / aneis;
            for (int k = 0; k < seg; k++) {
                double t0 = Math.PI * 2 * k / seg, t1 = Math.PI * 2 * (k + 1) / seg;
                double tm = (t0 + t1) / 2;
                Vec3 meio = u.scale(Math.cos(tm)).add(w.scale(Math.sin(tm)));
                boolean frente = meio.dot(toCam) > 0;
                if (frente != (metade == 1)) continue;
                dv(b, pose, c, u, w, ra, t0, a, aneis, k, seg, time, cr, cg, cb);
                dv(b, pose, c, u, w, ra, t1, a, aneis, k + 1, seg, time, cr, cg, cb);
                dv(b, pose, c, u, w, rb, t1, a + 1, aneis, k + 1, seg, time, cr, cg, cb);
                dv(b, pose, c, u, w, rb, t0, a + 1, aneis, k, seg, time, cr, cg, cb);
            }
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    private static void dv(BufferBuilder b, Matrix4f pose, Vec3 c, Vec3 u, Vec3 w, float rad, double t, int anel, int aneis,
                           int k, int seg, float time, float cr, float cg, float cb) {
        float f = (float) anel / aneis;                    // 0 = borda de dentro (mais quente)
        // braços em espiral girando (mais rápido perto do buraco)
        float giro = time * (0.05f + 0.12f * (1 - f));
        float espiral = 0.65f + 0.35f * Mth.sin((float) t * 3f + f * 9f - giro);
        float quente = 1 - f;
        float rr = Mth.lerp(quente, cr * 0.9f, 1f), gg = Mth.lerp(quente, cg * 0.55f, 0.95f), bb = Mth.lerp(quente, cb * 0.3f, 0.85f);
        float alpha = Mth.clamp((anel == 0 ? 0.55f : 1f) * (1 - f) * 1.1f * espiral, 0f, 1f);
        Vec3 p = c.add(u.scale(Math.cos(t) * rad)).add(w.scale(Math.sin(t) * rad));
        b.vertex(pose, (float) p.x, (float) p.y, (float) p.z).color(rr, gg, bb, alpha).endVertex();
    }

    private PerigosRender() {}
}
