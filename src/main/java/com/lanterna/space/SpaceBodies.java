package com.lanterna.space;

import com.lanterna.LanternaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * O mapa do espaço. A Terra fica embaixo de onde você chega; os planetas das Tropas
 * ficam espalhados em volta. Mais longe, o espaço é cheio de planetas gerados
 * ({@link ProceduralPlanets}) — quanto mais você voa, mais planetas encontra.
 *
 * Cada corpo tem: posição no espaço, raio, cor da atmosfera, textura e o mundo (dimensão)
 * para onde você vai ao entrar nele.
 */
public final class SpaceBodies {
    public static final ResourceKey<Level> SPACE = ResourceKey.create(Registries.DIMENSION, LanternaMod.id("space"));

    /** Dados extras de um planeta gerado. */
    public record Procedural(String name, Archetype type, long seed, int regionX, int regionZ) {}

    public record Body(String id, Vec3 center, double radius, int atmosphere, boolean landable,
                       @Nullable ResourceKey<Level> dimension, boolean breathable, @Nullable Procedural procedural) {

        public Body(String id, Vec3 center, double radius, int atmosphere, boolean landable,
                    @Nullable ResourceKey<Level> dimension, boolean breathable) {
            this(id, center, radius, atmosphere, landable, dimension, breathable, null);
        }

        /** O Mogo anda pelo espaço (MogoOrbita); os outros corpos ficam parados. */
        @Override
        public Vec3 center() {
            if ("mogo".equals(id)) return com.lanterna.mogo.MogoOrbita.agora();
            if ("hanks".equals(id)) return com.lanterna.hanks.HanksOrbita.agora();
            return center;
        }

        /** Planeta de um Lanterna lendário (região fixa num mundo de tipo; textura própria). */
        public boolean lendario() {
            return id.startsWith("lend_");
        }

        public ResourceLocation texture() {
            if (procedural != null && !lendario()) return LanternaMod.id("dynamic/planet/" + id);
            return LanternaMod.id("textures/space/" + id + ".png");
        }

        public String nameKey() {
            return "planet.lanterna." + id;
        }

        public Component displayName() {
            if (procedural != null && !lendario()) return Component.literal(procedural.name());
            return Component.translatable(nameKey());
        }
    }

    private static ResourceKey<Level> planet(String id) {
        return ResourceKey.create(Registries.DIMENSION, LanternaMod.id(id));
    }

    public static final Body EARTH = new Body("earth", new Vec3(0, -1000, 0), 600, 0x6FB5FF, true, Level.OVERWORLD, true);

    public static final List<Body> BODIES = List.of(
            EARTH,
            new Body("oa",       new Vec3(10000, 150, -7200),  220, 0x5CFF7A, true, planet("oa"), true),
            new Body("korugar",  new Vec3(-12800, 250, -10400), 200, 0xFFD24A, true, planet("korugar"), true),
            new Body("ysmault",  new Vec3(16800, -150, 10400),  180, 0xFF3B30, true, planet("ysmault"), true),
            new Body("odym",     new Vec3(-8000, 100, 16800),  230, 0x7FC8FF, true, planet("odym"), true),
            new Body("zamaron",  new Vec3(3600, 300, 20800),    190, 0xE58BFF, true, planet("zamaron"), true),
            new Body("nok",      new Vec3(-22400, -100, 2400),  170, 0x7B6BFF, true, planet("nok"), true),
            new Body("okaara",   new Vec3(24800, 200, -1600),   240, 0xFFA040, true, planet("okaara"), true),
            new Body("ryut",     new Vec3(-5600, -200, -25600), 160, 0x8A8A9A, true, planet("ryut"), false),
            // Gênese: onde o Coração Primordial se partiu. Escondido até o jogador juntar os sete anéis da fusão.
            new Body("genese",   new Vec3(-1200, 700, 10400),   140, 0xF4FFF4, true, planet("genese"), true),
            // Mogo: o planeta que é um Lanterna Verde (o maior corpo fixo do mapa)
            new Body("mogo",     new Vec3(-4600, 350, -3800), 280, 0x3CFF7A, true, planet("mogo"), true),
            // Hanks: o planeta-máquina dos Amarelos (também anda pelo espaço; a cidadela do Sinestro)
            new Body("hanks",    new Vec3(42000, 200, -30000), 300, 0xFFC830, true, planet("hanks"), false)
    );

    /**
     * De onde vem a luz que ilumina os planetas no céu. Não é mais um corpo do mapa (o Sol gigante que não dava para
     * alcançar saiu em 0.47.0): só a direção da luz.
     */
    public static final Body SUN = new Body("sun", new Vec3(30000, 9000, 20000), 4200, 0xFFF4C0, false, null, false);

    /** Planetas fixos + os dos Lanternas lendários. */
    public static final List<Body> TODOS = juntar();

    private static List<Body> juntar() {
        List<Body> l = new ArrayList<>(BODIES);
        l.addAll(com.lanterna.lendarios.PlanetasLendarios.CORPOS);
        return List.copyOf(l);
    }

    /** Corpo que só aparece para quem já revelou (Gênese). */
    public static boolean isHidden(String id) {
        return "genese".equals(id);
    }

    /** Onde você aparece ao subir da Terra (o ponto bate com onde você estava no mundo normal). */
    public static Vec3 arrivalFromEarth(double overworldX, double overworldZ) {
        return new Vec3(overworldX / 64.0, -300, overworldZ / 64.0);
    }

    /** Corpos fixos + planetas gerados em volta da posição (raio em setores de 8.000 blocos). */
    public static List<Body> near(Vec3 pos, int sectorRadius) {
        List<Body> list = new ArrayList<>(TODOS);
        list.addAll(ProceduralPlanets.near(pos, sectorRadius));
        return list;
    }

    /** Planeta fixo cujo mundo é este. */
    @Nullable
    public static Body byDimension(ResourceKey<Level> dimension) {
        for (Body b : BODIES) if (dimension.equals(b.dimension())) return b;
        return null;
    }

    /** O corpo celeste onde o jogador está (nos mundos dos planetas gerados, depende da região x/z). */
    @Nullable
    public static Body bodyAt(ResourceKey<Level> dimension, double x, double z) {
        Body fixed = byDimension(dimension);
        if (fixed != null) return fixed;
        Body lend = com.lanterna.lendarios.PlanetasLendarios.noMundo(dimension, x, z);
        if (lend != null) return lend;
        Archetype type = Archetype.byDimension(dimension);
        return type != null ? ProceduralPlanets.byRegion(type, x, z) : null;
    }

    @Nullable
    public static Body byId(String id) {
        for (Body b : TODOS) if (b.id().equals(id)) return b;
        return ProceduralPlanets.byId(id);
    }

    /** Em que corpo celeste a posição do espaço está (para pousar). */
    @Nullable
    public static Body inside(Vec3 pos) {
        for (Body b : near(pos, 1)) {
            if (b.landable() && pos.distanceToSqr(b.center()) < b.radius() * b.radius()) return b;
        }
        return null;
    }

    /** Mundo de algum planeta (fixo ou gerado), menos a Terra. */
    public static boolean isPlanet(ResourceKey<Level> dimension) {
        Body b = byDimension(dimension);
        return (b != null && b != EARTH) || Archetype.byDimension(dimension) != null;
    }

    private SpaceBodies() {}
}
