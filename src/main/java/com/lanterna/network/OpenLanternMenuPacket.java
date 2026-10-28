package com.lanterna.network;

import com.lanterna.menu.LanternMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

/** Cliente -> servidor: abrir a aba "Lanterna". */
public record OpenLanternMenuPacket() {

    public static void encode(OpenLanternMenuPacket msg, FriendlyByteBuf buf) {
    }

    public static OpenLanternMenuPacket decode(FriendlyByteBuf buf) {
        return new OpenLanternMenuPacket();
    }

    public static void handle(OpenLanternMenuPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            NetworkHooks.openScreen(player, new SimpleMenuProvider(
                    (id, inventory, p) -> new LanternMenu(id, inventory),
                    Component.translatable("container.lanterna.lantern")));
        }
        ctx.get().setPacketHandled(true);
    }
}
