package com.lanterna.mixin;

import com.lanterna.power.VooNaAgua;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Voando com o anel embaixo d'água, o Ctrl (correr) fazia o jogo pôr o jogador para "nadar": a pose de nado somada
 * à pose de voo deixava o corpo de cabeça para baixo e o voo travava. Voando com o anel, não se nada.
 */
@Mixin(Player.class)
public abstract class PlayerSwimMixin {
    @Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
    private void lanterna$semNadarVoando(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (VooNaAgua.voando(self)) {
            self.setSwimming(false);
            ci.cancel();
        }
    }
}
