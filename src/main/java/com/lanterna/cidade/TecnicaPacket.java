package com.lanterna.cidade;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: usar a técnica de uma tecla (acao 0) ou equipar uma técnica numa tecla (acao 1). */
public record TecnicaPacket(int acao, int slot, String tecnica) {
    public static void encode(TecnicaPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.acao);
        b.writeVarInt(m.slot);
        b.writeUtf(m.tecnica, 32);
    }

    public static TecnicaPacket decode(FriendlyByteBuf b) {
        return new TecnicaPacket(b.readVarInt(), b.readVarInt(), b.readUtf(32));
    }

    public static void handle(TecnicaPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            if (m.acao == 0) Tecnicas.usar(p, m.slot);
            else Tecnicas.equipar(p, m.tecnica, m.slot);
        });
        ctx.get().setPacketHandled(true);
    }
}
