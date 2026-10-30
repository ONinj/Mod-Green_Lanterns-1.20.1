package com.lanterna.naves.bloco;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Beliche: funciona como cama (dormir, ponto de renascer), mas com o modelo próprio. */
public class Beliche extends BedBlock {
    public Beliche(Properties p) { super(DyeColor.GRAY, p); }

    /**
     * Deitar no beliche: o ponto de renascer fica na nave, recupera um pouco de vida e, se for noite
     * lá fora (no mundo normal), a noite passa — a sala da nave fica sempre de dia, então a cama comum não deixaria dormir.
     */
    @Override
    @SuppressWarnings("deprecation")
    public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) return net.minecraft.world.InteractionResult.PASS;
        BlockPos head = state.getValue(PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD ? pos : pos.relative(state.getValue(FACING));
        sp.setRespawnPosition(level.dimension(), head, sp.getYRot(), false, false);
        net.minecraft.server.level.ServerLevel ow = sp.server.overworld();
        long t = ow.getDayTime() % 24000;
        sp.heal(6);
        sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 200, 0));
        if (t >= 12542 && t < 23460) {
            ow.setDayTime(ow.getDayTime() - t + 24000);
            if (ow.isThundering() || ow.isRaining()) ow.setWeatherParameters(12000, 0, false, false);
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("nave.lanterna.beliche.dormiu"), true);
        } else {
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("nave.lanterna.beliche.descansou"), true);
        }
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER, net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 0.8f);
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }
}
