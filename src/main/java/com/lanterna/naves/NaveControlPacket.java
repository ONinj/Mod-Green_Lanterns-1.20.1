package com.lanterna.naves;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: o piloto mudou o turbo, atirou ou mexeu na porta/hangar. */
public record NaveControlPacket(boolean boost, int action) {
    public static final int NONE = 0, FIRE = 1, DOOR = 2, HANGAR = 3;

    public static void encode(NaveControlPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.boost);
        buf.writeVarInt(msg.action);
    }

    public static NaveControlPacket decode(FriendlyByteBuf buf) {
        return new NaveControlPacket(buf.readBoolean(), buf.readVarInt());
    }

    public static void handle(NaveControlPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null || !(p.getVehicle() instanceof NaveEntity n)) return;
            boolean pilot = n.getControllingPassenger() == p;
            if (pilot) n.setTurbo(msg.boost);
            switch (msg.action) {
                case FIRE -> { if (pilot || n.getPassengers().indexOf(p) == 1) n.atirar(p.getLookAngle(), p); }
                case DOOR -> { if (pilot) n.togglePorta(); }
                case HANGAR -> { if (pilot) n.setHangar(!n.getPersistentData().getBoolean("hangar"));
                                 n.getPersistentData().putBoolean("hangar", !n.getPersistentData().getBoolean("hangar")); }
                default -> {}
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
