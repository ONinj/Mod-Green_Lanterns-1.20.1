package com.lanterna.mixin;

import com.lanterna.space.HyperRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Aceita a velocidade da hiperpropulsão no espaço (senão o servidor puxa o jogador de volta). */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Redirect(method = "handleMovePlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isChangingDimension()Z"),
            require = 1)
    private boolean lanterna$hyperIsFine(ServerPlayer player) {
        return player.isChangingDimension() || HyperRules.isHyper(player);
    }
}
