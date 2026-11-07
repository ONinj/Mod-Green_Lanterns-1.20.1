package com.lanterna.network;

import com.lanterna.client.ClientCutscene;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor -> cliente: começa a cerimônia de entrega do anel do jogador {@code entity}. Vai para o escolhido (que vê a
 * cena com câmera de cinema) e para quem está por perto (que vê a cerimônia acontecendo no mundo).
 */
public record SelectionCutscenePacket(String corps, int color, int duration, int entity) {

    public static void encode(SelectionCutscenePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.corps);
        buf.writeInt(msg.color);
        buf.writeVarInt(msg.duration);
        buf.writeVarInt(msg.entity);
    }

    public static SelectionCutscenePacket decode(FriendlyByteBuf buf) {
        return new SelectionCutscenePacket(buf.readUtf(), buf.readInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(SelectionCutscenePacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCutscene.start(msg.corps, msg.color, msg.duration, msg.entity));
        ctx.get().setPacketHandled(true);
    }
}
