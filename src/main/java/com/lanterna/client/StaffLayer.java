package com.lanterna.client;

import com.lanterna.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Cajado da Compaixão no slot de bateria: aparece na MÃO ESQUERDA do personagem,
 * sem ele precisar segurar (se a mão esquerda já estiver segurando algo, não aparece).
 */
public class StaffLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private ItemStack staff;

    public StaffLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (player.isInvisible() || !ClientVisibleRings.of(player.getId()).staff()) return;
        ItemStack leftHand = player.getMainArm() == HumanoidArm.LEFT ? player.getMainHandItem() : player.getOffhandItem();
        if (!leftHand.isEmpty()) return;
        if (staff == null) staff = new ItemStack(ModItems.COMPASSION_STAFF.get());

        poseStack.pushPose();
        // Igual ao item na mão (ItemInHandLayer), na mão esquerda
        getParentModel().translateToHand(HumanoidArm.LEFT, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.translate(-1.0F / 16.0F, 0.125F, -0.625F);
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, staff,
                ItemDisplayContext.THIRD_PERSON_LEFT_HAND, true, poseStack, buffer, light);
        poseStack.popPose();
    }
}
