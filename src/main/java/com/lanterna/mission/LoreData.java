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

    // ---- Nível do Lanterna ----
    private int xp;
    /** Posto mais alto cujas recompensas já foram entregues. */
    private int postoEntregue;
    /** Pontos de emoção (Prova da Emoção). */
    private int emocao;
    /** Concluiu a Prova: o anel se recarrega com a emoção da Tropa. */
    private boolean autoRecarga;
    /** Planetas já visitados (para a descoberta). */
    private final Set<String> visitados = new LinkedHashSet<>();
    /** Técnicas ensinadas pelos Capitães, e as equipadas nas teclas (até 3). */
    private final Set<String> tecnicas = new LinkedHashSet<>();
    private final String[] slotsTecnica = {"", "", ""};
    /** Guarda de Honra (3 técnicas equipadas). */
    private boolean honra;
    /** Corrupção da Morte: 0 nada, 1 acabou de morrer corrompido (falta renascer), 2 corrompido, 3 dominou (Lanterna Negro). */
    private int morte;
    /** Última vez (ms, tempo real) que o Lanterna Negro ceifou um portador de anel. */
    private long ultimaCeifa;
    /** Tropas que este jogador atacou (invadiu planeta delas): ficam hostis com ele. */
    private final java.util.Set<String> tropasHostis = new java.util.LinkedHashSet<>();
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

    public int xp() { return xp; }
    public void setXp(int v) { if (xp != v) { xp = v; dirty = true; } }
    public int postoEntregue() { return postoEntregue; }
    public void setPostoEntregue(int v) { postoEntregue = v; dirty = true; }
    public int emocao() { return emocao; }
    public void addEmocao(int v) { if (v != 0) { emocao = Math.max(0, emocao + v); dirty = true; } }
    public boolean autoRecarga() { return autoRecarga; }
    public void setAutoRecarga(boolean v) { autoRecarga = v; dirty = true; }
    public boolean visitar(String planet) {
        boolean added = visitados.add(planet);
        if (added) dirty = true;
        return added;
    }

    public Set<String> tecnicas() { return tecnicas; }
    public void addTecnica(String t) { if (tecnicas.add(t)) dirty = true; }
    public String slotTecnica(int i) { return i >= 0 && i < 3 ? slotsTecnica[i] : ""; }
    public void setSlotTecnica(int i, String t) {
        if (i < 0 || i >= 3) return;
        for (int j = 0; j < 3; j++) if (slotsTecnica[j].equals(t)) slotsTecnica[j] = "";
        slotsTecnica[i] = t;
        dirty = true;
    }
    public int morte() { return morte; }
    public void setMorte(int v) { morte = v; dirty = true; }
    public long ultimaCeifa() { return ultimaCeifa; }
    public void setUltimaCeifa(long v) { ultimaCeifa = v; dirty = true; }
    public java.util.Set<String> tropasHostis() { return tropasHostis; }
    public void addTropaHostil(String corps) { if (tropasHostis.add(corps)) dirty = true; }

    public boolean honra() { return honra; }
    public void setHonra(boolean v) { honra = v; dirty = true; }

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
        tag.putInt("Xp", xp);
        tag.putInt("PostoEntregue", postoEntregue);
        tag.putInt("Emocao", emocao);
        tag.putBoolean("AutoRecarga", autoRecarga);
        tag.put("Visitados", strings(visitados));
        tag.put("Tecnicas", strings(tecnicas));
        ListTag sl = new ListTag();
        for (String t : slotsTecnica) sl.add(StringTag.valueOf(t));
        tag.put("SlotsTecnica", sl);
        tag.putBoolean("Honra", honra);
        tag.putInt("Morte", morte);
        tag.putLong("UltimaCeifa", ultimaCeifa);
        tag.put("TropasHostis", strings(tropasHostis));
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
        xp = tag.getInt("Xp");
        postoEntregue = tag.getInt("PostoEntregue");
        emocao = tag.getInt("Emocao");
        autoRecarga = tag.getBoolean("AutoRecarga");
        visitados.clear();
        tag.getList("Visitados", Tag.TAG_STRING).forEach(t -> visitados.add(t.getAsString()));
        tecnicas.clear();
        tag.getList("Tecnicas", Tag.TAG_STRING).forEach(t -> tecnicas.add(t.getAsString()));
        ListTag sl = tag.getList("SlotsTecnica", Tag.TAG_STRING);
        for (int i = 0; i < 3; i++) slotsTecnica[i] = i < sl.size() ? sl.getString(i) : "";
        honra = tag.getBoolean("Honra");
        morte = tag.getInt("Morte");
        ultimaCeifa = tag.getLong("UltimaCeifa");
        tropasHostis.clear();
        tag.getList("TropasHostis", Tag.TAG_STRING).forEach(t -> tropasHostis.add(t.getAsString()));
        dirty = true;
    }

    private static ListTag strings(Set<String> set) {
        ListTag list = new ListTag();
        for (String s : set) list.add(StringTag.valueOf(s));
        return list;
    }
}
