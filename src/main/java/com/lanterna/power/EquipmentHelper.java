package com.lanterna.power;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.item.PowerBatteryItem;
import com.lanterna.item.PowerRingItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Optional;

/**
 * Atalhos para os slots próprios do mod (aba "Lanterna" do inventário):
 *   slot 0 -> anel de poder
 *   slot 1 -> bateria de poder
 */
public final class EquipmentHelper {

    public static Optional<ItemStackHandler> slots(Player player) {
        return ModCapabilities.get(player).map(RingData::equipment);
    }

    /** O anel no slot de anel (vazio se não houver). */
    public static ItemStack ring(Player player) {
        ItemStack stack = slots(player).map(h -> h.getStackInSlot(RingData.RING_SLOT)).orElse(ItemStack.EMPTY);
        return stack.getItem() instanceof PowerRingItem ? stack : ItemStack.EMPTY;
    }

    /** A bateria no slot de bateria (vazio se não houver). */
    public static ItemStack battery(Player player) {
        ItemStack stack = slots(player).map(h -> h.getStackInSlot(RingData.BATTERY_SLOT)).orElse(ItemStack.EMPTY);
        return com.lanterna.item.BatteryItems.isBattery(stack) ? stack : ItemStack.EMPTY;
    }

    /** Coloca no slot se estiver vazio. Devolve false se já estiver ocupado. */
    public static boolean equip(Player player, int slot, ItemStack stack) {
        return slots(player).map(h -> {
            if (!h.getStackInSlot(slot).isEmpty()) return false;
            h.setStackInSlot(slot, stack);
            return true;
        }).orElse(false);
    }

    /** Tira o item do slot e devolve ele. */
    public static ItemStack take(Player player, int slot) {
        return slots(player).map(h -> {
            ItemStack old = h.getStackInSlot(slot);
            h.setStackInSlot(slot, ItemStack.EMPTY);
            return old;
        }).orElse(ItemStack.EMPTY);
    }

    /** Coloca no slot; se estiver ocupado, vai para o inventário; se estiver cheio, cai no chão. */
    public static void equipOrGive(Player player, int slot, ItemStack stack) {
        if (equip(player, slot, stack)) return;
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private EquipmentHelper() {}
}
