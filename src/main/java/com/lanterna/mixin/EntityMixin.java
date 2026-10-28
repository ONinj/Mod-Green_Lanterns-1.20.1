package com.lanterna.mixin;

import com.lanterna.client.flight.AuraOutline;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** O contorno usa a cor da Tropa em vez da cor do time. */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "getTeamColor()I", at = @At("HEAD"), cancellable = true)
    private void lanterna$ringColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide) {
            int color = AuraOutline.color(self);
            if (color >= 0) cir.setReturnValue(color);
        }
    }
}
