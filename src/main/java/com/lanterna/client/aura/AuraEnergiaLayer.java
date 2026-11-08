package com.lanterna.client.aura;

import com.lanterna.client.flight.AuraOutline;
import com.lanterna.client.render.LanternaRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Aura do anel ligado: energia líquida em volta do corpo, na cor da Tropa.
 * Cada parte do corpo (cabeça, tronco, braços, pernas) ganha um "envelope" arredondado que acompanha a pose. A
 * superfície ondula sem parar (ruído que corre para cima, como líquido subindo), é transparente no meio e acende nas
 * beiradas (onde a superfície fica de lado para quem olha), então o corpo aparece por dentro e a borda brilha.
 * No alto (cabeça e ombros) a superfície estica em línguas que sobem e voltam, como chama de líquido. Uma faixa de luz
 * sobe pelo corpo de tempos em tempos. Fica junto com o contorno.
 */
public class AuraEnergiaLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final int LAT = 12, LON = 18;

    public AuraEnergiaLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> pai) {
        super(pai);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int luz, AbstractClientPlayer p, float limb, float limbAmt,
                       float pt, float idade, float cabeca, float pitch) {
        if (p.isInvisible()) return;
        int cor = AuraOutline.color(p);
        if (cor == -1) return;
        PlayerModel<AbstractClientPlayer> m = getParentModel();
        float t = idade + p.getId() * 13.7f;
        VertexConsumer vc = buf.getBuffer(LanternaRenderTypes.BRILHO);
        // caixas de cada parte em pixels (do jeito que o modelo do Minecraft é montado) + quanto sobe a chama
        envelope(ps, vc, m.head, -4, -8, -4, 4, 0, 4, cor, t, 0, 1.0f);
        envelope(ps, vc, m.body, -4, 0, -2, 4, 12, 2, cor, t, 1, 0.55f);
        envelope(ps, vc, m.rightArm, -3, -2, -2, 1, 10, 2, cor, t, 2, 0.45f);
        envelope(ps, vc, m.leftArm, -1, -2, -2, 3, 10, 2, cor, t, 3, 0.45f);
        envelope(ps, vc, m.rightLeg, -2, 0, -2, 2, 12, 2, cor, t, 4, 0.1f);
        envelope(ps, vc, m.leftLeg, -2, 0, -2, 2, 12, 2, cor, t, 5, 0.1f);
    }

    /** Ruído suave e barato (soma de senos), em [-1, 1]. */
    private static float ruido(float x, float y, float z, float t) {
        return (Mth.sin(x * 1.7f + t * 0.11f) * Mth.sin(y * 1.3f - t * 0.17f + z * 0.9f)
                + 0.6f * Mth.sin(x * 3.1f - z * 2.3f + y * 2.6f - t * 0.23f)
                + 0.35f * Mth.sin(y * 5.3f + x * 4.1f - t * 0.31f)) / 1.95f;
    }

    /**
     * Elipsoide arredondado em volta da caixa da parte (coordenadas em pixels do modelo), deformado pelo ruído.
     * chama = quanto o topo estica para cima em línguas (no modelo, "cima" é -Y).
     */
    private void envelope(PoseStack ps, VertexConsumer vc, ModelPart parte, float x0, float y0, float z0, float x1, float y1, float z1,
                          int cor, float t, int semente, float chama) {
        if (!parte.visible) return;
        ps.pushPose();
        parte.translateAndRotate(ps);
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, cz = (z0 + z1) / 2;
        float folga = 2.2f;
        float rx = (x1 - x0) / 2 + folga, ry = (y1 - y0) / 2 + folga, rz = (z1 - z0) / 2 + folga;
        float cr = ((cor >> 16) & 255) / 255f, cg = ((cor >> 8) & 255) / 255f, cb = (cor & 255) / 255f;
        float s = semente * 4.1f;
        float[][] pos = new float[(LAT + 1) * (LON + 1)][];
        for (int i = 0; i <= LAT; i++) {
            float la = -Mth.HALF_PI + Mth.PI * i / LAT;
            for (int j = 0; j <= LON; j++) {
                float lo = Mth.TWO_PI * j / LON;
                // superelipsoide (mais "quadradão" que uma esfera, para seguir o corpo, mas sem quinas)
                float cl = Mth.cos(la), sl = Mth.sin(la), co = Mth.cos(lo), so = Mth.sin(lo);
                float ex = Math.copySign((float) Math.pow(Math.abs(cl * co), 0.7), cl * co);
                float ey = Math.copySign((float) Math.pow(Math.abs(sl), 0.7), sl);
                float ez = Math.copySign((float) Math.pow(Math.abs(cl * so), 0.7), cl * so);
                float nx = ex, ny = ey, nz = ez;
                float n = ruido(ex * 2 + s, ey * 3, ez * 2 - s, t);
                // a superfície respira e ondula; no alto estica para cima em línguas
                float d = 1 + 0.16f * n;
                float x = cx + ex * rx * d, y = cy + ey * ry * d, z = cz + ez * rz * d;
                float lingua = 0;
                if (ey < -0.2f && chama > 0) {
                    lingua = Math.max(0, ruido(ex * 4 + s * 2, 0.5f, ez * 4, t * 1.3f)) * (-ey - 0.2f) / 0.8f;
                    y -= lingua * 3.2f * chama;
                }
                pos[i * (LON + 1) + j] = new float[]{x, y, z, nx, ny, nz, n, lingua};
            }
        }
        for (int i = 0; i < LAT; i++) for (int j = 0; j < LON; j++) {
            int[] idx = {i * (LON + 1) + j, i * (LON + 1) + j + 1, (i + 1) * (LON + 1) + j + 1, (i + 1) * (LON + 1) + j};
            for (int k : idx) {
                float[] v = pos[k];
                float px = v[0] / 16f, py = v[1] / 16f, pz = v[2] / 16f;
                // posição e normal no espaço da câmera para saber o quanto a superfície está "de lado"
                float vx = m.m00() * px + m.m10() * py + m.m20() * pz + m.m30();
                float vy = m.m01() * px + m.m11() * py + m.m21() * pz + m.m31();
                float vz = m.m02() * px + m.m12() * py + m.m22() * pz + m.m32();
                float wx = nm.m00() * v[3] + nm.m10() * v[4] + nm.m20() * v[5];
                float wy = nm.m01() * v[3] + nm.m11() * v[4] + nm.m21() * v[5];
                float wz = nm.m02() * v[3] + nm.m12() * v[4] + nm.m22() * v[5];
                float ln = Mth.sqrt(wx * wx + wy * wy + wz * wz), lv = Mth.sqrt(vx * vx + vy * vy + vz * vz);
                float olha = (ln < 1e-5f || lv < 1e-5f) ? 0 : Math.abs(wx * vx + wy * vy + wz * vz) / (ln * lv);
                float borda = 1 - olha;
                float a = (borda * borda * borda * 0.55f + 0.03f) * (1 - 0.65f * v[7]);
                // faixa de luz subindo pelo corpo
                float onda = Mth.sin(-v[1] * 0.35f + t * 0.12f + semente);
                a += Math.max(0, onda - 0.85f) * 1.2f * (0.3f + borda);
                // pontos mais "fundos" do ruído ficam mais claros (veios do líquido)
                float claro = 0.04f + 0.14f * Math.max(0, v[6]);
                float u = v[0] * 0.07f + v[2] * 0.05f, w = v[1] * 0.06f + t * 0.025f;
                vc.vertex(m, px, py, pz).color(Math.min(1, cr + claro), Math.min(1, cg + claro), Math.min(1, cb + claro), Math.min(1, a)).uv(u, w).endVertex();
            }
        }
        ps.popPose();
    }
}
