package com.lanterna.client.energia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Peças desenhadas com as texturas dos blocos (caixas e vigas inclinadas), para as máquinas grandes. */
public final class Pecas {
    public static TextureAtlasSprite sprite(String nome) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(new ResourceLocation(nome.contains(":") ? nome : "minecraft:block/" + nome));
    }

    /** Caixa alinhada aos eixos. tint em 0xRRGGBB. */
    public static void caixa(PoseStack ps, VertexConsumer vc, TextureAtlasSprite sp, float x0, float y0, float z0, float x1, float y1, float z1, int luz, int tint) {
        Vector3f[] c = {
                new Vector3f(x0, y0, z0), new Vector3f(x1, y0, z0), new Vector3f(x1, y0, z1), new Vector3f(x0, y0, z1),
                new Vector3f(x0, y1, z0), new Vector3f(x1, y1, z0), new Vector3f(x1, y1, z1), new Vector3f(x0, y1, z1)};
        faces(ps, vc, sp, c, luz, tint, new float[]{x1 - x0, y1 - y0, z1 - z0});
    }

    /** Viga quadrada de a até b, com espessura e. */
    public static void viga(PoseStack ps, VertexConsumer vc, TextureAtlasSprite sp, Vector3f a, Vector3f b, float e, int luz, int tint) {
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        if (len < 1e-4f) return;
        d.div(len);
        Vector3f up = Math.abs(d.y) > 0.95f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f u = new Vector3f(d).cross(up).normalize().mul(e / 2);
        Vector3f v = new Vector3f(d).cross(u).normalize().mul(e / 2);
        Vector3f[] c = new Vector3f[8];
        // 0..3 em a, 4..7 em b (mesma ordem da caixa: base e topo)
        Vector3f[] off = {new Vector3f(v).negate().sub(u), new Vector3f(v).sub(u), new Vector3f(v).add(u), new Vector3f(v).negate().add(u)};
        for (int i = 0; i < 4; i++) {
            c[i] = new Vector3f(a).add(off[i]);
            c[i + 4] = new Vector3f(b).add(off[i]);
        }
        faces(ps, vc, sp, c, luz, tint, new float[]{e, len, e});
    }

    /** 6 faces de um "cubo" deformado: 0-3 embaixo, 4-7 em cima. tam = tamanhos para repetir a textura. */
    private static void faces(PoseStack ps, VertexConsumer vc, TextureAtlasSprite sp, Vector3f[] c, int luz, int tint, float[] tam) {
        int[][] f = {{0, 1, 2, 3}, {7, 6, 5, 4}, {4, 5, 1, 0}, {6, 7, 3, 2}, {7, 4, 0, 3}, {5, 6, 2, 1}};
        float[][] uv = {{tam[0], tam[2]}, {tam[0], tam[2]}, {tam[0], tam[1]}, {tam[0], tam[1]}, {tam[2], tam[1]}, {tam[2], tam[1]}};
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float r = ((tint >> 16) & 255) / 255f, g = ((tint >> 8) & 255) / 255f, b = (tint & 255) / 255f;
        for (int k = 0; k < 6; k++) {
            Vector3f p0 = c[f[k][0]], p1 = c[f[k][1]], p2 = c[f[k][2]], p3 = c[f[k][3]];
            Vector3f nn = new Vector3f(p1).sub(p0).cross(new Vector3f(p3).sub(p0));
            if (nn.lengthSquared() < 1e-10f) nn.set(0, 1, 0);
            nn.normalize();
            // uma face escura embaixo, um pouco mais clara dos lados (o shader de entidade já sombreia pela normal)
            float su = Math.min(1f, uv[k][0]), sv = Math.min(1f, uv[k][1]);
            float u0 = sp.getU0(), v0 = sp.getV0(), u1 = sp.getU0() + (sp.getU1() - sp.getU0()) * su, v1 = sp.getV0() + (sp.getV1() - sp.getV0()) * sv;
            vert(vc, m, n, p0, u0, v1, luz, r, g, b, nn);
            vert(vc, m, n, p1, u1, v1, luz, r, g, b, nn);
            vert(vc, m, n, p2, u1, v0, luz, r, g, b, nn);
            vert(vc, m, n, p3, u0, v0, luz, r, g, b, nn);
        }
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Matrix3f n, Vector3f p, float u, float v, int luz, float r, float g, float b, Vector3f nn) {
        vc.vertex(m, p.x, p.y, p.z).color(r, g, b, 1f).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, nn.x, nn.y, nn.z).endVertex();
    }

    private Pecas() {}
}
