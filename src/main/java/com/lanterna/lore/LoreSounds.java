package com.lanterna.lore;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Atalhos de som usados pela lore (todos com sons do jogo). */
public final class LoreSounds {

    public static void riftWhisper(Level level, BlockPos pos) {
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.SCULK_CATALYST_BLOOM,
                SoundSource.BLOCKS, 0.6f, 0.5f, false);
    }

    public static void at(Entity e, SoundEvent sound, float volume, float pitch) {
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    public static void at(Level level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
    }

    private LoreSounds() {}
}
