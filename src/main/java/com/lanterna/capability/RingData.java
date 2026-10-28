package com.lanterna.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Tudo que o mod sabe sobre o jogador como portador de anel.
 * Fica salvo no próprio jogador e sobrevive à morte.
 */
public class RingData implements INBTSerializable<CompoundTag> {

    // --- Slots próprios do mod: 0 = anel, 1 = bateria ---
    public static final int RING_SLOT = 0;
    public static final int BATTERY_SLOT = 1;

    private final ItemStackHandler equipment = new ItemStackHandler(2) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    public ItemStackHandler equipment() { return equipment; }

    // --- O que os outros jogadores veem (anel na mão) ---
    private transient String lastVisibleRing = "";

    /** Devolve true se o anel visível mudou desde a última vez (e guarda o novo). */
    public boolean visibleRingChanged(String key) {
        if (key.equals(lastVisibleRing)) return false;
        lastVisibleRing = key;
        return true;
    }

    // --- Anel em uso ---
    private boolean hasRing;
    private String corps = "";
    private int color = 0x2BFF4A;
    private int charge;
    private int maxCharge = 1000;
    private boolean flightEnabled;

    // --- Seleção (entrega do anel) ---
    private boolean everChosen;
    private String pendingCorps = "";
    private int pendingTicks;
    private int pendingTotal;

    // --- Fúria (seleção vermelha) ---
    private int rageKills;
    private long rageWindowStart;

    // --- Só em memória ---
    private transient int flightTickCounter;
    private transient boolean dirty = true;

    // ===== Anel =====

    public boolean hasRing() { return hasRing; }

    public ResourceLocation corps() {
        ResourceLocation id = ResourceLocation.tryParse(corps);
        return id != null ? id : new ResourceLocation("lanterna", "green");
    }

    public int color() { return color; }
    public int charge() { return charge; }
    public int maxCharge() { return maxCharge; }
    public boolean flightEnabled() { return flightEnabled; }

    /**
     * Atualiza a "foto" do anel que está no slot de anel.
     * O anel (item) é a fonte da verdade; isto aqui é só para HUD e lógica rápida.
     */
    public void refresh(ResourceLocation corpsId, int color, int charge, int maxCharge) {
        String id = corpsId.toString();
        if (!hasRing || !id.equals(this.corps) || color != this.color
                || charge != this.charge || maxCharge != this.maxCharge) {
            this.hasRing = true;
            this.corps = id;
            this.color = color;
            this.maxCharge = Math.max(1, maxCharge);
            this.charge = Math.max(0, Math.min(charge, this.maxCharge));
            markDirty();
        }
    }

    public void unequip() {
        if (hasRing || flightEnabled) {
            this.hasRing = false;
            this.flightEnabled = false;
            this.charge = 0;
            markDirty();
        }
    }

    // --- Bateria de bolso (slot de bateria) ---
    private long lastPocketRecharge = -100000L;
    private int pocketStatus = -1; // -1 = sem bateria compatível, 0 = pronta, >0 = segundos de espera

    public long lastPocketRecharge() { return lastPocketRecharge; }
    public void setLastPocketRecharge(long time) { lastPocketRecharge = time; }
    public int pocketStatus() { return pocketStatus; }

    public void setPocketStatus(int status) {
        if (status != pocketStatus) {
            pocketStatus = status;
            markDirty();
        }
    }

    public void setCharge(int value) {
        int clamped = Math.max(0, Math.min(value, maxCharge));
        if (clamped != charge) {
            charge = clamped;
            markDirty();
        }
    }

    public void addCharge(int delta) { setCharge(charge + delta); }

    public void setMaxCharge(int value) {
        maxCharge = Math.max(1, value);
        if (charge > maxCharge) charge = maxCharge;
        markDirty();
    }

    public void setFlightEnabled(boolean value) {
        if (flightEnabled != value) {
            flightEnabled = value;
            flightTickCounter = 0;
            markDirty();
        }
    }

    public int nextFlightTick() { return ++flightTickCounter; }

    // ===== Seleção =====

    public boolean everChosen() { return everChosen; }
    public void setEverChosen(boolean v) { everChosen = v; }

    public boolean isSelectionPending() { return !pendingCorps.isEmpty() && pendingTicks > 0; }

    public ResourceLocation pendingCorps() { return ResourceLocation.tryParse(pendingCorps); }
    public int pendingTicks() { return pendingTicks; }
    public int pendingTotal() { return pendingTotal; }

    public void startPending(ResourceLocation corpsId, int ticks) {
        pendingCorps = corpsId.toString();
        pendingTicks = ticks;
        pendingTotal = ticks;
    }

    /** Diminui o contador e devolve quantos ticks faltam. */
    public int tickPending() { return --pendingTicks; }

    public void clearPending() {
        pendingCorps = "";
        pendingTicks = 0;
        pendingTotal = 0;
    }

    // ===== Fúria =====

    /** Registra um abate e devolve o total dentro da janela de tempo. */
    public int registerRageKill(long gameTime, int windowTicks) {
        if (rageKills == 0 || gameTime - rageWindowStart > windowTicks) {
            rageWindowStart = gameTime;
            rageKills = 0;
        }
        return ++rageKills;
    }

    public void resetRage() {
        rageKills = 0;
        rageWindowStart = 0;
    }

    // ===== Sincronização =====

    public boolean isDirty() { return dirty; }
    public void markDirty() { dirty = true; }
    public void clearDirty() { dirty = false; }

    // ===== Salvar / carregar =====

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("HasRing", hasRing);
        tag.putString("Corps", corps);
        tag.putInt("Color", color);
        tag.putInt("Charge", charge);
        tag.putInt("MaxCharge", maxCharge);
        tag.putBoolean("Flight", flightEnabled);
        tag.putBoolean("EverChosen", everChosen);
        tag.putString("PendingCorps", pendingCorps);
        tag.putInt("PendingTicks", pendingTicks);
        tag.putInt("PendingTotal", pendingTotal);
        tag.putInt("RageKills", rageKills);
        tag.putLong("RageStart", rageWindowStart);
        tag.put("Equipment", equipment.serializeNBT());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        hasRing = tag.getBoolean("HasRing");
        corps = tag.getString("Corps");
        color = tag.contains("Color") ? tag.getInt("Color") : 0x2BFF4A;
        charge = tag.getInt("Charge");
        maxCharge = tag.contains("MaxCharge") ? Math.max(1, tag.getInt("MaxCharge")) : 1000;
        flightEnabled = tag.getBoolean("Flight");
        everChosen = tag.getBoolean("EverChosen");
        pendingCorps = tag.getString("PendingCorps");
        pendingTicks = tag.getInt("PendingTicks");
        pendingTotal = tag.getInt("PendingTotal");
        rageKills = tag.getInt("RageKills");
        rageWindowStart = tag.getLong("RageStart");
        if (tag.contains("Equipment")) {
            equipment.deserializeNBT(tag.getCompound("Equipment"));
        }
        lastVisibleRing = "";
        dirty = true;
    }

    public void copyFrom(RingData other) {
        deserializeNBT(other.serializeNBT());
    }
}
