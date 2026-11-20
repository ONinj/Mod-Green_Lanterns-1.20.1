package com.lanterna.necron;

import com.lanterna.mission.Mission;
import com.lanterna.mission.Objective;
import com.lanterna.mission.Objective.Type;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 0.54.2: as missões da saga do Necron (a linha "necron", no diário).
 *
 *  necron_1  A Queda do Necron   ir até onde o Necron caiu na Terra (a cratera da Bateria Central da Morte)
 *  necron_2  O Ser da Morte      enfrentar o Necron (ele derruba o portador; a fuga vem numa cena)
 *  necron_3  Duas Luzes          forjar um segundo anel (de qualquer Tropa) e usar os dois ao mesmo tempo
 *  necron_4_<tropa>  A Defesa    voltar ao planeta da Tropa com dois anéis e vencer as três ondas da fenda
 */
public final class MissoesNecron {
    public static final String LINHA = "necron";
    public static final int COR = 0x8A3CC8;

    public static boolean eDoNecron(String id) {
        return id.startsWith("necron_");
    }

    /** O ponto do mapa (o centro da cratera), conhecido pelo servidor e mandado ao cliente. */
    @Nullable public static BlockPos centro;

    @Nullable
    public static Mission mission(String id) {
        BlockPos c = centro;
        BlockPos chao = c == null ? null : new BlockPos(c.getX(), 0, c.getZ());
        return switch (id) {
            case "necron_1" -> m(id, List.of(Objective.of(Type.FONTE, "necron:cratera", 1, "earth", chao)), "necron_2");
            case "necron_2" -> m(id, List.of(Objective.of(Type.FONTE, "necron:enfrentou", 1, "earth", chao)), null);
            case "necron_3" -> m(id, List.of(Objective.of(Type.FONTE, "necron:dois", 1, null, null)), null);
            default -> {
                // necron_4_<tropa>: defender a Bateria Central da própria Tropa
                if (!id.startsWith("necron_4_")) yield null;
                com.lanterna.lore.PlanetLore p = null;
                for (com.lanterna.lore.PlanetLore x : com.lanterna.lore.PlanetLore.values())
                    if (x.corps.id().getPath().equals(id.substring(9))) p = x;
                if (p == null) yield null;
                yield m(id, List.of(Objective.of(Type.FONTE, "necron:defesa", 1, p.planet, com.lanterna.lore.PlanetLore.HUB)), null);
            }
        };
    }

    private static Mission m(String id, List<Objective> o, @Nullable String next) {
        return new Mission(id, LINHA, COR, o, List::of, 100, next, null);
    }

    private MissoesNecron() {}
}
