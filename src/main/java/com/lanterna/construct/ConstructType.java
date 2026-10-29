package com.lanterna.construct;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Os construtos do anel. Custos em carga do anel (o anel tem ~1000 de carga);
 * "segurar" = fica ativo enquanto a tecla estiver apertada (custo por tick).
 *
 *   Ataque:     laser do punho, punho gigante, rajada, martelo gigante, lâminas giratórias
 *   Controle:   jaula, mão que puxa
 *   Defesa:     parede, escudo-bolha
 *   Utilidade:  picareta, ponte, gancho
 */
public enum ConstructType {
    //      id          categoria          custo segurar nível recarga(ticks)
    LASER("laser", Category.ATTACK, 2, true, 0, 0),
    PUNHO("punho", Category.ATTACK, 30, false, 0, 14),
    PAREDE("parede", Category.DEFENSE, 60, false, 0, 30),
    PONTE("ponte", Category.UTILITY, 50, false, 0, 30),
    PICARETA("picareta", Category.UTILITY, 1, true, 1, 0),
    RAJADA("rajada", Category.ATTACK, 14, false, 1, 8),
    MARTELO("martelo", Category.ATTACK, 120, false, 2, 70),
    ESCUDO("escudo", Category.DEFENSE, 90, false, 2, 200),
    GANCHO("gancho", Category.UTILITY, 30, false, 3, 16),
    JAULA("jaula", Category.CONTROL, 110, false, 3, 160),
    LAMINA("lamina", Category.ATTACK, 90, false, 4, 70),
    MAO("mao", Category.CONTROL, 50, false, 4, 50);

    public enum Category {
        ATTACK(0xFF5A5A), DEFENSE(0x5AB0FF), UTILITY(0xFFD35A), CONTROL(0xC07AFF);

        public final int color;

        Category(int color) {
            this.color = color;
        }

        public Component label() {
            return Component.translatable("construct.lanterna.category." + name().toLowerCase());
        }
    }

    public final String id;
    public final Category category;
    public final int cost;
    public final boolean hold;
    public final int unlockLevel;
    public final int cooldown;

    ConstructType(String id, Category category, int cost, boolean hold, int unlockLevel, int cooldown) {
        this.id = id;
        this.category = category;
        this.cost = cost;
        this.hold = hold;
        this.unlockLevel = unlockLevel;
        this.cooldown = cooldown;
    }

    public Component displayName() {
        return Component.translatable("construct.lanterna." + id);
    }

    public Component description() {
        return Component.translatable("construct.lanterna." + id + ".desc");
    }

    @Nullable
    public static ConstructType byId(String id) {
        for (ConstructType t : values()) if (t.id.equals(id)) return t;
        return null;
    }
}
