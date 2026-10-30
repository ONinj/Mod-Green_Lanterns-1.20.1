package com.lanterna.especies.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import com.lanterna.especies.TemEspecie;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Pele da espécie (ou corrupção) -> roupa -> faixas de oficial -> glowmask.
 * A roupa usa a MESMA geometria; a textura dela só pinta a metade de cima (corpo) e os ossos roupa_*.
 * Ossos roupa_* e capa: a pele deixa essa área transparente, então só aparecem quando a roupa os pinta.
 */
public class EspecieRenderer<T extends Entity & GeoAnimatable & TemEspecie> extends GeoEntityRenderer<T> {
    private final String modid;

    public EspecieRenderer(EntityRendererProvider.Context ctx, String modid) {
        super(ctx, new EspecieGeoModel<>(modid));
        this.modid = modid;
        // brilho da pele primeiro: a roupa, desenhada depois na mesma geometria, cobre o brilho do corpo
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        addRenderLayer(new CamadaRoupa());
    }

    private class CamadaRoupa extends GeoRenderLayer<T> {
        CamadaRoupa() { super(EspecieRenderer.this); }

        @Override
        public void render(PoseStack pose, T e, BakedGeoModel model, RenderType rt, MultiBufferSource buf, VertexConsumer vc,
                           float partialTick, int light, int overlay) {
            String roupa = e.getRoupa();
            if (roupa == null) return;
            camada(roupa, pose, e, model, buf, partialTick, light);
            if (e.isOficial()) camada("roupa_oficial", pose, e, model, buf, partialTick, light);
        }

        private void camada(String nome, PoseStack pose, T e, BakedGeoModel model, MultiBufferSource buf, float partialTick, int light) {
            ResourceLocation tex = new ResourceLocation(modid, "textures/entity/" + nome + "_" + e.getTipoCorpo() + ".png");
            RenderType tipo = RenderType.entityCutoutNoCull(tex);
            getRenderer().reRender(model, pose, buf, e, tipo, buf.getBuffer(tipo), partialTick, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            // brilho da roupa: <nome>_<tipo>_glowmask.png (o AutoGlowingGeoLayer só cuida do glowmask da pele)
            RenderType brilho = AutoGlowingTexture.getRenderType(tex);
            getRenderer().reRender(model, pose, buf, e, brilho, buf.getBuffer(brilho), partialTick, LightTexture.FULL_SKY, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        }
    }
}
