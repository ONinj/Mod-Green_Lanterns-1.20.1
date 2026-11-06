package com.lanterna.voz;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/** Ponte para o Simple Voice Chat: só é chamada quando ele está instalado (as classes dele só carregam aqui). */
final class VozBridge {
    static void juntar(MinecraftServer s, Ligacoes.Ligacao l) {
        VozPlugin.juntar(l);
    }

    static void sair(MinecraftServer s, UUID p) {
        VozPlugin.sair(p);
    }

    static void fim(MinecraftServer s, Ligacoes.Ligacao l) {
        VozPlugin.fim(l);
    }

    private VozBridge() {}
}
