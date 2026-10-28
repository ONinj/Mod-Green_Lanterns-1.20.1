package com.lanterna.client;

import com.lanterna.LanternaMod;
import com.lanterna.client.screen.LanternTabs;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.OpenLanternMenuPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.client.event.ScreenEvent;
import com.lanterna.client.flight.ClientFlight;
import com.lanterna.network.TogglePowerPacket;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
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
        while (KeyBindings.TOGGLE_POWER.consumeClick()) {
            if (ClientRingData.hasRing) {
                ModNetwork.sendToServer(new TogglePowerPacket());
            }
        }
        ClientCutscene.tick();

        // Pose de voo e partículas de todos os jogadores visíveis
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && !mc.isPaused()) {
            for (Player player : mc.level.players()) {
                ClientFlight.tickPose(player);
            }
        }
    }

    // ===================== Voo do anel =====================

    /** Física do voo do jogador local (antes do Minecraft mover o jogador). */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.side.isClient()
                && event.player == Minecraft.getInstance().player) {
            ClientFlight.tickLocalPlayer((LocalPlayer) event.player);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientRingData.reset();
        ClientCutscene.reset();
        ClientVisibleRings.clear();
        ClientFlight.clear();
    }

    private ClientForgeEvents() {}
}
