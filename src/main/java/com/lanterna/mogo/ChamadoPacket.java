package com.lanterna.mogo;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> clientes por perto: começa a cena do Chamado do Mogo. */
public record ChamadoPacket(int dono, int alvo, double x, double y, double z, double mx, double my, double mz, long inicio) {
    public static void encode(ChamadoPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.dono);
        b.writeVarInt(m.alvo);
        b.writeDouble(m.x); b.writeDouble(m.y); b.writeDouble(m.z);
        b.writeDouble(m.mx); b.writeDouble(m.my); b.writeDouble(m.mz);
        b.writeLong(m.inicio);
    }

    public static ChamadoPacket decode(FriendlyByteBuf b) {
        return new ChamadoPacket(b.readVarInt(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readLong());
    }

    public static void handle(ChamadoPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.lanterna.client.mogo.ClienteChamado.comecar(m));
        ctx.get().setPacketHandled(true);
    }
}
