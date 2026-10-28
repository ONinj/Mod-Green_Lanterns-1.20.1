package com.lanterna.network;

import com.lanterna.power.RingPowers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: jogador apertou a tecla de recarga pela bateria do slot. */
public record PocketRechargePacket() {

    public static void encode(PocketRechargePacket msg, FriendlyByteBuf buf) {
    }

    public static PocketRechargePacket decode(FriendlyByteBuf buf) {
        return new PocketRechargePacket();
    }

    public static void handle(PocketRechargePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            RingPowers.pocketRecharge(player);
        }
        ctx.get().setPacketHandled(true);
    }
}
