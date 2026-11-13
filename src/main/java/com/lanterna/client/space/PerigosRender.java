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
 *  - Sol: superfície granulada (textura em cinza tingida na cor da estrela) com o miolo quase branco e a borda mais
 *    escura e quente (escurecimento de borda), coroa, raios de luz girando devagar e um halo grande.
 *  - Buraco negro: a sombra preta e densa, o disco de acreção grosso e turbulento girando (o lado que vem na sua direção
 *    brilha mais), a imagem do lado de trás do disco curvada por cima e por baixo da sombra (lente), o anel de fótons
 *    fino colado na borda e um brilho quente em volta.
 */
final class PerigosRender {
    private static final ResourceLocation SOL = LanternaMod.id("textures/space/sol_base.png");
    private static final ResourceLocation DISCO = LanternaMod.id("textures/space/disco_buraco.png");

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

    private static float[] rgb(int c) {
        return new float[]{((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f};
    }

    /** Base (direita e cima) de um plano virado para a câmera. */
    private static Vec3[] base(Vec3 c) {
        Vec3 toCam = c.scale(-1).normalize();
        Vec3 right = new Vec3(0, 1, 0).cross(toCam);
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        return new Vec3[]{right, toCam.cross(right).normalize(), toCam};
    }

    // ------------------------------------------------------------------ sol

    private static void sol(Matrix4f pose, Vec3 pos, float r, Perigos.Perigo h, float time, double apparent) {
        float[] c = rgb(h.cor());
        // halo grande e um brilho médio (luz espalhada)
        halo(pose, pos, r * 4.2f, c, 0.22f);
        halo(pose, pos, r * 1.9f, c, 0.45f);
        raios(pose, pos, r, c, time, h.inclinacao());
        // superfície
        Vec3 toCam = pos.scale(-1).normalize();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, SOL);
        RenderSystem.defaultBlendFunc();
        int lat = apparent > 0.05 ? 28 : 14, lon = lat * 2;
        float spin = time * 0.00003f + (float) h.inclinacao() * 0.01f;
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < lat; i++)
            for (int j = 0; j < lon; j++) {
                sv(b, pose, pos, r, i, j, lat, lon, spin, c, toCam);
                sv(b, pose, pos, r, i, j + 1, lat, lon, spin, c, toCam);
                sv(b, pose, pos, r, i + 1, j + 1, lat, lon, spin, c, toCam);
                sv(b, pose, pos, r, i + 1, j, lat, lon, spin, c, toCam);
            }
        BufferUploader.drawWithShader(b.end());
        // brilho por cima da superfície (a estrela emite luz; sem isso ela parece uma lua)
        SpaceSkyEffects.drawAtmosphere(pose, pos, r * 1.0005f, h.cor(), 0.9f);
        halo(pose, pos, r * 1.05f, new float[]{Math.min(1, c[0] * 0.6f + 0.4f), Math.min(1, c[1] * 0.6f + 0.4f), Math.min(1, c[2] * 0.6f + 0.4f)}, 0.35f);
        // coroa colada na borda
        float pulso = 0.9f + 0.1f * Mth.sin(time * 0.05f);
        SpaceSkyEffects.drawAtmosphere(pose, pos, r * 1.03f, h.cor(), 1.8f * pulso);
        SpaceSkyEffects.drawAtmosphere(pose, pos, r * 1.16f, h.cor(), 0.9f * pulso);
    }

    private static void sv(BufferBuilder b, Matrix4f pose, Vec3 c, float radius, int lat, int lon, int latN, int lonN,
                           float spin, float[] cor, Vec3 toCam) {
        float theta = (float) Math.PI * lat / latN, phi = (float) Math.PI * 2 * lon / lonN;
        float nx = Mth.sin(theta) * Mth.cos(phi), ny = Mth.cos(theta), nz = Mth.sin(theta) * Mth.sin(phi);
        // escurecimento de borda: o miolo quase branco, a borda na cor da estrela e mais escura
        float f = (float) Math.max(0, nx * toCam.x + ny * toCam.y + nz * toCam.z);
        float branco = 0.6f * (float) Math.pow(f, 1.3);
        float luz = 0.72f + 0.4f * (float) Math.sqrt(f);
        float r = Math.min(1f, Mth.lerp(branco, cor[0], 1f) * luz), g = Math.min(1f, Mth.lerp(branco, cor[1], 1f) * luz),
                bl = Math.min(1f, Mth.lerp(branco, cor[2], 1f) * luz);
        b.vertex(pose, (float) (c.x + nx * radius), (float) (c.y + ny * radius), (float) (c.z + nz * radius))
                .uv((float) lon / lonN + spin, (float) lat / latN).color(r, g, bl, 1f).endVertex();
    }

    /** Disco virado para a câmera, forte no meio e sumindo na borda (luz espalhada). */
    private static void halo(Matrix4f pose, Vec3 c, float raio, float[] cor, float alpha) {
        Vec3[] bs = base(c);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int n = 48;
        for (int k = 0; k < n; k++) {
            double a0 = Math.PI * 2 * k / n, a1 = Math.PI * 2 * (k + 1) / n;
            b.vertex(pose, (float) c.x, (float) c.y, (float) c.z).color(cor[0], cor[1], cor[2], alpha).endVertex();
            Vec3 p0 = c.add(bs[0].scale(Math.cos(a0) * raio)).add(bs[1].scale(Math.sin(a0) * raio));
            Vec3 p1 = c.add(bs[0].scale(Math.cos(a1) * raio)).add(bs[1].scale(Math.sin(a1) * raio));
            b.vertex(pose, (float) p0.x, (float) p0.y, (float) p0.z).color(cor[0], cor[1], cor[2], 0f).endVertex();
            b.vertex(pose, (float) p1.x, (float) p1.y, (float) p1.z).color(cor[0], cor[1], cor[2], 0f).endVertex();
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    /** Raios de luz finos saindo da estrela, girando devagar. */
    private static void raios(Matrix4f pose, Vec3 c, float r, float[] cor, float time, double semente) {
        Vec3[] bs = base(c);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int n = 14;
        for (int k = 0; k < n; k++) {
            double s = Math.sin(semente * 13.7 + k * 4.1);
            double a = Math.PI * 2 * k / n + time * 0.0006 + s * 0.2;
            float comp = r * (2.0f + 1.4f * (float) Math.abs(s));
            float larg = r * 0.09f;
            Vec3 dir = bs[0].scale(Math.cos(a)).add(bs[1].scale(Math.sin(a)));
            Vec3 lado = bs[0].scale(-Math.sin(a)).add(bs[1].scale(Math.cos(a)));
            Vec3 p0 = c.add(dir.scale(r * 0.8)).add(lado.scale(larg)), p1 = c.add(dir.scale(r * 0.8)).subtract(lado.scale(larg));
            Vec3 ponta = c.add(dir.scale(comp));
            b.vertex(pose, (float) p0.x, (float) p0.y, (float) p0.z).color(cor[0], cor[1], cor[2], 0.2f).endVertex();
            b.vertex(pose, (float) p1.x, (float) p1.y, (float) p1.z).color(cor[0], cor[1], cor[2], 0.2f).endVertex();
            b.vertex(pose, (float) ponta.x, (float) ponta.y, (float) ponta.z).color(cor[0], cor[1], cor[2], 0f).endVertex();
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    // ------------------------------------------------------------------ buraco negro

    private static void buraco(Matrix4f pose, Vec3 pos, float r, Perigos.Perigo h, float time) {
        float[] c = rgb(h.cor());
        // eixo do disco: inclinado (cada buraco do seu jeito)
        double inc = h.inclinacao();
        Vec3 n = new Vec3(Math.sin(inc), Math.cos(inc), Math.sin(inc * 0.7) * 0.4).normalize();
        Vec3 u = n.cross(new Vec3(0, 0, 1));
        if (u.lengthSqr() < 1e-6) u = n.cross(new Vec3(1, 0, 0));
        u = u.normalize();
        Vec3 w = n.cross(u).normalize();
        Vec3[] bs = base(pos);
        Vec3 toCam = bs[2];
        float sombra = r * 1.3f;

        halo(pose, pos, r * 10f, new float[]{c[0], c[1] * 0.7f, c[2] * 0.5f}, 0.10f);
        halo(pose, pos, r * 4.5f, c, 0.22f);
        disco(pose, pos, r, u, w, n, toCam, time, c, 0);                 // metade de trás
        lente(pose, pos, sombra, bs, u, w, toCam, time, c);              // o lado de trás curvado por cima e por baixo
        // a sombra: um disco preto opaco virado para você
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int seg = 64;
        for (int k = 0; k < seg; k++) {
            double a0 = Math.PI * 2 * k / seg, a1 = Math.PI * 2 * (k + 1) / seg;
            Vec3 p0 = pos.add(bs[0].scale(Math.cos(a0) * sombra)).add(bs[1].scale(Math.sin(a0) * sombra));
            Vec3 p1 = pos.add(bs[0].scale(Math.cos(a1) * sombra)).add(bs[1].scale(Math.sin(a1) * sombra));
            b.vertex(pose, (float) pos.x, (float) pos.y, (float) pos.z).color(0f, 0f, 0f, 1f).endVertex();
            b.vertex(pose, (float) p0.x, (float) p0.y, (float) p0.z).color(0f, 0f, 0f, 1f).endVertex();
            b.vertex(pose, (float) p1.x, (float) p1.y, (float) p1.z).color(0f, 0f, 0f, 1f).endVertex();
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
        // anel de fótons: fino, quase branco, colado na sombra
        anel(pose, pos, bs, sombra * 1.0f, sombra * 1.07f, new float[]{1f, 0.93f, 0.8f}, 0.95f);
        anel(pose, pos, bs, sombra * 1.07f, sombra * 1.22f, c, 0.35f);
        disco(pose, pos, r, u, w, n, toCam, time, c, 1);                 // metade da frente, por cima da sombra
    }

    /** Anel fino virado para a câmera. */
    private static void anel(Matrix4f pose, Vec3 c, Vec3[] bs, float r0, float r1, float[] cor, float alpha) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int seg = 72;
        for (int k = 0; k < seg; k++) {
            double a0 = Math.PI * 2 * k / seg, a1 = Math.PI * 2 * (k + 1) / seg;
            Vec3 d0 = bs[0].scale(Math.cos(a0)).add(bs[1].scale(Math.sin(a0))), d1 = bs[0].scale(Math.cos(a1)).add(bs[1].scale(Math.sin(a1)));
            Vec3 i0 = c.add(d0.scale(r0)), o0 = c.add(d0.scale(r1)), o1 = c.add(d1.scale(r1)), i1 = c.add(d1.scale(r0));
            b.vertex(pose, (float) i0.x, (float) i0.y, (float) i0.z).color(cor[0], cor[1], cor[2], alpha).endVertex();
            b.vertex(pose, (float) o0.x, (float) o0.y, (float) o0.z).color(cor[0], cor[1], cor[2], 0f).endVertex();
            b.vertex(pose, (float) o1.x, (float) o1.y, (float) o1.z).color(cor[0], cor[1], cor[2], 0f).endVertex();
            b.vertex(pose, (float) i1.x, (float) i1.y, (float) i1.z).color(cor[0], cor[1], cor[2], alpha).endVertex();
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    /** Cor do gás: quase branco perto do buraco, a cor do buraco no meio e vermelho-escuro na borda de fora. */
    private static float[] gas(float f, float[] c, float doppler) {
        float rr, gg, bb;
        if (f < 0.35f) {
            float t = f / 0.35f;
            rr = Mth.lerp(t, 1f, c[0]); gg = Mth.lerp(t, 0.95f, c[1]); bb = Mth.lerp(t, 0.85f, c[2]);
        } else {
            float t = (f - 0.35f) / 0.65f;
            rr = Mth.lerp(t, c[0], c[0] * 0.75f); gg = Mth.lerp(t, c[1], c[1] * 0.3f); bb = Mth.lerp(t, c[2], c[2] * 0.15f);
        }
        return new float[]{Math.min(1, rr * doppler), Math.min(1, gg * doppler), Math.min(1, bb * doppler)};
    }

    /** Disco de acreção grosso (textura turbulenta) num plano inclinado; metade 0 = de trás, 1 = da frente. */
    private static void disco(Matrix4f pose, Vec3 c, float r, Vec3 u, Vec3 w, Vec3 n, Vec3 toCam, float time,
                              float[] cor, int metade) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, DISCO);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int seg = 128, aneis = 10;
        float r0 = r * 1.55f, r1 = r * 7.2f;
        // de que lado o gás vem na sua direção (esse lado brilha mais)
        Vec3 vista = toCam.subtract(n.scale(toCam.dot(n)));
        for (int a = 0; a < aneis; a++) {
            float fa = (float) a / aneis, fb = (float) (a + 1) / aneis;
            float ra = r0 + (r1 - r0) * fa * fa, rb = r0 + (r1 - r0) * fb * fb;
            // perto do buraco gira mais rápido
            float giroA = time * 0.0025f / (0.25f + fa), giroB = time * 0.0025f / (0.25f + fb);
            for (int k = 0; k < seg; k++) {
                double t0 = Math.PI * 2 * k / seg, t1 = Math.PI * 2 * (k + 1) / seg;
                double tm = (t0 + t1) / 2;
                Vec3 meio = u.scale(Math.cos(tm)).add(w.scale(Math.sin(tm)));
                boolean frente = meio.dot(toCam) > 0;
                if (frente != (metade == 1)) continue;
                dv(b, pose, c, u, w, ra, t0, fa, (float) k / seg + giroA, cor, vista);
                dv(b, pose, c, u, w, ra, t1, fa, (float) (k + 1) / seg + giroA, cor, vista);
                dv(b, pose, c, u, w, rb, t1, fb, (float) (k + 1) / seg + giroB, cor, vista);
                dv(b, pose, c, u, w, rb, t0, fb, (float) k / seg + giroB, cor, vista);
            }
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    private static void dv(BufferBuilder b, Matrix4f pose, Vec3 c, Vec3 u, Vec3 w, float rad, double t, float f, float tu,
                           float[] cor, Vec3 vista) {
        Vec3 dir = u.scale(Math.cos(t)).add(w.scale(Math.sin(t)));
        // direção do movimento do gás (tangente); vindo na sua direção = mais brilho (efeito Doppler)
        Vec3 tan = u.scale(-Math.sin(t)).add(w.scale(Math.cos(t)));
        float dop = 1f + 0.55f * (float) tan.dot(vista.lengthSqr() > 1e-6 ? vista.normalize() : Vec3.ZERO);
        float[] g = gas(f, cor, dop);
        Vec3 p = c.add(dir.scale(rad));
        b.vertex(pose, (float) p.x, (float) p.y, (float) p.z).uv(tu, Math.min(0.999f, f)).color(g[0], g[1], g[2], Math.min(1f, 1.1f * dop)).endVertex();
    }

    /**
     * A lente: a luz do lado de trás do disco faz a curva em volta do buraco e aparece como um arco por cima e por
     * baixo da sombra (mais forte onde o disco está "atrás" em relação a você).
     */
    private static void lente(Matrix4f pose, Vec3 c, float sombra, Vec3[] bs, Vec3 u, Vec3 w, Vec3 toCam, float time, float[] cor) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, DISCO);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        // o plano do disco visto daqui: em que direção da tela ele fica "deitado"
        Vec3 nTela = u.cross(w).normalize();
        Vec3 deitado = nTela.cross(toCam);
        if (deitado.lengthSqr() < 1e-6) deitado = bs[0];
        deitado = deitado.normalize();
        Vec3 em_pe = toCam.cross(deitado).normalize();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int seg = 96, aneis = 5;
        float r0 = sombra * 1.02f, r1 = sombra * 2.1f;
        float giro = time * 0.002f;
        for (int a = 0; a < aneis; a++) {
            float fa = (float) a / aneis, fb = (float) (a + 1) / aneis;
            float ra = Mth.lerp(fa, r0, r1), rb = Mth.lerp(fb, r0, r1);
            for (int k = 0; k < seg; k++) {
                double t0 = Math.PI * 2 * k / seg, t1 = Math.PI * 2 * (k + 1) / seg;
                lv(b, pose, c, deitado, em_pe, ra, t0, fa, (float) k / seg + giro, cor);
                lv(b, pose, c, deitado, em_pe, ra, t1, fa, (float) (k + 1) / seg + giro, cor);
                lv(b, pose, c, deitado, em_pe, rb, t1, fb, (float) (k + 1) / seg + giro, cor);
                lv(b, pose, c, deitado, em_pe, rb, t0, fb, (float) k / seg + giro, cor);
            }
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    private static void lv(BufferBuilder b, Matrix4f pose, Vec3 c, Vec3 x, Vec3 y, float rad, double t, float f, float tu, float[] cor) {
        // forte em cima e embaixo (onde a luz de trás aparece), fraco dos lados
        float topo = (float) Math.abs(Math.sin(t));
        float forca = 0.25f + 0.85f * topo * topo;
        float[] g = gas(f * 0.6f, cor, 1f);
        Vec3 p = c.add(x.scale(Math.cos(t) * rad)).add(y.scale(Math.sin(t) * rad));
        float alpha = forca * (1f - f) * 0.95f;
        b.vertex(pose, (float) p.x, (float) p.y, (float) p.z).uv(tu, 0.02f + f * 0.5f).color(g[0], g[1], g[2], alpha).endVertex();
    }

    private PerigosRender() {}
}
