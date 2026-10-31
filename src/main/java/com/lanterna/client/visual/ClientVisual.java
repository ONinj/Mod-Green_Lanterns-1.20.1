package com.lanterna.client.visual;

import java.util.HashMap;
import java.util.Map;

/** No cliente: o visual de cada jogador (traje do uniforme do anel e altura da máscara). */
public final class ClientVisual {
    public record Look(String traje, int mascara) {}

    private static final Look PADRAO = new Look("padrao", 0);
    private static final Map<Integer, Look> LOOKS = new HashMap<>();

    public static void set(int entity, String traje, int mascara) {
        LOOKS.put(entity, new Look(traje, mascara));
    }

    public static Look of(int entity) {
        return LOOKS.getOrDefault(entity, PADRAO);
    }

    /** Deslocamento da máscara em pixels do modelo (positivo = para cima). */
    public static float mascaraPx(int entity) {
        return of(entity).mascara() / 4f;
    }

    public static void clear() {
        LOOKS.clear();
    }

    private ClientVisual() {}
}
