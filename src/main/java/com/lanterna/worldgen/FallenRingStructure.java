package com.lanterna.worldgen;

import com.lanterna.registry.ModStructures;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * O Anel Caído: uma cratera de meteoro na Terra. No centro, a casca do meteoro guarda um baú
 * com o Anel Apagado de uma Tropa aleatória. Uma fogueira de sinal deixa uma coluna de fumaça para
 * achar a cratera de longe. Também aparece em /locate structure lanterna:anel_caido e no mapa do cartógrafo.
 */
public class FallenRingStructure extends Structure {

    public static final Codec<FallenRingStructure> CODEC = simpleCodec(FallenRingStructure::new);

    public FallenRingStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        int x = ctx.chunkPos().getMiddleBlockX();
        int z = ctx.chunkPos().getMiddleBlockZ();
        int y = ctx.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState()) - 1;
        int sea = ctx.chunkGenerator().getSeaLevel();
        // Nada de cratera no mar, em rio ou no alto de montanhas
        if (y <= sea || y > sea + 60 || y - 10 < ctx.heightAccessor().getMinBuildHeight()) return Optional.empty();
        // Terreno mais ou menos plano (cantos da cratera)
        for (int[] o : new int[][]{{8, 0}, {-8, 0}, {0, 8}, {0, -8}}) {
            int h = ctx.chunkGenerator().getBaseHeight(x + o[0], z + o[1], Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState()) - 1;
            if (Math.abs(h - y) > 5 || h <= sea) return Optional.empty();
        }
        BlockPos center = new BlockPos(x, y, z);
        int rot = ctx.random().nextInt(4);
        return Optional.of(new GenerationStub(center, builder -> builder.addPiece(new FallenRingPiece(center, rot))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.ANEL_CAIDO.get();
    }
}
