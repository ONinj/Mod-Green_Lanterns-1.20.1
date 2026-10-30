package com.lanterna.tropa;

import com.lanterna.corps.BuiltinCorps;
import com.lanterna.lore.Factions;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.Mission;
import com.lanterna.mission.MissionManager;
import com.lanterna.mission.Objective;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A Prova da Emoção (nível 50): sentir a emoção da Tropa de verdade (3000 pontos, ganhos do jeito de cada Tropa),
 * derrotar 3 capitães piratas e falar com o Guardião do planeta da Tropa. Ao terminar, o anel passa a se
 * recarregar sozinho com a emoção do portador.
 */
public final class Prova {
    public static final int PONTOS = 3000;

    public static void registrar(java.util.function.BiConsumer<String, Mission> add) {
        for (BuiltinCorps c : BuiltinCorps.values()) {
            if (c == BuiltinCorps.WHITE) continue;
            PlanetLore home = PlanetLore.byCorps(c.id());
            String planet = home != null ? home.planet : "oa";
            String id = "prova_" + c.id().getPath();
            List<Objective> obj = List.of(
                    Objective.of(Objective.Type.EMOCAO, c.id().getPath(), PONTOS, null, null),
                    Objective.of(Objective.Type.KILL, "papel:capitao", 3, null, null),
                    Objective.of(Objective.Type.TALK, "guardiao:" + planet, 1, planet, PlanetLore.HUB));
            add.accept(id, new Mission(id, "prova", c.color(), obj, () -> {
                List<ItemStack> r = new ArrayList<>();
                r.add(new ItemStack(com.lanterna.lore.LoreItems.MERITO.get(), 32));
                return r;
            }, 5000, null, null));
        }
    }

    public static void comecar(ServerPlayer p) {
        BuiltinCorps c = Factions.playerCorps(p);
        if (c == null || c == BuiltinCorps.WHITE) return;
        MissionManager.start(p, "prova_" + c.id().getPath());
    }

    /** Chamado quando a missão da Prova termina. */
    public static void concluir(ServerPlayer p) {
        com.lanterna.mission.LoreCapability.get(p).ifPresent(d -> d.setAutoRecarga(true));
        p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("prova.lanterna.concluida")
                .withStyle(s -> s.withColor(0xFFE070).withBold(true)));
    }

    private Prova() {}

    static BlockPos hub() { return PlanetLore.HUB; }
}
