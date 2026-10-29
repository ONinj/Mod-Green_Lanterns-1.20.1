package com.lanterna.network.lore;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: missões, respeito, presets e afinidade do jogador. */
public record SyncLorePacket(CompoundTag tag) {

    public static void encode(SyncLorePacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.tag);
    }

    public static SyncLorePacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new SyncLorePacket(tag == null ? new CompoundTag() : tag);
    }

    public static void handle(SyncLorePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lanterna.client.mission.ClientLoreData.load(msg.tag)));
        ctx.get().setPacketHandled(true);
    }
}
