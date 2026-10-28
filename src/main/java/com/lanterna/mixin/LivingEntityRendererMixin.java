package com.lanterna.mixin;

import com.lanterna.client.flight.FlightPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inclina o corpo no voo dentro da rotação do próprio modelo
 * (assim o nome em cima da cabeça continua reto).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity> {

    @Inject(method = "setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
            at = @At("TAIL"))
    private void lanterna$flightTilt(T entity, PoseStack poseStack, float ageInTicks, float rotationYaw,
                                     float partialTicks, CallbackInfo ci) {
        if (entity instanceof Player player) {
            FlightPose.tiltBody(player, poseStack, partialTicks);
        }
    }
}
