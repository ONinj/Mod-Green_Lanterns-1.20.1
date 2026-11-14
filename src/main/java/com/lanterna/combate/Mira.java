package com.lanterna.combate;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Teste de acerto dos poderes do anel contra quem está se mexendo.
 *
 * O jogador mira no que vê na tela, e a tela mostra os outros um pouco atrás de onde eles estão no servidor
 * (interpolação + o atraso da rede). Contra um Lanterna NPC voando rápido isso fazia o laser "passar" pelo alvo:
 * acertava só quando ele parava. Aqui o alvo também conta onde estava nos últimos ticks, e a caixa cresce um pouco
 * com a velocidade dele (até um limite, para não virar mira automática).
 */
@Mod.EventBusSubscriber(modid = com.lanterna.LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Mira {
    /**
     * PvP (0.48.12): onde cada jogador esteve nos últimos ticks. Quem atira vê o outro com o atraso da rede (o dele e o
     * do alvo); antes o servidor só olhava a posição atual e o laser "passava" por quem estava voando. Agora vale
     * qualquer posição dos últimos {@link #HISTORICO} ticks (~0,4 s), como nos jogos de tiro.
     */
    private static final int HISTORICO = 8;
    private static final java.util.Map<java.util.UUID, java.util.ArrayDeque<AABB>> CAIXAS = new java.util.concurrent.ConcurrentHashMap<>();

    @SubscribeEvent
    public static void guardar(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.level().isClientSide) return;
        var fila = CAIXAS.computeIfAbsent(e.player.getUUID(), k -> new java.util.ArrayDeque<>());
        fila.addFirst(e.player.getBoundingBox());
        while (fila.size() > HISTORICO) fila.removeLast();
    }

    @SubscribeEvent
    public static void saiu(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        CAIXAS.remove(e.getEntity().getUUID());
    }

    /** Quantos ticks para trás o alvo ainda conta (o que o jogador estava vendo). */
    private static final int ATRASO = 4;
    /** Só para o teste automático comparar com e sem a compensação. */
    public static volatile boolean desligada;

    public static Optional<Vec3> clip(Entity alvo, Vec3 de, Vec3 ate, double folga) {
        AABB caixa = alvo.getBoundingBox();
        if (desligada) return caixa.inflate(folga).clip(de, ate);
        if (alvo instanceof Player) {
            Optional<Vec3> melhor = caixa.inflate(folga + 0.3).clip(de, ate);
            var fila = CAIXAS.get(alvo.getUUID());
            if (fila != null) for (AABB velha : fila) {
                if (velha.getCenter().distanceToSqr(caixa.getCenter()) > 64) continue;   // teleporte: não vale
                Optional<Vec3> c = velha.inflate(folga + 0.2).clip(de, ate);
                if (c.isPresent() && (melhor.isEmpty() || c.get().distanceToSqr(de) < melhor.get().distanceToSqr(de))) melhor = c;
            }
            return melhor;
        }
        Vec3 passo = new Vec3(alvo.getX() - alvo.xo, alvo.getY() - alvo.yo, alvo.getZ() - alvo.zo);
        double vel = passo.length();
        // em PvP a compensação é menor (os dois lados estão mirando)
        double extra = alvo instanceof Player ? Math.min(0.35, vel * 1.5) : Math.min(1.1, vel * 2.5) + (!alvo.onGround() ? 0.25 : 0);
        Optional<Vec3> melhor = caixa.inflate(folga + extra).clip(de, ate);
        if (vel > 0.05) {
            for (int k = 1; k <= ATRASO; k++) {
                Optional<Vec3> c = caixa.move(passo.scale(-k)).inflate(folga + 0.2).clip(de, ate);
                if (c.isPresent() && (melhor.isEmpty() || c.get().distanceToSqr(de) < melhor.get().distanceToSqr(de))) melhor = c;
            }
        }
        return melhor;
    }

    private Mira() {}
}
