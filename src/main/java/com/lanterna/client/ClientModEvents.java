package com.lanterna.client;

import com.lanterna.LanternaMod;
import com.lanterna.block.PowerBatteryBlockEntity;
import com.lanterna.item.PowerBatteryItem;
import com.lanterna.item.PowerRingItem;
import com.lanterna.registry.ModBlocks;
import com.lanterna.registry.ModItems;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Eventos de cliente no barramento do mod (registro). */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.TOGGLE_FLIGHT);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("ring_hud", RingHudOverlay.HUD);
        event.registerAboveAll("selection_cutscene", CutsceneOverlay.OVERLAY);
    }

    /** Pinta a pedra do anel (camada 1) e o núcleo da bateria (tint 0) na cor da Tropa. */
    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 1 ? PowerRingItem.getColor(stack) : -1,
                ModItems.POWER_RING.get());
        event.register((stack, tintIndex) -> tintIndex == 0 ? PowerBatteryItem.getColor(stack) : -1,
                ModItems.POWER_BATTERY.get());
    }

    @SubscribeEvent
    public static void blockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0 || level == null || pos == null) return -1;
            if (level.getBlockEntity(pos) instanceof PowerBatteryBlockEntity battery) {
                return battery.getColor();
            }
            return -1;
        }, ModBlocks.POWER_BATTERY.get());
    }

    private ClientModEvents() {}
}
