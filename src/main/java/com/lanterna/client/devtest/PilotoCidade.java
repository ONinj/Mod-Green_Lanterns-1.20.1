package com.lanterna.client.devtest;

import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.Especie;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.naves.bloco.NaveBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Só para o mundo de teste: protótipos dos prédios da cidade da Tropa (não vai para o .jar). */
final class PilotoCidade {
    static final BlockPos MERCADO = new BlockPos(1000, -60, 1000);
    static final BlockPos QG = new BlockPos(1060, -60, 1000);
    static final BlockPos HANGAR = new BlockPos(1120, -60, 1000);
    static final BlockPos CASA = new BlockPos(1180, -60, 1000);

    private static ServerLevel L;
    private static BlockState b(Block x) { return x.defaultBlockState(); }
    private static void s(BlockPos p, BlockState st) { L.setBlock(p, st, 2); }
    private static void s(int x, int y, int z, BlockState st) { L.setBlock(new BlockPos(x, y, z), st, 2); }
    private static void box(BlockPos a, BlockPos c, BlockState st) {
        for (BlockPos p : BlockPos.betweenClosed(a, c)) s(p, st);
    }
    private static BlockState stair(Block x, Direction f) { return x.defaultBlockState().setValue(StairBlock.FACING, f); }
    private static BlockState slabTop(Block x) { return x.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP); }
    private static BlockState face(Block x, Direction f) { return x.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, f); }

    // Paleta Verde (Oa)
    static final BlockState CHAO = b(Blocks.SMOOTH_STONE), PAREDE = b(Blocks.QUARTZ_BRICKS), FAIXA = b(Blocks.LIME_CONCRETE),
            VIDRO = b(Blocks.LIME_STAINED_GLASS), LUZ = b(Blocks.SEA_LANTERN), TETO = b(Blocks.GREEN_CONCRETE), PILAR = b(Blocks.QUARTZ_PILLAR),
            DESTAQUE = b(Blocks.EMERALD_BLOCK), PISO = b(Blocks.POLISHED_DIORITE);

    static void construir(ServerLevel level) {
        L = level;
        box(new BlockPos(980, -61, 975), new BlockPos(1215, -61, 1030), CHAO);
        box(new BlockPos(980, -60, 975), new BlockPos(1215, -40, 1030), b(Blocks.AIR));
        mercado(MERCADO);
        qg(QG);
        hangar(HANGAR);
        casa(CASA);
    }

    private static void npc(BlockPos p, Especie e, Especie.Papel papel, BuiltinCorps c, String roupa, boolean oficial, String nome, float yaw) {
        LanternaEntity n = new LanternaEntity(com.lanterna.especies.EspeciesEntities.LANTERNA_NPC.get(), L);
        n.setup(e, 1 + (e.ordinal() % 3), papel, c, roupa, oficial, false);
        n.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, 0);
        n.setYHeadRot(yaw);
        n.setYBodyRot(yaw);
        n.setNoAi(true);
        n.setCustomName(Component.literal(nome));
        n.setCustomNameVisible(true);
        L.addFreshEntity(n);
    }

    // ===================== Mercado: rua com barracas e uma loja =====================

    private static void mercado(BlockPos o) {
        // rua
        box(o.offset(-14, -1, -3), o.offset(14, -1, 3), PISO);
        for (int x = -14; x <= 14; x++) { s(o.offset(x, -1, -3), FAIXA); s(o.offset(x, -1, 3), FAIXA); }
        Block[] toldo = {Blocks.LIME_WOOL, Blocks.WHITE_WOOL, Blocks.ORANGE_WOOL, Blocks.LIGHT_BLUE_WOOL};
        Block[] carpet = {Blocks.LIME_CARPET, Blocks.WHITE_CARPET, Blocks.ORANGE_CARPET, Blocks.LIGHT_BLUE_CARPET};
        // 3 barracas do lado norte, viradas para a rua
        for (int i = 0; i < 3; i++) {
            BlockPos c = o.offset(-10 + i * 7, 0, -6);
            for (int dx = -2; dx <= 2; dx++) {
                s(c.offset(dx, 0, 1), dx == 0 ? b(Blocks.BARREL) : slabTop(Blocks.SMOOTH_QUARTZ_SLAB)); // balcão
                s(c.offset(dx, 0, 1).below(), CHAO);
            }
            for (int[] pp : new int[][]{{-2, 1}, {2, 1}, {-2, -1}, {2, -1}}) for (int k = 0; k < 3; k++) s(c.offset(pp[0], k, pp[1]), b(Blocks.SPRUCE_FENCE));
            // toldo listrado inclinado
            for (int dx = -3; dx <= 3; dx++) {
                Block w = (dx & 1) == 0 ? toldo[i] : Blocks.WHITE_WOOL;
                s(c.offset(dx, 3, -1), b(w)); s(c.offset(dx, 3, 0), b(w));
                s(c.offset(dx, 3, 1), b(w));
                s(c.offset(dx, 3, 2), b(carpet[(dx & 1) == 0 ? i : 1]));
            }
            // mercadorias
            BlockState[][] bens = {
                    {b(Blocks.MELON), b(Blocks.PUMPKIN), b(Blocks.HAY_BLOCK)},
                    {b(Blocks.AMETHYST_CLUSTER), b(Blocks.EMERALD_BLOCK), b(Blocks.LAPIS_BLOCK)},
                    {b(Blocks.ANVIL), b(Blocks.SMITHING_TABLE), b(Blocks.GRINDSTONE)}};
            s(c.offset(-1, 1, 1), bens[i][0]);
            s(c.offset(1, 1, 1), bens[i][1]);
            s(c.offset(-1, 0, -1), b(Blocks.CHEST));
            s(c.offset(1, 0, -1), b(Blocks.BARREL));
            s(c.offset(0, 2, 0), b(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
            s(c.offset(0, 3, 0), b(toldo[i]));
        }
        npc(o.offset(-10, 0, -6), Especie.byId("drevan"), Especie.Papel.MERCADOR, null, "roupa_mercador", false, "$ Intendente", 0);
        npc(o.offset(-3, 0, -6), Especie.byId("qiri"), Especie.Papel.MERCADOR, null, "roupa_mercador", false, "$ Relíquias", 0);
        npc(o.offset(4, 0, -6), Especie.byId("brutor"), Especie.Papel.MERCADOR, null, "roupa_mercador", false, "$ Armeiro", 0);
        // loja do lado sul: vitrine de vidro, balcão e prateleiras
        BlockPos l = o.offset(0, 0, 9);
        for (int dx = -6; dx <= 6; dx++) for (int dz = -4; dz <= 4; dz++) {
            boolean borda = Math.abs(dx) == 6 || Math.abs(dz) == 4;
            s(l.offset(dx, -1, dz), Math.abs(dx) < 6 && Math.abs(dz) < 4 ? b(Blocks.POLISHED_ANDESITE) : CHAO);
            for (int k = 0; k < 5; k++) {
                BlockState st;
                if (!borda) st = b(Blocks.AIR);
                else if (Math.abs(dx) == 6 && Math.abs(dz) == 4) st = PILAR;
                else if (dz == -4 && k >= 1 && k <= 3 && Math.abs(dx) >= 2) st = VIDRO; // vitrine para a rua
                else if (k == 4) st = FAIXA;
                else st = PAREDE;
                s(l.offset(dx, k, dz), st);
            }
            s(l.offset(dx, 5, dz), borda ? FAIXA : ((dx % 3 == 0 && dz % 2 == 0) ? LUZ : TETO));
        }
        for (int k = 0; k < 3; k++) for (int dx = -1; dx <= 1; dx++) s(l.offset(dx, k, -4), b(Blocks.AIR)); // entrada
        s(l.offset(-1, 3, -5), LUZ); s(l.offset(1, 3, -5), LUZ);
        for (int dx = -2; dx <= 2; dx++) { s(l.offset(dx, 3, -5), FAIXA); }
        // balcão em L
        for (int dx = -4; dx <= 2; dx++) s(l.offset(dx, 0, 1), slabTop(Blocks.SMOOTH_QUARTZ_SLAB));
        s(l.offset(2, 0, 2), slabTop(Blocks.SMOOTH_QUARTZ_SLAB));
        // prateleiras ao fundo
        for (int dx = -5; dx <= 5; dx++) {
            s(l.offset(dx, 0, 3), dx % 2 == 0 ? b(Blocks.BARREL) : b(Blocks.CHISELED_BOOKSHELF));
            s(l.offset(dx, 1, 3), dx % 3 == 0 ? b(Blocks.LANTERN) : slabTop(Blocks.SPRUCE_SLAB));
            s(l.offset(dx, 2, 3), dx % 2 == 0 ? b(Blocks.DECORATED_POT) : b(Blocks.FLOWER_POT));
        }
        // vitrine: expositores
        s(l.offset(-4, 0, -3), b(Blocks.SMOOTH_QUARTZ)); s(l.offset(-4, 1, -3), b(Blocks.AMETHYST_CLUSTER));
        s(l.offset(4, 0, -3), b(Blocks.SMOOTH_QUARTZ)); s(l.offset(4, 1, -3), b(Blocks.END_ROD));
        npc(l.offset(-1, 0, 2), Especie.byId("lumiae"), Especie.Papel.MERCADOR, null, "roupa_mercador", false, "$ Cartógrafo", 180);
        // postes de luz na rua
        for (int x = -12; x <= 12; x += 8) for (int z : new int[]{-3, 3}) {
            if (z == 3 && Math.abs(x) <= 6) continue;
            s(o.offset(x, 0, z), PILAR); s(o.offset(x, 1, z), PILAR); s(o.offset(x, 2, z), b(Blocks.QUARTZ_PILLAR)); s(o.offset(x, 3, z), LUZ);
        }
        npc(o.offset(-6, 0, 1), Especie.byId("ilith"), Especie.Papel.LANTERNA, null, null, false, "Cidadã", 90);
        npc(o.offset(7, 0, -1), Especie.byId("aurith"), Especie.Papel.LANTERNA, BuiltinCorps.GREEN, "roupa_tropa_green", false, "▲ Guarda", 270);
    }

    // ===================== QG: sala de briefing =====================

    private static void qg(BlockPos o) {
        int W = 9, D = 7, H = 7;
        for (int dx = -W; dx <= W; dx++) for (int dz = -D; dz <= D; dz++) {
            boolean borda = Math.abs(dx) == W || Math.abs(dz) == D;
            s(o.offset(dx, -1, dz), (Math.abs(dx) + Math.abs(dz)) % 4 == 0 ? FAIXA : b(Blocks.POLISHED_DEEPSLATE));
            for (int k = 0; k < H; k++) {
                BlockState st;
                if (!borda) st = b(Blocks.AIR);
                else if (Math.abs(dx) == W && Math.abs(dz) == D) st = PILAR;
                else if (k == 3 && dz == D) st = VIDRO;
                else if (k == H - 1) st = FAIXA;
                else st = (dx + k) % 4 == 0 && Math.abs(dz) == D ? PILAR : PAREDE;
                s(o.offset(dx, k, dz), st);
            }
            s(o.offset(dx, H, dz), borda ? FAIXA : ((Math.abs(dx) % 4 == 0 && Math.abs(dz) % 3 == 0) ? LUZ : TETO));
        }
        // entrada pelo sul (onde fica a câmera)
        for (int k = 0; k < 4; k++) for (int dx = -2; dx <= 2; dx++) s(o.offset(dx, k, D), b(Blocks.AIR));
        // mesa redonda com o holomapa no centro
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= 3.3 && d > 1.4) s(o.offset(dx, 0, dz), slabTop(Blocks.SMOOTH_QUARTZ_SLAB));
            if (d <= 1.4) s(o.offset(dx, 0, dz), DESTAQUE);
        }
        s(o.offset(0, 1, 0), b(NaveBlocks.NAVE_HOLOMAPA.get()));
        // cadeiras em volta (degraus virados para a mesa)
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int w = -2; w <= 2; w += 2) {
                BlockPos p = o.relative(d, 5).relative(d.getClockWise(), w);
                s(p, stair(Blocks.QUARTZ_STAIRS, d));
            }
        }
        // parede do fundo: telões (consoles e vidro aceso)
        for (int dx = -7; dx <= 7; dx++) {
            s(o.offset(dx, 0, -D + 1), face(NaveBlocks.NAVE_CONSOLE.get(), Direction.SOUTH));
            s(o.offset(dx, 2, -D), dx % 2 == 0 ? b(Blocks.LIME_STAINED_GLASS) : LUZ);
            s(o.offset(dx, 3, -D), dx % 2 == 0 ? LUZ : b(Blocks.LIME_STAINED_GLASS));
            s(o.offset(dx, 4, -D), FAIXA);
        }
        // estrado do Comandante com púlpito
        for (int dx = -2; dx <= 2; dx++) s(o.offset(dx, 0, -D + 2), slabTop(Blocks.SMOOTH_QUARTZ_SLAB));
        s(o.offset(0, 1, -D + 2), face(Blocks.LECTERN, Direction.SOUTH));
        // estandartes nas laterais (colunas de lã verde e esmeralda)
        for (int dz = -4; dz <= 4; dz += 4) for (int x : new int[]{-W + 1, W - 1}) {
            for (int k = 1; k <= 4; k++) s(o.offset(x, k, dz), k == 4 ? DESTAQUE : b(Blocks.LIME_WOOL));
            s(o.offset(x, 0, dz), PILAR);
        }
        // arsenal: suportes com armaduras nas laterais
        for (int dz = -2; dz <= 2; dz += 2) {
            s(o.offset(-W + 1, 0, dz + 1), b(Blocks.BARREL));
            s(o.offset(W - 1, 0, dz + 1), face(NaveBlocks.NAVE_ARMARIO.get(), Direction.WEST));
        }
        // NPCs
        npc(o.offset(0, 1, -D + 3), Especie.byId("aurith"), Especie.Papel.LANTERNA, BuiltinCorps.GREEN, "roupa_tropa_green", true, "! Comandante Verde", 0);
        npc(o.offset(-5, 0, 2), Especie.byId("brutor"), Especie.Papel.LANTERNA, BuiltinCorps.GREEN, "roupa_tropa_green", true, "! Oficial de Contratos", 135);
        npc(o.offset(4, 0, -2), Especie.byId("qiri"), Especie.Papel.LANTERNA, BuiltinCorps.GREEN, "roupa_tropa_green", false, "Recruta", 225);
        npc(o.offset(-3, 0, -4), Especie.byId("lumiae"), Especie.Papel.LANTERNA, BuiltinCorps.GREEN, "roupa_tropa_green", false, "Patrulheira", 160);
    }

    // ===================== Hangar: uma baia com guindaste =====================

    private static void hangar(BlockPos o) {
        int W = 12, D = 10, H = 11;
        for (int dx = -W; dx <= W; dx++) for (int dz = -D; dz <= D; dz++) {
            boolean borda = Math.abs(dx) == W || Math.abs(dz) == D;
            boolean listra = (dx == -6 || dx == 6) && dz > -D;
            s(o.offset(dx, -1, dz), listra ? b(Blocks.YELLOW_CONCRETE) : (Math.abs(dz) == D - 1 ? FAIXA : b(Blocks.GRAY_CONCRETE)));
            for (int k = 0; k < H; k++) {
                if (!borda) continue;
                BlockState st = Math.abs(dx) == W && Math.abs(dz) == D ? PILAR : (k % 5 == 4 ? FAIXA : b(Blocks.LIGHT_GRAY_CONCRETE));
                if (dz == D) st = b(Blocks.AIR); // frente aberta
                if (Math.abs(dx) == W && dz % 4 == 0) st = PILAR;
                s(o.offset(dx, k, dz), st);
            }
            // teto com claraboias
            s(o.offset(dx, H, dz), (dx % 4 == 0 && dz % 4 == 0) ? LUZ : (Math.abs(dx) < 3 ? VIDRO : b(Blocks.GRAY_CONCRETE)));
        }
        // trilho do guindaste
        for (int dz = -D + 1; dz < D; dz++) { s(o.offset(-3, H - 1, dz), b(Blocks.IRON_BLOCK)); s(o.offset(3, H - 1, dz), b(Blocks.IRON_BLOCK)); }
        for (int dx = -3; dx <= 3; dx++) s(o.offset(dx, H - 2, -2), b(Blocks.IRON_BLOCK));
        for (int k = 4; k < H - 2; k++) s(o.offset(0, k, -2), b(Blocks.CHAIN));
        s(o.offset(0, 3, -2), b(Blocks.ANVIL));
        // oficina na parede do fundo
        for (int dx = -10; dx <= 10; dx++) {
            BlockState st = switch (Math.floorMod(dx, 5)) {
                case 0 -> b(Blocks.SMITHING_TABLE);
                case 1 -> b(Blocks.BLAST_FURNACE);
                case 2 -> b(Blocks.BARREL);
                case 3 -> b(Blocks.GRINDSTONE);
                default -> face(NaveBlocks.NAVE_RECARGA.get(), Direction.SOUTH);
            };
            s(o.offset(dx, 0, -D + 1), st);
            s(o.offset(dx, 2, -D + 1), dx % 3 == 0 ? b(Blocks.LANTERN) : slabTop(Blocks.SMOOTH_STONE_SLAB));
        }
        // caixas de peças
        for (int[] c : new int[][]{{-10, 4}, {-9, 4}, {-10, 5}, {10, 3}, {9, 3}, {10, 4}}) s(o.offset(c[0], 0, c[1]), b(Blocks.BARREL));
        s(o.offset(-10, 1, 4), b(Blocks.BARREL));
        // luzes de pista na frente
        for (int dx = -10; dx <= 10; dx += 4) s(o.offset(dx, -1, D + 2), LUZ);
        // a nave na baia (estacionada, sem dono: só para a foto)
        var n = com.lanterna.naves.NaveEntity.create(L, com.lanterna.naves.NaveTipo.NAVE_PATRULHA, "green");
        n.moveTo(o.getX() + 0.5, o.getY(), o.getZ() + 1.5, 180, 0);
        L.addFreshEntity(n);
        npc(o.offset(-8, 0, -6), Especie.byId("drevan"), Especie.Papel.MERCADOR, null, "roupa_mercador", false, "! Engenheiro-chefe", 160);
        npc(o.offset(8, 0, 6), Especie.byId("brutor"), Especie.Papel.MERCADOR, null, "roupa_mercador", false, "$ Estaleiro", 200);
    }

    // ===================== Casa com interior =====================

    private static void casa(BlockPos o) {
        int W = 5, D = 4, H = 4;
        for (int dx = -W; dx <= W; dx++) for (int dz = -D; dz <= D; dz++) {
            boolean borda = Math.abs(dx) == W || Math.abs(dz) == D;
            s(o.offset(dx, -1, dz), Math.abs(dx) < W && Math.abs(dz) < D ? b(Blocks.SPRUCE_PLANKS) : CHAO);
            for (int k = 0; k < H; k++) {
                BlockState st = !borda ? b(Blocks.AIR) : (Math.abs(dx) == W && Math.abs(dz) == D ? PILAR
                        : (k == 1 || k == 2) && (dx % 3 == 0) && Math.abs(dz) == D ? b(Blocks.GLASS_PANE) : PAREDE);
                s(o.offset(dx, k, dz), st);
            }
        }
        // telhado em degraus
        for (int dx = -W - 1; dx <= W + 1; dx++) for (int dz = -D - 1; dz <= D + 1; dz++) {
            s(o.offset(dx, H, dz), (dx + dz) % 5 == 0 && Math.abs(dx) < W && Math.abs(dz) < D ? LUZ : TETO);
        }
        // sem parede da frente (sul) para ver o interior
        for (int dx = -W + 1; dx < W; dx++) for (int k = 0; k < H; k++) s(o.offset(dx, k, D), b(Blocks.AIR));
        // quarto (oeste): cama, armário, criado-mudo
        BlockState cama = b(Blocks.LIME_BED).setValue(BedBlock.FACING, Direction.NORTH);
        s(o.offset(-4, 0, -2), cama.setValue(BedBlock.PART, BedPart.FOOT));
        s(o.offset(-4, 0, -3), cama.setValue(BedBlock.PART, BedPart.HEAD));
        s(o.offset(-3, 0, -3), b(Blocks.BARREL));
        s(o.offset(-3, 1, -3), b(Blocks.LANTERN));
        s(o.offset(-4, 0, 1), face(NaveBlocks.NAVE_ARMARIO.get(), Direction.EAST));
        s(o.offset(-4, 0, 2), b(Blocks.CHEST));
        // tapete
        for (int dx = -3; dx <= -1; dx++) for (int dz = -1; dz <= 1; dz++) s(o.offset(dx, 0, dz), b(Blocks.LIME_CARPET));
        // sala: mesa e cadeiras
        s(o.offset(0, 0, 0), b(Blocks.SPRUCE_FENCE)); s(o.offset(0, 1, 0), b(Blocks.SPRUCE_PRESSURE_PLATE));
        s(o.offset(-1, 0, 0), stair(Blocks.SPRUCE_STAIRS, Direction.WEST));
        s(o.offset(1, 0, 0), stair(Blocks.SPRUCE_STAIRS, Direction.EAST));
        s(o.offset(0, 1, 0).above(0), b(Blocks.FLOWER_POT));
        // cozinha (leste)
        s(o.offset(4, 0, -3), face(Blocks.SMOKER, Direction.WEST));
        s(o.offset(4, 0, -2), b(Blocks.CAULDRON));
        s(o.offset(4, 0, -1), face(Blocks.FURNACE, Direction.WEST));
        s(o.offset(3, 0, -3), b(Blocks.CRAFTING_TABLE));
        for (int dz = -3; dz <= -1; dz++) s(o.offset(4, 2, dz), slabTop(Blocks.SPRUCE_SLAB));
        s(o.offset(4, 0, 2), b(Blocks.BOOKSHELF)); s(o.offset(4, 1, 2), b(Blocks.BOOKSHELF));
        s(o.offset(3, 0, 2), b(Blocks.POTTED_FERN));
        npc(o.offset(1, 0, 2), Especie.byId("ilith"), Especie.Papel.LANTERNA, null, null, false, "! Cidadã (pedido)", 180);
    }

    private PilotoCidade() {}
}
