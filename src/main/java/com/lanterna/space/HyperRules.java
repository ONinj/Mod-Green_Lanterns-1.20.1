package com.lanterna.space;

import com.lanterna.capability.ModCapabilities;
import net.minecraft.server.level.ServerPlayer;

/**
 * Regras do servidor para a hiperpropulsão no espaço.
 *
 * A milhares de blocos por segundo, o Minecraft acharia que é trapaça ("moved too quickly")
 * e tentaria carregar milhares de chunks vazios pelo caminho. Enquanto o jogador está em
 * hiperpropulsão no espaço:
 *  - o servidor aceita a velocidade alta;
 *  - não carrega chunks em volta dele (o espaço é vazio, não há nada para ver).
 * Quando ele desacelera, tudo volta ao normal na hora.
 */
public final class HyperRules {

    public static boolean isHyper(ServerPlayer player) {
        if (!player.level().dimension().equals(SpaceBodies.SPACE)) return false;
        return ModCapabilities.get(player).map(d -> d.hasRing() && d.flightEnabled() && d.hyper()).orElse(false);
    }

    private HyperRules() {}
}
