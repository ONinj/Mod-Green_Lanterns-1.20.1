package com.lanterna.network.lore;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: o jogador escolheu uma opção na conversa com um NPC. */
public record DialogActionPacket(int entityId, String action) {

    public static void encode(DialogActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeUtf(msg.action);
    }

    public static DialogActionPacket decode(FriendlyByteBuf buf) {
        return new DialogActionPacket(buf.readVarInt(), buf.readUtf());
    }

    public static void handle(DialogActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) com.lanterna.lore.entity.NpcDialogs.action(player, msg.entityId, msg.action);
        });
        ctx.get().setPacketHandled(true);
    }
}
