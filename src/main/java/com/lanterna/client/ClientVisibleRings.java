package com.lanterna.client;

import com.lanterna.item.PowerRingItem;
import com.lanterna.network.SyncVisibleRingPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** No cliente: o anel de cada jogador visível (para desenhar na mão, a aura e a pose de voo). */
public final class ClientVisibleRings {

    public record Visible(ItemStack ring, int color, boolean active, boolean flying, boolean hyper, boolean autopilot,
                          boolean staff) {}

    private static final Visible NONE = new Visible(ItemStack.EMPTY, 0, false, false, false, false, false);
    private static final Map<Integer, Visible> RINGS = new HashMap<>();

    public static void update(SyncVisibleRingPacket msg) {
        ResourceLocation corps = ResourceLocation.tryParse(msg.corps());
        if ((!msg.hasRing() || corps == null) && !msg.staff()) {
            RINGS.remove(msg.entityId());
        } else {
            ItemStack ring = msg.hasRing() && corps != null ? PowerRingItem.create(corps, msg.color(), -1) : ItemStack.EMPTY;
            RINGS.put(msg.entityId(), new Visible(ring, msg.color(), msg.hasRing() && msg.active(), msg.hasRing() && msg.flying(),
                    msg.hyper(), msg.autopilot(), msg.staff()));
        }
    }

    public static Visible of(int entityId) {
        return RINGS.getOrDefault(entityId, NONE);
    }

    public static ItemStack get(int entityId) {
        return of(entityId).ring();
    }

    public static void clear() {
        RINGS.clear();
    }

    private ClientVisibleRings() {}
}
