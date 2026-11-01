package com.lanterna.baterias;

import net.minecraft.server.level.ServerPlayer;

/**
 * Quem pode atravessar o portal da Bateria Central: quem está usando o anel da Tropa daquela Bateria, quem tem a
 * tag "bateria_<tropa>" (/tag NOME add bateria_verde) ou operadores.
 */
public final class AcessoBateria {
    private AcessoBateria() {}
    public static boolean podeEntrar(ServerPlayer p, Tropa t) {
        return com.lanterna.lore.Factions.playerCorps(p) == t.corps() || p.getTags().contains("bateria_" + t.nome) || p.hasPermissions(2);
    }
}
