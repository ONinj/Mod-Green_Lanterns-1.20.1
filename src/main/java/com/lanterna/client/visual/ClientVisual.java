package com.lanterna.client.visual;

import java.util.HashMap;
import java.util.Map;

/** No cliente: o visual de cada jogador (traje do uniforme do anel e altura da máscara). */
public final class ClientVisual {
    public record Look(String traje, int mascara, int flags) {
        /** A aura em volta do corpo está ligada? */
        public boolean aura() { return (flags & com.lanterna.visual.Visual.SEM_AURA) == 0; }
        /** A aura ilumina em volta (na cor da Tropa)? */
        public boolean iluminar() { return aura() && (flags & com.lanterna.visual.Visual.ILUMINAR) != 0; }
    }

    private static final Look PADRAO = new Look("padrao", 0, 0);
    private static final Map<Integer, Look> LOOKS = new HashMap<>();

    public static void set(int entity, String traje, int mascara, int flags) {
        LOOKS.put(entity, new Look(traje, mascara, flags));
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
