package com.lanterna.power;

import com.lanterna.registry.ModItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.Optional;

/**
 * Atalhos para os slots do Curios usados pelo mod:
 *   "ring"            -> o anel de poder
 *   "lantern_battery" -> a bateria de bolso
 */
public final class CuriosHelper {
    public static final String RING_SLOT = "ring";
    public static final String BATTERY_SLOT = "lantern_battery";

    public static Optional<SlotResult> findRing(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity).resolve()
                .flatMap(handler -> handler.findFirstCurio(ModItems.POWER_RING.get()));
    }

    public static Optional<SlotResult> findBattery(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity).resolve()
                .flatMap(handler -> handler.findFirstCurio(ModItems.POWER_BATTERY.get()));
    }

    /** Coloca o item no primeiro espaço vazio do slot. Devolve false se não couber. */
    public static boolean equip(LivingEntity entity, String slot, ItemStack stack) {
        return CuriosApi.getCuriosInventory(entity).resolve()
                .flatMap(handler -> handler.getStacksHandler(slot))
                .map(stacksHandler -> {
                    IDynamicStackHandler stacks = stacksHandler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        if (stacks.getStackInSlot(i).isEmpty()) {
                            stacks.setStackInSlot(i, stack);
                            return true;
                        }
                    }
                    return false;
                })
                .orElse(false);
    }

    /** Tira o item de um slot e devolve uma cópia dele. */
    public static ItemStack take(LivingEntity entity, SlotResult result) {
        ItemStack copy = result.stack().copy();
        CuriosApi.getCuriosInventory(entity).ifPresent(handler ->
                handler.setEquippedCurio(result.slotContext().identifier(), result.slotContext().index(), ItemStack.EMPTY));
        return copy;
    }

    private CuriosHelper() {}
}
