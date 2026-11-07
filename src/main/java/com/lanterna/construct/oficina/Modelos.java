package com.lanterna.construct.oficina;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Modelos prontos para começar a desenhar (o jogador pode mudar tudo). Grade de 32, meio em 15,5. */
public final class Modelos {
    public static final Map<String, Supplier<Projeto>> TODOS = new LinkedHashMap<>();

    static {
        TODOS.put("espada", Modelos::espada);
        TODOS.put("machado", Modelos::machado);
        TODOS.put("escudo", Modelos::escudo);
        TODOS.put("flecha", Modelos::flecha);
        TODOS.put("martelo", Modelos::martelo);
        TODOS.put("carro", Modelos::carro);
        TODOS.put("muralha", Modelos::muralha);
    }

    private static final float M = 15.5f;

    private static void caixa(Projeto p, int x0, int y0, int z0, int x1, int y1, int z1, int tom) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) p.set(x, y, z, tom);
    }

    /** Cilindro em pé (eixo Y) centrado em (cx, cz). */
    private static void cilindroY(Projeto p, float cx, float cz, float r, int y0, int y1, int tom) {
        for (int x = 0; x < Projeto.N; x++) for (int z = 0; z < Projeto.N; z++) {
            if ((x - cx) * (x - cx) + (z - cz) * (z - cz) > r * r) continue;
            for (int y = y0; y <= y1; y++) p.set(x, y, z, tom);
        }
    }

    /** Cilindro deitado no eixo X centrado em (cy, cz). */
    private static void cilindroX(Projeto p, float cy, float cz, float r, int x0, int x1, int tom) {
        for (int y = 0; y < Projeto.N; y++) for (int z = 0; z < Projeto.N; z++) {
            if ((y - cy) * (y - cy) + (z - cz) * (z - cz) > r * r) continue;
            for (int x = x0; x <= x1; x++) p.set(x, y, z, tom);
        }
    }

    private static void elipsoide(Projeto p, float cx, float cy, float cz, float rx, float ry, float rz, int tom) {
        for (int x = 0; x < Projeto.N; x++) for (int y = 0; y < Projeto.N; y++) for (int z = 0; z < Projeto.N; z++) {
            float a = (x - cx) / rx, b = (y - cy) / ry, c = (z - cz) / rz;
            if (a * a + b * b + c * c <= 1) p.set(x, y, z, tom);
        }
    }

    static Projeto espada() {
        Projeto p = new Projeto();
        p.comp = Comportamento.ARMA;
        cilindroY(p, M, M, 1.6f, 0, 8, 2);                    // cabo
        for (int y = 2; y <= 7; y += 2) cilindroY(p, M, M, 1.9f, y, y, 1);   // tiras do cabo
        elipsoide(p, M, 0.5f, M, 2.4f, 1.6f, 2.4f, 3);         // pomo
        // guarda, com as pontas curvando para cima
        for (int x = 6; x <= 25; x++) {
            float d = Math.abs(x - M);
            int sobe = d > 7 ? (int) ((d - 7) * 0.8f) : 0;
            caixa(p, x, 9 + sobe, 14, x, 10 + sobe, 17, d < 2 ? 3 : 1);
        }
        // lâmina: losango na seção, afinando até a ponta
        for (int y = 11; y <= 31; y++) {
            float meia = y < 25 ? 3.6f : 3.6f * (31.5f - y) / 6.5f;
            for (int x = 0; x < Projeto.N; x++) for (int z = 0; z < Projeto.N; z++) {
                float dx = Math.abs(x - M), dz = Math.abs(z - M);
                if (dx / Math.max(0.6f, meia) + dz / 1.6f > 1) continue;
                p.set(x, y, z, dx < 0.6f ? 3 : 1);
            }
        }
        return p;
    }

    static Projeto machado() {
        Projeto p = new Projeto();
        p.comp = Comportamento.ARMA;
        cilindroY(p, M, M, 1.5f, 0, 27, 2);
        elipsoide(p, M, 28, M, 2, 2, 2, 3);
        for (int x = 0; x < Projeto.N; x++) for (int y = 14; y < 31; y++) for (int z = 12; z <= 19; z++) {
            // lâmina em meia-lua do lado +X, afinando para o fio
            float ex = (x - 16f) / 13f, ey = (y - 22f) / 8.5f;
            float ix = (x - 13f) / 9f, iy = (y - 22f) / 5f;
            boolean fora = ex * ex + ey * ey <= 1, dentro = ix * ix + iy * iy <= 1;
            if (!fora || (dentro && x > 20) || x < 16) continue;
            float esp = 3.5f - (x - 16) * 0.22f;
            if (Math.abs(z - M) > Math.max(0.7f, esp)) continue;
            p.set(x, y, z, x > 26 ? 3 : 1);
        }
        // contrapeso do outro lado
        for (int x = 8; x <= 14; x++) {
            float r = 2.6f - (14 - x) * 0.25f;
            for (int y = 0; y < Projeto.N; y++) for (int z = 0; z < Projeto.N; z++)
                if ((y - 22) * (y - 22) + (z - M) * (z - M) <= r * r) p.set(x, y, z, 2);
        }
        return p;
    }

    static Projeto escudo() {
        Projeto p = new Projeto();
        p.comp = Comportamento.ESCUDO;
        for (int x = 0; x < Projeto.N; x++) for (int y = 0; y < Projeto.N; y++) {
            float d = (float) Math.hypot(x - M, y - M);
            if (d > 15) continue;
            float bojo = 3.2f * (1 - (d / 15f) * (d / 15f));
            int z0 = Math.round(15 - bojo);
            int tom = d > 13.2f ? 2 : (d > 6 && d < 7.6f) ? 3 : d < 3.5f ? 3 : 1;
            for (int z = z0; z <= 16; z++) p.set(x, y, z, tom);
        }
        return p;
    }

    static Projeto flecha() {
        Projeto p = new Projeto();
        p.comp = Comportamento.PROJETIL;
        for (int z = 9; z <= 31; z++) for (int x = 0; x < Projeto.N; x++) for (int y = 0; y < Projeto.N; y++)
            if ((x - M) * (x - M) + (y - M) * (y - M) <= 1.3f * 1.3f) p.set(x, y, z, 1);
        // ponta: pirâmide afiada na frente (-Z)
        for (int z = 0; z <= 9; z++) {
            float r = 4.2f * z / 9f;
            for (int x = 0; x < Projeto.N; x++) for (int y = 0; y < Projeto.N; y++)
                if (Math.abs(x - M) + Math.abs(y - M) <= r) p.set(x, y, z, z < 3 ? 3 : 1);
        }
        // penas
        for (int z = 23; z <= 31; z++) {
            float alt = 1.5f + (z - 23) * 0.55f;
            for (int k = 1; k <= Math.round(alt); k++) {
                p.set(Math.round(M + 1 + k), 15, z, 2); p.set(Math.round(M - 1 - k), 15, z, 2);
                p.set(15, Math.round(M + 1 + k), z, 2); p.set(15, Math.round(M - 1 - k), z, 2);
            }
        }
        return p;
    }

    static Projeto martelo() {
        Projeto p = new Projeto();
        p.comp = Comportamento.QUEDA;
        cilindroY(p, M, M, 2f, 0, 18, 2);
        for (int y = 3; y <= 15; y += 4) cilindroY(p, M, M, 2.4f, y, y + 1, 1);
        // cabeça com cantos chanfrados
        for (int x = 5; x <= 26; x++) for (int y = 19; y <= 30; y++) for (int z = 9; z <= 22; z++) {
            int bx = Math.min(x - 5, 26 - x), by = Math.min(y - 19, 30 - y), bz = Math.min(z - 9, 22 - z);
            if (bx + by < 2 || bx + bz < 2 || by + bz < 2) continue;
            int tom = (x <= 6 || x >= 25) ? 3 : (y == 24 || y == 25) ? 2 : 1;
            p.set(x, y, z, tom);
        }
        return p;
    }

    static Projeto carro() {
        Projeto p = new Projeto();
        p.comp = Comportamento.VEICULO;
        // casco aerodinâmico (frente em -Z, mais baixa)
        for (int z = 2; z <= 29; z++) {
            float t = (z - 2) / 27f;
            float meia = 8.5f + 1.5f * (float) Math.sin(t * Math.PI);
            float alt = 2.5f + 4f * (float) Math.sin(Math.min(1, t * 1.4f) * Math.PI / 2);
            for (int x = 0; x < Projeto.N; x++) for (int y = 3; y < Projeto.N; y++) {
                float a = (x - M) / meia, b = (y - 3f) / alt;
                if (a * a + b * b > 1 || y < 3) continue;
                p.set(x, y, z, Math.abs(x - M) > meia - 1.2f ? 2 : 1);
            }
        }
        elipsoide(p, M, 8.5f, 18, 6.5f, 4f, 7, 3);           // cabine (bolha)
        for (int[] r : new int[][]{{6, 7}, {6, 24}, {22, 7}, {22, 24}}) cilindroX(p, 3.5f, r[1], 3.4f, r[0], r[0] + 3, 2); // rodas
        caixa(p, 9, 5, 1, 11, 6, 2, 3);                        // faróis
        caixa(p, 20, 5, 1, 22, 6, 2, 3);
        return p;
    }

    static Projeto muralha() {
        Projeto p = new Projeto();
        p.comp = Comportamento.ESTRUTURA;
        for (int x = 0; x < Projeto.N; x++) for (int y = 0; y <= 21; y++) for (int z = 14; z <= 17; z++) {
            boolean junta = y % 4 == 0 || (x + (y / 4 % 2) * 4) % 8 == 0;
            boolean seteira = (x % 10 == 5 || x % 10 == 6) && y >= 11 && y <= 16;
            if (seteira && z >= 15 && z <= 16) continue;
            p.set(x, y, z, junta ? 2 : 1);
        }
        for (int x = 0; x < Projeto.N; x += 7) caixa(p, x, 22, 13, Math.min(31, x + 3), 25, 18, 1);
        for (int x = 2; x < Projeto.N; x += 14) caixa(p, x, 0, 12, x + 2, 18, 19, 2); // contrafortes
        return p;
    }

    private Modelos() {}
}
