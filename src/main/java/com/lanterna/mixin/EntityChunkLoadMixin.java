package com.lanterna.mixin;

import com.lanterna.space.SpaceBodies;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * O Forge carrega na hora (travando o servidor) o chunk para onde uma entidade se move.
 * No espaço, que é vazio, isso não é necessário para jogadores — e em hiperpropulsão
 * (dezenas de chunks por segundo) travaria o servidor. Os chunks continuam chegando normalmente,
 * só que sem parar o jogo para esperar.
 */
@Mixin(Entity.class)
public abstract class EntityChunkLoadMixin {

    @Redirect(method = "setPosRaw(DDD)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;"),
            require = 0)
    private LevelChunk lanterna$noSyncLoadInSpace(Level level, int x, int z) {
        if ((Object) this instanceof ServerPlayer && level.dimension().equals(SpaceBodies.SPACE)) return null;
        return level.getChunk(x, z);
    }
}
