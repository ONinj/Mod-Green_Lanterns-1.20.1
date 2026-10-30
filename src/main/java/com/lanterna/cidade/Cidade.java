package com.lanterna.cidade;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.Especie;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.lore.LoreBlocks;
import com.lanterna.lore.LoreEntities;
import com.lanterna.lore.PlanetLore;
import com.lanterna.lore.world.CidadeTropa;
import com.lanterna.naves.NaveEntity;
import com.lanterna.naves.NaveTipo;
import com.lanterna.naves.bloco.NaveBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

import static com.lanterna.cidade.Kit.*;

/**
 * Cidade da Tropa completa (0.15). 128 x 128 blocos, ao norte da base de cada planeta de Tropa.
 *
 *          N (portão)                      Muralha com portões nas 4 avenidas e torres nos cantos.
 *   Celas    QG (3 andares)   Arquivo      Praça redonda com a Bateria Central gigante, fontes e bancos.
 *   Academia   PRAÇA          Mercado      QG: térreo com o balcão de contratos, 1º andar sala de briefing,
 *   Arena                                    2º andar sala do Comandante.
 *   Casas      Hangar        Enfermaria    Hangar atravessado pela avenida sul, com duas naves nas baias.
 *          S (portão principal)
 */
public final class Cidade {
    public static final BlockPos CENTRO = new BlockPos(0, 0, -150);
    public static final int R = 62;
    /** Dono "fictício" das naves estacionadas no hangar (ficam trancadas). */
    public static final UUID DONO_CIDADE = UUID.nameUUIDFromBytes("lanterna:cidade".getBytes());

    public static void construir(ServerLevel level, PlanetLore planet) {
        Paleta pal = Paleta.of(planet);
        int y = chao(level);
        BlockPos c = CENTRO.atY(y);
        Kit k = new Kit(level, pal);
        Obra.etapa(level, l -> limparAntigos(l, c));
        for (int x0 = -R - 2; x0 <= R + 2; x0 += 8) {
            int fx0 = x0;
            Obra.etapa(level, l -> terreno(k, c, fx0, Math.min(R + 2, fx0 + 7)));
        }
        Obra.etapa(level, l -> { avenidas(k, c); praca(k, c); });
        Obra.etapa(level, l -> CidadeTropa.bateriaCentral(l, c, planet));
        Obra.etapa(level, l -> muralha(k, c));
        Obra.etapa(level, l -> qgCasca(k, c));
        Obra.etapa(level, l -> qgInterior(k, c, planet));
        Obra.etapa(level, l -> arquivo(k, c));
        Obra.etapa(level, l -> celas(k, c));
        Obra.etapa(level, l -> academia(k, c));
        Obra.etapa(level, l -> mercado(k, c));
        Obra.etapa(level, l -> enfermaria(k, c));
        Obra.etapa(level, l -> hangar(k, c));
        int[][] casas = {{-48, 34, 0}, {-34, 34, 0}, {-20, 34, 0}, {-48, 50, 1}, {-34, 50, 1}, {-20, 50, 1}};
        for (int[] cs : casas) Obra.etapa(level, l -> casa(k, c.offset(cs[0], 0, cs[1]), cs[2] == 0 ? Direction.SOUTH : Direction.NORTH));
        Obra.etapa(level, l -> aneis(k, c));
        Obra.etapa(level, l -> moradores(l, c, planet));
        Obra.etapa(level, l -> LanternaMod.LOGGER.info("Lanterna: cidade completa da Tropa pronta em {}", planet.planet));
    }

    private static int chao(ServerLevel level) {
        int sum = 0, n = 0;
        for (int dx = -R; dx <= R; dx += 16) for (int dz = -R; dz <= R; dz += 16) {
            level.getChunk((CENTRO.getX() + dx) >> 4, (CENTRO.getZ() + dz) >> 4);
            sum += level.getHeight(Heightmap.Types.WORLD_SURFACE, CENTRO.getX() + dx, CENTRO.getZ() + dz);
            n++;
        }
        int y = sum / Math.max(1, n);
        return Math.max(level.getMinBuildHeight() + 12, Math.min(y, level.getMaxBuildHeight() - 70));
    }

    /** A cidade da 0.14 (e alvos de treino velhos) sai antes de construir a nova. */
    private static void limparAntigos(ServerLevel l, BlockPos c) {
        AABB area = new AABB(c).inflate(R + 4, 60, R + 4);
        for (Entity e : l.getEntities((Entity) null, area, e -> (e instanceof LanternaEntity le && le.funcao().isEmpty())
                || e.getType() == LoreEntities.ALVO_TREINO.get())) e.discard();
    }

    // ===================== Terreno, avenidas, praça, muralha =====================

    private static void terreno(Kit k, BlockPos c, int x0, int x1) {
        BlockState air = b(Blocks.AIR);
        for (int dx = x0; dx <= x1; dx++) for (int dz = -R - 2; dz <= R + 2; dz++) {
            int x = c.getX() + dx, z = c.getZ() + dz;
            boolean dentro = Math.abs(dx) <= R && Math.abs(dz) <= R;
            k.s(x, c.getY() - 1, z, dentro && ((dx + dz) & 7) == 0 ? k.p.parede() : k.p.chao());
            for (int i = 2; i < 30; i++) {
                BlockPos below = new BlockPos(x, c.getY() - i, z);
                BlockState s = k.L.getBlockState(below);
                if (!s.isAir() && !s.liquid() && !s.canBeReplaced()) break;
                k.s(below, k.p.parede());
            }
            for (int i = 0; i < 46; i++) {
                BlockPos above = new BlockPos(x, c.getY() + i, z);
                if (!k.L.getBlockState(above).isAir()) k.s(above, air);
            }
        }
    }

    private static void avenidas(Kit k, BlockPos c) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Direction lado = d.getClockWise();
            for (int i = 14; i <= R; i++) {
                BlockPos m = c.relative(d, i).below();
                for (int w = -3; w <= 3; w++) k.s(m.relative(lado, w), Math.abs(w) == 3 ? k.p.faixa() : (w == 0 && i % 4 == 0 ? k.p.luz() : k.p.parede()));
                if (i % 8 == 0 && i < R - 3 && !(d == Direction.SOUTH && i >= 26 && i <= 54)) {
                    k.poste(m.relative(lado, 4).above(), 3);
                    k.poste(m.relative(lado, -4).above(), 3);
                }
            }
        }
    }

    private static void praca(Kit k, BlockPos c) {
        for (int dx = -14; dx <= 14; dx++) for (int dz = -14; dz <= 14; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 14.5) continue;
            BlockState s = d > 13.5 ? k.p.faixa() : d > 12.5 ? k.p.destaque() : ((dx + dz) % 2 == 0 ? k.p.chao() : k.p.parede());
            k.s(c.offset(dx, -1, dz), s);
        }
        // fontes nas diagonais
        for (int[] f : new int[][]{{9, 9}, {-9, 9}, {9, -9}, {-9, -9}}) {
            BlockPos fc = c.offset(f[0], 0, f[1]);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                boolean borda = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                k.s(fc.offset(dx, -1, dz), borda ? k.p.faixa() : k.p.luz());
                k.s(fc.offset(dx, 0, dz), borda ? lajeBaixa(k.p.laje()) : b(Blocks.WATER));
            }
            k.s(fc.above(), k.p.pilar());
            k.s(fc.above(2), k.p.destaque());
        }
        // bancos e vasos em volta
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12 + Math.PI / 12;
            int x = (int) Math.round(Math.cos(a) * 12), z = (int) Math.round(Math.sin(a) * 12);
            if (Math.abs(x) <= 4 || Math.abs(z) <= 4) continue;
            k.s(c.offset(x, 0, z), i % 2 == 0 ? lajeBaixa(k.p.laje()) : b(Blocks.FLOWERING_AZALEA));
        }
    }

    private static BlockState lajeBaixa(Block laje) {
        return laje.defaultBlockState();
    }

    private static void muralha(Kit k, BlockPos c) {
        for (int i = -R; i <= R; i++) {
            for (int[] p : new int[][]{{i, -R}, {i, R}, {-R, i}, {R, i}}) {
                boolean portao = Math.abs(p[0]) <= 3 && Math.abs(p[1]) == R || Math.abs(p[1]) <= 3 && Math.abs(p[0]) == R;
                if (portao) continue;
                for (int h = 0; h < 5; h++) k.s(c.offset(p[0], h, p[1]), h == 4 ? k.p.faixa() : k.p.parede());
                if ((i & 1) == 0) k.s(c.offset(p[0], 5, p[1]), k.p.parede());
                if (i % 12 == 0) k.s(c.offset(p[0], 6, p[1]), k.p.luz());
            }
        }
        // arcos dos portões
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos g = c.relative(d, R);
            Direction lado = d.getClockWise();
            for (int s : new int[]{-4, 4}) for (int h = 0; h < 9; h++) k.s(g.relative(lado, s).above(h), h == 8 ? k.p.destaque() : k.p.pilar());
            for (int w = -4; w <= 4; w++) { k.s(g.relative(lado, w).above(7), k.p.faixa()); k.s(g.relative(lado, w).above(8), w % 2 == 0 ? k.p.luz() : k.p.faixa()); }
            k.estandarte(g.relative(lado, -5).relative(d.getOpposite()), 5);
            k.estandarte(g.relative(lado, 5).relative(d.getOpposite()), 5);
        }
        // torres nos cantos
        for (int[] t : new int[][]{{R, R}, {-R, R}, {R, -R}, {-R, -R}}) {
            BlockPos tc = c.offset(t[0], 0, t[1]);
            for (int h = 0; h < 14; h++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                boolean borda = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                if (!borda && h > 0 && h < 13) { k.s(tc.offset(dx, h, dz), b(Blocks.AIR)); continue; }
                k.s(tc.offset(dx, h, dz), h >= 10 && h <= 11 && borda && Math.abs(dx) != Math.abs(dz) ? k.p.vidro() : (h == 13 ? k.p.teto() : k.p.parede()));
            }
            k.s(tc.above(14), k.p.destaque());
            k.s(tc.above(12), k.p.luz());
        }
    }

    // ===================== Quartel-General =====================

    /** QG: centro (0, -38), 29 x 21, três andares de 6. */
    private static void qgCasca(Kit k, BlockPos c) {
        BlockPos q = c.offset(0, 0, -38);
        k.predio(q, 14, 10, 3, 6, Direction.SOUTH);
        // escadas: térreo -> 1º pela parede oeste; 1º -> 2º pela parede leste
        k.escada(q.offset(-12, 0, -8), Direction.SOUTH, 7);
        k.escada(q.offset(12, 7, -8), Direction.SOUTH, 6);
        // varanda no 2º andar (de frente para a praça)
        for (int dx = -6; dx <= 6; dx++) for (int dz = 11; dz <= 12; dz++) k.s(q.offset(dx, 12, dz), k.p.faixa());
        for (int dx = -6; dx <= 6; dx++) k.s(q.offset(dx, 13, 12), k.p.painel().defaultBlockState());
        for (int dx = -2; dx <= 2; dx++) for (int h = 13; h <= 15; h++) k.s(q.offset(dx, h, 10), b(Blocks.AIR));
        // estandartes gigantes na fachada
        for (int dx : new int[]{-9, 9}) for (int h = 6; h < 17; h++) k.s(q.offset(dx, h, 11), h == 16 ? k.p.destaque() : b(k.p.la()));
        // torre de antena
        for (int h = 19; h < 26; h++) k.s(q.offset(0, h, -6), h == 25 ? k.p.luz() : b(Blocks.LIGHTNING_ROD));
    }

    private static void qgInterior(Kit k, BlockPos c, PlanetLore planet) {
        BlockPos q = c.offset(0, 0, -38);
        // ---- Térreo: recepção e contratos ----
        for (int dx = -6; dx <= 6; dx++) k.s(q.offset(dx, 0, 2), lajeAlta(k.p.laje()));
        k.s(q.offset(-6, 0, 3), lajeAlta(k.p.laje()));
        k.s(q.offset(6, 0, 3), lajeAlta(k.p.laje()));
        k.quadro(q.offset(1, 1, -9), Direction.SOUTH);
        for (int dx = -5; dx <= 5; dx += 10) for (int h = 1; h <= 3; h++) k.s(q.offset(dx, h, -9), k.p.vidro());
        for (int dz = 6; dz <= 8; dz++) for (int dx : new int[]{-10, -9, -8, 8, 9, 10}) k.s(q.offset(dx, 0, dz), lajeBaixa(k.p.laje()));
        for (int dz : new int[]{-4, 4}) { k.estandarte(q.offset(-13, 0, dz), 4); k.estandarte(q.offset(13, 0, dz), 4); }
        k.s(q.offset(-13, 0, 9), b(Blocks.POTTED_FERN));
        k.s(q.offset(13, 0, 9), b(Blocks.POTTED_FERN));
        k.s(q.offset(9, 0, -8), virado(NaveBlocks.NAVE_CONSOLE.get(), Direction.SOUTH));
        k.s(q.offset(10, 0, -8), virado(NaveBlocks.NAVE_CONSOLE.get(), Direction.SOUTH));
        k.s(q.offset(-9, 0, -2), b(NaveBlocks.NAVE_HOLOMAPA.get()));
        // ---- 1º andar: sala de briefing ----
        int y1 = 7;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= 3.3 && d > 1.4) k.s(q.offset(dx, y1, dz + 1), lajeAlta(Blocks.SMOOTH_QUARTZ_SLAB));
            if (d <= 1.4) k.s(q.offset(dx, y1, dz + 1), k.p.destaque());
        }
        k.s(q.offset(0, y1 + 1, 1), b(NaveBlocks.NAVE_HOLOMAPA.get()));
        for (Direction d : Direction.Plane.HORIZONTAL) for (int w = -2; w <= 2; w += 2) {
            k.s(q.offset(0, y1, 1).relative(d, 5).relative(d.getClockWise(), w), degrau(Blocks.QUARTZ_STAIRS, d));
        }
        for (int dx = -9; dx <= 9; dx++) {
            if (Math.abs(dx) >= 10) continue;
            k.s(q.offset(dx, y1, -9), virado(NaveBlocks.NAVE_CONSOLE.get(), Direction.SOUTH));
            k.s(q.offset(dx, y1 + 2, -10), (dx & 1) == 0 ? k.p.vidro() : k.p.luz());
            k.s(q.offset(dx, y1 + 3, -10), (dx & 1) == 0 ? k.p.luz() : k.p.vidro());
        }
        // ---- 2º andar: sala do Comandante ----
        int y2 = 13;
        for (int dx = -3; dx <= 3; dx++) k.s(q.offset(dx, y2, -5), lajeAlta(k.p.laje()));
        k.s(q.offset(0, y2, -4), virado(Blocks.LECTERN, Direction.SOUTH));
        for (int dz = -9; dz <= 9; dz++) for (int h = 0; h < 3; h++) k.s(q.offset(-13, y2 + h, dz), h == 2 && dz % 3 == 0 ? b(Blocks.LANTERN) : b(Blocks.BOOKSHELF));
        for (int dx = -9; dx <= -6; dx++) k.s(q.offset(dx, y2, 4), b(Blocks.CARTOGRAPHY_TABLE));
        k.s(q.offset(7, y2, 5), b(NaveBlocks.NAVE_HOLOMAPA.get()));
        for (int dx = -3; dx <= 3; dx++) for (int dz = -1; dz <= 3; dz++) k.s(q.offset(dx, y2, dz), (Math.abs(dx) == 3 || dz == -1 || dz == 3) ? k.p.faixa().getBlock() == Blocks.AIR ? b(Blocks.AIR) : b(k.p.tapete()) : b(Blocks.WHITE_CARPET));
        k.estandarte(q.offset(-4, y2, -9), 4);
        k.estandarte(q.offset(4, y2, -9), 4);
        k.s(q.offset(0, y2 + 1, -9), k.p.destaque());
        k.s(q.offset(0, y2 + 2, -9), k.p.nucleo());
    }

    // ===================== Arquivo =====================

    private static void arquivo(Kit k, BlockPos c) {
        BlockPos a = c.offset(36, 0, -38);
        k.predio(a, 11, 9, 1, 7, Direction.SOUTH);
        for (int x : new int[]{-8, -5, 5, 8}) for (int dz = -7; dz <= -1; dz++) for (int h = 0; h < 4; h++) {
            k.s(a.offset(x, h, dz), h == 3 ? lajeAlta(k.p.laje()) : (dz == -1 && h == 2 ? b(Blocks.LANTERN) : b(Blocks.BOOKSHELF)));
        }
        // globo estelar no centro
        BlockPos g = a.offset(0, 3, -3);
        for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d <= 2.3 && d > 1.4) k.s(g.offset(dx, dy, dz), k.p.vidro());
            else if (d <= 1.0) k.s(g.offset(dx, dy, dz), k.p.nucleo());
        }
        k.s(a.offset(0, 0, -3), k.p.pilar());
        for (int dx = -1; dx <= 1; dx++) for (int dz = -4; dz <= -2; dz++) if (dx != 0 || dz != -3) k.s(a.offset(dx, 0, dz), lajeAlta(k.p.laje()));
        for (int dx : new int[]{-3, 3}) k.s(a.offset(dx, 0, 4), virado(Blocks.LECTERN, Direction.SOUTH));
        for (int dx = 6; dx <= 9; dx++) k.s(a.offset(dx, 0, 6), b(Blocks.CARTOGRAPHY_TABLE));
        for (int dx = -9; dx <= -6; dx++) k.s(a.offset(dx, 0, 6), b(Blocks.CHISELED_BOOKSHELF));
        k.s(a.offset(-10, 0, 0), b(Blocks.POTTED_AZALEA));
        k.s(a.offset(10, 0, 0), b(Blocks.POTTED_AZALEA));
    }

    // ===================== Celas de energia =====================

    private static void celas(Kit k, BlockPos c) {
        BlockPos a = c.offset(-36, 0, -38);
        k.predio(a, 11, 9, 1, 6, Direction.SOUTH);
        // corredor x -2..2; celas dos dois lados
        for (int side : new int[]{-1, 1}) {
            for (int cell = 0; cell < 3; cell++) {
                int z0 = -8 + cell * 5;
                int frente = side * 3;
                for (int dz = z0; dz <= z0 + 4; dz++) for (int h = 0; h < 5; h++) {
                    boolean divisoria = dz == z0 || dz == z0 + 4;
                    if (divisoria) for (int x = 3; x <= 10; x++) k.s(a.offset(side * x, h, dz), k.p.parede());
                    else k.s(a.offset(frente, h, dz), h <= 2 ? b(Blocks.IRON_BARS) : (h == 3 ? k.p.painel().defaultBlockState() : k.p.faixa()));
                }
                k.cama(Blocks.GRAY_BED, a.offset(side * 8, 0, z0 + 2), side > 0 ? Direction.EAST : Direction.WEST);
                k.s(a.offset(side * 9, 0, z0 + 1), b(Blocks.CAULDRON));
                k.s(a.offset(side * 6, 3, z0 + 2), k.p.luz());
            }
        }
        // mesa do carcereiro
        for (int dx = -2; dx <= 2; dx++) k.s(a.offset(dx, 0, 6), lajeAlta(k.p.laje()));
        k.s(a.offset(2, 1, 6), b(Blocks.LANTERN));
        k.s(a.offset(-2, 0, 8), b(Blocks.BARREL));
    }

    // ===================== Academia e arena =====================

    private static void academia(Kit k, BlockPos c) {
        BlockPos a = c.offset(-38, 0, -14);
        for (int dx = -14; dx <= 14; dx++) for (int dz = -9; dz <= 8; dz++) {
            boolean borda = Math.abs(dx) == 14 || dz == -9 || dz == 8;
            k.s(a.offset(dx, -1, dz), borda ? k.p.faixa() : ((dx & 1) == ((dz & 1)) ? k.p.parede() : k.p.chao()));
            if (borda && dz != 8 && (dx % 4 == 0 || Math.abs(dx) == 14)) k.poste(a.offset(dx, 0, dz), 2);
        }
        // alvos na parede oeste
        for (int dz = -6; dz <= 4; dz += 2) { k.s(a.offset(-13, 1, dz), b(Blocks.TARGET)); k.s(a.offset(-13, 0, dz), k.p.parede()); }
        // anéis de voo
        for (int[] r : new int[][]{{-4, 9}, {6, 12}}) {
            BlockPos rc = a.offset(r[0], r[1], 0);
            for (int i = 0; i < 24; i++) {
                double ang = Math.PI * 2 * i / 24;
                k.s(rc.offset(0, (int) Math.round(Math.sin(ang) * 3), (int) Math.round(Math.cos(ang) * 3)), i % 4 == 0 ? k.p.luz() : k.p.vidro());
            }
        }
        // arena (sul da avenida)
        BlockPos ar = c.offset(-38, 0, 14);
        for (int dx = -11; dx <= 11; dx++) for (int dz = -11; dz <= 11; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 11.5) continue;
            if (d <= 8) k.s(ar.offset(dx, -1, dz), d > 7 ? k.p.faixa() : k.p.chao());
            else {
                int deg = (int) (d - 8);
                for (int h = 0; h <= deg; h++) k.s(ar.offset(dx, h, dz), h == deg ? lajeAlta(k.p.laje()) : k.p.parede());
            }
        }
        for (int[] t : new int[][]{{0, -9}, {0, 9}}) for (int h = 0; h < 4; h++) k.s(ar.offset(t[0], h, t[1]), b(Blocks.AIR)); // entradas
        k.estandarte(ar.offset(-8, 0, 0), 4);
        k.estandarte(ar.offset(8, 0, 0), 4);
    }

    // ===================== Mercado =====================

    private static void mercado(Kit k, BlockPos c) {
        Block[] toldos = {k.p.la(), Blocks.WHITE_WOOL, Blocks.ORANGE_WOOL, Blocks.LIGHT_BLUE_WOOL};
        BlockState[][] bens = {
                {b(Blocks.HAY_BLOCK), b(Blocks.MELON), b(Blocks.PUMPKIN)},
                {b(Blocks.CARTOGRAPHY_TABLE), b(Blocks.LECTERN), b(Blocks.BARREL)},
                {b(Blocks.AMETHYST_BLOCK), k.p.destaque(), b(Blocks.LAPIS_BLOCK)},
                {b(Blocks.MELON), b(Blocks.CAKE), b(Blocks.HAY_BLOCK)}};
        for (int i = 0; i < 4; i++) {
            BlockPos s = c.offset(22 + i * 8, 0, -8);
            for (int dx = -2; dx <= 2; dx++) k.s(s.offset(dx, 0, 1), dx == 0 ? b(Blocks.BARREL) : lajeAlta(Blocks.SMOOTH_QUARTZ_SLAB));
            for (int[] pp : new int[][]{{-2, 1}, {2, 1}, {-2, -1}, {2, -1}}) for (int h = 0; h < 3; h++) k.s(s.offset(pp[0], h, pp[1]), b(Blocks.SPRUCE_FENCE));
            for (int dx = -3; dx <= 3; dx++) for (int dz = -1; dz <= 2; dz++) {
                Block w = (dx & 1) == 0 ? toldos[i] : Blocks.WHITE_WOOL;
                k.s(s.offset(dx, 3, dz), b(w));
            }
            k.s(s.offset(-1, 1, 1), bens[i][0]);
            k.s(s.offset(1, 1, 1), bens[i][1]);
            k.s(s.offset(-1, 0, -1), b(Blocks.CHEST));
            k.s(s.offset(1, 0, -1), bens[i][2]);
            k.s(s.offset(0, 2, 0), b(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
        }
        for (int i = 0; i < 2; i++) loja(k, c.offset(28 + i * 18, 0, 13));
    }

    /** Loja com vitrine para a avenida, balcão e prateleiras. */
    private static void loja(Kit k, BlockPos l) {
        k.predio(l, 6, 5, 1, 5, Direction.NORTH);
        for (int dx = -4; dx <= 4; dx++) if (Math.abs(dx) >= 2) for (int h = 1; h <= 2; h++) k.s(l.offset(dx, h, -5), k.p.vidro());
        for (int dx = -4; dx <= 2; dx++) k.s(l.offset(dx, 0, 0), lajeAlta(Blocks.SMOOTH_QUARTZ_SLAB));
        k.s(l.offset(2, 0, -1), lajeAlta(Blocks.SMOOTH_QUARTZ_SLAB));
        for (int dx = -5; dx <= 5; dx++) {
            k.s(l.offset(dx, 0, 4), (dx & 1) == 0 ? b(Blocks.BARREL) : b(Blocks.CHISELED_BOOKSHELF));
            k.s(l.offset(dx, 1, 4), dx % 3 == 0 ? b(Blocks.LANTERN) : lajeAlta(Blocks.SPRUCE_SLAB));
            k.s(l.offset(dx, 2, 4), (dx & 1) == 0 ? b(Blocks.DECORATED_POT) : b(Blocks.FLOWER_POT));
        }
        k.s(l.offset(-4, 0, -4), b(Blocks.SMOOTH_QUARTZ));
        k.s(l.offset(-4, 1, -4), b(Blocks.AMETHYST_CLUSTER));
        k.s(l.offset(4, 0, -4), b(Blocks.SMOOTH_QUARTZ));
        k.s(l.offset(4, 1, -4), b(Blocks.END_ROD));
    }

    // ===================== Enfermaria =====================

    private static void enfermaria(Kit k, BlockPos c) {
        BlockPos e = c.offset(36, 0, 38);
        k.predio(e, 11, 9, 1, 6, Direction.NORTH);
        for (int i = 0; i < 3; i++) {
            int dz = -5 + i * 4;
            k.cama(Blocks.WHITE_BED, e.offset(-8, 0, dz), Direction.WEST);
            k.cama(Blocks.WHITE_BED, e.offset(8, 0, dz), Direction.EAST);
            k.s(e.offset(-9, 0, dz + 1), b(Blocks.WHITE_CARPET));
            for (int h = 0; h < 3; h++) { k.s(e.offset(-7, h, dz + 2), b(Blocks.WHITE_STAINED_GLASS_PANE)); k.s(e.offset(7, h, dz + 2), b(Blocks.WHITE_STAINED_GLASS_PANE)); }
        }
        // poço de cura no centro
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            k.s(e.offset(dx, -2, dz + 1), k.p.luz());
            k.s(e.offset(dx, -1, dz + 1), b(Blocks.WATER));
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) if (Math.abs(dx) == 2 || Math.abs(dz) == 2) k.s(e.offset(dx, -1, dz + 1), k.p.faixa());
        // laboratório
        for (int dx = 4; dx <= 9; dx++) k.s(e.offset(dx, 0, 8), (dx & 1) == 0 ? b(Blocks.BREWING_STAND) : lajeAlta(Blocks.SMOOTH_QUARTZ_SLAB));
        for (int dx = -9; dx <= -4; dx++) k.s(e.offset(dx, 0, 8), (dx & 1) == 0 ? b(Blocks.CAULDRON) : b(Blocks.BARREL));
    }

    // ===================== Hangar / porto espacial =====================

    private static void hangar(Kit k, BlockPos c) {
        BlockPos h = c.offset(0, 0, 40);
        int W = 16, D = 12, H = 12;
        for (int dx = -W; dx <= W; dx++) for (int dz = -D; dz <= D; dz++) {
            boolean lateral = Math.abs(dx) == W;
            boolean listra = Math.abs(dx) == 5 || Math.abs(dx) == 13;
            if (Math.abs(dx) > 3) k.s(h.offset(dx, -1, dz), listra ? b(Blocks.YELLOW_CONCRETE) : b(Blocks.GRAY_CONCRETE));
            if (lateral) for (int y = 0; y < H; y++) k.s(h.offset(dx, y, dz), dz % 4 == 0 ? k.p.pilar() : (y % 4 == 3 ? k.p.faixa() : b(Blocks.LIGHT_GRAY_CONCRETE)));
            k.s(h.offset(dx, H, dz), (Math.floorMod(dx, 4) == 0 && Math.floorMod(dz, 4) == 0) ? k.p.luz() : (Math.abs(dx) <= 3 ? k.p.vidro() : b(Blocks.GRAY_CONCRETE)));
            if (Math.abs(dz) == D && (Math.abs(dx) == W || Math.abs(dx) == W - 1)) for (int y = 0; y < H; y++) k.s(h.offset(dx, y, dz), k.p.pilar());
        }
        for (int dx = -W; dx <= W; dx++) for (int dz : new int[]{-D, D}) k.s(h.offset(dx, H - 1, dz), k.p.faixa());
        // trilhos e guindastes sobre as baias
        for (int side : new int[]{-1, 1}) {
            for (int dz = -D + 1; dz < D; dz++) { k.s(h.offset(side * 6, H - 1, dz), b(Blocks.IRON_BLOCK)); k.s(h.offset(side * 12, H - 1, dz), b(Blocks.IRON_BLOCK)); }
            for (int dx = 6; dx <= 12; dx++) k.s(h.offset(side * dx, H - 2, -3), b(Blocks.IRON_BLOCK));
            for (int y = 4; y < H - 2; y++) k.s(h.offset(side * 9, y, -3), b(Blocks.CHAIN));
            k.s(h.offset(side * 9, 3, -3), b(Blocks.ANVIL));
            // oficina na parede
            for (int dz = -10; dz <= 10; dz++) {
                BlockState st = switch (Math.floorMod(dz, 5)) {
                    case 0 -> b(Blocks.SMITHING_TABLE);
                    case 1 -> b(Blocks.BLAST_FURNACE);
                    case 2 -> b(Blocks.BARREL);
                    case 3 -> b(Blocks.GRINDSTONE);
                    default -> virado(NaveBlocks.NAVE_RECARGA.get(), side > 0 ? Direction.WEST : Direction.EAST);
                };
                k.s(h.offset(side * (W - 1), 0, dz), st);
                k.s(h.offset(side * (W - 1), 2, dz), dz % 3 == 0 ? b(Blocks.LANTERN) : lajeAlta(Blocks.SMOOTH_STONE_SLAB));
            }
        }
        // naves estacionadas (trancadas: são da cidade)
        NaveEntity n1 = NaveEntity.create(k.L, NaveTipo.NAVE_PATRULHA, cor(k));
        n1.moveTo(h.getX() - 9 + 0.5, h.getY(), h.getZ() + 2.5, 180, 0);
        n1.setDono(DONO_CIDADE);
        k.L.addFreshEntity(n1);
        NaveEntity n2 = NaveEntity.create(k.L, NaveTipo.NAVE_LANCEIRA, cor(k));
        n2.moveTo(h.getX() + 9 + 0.5, h.getY(), h.getZ() + 2.5, 180, 0);
        n2.setDono(DONO_CIDADE);
        k.L.addFreshEntity(n2);
    }

    private static String cor(Kit k) {
        PlanetLore pl = PlanetLore.byDimension(k.L.dimension());
        return pl != null && pl.corps != BuiltinCorps.WHITE ? pl.corps.id().getPath() : "green";
    }

    // ===================== Casas =====================

    private static void casa(Kit k, BlockPos o, Direction porta) {
        k.predio(o, 5, 4, 1, 4, porta);
        int f = porta == Direction.SOUTH ? 1 : -1; // "frente" no eixo z
        k.cama(k.p.cama(), o.offset(-4, 0, -2 * f), porta.getOpposite());
        k.s(o.offset(-3, 0, -3 * f), b(Blocks.BARREL));
        k.s(o.offset(-3, 1, -3 * f), b(Blocks.LANTERN));
        k.s(o.offset(-4, 0, 1 * f), virado(NaveBlocks.NAVE_ARMARIO.get(), Direction.EAST));
        k.s(o.offset(-4, 0, 2 * f), b(Blocks.CHEST));
        for (int dx = -3; dx <= -1; dx++) for (int dz = -1; dz <= 1; dz++) k.s(o.offset(dx, 0, dz), b(k.p.tapete()));
        k.mesa(o.offset(1, 0, 0));
        k.s(o.offset(4, 0, -3 * f), virado(Blocks.SMOKER, Direction.WEST));
        k.s(o.offset(4, 0, -2 * f), b(Blocks.CAULDRON));
        k.s(o.offset(4, 0, -1 * f), virado(Blocks.FURNACE, Direction.WEST));
        k.s(o.offset(3, 0, -3 * f), b(Blocks.CRAFTING_TABLE));
        k.s(o.offset(4, 0, 2 * f), b(Blocks.BOOKSHELF));
        k.s(o.offset(4, 1, 2 * f), b(Blocks.BOOKSHELF));
        k.s(o.offset(3, 0, 3 * f), b(Blocks.POTTED_FERN));
        // jardim na frente
        BlockPos fr = o.relative(porta, 6);
        for (int dx : new int[]{-4, -3, 3, 4}) k.s(fr.offset(dx, -1, 0), b(Blocks.GRASS_BLOCK));
        for (int dx : new int[]{-4, 4}) k.s(fr.offset(dx, 0, 0), b(Blocks.AZALEA));
    }

    /** Anéis de luz no céu (teste de voo do Engenheiro). */
    private static void aneis(Kit k, BlockPos c) {
        for (BlockPos r : CidadeMissoes.aneisCidade()) {
            BlockPos rc = r.atY(altura(k.L, r.getX(), r.getZ()) + r.getY());
            for (int i = 0; i < 28; i++) {
                double a = Math.PI * 2 * i / 28;
                int ox = (int) Math.round(Math.cos(a) * 4);
                if (ox == 0) continue; // a coluna do meio fica livre (a altura do chão é medida ali)
                k.s(rc.offset(ox, (int) Math.round(Math.sin(a) * 4), 0), i % 4 == 0 ? k.p.luz() : k.p.vidro());
            }
        }
    }

    // ===================== Moradores =====================

    private static void moradores(ServerLevel l, BlockPos c, PlanetLore planet) {
        BuiltinCorps corps = planet.corps == BuiltinCorps.WHITE ? BuiltinCorps.GREEN : planet.corps;
        // QG
        CidadeNpcs.npc(l, c.offset(0, 0, -38), "contratos", corps, true, 0, 2);
        CidadeNpcs.npc(l, c.offset(0, 13, -45), "comandante", corps, true, 0, 2);
        CidadeNpcs.npc(l, c.offset(-4, 7, -34), "guarda", corps, false, 160, 4);
        CidadeNpcs.npc(l, c.offset(4, 7, -40), "guarda", corps, false, 200, 4);
        if (planet == PlanetLore.OA) CidadeNpcs.npc(l, c.offset(8, 0, -44), "arauto", corps, true, 180, 2);
        // prédios
        CidadeNpcs.npc(l, c.offset(36, 0, -33), "arquivista", corps, false, 0, 3);
        CidadeNpcs.npc(l, c.offset(-36, 0, -30), "carcereiro", corps, true, 0, 3);
        CidadeNpcs.npc(l, c.offset(-30, 0, -8), "instrutor", corps, true, 90, 4);
        CidadeNpcs.npc(l, c.offset(36, 0, 32), "medico", corps, false, 180, 3);
        CidadeNpcs.npc(l, c.offset(-12, 0, 32), "engenheiro", corps, false, 180, 4);
        CidadeNpcs.npc(l, c.offset(12, 0, 47), "estaleiro", corps, false, 180, 3);
        // mercado
        CidadeNpcs.npc(l, c.offset(22, 0, -9), "intendente", corps, false, 0, 1);
        CidadeNpcs.npc(l, c.offset(30, 0, -9), "cartografo", corps, false, 0, 1);
        CidadeNpcs.npc(l, c.offset(38, 0, -9), "reliquias", corps, false, 0, 1);
        CidadeNpcs.npc(l, c.offset(46, 0, -9), "cidadao_0", corps, false, 0, 1);
        CidadeNpcs.npc(l, c.offset(27, 0, 15), "armeiro", corps, false, 0, 1);
        CidadeNpcs.npc(l, c.offset(45, 0, 15), "boticario", corps, false, 0, 1);
        // cidadãos e guardas
        CidadeNpcs.npc(l, c.offset(-34, 0, 42), "cidadao_1", corps, false, 0, 12);
        CidadeNpcs.npc(l, c.offset(-6, 0, 10), "cidadao_2", corps, false, 0, 16);
        for (int[] g : new int[][]{{0, R - 4}, {0, -R + 4}, {R - 4, 0}, {-R + 4, 0}, {10, 0}, {-10, 0}}) {
            CidadeNpcs.npc(l, c.offset(g[0], 0, g[1]), "guarda", corps, false, 0, 10);
        }
        // presos (não fazem nada, não morrem)
        for (int[] p : new int[][]{{-42, -44}, {-30, -39}, {-42, -34}}) {
            LanternaEntity e = LanternaEntity.pirata(l, false);
            e.moveTo(c.getX() + p[0] + 0.5, c.getY(), c.getZ() + p[1] + 0.5, 90, 0);
            e.setNoAi(true);
            e.setInvulnerable(true);
            e.setPersistenceRequired();
            e.setFuncao("preso");
            l.addFreshEntity(e);
        }
        // alvos de treino na academia
        for (int i = 0; i < 4; i++) {
            Entity d = LoreEntities.ALVO_TREINO.get().create(l);
            if (d == null) continue;
            d.moveTo(c.getX() - 44 + i * 4 + 0.5, c.getY(), c.getZ() - 20 + 0.5, 0, 0);
            l.addFreshEntity(d);
        }
    }

    private Cidade() {}
}
