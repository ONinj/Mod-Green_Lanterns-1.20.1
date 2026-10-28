package com.lanterna.network;

import com.lanterna.client.ClientCutscene;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: começa o efeito de tela da entrega do anel. */
public record SelectionCutscenePacket(String corps, int color, int duration) {

    public static void encode(SelectionCutscenePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.corps);
        buf.writeInt(msg.color);
        buf.writeVarInt(msg.duration);
    }

    public static SelectionCutscenePacket decode(FriendlyByteBuf buf) {
        return new SelectionCutscenePacket(buf.readUtf(), buf.readInt(), buf.readVarInt());
    }

    public static void handle(SelectionCutscenePacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCutscene.start(msg.corps, msg.color, msg.duration));
        ctx.get().setPacketHandled(true);
    }
}
