package com.lanterna.naves.bloco;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.power.RingPowers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Estação de recarga: enche o anel de qualquer Tropa. Depois precisa de 5 minutos para carregar de novo. */
public class Recarga extends Horizontal {
    public static final int ESPERA = 20 * 60 * 5;
    private static final String KEY = "lanterna_recarga_nave";

    public Recarga(Properties p) { super(p); }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        var ring = ModCapabilities.get(sp).orElse(null);
        if (ring == null || !ring.hasRing()) {
            sp.displayClientMessage(Component.translatable("nave.lanterna.recarga.sem_anel"), true);
            return InteractionResult.CONSUME;
        }
        if (ring.charge() >= ring.maxCharge()) {
            sp.displayClientMessage(Component.translatable("nave.lanterna.recarga.cheio"), true);
            return InteractionResult.CONSUME;
        }
        long now = level.getGameTime();
        long pronto = sp.getPersistentData().getLong(KEY);
        if (now < pronto) {
            sp.displayClientMessage(Component.translatable("nave.lanterna.recarga.espera", (pronto - now) / 20 + 1), true);
            return InteractionResult.CONSUME;
        }
        sp.getPersistentData().putLong(KEY, now + ESPERA);
        RingPowers.setCharge(sp, ring, ring.maxCharge());
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1f, 1.4f);
        ((ServerLevel) level).sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 20, 0.3, 0.4, 0.3, 0.02);
        sp.displayClientMessage(Component.translatable("nave.lanterna.recarga.ok").withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.CONSUME;
    }
}
