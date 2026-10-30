package com.lanterna.naves.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import com.lanterna.naves.TemNave;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Renderiza a nave com o glowmask e esconde as caixas de colisão (col_1..col_8). */
public class NaveRenderer<T extends Entity & GeoAnimatable & TemNave> extends GeoEntityRenderer<T> {
    public NaveRenderer(EntityRendererProvider.Context ctx, String modid) {
        super(ctx, new NaveGeoModel<>(modid));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 2.5f;
    }

    @Override
    public void preRender(PoseStack pose, T e, BakedGeoModel model, MultiBufferSource buf, VertexConsumer vc, boolean isReRender,
                          float partialTick, int light, int overlay, float r, float g, float b, float a) {
        for (int i = 1; i <= 8; i++) model.getBone("col_" + i).ifPresent(bone -> bone.setHidden(true));
        super.preRender(pose, e, model, buf, vc, isReRender, partialTick, light, overlay, r, g, b, a);
    }

    /** a nave é grande: continua sendo desenhada mesmo com o centro fora da tela */
    @Override public boolean shouldRender(T e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return f.isVisible(e.getBoundingBox().inflate(6));
    }
}
