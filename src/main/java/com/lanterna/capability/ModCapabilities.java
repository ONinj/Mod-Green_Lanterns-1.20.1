package com.lanterna.capability;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

import java.util.Optional;

public final class ModCapabilities {
    public static final Capability<RingData> RING_DATA = CapabilityManager.get(new CapabilityToken<>() {});

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(RingData.class);
    }

    public static Optional<RingData> get(Player player) {
        return player.getCapability(RING_DATA).resolve();
    }

    private ModCapabilities() {}
}
