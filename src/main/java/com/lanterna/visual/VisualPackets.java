package com.lanterna.visual;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Pacotes do perfil: visual do uniforme (traje e altura da máscara) e a ficha de estatísticas. */
public final class VisualPackets {

    /** Cliente -> servidor: "quero este visual". */
    public record Pedido(String traje, int mascara) {
        public static void encode(Pedido m, FriendlyByteBuf b) { b.writeUtf(m.traje, 16); b.writeVarInt(m.mascara); }
        public static Pedido decode(FriendlyByteBuf b) { return new Pedido(b.readUtf(16), b.readVarInt()); }
        public static void handle(Pedido m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var p = ctx.get().getSender();
                if (p != null) Visual.definir(p, m.traje, m.mascara);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Servidor -> clientes: o visual de um jogador. */
    public record Sync(int entity, String traje, int mascara) {
        public static void encode(Sync m, FriendlyByteBuf b) { b.writeVarInt(m.entity); b.writeUtf(m.traje, 16); b.writeVarInt(m.mascara); }
        public static Sync decode(FriendlyByteBuf b) { return new Sync(b.readVarInt(), b.readUtf(16), b.readVarInt()); }
        public static void handle(Sync m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.lanterna.client.visual.ClientVisual.set(m.entity, m.traje, m.mascara)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Cliente -> servidor: manda a minha ficha. */
    public record FichaPedido() {
        public static void encode(FichaPedido m, FriendlyByteBuf b) {}
        public static FichaPedido decode(FriendlyByteBuf b) { return new FichaPedido(); }
        public static void handle(FichaPedido m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var p = ctx.get().getSender();
                if (p != null) com.lanterna.network.ModNetwork.sendTo(p, Visual.ficha(p));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Uma linha da ficha: seção, rótulo, valor, barra (0..1, ou -1 sem barra) e cor. */
    public record Linha(String secao, Component rotulo, Component valor, float barra, int cor) {}

    /** Servidor -> cliente: a ficha de estatísticas. */
    public record Ficha(List<Linha> linhas) {
        public static void encode(Ficha m, FriendlyByteBuf b) {
            b.writeVarInt(m.linhas.size());
            for (Linha l : m.linhas) {
                b.writeUtf(l.secao());
                b.writeComponent(l.rotulo());
                b.writeComponent(l.valor());
                b.writeFloat(l.barra());
                b.writeInt(l.cor());
            }
        }
        public static Ficha decode(FriendlyByteBuf b) {
            int n = b.readVarInt();
            List<Linha> l = new ArrayList<>();
            for (int i = 0; i < n; i++) l.add(new Linha(b.readUtf(), b.readComponent(), b.readComponent(), b.readFloat(), b.readInt()));
            return new Ficha(l);
        }
        public static void handle(Ficha m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.lanterna.client.visual.PerfilScreen.receberFicha(m)));
            ctx.get().setPacketHandled(true);
        }
    }

    private VisualPackets() {}
}
