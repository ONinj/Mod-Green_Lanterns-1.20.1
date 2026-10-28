package com.lanterna.network;

import com.lanterna.capability.RingData;
import com.lanterna.client.ClientRingData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: estado do anel, usado pelo HUD. */
public record SyncRingDataPacket(boolean hasRing, String corps, int color, int charge, int maxCharge, boolean flight,
                                 int pocketStatus) {

    public static SyncRingDataPacket from(RingData data) {
        return new SyncRingDataPacket(data.hasRing(), data.corps().toString(), data.color(),
                data.charge(), data.maxCharge(), data.flightEnabled(), data.pocketStatus());
    }

    public static void encode(SyncRingDataPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.hasRing);
        buf.writeUtf(msg.corps);
        buf.writeInt(msg.color);
        buf.writeVarInt(msg.charge);
        buf.writeVarInt(msg.maxCharge);
        buf.writeBoolean(msg.flight);
        buf.writeVarInt(msg.pocketStatus + 1);
    }

    public static SyncRingDataPacket decode(FriendlyByteBuf buf) {
        return new SyncRingDataPacket(buf.readBoolean(), buf.readUtf(), buf.readInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt() - 1);
    }

    public static void handle(SyncRingDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRingData.update(msg));
        ctx.get().setPacketHandled(true);
    }
}
