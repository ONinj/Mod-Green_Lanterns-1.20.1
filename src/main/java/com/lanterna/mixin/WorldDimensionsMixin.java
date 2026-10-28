package com.lanterna.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * O Minecraft marca qualquer dimensão nova como "experimental" e mostra um aviso ao abrir o mundo.
 * As dimensões do Lanterna (espaço e planetas) são marcadas como estáveis, então o aviso não aparece por causa delas.
 * (Dimensões de outros mods continuam com o comportamento normal.)
 */
@Mixin(WorldDimensions.class)
public abstract class WorldDimensionsMixin {

    @Inject(method = "checkStability", at = @At("HEAD"), cancellable = true)
    private static void lanterna$stableDimensions(ResourceKey<LevelStem> key, LevelStem stem,
                                                 CallbackInfoReturnable<Lifecycle> cir) {
        if ("lanterna".equals(key.location().getNamespace())) {
            cir.setReturnValue(Lifecycle.stable());
        }
    }
}
