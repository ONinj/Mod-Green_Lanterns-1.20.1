package com.lanterna.tropa;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Servidor -> cliente: abre o quadro de contratos (ofertas do dia e o estado de cada uma). */
public record QuadroPacket(String planet, List<String> ids, List<Integer> estado, int ativos, String tituloJson, List<String> notasJson) {
    /** Estados: 0 = pode aceitar, 1 = ativa, 2 = feita, 3 = trancada (a nota diz o que falta). */
    public QuadroPacket(String planet, List<String> ids, List<Integer> estado, int ativos) {
        this(planet, ids, estado, ativos, "", new ArrayList<>());
    }

    public static void encode(QuadroPacket m, FriendlyByteBuf b) {
        b.writeUtf(m.tituloJson, 4096);
        b.writeVarInt(m.notasJson.size());
        for (String n : m.notasJson) b.writeUtf(n, 4096);
        b.writeUtf(m.planet);
        b.writeVarInt(m.ids.size());
        for (int i = 0; i < m.ids.size(); i++) {
            b.writeUtf(m.ids.get(i));
            b.writeVarInt(m.estado.get(i));
        }
        b.writeVarInt(m.ativos);
    }

    public static QuadroPacket decode(FriendlyByteBuf b) {
        String titulo = b.readUtf(4096);
        int nn = b.readVarInt();
        List<String> notas = new ArrayList<>();
        for (int i = 0; i < nn; i++) notas.add(b.readUtf(4096));
        String planet = b.readUtf();
        int n = b.readVarInt();
        List<String> ids = new ArrayList<>();
        List<Integer> est = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add(b.readUtf());
            est.add(b.readVarInt());
        }
        return new QuadroPacket(planet, ids, est, b.readVarInt(), titulo, notas);
    }

    public static void handle(QuadroPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lanterna.client.tropa.QuadroScreen.abrir(m)));
        ctx.get().setPacketHandled(true);
    }
}
