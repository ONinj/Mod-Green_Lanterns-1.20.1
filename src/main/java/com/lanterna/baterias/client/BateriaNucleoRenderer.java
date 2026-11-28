package com.lanterna.baterias.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import com.lanterna.baterias.BateriaNucleoBlockEntity;
import com.lanterna.baterias.ModBaterias;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Registro (cliente):  BlockEntityRenderers.register(ModBaterias.NUCLEO_BE.get(), BateriaNucleoRenderer::new);
 */
public class BateriaNucleoRenderer extends GeoBlockRenderer<BateriaNucleoBlockEntity> {
    public BateriaNucleoRenderer(BlockEntityRendererProvider.Context ctx) {
        super(new Modelo());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        // 0.60.0: a entidade da emoção, presa dentro do núcleo
        addRenderLayer(new software.bernie.geckolib.renderer.layer.GeoRenderLayer<>(this) {
            @Override
            public void render(com.mojang.blaze3d.vertex.PoseStack ps, BateriaNucleoBlockEntity be, software.bernie.geckolib.cache.object.BakedGeoModel model,
                               RenderType type, net.minecraft.client.renderer.MultiBufferSource bufs, com.mojang.blaze3d.vertex.VertexConsumer buffer,
                               float pt, int light, int overlay) {
                ps.pushPose();
                ps.translate(-0.5, 0, -0.5);   // a camada está no centro do bloco; o desenho conta a partir do canto
                com.lanterna.client.entidades.EntidadesBateria.desenhar(be, ps, bufs, pt);
                ps.popPose();
            }
        });
    }
    @Override public boolean shouldRenderOffScreen(BateriaNucleoBlockEntity be) { return true; }

    @Override public int getViewDistance() { return 256; }

    static class Modelo extends GeoModel<BateriaNucleoBlockEntity> {
        private static ResourceLocation rl(String p) { return new ResourceLocation(ModBaterias.MODID, p); }
        @Override public ResourceLocation getModelResource(BateriaNucleoBlockEntity b) { return rl("geo/block/bateria_central_" + b.getTropa().nome + ".geo.json"); }
        @Override public ResourceLocation getTextureResource(BateriaNucleoBlockEntity b) { return rl("textures/block/bateria_central_" + b.getTropa().nome + ".png"); }
        @Override public ResourceLocation getAnimationResource(BateriaNucleoBlockEntity b) { return rl("animations/block/bateria_central_" + b.getTropa().nome + ".animation.json"); }
        /** vidro do núcleo e campo de energia são semitransparentes */
        @Override public RenderType getRenderType(BateriaNucleoBlockEntity b, ResourceLocation tex) { return RenderType.entityTranslucent(tex); }
    }
}
