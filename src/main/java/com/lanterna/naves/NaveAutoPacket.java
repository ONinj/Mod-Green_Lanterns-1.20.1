package com.lanterna.naves;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: pelo holomapa da sala, mandar a nave voar sozinha até um destino. */
public record NaveAutoPacket(String destino) {
    public static void encode(NaveAutoPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.destino, 64);
    }

    public static NaveAutoPacket decode(FriendlyByteBuf buf) {
        return new NaveAutoPacket(buf.readUtf(64));
    }

    public static void handle(NaveAutoPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) NaveInterior.iniciarViagem(p, msg.destino);
        });
        ctx.get().setPacketHandled(true);
    }
}
