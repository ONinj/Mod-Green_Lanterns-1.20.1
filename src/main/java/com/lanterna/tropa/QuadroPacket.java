package com.lanterna.tropa;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Servidor -> cliente: abre o quadro de contratos (ofertas do dia e o estado de cada uma). */
public record QuadroPacket(String planet, List<String> ids, List<Integer> estado, int ativos) {
    public static void encode(QuadroPacket m, FriendlyByteBuf b) {
        b.writeUtf(m.planet);
        b.writeVarInt(m.ids.size());
        for (int i = 0; i < m.ids.size(); i++) {
            b.writeUtf(m.ids.get(i));
            b.writeVarInt(m.estado.get(i));
        }
        b.writeVarInt(m.ativos);
    }

    public static QuadroPacket decode(FriendlyByteBuf b) {
        String planet = b.readUtf();
        int n = b.readVarInt();
        List<String> ids = new ArrayList<>();
        List<Integer> est = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add(b.readUtf());
            est.add(b.readVarInt());
        }
        return new QuadroPacket(planet, ids, est, b.readVarInt());
    }

    public static void handle(QuadroPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lanterna.client.tropa.QuadroScreen.abrir(m)));
        ctx.get().setPacketHandled(true);
    }
}
