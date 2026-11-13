package com.lanterna.lendarios;

import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.Especie;
import com.lanterna.space.Archetype;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Os Lanternas lendários: um de cada Tropa, cada um no seu planeta, longe de tudo (uns 50 mil blocos do centro).
 * O Sinestro fica em Hanks, o planeta-máquina que anda pelo espaço; os outros ficam em planetas fixos.
 *
 * Para quem é de uma Tropa inimiga, é um chefe de verdade. Para a própria Tropa (e as aliadas), é um mestre que aceita
 * duelos de treino (não mata) e dá experiência e itens.
 *
 * Os planetas ficam numa região própria do mundo do tipo deles (como os planetas gerados), bem longe das regiões
 * dos planetas gerados.
 */
public enum Lendario {
    HAL("hal_jordan", BuiltinCorps.GREEN, Especie.HUMANO, 1, Archetype.LUSH, "vigilia", 80, 900, 13),
    LARFLEEZE("larfleeze", BuiltinCorps.ORANGE, Especie.GOBRIN, 1, Archetype.DESERT, "tesouro", 125, 1000, 12),
    ATROCITUS("atrocitus", BuiltinCorps.RED, Especie.BRAKHUL, 1, Archetype.VOLCANIC, "fornalha", 170, 1000, 15),
    SANTO_ANDARILHO("santo_andarilho", BuiltinCorps.BLUE, Especie.SERENIM, 1, Archetype.OCEAN, "remanso", 215, 850, 11),
    INDIGO_1("indigo_1", BuiltinCorps.INDIGO, Especie.NHAI, 1, Archetype.CRYSTAL, "silencio", 260, 850, 12),
    CAROL("carol_ferris", BuiltinCorps.VIOLET, Especie.HUMANO, 2, Archetype.FUNGAL, "coracao", 305, 900, 12),
    MAO_NEGRA("mao_negra", BuiltinCorps.BLACK, Especie.HUMANO, 3, Archetype.BARREN, "necropole", 350, 980, 14),
    SINESTRO("sinestro", BuiltinCorps.YELLOW, Especie.VEKTHAR, 1, null, "hanks", 0, 1024, 15);

    public final String id;
    public final BuiltinCorps corps;
    public final Especie especie;
    public final int pele;
    /** Tipo do mundo do planeta (null = Hanks, mundo próprio). */
    @Nullable public final Archetype tipo;
    /** Nome do planeta (planet.lanterna.lend_<planeta>; o Sinestro: "hanks"). */
    public final String planeta;
    /** Ângulo no mapa (graus), a 50 mil blocos do centro. */
    public final double angulo;
    /** Vida (o Minecraft não deixa passar de 1024) e dano base. */
    public final double vida, dano;

    Lendario(String id, BuiltinCorps corps, Especie especie, int pele, @Nullable Archetype tipo, String planeta, double angulo,
             double vida, double dano) {
        this.id = id; this.corps = corps; this.especie = especie; this.pele = pele; this.tipo = tipo;
        this.planeta = planeta; this.angulo = angulo; this.vida = vida; this.dano = dano;
    }

    public String roupa() {
        return "roupa_lendario_" + id;
    }

    /** Id do corpo celeste ("lend_vigilia"; o Sinestro: "hanks"). */
    public String corpoId() {
        return this == SINESTRO ? "hanks" : "lend_" + planeta;
    }

    public Component nome() {
        return Component.translatable("lendario.lanterna." + id);
    }

    @Nullable
    public static Lendario byId(String id) {
        for (Lendario l : values()) if (l.id.equals(id)) return l;
        return null;
    }

    @Nullable
    public static Lendario doCorpo(String corpoId) {
        for (Lendario l : values()) if (l.corpoId().equals(corpoId)) return l;
        return null;
    }
}
