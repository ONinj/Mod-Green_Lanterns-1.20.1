package com.lanterna.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * O Minecraft só atualiza entidades em chunks carregados. No espaço, em hiperpropulsão, o jogador
 * passa por chunks que nem existem ainda; aqui garantimos que ele continue sendo atualizado.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Unique
    private int lanterna$tickBefore = -1;

    @Inject(method = "tickEntities", at = @At("HEAD"))
    private void lanterna$before(CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        lanterna$tickBefore = player != null ? player.tickCount : -1;
    }

    @Inject(method = "tickEntities", at = @At("TAIL"))
    private void lanterna$after(CallbackInfo ci) {
        ClientLevel self = (ClientLevel) (Object) this;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.level() != self || player.isRemoved() || player.isPassenger()) return;
        if (!com.lanterna.client.flight.ClientFlight.allowUnloadedMove()) return;
        if (player.tickCount == lanterna$tickBefore) self.tickNonPassenger(player);
    }
}
