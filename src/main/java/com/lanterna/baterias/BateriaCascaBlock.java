package com.lanterna.baterias;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** Colisão invisível que dá corpo ao modelo (é o "hitbox" da Bateria). */
public class BateriaCascaBlock extends Block {
    public BateriaCascaBlock(Properties p) { super(p); }
    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }
    @Override public float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) { return 1.0f; }
    @Override public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) { return true; }
}
