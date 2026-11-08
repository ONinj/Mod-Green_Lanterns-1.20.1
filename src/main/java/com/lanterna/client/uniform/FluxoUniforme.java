package com.lanterna.client.uniform;

import java.util.ArrayList;
import java.util.List;

/**
 * Como o uniforme escorre pelo corpo, feito líquido: nasce na mão do anel, sobe pelo braço, chega no ombro, se espalha
 * pelo peito e daí desce pelas pernas, sobe pelo pescoço até a cabeça e desce pelo outro braço até a outra mão.
 * A frente é irregular (ruído em 3D), então ela avança em línguas e escorre em vez de vir numa linha reta.
 *
 * Tudo em coordenadas do modelo em pixels, com Y para cima: pés em 0, topo da cabeça em 32; braço direito em x -8..-4.
 * O resultado é o "tempo" (0..1) em que aquele ponto do corpo fica coberto.
 */
public final class FluxoUniforme {
    /** Partes: 0 cabeça, 1 tronco, 2 braço direito, 3 braço esquerdo, 4 perna direita, 5 perna esquerda. */
    public static final int CABECA = 0, TRONCO = 1, BRACO_D = 2, BRACO_E = 3, PERNA_D = 4, PERNA_E = 5;

    private static final float BRACO = 0.22f;          // da mão do anel até o ombro
    private static final float PEITO = 0.026f;         // por pixel de distância do ombro, no tronco
    private static final float PESCOCO = 0.036f;       // por pixel subindo a cabeça
    private static final float PERNA = 0.034f;         // por pixel descendo a perna
    private static final float BRACO2 = 0.22f;         // do outro ombro até a outra mão
    private static final float MAX = 0.98f;

    private static float dist(float ax, float ay, float az, float bx, float by, float bz) {
        float dx = ax - bx, dy = ay - by, dz = az - bz;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static float tronco(float x, float y, float z) {
        // mais "rápido" para os lados e para baixo do que para trás: o líquido abraça o peito
        return BRACO + dist(x, y, z * 0.7f, -4f, 23f, 0f) * PEITO;
    }

    /** Tempo (0..1) em que o ponto (da parte dada) fica coberto, sem o ruído. */
    public static float base(int parte, float x, float y, float z) {
        float t = switch (parte) {
            case BRACO_D -> Math.max(0f, (y - 12f) / 12f) * BRACO
                    // a frente sobe um pouco torta, contornando o braço
                    + (z * 0.004f);
            case TRONCO -> tronco(x, y, z);
            case BRACO_E -> tronco(4f, 23f, 0f) + Math.max(0f, (24f - y) / 12f) * BRACO2;
            case CABECA -> tronco(0f, 24f, 0f) + dist(x * 0.8f, y, z * 0.8f, 0f, 24f, 0f) * PESCOCO;
            case PERNA_D -> tronco(-2f, 12f, 0f) + Math.max(0f, 12f - y) * PERNA;
            case PERNA_E -> tronco(2f, 12f, 0f) + Math.max(0f, 12f - y) * PERNA;
            default -> 0.5f;
        };
        return t;
    }

    /** Tempo com a frente irregular (línguas que escorrem). */
    public static float tempo(int parte, float x, float y, float z, int semente) {
        float t = base(parte, x, y, z);
        float n = fbm(x * 0.32f, y * 0.32f, z * 0.32f) - 0.5f;          // línguas grandes
        float f = hash(Math.round(x * 2), Math.round(y * 2), Math.round(z * 2) + semente * 131) - 0.5f; // grão fino
        t = t / MAX + n * 0.16f + f * 0.035f;
        return Math.max(0.001f, Math.min(0.999f, t));
    }

    // ------------------------------------------------------------------ pele (layout de skin 64x64)

    /** Caixas do layout de skin: (u, v, largura, altura, profundidade, parte). */
    private static final int[][] CAIXAS = {
            {0, 0, 8, 8, 8, CABECA}, {16, 16, 8, 12, 4, TRONCO}, {40, 16, 4, 12, 4, BRACO_D},
            {32, 48, 4, 12, 4, BRACO_E}, {0, 16, 4, 12, 4, PERNA_D}, {16, 48, 4, 12, 4, PERNA_E},
            {32, 0, 8, 8, 8, CABECA}, {16, 32, 8, 12, 4, TRONCO}, {40, 32, 4, 12, 4, BRACO_D},
            {48, 48, 4, 12, 4, BRACO_E}, {0, 32, 4, 12, 4, PERNA_D}, {0, 48, 4, 12, 4, PERNA_E},
    };

    /** Onde cada parte fica no corpo: x0, y0 (embaixo), z0 (frente). */
    private static final float[][] ORIGEM = {
            {-4, 24, -4}, {-4, 12, -2}, {-8, 12, -2}, {4, 12, -2}, {-4, 0, -2}, {0, 0, -2},
    };

    /** {parte, x, y, z} do pixel (lx, ly) da skin, ou null se ele não pertence ao corpo. */
    public static float[] ponto(int lx, int ly) {
        for (int[] b : CAIXAS) {
            int u = b[0], v = b[1], w = b[2], h = b[3], d = b[4], parte = b[5];
            if (lx < u || lx >= u + 2 * d + 2 * w || ly < v || ly >= v + d + h) continue;
            float[] o = ORIGEM[parte];
            float x0 = o[0], y0 = o[1], z0 = o[2], x1 = x0 + w, y1 = y0 + h, z1 = z0 + d;
            if (ly < v + d) {
                int r = ly - v;
                if (lx >= u + d && lx < u + d + w) return new float[]{parte, x0 + (lx - u - d) + 0.5f, y1, z1 - r - 0.5f};
                if (lx >= u + d + w && lx < u + d + 2 * w) return new float[]{parte, x0 + (lx - u - d - w) + 0.5f, y0, z1 - r - 0.5f};
                return null;
            }
            float y = y1 - (ly - v - d) - 0.5f;
            int c = lx - u;
            if (c < d) return new float[]{parte, x0, y, z1 - c - 0.5f};                    // lado direito
            c -= d;
            if (c < w) return new float[]{parte, x0 + c + 0.5f, y, z0};                    // frente
            c -= w;
            if (c < d) return new float[]{parte, x1, y, z0 + c + 0.5f};                    // lado esquerdo
            c -= d;
            return new float[]{parte, x1 - c - 0.5f, y, z1};                               // costas
        }
        return null;
    }

    /** Tempo do pixel (lx, ly) do layout de skin 64x64, ou -1 se não é do corpo. */
    public static float pele(int lx, int ly) {
        float[] p = ponto(lx, ly);
        if (p == null) return -1f;
        return tempo((int) p[0], p[1], p[2], p[3], 0);
    }

    /** Pontos da superfície do corpo com o tempo de cada um (para as faíscas seguirem a frente). */
    private static List<float[]> pontos;

    public static List<float[]> pontos() {
        if (pontos == null) {
            pontos = new ArrayList<>();
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                float[] p = ponto(x, y);
                if (p == null || (x >= 32 && y < 16) || y >= 32 && y < 48 || (x < 16 && y >= 48) || (x >= 48 && y >= 48)) continue;
                pontos.add(new float[]{p[1], p[2], p[3], tempo((int) p[0], p[1], p[2], p[3], 0)});
            }
        }
        return pontos;
    }

    /** Parte do corpo pelo nome do osso do uniforme 3D. */
    public static int parteDoOsso(String nome) {
        String n = nome.toLowerCase();
        if (n.contains("head")) return CABECA;
        if (n.contains("rightarm")) return BRACO_D;
        if (n.contains("leftarm")) return BRACO_E;
        if (n.contains("rightleg") || n.contains("rightboot")) return PERNA_D;
        if (n.contains("leftleg") || n.contains("leftboot")) return PERNA_E;
        return TRONCO;
    }

    // ------------------------------------------------------------------ ruído

    private static float hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535f;
    }

    private static float suave(float t) {
        return t * t * (3 - 2 * t);
    }

    private static float valor(float x, float y, float z) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        float fx = suave(x - xi), fy = suave(y - yi), fz = suave(z - zi);
        float a = lerp(fx, hash(xi, yi, zi), hash(xi + 1, yi, zi));
        float b = lerp(fx, hash(xi, yi + 1, zi), hash(xi + 1, yi + 1, zi));
        float c = lerp(fx, hash(xi, yi, zi + 1), hash(xi + 1, yi, zi + 1));
        float d = lerp(fx, hash(xi, yi + 1, zi + 1), hash(xi + 1, yi + 1, zi + 1));
        return lerp(fz, lerp(fy, a, b), lerp(fy, c, d));
    }

    private static float fbm(float x, float y, float z) {
        return valor(x, y, z) * 0.6f + valor(x * 2.1f + 7, y * 2.1f, z * 2.1f) * 0.3f + valor(x * 4.3f, y * 4.3f + 3, z * 4.3f) * 0.1f;
    }

    private static float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }

    private FluxoUniforme() {}
}
