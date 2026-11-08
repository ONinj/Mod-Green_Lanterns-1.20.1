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
    MAO("mao", Category.CONTROL, 50, false, 4, 50),

    // ---- Construtos próprios de cada Tropa (só o anel daquela Tropa faz) ----
    ARIETE("ariete", Category.ATTACK, 80, false, 1, 60, "green"),
    PAVOR("pavor", Category.CONTROL, 100, false, 1, 160, "yellow"),
    NAPALM("napalm", Category.ATTACK, 3, true, 1, 0, "red"),
    ESPERANCA("esperanca", Category.DEFENSE, 90, false, 1, 300, "blue"),
    CRISTAL("cristal", Category.CONTROL, 110, false, 1, 200, "violet"),
    TELEPORTE("teleporte", Category.UTILITY, 60, false, 1, 120, "indigo"),
    AVAREZA("avareza", Category.CONTROL, 100, false, 1, 160, "orange"),
    CEIFA("ceifa", Category.ATTACK, 120, false, 1, 180, "black"),
    VIDA("vida", Category.DEFENSE, 150, false, 1, 300, "white"),
    /** Rastro do Mogo: mostra onde o planeta-Lanterna está agora (ele anda pelo espaço). */
    RASTRO_MOGO("rastro_mogo", Category.UTILITY, 40, false, 2, 400, "green"),

    // ---- ARVORE-INICIO (gerado) ----
    G_CANHAO("g_canhao", Category.ATTACK, 90, false, 0, 70, "green", true),
    G_LANCA("g_lanca", Category.ATTACK, 60, false, 0, 40, "green", true),
    G_METRALHADORA("g_metralhadora", Category.ATTACK, 70, false, 0, 60, "green", true),
    G_METEOROS("g_meteoros", Category.ATTACK, 160, false, 0, 240, "green", true),
    G_SENTINELA("g_sentinela", Category.CONTROL, 110, false, 0, 300, "green", true),
    G_JATO("g_jato", Category.ATTACK, 130, false, 0, 360, "green", true),
    G_GRILHOES("g_grilhoes", Category.CONTROL, 90, false, 0, 160, "green", true),
    G_CUPULA("g_cupula", Category.DEFENSE, 120, false, 0, 400, "green", true),
    G_CATAPULTA("g_catapulta", Category.UTILITY, 70, false, 0, 140, "green", true),
    G_ARMADURA("g_armadura", Category.DEFENSE, 150, false, 0, 600, "green", true),
    Y_GARRAS("y_garras", Category.ATTACK, 70, false, 0, 60, "yellow", true),
    Y_SERPENTE("y_serpente", Category.ATTACK, 60, false, 0, 60, "yellow", true),
    Y_MORCEGOS("y_morcegos", Category.CONTROL, 80, false, 0, 120, "yellow", true),
    Y_FOICES("y_foices", Category.ATTACK, 110, false, 0, 240, "yellow", true),
    Y_ARANHA("y_aranha", Category.CONTROL, 110, false, 0, 320, "yellow", true),
    Y_OLHO("y_olho", Category.CONTROL, 120, false, 0, 360, "yellow", true),
    Y_FORCA("y_forca", Category.CONTROL, 100, false, 0, 200, "yellow", true),
    Y_PRISAO("y_prisao", Category.CONTROL, 90, false, 0, 200, "yellow", true),
    Y_ESPINHOS("y_espinhos", Category.DEFENSE, 100, false, 0, 200, "yellow", true),
    Y_MASCARA("y_mascara", Category.DEFENSE, 140, false, 0, 600, "yellow", true),
    R_VOMITO("r_vomito", Category.ATTACK, 70, false, 0, 60, "red", true),
    R_PUNHO("r_punho", Category.ATTACK, 80, false, 0, 60, "red", true),
    R_COMETA("r_cometa", Category.ATTACK, 90, false, 0, 140, "red", true),
    R_CHUVA("r_chuva", Category.ATTACK, 170, false, 0, 260, "red", true),
    R_ERUPCAO("r_erupcao", Category.CONTROL, 110, false, 0, 200, "red", true),
    R_CORRENTE("r_corrente", Category.CONTROL, 80, false, 0, 120, "red", true),
    R_CAO("r_cao", Category.ATTACK, 120, false, 0, 320, "red", true),
    R_GARRAS("r_garras", Category.ATTACK, 80, false, 0, 80, "red", true),
    R_BERSERKER("r_berserker", Category.ATTACK, 120, false, 0, 500, "red", true),
    R_COURACA("r_couraca", Category.DEFENSE, 130, false, 0, 500, "red", true),
    O_MOEDAS("o_moedas", Category.ATTACK, 60, false, 0, 50, "orange", true),
    O_GARRA("o_garra", Category.ATTACK, 80, false, 0, 100, "orange", true),
    O_MINA("o_mina", Category.ATTACK, 90, false, 0, 160, "orange", true),
    O_VORTICE("o_vortice", Category.ATTACK, 160, false, 0, 300, "orange", true),
    O_SANGUESSUGA("o_sanguessuga", Category.CONTROL, 100, false, 0, 300, "orange", true),
    O_TENTACULOS("o_tentaculos", Category.CONTROL, 110, false, 0, 220, "orange", true),
    O_COFRE("o_cofre", Category.CONTROL, 100, false, 0, 220, "orange", true),
    O_AVATAR("o_avatar", Category.ATTACK, 150, false, 0, 420, "orange", true),
    O_IMA("o_ima", Category.UTILITY, 60, false, 0, 100, "orange", true),
    O_COROA("o_coroa", Category.DEFENSE, 120, false, 0, 600, "orange", true),
    B_RAIO("b_raio", Category.ATTACK, 70, false, 0, 60, "blue", true),
    B_ESTRELA("b_estrela", Category.ATTACK, 70, false, 0, 80, "blue", true),
    B_AURORA("b_aurora", Category.ATTACK, 170, false, 0, 280, "blue", true),
    B_CURA("b_cura", Category.DEFENSE, 90, false, 0, 200, "blue", true),
    B_EGIDE("b_egide", Category.DEFENSE, 120, false, 0, 400, "blue", true),
    B_ASAS("b_asas", Category.UTILITY, 80, false, 0, 400, "blue", true),
    B_RENASCER("b_renascer", Category.DEFENSE, 200, false, 0, 1200, "blue", true),
    B_ANJO("b_anjo", Category.DEFENSE, 130, false, 0, 400, "blue", true),
    B_SANTUARIO("b_santuario", Category.DEFENSE, 140, false, 0, 400, "blue", true),
    B_PURIFICAR("b_purificar", Category.UTILITY, 100, false, 0, 240, "blue", true),
    I_OLHAR("i_olhar", Category.CONTROL, 60, false, 0, 80, "indigo", true),
    I_BASTAO("i_bastao", Category.ATTACK, 90, false, 0, 220, "indigo", true),
    I_TROCA("i_troca", Category.UTILITY, 70, false, 0, 140, "indigo", true),
    I_LAGRIMAS("i_lagrimas", Category.DEFENSE, 150, false, 0, 260, "indigo", true),
    I_ELO("i_elo", Category.CONTROL, 90, false, 0, 200, "indigo", true),
    I_RAIZES("i_raizes", Category.CONTROL, 100, false, 0, 200, "indigo", true),
    I_ESPIRITO("i_espirito", Category.DEFENSE, 120, false, 0, 400, "indigo", true),
    I_SILENCIO("i_silencio", Category.CONTROL, 140, false, 0, 400, "indigo", true),
    I_TOQUE("i_toque", Category.DEFENSE, 90, false, 0, 200, "indigo", true),
    I_ESPELHO("i_espelho", Category.DEFENSE, 130, false, 0, 500, "indigo", true),
    V_FLECHA("v_flecha", Category.CONTROL, 60, false, 0, 60, "violet", true),
    V_LANCA("v_lanca", Category.ATTACK, 80, false, 0, 60, "violet", true),
    V_LACO("v_laco", Category.ATTACK, 110, false, 0, 240, "violet", true),
    V_PAIXAO("v_paixao", Category.ATTACK, 170, false, 0, 320, "violet", true),
    V_JARDIM("v_jardim", Category.CONTROL, 100, false, 0, 200, "violet", true),
    V_ABRACO("v_abraco", Category.CONTROL, 90, false, 0, 180, "violet", true),
    V_CAVALEIRO("v_cavaleiro", Category.DEFENSE, 130, false, 0, 400, "violet", true),
    V_CORACOES("v_coracoes", Category.CONTROL, 150, false, 0, 300, "violet", true),
    V_NINHO("v_ninho", Category.DEFENSE, 110, false, 0, 360, "violet", true),
    V_PAR("v_par", Category.DEFENSE, 120, false, 0, 400, "violet", true);
    // ---- ARVORE-FIM ----

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
    /** Tropa dona do construto (path: "green"...), ou null se qualquer anel faz. */
    @Nullable public final String corps;
    /** Precisa ser desbloqueado na Árvore da Luz. */
    public final boolean arvore;

    ConstructType(String id, Category category, int cost, boolean hold, int unlockLevel, int cooldown) {
        this(id, category, cost, hold, unlockLevel, cooldown, null);
    }

    ConstructType(String id, Category category, int cost, boolean hold, int unlockLevel, int cooldown, @Nullable String corps) {
        this(id, category, cost, hold, unlockLevel, cooldown, corps, false);
    }

    ConstructType(String id, Category category, int cost, boolean hold, int unlockLevel, int cooldown, @Nullable String corps, boolean arvore) {
        this.arvore = arvore;
        this.id = id;
        this.category = category;
        this.cost = cost;
        this.hold = hold;
        this.unlockLevel = unlockLevel;
        this.cooldown = cooldown;
        this.corps = corps;
    }

    /** Este anel (Tropa) pode fazer o construto? */
    public boolean disponivel(@Nullable net.minecraft.resources.ResourceLocation ringCorps) {
        return corps == null || (ringCorps != null && ringCorps.getPath().equals(corps));
    }

    /** Construtos que este anel mostra na roda: os de todos + o próprio da Tropa. */
    public static java.util.List<ConstructType> daRoda(@Nullable net.minecraft.resources.ResourceLocation ringCorps) {
        java.util.List<ConstructType> l = new java.util.ArrayList<>();
        for (ConstructType t : values()) if (t.disponivel(ringCorps)) l.add(t);
        return l;
    }

    /** O construto próprio da Tropa (ou null). */
    @Nullable
    public static ConstructType daTropa(@Nullable net.minecraft.resources.ResourceLocation ringCorps) {
        for (ConstructType t : values()) if (t.corps != null && t.disponivel(ringCorps)) return t;
        return null;
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
