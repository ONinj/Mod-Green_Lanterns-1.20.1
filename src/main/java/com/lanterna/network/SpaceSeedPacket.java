package com.lanterna.network;

import com.lanterna.space.ProceduralPlanets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: semente dos planetas gerados (para o cliente ver os mesmos planetas). */
public record SpaceSeedPacket(long seed) {

    public static void encode(SpaceSeedPacket msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.seed);
    }

    public static SpaceSeedPacket decode(FriendlyByteBuf buf) {
        return new SpaceSeedPacket(buf.readLong());
    }

    public static void handle(SpaceSeedPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ProceduralPlanets.setSeed(msg.seed);
        ctx.get().setPacketHandled(true);
    }
}
