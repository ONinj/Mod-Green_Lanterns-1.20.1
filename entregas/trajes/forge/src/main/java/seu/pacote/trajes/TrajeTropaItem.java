package seu.pacote.trajes;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import seu.pacote.trajes.client.TrajeRenderer;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * Peça de traje "segunda pele" (GeckoLib). Arquivos usados:
 *   geo/item/armor/traje_<tropa>_<variante>.geo.json
 *   textures/item/armor/traje_<tropa>_<variante>.png (+ _glowmask.png)
 *   animations/item/armor/traje_<tropa>_<variante>.animation.json
 */
public class TrajeTropaItem extends ArmorItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final String tropa;     // verde, amarelo, vermelho, laranja, azul, indigo, violeta, preto, branco
    private final String variante;  // a, b ou energia

    public TrajeTropaItem(ArmorMaterial material, Type type, Properties properties, String tropa, String variante) {
        super(material, type, properties);
        this.tropa = tropa;
        this.variante = variante;
    }

    public String getTropa() { return tropa; }
    public String getVariante() { return variante; }
    /** nome base dos arquivos: traje_<tropa>_<variante> */
    public String getArquivo() { return "traje_" + tropa + "_" + variante; }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private GeoArmorRenderer<?> renderer;

            @Override
            public @NotNull HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                if (this.renderer == null) this.renderer = new TrajeRenderer();
                this.renderer.prepForRender(entity, stack, slot, original);
                return this.renderer;
            }
        });
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
