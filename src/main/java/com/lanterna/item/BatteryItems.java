package com.lanterna.item;

import com.lanterna.corps.BuiltinCorps;
import com.lanterna.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * As "baterias" de cada Tropa:
 *  - blocos (lanterna, braseiro, relicário, torre, farol, drusa, caveira): {@link PowerBatteryItem}
 *  - Índigo: o Cajado da Compaixão (item): {@link CompassionStaffItem}
 *  - Branca (Vida): não tem bateria — o anel branco não descarrega.
 */
public final class BatteryItems {

    public static boolean isBattery(ItemStack stack) {
        return stack.getItem() instanceof PowerBatteryItem || stack.getItem() instanceof CompassionStaffItem;
    }

    public static boolean hasBattery(ResourceLocation corps) {
        return !corps.equals(BuiltinCorps.WHITE.id());
    }

    /** A bateria da Tropa (vazio para a Tropa da Vida). */
    public static ItemStack create(ResourceLocation corps, int color) {
        if (corps.equals(BuiltinCorps.WHITE.id())) return ItemStack.EMPTY;
        if (corps.equals(BuiltinCorps.INDIGO.id())) return new ItemStack(ModItems.COMPASSION_STAFF.get());
        return PowerBatteryItem.createBlock(corps, color);
    }

    public static ResourceLocation corpsOf(ItemStack stack) {
        if (stack.getItem() instanceof CompassionStaffItem) return BuiltinCorps.INDIGO.id();
        return PowerBatteryItem.getCorps(stack);
    }

    public static boolean isStaff(ItemStack stack) {
        return stack.getItem() instanceof CompassionStaffItem;
    }

    private BatteryItems() {}
}
