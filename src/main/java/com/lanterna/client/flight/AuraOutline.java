package com.lanterna.client.flight;

import com.lanterna.client.ClientRingData;
import com.lanterna.client.ClientVisibleRings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Contorno fino na cor da Tropa em volta de quem está com o anel ligado
 * (o mesmo traço do efeito "Brilho" do Minecraft, mas na cor do anel).
 *
 * Diferente do efeito Brilho, aqui o contorno de outros jogadores só aparece
 * quando você consegue vê-los de verdade (não atravessa paredes).
 */
public final class AuraOutline {

    /** Esse jogador deve aparecer com contorno agora? */
    public static boolean shouldOutline(Entity entity) {
        if (!(entity instanceof Player player) || player.isInvisible()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!isActive(player)) return false;
        if (player == mc.player) return true;
        return mc.player != null && mc.player.hasLineOfSight(player);
    }

    /** Cor do contorno (cor da Tropa), ou -1 se não for o caso. */
    public static int color(Entity entity) {
        if (!(entity instanceof Player player) || !isActive(player)) return -1;
        Minecraft mc = Minecraft.getInstance();
        return player == mc.player ? ClientRingData.color : ClientVisibleRings.of(player.getId()).color();
    }

    private static boolean isActive(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player) return ClientRingData.hasRing && ClientRingData.active;
        return ClientVisibleRings.of(player.getId()).active();
    }

    private AuraOutline() {}
}
