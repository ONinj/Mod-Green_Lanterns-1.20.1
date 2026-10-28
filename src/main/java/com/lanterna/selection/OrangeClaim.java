package com.lanterna.selection;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/**
 * A avareza não divide: só pode existir UM Lanterna Laranja por mundo.
 * Guardado junto com o mundo (data/lanterna_orange.dat).
 */
public class OrangeClaim extends SavedData {
    private static final String NAME = "lanterna_orange";
    private UUID owner;

    public static OrangeClaim get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(OrangeClaim::load, OrangeClaim::new, NAME);
    }

    private static OrangeClaim load(CompoundTag tag) {
        OrangeClaim claim = new OrangeClaim();
        if (tag.hasUUID("Owner")) claim.owner = tag.getUUID("Owner");
        return claim;
    }

    public boolean isTaken() {
        return owner != null;
    }

    public UUID owner() {
        return owner;
    }

    public void claim(UUID player) {
        owner = player;
        setDirty();
    }

    public void release() {
        owner = null;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (owner != null) tag.putUUID("Owner", owner);
        return tag;
    }
}
