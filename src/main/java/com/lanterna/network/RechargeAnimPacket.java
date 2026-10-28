package com.lanterna.network;

import com.lanterna.client.recharge.ClientRecharge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> clientes: um jogador começou a recarregar o anel pela bateria do slot. */
public record RechargeAnimPacket(int entityId, String corps, int color) {

    public static void encode(RechargeAnimPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeUtf(msg.corps);
        buf.writeInt(msg.color);
    }

    public static RechargeAnimPacket decode(FriendlyByteBuf buf) {
        return new RechargeAnimPacket(buf.readVarInt(), buf.readUtf(), buf.readInt());
    }

    public static void handle(RechargeAnimPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRecharge.start(msg)));
        ctx.get().setPacketHandled(true);
    }
}
