package com.lanterna.lore.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Blocos especiais dos minérios da lore. */
public final class LoreBlockTypes {

    /** Cristal Devoto (minério e aglomerado): só brilha com outro jogador ou bicho domado por perto. */
    public static class CristalDevoto extends Block {
        public static final BooleanProperty ACESO = BooleanProperty.create("aceso");
        private final VoxelShape shape;

        public CristalDevoto(Properties props, @Nullable VoxelShape shape) {
            super(props);
            this.shape = shape;
            registerDefaultState(stateDefinition.any().setValue(ACESO, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(ACESO);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return shape != null ? shape : super.getShape(state, level, pos, ctx);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (state.getValue(ACESO) && random.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.END_ROD, pos.getX() + random.nextDouble(), pos.getY() + 0.8,
                        pos.getZ() + random.nextDouble(), 0, 0.02, 0);
            }
        }
    }

    /** Concha Gigante com a Pérola do Amanhã: fica no fundo do mar de Odym. */
    public static class Concha extends Block implements SimpleWaterloggedBlock {
        private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 9, 15);

        public Concha(Properties props) {
            super(props);
            registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.WATERLOGGED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(BlockStateProperties.WATERLOGGED);
        }

        @Nullable
        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            boolean water = ctx.getLevel().getFluidState(ctx.getClickedPos()).getType() == Fluids.WATER;
            return defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, water);
        }

        @Override
        public FluidState getFluidState(BlockState state) {
            return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
        }

        @Override
        public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
            if (state.getValue(BlockStateProperties.WATERLOGGED)) {
                level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            }
            return super.updateShape(state, dir, other, level, pos, otherPos);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.GLOW, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 0, 0.03, 0);
            }
        }
    }

    /** Lágrima Índigo: cresce pendurada no teto das cavernas de Nok. */
    public static class Lagrima extends Block {
        private static final VoxelShape SHAPE = Block.box(1, 2, 1, 15, 16, 15);

        public Lagrima(Properties props) {
            super(props);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }

        @Override
        public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            return Block.canSupportCenter(level, pos.above(), Direction.DOWN);
        }

        @Override
        public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
            return dir == Direction.UP && !canSurvive(state, level, pos)
                    ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                    : super.updateShape(state, dir, other, level, pos, otherPos);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextInt(5) == 0) {
                level.addParticle(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, pos.getX() + 0.3 + random.nextDouble() * 0.4,
                        pos.getY() + 0.05, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0, 0);
            }
        }
    }

    /** Gelo negro: escorrega como gelo compactado. */
    public static class GeloNegro extends Block {
        public GeloNegro(Properties props) {
            super(props);
        }
    }

    private LoreBlockTypes() {}
}
