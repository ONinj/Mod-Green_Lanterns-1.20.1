package com.lanterna.client;

import com.lanterna.network.SyncRingDataPacket;
import net.minecraft.resources.ResourceLocation;

/** Cópia no cliente do estado do anel, recebida do servidor. Usada pelo HUD. */
public final class ClientRingData {
    public static boolean hasRing;
    public static ResourceLocation corps = new ResourceLocation("lanterna", "green");
    public static int color = 0x2BFF4A;
    public static int charge;
    public static int maxCharge = 1000;
    public static boolean flight;

    public static void update(SyncRingDataPacket msg) {
        hasRing = msg.hasRing();
        ResourceLocation id = ResourceLocation.tryParse(msg.corps());
        if (id != null) corps = id;
        color = msg.color();
        charge = msg.charge();
        maxCharge = Math.max(1, msg.maxCharge());
        flight = msg.flight();
    }

    public static void reset() {
        hasRing = false;
        charge = 0;
        flight = false;
    }

    private ClientRingData() {}
}
