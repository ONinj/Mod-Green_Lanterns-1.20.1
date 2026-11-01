package com.lanterna.fonte;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Pacotes da Fonte (servidor → cliente). */
public final class FontePackets {

    /** Estado da Fonte do próprio jogador (HUD da pressão, do banho, da sobrecarga e da essência). */
    public record Estado(int pressao, int banho, int sobrecarga, boolean emFonte, boolean bloqueado, int essencia, String nos, String selos) {
        public static void encode(Estado m, FriendlyByteBuf b) {
            b.writeVarInt(m.pressao); b.writeVarInt(m.banho); b.writeVarInt(m.sobrecarga);
            b.writeBoolean(m.emFonte); b.writeBoolean(m.bloqueado); b.writeVarInt(m.essencia);
            b.writeUtf(m.nos, 4096); b.writeUtf(m.selos, 256);
        }

        public static Estado decode(FriendlyByteBuf b) {
            return new Estado(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readUtf(4096), b.readUtf(256));
        }

        public static void handle(Estado m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.lanterna.client.fonte.ClientFonte.estado(m));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Aura da sobrecarga de um jogador (para todos que o veem). */
    public record Aura(int entidade, int cor, boolean ligada) {
        public static void encode(Aura m, FriendlyByteBuf b) { b.writeVarInt(m.entidade); b.writeInt(m.cor); b.writeBoolean(m.ligada); }

        public static Aura decode(FriendlyByteBuf b) { return new Aura(b.readVarInt(), b.readInt(), b.readBoolean()); }

        public static void handle(Aura m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.lanterna.client.fonte.ClientFonte.aura(m));
            ctx.get().setPacketHandled(true);
        }
    }

    private FontePackets() {}
}
