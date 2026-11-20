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
            // Capítulo 2: a Vila Aurora
            case "necron_a1" -> m(id, List.of(Objective.of(Type.TALK, "seguranca:earth", 1, "earth", aurora())), "necron_a2");
            case "necron_a2" -> m(id, List.of(fonte("necron:resgate_1", Aurora.refem(1)), fonte("necron:resgate_2", Aurora.refem(2)),
                    fonte("necron:resgate_3", Aurora.refem(3)), fonte("necron:resgate_4", Aurora.refem(4))), "necron_a3");
            case "necron_a3" -> m(id, List.of(Objective.of(Type.TALK, "cientista:earth", 1, "earth", aurora())), "necron_a4");
            case "necron_a4" -> m(id, List.of(fonte("necron:rastro_1", Aurora.marca(1)), fonte("necron:rastro_2", Aurora.marca(2)),
                    fonte("necron:rastro_3", Aurora.marca(3))), "necron_1");
            case "necron_1" -> m(id, List.of(Objective.of(Type.FONTE, "necron:cratera", 1, "earth", chao)), "necron_2");
            case "necron_2" -> m(id, List.of(Objective.of(Type.FONTE, "necron:enfrentou", 1, "earth", chao)), null);
            case "necron_3" -> m(id, List.of(Objective.of(Type.FONTE, "necron:dois", 1, null, null)), null);
            case "necron_3d" -> m(id, List.of(fonte("necron:primeira_fechou", primeiraFenda())), null);
            case "necron_5" -> m(id, List.of(Objective.of(Type.FONTE, "necron:terra", 1, "earth", chao)), null);
            case "necron_6" -> m(id, List.of(Objective.of(Type.FONTE, "necron:vitoria", 1, "earth", chao)), "necron_7");
            case "necron_7" -> m(id, List.of(Objective.of(Type.FONTE, "necron:purificou_terra", 1, "earth", chao)), null);
            default -> {
                // necron_3b_<tropa> / necron_3c_<tropa>: o treino e o duelo no planeta da Tropa (Capítulo 3)
                if (id.startsWith("necron_3b_") || id.startsWith("necron_3c_")) {
                    com.lanterna.lore.PlanetLore pl = null;
                    for (com.lanterna.lore.PlanetLore x : com.lanterna.lore.PlanetLore.values())
                        if (x.corps.id().getPath().equals(id.substring(10))) pl = x;
                    if (pl == null) yield null;
                    BlockPos h = com.lanterna.lore.PlanetLore.HUB;
                    if (id.startsWith("necron_3b_")) yield m(id, List.of(
                            Objective.of(Type.CONSTRUCT, "any", 6, pl.planet, h),
                            Objective.of(Type.FONTE, "necron:apoio", 1, pl.planet, h),
                            Objective.of(Type.DUMMY, "alvo", 4, pl.planet, h)), "necron_3c_" + id.substring(10));
                    yield m(id, List.of(Objective.of(Type.FONTE, "necron:duelo", 1, pl.planet, h)), "necron_3d");
                }
                // necron_q_<tropa>: as ruínas de uma Tropa caída (Capítulo 6)
                if (id.startsWith("necron_q_")) {
                    String t = id.substring(9);
                    com.lanterna.lore.PlanetLore pl = null;
                    for (com.lanterna.lore.PlanetLore x : com.lanterna.lore.PlanetLore.values()) if (x.corps.id().getPath().equals(t)) pl = x;
                    if (pl == null) yield null;
                    String pn = pl.planet;
                    yield m(id, List.of(Objective.of(Type.FONTE, "necron:caido_" + t + "_1", 1, pn, Ruinas.ponto(1)),
                            Objective.of(Type.FONTE, "necron:caido_" + t + "_2", 1, pn, Ruinas.ponto(2)),
                            Objective.of(Type.FONTE, "necron:caido_" + t + "_3", 1, pn, Ruinas.ponto(3)),
                            Objective.of(Type.FONTE, "necron:guardiao_" + t, 1, pn, com.lanterna.lore.PlanetLore.HUB),
                            Objective.of(Type.FONTE, "necron:nucleo_" + t, 1, null, null)), null);
                }
                // necron_s_<tropa>: o socorro no planeta de outra Tropa antes da defesa (Capítulo 5)
                if (id.startsWith("necron_s_")) {
                    String t = id.substring(9);
                    com.lanterna.lore.PlanetLore pl = null;
                    for (com.lanterna.lore.PlanetLore x : com.lanterna.lore.PlanetLore.values()) if (x.corps.id().getPath().equals(t)) pl = x;
                    if (pl == null) yield null;
                    String pn = pl.planet;
                    yield switch (Socorro.tipo(t)) {
                        case PRISIONEIROS -> m(id, List.of(Objective.of(Type.FONTE, "necron:preso_" + t + "_1", 1, pn, Socorro.ponto(1)),
                                Objective.of(Type.FONTE, "necron:preso_" + t + "_2", 1, pn, Socorro.ponto(2)),
                                Objective.of(Type.FONTE, "necron:preso_" + t + "_3", 1, pn, Socorro.ponto(3))), "necron_4_" + t);
                        case SOBREVIVENTES -> m(id, List.of(Objective.of(Type.FONTE, "necron:sobrev_" + t + "_1", 1, pn, Socorro.ponto(1)),
                                Objective.of(Type.FONTE, "necron:sobrev_" + t + "_2", 1, pn, Socorro.ponto(2)),
                                Objective.of(Type.FONTE, "necron:sobrev_" + t + "_3", 1, pn, Socorro.ponto(3))), "necron_4_" + t);
                        case LADROES -> m(id, List.of(Objective.of(Type.COLLECT, "lanterna:fragmento_bateria", Socorro.FRAGMENTOS, pn, null)), "necron_4_" + t);
                    };
                }
                // necron_4a_<tropa>: as três âncoras da Morte no planeta da Tropa (Capítulo 4)
                if (id.startsWith("necron_4a_")) {
                    com.lanterna.lore.PlanetLore pl = null;
                    for (com.lanterna.lore.PlanetLore x : com.lanterna.lore.PlanetLore.values())
                        if (x.corps.id().getPath().equals(id.substring(10))) pl = x;
                    if (pl == null) yield null;
                    yield m(id, List.of(Objective.of(Type.FONTE, "necron:ancora_1", 1, pl.planet, Ancoras.posicao(1)),
                            Objective.of(Type.FONTE, "necron:ancora_2", 1, pl.planet, Ancoras.posicao(2)),
                            Objective.of(Type.FONTE, "necron:ancora_3", 1, pl.planet, Ancoras.posicao(3))), "necron_4_" + id.substring(10));
                }
                // necron_4_<tropa>: defender a Bateria Central da própria Tropa
                if (!id.startsWith("necron_4_")) yield null;
                com.lanterna.lore.PlanetLore p = null;
                for (com.lanterna.lore.PlanetLore x : com.lanterna.lore.PlanetLore.values())
                    if (x.corps.id().getPath().equals(id.substring(9))) p = x;
                if (p == null) yield null;
                yield m(id, List.of(Objective.of(Type.FONTE, "necron:defesa_" + p.corps.id().getPath(), 1, p.planet, com.lanterna.lore.PlanetLore.HUB)), null);
            }
        };
    }

    @Nullable
    private static BlockPos aurora() {
        BlockPos c = com.lanterna.terra.TerraSite.centro;
        return c == null ? null : new BlockPos(c.getX(), 0, c.getZ());
    }

    @Nullable
    private static BlockPos primeiraFenda() {
        BlockPos c = com.lanterna.terra.TerraSite.centro;
        return c == null ? null : new BlockPos(c.getX() + 170, 0, c.getZ() + 60);
    }

    private static Objective fonte(String alvo, @Nullable BlockPos pos) {
        return Objective.of(Type.FONTE, alvo, 1, pos == null ? null : "earth", pos);
    }

    private static Mission m(String id, List<Objective> o, @Nullable String next) {
        return new Mission(id, LINHA, COR, o, List::of, 100, next, null);
    }

    private MissoesNecron() {}
}
