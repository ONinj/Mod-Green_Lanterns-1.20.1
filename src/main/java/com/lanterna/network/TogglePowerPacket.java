package com.lanterna.network;

import com.lanterna.power.RingPowers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: jogador apertou a tecla de ligar/desligar o anel. */
public record TogglePowerPacket() {

    public static void encode(TogglePowerPacket msg, FriendlyByteBuf buf) {
    }

    public static TogglePowerPacket decode(FriendlyByteBuf buf) {
        return new TogglePowerPacket();
    }

    public static void handle(TogglePowerPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            RingPowers.togglePower(player);
        }
        ctx.get().setPacketHandled(true);
    }
}
