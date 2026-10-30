package com.lanterna.guilda;

import com.lanterna.cidade.CidadeNpcs;
import com.lanterna.cidade.Kit;
import com.lanterna.cidade.Obra;
import com.lanterna.cidade.Paleta;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.lore.PlanetLore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static com.lanterna.cidade.Kit.b;
import static com.lanterna.cidade.Kit.degrau;
import static com.lanterna.cidade.Kit.virado;

/**
 * A base pronta que aparece em volta da bandeira: praça com a bandeira no meio, Cabana Principal (norte),
 * Cabana do Minerador (leste), Quartel do General (oeste), caminhos, postes e uma muralha baixa com 4 portões.
 * Tudo nas cores da Tropa da guilda. Feita em etapas (Obra) para não travar o servidor.
 */
public final class BaseGuilda {
    public static final int R = 20;

    public static BuiltinCorps corps(Guilda g) {
        for (BuiltinCorps c : BuiltinCorps.values()) if (c.id().getPath().equals(g.tropa)) return c;
        return BuiltinCorps.GREEN;
    }

    public static Paleta paleta(Guilda g) {
        PlanetLore pl = PlanetLore.byCorps(corps(g).id());
        Paleta p = Paleta.of(pl == null ? PlanetLore.OA : pl);
        // nada que queime quem anda na base (o destaque vermelho é bloco de magma)
        if (p.destaque().is(Blocks.MAGMA_BLOCK)) p = new Paleta(p.chao(), p.parede(), p.faixa(), p.vidro(), p.luz(), p.teto(),
                b(Blocks.RED_NETHER_BRICKS), p.nucleo(), p.pilar(), p.madeira(), p.escada(), p.laje(), p.la(), p.tapete(), p.cama(), p.painel());
        return p;
    }

    public static void construir(ServerLevel level, BlockPos flag, Guilda g, String planeta) {
        Kit k = new Kit(level, paleta(g));
        for (int x0 = -R - 1; x0 <= R + 1; x0 += 6) {
            int f = x0;
            Obra.etapa(level, l -> terreno(k, flag, f, Math.min(R + 1, f + 5)));
        }
        Obra.etapa(level, l -> praca(k, flag));
        Obra.etapa(level, l -> principal(k, flag.offset(0, 0, -12)));
        Obra.etapa(level, l -> minerador(k, flag.offset(12, 0, 0)));
        Obra.etapa(level, l -> quartel(k, flag.offset(-12, 0, 0)));
        Obra.etapa(level, l -> muralha(k, flag));
        Obra.etapa(level, l -> {
            l.setBlock(flag, GuildaItems.BANDEIRA.get().defaultBlockState(), 3);
            npcs(l, flag, g, planeta);
        });
    }

    /** Chão plano em volta (e ar em cima), sem mexer na bandeira. */
    private static void terreno(Kit k, BlockPos c, int x0, int x1) {
        int y = c.getY();
        for (int dx = x0; dx <= x1; dx++) for (int dz = -R - 1; dz <= R + 1; dz++) {
            int x = c.getX() + dx, z = c.getZ() + dz;
            k.L.getChunk(x >> 4, z >> 4);
            for (int h = 0; h <= 30; h++) {
                if (dx == 0 && dz == 0 && h == 0) continue;
                k.s(x, y + h, z, b(Blocks.AIR));
            }
            boolean caminho = (Math.abs(dx) <= 1 && Math.abs(dz) <= R) || (Math.abs(dz) <= 1 && Math.abs(dx) <= R);
            k.s(x, y - 1, z, caminho ? k.p.faixa() : ((dx + dz) % 7 == 0 ? k.p.parede() : k.p.chao()));
            for (int h = 2; h <= 7; h++) {
                BlockState st = k.L.getBlockState(new BlockPos(x, y - h, z));
                if (st.isAir() || !st.getFluidState().isEmpty()) k.s(x, y - h, z, b(Blocks.STONE));
            }
        }
    }

    private static void praca(Kit k, BlockPos c) {
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 5.4) continue;
            k.s(c.offset(dx, -1, dz), d < 1.5 ? k.p.nucleo() : d > 4.4 ? k.p.destaque() : k.p.chao());
        }
        for (int[] q : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) k.poste(c.offset(q[0], 0, q[1]), 3);
        for (int[] q : new int[][]{{-3, 0}, {3, 0}}) k.estandarte(c.offset(q[0], 0, q[1] - 3), 3);
    }

    /** Cabana Principal: 2 andares, sala, mesa, baú e cama em cima. */
    private static void principal(Kit k, BlockPos c) {
        k.predio(c, 5, 4, 2, 4, Direction.SOUTH);
        k.escada(c.offset(-4, 0, -2), Direction.EAST, 4);
        k.mesa(c.offset(2, 0, -1));
        k.s(c.offset(4, 0, -3), b(Blocks.CHEST));
        k.s(c.offset(3, 0, -3), b(Blocks.CHEST));
        k.s(c.offset(-1, 0, -3), b(Blocks.CRAFTING_TABLE));
        k.s(c.offset(0, 0, -3), b(Blocks.LECTERN));
        k.cama(k.p.cama(), c.offset(2, 4, 1), Direction.NORTH);
        k.cama(k.p.cama(), c.offset(4, 4, 1), Direction.NORTH);
        for (int dx = -3; dx <= 3; dx++) k.s(c.offset(dx, 4, -2), b(k.p.tapete()));
        k.s(c.offset(0, 0, 0), b(k.p.tapete()));
    }

    /** Cabana do Minerador: fornalhas, baús e minério à mostra. */
    private static void minerador(Kit k, BlockPos c) {
        k.predio(c, 4, 4, 1, 5, Direction.WEST);
        k.s(c.offset(3, 0, -3), virado(Blocks.FURNACE, Direction.WEST));
        k.s(c.offset(3, 0, -2), virado(Blocks.BLAST_FURNACE, Direction.WEST));
        k.s(c.offset(3, 0, 2), b(Blocks.CHEST));
        k.s(c.offset(3, 0, 3), b(Blocks.BARREL));
        k.s(c.offset(-1, 0, -3), b(Blocks.ANVIL));
        k.s(c.offset(1, 0, 3), b(Blocks.SMITHING_TABLE));
        // pilha de minério do lado de fora
        BlockState[] min = {b(Blocks.IRON_ORE), b(Blocks.GOLD_ORE), b(Blocks.EMERALD_ORE), b(Blocks.DIAMOND_ORE), b(Blocks.COAL_ORE)};
        for (int i = 0; i < 7; i++) k.s(c.offset(6 + i % 2, i / 4, -3 + i % 4), min[i % min.length]);
        k.s(c.offset(6, 0, 2), b(Blocks.RAIL));
        k.s(c.offset(6, 0, 3), b(Blocks.RAIL));
    }

    /** Quartel do General: mesa de guerra e pátio de treino com alvos. */
    private static void quartel(Kit k, BlockPos c) {
        k.predio(c, 4, 4, 1, 5, Direction.EAST);
        k.mesa(c.offset(-1, 0, 0));
        k.s(c.offset(-3, 0, -3), b(Blocks.CARTOGRAPHY_TABLE));
        k.s(c.offset(-3, 0, 3), b(Blocks.CHEST));
        k.s(c.offset(-3, 1, 0), k.p.destaque());
        // pátio de treino ao sul
        for (int dx = -4; dx <= 4; dx++) for (int dz = 6; dz <= 11; dz++) {
            boolean borda = Math.abs(dx) == 4 || dz == 11;
            k.s(c.offset(dx, -1, dz), k.p.faixa());
            if (borda) k.s(c.offset(dx, 0, dz), b(Blocks.SPRUCE_FENCE));
        }
        for (int i = -2; i <= 2; i += 2) {
            k.s(c.offset(i, 0, 10), b(Blocks.HAY_BLOCK));
            k.s(c.offset(i, 1, 10), b(Blocks.TARGET));
        }
    }

    /** Muralha baixa com postes e 4 portões. */
    private static void muralha(Kit k, BlockPos c) {
        for (int i = -R; i <= R; i++) {
            for (int[] q : new int[][]{{i, -R}, {i, R}, {-R, i}, {R, i}}) {
                int dx = q[0], dz = q[1];
                if (Math.abs(dx) <= 2 && Math.abs(dz) == R || Math.abs(dz) <= 2 && Math.abs(dx) == R) continue; // portões
                boolean poste = Math.floorMod(i, 5) == 0;
                k.s(c.offset(dx, 0, dz), poste ? k.p.pilar() : k.p.parede());
                k.s(c.offset(dx, 1, dz), poste ? k.p.pilar() : k.p.faixa());
                if (poste) k.s(c.offset(dx, 2, dz), k.p.luz());
            }
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos g = c.relative(d, R);
            Direction lado = d.getClockWise();
            for (int w = -3; w <= 3; w += 6) {
                k.s(g.relative(lado, w), k.p.pilar());
                k.s(g.relative(lado, w).above(), k.p.pilar());
                k.s(g.relative(lado, w).above(2), k.p.pilar());
                k.s(g.relative(lado, w).above(3), k.p.luz());
            }
            for (int w = -3; w <= 3; w++) k.s(g.relative(lado, w).above(4), k.p.faixa());
        }
    }

    /** Minerador e General (invulneráveis, presos perto das cabanas). */
    public static void npcs(ServerLevel l, BlockPos flag, Guilda g, String planeta) {
        BuiltinCorps c = corps(g);
        LanternaEntity m = CidadeNpcs.npc(l, flag.offset(10, 0, 1), "guilda_minerador", c, false, 90, 2);
        LanternaEntity gen = CidadeNpcs.npc(l, flag.offset(-10, 0, 1), "guilda_general", c, true, 270, 2);
        for (LanternaEntity e : new LanternaEntity[]{m, gen}) {
            e.setInvulnerable(true);
            e.getPersistentData().putString(Conquistas.TAG_GUILDA, g.id.toString());
            e.getPersistentData().putString(Conquistas.TAG_BASE, planeta);
        }
    }

    private BaseGuilda() {}
}
