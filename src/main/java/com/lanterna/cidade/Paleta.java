package com.lanterna.cidade;

import com.lanterna.lore.LoreBlocks;
import com.lanterna.lore.PlanetLore;
import com.lanterna.lore.block.LoreBlockTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Materiais de cada Tropa (a mesma planta fica com a cara de cada uma). */
public record Paleta(BlockState chao, BlockState parede, BlockState faixa, BlockState vidro, BlockState luz, BlockState teto,
                     BlockState destaque, BlockState nucleo, BlockState pilar, Block madeira, Block escada, Block laje,
                     Block la, Block tapete, Block cama, Block painel) {

    private static BlockState b(Block x) {
        return x.defaultBlockState();
    }

    private static BlockState cristal() {
        return LoreBlocks.CRISTAL_DEVOTO_AGLOMERADO.get().defaultBlockState().setValue(LoreBlockTypes.CristalDevoto.ACESO, true);
    }

    public static Paleta of(PlanetLore p) {
        BlockState pedra = p.stoneBlock().defaultBlockState();
        return switch (p) {
            case KORUGAR -> new Paleta(pedra, b(Blocks.POLISHED_BLACKSTONE_BRICKS), b(Blocks.YELLOW_CONCRETE), b(Blocks.YELLOW_STAINED_GLASS),
                    b(Blocks.OCHRE_FROGLIGHT), b(Blocks.YELLOW_TERRACOTTA), b(Blocks.GOLD_BLOCK), b(Blocks.OCHRE_FROGLIGHT), b(Blocks.POLISHED_BASALT),
                    Blocks.DARK_OAK_PLANKS, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB,
                    Blocks.YELLOW_WOOL, Blocks.YELLOW_CARPET, Blocks.YELLOW_BED, Blocks.YELLOW_STAINED_GLASS_PANE);
            case YSMAULT -> new Paleta(pedra, b(Blocks.RED_NETHER_BRICKS), b(Blocks.RED_CONCRETE), b(Blocks.RED_STAINED_GLASS),
                    b(Blocks.SHROOMLIGHT), b(Blocks.NETHER_WART_BLOCK), b(Blocks.MAGMA_BLOCK), b(Blocks.SHROOMLIGHT), b(Blocks.STRIPPED_CRIMSON_STEM),
                    Blocks.CRIMSON_PLANKS, Blocks.RED_NETHER_BRICK_STAIRS, Blocks.RED_NETHER_BRICK_SLAB,
                    Blocks.RED_WOOL, Blocks.RED_CARPET, Blocks.RED_BED, Blocks.RED_STAINED_GLASS_PANE);
            case ODYM -> new Paleta(pedra, b(Blocks.PRISMARINE_BRICKS), b(Blocks.LIGHT_BLUE_CONCRETE), b(Blocks.LIGHT_BLUE_STAINED_GLASS),
                    b(Blocks.SEA_LANTERN), b(Blocks.DARK_PRISMARINE), b(Blocks.BLUE_CONCRETE), b(Blocks.SEA_LANTERN), b(Blocks.PRISMARINE),
                    Blocks.WARPED_PLANKS, Blocks.PRISMARINE_BRICK_STAIRS, Blocks.PRISMARINE_BRICK_SLAB,
                    Blocks.LIGHT_BLUE_WOOL, Blocks.LIGHT_BLUE_CARPET, Blocks.LIGHT_BLUE_BED, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
            case OKAARA -> new Paleta(pedra, b(Blocks.SMOOTH_SANDSTONE), b(Blocks.ORANGE_CONCRETE), b(Blocks.ORANGE_STAINED_GLASS),
                    b(Blocks.OCHRE_FROGLIGHT), b(Blocks.ORANGE_TERRACOTTA), b(Blocks.GOLD_BLOCK), b(Blocks.OCHRE_FROGLIGHT), b(Blocks.CUT_SANDSTONE),
                    Blocks.ACACIA_PLANKS, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_SLAB,
                    Blocks.ORANGE_WOOL, Blocks.ORANGE_CARPET, Blocks.ORANGE_BED, Blocks.ORANGE_STAINED_GLASS_PANE);
            case NOK -> new Paleta(pedra, b(Blocks.POLISHED_DEEPSLATE), b(Blocks.PURPLE_CONCRETE), b(Blocks.PURPLE_STAINED_GLASS),
                    cristal(), b(Blocks.BLUE_TERRACOTTA), b(Blocks.AMETHYST_BLOCK), b(Blocks.PEARLESCENT_FROGLIGHT), b(Blocks.DEEPSLATE_TILES),
                    Blocks.DARK_OAK_PLANKS, Blocks.POLISHED_DEEPSLATE_STAIRS, Blocks.POLISHED_DEEPSLATE_SLAB,
                    Blocks.PURPLE_WOOL, Blocks.PURPLE_CARPET, Blocks.PURPLE_BED, Blocks.PURPLE_STAINED_GLASS_PANE);
            case ZAMARON -> new Paleta(pedra, b(Blocks.CALCITE), b(Blocks.MAGENTA_CONCRETE), b(Blocks.MAGENTA_STAINED_GLASS),
                    cristal(), b(Blocks.PURPLE_CONCRETE), b(Blocks.AMETHYST_BLOCK), b(Blocks.PEARLESCENT_FROGLIGHT), b(Blocks.PURPUR_PILLAR),
                    Blocks.CHERRY_PLANKS, Blocks.PURPUR_STAIRS, Blocks.PURPUR_SLAB,
                    Blocks.MAGENTA_WOOL, Blocks.MAGENTA_CARPET, Blocks.MAGENTA_BED, Blocks.MAGENTA_STAINED_GLASS_PANE);
            case RYUT -> new Paleta(pedra, b(Blocks.POLISHED_BLACKSTONE_BRICKS), b(Blocks.BLACK_CONCRETE), b(Blocks.TINTED_GLASS),
                    b(Blocks.SEA_LANTERN), b(Blocks.BLACKSTONE), b(Blocks.OBSIDIAN), b(Blocks.CRYING_OBSIDIAN), b(Blocks.POLISHED_BASALT),
                    Blocks.DARK_OAK_PLANKS, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB,
                    Blocks.BLACK_WOOL, Blocks.BLACK_CARPET, Blocks.BLACK_BED, Blocks.BLACK_STAINED_GLASS_PANE);
            default -> new Paleta(p == PlanetLore.OA ? b(LoreBlocks.ROCHA_POLIDA_VERIDIA.get()) : pedra, b(Blocks.QUARTZ_BRICKS),
                    b(Blocks.LIME_CONCRETE), b(Blocks.LIME_STAINED_GLASS), b(Blocks.SEA_LANTERN), b(Blocks.GREEN_CONCRETE),
                    b(Blocks.EMERALD_BLOCK), b(Blocks.VERDANT_FROGLIGHT), b(Blocks.QUARTZ_PILLAR),
                    Blocks.BIRCH_PLANKS, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ_SLAB,
                    Blocks.LIME_WOOL, Blocks.LIME_CARPET, Blocks.LIME_BED, Blocks.LIME_STAINED_GLASS_PANE);
        };
    }
}
