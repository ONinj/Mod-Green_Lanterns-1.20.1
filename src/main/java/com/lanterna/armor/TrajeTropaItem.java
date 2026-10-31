package com.lanterna.armor;

import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;

/**
 * Peça de traje "segunda pele" (GeckoLib). É um uniforme como os outros, só que com arquivos próprios:
 *   geo/item/armor/traje_<tropa>_<variante>.geo.json
 *   textures/item/armor/traje_<tropa>_<variante>.png (+ _glowmask.png)
 *   animations/item/armor/traje_<tropa>_<variante>.animation.json
 */
public class TrajeTropaItem extends UniformeTropaItem {
    private final String variante;

    public TrajeTropaItem(ArmorMaterial material, Type type, Properties properties, String tropa, String variante) {
        super(material, type, properties, tropa);
        this.variante = variante;
    }

    /** "a" ou "b" */
    public String getVariante() {
        return variante;
    }

    @Override
    public String arquivo() {
        return "traje_" + getTropa() + "_" + variante;
    }

    @Override
    public String getArmorTexture(ItemStack stack, net.minecraft.world.entity.Entity entity,
                                  net.minecraft.world.entity.EquipmentSlot slot, String type) {
        return "lanterna:textures/item/armor/" + arquivo() + ".png";
    }
}
