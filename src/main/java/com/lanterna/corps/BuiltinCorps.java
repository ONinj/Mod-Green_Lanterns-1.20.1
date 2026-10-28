package com.lanterna.corps;

import com.lanterna.LanternaMod;
import net.minecraft.resources.ResourceLocation;

/**
 * As Tropas que já vêm no mod. Os valores de jogo (carga, gasto de voo, etc.)
 * ficam nos JSON em data/lanterna/lanterna_corps/. Aqui só fica a cor padrão,
 * usada no menu criativo e como reserva caso um JSON esteja faltando.
 */
public enum BuiltinCorps {
    GREEN("green", 0x2BFF4A),
    YELLOW("yellow", 0xFFE01A),
    RED("red", 0xE01010),
    BLUE("blue", 0x2A7BFF),
    VIOLET("violet", 0xC23BFF),
    INDIGO("indigo", 0x5B3BD8),
    ORANGE("orange", 0xFF8A1A),
    BLACK("black", 0x3A3A44),
    WHITE("white", 0xF2F2F2);

    private final ResourceLocation id;
    private final int color;

    BuiltinCorps(String path, int color) {
        this.id = LanternaMod.id(path);
        this.color = color;
    }

    public ResourceLocation id() {
        return id;
    }

    public int color() {
        return color;
    }

    public static int colorOf(ResourceLocation id) {
        for (BuiltinCorps c : values()) {
            if (c.id.equals(id)) return c.color;
        }
        return GREEN.color;
    }
}
