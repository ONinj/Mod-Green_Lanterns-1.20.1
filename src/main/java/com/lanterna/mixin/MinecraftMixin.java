package com.lanterna.mixin;

import com.lanterna.client.flight.AuraOutline;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Faz o jogador com anel ligado ganhar o contorno de brilho. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "shouldEntityAppearGlowing(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void lanterna$ringOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (AuraOutline.shouldOutline(entity)) cir.setReturnValue(true);
    }
}
