package com.lanterna.mixin;

import com.lanterna.client.flight.Autopilot;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Com o piloto automático ligado, o anel é quem aponta a direção (o mouse não vira o jogador). */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void lanterna$autopilotSteers(LocalPlayer player, double yaw, double pitch) {
        if (!Autopilot.active()) player.turn(yaw, pitch);
    }
}
