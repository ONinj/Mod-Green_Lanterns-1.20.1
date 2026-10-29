package com.lanterna.lore.entity;

import org.jetbrains.annotations.Nullable;

/**
 * As raças dos planetas (da lore do autor) e os Guardiões do Universo, que ficam em Oa.
 * O comportamento de cada uma está em {@link RaceEntity}; as trocas em {@link RaceTrades}.
 */
public enum Race {
    //          id            planeta    larg  alt   vida  veloc  dano  translúcido
    BRAKKARI("brakkari", "ysmault", 1.3f, 2.6f, 60, 0.27, 9, false),
    GHALLOS("ghallos", "okaara", 0.6f, 1.2f, 20, 0.34, 2, false),
    SKRELL_VATH("skrell_vath", "korugar", 0.6f, 2.8f, 34, 0.31, 7, true),
    VELTARI("veltari", "oa", 0.7f, 2.7f, 50, 0.25, 6, false),
    AELINOS("aelinos", "odym", 0.6f, 2.1f, 18, 0.33, 1, true),
    VES_KAI("ves_kai", "nok", 0.6f, 2.1f, 26, 0.30, 4, false),
    ZAELITH("zaelith", "zamaron", 0.8f, 2.1f, 30, 0.27, 5, true),
    SILENTE("silente", "ryut", 0.6f, 2.1f, 26, 0.21, 5, false),
    COLETOR("coletor", "ryut", 0.6f, 2.7f, 60, 0.20, 6, false),
    GUARDIAO("guardiao", "oa", 0.7f, 2.3f, 200, 0.0, 0, false);

    public final String id;
    public final String planet;
    public final float width, height;
    public final int health;
    public final double speed;
    public final int damage;
    public final boolean translucent;

    Race(String id, String planet, float width, float height, int health, double speed, int damage, boolean translucent) {
        this.id = id;
        this.planet = planet;
        this.width = width;
        this.height = height;
        this.health = health;
        this.speed = speed;
        this.damage = damage;
        this.translucent = translucent;
    }

    /** Chave de respeito (o Coletor conta como os Silentes). */
    public String repKey() {
        return this == COLETOR ? "silente" : id;
    }

    public boolean trades() {
        return this != SILENTE && this != GUARDIAO;
    }

    @Nullable
    public static Race byId(String id) {
        for (Race r : values()) if (r.id.equals(id)) return r;
        return null;
    }

    /** A raça que mora no planeta (nas vilas, os aldeões viram ela). */
    @Nullable
    public static Race ofPlanet(String planet) {
        for (Race r : values()) if (r.planet.equals(planet) && r != COLETOR && r != GUARDIAO) return r;
        return null;
    }
}
