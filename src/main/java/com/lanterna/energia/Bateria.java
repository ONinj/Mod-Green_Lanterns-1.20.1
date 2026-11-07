package com.lanterna.energia;

import net.minecraftforge.energy.EnergyStorage;

/** Armazém de FE com controle do que entra e sai pelos lados. */
public class Bateria extends EnergyStorage {
    private final Runnable mudou;

    public Bateria(int cap, int entra, int sai, Runnable mudou) {
        super(cap, entra, sai);
        this.mudou = mudou;
    }

    public void definir(int v) {
        energy = Math.max(0, Math.min(capacity, v));
    }

    /** Uso interno (ignora o limite de entrada). */
    public int gerar(int v) {
        int e = Math.min(capacity - energy, v);
        energy += e;
        if (e > 0) mudou.run();
        return e;
    }

    /** Uso interno (ignora o limite de saída). */
    public boolean gastar(int v) {
        if (energy < v) return false;
        energy -= v;
        mudou.run();
        return true;
    }

    @Override
    public int receiveEnergy(int max, boolean sim) {
        int r = super.receiveEnergy(max, sim);
        if (r > 0 && !sim) mudou.run();
        return r;
    }

    @Override
    public int extractEnergy(int max, boolean sim) {
        int r = super.extractEnergy(max, sim);
        if (r > 0 && !sim) mudou.run();
        return r;
    }
}
