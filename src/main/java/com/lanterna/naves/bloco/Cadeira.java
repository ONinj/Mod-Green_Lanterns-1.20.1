package com.lanterna.naves.bloco;

import com.lanterna.naves.AssentoEntity;
import com.lanterna.naves.NaveEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/** Cadeira da nave: clique para sentar (Shift levanta). */
public class Cadeira extends Horizontal {
    public Cadeira(Properties p) { super(p); }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSecondaryUseActive() || player.isPassenger()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!level.getEntitiesOfClass(AssentoEntity.class, new AABB(pos)).isEmpty()) return InteractionResult.CONSUME;
        AssentoEntity seat = new AssentoEntity(NaveEntities.ASSENTO.get(), level);
        seat.moveTo(pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, state.getValue(FACING).toYRot(), 0);
        level.addFreshEntity(seat);
        player.startRiding(seat, true);
        return InteractionResult.CONSUME;
    }
}
