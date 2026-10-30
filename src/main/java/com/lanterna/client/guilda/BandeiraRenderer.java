package com.lanterna.client.guilda;

import com.lanterna.guilda.BandeiraBlock;
import com.lanterna.space.SpaceBodies;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Mastro alto com o estandarte da guilda (cor da Tropa e o símbolo), balançando ao vento. */
public class BandeiraRenderer implements BlockEntityRenderer<BandeiraBlock.Entidade> {
    private static final ResourceLocation MASTRO = new ResourceLocation("minecraft", "textures/block/quartz_pillar.png");

    public BandeiraRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(BandeiraBlock.Entidade be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        if (be.getLevel() == null) return;
        var bp = be.getBlockPos();
        float t = be.getLevel().getGameTime() + pt;
        boolean liberada = be.getBlockState().getValue(BandeiraBlock.LIBERADA);
        // mastro: de 1 até 7 blocos
        VertexConsumer m = buf.getBuffer(RenderType.entitySolid(MASTRO));
        caixa(ps.last().pose(), m, 7f / 16, 1f, 7f / 16, 9f / 16, 7.2f, 9f / 16, light);
        caixa(ps.last().pose(), m, 6.2f / 16, 7.2f, 6.2f / 16, 9.8f / 16, 7.5f, 9.8f / 16, light);
        // estandarte
        SpaceBodies.Body b = SpaceBodies.bodyAt(be.getLevel().dimension(), bp.getX(), bp.getZ());
        var p = b == null ? null : ClientGuildas.planeta(b.id());
        if (p == null || ClientGuildas.guilda(p.guilda()) == null) return;
        VertexConsumer v = buf.getBuffer(RenderType.entityCutoutNoCull(ClientGuildas.estandarte(p.guilda())));
        Matrix4f pose = ps.last().pose();
        int seg = 8;
        float w = 2.0f, h = 3.0f, x0 = 9f / 16, top = 7.0f;
        float queda = liberada ? 2.2f : 0f; // bandeira liberada: meio-mastro
        for (int i = 0; i < seg; i++) {
            float u0 = i / (float) seg, u1 = (i + 1) / (float) seg;
            float xa = x0 + u0 * w, xb = x0 + u1 * w;
            float za = 0.5f + Mth.sin(t * 0.12f + u0 * 4f) * 0.12f * u0, zb = 0.5f + Mth.sin(t * 0.12f + u1 * 4f) * 0.12f * u1;
            float ya = top - queda, yb = top - h - queda;
            int L = 0xF000F0;
            v.vertex(pose, xa, ya, za).color(255, 255, 255, 255).uv(u0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(L).normal(0, 0, 1).endVertex();
            v.vertex(pose, xa, yb, za).color(255, 255, 255, 255).uv(u0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(L).normal(0, 0, 1).endVertex();
            v.vertex(pose, xb, yb, zb).color(255, 255, 255, 255).uv(u1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(L).normal(0, 0, 1).endVertex();
            v.vertex(pose, xb, ya, zb).color(255, 255, 255, 255).uv(u1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(L).normal(0, 0, 1).endVertex();
        }
    }

    private static void caixa(Matrix4f m, VertexConsumer v, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
        float[][][] faces = {
                {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}},
                {{x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}, {x0, y0, z1}},
                {{x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}, {x0, y0, z0}},
                {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}},
                {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}}};
        float[][] nor = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}};
        float[][] uv = {{0, 1}, {0, 0}, {1, 0}, {1, 1}};
        for (int f = 0; f < faces.length; f++)
            for (int k = 0; k < 4; k++)
                v.vertex(m, faces[f][k][0], faces[f][k][1], faces[f][k][2]).color(255, 255, 255, 255).uv(uv[k][0], uv[k][1] * 4)
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nor[f][0], nor[f][1], nor[f][2]).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(BandeiraBlock.Entidade be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
