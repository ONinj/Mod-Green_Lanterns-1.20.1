package com.lanterna.lore.world;

import com.lanterna.cidade.Cidade;
import com.lanterna.cidade.CidadeNativa;
import com.lanterna.cidade.Palacio;
import com.lanterna.lore.PlanetLore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Onde os Ecos Cinzentos podem nascer sozinhos (spawn natural) nos planetas:
 *
 *  - nunca nas megacidades dos povos nem no Palácio dos Guardiões;
 *  - raramente nos QGs (a cidade da Tropa, a base e a arena do planeta);
 *  - fora disso, só em chão natural (nada de piso de construção) e:
 *      Oa (o planeta com floresta): só perto de árvores;
 *      Odym (o planeta de água): só em cima das ilhas, a céu aberto, acima do mar e longe da beira d'água;
 *      planetas sem árvores: em qualquer chão natural.
 *
 * Missões, contratos e chefes que criam Ecos de propósito não passam por aqui.
 */
public final class ZonasDeNascimento {
    /** Chance de um Eco nascer dentro de um QG (quando o resto da regra já deixaria). */
    public static final float CHANCE_QG = 0.08f;

    public static boolean ecoPodeNascer(ServerLevelAccessor level, BlockPos pos, MobSpawnType motivo, RandomSource random) {
        if (motivo != MobSpawnType.NATURAL && motivo != MobSpawnType.CHUNK_GENERATION) return true;
        // nunca dentro d'água (nem com a cabeça na água)
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) return false;
        PlanetLore planet = PlanetLore.byDimension(level.getLevel().dimension());
        BlockState chao = level.getBlockState(pos.below());
        if (planet == null) return !artificial(chao);
        if (dentro(pos, CidadeNativa.CENTRO, CidadeNativa.R + 24)) return false;
        if (planet == PlanetLore.OA && dentro(pos, Palacio.CENTRO, Palacio.R + 40)) return false;
        if (qg(pos)) return random.nextFloat() < CHANCE_QG;
        if (artificial(chao)) return false;
        if (planet == PlanetLore.ODYM) return ilhaSeca(level, pos, chao);
        if (planet == PlanetLore.OA) return pertoDeArvore(level, pos);
        return true;
    }

    /** Cidade da Tropa (o QG fica nela), base do planeta e arena do desafio. */
    public static boolean qg(BlockPos p) {
        boolean cidade = Math.abs(p.getX() - Cidade.CENTRO.getX()) <= Cidade.R + 8 && Math.abs(p.getZ() - Cidade.CENTRO.getZ()) <= Cidade.R + 8;
        return cidade || dentro(p, PlanetLore.HUB, 40) || dentro(p, new BlockPos(120, 0, 0), 30);
    }

    private static boolean dentro(BlockPos p, BlockPos c, int r) {
        double dx = p.getX() - c.getX(), dz = p.getZ() - c.getZ();
        return dx * dx + dz * dz <= (double) r * r;
    }

    /**
     * Odym: só em cima das ilhas, de verdade. Nada no fundo do mar (areia submersa), em cavernas ou em barrancos da
     * praia colados na água: acima do nível do mar, a céu aberto e com terra firme em volta.
     */
    private static boolean ilhaSeca(ServerLevelAccessor level, BlockPos pos, BlockState chao) {
        if (!ilha(chao)) return false;
        if (pos.getY() < level.getLevel().getChunkSource().getGenerator().getSeaLevel()) return false;
        if (!level.canSeeSky(pos)) return false;
        int seco = 0;
        for (BlockPos q : new BlockPos[]{pos.north(2), pos.south(2), pos.east(2), pos.west(2)}) {
            if (level.getFluidState(q).isEmpty() && level.getFluidState(q.below()).isEmpty()) seco++;
        }
        return seco >= 2;
    }

    private static boolean ilha(BlockState chao) {
        if (chao.is(BlockTags.SAND) || chao.is(BlockTags.DIRT) || chao.is(net.minecraft.world.level.block.Blocks.GRAVEL)
                || chao.is(net.minecraft.world.level.block.Blocks.STONE)) return true;
        // a areia das ilhas de Odym é a do próprio planeta
        return BuiltInRegistries.BLOCK.getKey(chao.getBlock()).getPath().startsWith("areia_");
    }

    private static boolean pertoDeArvore(ServerLevelAccessor level, BlockPos pos) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -6; dx <= 6; dx += 2) for (int dz = -6; dz <= 6; dz += 2) for (int dy = 0; dy <= 8; dy += 2) {
            BlockState s = level.getBlockState(m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz));
            if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) return true;
        }
        return false;
    }

    /** Piso de construção (cidades, bases de guilda, estradas): o Eco não nasce em cima. */
    public static boolean artificial(BlockState s) {
        if (s.is(BlockTags.PLANKS) || s.is(BlockTags.WOOL) || s.is(BlockTags.SLABS) || s.is(BlockTags.STAIRS) || s.is(BlockTags.WALLS)) return true;
        String n = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        for (String a : new String[]{"concrete", "glass", "quartz", "brick", "polished", "smooth", "cut_", "froglight", "lantern",
                "glazed", "tile", "iron_block", "gold_block", "diamond_block", "emerald_block", "copper", "lamp",
                "purpur", "prismarine", "chiseled", "construct"})
            if (n.contains(a)) return true;
        return false;
    }

    private ZonasDeNascimento() {}
}
