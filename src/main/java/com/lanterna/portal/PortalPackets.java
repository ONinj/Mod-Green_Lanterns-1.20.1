package com.lanterna.portal;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Pacotes da Porta Estelar. */
public final class PortalPackets {

    /** Cliente -> servidor: "" = pedir a lista (abrir a tela); id = abrir a porta para esse planeta. */
    public record Pedido(String destino) {
        public static void encode(Pedido m, FriendlyByteBuf b) { b.writeUtf(m.destino, 64); }
        public static Pedido decode(FriendlyByteBuf b) { return new Pedido(b.readUtf(64)); }
        public static void handle(Pedido m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p == null) return;
                if (m.destino.isEmpty()) PortalEstelar.abrirLista(p);
                else PortalEstelar.pedir(p, m.destino);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Servidor -> cliente: a tela de destinos. */
    public record Lista(List<String> destinos, long esperaMs, int custo, int carga, int cor) {
        public static void encode(Lista m, FriendlyByteBuf b) {
            b.writeVarInt(m.destinos.size());
            for (String s : m.destinos) b.writeUtf(s, 64);
            b.writeVarLong(m.esperaMs);
            b.writeVarInt(m.custo);
            b.writeVarInt(m.carga);
            b.writeInt(m.cor);
        }
        public static Lista decode(FriendlyByteBuf b) {
            int n = b.readVarInt();
            List<String> l = new ArrayList<>();
            for (int i = 0; i < n; i++) l.add(b.readUtf(64));
            return new Lista(l, b.readVarLong(), b.readVarInt(), b.readVarInt(), b.readInt());
        }
        public static void handle(Lista m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.lanterna.client.portal.PortalScreen.abrir(m)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Servidor -> clientes: fases da viagem (0 = sai, 1 = chega, 2 = acabou). */
    public record Anim(int entity, int fase, double x, double y, double z, float yaw, int cor, String destino) {
        public static void encode(Anim m, FriendlyByteBuf b) {
            b.writeVarInt(m.entity);
            b.writeVarInt(m.fase);
            b.writeDouble(m.x);
            b.writeDouble(m.y);
            b.writeDouble(m.z);
            b.writeFloat(m.yaw);
            b.writeInt(m.cor);
            b.writeUtf(m.destino, 64);
        }
        public static Anim decode(FriendlyByteBuf b) {
            return new Anim(b.readVarInt(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readInt(), b.readUtf(64));
        }
        public static void handle(Anim m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.lanterna.client.portal.PortalCliente.receber(m)));
            ctx.get().setPacketHandled(true);
        }
    }

    private PortalPackets() {}
}
