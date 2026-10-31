package com.lanterna.armor;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import com.lanterna.armor.client.UniformeRenderer;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * Peça de uniforme 3D (GeckoLib). Cada Tropa usa o próprio modelo e textura:
 *   assets/lanterna/geo/item/armor/uniforme_<tropa>.geo.json
 *   assets/lanterna/textures/item/armor/uniforme_<tropa>.png (+ _glowmask.png para as partes que brilham)
 *   assets/lanterna/animations/item/armor/uniforme_<tropa>.animation.json
 */
public class UniformeTropaItem extends ArmorItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final String tropa;

    public UniformeTropaItem(ArmorMaterial material, Type type, Properties properties, String tropa) {
        super(material, type, properties);
        this.tropa = tropa;
    }

    /** O Minecraft procura uma textura de armadura comum; aponta para a do uniforme (o desenho é do GeckoLib). */
    @Override
    public String getArmorTexture(ItemStack stack, net.minecraft.world.entity.Entity entity,
                                  net.minecraft.world.entity.EquipmentSlot slot, String type) {
        return "lanterna:textures/item/armor/uniforme_" + tropa + ".png";
    }

    /** Nome base dos arquivos (modelo, textura, animação): uniforme_<tropa>, ou traje_<tropa>_<variante>. */
    public String arquivo() {
        return "uniforme_" + tropa;
    }

    /** "verde", "vermelho", "laranja", "amarelo", "azul", "indigo", "violeta", "preto" ou "branco" */
    public String getTropa() {
        return this.tropa;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private GeoArmorRenderer<?> renderer;

            @Override
            public @NotNull HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack,
                                                                    EquipmentSlot slot, HumanoidModel<?> original) {
                if (this.renderer == null)
                    this.renderer = new UniformeRenderer();

                this.renderer.prepForRender(entity, stack, slot, original);
                return this.renderer;
            }
        });
    }

    /**
     * Animações feitas no Blockbench (arquivo uniforme_<tropa>.animation.json):
     *   "misc.idle"  toca sempre, em loop (parado)
     *   "misc.walk"  se existir, toca enquanto a pessoa anda
     * Sem keyframes (como vem agora), nada se mexe — e não dá erro.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new software.bernie.geckolib.core.animation.AnimationController<>(this, "uniforme", 5, state -> {
            net.minecraft.world.entity.Entity entity = state.getData(software.bernie.geckolib.constant.DataTickets.ENTITY);
            boolean moving = entity != null && entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4;
            if (moving && com.lanterna.armor.UniformeAnimations.has(arquivo(), "misc.walk")) {
                return state.setAndContinue(WALK);
            }
            return state.setAndContinue(IDLE);
        }));
    }

    private static final software.bernie.geckolib.core.animation.RawAnimation IDLE =
            software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("misc.idle");
    private static final software.bernie.geckolib.core.animation.RawAnimation WALK =
            software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("misc.walk");

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
