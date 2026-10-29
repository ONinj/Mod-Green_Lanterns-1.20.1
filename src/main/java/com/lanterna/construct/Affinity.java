package com.lanterna.construct;

import com.lanterna.power.EquipmentHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * Afinidade (aceitação) do anel com o portador. Fica gravada no próprio anel: quanto mais você usa,
 * mais o anel confia em você e mais fortes ficam os construtos.
 *
 *   0 Desconfiado   1 Tolerante   2 Aceito   3 Confiável   4 Vinculado   5 Uno com o anel
 *
 * Ganha: 1 ponto por segundo com o anel ligado, pontos por construto usado e muitos por missão.
 */
public final class Affinity {
    public static final String TAG = "Affinity";
    public static final int[] THRESHOLDS = {0, 600, 2400, 6000, 12000, 24000};
    public static final int MAX_LEVEL = THRESHOLDS.length - 1;

    public static int points(ItemStack ring) {
        return ring.isEmpty() || !ring.hasTag() ? 0 : ring.getTag().getInt(TAG);
    }

    public static int level(int points) {
        int lvl = 0;
        for (int i = 0; i < THRESHOLDS.length; i++) if (points >= THRESHOLDS[i]) lvl = i;
        return lvl;
    }

    /** 0..1 até o próximo nível (1 no nível máximo). */
    public static float progress(int points) {
        int lvl = level(points);
        if (lvl >= MAX_LEVEL) return 1f;
        int a = THRESHOLDS[lvl], b = THRESHOLDS[lvl + 1];
        return (points - a) / (float) (b - a);
    }

    /** Força dos construtos: 1.0 (desconfiado) até 2.0 (uno com o anel). */
    public static float power(int points) {
        return 1.0f + 0.2f * level(points);
    }

    // ---- Bônus passivos do anel (crescem com a afinidade; só com o anel ligado e com carga) ----

    /** A cada quantos segundos o anel mata um pouco da fome (+1 de comida). */
    public static int foodSeconds(int level) {
        return Math.max(5, 20 - 3 * level);
    }

    /** A cada quantos segundos o anel cura meio coração. */
    public static int regenSeconds(int level) {
        return Math.max(2, 12 - 2 * level);
    }

    /** Dano extra do soco de mão vazia (antes do multiplicador da Tropa). */
    public static float punchBonus(int level) {
        return 1.5f + 1.0f * level;
    }

    public static Component levelName(int level) {
        return Component.translatable("affinity.lanterna." + level);
    }

    /** Soma afinidade no anel que o jogador está usando (avisa quando sobe de nível). */
    public static void add(ServerPlayer player, int amount) {
        ItemStack ring = EquipmentHelper.ring(player);
        if (ring.isEmpty() || amount == 0) return;
        int before = points(ring);
        int after = Math.max(0, before + amount);
        ring.getOrCreateTag().putInt(TAG, after);
        int lb = level(before), la = level(after);
        if (la > lb) {
            player.sendSystemMessage(Component.translatable("message.lanterna.affinity_up", levelName(la))
                    .withStyle(s -> s.withColor(com.lanterna.item.PowerRingItem.getColor(ring)).withBold(true)));
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.4f);
            player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 1.6f);
        }
    }

    private Affinity() {}
}
