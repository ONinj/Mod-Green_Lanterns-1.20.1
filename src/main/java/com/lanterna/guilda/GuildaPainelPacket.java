package com.lanterna.guilda;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Servidor -> cliente: painel do minerador / general (linhas de texto e botões). */
public record GuildaPainelPacket(String planeta, int cor, String tituloJson, List<String> linhasJson, List<String> acoes, List<String> rotulosJson, List<Boolean> ativos) {
    public static void encode(GuildaPainelPacket m, FriendlyByteBuf b) {
        b.writeUtf(m.planeta);
        b.writeInt(m.cor);
        b.writeUtf(m.tituloJson, 4096);
        b.writeVarInt(m.linhasJson.size());
        for (String s : m.linhasJson) b.writeUtf(s, 4096);
        b.writeVarInt(m.acoes.size());
        for (int i = 0; i < m.acoes.size(); i++) {
            b.writeUtf(m.acoes.get(i));
            b.writeUtf(m.rotulosJson.get(i), 4096);
            b.writeBoolean(m.ativos.get(i));
        }
    }

    public static GuildaPainelPacket decode(FriendlyByteBuf b) {
        String planeta = b.readUtf();
        int cor = b.readInt();
        String titulo = b.readUtf(4096);
        int n = b.readVarInt();
        List<String> linhas = new ArrayList<>();
        for (int i = 0; i < n; i++) linhas.add(b.readUtf(4096));
        int k = b.readVarInt();
        List<String> acoes = new ArrayList<>(), rot = new ArrayList<>();
        List<Boolean> at = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            acoes.add(b.readUtf());
            rot.add(b.readUtf(4096));
            at.add(b.readBoolean());
        }
        return new GuildaPainelPacket(planeta, cor, titulo, linhas, acoes, rot, at);
    }

    public static void handle(GuildaPainelPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lanterna.client.guilda.GuildaScreen.abrir(m)));
        ctx.get().setPacketHandled(true);
    }
}
