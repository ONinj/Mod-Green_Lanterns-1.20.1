package com.lanterna.client;

import com.lanterna.item.PowerRingItem;
import com.lanterna.network.SyncVisibleRingPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** No cliente: o anel que cada jogador visível está usando, para desenhar na mão. */
public final class ClientVisibleRings {
    private static final Map<Integer, ItemStack> RINGS = new HashMap<>();

    public static void update(SyncVisibleRingPacket msg) {
        ResourceLocation corps = ResourceLocation.tryParse(msg.corps());
        if (!msg.hasRing() || corps == null) {
            RINGS.remove(msg.entityId());
        } else {
            RINGS.put(msg.entityId(), PowerRingItem.create(corps, msg.color(), -1));
        }
    }

    public static ItemStack get(int entityId) {
        return RINGS.getOrDefault(entityId, ItemStack.EMPTY);
    }

    public static void clear() {
        RINGS.clear();
    }

    private ClientVisibleRings() {}
}
