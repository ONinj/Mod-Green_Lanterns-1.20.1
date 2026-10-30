package com.lanterna.guilda;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: abre o editor do símbolo da guilda. */
public record AbrirSimboloPacket(String nome, int cor, byte[] simbolo) {
    public static void encode(AbrirSimboloPacket m, FriendlyByteBuf b) {
        b.writeUtf(m.nome, 64);
        b.writeInt(m.cor);
        b.writeByteArray(m.simbolo);
    }

    public static AbrirSimboloPacket decode(FriendlyByteBuf b) {
        return new AbrirSimboloPacket(b.readUtf(64), b.readInt(), b.readByteArray(256));
    }

    public static void handle(AbrirSimboloPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lanterna.client.guilda.SimboloScreen.abrir(m)));
        ctx.get().setPacketHandled(true);
    }
}
