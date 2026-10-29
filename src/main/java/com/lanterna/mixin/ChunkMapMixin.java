package com.lanterna.mixin;

import com.lanterna.space.HyperRules;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Em hiperpropulsão no espaço, o jogador não carrega chunks (o espaço é vazio). */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

    @Inject(method = "skipPlayer", at = @At("HEAD"), cancellable = true)
    private void lanterna$skipWhileHyper(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (HyperRules.isHyper(player)) cir.setReturnValue(true);
    }
}
