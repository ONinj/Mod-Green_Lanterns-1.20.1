package com.lanterna.client.flight;

import com.lanterna.space.SpaceBodies;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Lugares para onde o piloto automático pode levar o jogador. */
public final class Destinations {

    public record Destination(String id, Component name, ResourceKey<Level> dimension, Vec3 center,
                              double radius, int color) {}

    /** Raio (em setores de 8.000 blocos) dos planetas gerados que aparecem no mapa. */
    public static final int MAP_SECTORS = 4;

    /** Destinos conhecidos: Terra, planetas das Tropas e planetas gerados em volta de você. */
    public static List<Destination> all() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        Vec3 here = player != null ? spacePosition(player) : Vec3.ZERO;
        List<Destination> list = new ArrayList<>();
        for (SpaceBodies.Body b : SpaceBodies.near(here, MAP_SECTORS)) {
            if (!b.landable() || b.dimension() == null) continue;
            if (SpaceBodies.isHidden(b.id()) && !com.lanterna.client.mission.ClientLoreData.geneseRevealed()) continue;
            list.add(new Destination(b.id(), b.displayName(), b.dimension(), b.center(), b.radius(), b.atmosphere()));
        }
        return list;
    }

    public static Destination byId(String id) {
        SpaceBodies.Body b = id == null ? null : SpaceBodies.byId(id);
        if (b == null || !b.landable() || b.dimension() == null) return null;
        return new Destination(b.id(), b.displayName(), b.dimension(), b.center(), b.radius(), b.atmosphere());
    }

    /** Onde o jogador está, em coordenadas do espaço (para o mapa e as distâncias). */
    public static Vec3 spacePosition(Player player) {
        ResourceKey<Level> dim = player.level().dimension();
        if (dim.equals(SpaceBodies.SPACE)) return player.position();
        if (dim.equals(Level.OVERWORLD)) {
            Vec3 a = SpaceBodies.arrivalFromEarth(player.getX(), player.getZ());
            return new Vec3(a.x, a.y - 100, a.z);
        }
        SpaceBodies.Body here = SpaceBodies.bodyAt(dim, player.getX(), player.getZ());
        if (here != null) return here.center();
        return com.lanterna.space.ProceduralPlanets.spacePointForRegion(player.getX(), player.getZ());
    }

    /** Destino onde o jogador já está (não dá para ir para ele). */
    public static boolean isHere(Player player, Destination d) {
        if (!player.level().dimension().equals(d.dimension())) return false;
        SpaceBodies.Body here = SpaceBodies.bodyAt(d.dimension(), player.getX(), player.getZ());
        return here != null && here.id().equals(d.id());
    }

    private Destinations() {}
}
