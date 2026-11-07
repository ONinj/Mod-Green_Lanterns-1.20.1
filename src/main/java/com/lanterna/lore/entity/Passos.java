package com.lanterna.lore.entity;

import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * Ritmo do andar das criaturas (GeckoLib): a animação de andar acompanha a velocidade de verdade,
 * para os pés não "patinarem" quando o bicho anda devagar nem ficarem em câmera lenta quando corre.
 */
public final class Passos {
    /** Velocidade da animação para a amplitude do passo (0 = parado, 1 = correndo). */
    public static double ritmo(float passo) {
        return Mth.clamp(0.55 + passo * 1.45, 0.55, 2.0);
    }

    public static <T extends GeoAnimatable> PlayState andar(AnimationState<T> s, RawAnimation andando, RawAnimation parado, boolean anda) {
        s.getController().setAnimationSpeed(anda ? ritmo(s.getLimbSwingAmount()) : 1.0);
        return s.setAndContinue(anda ? andando : parado);
    }

    private Passos() {}
}
