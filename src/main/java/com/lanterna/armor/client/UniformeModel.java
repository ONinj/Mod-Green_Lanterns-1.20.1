package com.lanterna.armor.client;

import com.lanterna.LanternaMod;
import com.lanterna.armor.UniformeTropaItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Modelo, textura e animação de cada Tropa (só cliente). */
public class UniformeModel extends GeoModel<UniformeTropaItem> {

    @Override
    public ResourceLocation getModelResource(UniformeTropaItem item) {
        return LanternaMod.id("geo/item/armor/" + item.arquivo() + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(UniformeTropaItem item) {
        return LanternaMod.id("textures/item/armor/" + item.arquivo() + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(UniformeTropaItem item) {
        return LanternaMod.id("animations/item/armor/" + item.arquivo() + ".animation.json");
    }
}
