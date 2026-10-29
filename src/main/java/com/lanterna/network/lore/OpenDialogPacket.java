package com.lanterna.network.lore;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Servidor -> cliente: abre a conversa com um NPC.
 *
 * @param entityId  o NPC
 * @param race      raça (veltari, guardiao...)
 * @param role      papel ("lider", "instrutor", "guardiao" ou "" para um morador comum)
 * @param planet    planeta onde ele está
 * @param lines     chaves de texto a mostrar (saudação, fala da missão...)
 * @param options   botões ("trade", "ask_way", "accept:ryut_1", "accept:patrulha", "bye")
 * @param symbols   fala em símbolos (o jogador ainda não entende a língua)
 */
public record OpenDialogPacket(int entityId, String race, String role, String planet, List<String> lines,
                               List<String> options, boolean symbols) {

    public static void encode(OpenDialogPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeUtf(msg.race);
        buf.writeUtf(msg.role);
        buf.writeUtf(msg.planet);
        buf.writeCollection(msg.lines, FriendlyByteBuf::writeUtf);
        buf.writeCollection(msg.options, FriendlyByteBuf::writeUtf);
        buf.writeBoolean(msg.symbols);
    }

    public static OpenDialogPacket decode(FriendlyByteBuf buf) {
        return new OpenDialogPacket(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readList(FriendlyByteBuf::readUtf), buf.readList(FriendlyByteBuf::readUtf), buf.readBoolean());
    }

    public static void handle(OpenDialogPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lanterna.client.mission.DialogScreen.open(msg)));
        ctx.get().setPacketHandled(true);
    }
}
