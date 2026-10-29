package com.lanterna.network.lore;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: começar (ou soltar) um construto. */
public record ConstructCastPacket(String construct, boolean start) {

    public static void encode(ConstructCastPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.construct);
        buf.writeBoolean(msg.start);
    }

    public static ConstructCastPacket decode(FriendlyByteBuf buf) {
        return new ConstructCastPacket(buf.readUtf(), buf.readBoolean());
    }

    public static void handle(ConstructCastPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (msg.start) com.lanterna.construct.Constructs.cast(player, msg.construct);
            else com.lanterna.construct.Constructs.release(player, msg.construct);
        });
        ctx.get().setPacketHandled(true);
    }
}
