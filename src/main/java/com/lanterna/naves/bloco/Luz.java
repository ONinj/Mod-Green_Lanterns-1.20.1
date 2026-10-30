package com.lanterna.naves.bloco;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Painel de luz do teto (fino, preso em cima). */
public class Luz extends Block {
    private static final VoxelShape SHAPE = Block.box(1, 13, 1, 15, 16, 15);
    public Luz(Properties p) { super(p); }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return SHAPE;
    }
}
