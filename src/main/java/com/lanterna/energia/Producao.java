package com.lanterna.energia;

import com.lanterna.LanternaConfig;

/** Números de equilíbrio da Energia da Emoção (num lugar só). */
public final class Producao {
    /** Refino próprio: 1000 mB + 1 redstone = 1 Cristal Emocional. */
    public static final int MB_POR_CRISTAL = 1000;
    public static final int TICKS_REFINO = 160;
    /** FE de cada combustível no Conversor de Luz (o caminho do Mekanism rende 2 núcleos por balde = 10x). */
    public static final int FE_CRISTAL = 60_000;
    public static final int FE_NUCLEO = 300_000;
    public static final int FE_POR_TICK_CONVERSOR = 600;

    public static float mult() {
        return LanternaConfig.ENERGIA_GERADOR_MULT.get().floatValue();
    }

    public static int feMineradora() {
        return LanternaConfig.ENERGIA_MINERADORA_FE.get();
    }

    private Producao() {}
}
