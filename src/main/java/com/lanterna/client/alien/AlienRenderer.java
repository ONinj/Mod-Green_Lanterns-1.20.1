package com.lanterna.client.alien;

import com.lanterna.alien.AlienEntity;
import com.lanterna.alien.AlienSpecies;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Desenha os alienígenas. No modelo provisório, cada espécie liga/desliga partes
 * (olhos, antenas, braços extras, cauda, cabeça grande) e muda de tamanho e de cor.
 * Num modelo próprio da espécie essas partes podem nem existir — tudo bem, é ignorado.
 */
public class AlienRenderer extends GeoEntityRenderer<AlienEntity> {

    public AlienRenderer(EntityRendererProvider.Context context) {
        super(context, new AlienModel());
        this.shadowRadius = 0.45f;
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, AlienEntity alien,
                                    BakedGeoModel model, boolean isReRender, float partialTick, int light, int overlay) {
        float s = alien.species().height();
        super.scaleModelForRender(widthScale * s, heightScale * s, poseStack, alien, model, isReRender, partialTick, light, overlay);
    }

    @Override
    public Color getRenderColor(AlienEntity alien, float partialTick, int light) {
        if (AlienModel.hasOwnTexture(alien.speciesId())) return Color.WHITE;
        return Color.ofOpaque(alien.species().skin());
    }

    @Override
    public void renderRecursively(PoseStack poseStack, AlienEntity alien, GeoBone bone, RenderType renderType,
                                  MultiBufferSource buffer, VertexConsumer vc, boolean isReRender, float partialTick,
                                  int light, int overlay, float r, float g, float b, float a) {
        AlienSpecies sp = alien.species();
        switch (bone.getName()) {
            case "brain" -> bone.setHidden(!sp.bigBrain());
            case "eye_left", "eye_right" -> bone.setHidden(sp.eyes() == 1);
            case "eye_mid" -> bone.setHidden(sp.eyes() == 2);
            case "antenna_left", "antenna_right" -> bone.setHidden(!sp.antennae());
            case "arm2_left", "arm2_right" -> bone.setHidden(!sp.fourArms());
            case "tail" -> bone.setHidden(!sp.tail());
            case "head" -> {
                bone.setScaleX(sp.headScale());
                bone.setScaleY(sp.headScale());
                bone.setScaleZ(sp.headScale());
            }
            default -> { }
        }
        super.renderRecursively(poseStack, alien, bone, renderType, buffer, vc, isReRender, partialTick, light, overlay, r, g, b, a);
    }
}
