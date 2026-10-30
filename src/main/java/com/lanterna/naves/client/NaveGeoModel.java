package com.lanterna.naves.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import com.lanterna.naves.TemNave;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

/**
 * geo:       geo/entity/<tipo>.geo.json
 * textura:   textures/entity/<tipo>_<pintura>.png (+ _glowmask)
 * animações: animations/entity/<tipo>.animation.json
 */
public class NaveGeoModel<T extends Entity & GeoAnimatable & TemNave> extends GeoModel<T> {
    private final String modid;
    public NaveGeoModel(String modid) { this.modid = modid; }
    private ResourceLocation rl(String p) { return new ResourceLocation(modid, p); }

    @Override public ResourceLocation getModelResource(T e) { return rl("geo/entity/" + e.getTipoNave() + ".geo.json"); }
    @Override public ResourceLocation getTextureResource(T e) {
        String p = e.getPintura();
        if (p == null || p.isEmpty()) return rl("textures/entity/" + e.getTipoNave() + ".png");   // destroços: textura única
        return rl("textures/entity/" + e.getTipoNave() + "_" + p + ".png");
    }
    @Override public ResourceLocation getAnimationResource(T e) { return rl("animations/entity/" + e.getTipoNave() + ".animation.json"); }
    /** Torres: os ossos canhao_N giram no eixo Y para onde o piloto (ou a IA) mira. */
    @Override
    public void setCustomAnimations(T e, long id, software.bernie.geckolib.core.animation.AnimationState<T> state) {
        super.setCustomAnimations(e, id, state);
        com.lanterna.naves.NaveTipo tipo = com.lanterna.naves.NaveTipo.byId(e.getTipoNave());
        if (tipo.torres.length == 0) return;
        float rad = (float) Math.toRadians(-e.getAnguloTorre(state.getPartialTick()));
        for (int i : tipo.torres) {
            var bone = getAnimationProcessor().getBone("canhao_" + i);
            if (bone != null) bone.setRotY(rad);
        }
    }

    /** a cabine e o fogo dos motores são semitransparentes */
    @Override public RenderType getRenderType(T e, ResourceLocation tex) { return RenderType.entityTranslucent(tex); }
}
