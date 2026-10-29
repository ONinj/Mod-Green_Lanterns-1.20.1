package com.lanterna.network.lore;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: liga o piloto automático até um planeta (o anel verde leva o novo Lanterna a Oa). */
public record StartAutopilotPacket(String planet, int delayTicks) {

    public static void encode(StartAutopilotPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.planet);
        buf.writeVarInt(msg.delayTicks);
    }

    public static StartAutopilotPacket decode(FriendlyByteBuf buf) {
        return new StartAutopilotPacket(buf.readUtf(), buf.readVarInt());
    }

    public static void handle(StartAutopilotPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lanterna.client.mission.ClientLoreData.scheduleAutopilot(msg.planet, msg.delayTicks)));
        ctx.get().setPacketHandled(true);
    }
}
