package com.lanterna.baterias;

import net.minecraft.server.level.ServerPlayer;

/**
 * Quem pode atravessar o portal da Bateria Central: quem completou o Chamado da Fonte daquela Tropa (Selo da Fonte),
 * quem tem a tag "bateria_<tropa>" (/tag NOME add bateria_verde) ou operadores.
 */
public final class AcessoBateria {
    private AcessoBateria() {}
    public static boolean podeEntrar(ServerPlayer p, Tropa t) {
        return com.lanterna.fonte.FonteDados.temSelo(p, t.nome) || p.getTags().contains("bateria_" + t.nome) || p.hasPermissions(2);
    }
}
