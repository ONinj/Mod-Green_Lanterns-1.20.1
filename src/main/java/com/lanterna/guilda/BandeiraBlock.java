package com.lanterna.guilda;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Bandeira da Guilda: fincada num planeta gerado, conquista o planeta e monta a base em volta.
 * Não quebra; só durante uma invasão, depois que todas as tropas de defesa caem, ela fica LIBERADA e os invasores
 * podem derrubá-la para tomar o planeta.
 */
public class BandeiraBlock extends BaseEntityBlock {
    public static final BooleanProperty LIBERADA = BooleanProperty.create("liberada");
    private static final VoxelShape FORMA = Shapes.or(Block.box(1, 0, 1, 15, 4, 15), Block.box(6.5, 4, 6.5, 9.5, 16, 9.5));

    public BandeiraBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(LIBERADA, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LIBERADA);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return FORMA;
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!state.getValue(LIBERADA)) return 0f;
        return 1f / 60f; // ~3 s com a mão
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        if (level.isClientSide || !(by instanceof ServerPlayer p)) return;
        Component erro = Conquistas.fincar(p, (ServerLevel) level, pos);
        if (erro != null) {
            level.removeBlock(pos, false);
            p.displayClientMessage(erro, true);
            p.sendSystemMessage(erro);
            if (!p.isCreative()) p.getInventory().add(new ItemStack(GuildaItems.BANDEIRA_ITEM.get()));
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entidade(pos, state);
    }

    /** Só existe para o desenho do mastro e do estandarte com o símbolo da guilda. */
    public static class Entidade extends BlockEntity {
        public Entidade(BlockPos pos, BlockState state) {
            super(GuildaItems.BANDEIRA_BE.get(), pos, state);
        }

        @Override
        public net.minecraft.world.phys.AABB getRenderBoundingBox() {
            return new net.minecraft.world.phys.AABB(getBlockPos()).inflate(3, 8, 3).move(0, 6, 0);
        }
    }
}
