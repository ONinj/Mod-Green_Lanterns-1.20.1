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
    /** true (padrão): só um Lanterna Laranja por mundo. false: qualquer jogador pode fazer o ritual. Comando /lanterna laranja. */
    private boolean unico = true;

    public static OrangeClaim get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(OrangeClaim::load, OrangeClaim::new, NAME);
    }

    private static OrangeClaim load(CompoundTag tag) {
        OrangeClaim claim = new OrangeClaim();
        if (tag.hasUUID("Owner")) claim.owner = tag.getUUID("Owner");
        claim.unico = !tag.contains("Unico") || tag.getBoolean("Unico");
        return claim;
    }

    /** O anel já tem dono e o mundo está no modo "um só". */
    public boolean isTaken() {
        return unico && owner != null;
    }

    public boolean unico() {
        return unico;
    }

    public void setUnico(boolean v) {
        unico = v;
        setDirty();
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
        tag.putBoolean("Unico", unico);
        return tag;
    }
}
