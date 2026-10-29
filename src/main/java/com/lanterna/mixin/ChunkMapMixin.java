package com.lanterna.mixin;

import com.lanterna.space.HyperRules;
import com.lanterna.space.SpaceBodies;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Espaço leve para o servidor:
 *  - em hiperpropulsão o jogador não carrega chunks (o espaço é vazio);
 *  - chunks do espaço completamente vazios não são gravados no disco (não há nada neles).
 *    Se alguém construir no espaço, aquele chunk é gravado normalmente.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Shadow @Final ServerLevel level;

    @Inject(method = "skipPlayer", at = @At("HEAD"), cancellable = true)
    private void lanterna$skipWhileHyper(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (HyperRules.isHyper(player)) cir.setReturnValue(true);
    }

    @Inject(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At("HEAD"), cancellable = true)
    private void lanterna$skipEmptySpaceChunks(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        if (!level.dimension().equals(SpaceBodies.SPACE)) return;
        if (!chunk.getBlockEntitiesPos().isEmpty()) return;
        for (LevelChunkSection section : chunk.getSections()) {
            if (!section.hasOnlyAir()) return;
        }
        chunk.setUnsaved(false);
        cir.setReturnValue(false);
    }
}
