package com.lanterna.client;

import com.mojang.blaze3d.vertex.PoseStack;
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
 * Desenha um anel pequeno embaixo do punho (na ponta da mão principal) de qualquer jogador
 * que esteja usando um, com a pedra para baixo e sempre brilhando (luz máxima).
 *
 * Ajuste fino: mude POSITION / SCALE se quiser o anel mais para cima, mais para baixo,
 * maior ou menor.
 */
public class RingHandLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /** Onde o anel fica no braço (pixels do modelo: -2 = ombro, 10 = ponta/fundo da mão). */
    private static final float POSITION = 9.75f;
    /** Tamanho do anel (0.25 = um quarto do tamanho do item). */
    private static final float SCALE = 0.24f;

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

        // Centro do braço (o direito vai de x=-3 a 1, o esquerdo de -1 a 3), na face de BAIXO da mão.
        // No espaço do modelo o Y aponta para baixo, então a pedra do anel (que fica em +Y no item)
        // já sai virada para baixo, na ponta da mão, sem precisar girar.
        float centerX = right ? -1f / 16f : 1f / 16f;
        poseStack.translate(centerX, POSITION / 16f, 0);
        poseStack.scale(SCALE, SCALE, SCALE);

        Minecraft.getInstance().getItemRenderer().renderStatic(ring, ItemDisplayContext.FIXED,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, poseStack, buffer, player.level(), 0);

        poseStack.popPose();
    }
}
