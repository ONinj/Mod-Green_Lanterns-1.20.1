package com.lanterna.network.lore;

import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: escolheu um construto na roda (preset -1) ou gravou num preset (0..3). */
public record ConstructSelectPacket(int preset, String construct) {

    public static void encode(ConstructSelectPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.preset);
        buf.writeUtf(msg.construct);
    }

    public static ConstructSelectPacket decode(FriendlyByteBuf buf) {
        return new ConstructSelectPacket(buf.readVarInt(), buf.readUtf());
    }

    public static void handle(ConstructSelectPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || com.lanterna.construct.ConstructType.byId(msg.construct) == null) return;
            LoreData data = MissionManager.data(player);
            if (data == null) return;
            if (msg.preset < 0) data.setSelected(msg.construct);
            else data.setPreset(msg.preset, msg.construct);
            MissionManager.sync(player, data);
        });
        ctx.get().setPacketHandled(true);
    }
}
