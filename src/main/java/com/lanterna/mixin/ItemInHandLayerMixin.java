package com.lanterna.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** O Cajado da Compaixão na mão de um jogador é desenhado em pé pela StaffLayer (client/cajado/Cajado), não pela mão. */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void lanterna$cajado(LivingEntity e, ItemStack stack, ItemDisplayContext ctx, HumanoidArm arm, PoseStack ps,
                                 MultiBufferSource buf, int light, CallbackInfo ci) {
        if (e instanceof Player && com.lanterna.client.cajado.Cajado.eCajado(stack)) ci.cancel();
    }
}
