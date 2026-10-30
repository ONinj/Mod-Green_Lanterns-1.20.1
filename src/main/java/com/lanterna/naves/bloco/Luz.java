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
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty LIT =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;
    public Luz(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(LIT, true));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> b) {
        b.add(LIT);
    }

    /** Clique liga e desliga a luz. */
    @Override
    @SuppressWarnings("deprecation")
    public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide) {
            boolean lit = !state.getValue(LIT);
            level.setBlock(pos, state.setValue(LIT, lit), 3);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.STONE_BUTTON_CLICK_ON, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, lit ? 1.4f : 1.0f);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return SHAPE;
    }
}
