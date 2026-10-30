package com.lanterna.mixin;

import com.lanterna.naves.NaveEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pilotando uma nave, a câmera em terceira pessoa fica longe o bastante para ver a nave inteira (e não dentro do casco). */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Unique private Entity lanterna$entity;

    @Inject(method = "setup", at = @At("HEAD"))
    private void lanterna$grab(BlockGetter level, Entity entity, boolean detached, boolean mirror, float partialTick, CallbackInfo ci) {
        lanterna$entity = entity;
    }

    @ModifyArg(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(D)D"))
    private double lanterna$shipZoom(double dist) {
        if (lanterna$entity != null && lanterna$entity.getVehicle() instanceof NaveEntity n) {
            return Math.max(dist, n.tipo().width * 1.6 + 5);
        }
        return dist;
    }
}
