package com.lanterna.block;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/** O formato da bateria de cada Tropa (escolhe o modelo do bloco). */
public enum BatteryShape implements StringRepresentable {
    GREEN("green"),    // lanterna de vigia
    RED("red"),        // braseiro
    ORANGE("orange"),  // relicário
    YELLOW("yellow"),  // torre de lâminas
    BLUE("blue"),      // farol
    VIOLET("violet"),  // drusa de cristais
    BLACK("black");    // caveira

    private final String name;

    BatteryShape(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static BatteryShape of(ResourceLocation corps) {
        for (BatteryShape s : values()) if (s.name.equals(corps.getPath())) return s;
        return GREEN;
    }
}
