package com.lanterna.client.uniform;

import com.lanterna.armor.UniformeTropaItem;
import com.lanterna.armor.client.UniformeRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * O uniforme 3D vestido pelo anel, com a transformação:
 *  - camada de base (rente à skin): aparece pixel por pixel, dos pés à cabeça;
 *  - peças 3D (cubinhos): aparecem uma a uma, também subindo, e brilham na cor da Tropa ao se formar.
 * Formado por completo, desenha igual ao uniforme normal (com as partes luminosas da _glowmask).
 */
public class RingUniformRenderer extends UniformeRenderer {
    public enum Mode { NORMAL, BASE, DETAIL, BAND }

    private static final float SCATTER = 0.2f;
    private static final float BAND = 0.08f;

    private Mode mode = Mode.NORMAL;
    private float progress = 1f;
    private float cr = 1f, cg = 1f, cb = 1f;
    private ResourceLocation baseTexture, bandTexture;
    private final Matrix4f toModel = new Matrix4f();
    private final Map<GeoCube, Boolean> baseCubes = new IdentityHashMap<>();
    private final Map<GeoCube, Float> noise = new IdentityHashMap<>();

    /** Prepara a etapa: p = progresso da transformação, color = cor da Tropa, layerPose = espaço do modelo do jogador. */
    public void setup(Mode mode, float p, int color, ResourceLocation[] stage, Matrix4f layerPose) {
        this.mode = mode;
        this.progress = p;
        this.cr = Math.min(1f, ((color >> 16) & 0xFF) / 255f + 0.3f);
        this.cg = Math.min(1f, ((color >> 8) & 0xFF) / 255f + 0.3f);
        this.cb = Math.min(1f, (color & 0xFF) / 255f + 0.3f);
        this.baseTexture = stage != null ? stage[0] : null;
        this.bandTexture = stage != null ? stage[1] : null;
        this.toModel.set(layerPose).invert();
    }

    public void reset() {
        mode = Mode.NORMAL;
    }

    @Override
    public ResourceLocation getTextureLocation(UniformeTropaItem item) {
        if (mode == Mode.BASE && baseTexture != null) return baseTexture;
        if (mode == Mode.BAND && bandTexture != null) return bandTexture;
        return super.getTextureLocation(item);
    }

    @Override
    public RenderType getRenderType(UniformeTropaItem item, ResourceLocation texture, MultiBufferSource buffer, float partialTick) {
        if (mode == Mode.BAND) return RenderType.entityTranslucentEmissive(texture);
        if (mode == Mode.BASE) return RenderType.entityCutoutNoCull(texture);
        return super.getRenderType(item, texture, buffer, partialTick);
    }

    @Override
    public List<GeoRenderLayer<UniformeTropaItem>> getRenderLayers() {
        return mode == Mode.NORMAL ? super.getRenderLayers() : List.of();
    }

    @Override
    public void renderCube(PoseStack poseStack, GeoCube cube, VertexConsumer buffer, int light, int overlay,
                           float red, float green, float blue, float alpha) {
        if (mode == Mode.NORMAL) {
            super.renderCube(poseStack, cube, buffer, light, overlay, red, green, blue, alpha);
            return;
        }
        boolean base = baseCubes.computeIfAbsent(cube, RingUniformRenderer::isBase);
        switch (mode) {
            case BASE -> { if (base) super.renderCube(poseStack, cube, buffer, light, overlay, red, green, blue, alpha); }
            case BAND -> { if (base) super.renderCube(poseStack, cube, buffer, LightTexture.FULL_BRIGHT, overlay, cr, cg, cb, alpha); }
            case DETAIL -> {
                if (base) return;
                float t = threshold(poseStack, cube);
                if (t > progress) return;
                if (progress - t < BAND) {
                    // Peça acabando de se formar: brilha na cor da Tropa
                    super.renderCube(poseStack, cube, buffer, LightTexture.FULL_BRIGHT, overlay, cr, cg, cb, alpha);
                } else {
                    super.renderCube(poseStack, cube, buffer, light, overlay, red, green, blue, alpha);
                }
            }
            default -> { }
        }
    }

    /** Cubo da camada de base: toda a textura dele fica no canto 64x64 (layout de skin). */
    private static boolean isBase(GeoCube cube) {
        for (GeoQuad quad : cube.quads()) {
            if (quad == null) continue;
            for (GeoVertex v : quad.vertices()) {
                if (v.texU() > 0.5f + 1.0E-4f || v.texV() > 0.5f + 1.0E-4f) return false;
            }
        }
        return true;
    }

    /** Em que altura do corpo (0 = pés, 1 = topo da cabeça) o cubo está, com um pouco de sorteio. */
    private float threshold(PoseStack poseStack, GeoCube cube) {
        float sx = 0, sy = 0, sz = 0;
        int n = 0;
        for (GeoQuad quad : cube.quads()) {
            if (quad == null) continue;
            for (GeoVertex v : quad.vertices()) {
                sx += v.position().x(); sy += v.position().y(); sz += v.position().z();
                n++;
            }
        }
        if (n == 0) return 0f;
        Vector4f c = new Vector4f(sx / n, sy / n, sz / n, 1f);
        poseStack.last().pose().transform(c);
        toModel.transform(c);
        // No espaço do modelo do jogador o Y aponta para baixo: pés em 1,5 e topo da cabeça em -0,5
        float height = Math.max(0f, Math.min(1f, (1.5f - c.y()) / 2f));
        float rnd = noise.computeIfAbsent(cube, k -> {
            int h = System.identityHashCode(k) * 0x9E3779B1;
            h ^= h >>> 15;
            return (h & 0xFFFF) / 65535f;
        });
        return height * (1f - SCATTER) + rnd * SCATTER;
    }
}
