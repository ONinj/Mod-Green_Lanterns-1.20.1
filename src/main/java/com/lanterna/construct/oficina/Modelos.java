package com.lanterna.construct.oficina;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Modelos prontos para começar a desenhar (o jogador pode mudar tudo). */
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

    private static void caixa(Projeto p, int x0, int y0, int z0, int x1, int y1, int z1, int tom) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) p.set(x, y, z, tom);
    }

    static Projeto espada() {
        Projeto p = new Projeto();
        p.comp = Comportamento.ARMA;
        caixa(p, 7, 0, 7, 8, 3, 8, 3);       // cabo
        caixa(p, 7, 0, 7, 8, 0, 8, 4);       // pomo
        caixa(p, 4, 4, 7, 11, 4, 8, 1);      // guarda
        caixa(p, 3, 4, 7, 3, 5, 8, 2);
        caixa(p, 12, 4, 7, 12, 5, 8, 2);
        caixa(p, 6, 5, 7, 9, 13, 8, 1);      // lâmina
        caixa(p, 7, 5, 7, 8, 13, 8, 4);      // fio do meio
        caixa(p, 7, 14, 7, 8, 14, 8, 1);     // ponta
        caixa(p, 7, 15, 7, 7, 15, 8, 4);
        return p;
    }

    static Projeto machado() {
        Projeto p = new Projeto();
        p.comp = Comportamento.ARMA;
        caixa(p, 7, 0, 7, 8, 13, 8, 3);
        for (int y = 8; y <= 14; y++) {
            int larg = 4 - Math.abs(y - 11);
            caixa(p, 9, y, 7, 10 + larg + 1, y, 8, y == 8 || y == 14 ? 2 : 1);
        }
        caixa(p, 14, 9, 7, 14, 13, 8, 4);
        caixa(p, 5, 10, 7, 6, 12, 8, 2);
        return p;
    }

    static Projeto escudo() {
        Projeto p = new Projeto();
        p.comp = Comportamento.ESCUDO;
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
            double d = Math.hypot(x - 7.5, y - 7.5);
            if (d > 7.6) continue;
            int tom = d > 6.4 ? 2 : d < 1.6 ? 4 : (Math.abs(x - 7.5) < 1 || Math.abs(y - 7.5) < 1) ? 2 : 1;
            p.set(x, y, 7, tom);
            if (d < 5.5) p.set(x, y, 8, 1);
            if (d < 2.6) p.set(x, y, 6, 4);
        }
        return p;
    }

    static Projeto flecha() {
        Projeto p = new Projeto();
        p.comp = Comportamento.PROJETIL;
        caixa(p, 7, 7, 4, 8, 8, 15, 1);
        for (int z = 0; z <= 3; z++) {
            int r = z / 2;
            caixa(p, 7 - r, 7 - r, z, 8 + r, 8 + r, z, z == 0 ? 4 : 1);
        }
        for (int z = 12; z <= 15; z++) {
            p.set(5, 7, z, 2); p.set(10, 7, z, 2); p.set(7, 5, z, 2); p.set(7, 10, z, 2);
            p.set(6, 7, z, 2); p.set(9, 7, z, 2); p.set(7, 6, z, 2); p.set(7, 9, z, 2);
        }
        return p;
    }

    static Projeto martelo() {
        Projeto p = new Projeto();
        p.comp = Comportamento.QUEDA;
        caixa(p, 7, 0, 7, 8, 8, 8, 2);
        caixa(p, 2, 9, 4, 13, 14, 11, 1);
        caixa(p, 1, 10, 5, 1, 13, 10, 2);
        caixa(p, 14, 10, 5, 14, 13, 10, 2);
        caixa(p, 3, 15, 5, 12, 15, 10, 1);
        caixa(p, 6, 11, 3, 9, 12, 3, 4);
        caixa(p, 6, 11, 12, 9, 12, 12, 4);
        return p;
    }

    static Projeto carro() {
        Projeto p = new Projeto();
        p.comp = Comportamento.VEICULO;
        caixa(p, 2, 2, 1, 13, 4, 14, 1);       // chassi (frente em -Z)
        caixa(p, 3, 5, 5, 12, 6, 11, 2);       // cabine
        caixa(p, 4, 5, 4, 11, 6, 4, 4);        // para-brisa
        caixa(p, 1, 3, 0, 14, 3, 0, 4);        // farol / para-choque
        for (int[] r : new int[][]{{1, 2}, {1, 11}, {13, 2}, {13, 11}}) caixa(p, r[0], 0, r[1], r[0] + 1, 2, r[1] + 2, 3);
        caixa(p, 3, 5, 12, 12, 7, 14, 1);      // aerofólio
        caixa(p, 3, 8, 13, 12, 8, 14, 2);
        return p;
    }

    static Projeto muralha() {
        Projeto p = new Projeto();
        p.comp = Comportamento.ESTRUTURA;
        for (int x = 0; x < 16; x++) for (int y = 0; y < 12; y++) {
            int tom = ((x + (y / 2) % 2 * 2) % 4 == 0 || y % 2 == 0 && x % 4 == 2) ? 2 : 1;
            p.set(x, y, 7, tom);
            p.set(x, y, 8, tom);
        }
        for (int x = 0; x < 16; x += 3) caixa(p, x, 12, 7, Math.min(15, x + 1), 13, 8, 1);
        return p;
    }

    private Modelos() {}
}
