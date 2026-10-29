package com.lanterna.worldgen;

import com.lanterna.LanternaMod;
import com.lanterna.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.resources.ResourceLocation;

/** A cratera do Anel Caído, construída bloco a bloco (cada chunk constrói só a sua parte). */
public class FallenRingPiece extends StructurePiece {

    public static final ResourceLocation LOOT = LanternaMod.id("chests/anel_caido");
    private static final int R = 8;

    private final BlockPos center;
    private final int rot;

    public FallenRingPiece(BlockPos center, int rot) {
        super(ModStructures.ANEL_CAIDO_PIECE.get(), 0,
                new BoundingBox(center.getX() - R - 3, center.getY() - 9, center.getZ() - R - 3,
                        center.getX() + R + 3, center.getY() + 10, center.getZ() + R + 3));
        this.center = center;
        this.rot = rot;
    }

    public FallenRingPiece(CompoundTag tag) {
        super(ModStructures.ANEL_CAIDO_PIECE.get(), tag);
        this.center = new BlockPos(tag.getInt("CX"), tag.getInt("CY"), tag.getInt("CZ"));
        this.rot = tag.getInt("Rot");
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putInt("CX", center.getX());
        tag.putInt("CY", center.getY());
        tag.putInt("CZ", center.getZ());
        tag.putInt("Rot", rot);
    }

    private static void set(WorldGenLevel level, BoundingBox box, BlockPos p, BlockState s) {
        if (box.isInside(p)) level.setBlock(p, s, 2);
    }

    private static BlockState pick(RandomSource r, BlockState... options) {
        return options[r.nextInt(options.length)];
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        BlockState air = Blocks.AIR.defaultBlockState();

        // ---- A cratera ----
        for (int dx = -R - 2; dx <= R + 2; dx++) {
            for (int dz = -R - 2; dz <= R + 2; dz++) {
                int x = cx + dx, z = cz + dz;
                if (!box.isInside(x, cy, z)) continue;
                RandomSource r = RandomSource.create(Mth.getSeed(x, cy, z));
                double wobble = r.nextDouble() * 0.8;
                double d = Math.sqrt(dx * dx + dz * dz) + wobble;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                if (d <= R) {
                    int depth = (int) Math.round(4.5 * (1 - (d / R) * (d / R)));
                    int bottom = cy - depth;
                    for (int y = bottom + 1; y <= Math.max(top, cy) + 4; y++) set(level, box, new BlockPos(x, y, z), air);
                    BlockState floor;
                    if (d < 2.6) floor = pick(r, Blocks.OBSIDIAN.defaultBlockState(), Blocks.CRYING_OBSIDIAN.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState());
                    else if (d < 5.2) floor = pick(r, Blocks.BLACKSTONE.defaultBlockState(), Blocks.BASALT.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState(), Blocks.TUFF.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState());
                    else floor = pick(r, Blocks.COARSE_DIRT.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(), Blocks.DIRT.defaultBlockState(), Blocks.TUFF.defaultBlockState());
                    set(level, box, new BlockPos(x, bottom, z), floor);
                    // Sem buracos embaixo (cavernas)
                    for (int y = bottom - 1; y >= bottom - 3; y--) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (box.isInside(p) && level.getBlockState(p).isAir()) set(level, box, p, Blocks.STONE.defaultBlockState());
                    }
                    // Fogo ainda queimando no fundo
                    if (d > 2.6 && d < 5.2 && r.nextFloat() < 0.06) set(level, box, new BlockPos(x, bottom + 1, z), Blocks.FIRE.defaultBlockState());
                } else if (d <= R + 1.6) {
                    // Borda levantada pela queda
                    if (r.nextFloat() < 0.65 && top <= cy + 2) {
                        int y = Math.max(top, cy - 1) + 1;
                        set(level, box, new BlockPos(x, y, z), pick(r, Blocks.COARSE_DIRT.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(), Blocks.DIRT.defaultBlockState()));
                        if (r.nextFloat() < 0.08) set(level, box, new BlockPos(x, y + 1, z), pick(r, Blocks.OBSIDIAN.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState()));
                    }
                }
            }
        }

        // ---- A casca do meteoro (oca), com o baú lá dentro ----
        Direction door = Direction.from2DDataValue(rot);
        BlockPos core = new BlockPos(cx, cy - 3, cz);
        for (int dx = -3; dx <= 3; dx++) for (int dy = -1; dy <= 3; dy++) for (int dz = -3; dz <= 3; dz++) {
            double d = Math.sqrt(dx * dx + dy * dy * 1.3 + dz * dz);
            BlockPos p = core.offset(dx, dy, dz);
            if (!box.isInside(p)) continue;
            RandomSource r = RandomSource.create(Mth.getSeed(p.getX(), p.getY(), p.getZ()));
            if (d < 1.5) set(level, box, p, air);
            else if (d < 2.6) {
                int along = dx * door.getStepX() + dz * door.getStepZ();
                int across = dx * door.getStepZ() - dz * door.getStepX();
                boolean opening = dy >= 0 && dy <= 1 && along > 0 && across == 0;
                if (opening) set(level, box, p, air);
                else if (r.nextFloat() < 0.88) set(level, box, p, pick(r, Blocks.CRYING_OBSIDIAN.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(),
                        Blocks.CRYING_OBSIDIAN.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState()));
            }
        }
        // Piso de dentro e o baú virado para a abertura
        set(level, box, core.below(), Blocks.CRYING_OBSIDIAN.defaultBlockState());
        BlockPos chest = core;
        if (box.isInside(chest)) {
            level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, door), 2);
            RandomizableContainerBlockEntity.setLootTable(level, random, chest, LOOT);
        }
        set(level, box, core.above(2), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));

        // ---- Fogueira de sinal: coluna de fumaça para achar a cratera de longe ----
        Direction side = door.getClockWise();
        BlockPos fire = new BlockPos(cx + side.getStepX() * 4, cy - 2, cz + side.getStepZ() * 4);
        set(level, box, fire.below(), Blocks.HAY_BLOCK.defaultBlockState());
        set(level, box, fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true));
        set(level, box, fire.above(), air);
    }
}
