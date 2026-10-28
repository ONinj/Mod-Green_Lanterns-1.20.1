package com.lanterna.client;

import com.lanterna.LanternaMod;
import com.lanterna.client.screen.LanternTabs;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.OpenLanternMenuPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.client.event.ScreenEvent;
import com.lanterna.network.PocketRechargePacket;
import com.lanterna.network.ToggleFlightPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Eventos de cliente durante o jogo. */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        while (KeyBindings.TOGGLE_FLIGHT.consumeClick()) {
            if (ClientRingData.hasRing) {
                ModNetwork.sendToServer(new ToggleFlightPacket());
            }
        }
        while (KeyBindings.POCKET_RECHARGE.consumeClick()) {
            if (ClientRingData.hasRing) {
                ModNetwork.sendToServer(new PocketRechargePacket());
            }
        }
        while (KeyBindings.OPEN_LANTERN.consumeClick()) {
            ModNetwork.sendToServer(new OpenLanternMenuPacket());
        }
        ClientCutscene.tick();
    }

    // ===================== Abas no inventário normal =====================

    /** Desenha as abas "Inventário" e "Lanterna" em cima do inventário de sobrevivência. */
    @SubscribeEvent
    public static void onInventoryRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        GuiGraphics g = event.getGuiGraphics();
        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        g.pose().pushPose();
        g.pose().translate(0, 0, 300); // por cima dos itens
        LanternTabs.renderBack(g, left, top, LanternTabs.Tab.INVENTORY);
        LanternTabs.renderFront(g, left, top, LanternTabs.Tab.INVENTORY);
        g.pose().popPose();
        LanternTabs.renderTooltip(g, Minecraft.getInstance().font, left, top, event.getMouseX(), event.getMouseY());
    }

    /** Clique na aba "Lanterna" abre a tela do anel. */
    @SubscribeEvent
    public static void onInventoryClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || event.getButton() != 0) return;
        LanternTabs.Tab tab = LanternTabs.hovered(screen.getGuiLeft(), screen.getGuiTop(), event.getMouseX(), event.getMouseY());
        if (tab == null) return;
        if (tab == LanternTabs.Tab.LANTERN) ModNetwork.sendToServer(new OpenLanternMenuPacket());
        event.setCanceled(true); // não deixa o clique na aba jogar o item da mão no chão
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientRingData.reset();
        ClientCutscene.reset();
        ClientVisibleRings.clear();
    }

    private ClientForgeEvents() {}
}
