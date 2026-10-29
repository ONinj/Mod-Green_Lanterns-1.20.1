package com.lanterna.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * No espaço (que é vazio), o jogador continua se movendo mesmo onde os chunks ainda não chegaram.
 * Sem isso, na hiperpropulsão ele "congelaria" a cada poucos blocos esperando o chunk.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;hasChunkAt(II)Z"))
    private boolean lanterna$spaceIsAlwaysLoaded(Level level, int x, int z) {
        return level.hasChunkAt(x, z) || com.lanterna.client.flight.ClientFlight.allowUnloadedMove();
    }
}
