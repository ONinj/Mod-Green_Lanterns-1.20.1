package seu.pacote.trajes.client;

import net.minecraft.resources.ResourceLocation;
import seu.pacote.trajes.ModTrajes;
import seu.pacote.trajes.TrajeTropaItem;
import software.bernie.geckolib.model.GeoModel;

public class TrajeModel extends GeoModel<TrajeTropaItem> {
    private static ResourceLocation rl(String p) { return new ResourceLocation(ModTrajes.MODID, p); }
    @Override public ResourceLocation getModelResource(TrajeTropaItem i) { return rl("geo/item/armor/" + i.getArquivo() + ".geo.json"); }
    @Override public ResourceLocation getTextureResource(TrajeTropaItem i) { return rl("textures/item/armor/" + i.getArquivo() + ".png"); }
    @Override public ResourceLocation getAnimationResource(TrajeTropaItem i) { return rl("animations/item/armor/" + i.getArquivo() + ".animation.json"); }
}
