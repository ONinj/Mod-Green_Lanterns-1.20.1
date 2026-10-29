package com.lanterna.network.lore;

import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: acompanhar ou abandonar uma missão (diário). */
public record MissionActionPacket(String action, String mission) {

    public static void encode(MissionActionPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.action);
        buf.writeUtf(msg.mission);
    }

    public static MissionActionPacket decode(FriendlyByteBuf buf) {
        return new MissionActionPacket(buf.readUtf(), buf.readUtf());
    }

    public static void handle(MissionActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            LoreData data = MissionManager.data(player);
            if (data == null) return;
            switch (msg.action) {
                case "track" -> {
                    data.setTracked(msg.mission);
                    MissionManager.sync(player, data);
                }
                case "abandon" -> MissionManager.abandon(player, msg.mission);
                default -> {}
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
