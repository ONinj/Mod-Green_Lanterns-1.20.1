package com.lanterna.menu;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.item.PowerBatteryItem;
import com.lanterna.item.PowerRingItem;
import com.lanterna.registry.ModMenus;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A aba "Lanterna": slot do anel, slot da bateria e o inventário do jogador.
 *
 * Posições (dentro da tela de 176x166):
 *   anel    -> (104, 18)
 *   bateria -> (104, 41)
 *   inventário e barra rápida no lugar de sempre
 */
public class LanternMenu extends AbstractContainerMenu {
    public static final int RING_X = 104, RING_Y = 18;
    public static final int BATTERY_X = 104, BATTERY_Y = 41;

    private static final int EQUIP_SLOTS = 2;
    private static final int INV_START = EQUIP_SLOTS;          // 2
    private static final int HOTBAR_START = INV_START + 27;    // 29
    private static final int END = HOTBAR_START + 9;           // 38

    private final Player player;

    public LanternMenu(int id, Inventory inventory) {
        super(ModMenus.LANTERN.get(), id);
        this.player = inventory.player;

        IItemHandler equipment = ModCapabilities.get(player)
                .<IItemHandler>map(RingData::equipment)
                .orElseGet(() -> new ItemStackHandler(2));

        addSlot(new EquipSlot(equipment, RingData.RING_SLOT, RING_X, RING_Y, true));
        addSlot(new EquipSlot(equipment, RingData.BATTERY_SLOT, BATTERY_X, BATTERY_Y, false));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    public Player player() {
        return player;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    /** Shift + clique: manda anel/bateria para o slot certo, e o resto entre inventário e barra. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < EQUIP_SLOTS) {
            if (!moveItemStackTo(stack, INV_START, END, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            if (stack.getItem() instanceof PowerRingItem && slots.get(RingData.RING_SLOT).mayPlace(stack)) {
                moved = moveItemStackTo(stack, RingData.RING_SLOT, RingData.RING_SLOT + 1, false);
            } else if (stack.getItem() instanceof PowerBatteryItem) {
                moved = moveItemStackTo(stack, RingData.BATTERY_SLOT, RingData.BATTERY_SLOT + 1, false);
            }
            if (!moved) {
                if (index < HOTBAR_START) {
                    if (!moveItemStackTo(stack, HOTBAR_START, END, false)) return ItemStack.EMPTY;
                } else if (!moveItemStackTo(stack, INV_START, HOTBAR_START, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();

        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }

    /** Slot que só aceita anel (do dono) ou bateria, um de cada. */
    private class EquipSlot extends SlotItemHandler {
        private final boolean ringSlot;

        EquipSlot(IItemHandler handler, int index, int x, int y, boolean ringSlot) {
            super(handler, index, x, y);
            this.ringSlot = ringSlot;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (ringSlot) {
                return stack.getItem() instanceof PowerRingItem && PowerRingItem.canBeWornBy(stack, player);
            }
            return stack.getItem() instanceof PowerBatteryItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }

        @Override
        public void set(ItemStack stack) {
            if (ringSlot && !stack.isEmpty() && PowerRingItem.getOwner(stack) == null && !player.level().isClientSide) {
                PowerRingItem.setOwner(stack, player);
            }
            super.set(stack);
        }

        @Nullable
        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return Pair.of(InventoryMenu.BLOCK_ATLAS, new ResourceLocation("lanterna",
                    ringSlot ? "item/empty_ring_slot" : "item/empty_battery_slot"));
        }
    }
}
