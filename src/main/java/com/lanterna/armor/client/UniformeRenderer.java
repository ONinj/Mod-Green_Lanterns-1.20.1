package com.lanterna.armor.client;

import com.lanterna.armor.UniformeTropaItem;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Desenha o uniforme 3D; as partes da textura _glowmask brilham no escuro (só cliente). */
public class UniformeRenderer extends GeoArmorRenderer<UniformeTropaItem> {
    public UniformeRenderer() {
        super(new UniformeModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
