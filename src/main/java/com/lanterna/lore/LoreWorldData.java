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
    /** Casas do bairro novo de cada megacidade (planeta -> posições). */
    private final java.util.Map<String, java.util.List<net.minecraft.core.BlockPos>> casas = new java.util.HashMap<>();

    public java.util.List<net.minecraft.core.BlockPos> casas(String planeta) {
        return casas.getOrDefault(planeta, java.util.List.of());
    }

    public void guardarCasas(String planeta, java.util.List<net.minecraft.core.BlockPos> l) {
        casas.put(planeta, new java.util.ArrayList<>(l));
        setDirty();
    }

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

    /** Desenho dos planetas (0 = ainda não decidido, 1 = antigo, 2 = cidade única da 0.48). */
    public int layout() {
        return sites.contains("layout:2") ? 2 : sites.contains("layout:1") ? 1 : 0;
    }

    public void setLayout(int v) {
        sites.remove("layout:1");
        sites.remove("layout:2");
        sites.add("layout:" + v);
        setDirty();
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
        CompoundTag cs = new CompoundTag();
        casas.forEach((k, v) -> cs.putLongArray(k, v.stream().mapToLong(net.minecraft.core.BlockPos::asLong).toArray()));
        tag.put("Casas", cs);
        return tag;
    }

    private static LoreWorldData load(CompoundTag tag) {
        LoreWorldData d = new LoreWorldData();
        tag.getList("Bosses", Tag.TAG_STRING).forEach(t -> d.bosses.add(t.getAsString()));
        tag.getList("Sites", Tag.TAG_STRING).forEach(t -> d.sites.add(t.getAsString()));
        CompoundTag cs = tag.getCompound("Casas");
        for (String k : cs.getAllKeys()) {
            java.util.List<net.minecraft.core.BlockPos> l = new java.util.ArrayList<>();
            for (long v : cs.getLongArray(k)) l.add(net.minecraft.core.BlockPos.of(v));
            d.casas.put(k, l);
        }
        return d;
    }
}
