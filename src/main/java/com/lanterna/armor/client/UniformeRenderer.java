package com.lanterna.armor.client;

import com.lanterna.armor.UniformeTropaItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Desenha o uniforme 3D; as partes da textura _glowmask brilham no escuro (só cliente).
 * A máscara (osso armorHead) sobe ou desce conforme a escolha do jogador na tela de Personalização,
 * para encaixar nos olhos da skin dele.
 */
public class UniformeRenderer extends GeoArmorRenderer<UniformeTropaItem> {
    public UniformeRenderer() {
        super(new UniformeModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int light, int overlay,
                                  float red, float green, float blue, float alpha) {
        float off = "armorHead".equals(bone.getName()) && currentEntity instanceof net.minecraft.world.entity.player.Player p
                ? com.lanterna.client.visual.ClientVisual.mascaraPx(p.getId()) : 0f;
        if (off == 0f) {
            super.renderCubesOfBone(poseStack, bone, buffer, light, overlay, red, green, blue, alpha);
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0, off / 16f, 0);
        super.renderCubesOfBone(poseStack, bone, buffer, light, overlay, red, green, blue, alpha);
        poseStack.popPose();
    }
}
