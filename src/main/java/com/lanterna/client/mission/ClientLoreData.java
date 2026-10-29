package com.lanterna.client.mission;

import com.lanterna.client.flight.Autopilot;
import com.lanterna.client.flight.Destinations;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.Mission;
import com.lanterna.mission.Missions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Cópia no cliente do progresso da lore do jogador (missões, presets, afinidade). */
public final class ClientLoreData {
    public static final LoreData DATA = new LoreData();
    private static boolean loaded;

    private static String autopilotTarget;
    private static int autopilotDelay;
    private static int autopilotTries;

    public static void load(CompoundTag tag) {
        DATA.deserializeNBT(tag);
        loaded = true;
    }

    public static boolean loaded() {
        return loaded;
    }

    public static void reset() {
        DATA.deserializeNBT(new CompoundTag());
        loaded = false;
        autopilotTarget = null;
    }

    @Nullable
    public static Mission tracked() {
        String id = DATA.tracked();
        if (id.isEmpty() || !DATA.isActive(id)) {
            for (String k : DATA.active().keySet()) return Missions.get(k);
            return null;
        }
        return Missions.get(id);
    }

    public static boolean geneseRevealed() {
        return DATA.geneseRevealed();
    }

    // ===================== Piloto automático pedido pelo servidor =====================

    public static void scheduleAutopilot(String planet, int delay) {
        autopilotTarget = planet;
        autopilotDelay = delay;
        autopilotTries = 0;
    }

    /** Chamado a cada tick do cliente. */
    public static void tick() {
        if (autopilotTarget == null) return;
        if (--autopilotDelay > 0) return;
        Destinations.Destination d = Destinations.byId(autopilotTarget);
        Component error = d == null ? Component.literal("?") : Autopilot.start(d);
        if (error == null || ++autopilotTries > 10) {
            autopilotTarget = null;
        } else {
            autopilotDelay = 20;
        }
    }

    private ClientLoreData() {}
}
