package com.lanterna.especies.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import com.lanterna.especies.TemEspecie;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

/**
 * Modelo de uma espécie:
 *   geo:        geo/entity/especie_<id>.geo.json
 *   textura:    textures/entity/especie_<id>_v<n>.png  (+ _glowmask)
 *               ou textures/entity/corrupcao_<id>.png quando isCorrompido()
 *   animações:  animations/entity/especie_<id>.animation.json (misc.extra)
 *               e, como fallback, animations/entity/corpo_<tipo>.animation.json (todas as do corpo)
 */
public class EspecieGeoModel<T extends Entity & GeoAnimatable & TemEspecie> extends GeoModel<T> {
    private final String modid;

    public EspecieGeoModel(String modid) { this.modid = modid; }

    private ResourceLocation rl(String p) { return new ResourceLocation(modid, p); }

    @Override public ResourceLocation getModelResource(T e) {
        return rl("geo/entity/especie_" + e.getEspecie() + ".geo.json");
    }
    @Override public ResourceLocation getTextureResource(T e) {
        if (e.isCorrompido()) return rl("textures/entity/corrupcao_" + e.getEspecie() + ".png");
        return rl("textures/entity/especie_" + e.getEspecie() + "_v" + e.getVariantePele() + ".png");
    }
    @Override public ResourceLocation getAnimationResource(T e) {
        return rl("animations/entity/especie_" + e.getEspecie() + ".animation.json");
    }
    @Override public ResourceLocation[] getAnimationResourceFallbacks(T e) {
        return new ResourceLocation[] { rl("animations/entity/corpo_" + e.getTipoCorpo() + ".animation.json") };
    }
    /** Lumi'ae e Aurith têm partes semitransparentes (corpo gelatinoso / asas). Corrompidos ficam opacos. */
    @Override public RenderType getRenderType(T e, ResourceLocation tex) {
        if (e.isTranslucida() && !e.isCorrompido()) return RenderType.entityTranslucent(tex);
        return RenderType.entityCutoutNoCull(tex);
    }
}
