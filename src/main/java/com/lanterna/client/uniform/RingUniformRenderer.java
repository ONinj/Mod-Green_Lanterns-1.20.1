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
 *  - o uniforme escorre pelo corpo como líquido, a partir da mão do anel (FluxoUniforme): a camada de base
 *    (rente à skin) pixel por pixel e as peças 3D (cubinhos) acompanhando a mesma frente;
 *  - a beirada da frente brilha na cor da Tropa.
 * Formado por completo, desenha igual ao uniforme normal (com as partes luminosas da _glowmask).
 */
public class RingUniformRenderer extends UniformeRenderer {
    public enum Mode { NORMAL, BASE, DETAIL, BAND, ACESSORIO }

    private static final float SCATTER = 0.2f;
    private static final float BAND = 0.09f;

    private Mode mode = Mode.NORMAL;
    /** Trajes "segunda pele" não têm camada de base em layout de skin: tudo aparece cubinho por cubinho. */
    private boolean semBase;
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
        semBase = false;
    }

    @org.jetbrains.annotations.Nullable private net.minecraft.world.entity.HumanoidArm soBraco;

    /** Primeira pessoa: só o braço deste lado aparece. */
    public void setSoBraco(@org.jetbrains.annotations.Nullable net.minecraft.world.entity.HumanoidArm arm) {
        soBraco = arm;
    }

    @Override
    protected void applyBoneVisibilityBySlot(net.minecraft.world.entity.EquipmentSlot slot) {
        super.applyBoneVisibilityBySlot(slot);
        if (soBraco == null) return;
        setAllBonesVisible(false);
        setBoneVisible(soBraco == net.minecraft.world.entity.HumanoidArm.RIGHT ? rightArm : leftArm, true);
    }

    public void setSemBase(boolean v) {
        semBase = v;
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
        return mode == Mode.NORMAL || mode == Mode.ACESSORIO && progress >= 1f ? super.getRenderLayers() : List.of();
    }

    @Override
    public void renderCube(PoseStack poseStack, GeoCube cube, VertexConsumer buffer, int light, int overlay,
                           float red, float green, float blue, float alpha) {
        if (mode == Mode.NORMAL) {
            super.renderCube(poseStack, cube, buffer, light, overlay, red, green, blue, alpha);
            return;
        }
        boolean base = !semBase && baseCubes.computeIfAbsent(cube, RingUniformRenderer::isBase);
        switch (mode) {
            case BASE -> { if (base) super.renderCube(poseStack, cube, buffer, light, overlay, red, green, blue, alpha); }
            case BAND -> { if (base) super.renderCube(poseStack, cube, buffer, LightTexture.FULL_BRIGHT, overlay, cr, cg, cb, alpha); }
            case ACESSORIO -> {
                // só as peças 3D que saem do corpo (asas, auréola, espinhos...); o resto já está pintado na skin
                threshold(poseStack, cube);
                if (!acessorios.getOrDefault(cube, false)) return;
                float t = threshold(poseStack, cube);
                if (t > progress) return;
                if (progress < 1f && progress - t < BAND)
                    super.renderCube(poseStack, cube, buffer, LightTexture.FULL_BRIGHT, overlay, cr, cg, cb, alpha);
                else super.renderCube(poseStack, cube, buffer, light, overlay, red, green, blue, alpha);
            }
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

    /** Quando o líquido chega neste cubo: pelo osso (parte do corpo) e pelo centro do cubo, igual à camada de base. */
    private final Map<GeoCube, Float> fluxo = new IdentityHashMap<>();

    private float threshold(PoseStack poseStack, GeoCube cube) {
        Float t = fluxo.get(cube);
        if (t != null) return t;
        // monta o mapa inteiro de uma vez a partir do modelo (posição de descanso)
        try {
            var modelo = getGeoModel().getBakedModel(getGeoModel().getModelResource(this.animatable));
            for (var osso : modelo.topLevelBones()) mapear(osso);
        } catch (Exception ignored) {
        }
        t = fluxo.get(cube);
        if (t == null) { t = 0.5f; fluxo.put(cube, t); }
        return t;
    }

    /** Cubos que saem do corpo mais que a skin consegue mostrar (ou girados): continuam 3D. */
    private final Map<GeoCube, Boolean> acessorios = new IdentityHashMap<>();
    /** Caixa de cada parte no Blockbench (x0, y0, z0, x1, y1, z1), igual ao tools/achatar_trajes.py. */
    private static final float[][] CAIXA = {
            {-4, 24, -4, 4, 32, 4}, {-4, 12, -2, 4, 24, 2}, {-8, 12, -2, -4, 24, 2}, {4, 12, -2, 8, 24, 2},
            {-3.9f, 0, -2, 0.1f, 12, 2}, {-0.1f, 0, -2, 3.9f, 12, 2}};
    private static final float SOBRA = 1.3f;

    private static boolean acessorio(int parte, GeoCube cube) {
        var rot = cube.rotation();
        if (Math.abs(rot.x) > 1e-3 || Math.abs(rot.y) > 1e-3 || Math.abs(rot.z) > 1e-3) return true;
        float x0 = 1e9f, y0 = 1e9f, z0 = 1e9f, x1 = -1e9f, y1 = -1e9f, z1 = -1e9f;
        for (GeoQuad quad : cube.quads()) {
            if (quad == null) continue;
            for (GeoVertex v : quad.vertices()) {
                float x = -v.position().x() * 16f, y = v.position().y() * 16f, z = v.position().z() * 16f;
                x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); z0 = Math.min(z0, z); z1 = Math.max(z1, z);
            }
        }
        float[] c = CAIXA[parte];
        return c[0] - x0 > SOBRA || x1 - c[3] > SOBRA || c[1] - y0 > SOBRA || y1 - c[4] > SOBRA || c[2] - z0 > SOBRA || z1 - c[5] > SOBRA;
    }

    private void mapear(software.bernie.geckolib.cache.object.GeoBone osso) {
        int parte = FluxoUniforme.parteDoOsso(osso.getName());
        int caixa = switch (parte) {
            case FluxoUniforme.CABECA -> 0; case FluxoUniforme.BRACO_D -> 2; case FluxoUniforme.BRACO_E -> 3;
            case FluxoUniforme.PERNA_D -> 4; case FluxoUniforme.PERNA_E -> 5; default -> 1;
        };
        for (GeoCube cube : osso.getCubes()) {
            acessorios.put(cube, acessorio(caixa, cube));
            float sx = 0, sy = 0, sz = 0;
            int n = 0;
            for (GeoQuad quad : cube.quads()) {
                if (quad == null) continue;
                for (GeoVertex v : quad.vertices()) {
                    sx += v.position().x(); sy += v.position().y(); sz += v.position().z();
                    n++;
                }
            }
            if (n == 0) continue;
            // o GeckoLib espelha o X do Blockbench e guarda em blocos
            float x = -sx / n * 16f, y = sy / n * 16f, z = sz / n * 16f;
            fluxo.put(cube, FluxoUniforme.tempo(parte, x, y, z, System.identityHashCode(cube) & 7));
        }
        for (var filho : osso.getChildBones()) mapear(filho);
    }
}
