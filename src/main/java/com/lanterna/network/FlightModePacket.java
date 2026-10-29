package com.lanterna.network;

import com.lanterna.power.RingPowers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: entrou/saiu da hiperpropulsão ou do piloto automático. */
public record FlightModePacket(boolean hyper, boolean autopilot) {

    public static void encode(FlightModePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.hyper);
        buf.writeBoolean(msg.autopilot);
    }

    public static FlightModePacket decode(FriendlyByteBuf buf) {
        return new FlightModePacket(buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(FlightModePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) RingPowers.setFlightMode(player, msg.hyper, msg.autopilot);
        ctx.get().setPacketHandled(true);
    }
}
