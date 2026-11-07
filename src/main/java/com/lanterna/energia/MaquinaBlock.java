package com.lanterna.energia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** Bloco de máquina: virado para quem colocou, aceso quando trabalha; a lógica fica na block entity. */
public class MaquinaBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACESO = BooleanProperty.create("aceso");
    private final Supplier<BlockEntityType<? extends MaquinaBE>> tipo;

    public MaquinaBlock(Properties p, Supplier<BlockEntityType<? extends MaquinaBE>> tipo) {
        super(p);
        this.tipo = tipo;
        registerDefaultState(estadoPadrao());
    }

    protected BlockState estadoPadrao() {
        return stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACESO, false);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, ACESO);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState s) {
        return tipo.get().create(pos, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState s, BlockEntityType<T> t) {
        if (t != tipo.get()) return null;
        return level.isClientSide ? (l, p, st, be) -> ((MaquinaBE) be).tickCliente() : (l, p, st, be) -> ((MaquinaBE) be).tickServidor();
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof MaquinaBE be)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        return be.usar(p, hand);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState s, Level level, BlockPos pos, BlockState novo, boolean moving) {
        if (!s.is(novo.getBlock()) && level.getBlockEntity(pos) instanceof MaquinaBE be) be.soltarItens();
        super.onRemove(s, level, pos, novo, moving);
    }
}
