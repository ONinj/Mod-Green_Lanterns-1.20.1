package com.lanterna.lore.block;

import com.lanterna.lore.LoreSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

/** Blocos das bases dos planetas: forja, altar do desafio, altar da vida, faróis de pérola, rachadura do Nulo. */
public final class RitualBlocks {

    /** Forja do Espectro: forja um anel a partir do Anel Vazio (cada planeta pede o seu ritual). */
    public static class Forja extends Block {
        private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 4, 15), Block.box(3, 4, 3, 13, 10, 13),
                Block.box(0, 10, 0, 16, 14, 16));

        public Forja(Properties props) {
            super(props);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!level.isClientSide && player instanceof ServerPlayer sp) com.lanterna.lore.ritual.ForgeRituals.use(sp, pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.95,
                        pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.01, 0);
            }
            if (random.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                        (random.nextDouble() - 0.5) * 0.02, 0.03, (random.nextDouble() - 0.5) * 0.02);
            }
        }
    }

    /** Altar do Desafio: com minério do planeta, chama o boss do planeta. */
    public static class AltarDesafio extends Block {
        private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 6, 16), Block.box(2, 6, 2, 14, 12, 14));

        public AltarDesafio(Properties props) {
            super(props);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!level.isClientSide && player instanceof ServerPlayer sp) com.lanterna.lore.entity.boss.BossAltar.use(sp, pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextInt(2) == 0) {
                double a = random.nextDouble() * Math.PI * 2;
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5 + Math.cos(a) * 0.5, pos.getY() + 0.8,
                        pos.getZ() + 0.5 + Math.sin(a) * 0.5, 0, 0.04, 0);
            }
        }
    }

    /** Altar da Vida (Gênese): os sete anéis + os sete drops viram o Anel Branco. */
    public static class AltarVida extends Block {
        private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 4, 16), Block.box(4, 4, 4, 12, 12, 12),
                Block.box(1, 12, 1, 15, 15, 15));

        public AltarVida(Properties props) {
            super(props);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!level.isClientSide && player instanceof ServerPlayer sp) com.lanterna.lore.ritual.WhiteAltar.use(sp, pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + random.nextDouble(), pos.getY() + 1.0 + random.nextDouble(),
                    pos.getZ() + random.nextDouble(), 0, 0.02, 0);
        }
    }

    /** Farol de Pérola (arena de Morrow): reacende com uma Pérola do Amanhã. */
    public static class FarolPerola extends Block {
        public static final BooleanProperty ACESO = BooleanProperty.create("aceso");

        public FarolPerola(Properties props) {
            super(props);
            registerDefaultState(stateDefinition.any().setValue(ACESO, true));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(ACESO);
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (state.getValue(ACESO)) return InteractionResult.PASS;
            var stack = player.getItemInHand(hand);
            if (!stack.is(com.lanterna.lore.LoreItems.PEROLA_DO_AMANHA.get())) {
                if (!level.isClientSide) player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.lanterna.farol_needs_pearl"), true);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!level.isClientSide) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                level.setBlock(pos, state.setValue(ACESO, true), 3);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.BLOCKS, 1.5f, 1.4f);
                ((net.minecraft.server.level.ServerLevel) level).sendParticles(new DustParticleOptions(new Vector3f(0.4f, 0.8f, 1f), 2f),
                        pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.4, 0.6, 0.4, 0.05);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    /** Rachadura do Nulo (Gênese): a ferida cinza de onde vazam os Ecos. */
    public static class Rachadura extends Block {
        public Rachadura(Properties props) {
            super(props);
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return Shapes.empty();
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return Block.box(6, 0, 0, 10, 16, 16);
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!level.isClientSide && player instanceof ServerPlayer sp) com.lanterna.lore.entity.boss.BossAltar.useRift(sp, pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextInt(2) == 0) {
                level.addParticle(new DustParticleOptions(new Vector3f(0.55f, 0.55f, 0.58f), 1.5f), pos.getX() + 0.5,
                        pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0, 0.01, 0);
            }
            if (random.nextInt(30) == 0) LoreSounds.riftWhisper(level, pos);
        }
    }

    private RitualBlocks() {}
}
