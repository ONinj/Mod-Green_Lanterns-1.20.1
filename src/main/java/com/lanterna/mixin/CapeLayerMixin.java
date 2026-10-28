package com.lanterna.mixin;

import com.lanterna.client.flight.ClientFlight;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Capa no voo do anel: em vez de levantar como se o jogador corresse de pé
 * (o que, com o corpo deitado, faria ela apontar para cima), ela fica deitada
 * nas costas e tremula mais rápido quanto maior a velocidade.
 */
@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void lanterna$flightCape(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                                     float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                                     float netHeadYaw, float headPitch, CallbackInfo ci) {
        float k = ClientFlight.tilt(player, partialTicks);
        if (k <= 0.05f) return; // de pé (ou pairando): capa normal do Minecraft

        ci.cancel();
        if (!player.isCapeLoaded() || player.isInvisible() || !player.isModelPartShown(PlayerModelPart.CAPE)
                || player.getCloakTextureLocation() == null || player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            return;
        }

        double speed = Math.sqrt(Mth.lengthSquared(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo));
        float bank = ClientFlight.bank(player, partialTicks);

        // Quase colada nas costas; tremula com o vento (mais rápido e mais forte em alta velocidade)
        float flutter = Mth.sin(ageInTicks * (0.6f + (float) speed * 0.8f)) * (2f + (float) speed * 6f);
        float lift = Mth.lerp(k, 30f, 8f) + flutter;
        float sway = Mth.clamp(-bank * 0.35f, -20f, 20f);

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, 0.125F);
        poseStack.mulPose(Axis.XP.rotationDegrees(6.0F + lift));
        poseStack.mulPose(Axis.ZP.rotationDegrees(sway / 2.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - sway / 2.0F));

        @SuppressWarnings("unchecked")
        PlayerModel<AbstractClientPlayer> model =
                ((RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) (Object) this).getParentModel();
        VertexConsumer vc = buffer.getBuffer(RenderType.entitySolid(player.getCloakTextureLocation()));
        model.renderCloak(poseStack, vc, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
