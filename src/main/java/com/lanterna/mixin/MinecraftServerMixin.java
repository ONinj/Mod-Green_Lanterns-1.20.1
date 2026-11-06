package com.lanterna.mixin;

import com.lanterna.Lento;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Só para gravar vídeo em câmera lenta (Lento.FATOR = 1 no jogo normal). */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @ModifyConstant(method = "runServer", constant = @Constant(longValue = 50L), require = 0)
    private long lanterna$lento(long v) {
        return v * Lento.FATOR;
    }
}
