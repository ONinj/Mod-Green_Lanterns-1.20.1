package com.lanterna.space;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Planetas gerados: o espaço é dividido em setores de {@link #SECTOR} blocos. Cada setor pode ter
 * um planeta, sempre o mesmo para a mesma semente do mundo (todo mundo no servidor vê os mesmos).
 * Quanto mais você voa, mais planetas aparecem — o espaço não acaba.
 *
 * Cada planeta tem nome, tipo, tamanho e cor próprios. O terreno fica no mundo do tipo dele,
 * numa região só dele ({@link #REGION} blocos entre uma região e outra).
 */
public final class ProceduralPlanets {
    /** Tamanho de um setor do espaço (um planeta no máximo por setor). */
    public static final int SECTOR = 8000;
    /** Distância entre as regiões dos planetas dentro do mundo de cada tipo. */
    public static final int REGION = 50000;
    /** Perto do centro ficam só os planetas fixos (Terra e os planetas das Tropas). */
    private static final double CORE_RADIUS = 9000;
    private static final float CHANCE = 0.85f;

    private static volatile long seed;
    private static final Map<Long, Optional<SpaceBodies.Body>> CACHE = new ConcurrentHashMap<>();

    public static void setSeed(long newSeed) {
        if (newSeed != seed) {
            seed = newSeed;
            CACHE.clear();
            com.lanterna.alien.AlienSpecies.clearCache();
        }
    }

    public static long seed() {
        return seed;
    }

    /** O planeta do setor (ou nenhum). */
    public static SpaceBodies.Body inSector(int sx, int sz) {
        long key = ((long) sx << 32) ^ (sz & 0xFFFFFFFFL);
        return CACHE.computeIfAbsent(key, k -> Optional.ofNullable(generate(sx, sz))).orElse(null);
    }

    private static SpaceBodies.Body generate(int sx, int sz) {
        Random r = new Random(seed * 6364136223846793005L + sx * 341873128712L + sz * 132897987541L + 0x5DEECE66DL);
        if (r.nextFloat() > CHANCE) return null;
        double cx = sx * (double) SECTOR + SECTOR / 2.0 + (r.nextDouble() - 0.5) * SECTOR * 0.6;
        double cz = sz * (double) SECTOR + SECTOR / 2.0 + (r.nextDouble() - 0.5) * SECTOR * 0.6;
        if (Math.hypot(cx, cz) < CORE_RADIUS) return null;
        Vec3 center = new Vec3(cx, -200 + r.nextDouble() * 450, cz);
        if (center.distanceTo(SpaceBodies.SUN.center()) < SpaceBodies.SUN.radius() + 4000) return null;
        double radius = 140 + r.nextDouble() * 180;
        Archetype type = Archetype.pick(r);
        String name = PlanetNames.generate(r);
        int atmosphere = jitter(type.atmosphere, r);
        String id = "p" + (sx < 0 ? "m" + -sx : sx) + "_" + (sz < 0 ? "m" + -sz : sz);
        long planetSeed = r.nextLong();
        var proc = new SpaceBodies.Procedural(name, type, planetSeed, sx * REGION, sz * REGION);
        return new SpaceBodies.Body(id, center, radius, atmosphere, true, type.dimension, type.breathable, proc);
    }

    /** Planetas gerados nos setores em volta da posição (raio em setores). */
    public static List<SpaceBodies.Body> near(Vec3 pos, int sectorRadius) {
        int sx = Math.floorDiv((int) Math.floor(pos.x), SECTOR);
        int sz = Math.floorDiv((int) Math.floor(pos.z), SECTOR);
        List<SpaceBodies.Body> list = new ArrayList<>();
        for (int dx = -sectorRadius; dx <= sectorRadius; dx++) {
            for (int dz = -sectorRadius; dz <= sectorRadius; dz++) {
                SpaceBodies.Body b = inSector(sx + dx, sz + dz);
                if (b != null) list.add(b);
            }
        }
        return list;
    }

    /** Em que planeta gerado o jogador está, pela posição dentro do mundo do tipo. */
    public static SpaceBodies.Body byRegion(Archetype type, double x, double z) {
        int sx = (int) Math.round(x / REGION), sz = (int) Math.round(z / REGION);
        SpaceBodies.Body b = inSector(sx, sz);
        return b != null && b.procedural() != null && b.procedural().type() == type ? b : null;
    }

    /** Ponto do espaço correspondente a uma região (para quem andou para fora do planeta). */
    public static Vec3 spacePointForRegion(double x, double z) {
        int sx = (int) Math.round(x / REGION), sz = (int) Math.round(z / REGION);
        return new Vec3(sx * (double) SECTOR + SECTOR / 2.0, 0, sz * (double) SECTOR + SECTOR / 2.0);
    }

    public static SpaceBodies.Body byId(String id) {
        if (!id.startsWith("p")) return null;
        try {
            String[] parts = id.substring(1).split("_");
            int sx = parts[0].startsWith("m") ? -Integer.parseInt(parts[0].substring(1)) : Integer.parseInt(parts[0]);
            int sz = parts[1].startsWith("m") ? -Integer.parseInt(parts[1].substring(1)) : Integer.parseInt(parts[1]);
            return inSector(sx, sz);
        } catch (Exception e) {
            return null;
        }
    }

    private static int jitter(int rgb, Random r) {
        int red = clamp(((rgb >> 16) & 0xFF) + r.nextInt(41) - 20);
        int green = clamp(((rgb >> 8) & 0xFF) + r.nextInt(41) - 20);
        int blue = clamp((rgb & 0xFF) + r.nextInt(41) - 20);
        return (red << 16) | (green << 8) | blue;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private ProceduralPlanets() {}
}
