package com.lanterna.network;

import com.lanterna.power.RingPowers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: jogador apertou a tecla de voo. */
public record ToggleFlightPacket() {

    public static void encode(ToggleFlightPacket msg, FriendlyByteBuf buf) {
    }

    public static ToggleFlightPacket decode(FriendlyByteBuf buf) {
        return new ToggleFlightPacket();
    }

    public static void handle(ToggleFlightPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            RingPowers.toggleFlight(player);
        }
        ctx.get().setPacketHandled(true);
    }
}
