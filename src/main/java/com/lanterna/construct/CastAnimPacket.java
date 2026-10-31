package com.lanterna.construct;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> clientes por perto: alguém fez um construto (para a animação do braço e o clarão do anel). */
public record CastAnimPacket(int entity, String type, int color) {
    public static void encode(CastAnimPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.entity);
        b.writeUtf(m.type, 32);
        b.writeInt(m.color);
    }

    public static CastAnimPacket decode(FriendlyByteBuf b) {
        return new CastAnimPacket(b.readVarInt(), b.readUtf(32), b.readInt());
    }

    public static void handle(CastAnimPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lanterna.client.construct.CastAnim.receber(m)));
        ctx.get().setPacketHandled(true);
    }
}
