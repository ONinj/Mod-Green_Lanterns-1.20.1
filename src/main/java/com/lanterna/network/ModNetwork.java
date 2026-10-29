package com.lanterna.network;

import com.lanterna.LanternaMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "3";
    public static SimpleChannel CHANNEL;

    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                LanternaMod.id("main"),
                () -> VERSION,
                VERSION::equals,
                VERSION::equals);

        int id = 0;

        CHANNEL.messageBuilder(SyncRingDataPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncRingDataPacket::encode)
                .decoder(SyncRingDataPacket::decode)
                .consumerMainThread(SyncRingDataPacket::handle)
                .add();

        CHANNEL.messageBuilder(SelectionCutscenePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SelectionCutscenePacket::encode)
                .decoder(SelectionCutscenePacket::decode)
                .consumerMainThread(SelectionCutscenePacket::handle)
                .add();

        CHANNEL.messageBuilder(ToggleFlightPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleFlightPacket::encode)
                .decoder(ToggleFlightPacket::decode)
                .consumerMainThread(ToggleFlightPacket::handle)
                .add();

        CHANNEL.messageBuilder(PocketRechargePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PocketRechargePacket::encode)
                .decoder(PocketRechargePacket::decode)
                .consumerMainThread(PocketRechargePacket::handle)
                .add();

        CHANNEL.messageBuilder(TogglePowerPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TogglePowerPacket::encode)
                .decoder(TogglePowerPacket::decode)
                .consumerMainThread(TogglePowerPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenLanternMenuPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenLanternMenuPacket::encode)
                .decoder(OpenLanternMenuPacket::decode)
                .consumerMainThread(OpenLanternMenuPacket::handle)
                .add();

        CHANNEL.messageBuilder(RechargeAnimPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RechargeAnimPacket::encode)
                .decoder(RechargeAnimPacket::decode)
                .consumerMainThread(RechargeAnimPacket::handle)
                .add();

        CHANNEL.messageBuilder(SyncVisibleRingPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncVisibleRingPacket::encode)
                .decoder(SyncVisibleRingPacket::decode)
                .consumerMainThread(SyncVisibleRingPacket::handle)
                .add();

        CHANNEL.messageBuilder(FlightModePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FlightModePacket::encode)
                .decoder(FlightModePacket::decode)
                .consumerMainThread(FlightModePacket::handle)
                .add();

        CHANNEL.messageBuilder(SpaceSeedPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SpaceSeedPacket::encode)
                .decoder(SpaceSeedPacket::decode)
                .consumerMainThread(SpaceSeedPacket::handle)
                .add();

        // ---- Lore: missões, conversas, construtos ----
        CHANNEL.messageBuilder(com.lanterna.network.lore.SyncLorePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.network.lore.SyncLorePacket::encode)
                .decoder(com.lanterna.network.lore.SyncLorePacket::decode)
                .consumerNetworkThread(com.lanterna.network.lore.SyncLorePacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.network.lore.MissionToastPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.network.lore.MissionToastPacket::encode)
                .decoder(com.lanterna.network.lore.MissionToastPacket::decode)
                .consumerNetworkThread(com.lanterna.network.lore.MissionToastPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.network.lore.OpenDialogPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.network.lore.OpenDialogPacket::encode)
                .decoder(com.lanterna.network.lore.OpenDialogPacket::decode)
                .consumerNetworkThread(com.lanterna.network.lore.OpenDialogPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.network.lore.StartAutopilotPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.network.lore.StartAutopilotPacket::encode)
                .decoder(com.lanterna.network.lore.StartAutopilotPacket::decode)
                .consumerNetworkThread(com.lanterna.network.lore.StartAutopilotPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.network.lore.DialogActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.lanterna.network.lore.DialogActionPacket::encode)
                .decoder(com.lanterna.network.lore.DialogActionPacket::decode)
                .consumerNetworkThread(com.lanterna.network.lore.DialogActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.network.lore.MissionActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.lanterna.network.lore.MissionActionPacket::encode)
                .decoder(com.lanterna.network.lore.MissionActionPacket::decode)
                .consumerNetworkThread(com.lanterna.network.lore.MissionActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.network.lore.ConstructCastPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.lanterna.network.lore.ConstructCastPacket::encode)
                .decoder(com.lanterna.network.lore.ConstructCastPacket::decode)
                .consumerNetworkThread(com.lanterna.network.lore.ConstructCastPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.network.lore.ConstructSelectPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.lanterna.network.lore.ConstructSelectPacket::encode)
                .decoder(com.lanterna.network.lore.ConstructSelectPacket::decode)
                .consumerNetworkThread(com.lanterna.network.lore.ConstructSelectPacket::handle)
                .add();
    }

    public static void sendTo(ServerPlayer player, Object message) {
        if (CHANNEL != null) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
    }

    public static void sendToServer(Object message) {
        if (CHANNEL != null) {
            CHANNEL.sendToServer(message);
        }
    }

    private ModNetwork() {}
}
