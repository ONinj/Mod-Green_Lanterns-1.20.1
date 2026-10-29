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

    /** Todos os destinos conhecidos (Terra e planetas que dá para pousar). */
    public static List<Destination> all() {
        List<Destination> list = new ArrayList<>();
        for (SpaceBodies.Body b : SpaceBodies.allBodies()) {
            if (!b.landable() || b.dimension() == null) continue;
            list.add(new Destination(b.id(), b.displayName(), b.dimension(), b.center(), b.radius(), b.atmosphere()));
        }
        return list;
    }

    public static Destination byId(String id) {
        for (Destination d : all()) if (d.id().equals(id)) return d;
        return null;
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
        return here != null ? here.center() : player.position();
    }

    /** Destino onde o jogador já está (não dá para ir para ele). */
    public static boolean isHere(Player player, Destination d) {
        if (!player.level().dimension().equals(d.dimension())) return false;
        SpaceBodies.Body here = SpaceBodies.bodyAt(d.dimension(), player.getX(), player.getZ());
        return here == null || here.id().equals(d.id());
    }

    private Destinations() {}
}
