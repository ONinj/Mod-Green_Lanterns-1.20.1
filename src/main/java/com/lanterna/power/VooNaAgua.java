package com.lanterna.power;

import com.lanterna.capability.ModCapabilities;
import net.minecraft.world.entity.player.Player;

import java.util.function.Predicate;

/** Quem está voando com o anel (para o jogo não pôr o jogador para nadar). */
public final class VooNaAgua {
    /** No cliente: preenchido na inicialização do cliente com o estado do voo que o cliente conhece. */
    public static Predicate<Player> CLIENTE = p -> false;

    public static boolean voando(Player p) {
        if (p.level().isClientSide) return CLIENTE.test(p);
        return ModCapabilities.get(p).map(d -> d.hasRing() && d.flightEnabled() && d.powerActive()).orElse(false);
    }

    private VooNaAgua() {}
}
