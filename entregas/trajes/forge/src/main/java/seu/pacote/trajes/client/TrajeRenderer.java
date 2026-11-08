package seu.pacote.trajes.client;

import seu.pacote.trajes.TrajeTropaItem;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Renderiza o traje; tudo o que está no _glowmask brilha no escuro. */
public class TrajeRenderer extends GeoArmorRenderer<TrajeTropaItem> {
    public TrajeRenderer() {
        super(new TrajeModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
