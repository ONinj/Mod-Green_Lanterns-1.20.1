package com.lanterna.naves.bloco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Quadro de contratos: painel 2 x 2 na parede. Ao colocar, ocupa os 4 blocos; quebrar uma parte quebra as 4.
 * "esq"/"dir" são de quem OLHA o quadro: com facing=north, a parte _esq fica a leste (+x) da _dir.
 * Clique: abre os contratos daquele planeta.
 */
public class QuadroContratos extends HorizontalDirectionalBlock {
    public enum Parte implements StringRepresentable {
        BAIXO_ESQ("baixo_esq"), BAIXO_DIR("baixo_dir"), CIMA_ESQ("cima_esq"), CIMA_DIR("cima_dir");
        private final String name;
        Parte(String n) { name = n; }
        @Override public String getSerializedName() { return name; }
        boolean cima() { return this == CIMA_ESQ || this == CIMA_DIR; }
        boolean esq() { return this == BAIXO_ESQ || this == CIMA_ESQ; }
    }

    public static final EnumProperty<Parte> PARTE = EnumProperty.create("parte", Parte.class);
    private static final VoxelShape[] SHAPES = {
            Block.box(0, 0, 13, 16, 16, 16),  // north: costas no sul
            Block.box(0, 0, 0, 3, 16, 16),    // east
            Block.box(0, 0, 0, 16, 16, 3),    // south
            Block.box(13, 0, 0, 16, 16, 16)}; // west

    public QuadroContratos(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PARTE, Parte.BAIXO_DIR));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, PARTE);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return switch (s.getValue(FACING)) {
            case EAST -> SHAPES[1];
            case SOUTH -> SHAPES[2];
            case WEST -> SHAPES[3];
            default -> SHAPES[0];
        };
    }

    /** Posição da parte "baixo_dir" (a âncora) a partir de qualquer parte. */
    private static BlockPos anchor(BlockPos pos, BlockState s) {
        Parte p = s.getValue(PARTE);
        BlockPos a = p.cima() ? pos.below() : pos;
        if (p.esq()) a = a.relative(s.getValue(FACING).getCounterClockWise());
        return a;
    }

    private static BlockPos of(BlockPos anchor, Direction facing, Parte p) {
        BlockPos q = p.cima() ? anchor.above() : anchor;
        return p.esq() ? q.relative(facing.getClockWise()) : q;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getClickedFace().getAxis().isHorizontal() ? ctx.getClickedFace() : ctx.getHorizontalDirection().getOpposite();
        BlockPos a = ctx.getClickedPos();
        Level level = ctx.getLevel();
        for (Parte p : Parte.values()) {
            BlockPos q = of(a, facing, p);
            if (!level.getBlockState(q).canBeReplaced(ctx) || !level.getWorldBorder().isWithinBounds(q)) return null;
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PARTE, Parte.BAIXO_DIR);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        if (level.isClientSide) return;
        Direction f = state.getValue(FACING);
        for (Parte p : Parte.values()) {
            if (p == Parte.BAIXO_DIR) continue;
            level.setBlock(of(pos, f, p), state.setValue(PARTE, p), 3);
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos a = anchor(pos, state);
            Direction f = state.getValue(FACING);
            for (Parte p : Parte.values()) {
                BlockPos q = of(a, f, p);
                if (!q.equals(pos) && level.getBlockState(q).is(this)) level.setBlock(q, Blocks.AIR.defaultBlockState(), 35);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) com.lanterna.tropa.Contratos.abrirQuadro(sp);
        return InteractionResult.CONSUME;
    }
}
