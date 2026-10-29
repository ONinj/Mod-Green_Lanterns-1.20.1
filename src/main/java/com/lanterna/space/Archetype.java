package com.lanterna.space;

import com.lanterna.LanternaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Tipos de planeta gerado. Cada tipo tem um mundo (dimensão) próprio; cada planeta desse tipo
 * ocupa uma região diferente desse mundo, então o terreno de cada um é diferente.
 *
 * Cores da textura vista do espaço: {fundo do mar, mar raso, terra baixa, terra alta, picos}.
 */
public enum Archetype {
    LUSH("lush", 0x6FD8A0, 22, 0.48f, true, true,
            new int[]{0x123C78, 0x2A6FB8, 0x3E9A3A, 0x2C6E2A, 0xE8F0E8}),
    DESERT("desert", 0xFFC98A, 16, 0.12f, true, false,
            new int[]{0x6A4A20, 0x9C7438, 0xE0B66A, 0xC08A48, 0xF4DFA8}),
    ICE("ice", 0xB8E4FF, 12, 0.35f, true, true,
            new int[]{0x2A5070, 0x5A90B8, 0xD8ECF8, 0xB0D0E8, 0xFFFFFF}),
    VOLCANIC("volcanic", 0xFF6A30, 10, 0.40f, false, false,
            new int[]{0xFF5A10, 0xFFA030, 0x2A2224, 0x3C3236, 0x5A4A4A}),
    OCEAN("ocean", 0x60B0FF, 12, 0.78f, true, false,
            new int[]{0x0A2A60, 0x1E5CA8, 0xD8C890, 0x58A048, 0xE8F0E8}),
    CRYSTAL("crystal", 0xD0A8FF, 9, 0.30f, true, false,
            new int[]{0x3A1E68, 0x6A40B0, 0xB890F0, 0xE0C8FF, 0xFFFFFF}),
    FUNGAL("fungal", 0x60E0C8, 10, 0.40f, true, false,
            new int[]{0x1E3A3A, 0x2E7070, 0x8A5AA0, 0x6A4080, 0xD8A8E8}),
    BARREN("barren", 0xA0A0B0, 9, 0.0f, false, false,
            new int[]{0x404048, 0x5A5A64, 0x8A8A94, 0x6E6E78, 0xB8B8C0});

    public final String id;
    public final int atmosphere;
    public final int weight;
    /** Quanto da superfície é mar (vista do espaço). */
    public final float sea;
    /** Dá para respirar (sem o anel). */
    public final boolean breathable;
    /** Calotas polares brancas. */
    public final boolean polarCaps;
    public final int[] palette;
    public final ResourceKey<Level> dimension;

    Archetype(String id, int atmosphere, int weight, float sea, boolean breathable, boolean polarCaps, int[] palette) {
        this.id = id;
        this.atmosphere = atmosphere;
        this.weight = weight;
        this.sea = sea;
        this.breathable = breathable;
        this.polarCaps = polarCaps;
        this.palette = palette;
        this.dimension = ResourceKey.create(Registries.DIMENSION, LanternaMod.id("world_" + id));
    }

    public String nameKey() {
        return "planet_type.lanterna." + id;
    }

    public static Archetype byDimension(ResourceKey<Level> dim) {
        for (Archetype a : values()) if (a.dimension.equals(dim)) return a;
        return null;
    }

    static Archetype pick(java.util.Random r) {
        int total = 0;
        for (Archetype a : values()) total += a.weight;
        int n = r.nextInt(total);
        for (Archetype a : values()) {
            n -= a.weight;
            if (n < 0) return a;
        }
        return LUSH;
    }
}
