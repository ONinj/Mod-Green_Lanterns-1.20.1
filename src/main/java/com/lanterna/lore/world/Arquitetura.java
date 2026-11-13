package com.lanterna.lore.world;

import com.lanterna.corps.BuiltinCorps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.lanterna.cidade.Mundo;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Peças de arquitetura para as estruturas do mod (arenas, bases, altares...): colunas com base e capitel, arcos,
 * arquibancadas, braseiros, obeliscos, pisos com desenho e portões. Cada planeta usa um {@link Estilo} (família de
 * blocos com escada, laje e muro) e as cores da Tropa.
 */
public final class Arquitetura {

    /** Família de blocos de um lugar. */
    public record Estilo(Block cheio, Block tijolo, Block polido, Block escada, Block laje, Block muro, Block destaque, Block luz,
                         Block vidro, DyeColor cor) {
        public BlockState s(Block b) { return b.defaultBlockState(); }
    }

    public static Estilo estilo(BuiltinCorps c) {
        return switch (c) {
            case GREEN -> new Estilo(Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_BRICKS, Blocks.SMOOTH_QUARTZ, Blocks.SMOOTH_QUARTZ_STAIRS,
                    Blocks.SMOOTH_QUARTZ_SLAB, Blocks.DIORITE_WALL, Blocks.EMERALD_BLOCK, Blocks.SEA_LANTERN, Blocks.LIME_STAINED_GLASS, DyeColor.LIME);
            case YELLOW -> new Estilo(Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE,
                    Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, Blocks.POLISHED_BLACKSTONE_BRICK_WALL,
                    Blocks.GOLD_BLOCK, Blocks.OCHRE_FROGLIGHT, Blocks.YELLOW_STAINED_GLASS, DyeColor.YELLOW);
            case RED -> new Estilo(Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICKS, Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICK_STAIRS,
                    Blocks.RED_NETHER_BRICK_SLAB, Blocks.RED_NETHER_BRICK_WALL, Blocks.MAGMA_BLOCK, Blocks.SHROOMLIGHT,
                    Blocks.RED_STAINED_GLASS, DyeColor.RED);
            case BLUE -> new Estilo(Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE_BRICK_STAIRS,
                    Blocks.PRISMARINE_BRICK_SLAB, Blocks.PRISMARINE_WALL, Blocks.LAPIS_BLOCK, Blocks.SEA_LANTERN,
                    Blocks.LIGHT_BLUE_STAINED_GLASS, DyeColor.LIGHT_BLUE);
            case VIOLET -> new Estilo(Blocks.PURPUR_BLOCK, Blocks.PURPUR_PILLAR, Blocks.PURPUR_BLOCK, Blocks.PURPUR_STAIRS, Blocks.PURPUR_SLAB,
                    Blocks.DEEPSLATE_TILE_WALL, Blocks.AMETHYST_BLOCK, Blocks.PEARLESCENT_FROGLIGHT, Blocks.MAGENTA_STAINED_GLASS, DyeColor.MAGENTA);
            case INDIGO -> new Estilo(Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILE_STAIRS,
                    Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILE_WALL, Blocks.PURPLE_GLAZED_TERRACOTTA, Blocks.VERDANT_FROGLIGHT,
                    Blocks.PURPLE_STAINED_GLASS, DyeColor.PURPLE);
            case ORANGE -> new Estilo(Blocks.CUT_RED_SANDSTONE, Blocks.CHISELED_RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE,
                    Blocks.SMOOTH_RED_SANDSTONE_STAIRS, Blocks.SMOOTH_RED_SANDSTONE_SLAB, Blocks.RED_SANDSTONE_WALL, Blocks.GOLD_BLOCK,
                    Blocks.LANTERN, Blocks.ORANGE_STAINED_GLASS, DyeColor.ORANGE);
            case BLACK -> new Estilo(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE,
                    Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, Blocks.POLISHED_BLACKSTONE_BRICK_WALL,
                    Blocks.BONE_BLOCK, Blocks.SOUL_LANTERN, Blocks.BLACK_STAINED_GLASS, DyeColor.BLACK);
            default -> new Estilo(Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_BRICKS, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_SLAB,
                    Blocks.DIORITE_WALL, Blocks.GOLD_BLOCK, Blocks.GLOWSTONE, Blocks.WHITE_STAINED_GLASS, DyeColor.WHITE);
        };
    }

    // ===================== básicos =====================

    public static void set(Mundo l, BlockPos p, BlockState s) {
        l.set(p, s);
    }

    /** Luz de bloco inteiro (para piso e arcos): lanterna não serve, vira um bloco que brilha. */
    public static BlockState luzCheia(Estilo e) {
        BlockState s = e.luz().defaultBlockState();
        if (!s.hasProperty(LanternBlock.HANGING)) return s;
        return e.cor() == DyeColor.BLACK ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.SHROOMLIGHT.defaultBlockState();
    }

    /** Uma "luz" pode ser lanterna (precisa de corrente ou chão): devolve o estado certo pendurado. */
    private static BlockState pendurada(Block luz) {
        BlockState s = luz.defaultBlockState();
        if (s.hasProperty(LanternBlock.HANGING)) s = s.setValue(LanternBlock.HANGING, true);
        return s;
    }

    public static BlockState escada(Estilo e, Direction olhaPara, boolean deCabeca) {
        BlockState s = e.escada().defaultBlockState();
        if (s.hasProperty(StairBlock.FACING)) s = s.setValue(StairBlock.FACING, olhaPara);
        if (s.hasProperty(StairBlock.HALF)) s = s.setValue(StairBlock.HALF, deCabeca ? Half.TOP : Half.BOTTOM);
        return s;
    }

    public static BlockState laje(Estilo e, boolean emCima) {
        BlockState s = e.laje().defaultBlockState();
        if (s.hasProperty(SlabBlock.TYPE)) s = s.setValue(SlabBlock.TYPE, emCima ? SlabType.TOP : SlabType.BOTTOM);
        return s;
    }

    /** Direção horizontal mais próxima de (dx, dz). */
    public static Direction rumo(double dx, double dz) {
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    // ===================== peças =====================

    /**
     * Coluna: base 3x3 com degraus, fuste com faixas, capitel 3x3 com laje e luz no topo, e luzes penduradas nos 4 lados.
     * @return a altura do topo (y do capitel)
     */
    public static int coluna(Mundo l, BlockPos base, int altura, Estilo e) {
        int x = base.getX(), y = base.getY(), z = base.getZ();
        // base: bloco no meio, escadas viradas para fora em volta
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = base.relative(d);
            set(l, p, escada(e, d.getOpposite(), false));
        }
        for (int[] c : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) set(l, new BlockPos(x + c[0], y, z + c[1]), laje(e, false));
        for (int k = 0; k < altura; k++) {
            BlockState b = k == 0 || k == altura - 1 ? e.s(e.cheio()) : k % 4 == 2 ? e.s(e.destaque()) : e.s(e.tijolo());
            set(l, new BlockPos(x, y + k, z), b);
        }
        int topo = y + altura;
        // capitel: escadas de cabeça para baixo em volta e laje por cima
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = new BlockPos(x, topo - 1, z).relative(d);
            set(l, p, escada(e, d.getOpposite(), true));
            set(l, p.above(), laje(e, false));
            BlockPos pendura = p.relative(d);
            if (l.get(pendura.below()).isAir()) {
                set(l, pendura, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
                set(l, pendura.below(), pendurada(Blocks.LANTERN));
            }
        }
        for (int[] c : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) set(l, new BlockPos(x + c[0], topo, z + c[1]), laje(e, false));
        set(l, new BlockPos(x, topo, z), e.s(e.cheio()));
        set(l, new BlockPos(x, topo + 1, z), e.s(e.luz()));
        return topo;
    }

    /** Estandarte da Tropa pendurado numa parede, virado para "frente". */
    public static void estandarte(Mundo l, BlockPos p, Direction frente, DyeColor cor) {
        Block b = switch (cor) {
            case LIME -> Blocks.LIME_WALL_BANNER;
            case YELLOW -> Blocks.YELLOW_WALL_BANNER;
            case RED -> Blocks.RED_WALL_BANNER;
            case LIGHT_BLUE -> Blocks.LIGHT_BLUE_WALL_BANNER;
            case MAGENTA -> Blocks.MAGENTA_WALL_BANNER;
            case PURPLE -> Blocks.PURPLE_WALL_BANNER;
            case ORANGE -> Blocks.ORANGE_WALL_BANNER;
            case BLACK -> Blocks.BLACK_WALL_BANNER;
            case GREEN -> Blocks.GREEN_WALL_BANNER;
            default -> Blocks.WHITE_WALL_BANNER;
        };
        if (l.get(p).isAir()) set(l, p, b.defaultBlockState().setValue(WallBannerBlock.FACING, frente));
    }

    /** Arco entre o topo de duas colunas (curva com uma faixa de luz no meio). */
    public static void arco(Mundo l, BlockPos a, BlockPos b, int subida, Estilo e) {
        double dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ();
        int n = (int) Math.ceil(Math.hypot(dx, dz) * 2);
        BlockPos ultimo = null;
        for (int i = 1; i < n; i++) {
            double t = i / (double) n;
            int x = (int) Math.round(a.getX() + dx * t), z = (int) Math.round(a.getZ() + dz * t);
            int y = (int) Math.round(a.getY() + (b.getY() - a.getY()) * t + Math.sin(Math.PI * t) * subida);
            BlockPos p = new BlockPos(x, y, z);
            if (p.equals(ultimo)) continue;
            ultimo = p;
            set(l, p, Math.abs(t - 0.5) < 0.04 ? luzCheia(e) : e.s(e.tijolo()));
            if (l.get(p.below()).isAir() && Math.abs(t - 0.5) > 0.2) set(l, p.below(), laje(e, true));
        }
    }

    /** Braseiro: pedestal de muro com fogo (ou alma, no estilo da Morte). */
    public static void braseiro(Mundo l, BlockPos p, Estilo e) {
        set(l, p, e.s(e.muro()));
        set(l, p.above(), e.cor() == DyeColor.BLACK ? Blocks.SOUL_CAMPFIRE.defaultBlockState() : Blocks.CAMPFIRE.defaultBlockState());
    }

    /** Obelisco afinando para cima, com a ponta de luz. */
    public static void obelisco(Mundo l, BlockPos p, int altura, Estilo e) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            boolean canto = dx != 0 && dz != 0;
            set(l, p.offset(dx, 0, dz), canto ? laje(e, false) : e.s(e.tijolo()));
            if (!canto && (dx != 0 || dz != 0)) set(l, p.offset(dx, 1, dz), escada(e, rumo(-dx, -dz), false));
        }
        for (int k = 0; k < altura; k++) set(l, p.above(k), k % 3 == 1 ? e.s(e.destaque()) : e.s(e.cheio()));
        set(l, p.above(altura), e.s(e.muro()));
        set(l, p.above(altura + 1), e.s(e.luz()));
    }

    /**
     * Arquibancada em volta de um círculo: degraus subindo de raioInt para fora, com 4 entradas (N, L, S, O).
     * Termina num muro baixo com luzes.
     */
    public static void arquibancada(Mundo l, BlockPos c, int y, int raioInt, int degraus, Estilo e) {
        int ext = raioInt + degraus;
        for (int dx = -ext - 1; dx <= ext + 1; dx++) for (int dz = -ext - 1; dz <= ext + 1; dz++) {
            double r = Math.sqrt(dx * dx + dz * dz);
            if (r < raioInt - 0.5 || r > ext + 1.5) continue;
            double ang = Math.toDegrees(Math.atan2(dz, dx));
            double paraEixo = Math.abs(((ang % 90) + 90 + 45) % 90 - 45);
            boolean entrada = paraEixo < Math.toDegrees(2.6 / Math.max(1, r));
            int x = c.getX() + dx, z = c.getZ() + dz;
            if (entrada) {
                set(l, new BlockPos(x, y - 1, z), e.s(e.polido()));
                continue;
            }
            int k = (int) Math.floor(r - raioInt + 0.5);
            if (k > degraus) {
                // muro de fora
                for (int h = 0; h <= degraus; h++) set(l, new BlockPos(x, y + h, z), e.s(e.tijolo()));
                set(l, new BlockPos(x, y + degraus + 1, z), e.s(e.muro()));
                continue;
            }
            for (int h = 0; h < k; h++) set(l, new BlockPos(x, y + h, z), e.s(e.cheio()));
            Direction paraDentro = rumo(-dx, -dz);
            set(l, new BlockPos(x, y + k, z), escada(e, paraDentro.getOpposite(), false));
        }
        // luzes em cima do muro e portões nas entradas
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16 + Math.PI / 16;
            int x = c.getX() + (int) Math.round(Math.cos(a) * (ext + 1)), z = c.getZ() + (int) Math.round(Math.sin(a) * (ext + 1));
            set(l, new BlockPos(x, y + degraus + 2, z), e.s(e.luz()));
        }
        for (Direction d : Direction.Plane.HORIZONTAL) portao(l, c.relative(d, ext + 1).atY(y), d, degraus + 4, e);
    }

    /** Portão: dois pilares e uma verga com luz, atravessando a direção "d". */
    public static void portao(Mundo l, BlockPos meio, Direction d, int altura, Estilo e) {
        Direction lado = d.getClockWise();
        for (int s = -1; s <= 1; s += 2) {
            BlockPos p = meio.relative(lado, 3 * s);
            for (int k = 0; k < altura; k++) set(l, p.above(k), k % 3 == 2 ? e.s(e.destaque()) : e.s(e.cheio()));
            set(l, p.above(altura), e.s(e.luz()));
            estandarte(l, p.above(altura - 2).relative(d), d, e.cor());
            estandarte(l, p.above(altura - 2).relative(d.getOpposite()), d.getOpposite(), e.cor());
        }
        for (int s = -3; s <= 3; s++) {
            BlockPos p = meio.relative(lado, s).above(altura - 1);
            set(l, p, Math.abs(s) == 0 ? e.s(e.destaque()) : e.s(e.tijolo()));
            if (Math.abs(s) <= 2) set(l, p.below(), escada(e, s < 0 ? lado : lado.getOpposite(), true));
        }
        set(l, meio.above(altura - 2), Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
        set(l, meio.above(altura - 3), pendurada(Blocks.LANTERN));
    }

    /**
     * Piso desenhado num círculo: anéis de destaque, 8 raios, quadrantes alternados e pontos de luz embutidos.
     * Só muda o bloco do chão (y - 1).
     */
    public static void piso(Mundo l, BlockPos c, int y, int raio, Estilo e) {
        for (int dx = -raio; dx <= raio; dx++) for (int dz = -raio; dz <= raio; dz++) {
            double r = Math.sqrt(dx * dx + dz * dz);
            if (r > raio + 0.5) continue;
            double ang = Math.atan2(dz, dx);
            double setor = (ang + Math.PI) / (Math.PI / 4);
            boolean raioLinha = Math.abs(setor - Math.round(setor)) * (Math.PI / 4) * r < 0.55 && r > 4.5;
            BlockState b;
            if (Math.abs(r - 4.5) < 0.6 || Math.abs(r - raio * 0.6) < 0.55 || r > raio - 0.6) b = e.s(e.destaque());
            else if (raioLinha) b = e.s(e.tijolo());
            else if (Math.abs(r - raio * 0.6 + 2.5) < 0.5 && ((int) Math.floor(setor) % 2 == 0)) b = luzCheia(e);
            else b = ((int) Math.floor(setor) + (r > raio * 0.6 ? 1 : 0)) % 2 == 0 ? e.s(e.polido()) : e.s(e.cheio());
            set(l, new BlockPos(c.getX() + dx, y - 1, c.getZ() + dz), b);
        }
    }

    private Arquitetura() {}
}
