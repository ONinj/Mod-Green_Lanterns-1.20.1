package com.lanterna.guilda;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: botão do painel do minerador/general, ou o símbolo novo da guilda. */
public record GuildaAcaoPacket(String planeta, String acao, byte[] simbolo) {
    public GuildaAcaoPacket(String planeta, String acao) {
        this(planeta, acao, new byte[0]);
    }

    public static void encode(GuildaAcaoPacket m, FriendlyByteBuf b) {
        b.writeUtf(m.planeta);
        b.writeUtf(m.acao);
        b.writeByteArray(m.simbolo);
    }

    public static GuildaAcaoPacket decode(FriendlyByteBuf b) {
        return new GuildaAcaoPacket(b.readUtf(), b.readUtf(), b.readByteArray(256));
    }

    public static void handle(GuildaAcaoPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            if (m.acao.equals("simbolo")) GuildaComando.salvarSimbolo(p, m.simbolo);
            else GuildaNpcs.acao(p, m.planeta, m.acao);
        });
        ctx.get().setPacketHandled(true);
    }
}
