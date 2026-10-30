package com.lanterna.lore.world;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.lore.LoreBlocks;
import com.lanterna.lore.LoreEntities;
import com.lanterna.lore.PlanetLore;
import com.lanterna.lore.block.LoreBlockTypes;
import com.lanterna.naves.bloco.NaveBlocks;
import com.lanterna.naves.bloco.QuadroContratos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Cidade da Tropa: em cada planeta de Tropa, ao norte da base, uma cidade com a cara da Tropa.
 *
 *   Centro   praça redonda com a BATERIA CENTRAL gigante no formato da bateria da Tropa
 *            (lanterna de vigia, torre de lâminas, braseiro, farol, relicário, drusa, caveira; Nok: o cajado).
 *   Norte    Salão da Tropa: quadro de contratos, bancos, oficiais.
 *   Leste    Alojamento: beliches e armários.
 *   Oeste    Campo de treino: forja, bateria e alvos.
 *   Sul      Pouso de naves: círculo iluminado, estação de recarga e holomapa.
 *   Cantos   torres de vigia; ruas iluminadas ligando tudo; muralha baixa com portões.
 *   Lanternas da Tropa morando e patrulhando a cidade.
 */
public final class CidadeTropa {
    public static final BlockPos CENTRO = new BlockPos(0, 0, -150);
    private static final int R = 36;

    /** Materiais de cada Tropa. */
    private record Paleta(BlockState chao, BlockState parede, BlockState faixa, BlockState vidro, BlockState luz, BlockState teto,
                          BlockState destaque, BlockState nucleo) {}

    private static BlockState b(Block x) {
        return x.defaultBlockState();
    }

    private static Paleta paleta(PlanetLore p) {
        BlockState pedra = p.stoneBlock().defaultBlockState();
        return switch (p) {
            case OA -> new Paleta(b(LoreBlocks.ROCHA_POLIDA_VERIDIA.get()), b(Blocks.QUARTZ_BRICKS), b(Blocks.LIME_CONCRETE),
                    b(Blocks.LIME_STAINED_GLASS), b(Blocks.SEA_LANTERN), b(Blocks.GREEN_CONCRETE), b(Blocks.EMERALD_BLOCK), b(Blocks.VERDANT_FROGLIGHT));
            case KORUGAR -> new Paleta(pedra, b(Blocks.POLISHED_BLACKSTONE_BRICKS), b(Blocks.YELLOW_CONCRETE),
                    b(Blocks.YELLOW_STAINED_GLASS), b(Blocks.OCHRE_FROGLIGHT), b(Blocks.YELLOW_TERRACOTTA), b(Blocks.GOLD_BLOCK), b(Blocks.OCHRE_FROGLIGHT));
            case YSMAULT -> new Paleta(pedra, b(Blocks.RED_NETHER_BRICKS), b(Blocks.RED_CONCRETE),
                    b(Blocks.RED_STAINED_GLASS), b(Blocks.SHROOMLIGHT), b(Blocks.NETHER_WART_BLOCK), b(Blocks.MAGMA_BLOCK), b(Blocks.SHROOMLIGHT));
            case ODYM -> new Paleta(pedra, b(Blocks.PRISMARINE_BRICKS), b(Blocks.LIGHT_BLUE_CONCRETE),
                    b(Blocks.LIGHT_BLUE_STAINED_GLASS), b(Blocks.SEA_LANTERN), b(Blocks.DARK_PRISMARINE), b(Blocks.BLUE_CONCRETE), b(Blocks.SEA_LANTERN));
            case OKAARA -> new Paleta(pedra, b(Blocks.SMOOTH_SANDSTONE), b(Blocks.ORANGE_CONCRETE),
                    b(Blocks.ORANGE_STAINED_GLASS), b(Blocks.OCHRE_FROGLIGHT), b(Blocks.ORANGE_TERRACOTTA), b(Blocks.GOLD_BLOCK), b(Blocks.OCHRE_FROGLIGHT));
            case NOK -> new Paleta(pedra, b(Blocks.POLISHED_DEEPSLATE), b(Blocks.PURPLE_CONCRETE),
                    b(Blocks.PURPLE_STAINED_GLASS), lampCristal(), b(Blocks.BLUE_TERRACOTTA), b(Blocks.AMETHYST_BLOCK), b(Blocks.PEARLESCENT_FROGLIGHT));
            case ZAMARON -> new Paleta(pedra, b(Blocks.CALCITE), b(Blocks.MAGENTA_CONCRETE),
                    b(Blocks.MAGENTA_STAINED_GLASS), lampCristal(), b(Blocks.PURPLE_CONCRETE), b(Blocks.AMETHYST_BLOCK), b(Blocks.PEARLESCENT_FROGLIGHT));
            case RYUT -> new Paleta(pedra, b(Blocks.POLISHED_BLACKSTONE_BRICKS), b(Blocks.BLACK_CONCRETE),
                    b(Blocks.TINTED_GLASS), b(Blocks.CRYING_OBSIDIAN), b(Blocks.BLACKSTONE), b(Blocks.OBSIDIAN), b(Blocks.CRYING_OBSIDIAN));
            default -> new Paleta(pedra, b(Blocks.QUARTZ_BRICKS), b(Blocks.WHITE_CONCRETE),
                    b(Blocks.WHITE_STAINED_GLASS), b(Blocks.SEA_LANTERN), b(Blocks.WHITE_CONCRETE), b(Blocks.QUARTZ_BLOCK), b(Blocks.SEA_LANTERN));
        };
    }

    private static BlockState lampCristal() {
        return LoreBlocks.CRISTAL_DEVOTO_AGLOMERADO.get().defaultBlockState().setValue(LoreBlockTypes.CristalDevoto.ACESO, true);
    }

    // ===================== Construção =====================

    public static void construir(ServerLevel level, PlanetLore planet) {
        Paleta pal = paleta(planet);
        BlockPos c = CENTRO;
        int y = chao(level, c);
        terreno(level, c, y, pal);
        ruas(level, c, y, pal);
        praca(level, c, y, pal);
        bateria(level, c.atY(y), planet, pal);
        salao(level, c.offset(0, 0, -21).atY(y), pal);
        alojamento(level, c.offset(21, 0, 0).atY(y), pal);
        treino(level, c.offset(-21, 0, 0).atY(y), planet, pal);
        pouso(level, c.offset(0, 0, 22).atY(y), pal);
        for (int[] k : new int[][]{{-24, -24}, {24, -24}, {-24, 24}, {24, 24}}) torre(level, c.offset(k[0], 0, k[1]).atY(y), pal);
        muralha(level, c, y, pal);
        moradores(level, c.atY(y), planet);
        LanternaMod.LOGGER.info("Lanterna: cidade da Tropa construída em {}", planet.planet);
    }

    private static int chao(ServerLevel level, BlockPos c) {
        int sum = 0, n = 0;
        for (int dx = -R; dx <= R; dx += 12) {
            for (int dz = -R; dz <= R; dz += 12) {
                level.getChunk((c.getX() + dx) >> 4, (c.getZ() + dz) >> 4);
                sum += level.getHeight(Heightmap.Types.WORLD_SURFACE, c.getX() + dx, c.getZ() + dz);
                n++;
            }
        }
        int y = sum / Math.max(1, n);
        return Math.max(level.getMinBuildHeight() + 10, Math.min(y, level.getMaxBuildHeight() - 60));
    }

    private static void set(ServerLevel level, BlockPos p, BlockState s) {
        level.setBlock(p, s, 2);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState s) {
        level.setBlock(new BlockPos(x, y, z), s, 2);
    }

    /** Chão plano redondo: aterra embaixo e limpa (até 40 blocos) em cima. */
    private static void terreno(ServerLevel level, BlockPos c, int y, Paleta pal) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > R + 0.5) continue;
                int x = c.getX() + dx, z = c.getZ() + dz;
                set(level, x, y - 1, z, pal.chao);
                for (int k = 2; k < 24; k++) {
                    BlockPos below = new BlockPos(x, y - k, z);
                    BlockState s = level.getBlockState(below);
                    if (!s.isAir() && !s.liquid() && !s.canBeReplaced()) break;
                    set(level, below, pal.parede);
                }
                for (int k = 0; k < 40; k++) {
                    BlockPos above = new BlockPos(x, y + k, z);
                    if (!level.getBlockState(above).isAir()) set(level, above, air);
                }
            }
        }
    }

    /** Quatro ruas largas da praça até a muralha, com postes de luz. */
    private static void ruas(ServerLevel level, BlockPos c, int y, Paleta pal) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Direction lado = d.getClockWise();
            for (int i = 9; i <= R; i++) {
                BlockPos m = c.relative(d, i).atY(y - 1);
                for (int w = -2; w <= 2; w++) set(level, m.relative(lado, w), Math.abs(w) == 2 ? pal.faixa : pal.parede);
                if (i % 6 == 0 && i < R - 2) {
                    for (int s = -3; s <= 3; s += 6) {
                        BlockPos post = m.relative(lado, s).above();
                        set(level, post, pal.parede);
                        set(level, post.above(), pal.parede);
                        set(level, post.above(2), pal.luz);
                    }
                }
            }
        }
    }

    /** Praça redonda em volta da bateria, com anel colorido e bancos. */
    private static void praca(ServerLevel level, BlockPos c, int y, Paleta pal) {
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 9.5) continue;
                BlockState s = d > 8.5 ? pal.faixa : (d > 7.5 ? pal.destaque : ((dx + dz) % 2 == 0 ? pal.chao : pal.parede));
                set(level, c.getX() + dx, y - 1, c.getZ() + dz, s);
            }
        }
        // luzes no chão em volta
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            set(level, c.getX() + (int) Math.round(Math.cos(a) * 8), y - 1, c.getZ() + (int) Math.round(Math.sin(a) * 8), pal.luz);
        }
    }

    // ===================== A Bateria Central =====================

    private static void bateria(ServerLevel level, BlockPos base, PlanetLore planet, Paleta pal) {
        // degraus de base
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            set(level, base.offset(dx, 0, dz), Math.max(Math.abs(dx), Math.abs(dz)) == 4 ? pal.faixa : pal.parede);
            if (Math.max(Math.abs(dx), Math.abs(dz)) <= 3) set(level, base.offset(dx, 1, dz), pal.parede);
        }
        BlockPos b = base.above(2);
        switch (planet.corps) {
            case GREEN -> lanterna(level, b, pal);
            case YELLOW -> torreLaminas(level, b, pal);
            case RED -> braseiro(level, b, pal);
            case BLUE -> farol(level, b, pal);
            case ORANGE -> relicario(level, b, pal);
            case VIOLET -> drusa(level, b, pal);
            case BLACK -> caveira(level, b, pal);
            case INDIGO -> cajado(level, b, pal);
            default -> lanterna(level, b, pal);
        }
    }

    private static void disco(ServerLevel level, BlockPos c, double r, BlockState s) {
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) for (int dz = -ri; dz <= ri; dz++)
            if (dx * dx + dz * dz <= r * r + 0.5) set(level, c.offset(dx, 0, dz), s);
    }

    private static void anel(ServerLevel level, BlockPos c, double r, BlockState borda, BlockState dentro) {
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) for (int dz = -ri; dz <= ri; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > r + 0.5) continue;
            set(level, c.offset(dx, 0, dz), d > r - 0.5 ? borda : dentro);
        }
    }

    /** Verde: lanterna de vigia (corpo de vidro com núcleo de luz, tampa e alça). */
    private static void lanterna(ServerLevel level, BlockPos b, Paleta pal) {
        disco(level, b, 3.2, pal.faixa);
        for (int k = 1; k <= 9; k++) {
            anel(level, b.above(k), 3.2, pal.vidro, pal.nucleo);
            for (Direction d : Direction.Plane.HORIZONTAL) set(level, b.above(k).relative(d, 3), pal.faixa);
        }
        disco(level, b.above(10), 3.2, pal.faixa);
        disco(level, b.above(11), 2.2, pal.parede);
        disco(level, b.above(12), 1.2, pal.faixa);
        int[][] alca = {{-2, 13}, {2, 13}, {-2, 14}, {2, 14}, {-1, 15}, {1, 15}, {0, 15}};
        for (int[] a : alca) set(level, b.offset(a[0], a[1], 0), pal.destaque);
    }

    /** Amarelo: torre de lâminas (torre quadrada com lâminas saindo dos lados). */
    private static void torreLaminas(ServerLevel level, BlockPos b, Paleta pal) {
        for (int k = 0; k < 15; k++) {
            int r = k > 12 ? 1 : 2;
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                boolean borda = Math.abs(dx) == r || Math.abs(dz) == r;
                set(level, b.offset(dx, k, dz), borda ? ((Math.abs(dx) == r && Math.abs(dz) == r) ? pal.parede : pal.vidro) : pal.nucleo);
            }
        }
        for (int h : new int[]{3, 7, 11}) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                for (int i = 3; i <= 5; i++) {
                    set(level, b.above(h + (i - 3)).relative(d, i), pal.destaque);
                }
            }
        }
        for (int k = 15; k < 19; k++) set(level, b.above(k), pal.destaque);
    }

    /** Vermelho: braseiro (pedestal e uma bacia larga em chamas). */
    private static void braseiro(ServerLevel level, BlockPos b, Paleta pal) {
        for (int k = 0; k < 5; k++) disco(level, b.above(k), k < 1 ? 2.2 : 1.2, pal.parede);
        disco(level, b.above(5), 4.2, pal.faixa);
        for (int k = 6; k <= 7; k++) anel(level, b.above(k), 5.2, pal.parede, Blocks.NETHERRACK.defaultBlockState());
        disco(level, b.above(8), 4.2, Blocks.FIRE.defaultBlockState());
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int k = 0; k < 5; k++) set(level, b.above(k).relative(d, 3).relative(d.getClockWise(), 0), k == 4 ? pal.destaque : Blocks.AIR.defaultBlockState());
            set(level, b.above(6).relative(d, 5), pal.luz);
        }
    }

    /** Azul: farol (torre redonda que afina, com a sala de luz no topo). */
    private static void farol(ServerLevel level, BlockPos b, Paleta pal) {
        for (int k = 0; k < 16; k++) {
            double r = 4 - k * 0.14;
            anel(level, b.above(k), r, (k / 3) % 2 == 0 ? pal.parede : pal.faixa, Blocks.AIR.defaultBlockState());
        }
        for (int k = 16; k < 19; k++) anel(level, b.above(k), 2.2, pal.vidro, pal.nucleo);
        disco(level, b.above(19), 2.5, pal.teto);
        disco(level, b.above(20), 1.2, pal.teto);
        set(level, b.above(21), pal.luz);
    }

    /** Laranja: relicário (um cofre ornamentado, com âmbar brilhando nas janelas). */
    private static void relicario(ServerLevel level, BlockPos b, Paleta pal) {
        for (int k = 0; k < 7; k++) {
            for (int dx = -4; dx <= 4; dx++) for (int dz = -3; dz <= 3; dz++) {
                boolean borda = Math.abs(dx) == 4 || Math.abs(dz) == 3;
                boolean canto = Math.abs(dx) == 4 && Math.abs(dz) == 3;
                BlockState s = canto || k == 0 || k == 6 ? pal.destaque : borda ? (k % 3 == 1 ? pal.vidro : pal.parede) : pal.nucleo;
                set(level, b.offset(dx, k, dz), s);
            }
        }
        for (int k = 7; k <= 9; k++) {
            int rx = 4 - (k - 6), rz = 3 - (k - 6);
            for (int dx = -rx; dx <= rx; dx++) for (int dz = -Math.max(0, rz); dz <= Math.max(0, rz); dz++) set(level, b.offset(dx, k, dz), pal.teto);
        }
        set(level, b.above(10), pal.destaque);
        set(level, b.above(11), pal.luz);
    }

    /** Violeta: drusa (um aglomerado de cristais gigantes). */
    private static void drusa(ServerLevel level, BlockPos b, Paleta pal) {
        int[][] picos = {{0, 0, 16}, {3, 1, 10}, {-3, 2, 11}, {1, -3, 9}, {-2, -2, 12}, {2, 3, 7}, {-4, -1, 6}};
        for (int[] p : picos) {
            for (int k = 0; k < p[2]; k++) {
                double r = 1.6 * (1 - (double) k / p[2]) + 0.3;
                BlockPos c = b.offset(p[0] + (int) Math.round(p[0] * 0.08 * k), k, p[1] + (int) Math.round(p[1] * 0.08 * k));
                int ri = (int) Math.ceil(r);
                for (int dx = -ri; dx <= ri; dx++) for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dz * dz > r * r + 0.3) continue;
                    set(level, c.offset(dx, 0, dz), (dx == 0 && dz == 0) ? pal.nucleo : (k % 4 == 3 ? pal.vidro : pal.destaque));
                }
            }
        }
    }

    /** Preto: caveira (a cabeça sobre um pedestal, com os olhos acesos). */
    private static void caveira(ServerLevel level, BlockPos b, Paleta pal) {
        disco(level, b, 2.2, pal.parede);
        BlockPos c = b.above(7);
        for (int dx = -5; dx <= 5; dx++) for (int dy = -5; dy <= 5; dy++) for (int dz = -5; dz <= 5; dz++) {
            double d = Math.sqrt(dx * dx + dy * dy * 1.1 + dz * dz);
            if (d > 5.2 || d < 4.0) continue;
            set(level, c.offset(dx, dy, dz), Blocks.BONE_BLOCK.defaultBlockState());
        }
        // queixo
        for (int dx = -3; dx <= 3; dx++) for (int dz = 2; dz <= 5; dz++) set(level, c.offset(dx, -5, dz), Blocks.BONE_BLOCK.defaultBlockState());
        // olhos e nariz (de frente para o sul, onde fica a entrada da cidade)
        for (int ex : new int[]{-2, 2}) for (int dy = 0; dy <= 1; dy++) for (int ddx = 0; ddx <= 1; ddx++) {
            set(level, c.offset(ex + (ex < 0 ? -ddx : ddx), dy, 5), pal.luz);
            set(level, c.offset(ex + (ex < 0 ? -ddx : ddx), dy, 4), pal.luz);
        }
        set(level, c.offset(0, -2, 5), pal.parede);
        set(level, c.offset(0, -2, 4), pal.parede);
        for (int k = 1; k < 3; k++) disco(level, b.above(k), 1.2, pal.parede);
        disco(level, c, 3.5, pal.nucleo);
    }

    /** Índigo (Nok): o Cajado da Compaixão gigante, com o cristal no alto. */
    private static void cajado(ServerLevel level, BlockPos b, Paleta pal) {
        for (int k = 0; k < 16; k++) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                if (Math.abs(dx) + Math.abs(dz) <= 1) set(level, b.offset(dx, k, dz), k % 5 == 4 ? pal.faixa : pal.parede);
        }
        BlockPos top = b.above(18);
        for (int dx = -3; dx <= 3; dx++) for (int dy = -3; dy <= 3; dy++) for (int dz = -3; dz <= 3; dz++) {
            int m = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
            if (m > 3) continue;
            set(level, top.offset(dx, dy, dz), m <= 1 ? pal.nucleo : pal.vidro);
        }
        for (Direction d : Direction.Plane.HORIZONTAL) set(level, b.above(15).relative(d, 2), pal.destaque);
    }

    // ===================== Prédios =====================

    /** Prédio retangular oco, porta virada para a praça, faixa de janelas e luzes no teto. */
    private static void predio(ServerLevel level, BlockPos c, int hw, int hd, int h, Direction porta, Paleta pal) {
        for (int dx = -hw; dx <= hw; dx++) for (int dz = -hd; dz <= hd; dz++) {
            boolean borda = Math.abs(dx) == hw || Math.abs(dz) == hd;
            boolean canto = Math.abs(dx) == hw && Math.abs(dz) == hd;
            set(level, c.offset(dx, -1, dz), pal.chao);
            for (int k = 0; k < h; k++) {
                BlockState s;
                if (!borda) s = Blocks.AIR.defaultBlockState();
                else if (canto) s = pal.faixa;
                else if (k == 2 && (dx + dz) % 3 != 0) s = pal.vidro;
                else s = k == h - 1 ? pal.faixa : pal.parede;
                set(level, c.offset(dx, k, dz), s);
            }
            set(level, c.offset(dx, h, dz), borda ? pal.faixa : ((dx % 3 == 0 && dz % 3 == 0) ? pal.luz : pal.teto));
        }
        // porta (abertura de 3 x 3)
        BlockPos p = c.relative(porta, porta.getAxis() == Direction.Axis.X ? hw : hd);
        Direction lado = porta.getClockWise();
        for (int w = -1; w <= 1; w++) for (int k = 0; k < 3; k++) set(level, p.relative(lado, w).above(k), Blocks.AIR.defaultBlockState());
        set(level, p.relative(lado, -2).above(3), pal.luz);
        set(level, p.relative(lado, 2).above(3), pal.luz);
    }

    /** Norte: Salão da Tropa, com o quadro de contratos na parede do fundo. */
    private static void salao(ServerLevel level, BlockPos c, Paleta pal) {
        predio(level, c, 7, 5, 6, Direction.SOUTH, pal);
        // estrado e quadro no fundo (parede norte), virado para o sul
        for (int dx = -3; dx <= 3; dx++) set(level, c.offset(dx, 0, -4), pal.faixa);
        quadro(level, c.offset(1, 1, -4), Direction.SOUTH);
        // bancos
        for (int dz = -1; dz <= 2; dz += 3) for (int dx = -5; dx <= 5; dx++) {
            if (Math.abs(dx) <= 1) continue;
            set(level, c.offset(dx, 0, dz), Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
        }
        set(level, c.offset(-6, 0, -4), NaveBlocks.NAVE_HOLOMAPA.get().defaultBlockState());
        set(level, c.offset(6, 0, -4), NaveBlocks.NAVE_CONSOLE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
    }

    private static void quadro(ServerLevel level, BlockPos a, Direction facing) {
        BlockState q = NaveBlocks.QUADRO_CONTRATOS.get().defaultBlockState().setValue(QuadroContratos.FACING, facing);
        Direction esq = facing.getClockWise();
        set(level, a, q.setValue(QuadroContratos.PARTE, QuadroContratos.Parte.BAIXO_DIR));
        set(level, a.relative(esq), q.setValue(QuadroContratos.PARTE, QuadroContratos.Parte.BAIXO_ESQ));
        set(level, a.above(), q.setValue(QuadroContratos.PARTE, QuadroContratos.Parte.CIMA_DIR));
        set(level, a.above().relative(esq), q.setValue(QuadroContratos.PARTE, QuadroContratos.Parte.CIMA_ESQ));
    }

    /** Leste: alojamento com beliches e armários. */
    private static void alojamento(ServerLevel level, BlockPos c, Paleta pal) {
        predio(level, c, 5, 7, 5, Direction.WEST, pal);
        for (int i = 0; i < 4; i++) {
            int dz = -5 + i * 3;
            // beliche encostado na parede leste, cabeceira para o leste
            BlockPos foot = c.offset(3, 0, dz);
            BlockState bed = NaveBlocks.NAVE_BELICHE.get().defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
            set(level, foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
            set(level, foot.east(), bed.setValue(BedBlock.PART, BedPart.HEAD));
            set(level, c.offset(4, 0, dz + 1), NaveBlocks.NAVE_ARMARIO.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        }
        set(level, c.offset(-3, 0, -6), NaveBlocks.NAVE_RECARGA.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        set(level, c.offset(-3, 0, 6), Blocks.BARREL.defaultBlockState());
    }

    /** Oeste: campo de treino a céu aberto com forja, bateria e alvos. */
    private static void treino(ServerLevel level, BlockPos c, PlanetLore planet, Paleta pal) {
        for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
            boolean borda = Math.max(Math.abs(dx), Math.abs(dz)) == 7;
            set(level, c.offset(dx, -1, dz), borda ? pal.faixa : ((dx + dz) % 2 == 0 ? pal.parede : pal.chao));
            if (borda && (dx % 4 == 0 || dz % 4 == 0) && !(dx == 7 && Math.abs(dz) <= 2)) {
                set(level, c.offset(dx, 0, dz), pal.parede);
                set(level, c.offset(dx, 1, dz), pal.luz);
            }
        }
        set(level, c.offset(-4, 0, -4), LoreBlocks.FORJA.get().defaultBlockState());
        if (planet != PlanetLore.NOK) {
            BlockState s = com.lanterna.registry.ModBlocks.POWER_BATTERY.get().defaultBlockState()
                    .setValue(com.lanterna.block.PowerBatteryBlock.CORPS, com.lanterna.block.BatteryShape.of(planet.corps.id()));
            BlockPos bp = c.offset(-4, 0, 4);
            set(level, bp, s);
            if (level.getBlockEntity(bp) instanceof com.lanterna.block.PowerBatteryBlockEntity be) be.setCorps(planet.corps.id(), planet.color());
        }
        for (int i = -1; i <= 1; i++) {
            Entity d = LoreEntities.ALVO_TREINO.get().create(level);
            if (d == null) continue;
            d.moveTo(c.getX() - 2 + 0.5, c.getY(), c.getZ() + i * 3 + 0.5, 90, 0);
            level.addFreshEntity(d);
        }
    }

    /** Sul: pouso de naves (círculo iluminado com a marca no meio), recarga e holomapa. */
    private static void pouso(ServerLevel level, BlockPos c, Paleta pal) {
        for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 8.5) continue;
            BlockState s = d > 7.5 ? pal.faixa : (d > 6.5 && ((dx + dz) & 1) == 0 ? pal.luz : pal.parede);
            set(level, c.offset(dx, -1, dz), s);
        }
        // a marca "H" de pouso
        for (int k = -3; k <= 3; k++) {
            set(level, c.offset(-2, -1, k), pal.destaque);
            set(level, c.offset(2, -1, k), pal.destaque);
        }
        for (int k = -2; k <= 2; k++) set(level, c.offset(k, -1, 0), pal.destaque);
        set(level, c.offset(-9, 0, -4), NaveBlocks.NAVE_RECARGA.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        set(level, c.offset(9, 0, -4), NaveBlocks.NAVE_HOLOMAPA.get().defaultBlockState());
    }

    /** Torre de vigia: pilar com janelas e luz no alto. */
    private static void torre(ServerLevel level, BlockPos c, Paleta pal) {
        for (int k = 0; k < 12; k++) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                boolean canto = Math.abs(dx) == 1 && Math.abs(dz) == 1;
                BlockState s = k >= 9 ? (canto ? pal.faixa : pal.vidro) : (canto ? pal.faixa : pal.parede);
                if (k >= 9 && dx == 0 && dz == 0) s = pal.luz;
                set(level, c.offset(dx, k, dz), s);
            }
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) set(level, c.offset(dx, 12, dz), pal.teto);
        set(level, c.above(13), pal.destaque);
    }

    /** Muralha baixa em volta, com portões nas quatro ruas. */
    private static void muralha(ServerLevel level, BlockPos c, int y, Paleta pal) {
        for (int i = 0; i < 360; i++) {
            double a = Math.toRadians(i);
            int dx = (int) Math.round(Math.cos(a) * (R - 1)), dz = (int) Math.round(Math.sin(a) * (R - 1));
            if (Math.abs(dx) <= 3 || Math.abs(dz) <= 3) continue; // portões
            set(level, c.getX() + dx, y, c.getZ() + dz, pal.parede);
            set(level, c.getX() + dx, y + 1, c.getZ() + dz, i % 15 == 0 ? pal.luz : pal.faixa);
        }
    }

    /** Lanternas da Tropa morando na cidade: dois oficiais e quatro patrulheiros. */
    private static void moradores(ServerLevel level, BlockPos c, PlanetLore planet) {
        BuiltinCorps corps = planet.corps;
        int[][] lugares = {{0, -18}, {3, -18}, {16, 2}, {-16, -3}, {4, 16}, {-6, 12}};
        for (int i = 0; i < lugares.length; i++) {
            LanternaEntity l = LanternaEntity.lanterna(level, corps, i < 2);
            BlockPos at = c.offset(lugares[i][0], 0, lugares[i][1]);
            l.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360, 0);
            l.restrictTo(c, 30);
            l.setPersistenceRequired();
            level.addFreshEntity(l);
        }
    }

    private CidadeTropa() {}
}
