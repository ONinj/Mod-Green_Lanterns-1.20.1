package seu.pacote.baterias;

import net.minecraft.server.level.ServerPlayer;

/**
 * Quem pode atravessar o portal da Bateria Central. Só pouquíssimos jogadores devem conseguir.
 * Por padrão: jogadores com a tag "bateria_<tropa>" (dê com /tag NOME add bateria_verde) ou operadores.
 * Troque por: nível do anel, missão concluída, cargo de Guarda de Honra etc.
 */
public final class AcessoBateria {
    private AcessoBateria() {}
    public static boolean podeEntrar(ServerPlayer p, Tropa t) {
        return p.getTags().contains("bateria_" + t.nome) || p.hasPermissions(2);
    }
}
