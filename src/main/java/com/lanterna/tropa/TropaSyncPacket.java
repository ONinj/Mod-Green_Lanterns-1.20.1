package com.lanterna.tropa;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: ganhou XP de Lanterna (mostra "+N XP" no HUD). */
public record TropaSyncPacket(int xp, String motivo) {
    public static void encode(TropaSyncPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.xp);
        b.writeUtf(m.motivo, 128);
    }

    public static TropaSyncPacket decode(FriendlyByteBuf b) {
        return new TropaSyncPacket(b.readVarInt(), b.readUtf(128));
    }

    public static void handle(TropaSyncPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lanterna.client.tropa.TropaHud.ganhou(m.xp, m.motivo)));
        ctx.get().setPacketHandled(true);
    }
}
