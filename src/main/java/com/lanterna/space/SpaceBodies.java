package com.lanterna.space;

import com.lanterna.LanternaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * O mapa do espaço. A Terra fica embaixo de onde você chega; os planetas das Tropas
 * ficam espalhados em volta, cada vez mais longe (e mais perigosos).
 *
 * Cada corpo tem: posição no espaço, raio, cor da atmosfera, textura e o mundo (dimensão)
 * para onde você vai ao entrar nele.
 */
public final class SpaceBodies {
    public static final ResourceKey<Level> SPACE = ResourceKey.create(Registries.DIMENSION, LanternaMod.id("space"));

    public record Body(String id, Vec3 center, double radius, int atmosphere, boolean landable,
                       @Nullable ResourceKey<Level> dimension, boolean breathable) {
        public ResourceLocation texture() {
            return LanternaMod.id("textures/space/" + id + ".png");
        }

        public String nameKey() {
            return "planet.lanterna." + id;
        }

        public net.minecraft.network.chat.Component displayName() {
            return net.minecraft.network.chat.Component.translatable(nameKey());
        }
    }

    /** Todos os corpos celestes (fixos). */
    public static List<Body> allBodies() {
        return BODIES;
    }

    /** O corpo celeste cujo mundo é este (e, nos mundos compartilhados, a região x/z). */
    @Nullable
    public static Body bodyAt(ResourceKey<Level> dimension, double x, double z) {
        return byDimension(dimension);
    }

    private static ResourceKey<Level> planet(String id) {
        return ResourceKey.create(Registries.DIMENSION, LanternaMod.id(id));
    }

    public static final Body EARTH = new Body("earth", new Vec3(0, -1000, 0), 600, 0x6FB5FF, true, Level.OVERWORLD, true);

    public static final List<Body> BODIES = List.of(
            EARTH,
            new Body("oa",       new Vec3(2500, 150, -1800),  220, 0x5CFF7A, true, planet("oa"), true),
            new Body("korugar",  new Vec3(-3200, 250, -2600), 200, 0xFFD24A, true, planet("korugar"), true),
            new Body("ysmault",  new Vec3(4200, -150, 2600),  180, 0xFF3B30, true, planet("ysmault"), true),
            new Body("odym",     new Vec3(-2000, 100, 4200),  230, 0x7FC8FF, true, planet("odym"), true),
            new Body("zamaron",  new Vec3(900, 300, 5200),    190, 0xE58BFF, true, planet("zamaron"), true),
            new Body("nok",      new Vec3(-5600, -100, 600),  170, 0x7B6BFF, true, planet("nok"), true),
            new Body("okaara",   new Vec3(6200, 200, -400),   240, 0xFFA040, true, planet("okaara"), true),
            new Body("ryut",     new Vec3(-1400, -200, -6400), 160, 0x8A8A9A, true, planet("ryut"), false),
            // O Sol: só para ver (e iluminar os planetas), longe demais para alcançar
            new Body("sun",      new Vec3(30000, 9000, 20000), 4200, 0xFFF4C0, false, null, false)
    );

    public static final Body SUN = BODIES.get(BODIES.size() - 1);

    /** Onde você aparece ao subir da Terra (o ponto bate com onde você estava no mundo normal). */
    public static Vec3 arrivalFromEarth(double overworldX, double overworldZ) {
        return new Vec3(overworldX / 64.0, -300, overworldZ / 64.0);
    }

    @Nullable
    public static Body byDimension(ResourceKey<Level> dimension) {
        for (Body b : BODIES) if (dimension.equals(b.dimension())) return b;
        return null;
    }

    @Nullable
    public static Body inside(Vec3 pos) {
        for (Body b : BODIES) {
            if (b.landable() && pos.distanceToSqr(b.center()) < b.radius() * b.radius()) return b;
        }
        return null;
    }

    public static boolean isPlanet(ResourceKey<Level> dimension) {
        Body b = byDimension(dimension);
        return b != null && b != EARTH;
    }

    private SpaceBodies() {}
}
