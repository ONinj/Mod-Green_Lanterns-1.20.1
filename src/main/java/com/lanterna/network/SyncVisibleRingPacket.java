package com.lanterna.network;

import com.lanterna.capability.RingData;
import com.lanterna.client.ClientVisibleRings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> clientes: qual anel desenhar na mão de um jogador (ou nenhum). */
public record SyncVisibleRingPacket(int entityId, boolean hasRing, String corps, int color, boolean active, boolean flying,
                                    boolean hyper, boolean autopilot) {

    public static SyncVisibleRingPacket of(int entityId, RingData data) {
        return new SyncVisibleRingPacket(entityId, data.hasRing(), data.corps().toString(), data.color(),
                data.hasRing() && data.powerActive(), data.hasRing() && data.flightEnabled(),
                data.hasRing() && data.hyper(), data.hasRing() && data.autopilot());
    }

    public static void encode(SyncVisibleRingPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.hasRing);
        buf.writeUtf(msg.corps);
        buf.writeInt(msg.color);
        buf.writeBoolean(msg.active);
        buf.writeBoolean(msg.flying);
        buf.writeBoolean(msg.hyper);
        buf.writeBoolean(msg.autopilot);
    }

    public static SyncVisibleRingPacket decode(FriendlyByteBuf buf) {
        return new SyncVisibleRingPacket(buf.readVarInt(), buf.readBoolean(), buf.readUtf(), buf.readInt(),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(SyncVisibleRingPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientVisibleRings.update(msg)));
        ctx.get().setPacketHandled(true);
    }
}
