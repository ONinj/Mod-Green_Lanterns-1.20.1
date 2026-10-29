package com.lanterna.mission;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Progresso do jogador na lore: missões, respeito com cada raça, presets de construtos e marcos.
 * Fica salvo no jogador e sobrevive à morte (como o RingData).
 */
public class LoreData implements INBTSerializable<CompoundTag> {
    public static final int PRESETS = 4;

    /** Missões em andamento: id -> progresso de cada objetivo. */
    private final Map<String, int[]> active = new LinkedHashMap<>();
    private final Set<String> completed = new LinkedHashSet<>();
    private String tracked = "";
    private final Map<String, Integer> rep = new HashMap<>();
    private final String[] presets = {"laser", "parede", "martelo", "escudo"};
    private String selected = "laser";
    private final Set<String> bossKills = new LinkedHashSet<>();
    private long lastPassiveKill = -1_000_000L;
    private boolean geneseRevealed;
    /** Afinidade do anel em uso (espelho do anel, para o HUD). */
    private int affinity;

    private transient boolean dirty = true;

    // ===================== Missões =====================

    public Map<String, int[]> active() {
        return active;
    }

    public Set<String> completed() {
        return completed;
    }

    public boolean isActive(String id) {
        return active.containsKey(id);
    }

    public boolean isDone(String id) {
        return completed.contains(id);
    }

    public void start(Mission m) {
        if (active.containsKey(m.id()) || (completed.contains(m.id()) && !m.line().equals("patrulha"))) return;
        active.put(m.id(), new int[m.objectives().size()]);
        if (tracked.isEmpty() || m.main() || Missions.get(tracked) == null || !active.containsKey(tracked)) tracked = m.id();
        dirty = true;
    }

    public void finish(String id) {
        active.remove(id);
        completed.add(id);
        if (tracked.equals(id)) tracked = active.isEmpty() ? "" : active.keySet().iterator().next();
        dirty = true;
    }

    public void abandon(String id) {
        active.remove(id);
        if (tracked.equals(id)) tracked = active.isEmpty() ? "" : active.keySet().iterator().next();
        dirty = true;
    }

    public String tracked() {
        return tracked;
    }

    public void setTracked(String id) {
        if (active.containsKey(id)) {
            tracked = id;
            dirty = true;
        }
    }

    /** Muda o progresso de um objetivo (devolve true se mudou). */
    public boolean setProgress(String id, int index, int value) {
        int[] p = active.get(id);
        if (p == null || index >= p.length || p[index] == value) return false;
        p[index] = value;
        dirty = true;
        return true;
    }

    public int progress(String id, int index) {
        int[] p = active.get(id);
        return p == null || index >= p.length ? 0 : p[index];
    }

    // ===================== Respeito =====================

    public int rep(String race) {
        return rep.getOrDefault(race, 0);
    }

    public void addRep(String race, int amount) {
        rep.merge(race, amount, Integer::sum);
        dirty = true;
    }

    public Map<String, Integer> reps() {
        return rep;
    }

    // ===================== Construtos =====================

    public String preset(int i) {
        return presets[i];
    }

    public void setPreset(int i, String construct) {
        if (i < 0 || i >= PRESETS) return;
        presets[i] = construct;
        dirty = true;
    }

    public String selected() {
        return selected;
    }

    public void setSelected(String s) {
        selected = s;
        dirty = true;
    }

    // ===================== Marcos =====================

    public Set<String> bossKills() {
        return bossKills;
    }

    public void addBossKill(String boss) {
        if (bossKills.add(boss)) dirty = true;
    }

    public long lastPassiveKill() {
        return lastPassiveKill;
    }

    public void setLastPassiveKill(long t) {
        lastPassiveKill = t;
    }

    public boolean geneseRevealed() {
        return geneseRevealed;
    }

    public void setGeneseRevealed(boolean v) {
        if (geneseRevealed != v) {
            geneseRevealed = v;
            dirty = true;
        }
    }

    public int affinity() {
        return affinity;
    }

    public void setAffinity(int v) {
        if (affinity != v) {
            affinity = v;
            dirty = true;
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void clearDirty() {
        dirty = false;
    }

    public void copyFrom(LoreData other) {
        deserializeNBT(other.serializeNBT());
        dirty = true;
    }

    // ===================== NBT =====================

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        CompoundTag act = new CompoundTag();
        active.forEach((id, p) -> act.putIntArray(id, p));
        tag.put("Active", act);
        tag.put("Completed", strings(completed));
        tag.putString("Tracked", tracked);
        CompoundTag r = new CompoundTag();
        rep.forEach(r::putInt);
        tag.put("Rep", r);
        ListTag pr = new ListTag();
        for (String s : presets) pr.add(StringTag.valueOf(s));
        tag.put("Presets", pr);
        tag.putString("Selected", selected);
        tag.put("Bosses", strings(bossKills));
        tag.putLong("PassiveKill", lastPassiveKill);
        tag.putBoolean("Genese", geneseRevealed);
        tag.putInt("Affinity", affinity);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        active.clear();
        CompoundTag act = tag.getCompound("Active");
        for (String id : act.getAllKeys()) {
            Mission m = Missions.get(id);
            int[] p = act.getIntArray(id);
            if (m != null && p.length != m.objectives().size()) p = java.util.Arrays.copyOf(p, m.objectives().size());
            active.put(id, p);
        }
        completed.clear();
        tag.getList("Completed", Tag.TAG_STRING).forEach(t -> completed.add(t.getAsString()));
        tracked = tag.getString("Tracked");
        rep.clear();
        CompoundTag r = tag.getCompound("Rep");
        for (String k : r.getAllKeys()) rep.put(k, r.getInt(k));
        ListTag pr = tag.getList("Presets", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(PRESETS, pr.size()); i++) presets[i] = pr.getString(i);
        if (tag.contains("Selected")) selected = tag.getString("Selected");
        bossKills.clear();
        tag.getList("Bosses", Tag.TAG_STRING).forEach(t -> bossKills.add(t.getAsString()));
        lastPassiveKill = tag.contains("PassiveKill") ? tag.getLong("PassiveKill") : -1_000_000L;
        geneseRevealed = tag.getBoolean("Genese");
        affinity = tag.getInt("Affinity");
        dirty = true;
    }

    private static ListTag strings(Set<String> set) {
        ListTag list = new ListTag();
        for (String s : set) list.add(StringTag.valueOf(s));
        return list;
    }
}
