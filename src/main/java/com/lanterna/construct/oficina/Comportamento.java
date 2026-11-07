package com.lanterna.construct.oficina;

import net.minecraft.network.chat.Component;

/**
 * O que o construto desenhado faz. Cada comportamento já vem com as animações dele (montar, segurar, golpear,
 * cair, rodar...), então o jogador só desenha a forma.
 *
 * tamanhoBloco = tamanho de cada "cubinho" do desenho no mundo (com a escala 1x).
 */
public enum Comportamento {
    /** Arma na mão (espada, martelo, lança, machado): golpeia com o clique esquerdo enquanto dura. */
    ARMA("arma", 1f / 9f, 18, 20 * 20, 20 * 4),
    /** Escudo que flutua na frente: bloqueia flechas e diminui o dano que vem da frente. */
    ESCUDO("escudo", 1f / 8f, 16, 20 * 14, 20 * 6),
    /** Disparo: sai voando na direção da mira e explode de leve no impacto. */
    PROJETIL("projetil", 1f / 12f, 14, 20 * 4, 20 * 2),
    /** Queda do céu: aparece bem alto em cima da mira e cai com tudo (área de impacto). */
    QUEDA("queda", 1f / 4f, 34, 20 * 6, 20 * 10),
    /** Veículo: aparece embaixo de você e você monta; anda para onde olha (W/S), flutua um pouco do chão. */
    VEICULO("veiculo", 1f / 6f, 30, 20 * 60, 20 * 15),
    /** Estrutura: fica parada onde você mira (muralha, torre, ponte); segura flechas e empurra quem encosta. */
    ESTRUTURA("estrutura", 1f / 4f, 24, 20 * 15, 20 * 8);

    public final String id;
    public final float tamanhoBloco;
    public final int custoBase, duracao, recarga;

    Comportamento(String id, float tamanhoBloco, int custoBase, int duracao, int recarga) {
        this.id = id;
        this.tamanhoBloco = tamanhoBloco;
        this.custoBase = custoBase;
        this.duracao = duracao;
        this.recarga = recarga;
    }

    public Component nome() {
        return Component.translatable("oficina.lanterna.comp." + id);
    }

    public Component descricao() {
        return Component.translatable("oficina.lanterna.comp." + id + ".desc");
    }

    public static Comportamento of(int i) {
        Comportamento[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
