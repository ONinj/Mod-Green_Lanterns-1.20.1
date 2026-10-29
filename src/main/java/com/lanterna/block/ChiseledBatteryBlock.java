package com.lanterna.block;

import com.lanterna.item.DormantRingItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bateria Cinzelada: uma bateria de pedra sem cor, feita na bancada. Guarda uma única descarga de
 * energia pura. Clique nela com o Anel Apagado: ela se parte, o anel desperta e escolhe sozinho
 * uma das Tropas para o portador.
 */
public class ChiseledBatteryBlock extends HorizontalDirectionalBlock {

    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 15, 13);

    public ChiseledBatteryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof DormantRingItem)) {
            if (!level.isClientSide && hand == InteractionHand.MAIN_HAND)
                player.displayClientMessage(Component.translatable("message.lanterna.chiseled_battery_hint"), true);
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        DormantRingItem.awaken((ServerPlayer) player, held, pos);
        return InteractionResult.CONSUME;
    }
}
