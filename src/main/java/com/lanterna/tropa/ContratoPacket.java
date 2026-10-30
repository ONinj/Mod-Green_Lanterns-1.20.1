package com.lanterna.tropa;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: aceitar ou desistir de um contrato. */
public record ContratoPacket(String id, boolean aceitar) {
    public static void encode(ContratoPacket m, FriendlyByteBuf b) {
        b.writeUtf(m.id);
        b.writeBoolean(m.aceitar);
    }

    public static ContratoPacket decode(FriendlyByteBuf b) {
        return new ContratoPacket(b.readUtf(), b.readBoolean());
    }

    public static void handle(ContratoPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            if (com.lanterna.cidade.CidadeMissoes.eDaCidade(m.id)) {
                if (m.aceitar) com.lanterna.cidade.CidadeMissoes.aceitar(p, m.id);
                else com.lanterna.cidade.CidadeMissoes.desistir(p, m.id);
            } else if (m.aceitar) Contratos.aceitar(p, m.id);
            else Contratos.desistir(p, m.id);
        });
        ctx.get().setPacketHandled(true);
    }
}
