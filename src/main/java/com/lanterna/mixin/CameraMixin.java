package com.lanterna.mixin;

import com.lanterna.naves.NaveEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
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
    @Shadow private Vec3 position;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f left;
    @Shadow @Final private Vector3f up;
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow private boolean detached;
    @Shadow public abstract float getXRot();
    @Shadow public abstract float getYRot();

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

    /** Terceira pessoa melhorada (por cima do ombro). */
    @Inject(method = "setup", at = @At("TAIL"))
    private void lanterna$ombro(BlockGetter level, Entity entity, boolean detached, boolean mirror, float partialTick, CallbackInfo ci) {
        // cerimônia de escolha do anel: câmera de cinema
        double[] cine = com.lanterna.client.escolha.Cerimonias.camera(entity, partialTick, position, getYRot(), getXRot());
        if (cine == null) cine = com.lanterna.client.mogo.ClienteChamado.camera(entity, partialTick);
        if (cine != null) {
            this.detached = true;
            setRotation((float) cine[3], (float) cine[4]);
            setPosition(new Vec3(cine[0], cine[1], cine[2]));
            com.lanterna.client.camera.CameraLanterna.aplicada = false;
            return;
        }
        boolean ativa = com.lanterna.client.camera.CameraLanterna.ativa(entity, detached, mirror);
        com.lanterna.client.camera.CameraLanterna.aplicada = ativa;
        if (!ativa) return;
        Vec3 olho = new Vec3(net.minecraft.util.Mth.lerp(partialTick, entity.xo, entity.getX()),
                net.minecraft.util.Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getEyeHeight(),
                net.minecraft.util.Mth.lerp(partialTick, entity.zo, entity.getZ()));
        setPosition(com.lanterna.client.camera.CameraLanterna.posicionar(level, entity, olho, forwards, left, up));
    }
}
