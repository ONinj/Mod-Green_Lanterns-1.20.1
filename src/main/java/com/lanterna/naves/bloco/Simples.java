package com.lanterna.naves.bloco;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Holomapa: mesa redonda com o mapa estelar em cima. */
public class Simples extends Block {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);
    public Simples(Properties p) { super(p); }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return SHAPE;
    }
}
