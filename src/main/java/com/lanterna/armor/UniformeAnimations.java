package com.lanterna.armor;

import com.lanterna.LanternaMod;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.GeckoLibCache;

/** Confere se o arquivo de animação de um uniforme tem uma animação com tal nome. */
public final class UniformeAnimations {

    public static boolean has(String arquivo, String name) {
        try {
            var anims = GeckoLibCache.getBakedAnimations()
                    .get(LanternaMod.id("animations/item/armor/" + arquivo + ".animation.json"));
            return anims != null && anims.getAnimation(name) != null;
        } catch (Throwable e) {
            return false; // no servidor o cache de animações não existe
        }
    }

    private UniformeAnimations() {}
}
