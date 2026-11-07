package com.lanterna.client.energia;

import com.lanterna.baterias.Tropa;
import com.lanterna.client.render.LanternaRenderTypes;
import com.lanterna.energia.GeradorEmocionalBE;
import com.lanterna.energia.GeradorEmocionalBlock;
import com.lanterna.energia.MaquinaBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Um cristal de emoção flutuando em cima do gerador, girando, na cor da emoção; mais forte quando produz. */
public class GeradorRenderer implements BlockEntityRenderer<GeradorEmocionalBE> {
    public GeradorRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public void render(GeradorEmocionalBE be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        if (be.getLevel() == null) return;
        Tropa t = be.getBlockState().getValue(GeradorEmocionalBlock.EMOCAO);
        boolean aceso = be.getBlockState().getValue(MaquinaBlock.ACESO);
        float gt = be.getLevel().getGameTime() + pt;
        float forca = aceso ? 0.85f + 0.15f * Mth.sin(gt * 0.25f) : 0.35f;
        ps.pushPose();
        ps.translate(0.5, 1.25 + 0.06 * Mth.sin(gt * 0.08f), 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(gt * (aceso ? 3f : 0.8f)));
        ps.mulPose(Axis.ZP.rotationDegrees(45));
        ps.mulPose(Axis.XP.rotationDegrees(35));
        VertexConsumer corpo = buf.getBuffer(LanternaRenderTypes.ENERGIA);
        octaedro(corpo, ps.last().pose(), 0.2f, t.cor, 0.8f);
        VertexConsumer brilho = buf.getBuffer(LanternaRenderTypes.BRILHO);
        octaedro(brilho, ps.last().pose(), 0.28f, t.cor, 0.45f * forca);
        ps.popPose();
    }

    private static void octaedro(VertexConsumer vc, Matrix4f m, float r, int rgb, float a) {
        float cr = ((rgb >> 16) & 255) / 255f, cg = ((rgb >> 8) & 255) / 255f, cb = (rgb & 255) / 255f;
        float[][] p = {{r, 0, 0}, {0, 0, r}, {-r, 0, 0}, {0, 0, -r}};
        for (int i = 0; i < 4; i++) {
            float[] a0 = p[i], a1 = p[(i + 1) % 4];
            for (float y : new float[]{r * 1.4f, -r * 1.4f}) {
                vc.vertex(m, 0, y, 0).color(Math.min(1, cr + 0.3f), Math.min(1, cg + 0.3f), Math.min(1, cb + 0.3f), a).uv(0.5f, 0).endVertex();
                vc.vertex(m, 0, y, 0).color(Math.min(1, cr + 0.3f), Math.min(1, cg + 0.3f), Math.min(1, cb + 0.3f), a).uv(0.5f, 0).endVertex();
                vc.vertex(m, a1[0], 0, a1[2]).color(cr, cg, cb, a).uv(1, 1).endVertex();
                vc.vertex(m, a0[0], 0, a0[2]).color(cr, cg, cb, a).uv(0, 1).endVertex();
            }
        }
    }
}
