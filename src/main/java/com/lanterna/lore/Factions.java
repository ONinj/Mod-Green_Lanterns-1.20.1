package com.lanterna.lore;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.lore.entity.Race;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Inimizades entre as Tropas (como nos quadrinhos). Os povos de um planeta tomam o lado da Tropa
 * daquele planeta: atacam Lanternas de Tropas inimigas e não fazem trocas com eles, até que o
 * Lanterna conquiste o respeito daquele povo (missões, Ecos derrotados por perto, presentes).
 *
 *  Verde    x Amarelo, Vermelho, Laranja, Preto
 *  Amarelo  x Verde, Azul, Vermelho, Preto
 *  Vermelho x Verde, Amarelo, Azul, Laranja, Preto
 *  Azul     x Amarelo, Vermelho, Laranja, Preto   (aliados do Verde)
 *  Violeta  x Laranja, Preto
 *  Índigo   x Preto
 *  Laranja  x todos (a avareza não tem amigos)
 *  Preto    x todos (a morte contra a vida)
 */
public final class Factions {

    private static final Map<BuiltinCorps, Set<BuiltinCorps>> ENEMIES = new EnumMap<>(BuiltinCorps.class);

    private static void war(BuiltinCorps a, BuiltinCorps... others) {
        for (BuiltinCorps b : others) {
            ENEMIES.computeIfAbsent(a, k -> EnumSet.noneOf(BuiltinCorps.class)).add(b);
            ENEMIES.computeIfAbsent(b, k -> EnumSet.noneOf(BuiltinCorps.class)).add(a);
        }
    }

    static {
        war(BuiltinCorps.GREEN, BuiltinCorps.YELLOW, BuiltinCorps.RED, BuiltinCorps.ORANGE, BuiltinCorps.BLACK);
        war(BuiltinCorps.YELLOW, BuiltinCorps.BLUE, BuiltinCorps.RED, BuiltinCorps.BLACK);
        war(BuiltinCorps.RED, BuiltinCorps.BLUE, BuiltinCorps.ORANGE, BuiltinCorps.BLACK);
        war(BuiltinCorps.BLUE, BuiltinCorps.ORANGE, BuiltinCorps.BLACK);
        war(BuiltinCorps.VIOLET, BuiltinCorps.ORANGE, BuiltinCorps.BLACK);
        war(BuiltinCorps.INDIGO, BuiltinCorps.BLACK);
        war(BuiltinCorps.ORANGE, BuiltinCorps.YELLOW, BuiltinCorps.INDIGO, BuiltinCorps.BLACK);
        war(BuiltinCorps.WHITE, BuiltinCorps.BLACK);
    }

    @Nullable
    public static BuiltinCorps of(@Nullable ResourceLocation id) {
        if (id == null) return null;
        for (BuiltinCorps c : BuiltinCorps.values()) if (c.id().equals(id)) return c;
        return null;
    }

    public static boolean enemies(@Nullable BuiltinCorps a, @Nullable BuiltinCorps b) {
        if (a == null || b == null || a == b) return false;
        Set<BuiltinCorps> set = ENEMIES.get(a);
        return set != null && set.contains(b);
    }

    /** Tropa do anel que o jogador está usando (null se não tem anel). */
    @Nullable
    public static BuiltinCorps playerCorps(Player p) {
        RingData ring = ModCapabilities.get(p).orElse(null);
        return ring != null && ring.hasRing() ? of(ring.corps()) : null;
    }

    /** A Tropa à qual um povo é leal (a do seu planeta). */
    @Nullable
    public static BuiltinCorps raceCorps(Race race) {
        PlanetLore p = PlanetLore.byPlanet(race.planet);
        return p != null ? of(p.corps.id()) : null;
    }

    /** Este povo é inimigo do jogador por causa do anel dele? */
    public static boolean raceHatesPlayer(Race race, Player p) {
        return enemies(raceCorps(race), playerCorps(p));
    }

    private Factions() {}
}
