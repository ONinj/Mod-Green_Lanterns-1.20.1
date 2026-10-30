package com.lanterna.client.naves;

import com.lanterna.client.KeyBindings;
import com.lanterna.naves.NaveControlPacket;
import com.lanterna.naves.NaveEntity;
import com.lanterna.naves.NaveInterior;
import com.lanterna.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Controles da nave no cliente: WASD + espaço/C + Ctrl (turbo), X atira, B porta, H hangar. */
public final class ClientNaves {
    private static boolean lastBoost;

    public static void init() {
        NaveEntity.CLIENT_INPUT = n -> {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            if (p == null || p.getVehicle() != n || mc.screen != null) return new NaveEntity.Input(0, 0, false, false, false);
            float fwd = p.input.forwardImpulse;
            float strafe = p.input.leftImpulse;
            return new NaveEntity.Input(fwd, strafe, mc.options.keyJump.isDown(), KeyBindings.NAVE_DESCER.isDown(), mc.options.keySprint.isDown());
        };
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !(p.getVehicle() instanceof NaveEntity n) || n.getControllingPassenger() != p) {
            lastBoost = false;
            while (KeyBindings.NAVE_PORTA.consumeClick()) {}
            return;
        }
        boolean boost = mc.options.keySprint.isDown();
        if (boost != lastBoost) {
            lastBoost = boost;
            ModNetwork.sendToServer(new NaveControlPacket(boost, NaveControlPacket.NONE));
        }
        while (KeyBindings.NAVE_PORTA.consumeClick()) ModNetwork.sendToServer(new NaveControlPacket(boost, NaveControlPacket.DOOR));
        while (KeyBindings.NAVE_HANGAR.consumeClick()) ModNetwork.sendToServer(new NaveControlPacket(boost, NaveControlPacket.HANGAR));
    }

    public static void fire() {
        ModNetwork.sendToServer(new NaveControlPacket(lastBoost, NaveControlPacket.FIRE));
    }

    /** Cor das faixas dos blocos: dentro das naves vem da posição da sala; fora, verde. */
    public static int corBloco(net.minecraft.core.BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (pos != null && mc.level != null && mc.level.dimension().equals(NaveInterior.DIM)) return NaveInterior.corDaRegiao(pos.getZ());
        return 0x46E66E;
    }

    private ClientNaves() {}

    /** Holomapa da nave: abre o mapa estelar. */
    public static void abrirMapa() {
        net.minecraft.client.Minecraft.getInstance().setScreen(new com.lanterna.client.screen.StarMapScreen());
    }
}
