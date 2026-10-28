package com.lanterna.network;

import com.lanterna.capability.RingData;
import com.lanterna.client.ClientVisibleRings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> clientes: qual anel desenhar na mão de um jogador (ou nenhum). */
public record SyncVisibleRingPacket(int entityId, boolean hasRing, String corps, int color) {

    public static SyncVisibleRingPacket of(int entityId, RingData data) {
        return new SyncVisibleRingPacket(entityId, data.hasRing(), data.corps().toString(), data.color());
    }

    public static void encode(SyncVisibleRingPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.hasRing);
        buf.writeUtf(msg.corps);
        buf.writeInt(msg.color);
    }

    public static SyncVisibleRingPacket decode(FriendlyByteBuf buf) {
        return new SyncVisibleRingPacket(buf.readVarInt(), buf.readBoolean(), buf.readUtf(), buf.readInt());
    }

    public static void handle(SyncVisibleRingPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientVisibleRings.update(msg));
        ctx.get().setPacketHandled(true);
    }
}
