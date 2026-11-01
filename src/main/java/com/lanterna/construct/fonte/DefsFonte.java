package com.lanterna.construct.fonte;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** GERADO por gerador/arvore/gen.py a partir de spec.py — não edite à mão. Comportamento de cada construto da Árvore da Luz. */
public final class DefsFonte {
    public enum Comp { PROJETIL, SALVA, CHUVA, RAIO, PULSO, AREA, ARMADILHA, INVOCACAO, ORBITA, BARREIRA, BUFF, MOVIMENTO, CORRENTE }

    public record Ef(String efeito, int ticks, int nivel) {}

    public record Def(String id, String corps, Comp comp, float dano, float raio, int dur, int qtd, float vel, int alcance, Set<String> flags,
                      List<Ef> inimigos, List<Ef> aliados, float cura, String som, int tier) {
        public boolean tem(String f) { return flags.contains(f); }
    }

    private static final Map<String, Def> DEFS = new HashMap<>();

    public static Def get(String id) { return DEFS.get(id); }

    private static void d(Def d) { DEFS.put(d.id(), d); }

    static {
        d(new Def("g_canhao", "green", Comp.PROJETIL, 16f, 3.5f, 0, 1, 1.3f, 24, Set.of("explode"),
                List.of(), List.of(), 0f, "GENERIC_EXPLODE", 2));
        d(new Def("g_lanca", "green", Comp.PROJETIL, 12f, 0f, 0, 1, 2.6f, 24, Set.of("perfura"),
                List.of(), List.of(), 0f, "TRIDENT_THROW", 3));
        d(new Def("g_metralhadora", "green", Comp.SALVA, 3f, 0f, 0, 12, 2.4f, 24, Set.of(),
                List.of(), List.of(), 0f, "CROSSBOW_SHOOT", 5));
        d(new Def("g_meteoros", "green", Comp.CHUVA, 9f, 5f, 0, 10, 1.0f, 24, Set.of("explode"),
                List.of(), List.of(), 0f, "FIREWORK_ROCKET_LARGE_BLAST", 7));
        d(new Def("g_sentinela", "green", Comp.INVOCACAO, 6f, 0f, 500, 1, 0.35f, 24, Set.of(),
                List.of(), List.of(), 0f, "IRON_GOLEM_REPAIR", 2));
        d(new Def("g_jato", "green", Comp.INVOCACAO, 4f, 0f, 300, 1, 0.8f, 24, Set.of("voa", "atira"),
                List.of(), List.of(), 0f, "FIREWORK_ROCKET_LAUNCH", 3));
        d(new Def("g_grilhoes", "green", Comp.CORRENTE, 3f, 6f, 100, 4, 1.0f, 24, Set.of(),
                List.of(new Ef("root", 100, 0), new Ef("slow", 100, 3)), List.of(), 0f, "CHAIN_PLACE", 5));
        d(new Def("g_cupula", "green", Comp.BARREIRA, 0f, 4f, 500, 1, 1.0f, 24, Set.of("cupula"),
                List.of(), List.of(), 0f, "BEACON_ACTIVATE", 2));
        d(new Def("g_catapulta", "green", Comp.MOVIMENTO, 8f, 4f, 0, 1, 2.4f, 24, Set.of("salto"),
                List.of(), List.of(), 0f, "RAVAGER_ROAR", 4));
        d(new Def("g_armadura", "green", Comp.BUFF, 0f, 0f, 400, 1, 1.0f, 24, Set.of("so_eu"),
                List.of(), List.of(new Ef("res", 400, 1), new Ef("str", 400, 1)), 0f, "ARMOR_EQUIP_NETHERITE", 6));
        d(new Def("y_garras", "yellow", Comp.PULSO, 11f, 5f, 0, 1, 1.0f, 24, Set.of("cone", "pavor"),
                List.of(), List.of(), 0f, "WARDEN_ATTACK_IMPACT", 2));
        d(new Def("y_serpente", "yellow", Comp.PROJETIL, 8f, 0f, 0, 1, 0.9f, 24, Set.of("teleguiado"),
                List.of(new Ef("poison", 100, 1)), List.of(), 0f, "EVOKER_PREPARE_ATTACK", 3));
        d(new Def("y_morcegos", "yellow", Comp.SALVA, 4f, 0f, 0, 6, 0.9f, 24, Set.of("teleguiado"),
                List.of(new Ef("blind", 60, 0)), List.of(), 0f, "BAT_TAKEOFF", 5));
        d(new Def("y_foices", "yellow", Comp.ORBITA, 6f, 3f, 200, 3, 1.0f, 24, Set.of(),
                List.of(), List.of(), 0f, "PLAYER_ATTACK_SWEEP", 7));
        d(new Def("y_aranha", "yellow", Comp.INVOCACAO, 5f, 0f, 400, 1, 0.4f, 24, Set.of(),
                List.of(new Ef("slow", 60, 2), new Ef("poison", 60, 0)), List.of(), 0f, "SPIDER_AMBIENT", 2));
        d(new Def("y_olho", "yellow", Comp.INVOCACAO, 4f, 12f, 400, 1, 1.0f, 24, Set.of("torre"),
                List.of(), List.of(), 0f, "GUARDIAN_ATTACK", 3));
        d(new Def("y_forca", "yellow", Comp.CORRENTE, 3f, 0f, 80, 1, 1.0f, 24, Set.of(),
                List.of(new Ef("levit", 60, 0), new Ef("wither", 80, 1)), List.of(), 0f, "CHAIN_BREAK", 5));
        d(new Def("y_prisao", "yellow", Comp.ARMADILHA, 6f, 3f, 1200, 1, 1.0f, 24, Set.of("pavor"),
                List.of(new Ef("root", 100, 0), new Ef("dark", 120, 0), new Ef("slow", 120, 3)), List.of(), 0f, "SCULK_SHRIEKER_SHRIEK", 7));
        d(new Def("y_espinhos", "yellow", Comp.AREA, 3f, 5f, 160, 1, 1.0f, 24, Set.of(),
                List.of(new Ef("slow", 40, 2)), List.of(), 0f, "POINTED_DRIPSTONE_LAND", 2));
        d(new Def("y_mascara", "yellow", Comp.BUFF, 0f, 6f, 400, 1, 1.0f, 24, Set.of("so_eu", "aura", "pavor"),
                List.of(new Ef("weak", 60, 1)), List.of(new Ef("str", 400, 1), new Ef("res", 400, 0)), 0f, "WARDEN_EMERGE", 4));
        d(new Def("r_vomito", "red", Comp.PULSO, 9f, 7f, 0, 1, 1.0f, 24, Set.of("cone", "fogo"),
                List.of(), List.of(), 0f, "BLAZE_SHOOT", 2));
        d(new Def("r_punho", "red", Comp.PROJETIL, 14f, 3f, 0, 1, 1.4f, 24, Set.of("explode", "fogo"),
                List.of(), List.of(), 0f, "FIRECHARGE_USE", 3));
        d(new Def("r_cometa", "red", Comp.MOVIMENTO, 10f, 2.5f, 0, 1, 2.6f, 24, Set.of("investida", "fogo"),
                List.of(), List.of(), 0f, "BLAZE_SHOOT", 5));
        d(new Def("r_chuva", "red", Comp.CHUVA, 8f, 6f, 0, 14, 1.0f, 24, Set.of("fogo"),
                List.of(), List.of(), 0f, "FIRE_AMBIENT", 7));
        d(new Def("r_erupcao", "red", Comp.AREA, 4f, 4f, 120, 1, 1.0f, 24, Set.of("fogo", "arremessa"),
                List.of(), List.of(), 0f, "LAVA_POP", 2));
        d(new Def("r_corrente", "red", Comp.CORRENTE, 6f, 0f, 30, 1, 1.0f, 24, Set.of("puxa", "fogo"),
                List.of(), List.of(), 0f, "CHAIN_PLACE", 3));
        d(new Def("r_cao", "red", Comp.INVOCACAO, 7f, 0f, 400, 1, 0.5f, 24, Set.of("fogo"),
                List.of(), List.of(), 0f, "WOLF_GROWL", 5));
        d(new Def("r_garras", "red", Comp.PULSO, 8f, 4f, 0, 1, 1.0f, 24, Set.of("roubo"),
                List.of(), List.of(), 0f, "PLAYER_ATTACK_CRIT", 2));
        d(new Def("r_berserker", "red", Comp.BUFF, 0f, 0f, 300, 1, 1.0f, 24, Set.of("so_eu", "custo_vida"),
                List.of(), List.of(new Ef("str", 300, 2), new Ef("speed", 300, 1), new Ef("res", 300, 0)), 0f, "RAVAGER_ROAR", 4));
        d(new Def("r_couraca", "red", Comp.BUFF, 0f, 0f, 400, 1, 1.0f, 24, Set.of("so_eu", "espelho"),
                List.of(), List.of(new Ef("res", 400, 0)), 0f, "ARMOR_EQUIP_IRON", 6));
        d(new Def("o_moedas", "orange", Comp.SALVA, 3f, 0f, 0, 10, 2.2f, 24, Set.of(),
                List.of(), List.of(), 0f, "CHAIN_HIT", 2));
        d(new Def("o_garra", "orange", Comp.CORRENTE, 6f, 0f, 40, 1, 1.0f, 24, Set.of("puxa", "roubo", "rouba_carga"),
                List.of(), List.of(), 0f, "EVOKER_CAST_SPELL", 3));
        d(new Def("o_mina", "orange", Comp.ARMADILHA, 18f, 3.5f, 1200, 1, 1.0f, 24, Set.of("explode"),
                List.of(), List.of(), 0f, "GENERIC_EXPLODE", 5));
        d(new Def("o_vortice", "orange", Comp.AREA, 4f, 7f, 120, 1, 1.0f, 24, Set.of("puxa", "roubo"),
                List.of(), List.of(), 0f, "ENDER_DRAGON_FLAP", 7));
        d(new Def("o_sanguessuga", "orange", Comp.INVOCACAO, 4f, 0f, 360, 1, 0.45f, 24, Set.of("roubo", "voa"),
                List.of(), List.of(), 0f, "SLIME_SQUISH", 2));
        d(new Def("o_tentaculos", "orange", Comp.AREA, 3f, 5f, 140, 1, 1.0f, 24, Set.of("roubo"),
                List.of(new Ef("root", 30, 0), new Ef("slow", 40, 2)), List.of(), 0f, "EVOKER_FANGS_ATTACK", 3));
        d(new Def("o_cofre", "orange", Comp.BARREIRA, 0f, 1f, 120, 1, 1.0f, 24, Set.of("caixa_alvo"),
                List.of(), List.of(), 0f, "IRON_DOOR_CLOSE", 5));
        d(new Def("o_avatar", "orange", Comp.INVOCACAO, 9f, 0f, 400, 1, 0.4f, 24, Set.of("roubo", "grande"),
                List.of(), List.of(), 0f, "RAVAGER_ATTACK", 7));
        d(new Def("o_ima", "orange", Comp.PULSO, 2f, 10f, 0, 1, 1.0f, 24, Set.of("puxa", "itens"),
                List.of(), List.of(), 0f, "BEACON_POWER_SELECT", 2));
        d(new Def("o_coroa", "orange", Comp.BUFF, 0f, 0f, 600, 1, 1.0f, 24, Set.of("so_eu", "itens"),
                List.of(), List.of(new Ef("absorb", 600, 2), new Ef("luck", 600, 1)), 0f, "ARMOR_EQUIP_GOLD", 4));
        d(new Def("b_raio", "blue", Comp.RAIO, 10f, 0f, 0, 1, 1.0f, 32, Set.of("anti_morto"),
                List.of(), List.of(), 0f, "BEACON_ACTIVATE", 2));
        d(new Def("b_estrela", "blue", Comp.PROJETIL, 12f, 2.5f, 0, 1, 1.1f, 24, Set.of("teleguiado", "explode", "anti_morto"),
                List.of(), List.of(), 0f, "FIREWORK_ROCKET_TWINKLE", 3));
        d(new Def("b_aurora", "blue", Comp.CHUVA, 8f, 7f, 0, 12, 1.0f, 24, Set.of("cura_aliado", "anti_morto"),
                List.of(), List.of(), 4f, "AMETHYST_BLOCK_CHIME", 5));
        d(new Def("b_cura", "blue", Comp.PULSO, 0f, 8f, 0, 1, 1.0f, 24, Set.of("cura_aliado", "so_aliados"),
                List.of(), List.of(new Ef("regen", 120, 1)), 10f, "BEACON_ACTIVATE", 2));
        d(new Def("b_egide", "blue", Comp.BUFF, 0f, 8f, 400, 1, 1.0f, 24, Set.of(),
                List.of(), List.of(new Ef("absorb", 400, 2), new Ef("res", 400, 0)), 0f, "BEACON_POWER_SELECT", 4));
        d(new Def("b_asas", "blue", Comp.BUFF, 0f, 0f, 600, 1, 1.0f, 24, Set.of("so_eu"),
                List.of(), List.of(new Ef("speed", 600, 1), new Ef("jump", 600, 2), new Ef("slowfall", 600, 0)), 0f, "ELYTRA_FLYING", 2));
        d(new Def("b_renascer", "blue", Comp.BUFF, 0f, 0f, 1200, 1, 1.0f, 24, Set.of("so_eu", "antimorte"),
                List.of(), List.of(), 0f, "TOTEM_USE", 6));
        d(new Def("b_anjo", "blue", Comp.INVOCACAO, 5f, 0f, 500, 1, 0.4f, 24, Set.of("voa", "cura_dono", "anti_morto"),
                List.of(), List.of(), 3f, "BEACON_AMBIENT", 3));
        d(new Def("b_santuario", "blue", Comp.AREA, 2f, 6f, 240, 1, 1.0f, 24, Set.of("cura_aliado", "empurra", "anti_morto"),
                List.of(), List.of(), 2f, "BEACON_AMBIENT", 5));
        d(new Def("b_purificar", "blue", Comp.PULSO, 8f, 9f, 0, 1, 1.0f, 24, Set.of("purifica", "anti_morto"),
                List.of(), List.of(), 0f, "TOTEM_USE", 7));
        d(new Def("i_olhar", "indigo", Comp.RAIO, 6f, 0f, 0, 1, 1.0f, 28, Set.of("acalma"),
                List.of(new Ef("nausea", 120, 0), new Ef("weak", 160, 1)), List.of(), 0f, "ILLUSIONER_CAST_SPELL", 2));
        d(new Def("i_bastao", "indigo", Comp.ORBITA, 5f, 2.6f, 200, 2, 1.0f, 24, Set.of("empurra"),
                List.of(), List.of(), 0f, "PLAYER_ATTACK_KNOCKBACK", 3));
        d(new Def("i_troca", "indigo", Comp.MOVIMENTO, 4f, 0f, 0, 1, 1.0f, 30, Set.of("troca"),
                List.of(new Ef("nausea", 100, 0), new Ef("slow", 60, 1)), List.of(), 0f, "ENDERMAN_TELEPORT", 5));
        d(new Def("i_lagrimas", "indigo", Comp.CHUVA, 4f, 6f, 0, 14, 1.0f, 24, Set.of("cura_aliado"),
                List.of(new Ef("weak", 100, 0)), List.of(), 3f, "AMETHYST_BLOCK_CHIME", 7));
        d(new Def("i_elo", "indigo", Comp.CORRENTE, 2f, 0f, 200, 1, 1.0f, 24, Set.of("espelho_alvo"),
                List.of(), List.of(), 0f, "CHAIN_PLACE", 2));
        d(new Def("i_raizes", "indigo", Comp.AREA, 2f, 5f, 80, 1, 1.0f, 24, Set.of(),
                List.of(new Ef("root", 80, 0), new Ef("slow", 80, 4)), List.of(), 0f, "ROOTS_PLACE", 3));
        d(new Def("i_espirito", "indigo", Comp.INVOCACAO, 3f, 0f, 500, 1, 0.5f, 24, Set.of("voa", "cura_dono"),
                List.of(new Ef("slow", 40, 2)), List.of(), 2f, "ALLAY_AMBIENT_WITH_ITEM", 5));
        d(new Def("i_silencio", "indigo", Comp.PULSO, 4f, 9f, 0, 1, 1.0f, 24, Set.of("acalma", "silencio"),
                List.of(new Ef("slow", 100, 2), new Ef("weak", 100, 1)), List.of(), 0f, "WARDEN_SONIC_BOOM", 7));
        d(new Def("i_toque", "indigo", Comp.PULSO, 0f, 7f, 0, 1, 1.0f, 24, Set.of("cura_aliado", "acalma"),
                List.of(), List.of(new Ef("regen", 100, 0)), 8f, "AMETHYST_BLOCK_RESONATE", 2));
        d(new Def("i_espelho", "indigo", Comp.BUFF, 0f, 0f, 300, 1, 1.0f, 24, Set.of("so_eu", "espelho"),
                List.of(), List.of(new Ef("res", 300, 0)), 0f, "AMETHYST_CLUSTER_PLACE", 4));
        d(new Def("v_flecha", "violet", Comp.PROJETIL, 6f, 0f, 0, 1, 2.2f, 24, Set.of("encanto"),
                List.of(), List.of(), 0f, "ARROW_SHOOT", 2));
        d(new Def("v_lanca", "violet", Comp.PROJETIL, 13f, 0f, 0, 1, 2.4f, 24, Set.of("perfura"),
                List.of(), List.of(), 0f, "AMETHYST_CLUSTER_BREAK", 3));
        d(new Def("v_laco", "violet", Comp.ORBITA, 5f, 3.2f, 240, 4, 1.0f, 24, Set.of(),
                List.of(), List.of(), 0f, "AMETHYST_BLOCK_CHIME", 5));
        d(new Def("v_paixao", "violet", Comp.PULSO, 14f, 8f, 0, 1, 1.0f, 24, Set.of("encanto"),
                List.of(), List.of(), 0f, "GENERIC_EXPLODE", 7));
        d(new Def("v_jardim", "violet", Comp.AREA, 2f, 5f, 160, 1, 1.0f, 24, Set.of(),
                List.of(new Ef("root", 40, 0), new Ef("slow", 60, 3)), List.of(), 0f, "AMETHYST_CLUSTER_PLACE", 2));
        d(new Def("v_abraco", "violet", Comp.CORRENTE, 3f, 0f, 100, 1, 1.0f, 24, Set.of("roubo"),
                List.of(new Ef("root", 100, 0)), List.of(), 0f, "AMETHYST_BLOCK_RESONATE", 3));
        d(new Def("v_cavaleiro", "violet", Comp.INVOCACAO, 6f, 0f, 500, 1, 0.38f, 24, Set.of(),
                List.of(), List.of(), 0f, "AMETHYST_BLOCK_STEP", 5));
        d(new Def("v_coracoes", "violet", Comp.CHUVA, 3f, 6f, 0, 14, 1.0f, 24, Set.of("cura_aliado", "encanto"),
                List.of(), List.of(), 3f, "AMETHYST_BLOCK_CHIME", 7));
        d(new Def("v_ninho", "violet", Comp.BARREIRA, 0f, 5f, 500, 1, 1.0f, 24, Set.of("anel"),
                List.of(), List.of(), 0f, "AMETHYST_CLUSTER_PLACE", 2));
        d(new Def("v_par", "violet", Comp.BUFF, 0f, 10f, 400, 1, 1.0f, 24, Set.of("par"),
                List.of(), List.of(new Ef("res", 400, 1), new Ef("regen", 400, 0)), 0f, "BEACON_POWER_SELECT", 4));
    }

    private DefsFonte() {}
}
