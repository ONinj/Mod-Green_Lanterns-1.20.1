package com.lanterna.client.space;

import com.lanterna.LanternaMod;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 0.54.1: Ryut depois que o Necron saiu dela: duas metades afastadas (sem atmosfera), a face partida escura com um
 * brilho roxo fraco no miolo, e pedaços de rocha boiando na fenda.
 */
final class RyutPartida {
    private static final int LAT = 32, LON = 64;
    /** Quanto cada metade se afastou do centro (fração do raio). */
    private static final float SEP = 0.34f;
    private static float[][] pedras;

    static void desenhar(Matrix4f pose, Vec3 c, float r, Vec3 sol, float tempo) {
        RenderSystem.enableCull();
        for (int lado = 0; lado < 2; lado++) {
            // as metades se afastam em x (quem vem da Terra chega por +z e vê a fenda de lado)
            float sinal = lado == 0 ? 1 : -1;
            Vec3 cc = c.add(r * SEP * sinal, -r * 0.04f * sinal, 0);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, LanternaMod.id("textures/space/ryut.png"));
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            BufferBuilder b = Tesselator.getInstance().getBuilder();
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            // x >= 0: j em [0, LON/4] e [3LON/4, LON]; x <= 0: j em [LON/4, 3LON/4]
            int[][] faixas = lado == 0 ? new int[][]{{0, LON / 4}, {3 * LON / 4, LON}} : new int[][]{{LON / 4, 3 * LON / 4}};
            for (int[] f : faixas)
                for (int i = 0; i < LAT; i++)
                    for (int j = f[0]; j < f[1]; j++) {
                        SpaceSkyEffects.vertex(b, pose, cc, r, i, j, LAT, LON, sol, false, 0);
                        SpaceSkyEffects.vertex(b, pose, cc, r, i, j + 1, LAT, LON, sol, false, 0);
                        SpaceSkyEffects.vertex(b, pose, cc, r, i + 1, j + 1, LAT, LON, sol, false, 0);
                        SpaceSkyEffects.vertex(b, pose, cc, r, i + 1, j, LAT, LON, sol, false, 0);
                    }
            BufferUploader.drawWithShader(b.end());
            // a face partida (o plano x = 0 da metade): rocha escura, núcleo roxo apagado
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            int n = 48;
            float pulso = 0.75f + 0.25f * Mth.sin(tempo * 0.05f + lado);
            for (int k = 0; k < n; k++) {
                float a0 = Mth.TWO_PI * k / n, a1 = Mth.TWO_PI * (k + 1) / n;
                // borda irregular (rachada)
                float q0 = r * (0.93f + 0.07f * Mth.sin(k * 2.7f)), q1 = r * (0.93f + 0.07f * Mth.sin((k + 1) * 2.7f));
                float y0 = Mth.cos(a0) * q0, z0 = Mth.sin(a0) * q0, y1 = Mth.cos(a1) * q1, z1 = Mth.sin(a1) * q1;
                float m = 0.6f;
                cor(b, pose, cc, y0, z0, 0.13f, 0.12f, 0.14f);
                cor(b, pose, cc, y1, z1, 0.13f, 0.12f, 0.14f);
                cor(b, pose, cc, y1 * m, z1 * m, 0.2f * pulso, 0.07f, 0.28f * pulso);
                cor(b, pose, cc, y0, z0, 0.13f, 0.12f, 0.14f);
                cor(b, pose, cc, y1 * m, z1 * m, 0.2f * pulso, 0.07f, 0.28f * pulso);
                cor(b, pose, cc, y0 * m, z0 * m, 0.2f * pulso, 0.07f, 0.28f * pulso);
                cor(b, pose, cc, 0, 0, 0.04f, 0.0f, 0.07f);
                cor(b, pose, cc, y0 * m, z0 * m, 0.2f * pulso, 0.07f, 0.28f * pulso);
                cor(b, pose, cc, y1 * m, z1 * m, 0.2f * pulso, 0.07f, 0.28f * pulso);
            }
            BufferUploader.drawWithShader(b.end());
        }
        // pedaços de rocha boiando na fenda (cubinhos escuros, girando devagar)
        if (pedras == null) {
            RandomSource rnd = RandomSource.create(9091L);
            pedras = new float[90][];
            for (int i = 0; i < pedras.length; i++) {
                float a = rnd.nextFloat() * Mth.TWO_PI, d = (float) Math.sqrt(rnd.nextFloat()) * 1.05f;
                pedras[i] = new float[]{(rnd.nextFloat() - 0.5f) * SEP * 1.6f, Mth.cos(a) * d, Mth.sin(a) * d, 0.008f + rnd.nextFloat() * 0.03f, rnd.nextFloat() * 6};
            }
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (float[] p : pedras) {
            float gir = tempo * 0.0006f + p[4];
            float py = p[1] * Mth.cos(gir * 0.1f) - p[2] * Mth.sin(gir * 0.1f), pz = p[1] * Mth.sin(gir * 0.1f) + p[2] * Mth.cos(gir * 0.1f);
            Vec3 q = c.add(p[0] * r, py * r, pz * r);
            cubo(b, pose, q, p[3] * r, gir);
        }
        BufferUploader.drawWithShader(b.end());
        // um brilho roxo bem fraco saindo da fenda (a luz que ficou)
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        SpaceSkyEffects.drawAtmosphere(pose, c, r * 0.7f, 0x6A2AA0, 0.35f);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
    }

    private static void cor(BufferBuilder b, Matrix4f m, Vec3 c, float y, float z, float r, float g, float bl) {
        b.vertex(m, (float) c.x, (float) c.y + y, (float) c.z + z).color(r, g, bl, 1f).endVertex();
    }

    private static void cubo(BufferBuilder b, Matrix4f m, Vec3 c, float s, float gir) {
        float cs = Mth.cos(gir), sn = Mth.sin(gir);
        float[][] v = new float[8][];
        for (int i = 0; i < 8; i++) {
            float x = (i & 1) == 0 ? -s : s, y = (i & 2) == 0 ? -s : s, z = (i & 4) == 0 ? -s : s;
            v[i] = new float[]{x * cs - z * sn, y, x * sn + z * cs};
        }
        int[][] f = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        float[] luz = {0.32f, 0.22f, 0.18f, 0.36f, 0.27f, 0.25f};
        for (int k = 0; k < 6; k++)
            for (int i : f[k])
                b.vertex(m, (float) c.x + v[i][0], (float) c.y + v[i][1], (float) c.z + v[i][2]).color(luz[k], luz[k] * 0.95f, luz[k] * 1.05f, 1f).endVertex();
    }

    private RyutPartida() {}
}
