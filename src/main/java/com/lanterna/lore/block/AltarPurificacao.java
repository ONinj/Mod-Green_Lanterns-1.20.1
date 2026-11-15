package com.lanterna.lore.block;

import com.lanterna.morte.RitualPurificacao;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Altar da Purificação (0.51): um dais de mármore com quatro cristais, a laje de pedra com runas onde o portador se
 * deita e, na cabeceira, o pedestal onde o anel fica durante o ritual. Os Guardiões de Oa entregam o altar na linha da
 * Purificação (e dá para fabricar outro). O ritual em si está em {@link RitualPurificacao}.
 *
 * Para o jogo deixar o jogador deitado nele, o altar conta como "cama" (isBed) e tem a direção da cabeceira (FACING).
 */
public class AltarPurificacao extends HorizontalDirectionalBlock {
    // a parte que fica no bloco: degrau e a cabeceira da laje (o resto do modelo é só visual)
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 3, 16), Block.box(1, 3, 0, 15, 11, 16));

    public AltarPurificacao(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // a cabeceira fica para o lado que o jogador olha (o corpo deita na direção de quem colocou)
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer sp) RitualPurificacao.usar(sp, pos, state.getValue(FACING));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean isBed(BlockState state, BlockGetter level, BlockPos pos, @Nullable Entity player) {
        return true;
    }

    @Override
    public void setBedOccupied(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.LivingEntity sleeper, boolean occupied) {
        // não é uma cama de verdade: nada para marcar
    }

    @Override
    public Direction getBedDirection(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        return state.getValue(FACING);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        // brilho dos cristais e das runas
        Direction f = state.getValue(FACING);
        if (r.nextInt(3) == 0) {
            double sx = (r.nextBoolean() ? -0.25 : 1.25), sz = (r.nextBoolean() ? -0.9 : 1.85);
            double[] c = gira(sx - 0.5, sz - 0.5, f);
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + c[0], pos.getY() + 1.0, pos.getZ() + 0.5 + c[1], 0, 0.015, 0);
        }
        if (r.nextInt(4) == 0)
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.2 + r.nextDouble() * 0.6, pos.getY() + 0.8, pos.getZ() + 0.2 + r.nextDouble() * 0.6, 0, 0.2, 0);
    }

    /** Gira um deslocamento (dx, dz, pensado para a cabeceira ao norte) para a direção do altar. */
    public static double[] gira(double dx, double dz, Direction f) {
        return switch (f) {
            case EAST -> new double[]{-dz, dx};
            case SOUTH -> new double[]{-dx, -dz};
            case WEST -> new double[]{dz, -dx};
            default -> new double[]{dx, dz};
        };
    }
}
