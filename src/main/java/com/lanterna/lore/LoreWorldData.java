package com.lanterna.lore;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Estado da lore no mundo (salvo junto do mundo normal):
 * bosses já derrotados (os Ecos ficam mais comuns a cada um) e bases já construídas nos planetas.
 */
public class LoreWorldData extends SavedData {
    private static final String NAME = "lanterna_lore";
    private final Set<String> bosses = new LinkedHashSet<>();
    private final Set<String> sites = new LinkedHashSet<>();

    public static LoreWorldData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(LoreWorldData::load, LoreWorldData::new, NAME);
    }

    public int bossesDefeated() {
        return bosses.size();
    }

    public Set<String> bosses() {
        return bosses;
    }

    public void bossDefeated(String boss) {
        if (bosses.add(boss)) setDirty();
    }

    public boolean siteBuilt(String id) {
        return sites.contains(id);
    }

    public void markSite(String id) {
        if (sites.add(id)) setDirty();
    }

    public void resetSite(String id) {
        if (sites.remove(id)) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag b = new ListTag();
        bosses.forEach(s -> b.add(StringTag.valueOf(s)));
        tag.put("Bosses", b);
        ListTag s = new ListTag();
        sites.forEach(x -> s.add(StringTag.valueOf(x)));
        tag.put("Sites", s);
        return tag;
    }

    private static LoreWorldData load(CompoundTag tag) {
        LoreWorldData d = new LoreWorldData();
        tag.getList("Bosses", Tag.TAG_STRING).forEach(t -> d.bosses.add(t.getAsString()));
        tag.getList("Sites", Tag.TAG_STRING).forEach(t -> d.sites.add(t.getAsString()));
        return d;
    }
}
