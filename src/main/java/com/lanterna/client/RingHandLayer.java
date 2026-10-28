package com.lanterna.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Desenha um anel pequeno na frente do punho da mão principal de qualquer jogador
 * que esteja usando um, com a pedra para fora e sempre brilhando (luz máxima).
 *
 * Ajuste fino: mude POSITION / SCALE se quiser o anel mais para cima, mais para baixo,
 * maior ou menor.
 */
public class RingHandLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /** Altura no braço (pixels do modelo: -2 = ombro, 10 = ponta da mão). */
    private static final float POSITION = 8.6f;
    /** Tamanho do anel (0.25 = um quarto do tamanho do item). */
    private static final float SCALE = 0.24f;
    /** Quanto o anel fica para fora da frente do punho (pixels). */
    private static final float OUTSET = 0.15f;

    public RingHandLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        ItemStack ring = ClientVisibleRings.get(player.getId());
        if (ring.isEmpty() || player.isInvisible()) return;

        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        ModelPart arm = right ? getParentModel().rightArm : getParentModel().leftArm;
        if (!arm.visible) return;

        poseStack.pushPose();

        // Segue o movimento do braço
        arm.translateAndRotate(poseStack);

        // Centro do braço (o direito vai de x=-3 a 1, o esquerdo de -1 a 3), na frente do punho (z=-2)
        float centerX = right ? -1f / 16f : 1f / 16f;
        poseStack.translate(centerX, POSITION / 16f, -(2f + OUTSET) / 16f);

        // Pedra virada para a frente do punho, aro em volta do "dedo"
        poseStack.mulPose(Axis.XP.rotationDegrees(-90));
        poseStack.scale(SCALE, SCALE, SCALE);

        Minecraft.getInstance().getItemRenderer().renderStatic(ring, ItemDisplayContext.FIXED,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, poseStack, buffer, player.level(), 0);

        poseStack.popPose();
    }
}
