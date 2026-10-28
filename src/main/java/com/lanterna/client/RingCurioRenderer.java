package com.lanterna.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Desenha o anel no punho da mão principal do personagem, com a pedra
 * virada para o lado de fora e sempre brilhando (luz máxima).
 *
 * Ajuste fino: mude os valores em POSITION / SCALE se quiser o anel
 * mais para cima, mais para baixo, maior ou menor.
 */
public class RingCurioRenderer implements ICurioRenderer {

    /** Altura no braço (em pixels do modelo, 0 = ombro, 10 = ponta da mão). */
    private static final float POSITION = 9.0f;
    /** Tamanho do anel em relação ao modelo do item. */
    private static final float SCALE = 0.62f;

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext,
            PoseStack poseStack, RenderLayerParent<T, M> renderLayerParent, MultiBufferSource buffer, int light,
            float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw,
            float headPitch) {

        if (!(renderLayerParent.getModel() instanceof HumanoidModel<?> model)) return;
        LivingEntity entity = slotContext.entity();
        boolean right = entity.getMainArm() == HumanoidArm.RIGHT;
        ModelPart arm = right ? model.rightArm : model.leftArm;
        if (!arm.visible) return;

        poseStack.pushPose();

        // Segue o movimento do braço
        arm.translateAndRotate(poseStack);

        // Centro do braço: braço direito vai de x=-3 a x=1, o esquerdo de -1 a 3
        float centerX = right ? -1f / 16f : 1f / 16f;
        poseStack.translate(centerX, POSITION / 16f, 0);

        // Anel deitado em volta do braço, com a pedra virada para fora
        poseStack.mulPose(Axis.XP.rotationDegrees(90));
        poseStack.mulPose(Axis.ZP.rotationDegrees(right ? 90 : -90));
        poseStack.scale(SCALE, SCALE, SCALE);

        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), 0);

        poseStack.popPose();
    }
}
