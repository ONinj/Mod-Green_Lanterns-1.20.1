package com.lanterna.network.lore;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: aviso grande de missão começada/concluída. */
public record MissionToastPacket(Kind kind, String mission) {
    public enum Kind { STARTED, COMPLETED }

    public static void encode(MissionToastPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.kind);
        buf.writeUtf(msg.mission);
    }

    public static MissionToastPacket decode(FriendlyByteBuf buf) {
        return new MissionToastPacket(buf.readEnum(Kind.class), buf.readUtf());
    }

    public static void handle(MissionToastPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lanterna.client.mission.MissionHud.toast(msg.kind == Kind.COMPLETED, msg.mission)));
        ctx.get().setPacketHandled(true);
    }
}
