package com.lanterna.client.alien;

import com.lanterna.LanternaMod;
import com.lanterna.alien.AlienEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

import java.util.HashMap;
import java.util.Map;

/**
 * Modelo GeckoLib dos alienígenas. Para cada espécie (id do planeta) procura arquivos próprios
 * e, se não houver, usa o modelo provisório:
 *
 *   assets/lanterna/geo/alien/<espécie>.geo.json                 (senão: default.geo.json)
 *   assets/lanterna/textures/entity/alien/<espécie>.png          (senão: default.png, pintada na cor da espécie)
 *   assets/lanterna/animations/alien/<espécie>.animation.json    (senão: default.animation.json)
 *
 * As animações precisam se chamar "animation.alien.idle" e "animation.alien.walk".
 */
public class AlienModel extends GeoModel<AlienEntity> {
    private static final Map<ResourceLocation, Boolean> EXISTS = new HashMap<>();

    private static ResourceLocation pick(String folder, String species, String suffix) {
        ResourceLocation own = LanternaMod.id(folder + "/" + species + suffix);
        boolean present = EXISTS.computeIfAbsent(own,
                id -> Minecraft.getInstance().getResourceManager().getResource(id).isPresent());
        return present ? own : LanternaMod.id(folder + "/default" + suffix);
    }

    /** A espécie tem textura própria (então não é pintada com a cor provisória). */
    public static boolean hasOwnTexture(String species) {
        return !pick("textures/entity/alien", species, ".png").getPath().endsWith("default.png");
    }

    @Override
    public ResourceLocation getModelResource(AlienEntity alien) {
        return pick("geo/alien", alien.speciesId(), ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(AlienEntity alien) {
        return pick("textures/entity/alien", alien.speciesId(), ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(AlienEntity alien) {
        return pick("animations/alien", alien.speciesId(), ".animation.json");
    }

    public static void clearCache() {
        EXISTS.clear();
    }
}
