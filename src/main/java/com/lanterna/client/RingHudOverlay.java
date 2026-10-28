package com.lanterna.client;

import com.lanterna.corps.CorpsDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * HUD do anel, no canto superior esquerdo:
 *
 *   ◆ Tropa dos Lanternas Verdes
 *   [██████████░░░░]  72%
 *   VOO ATIVO
 */
public final class RingHudOverlay {

    private static final int BAR_WIDTH = 90;
    private static final int BAR_HEIGHT = 7;

    public static final IGuiOverlay HUD = (ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !ClientRingData.hasRing) return;

        Font font = mc.font;
        int color = 0xFF000000 | ClientRingData.color;
        int x = 8;
        int y = 8;

        // Nome da Tropa com um "brilho" (losango) na cor dela
        Component name = Component.translatable(CorpsDefinition.fullNameKey(ClientRingData.corps));
        g.drawString(font, "◆", x, y, color, true);
        g.drawString(font, name, x + 9, y, color, true);
        y += 12;

        // Barra de carga
        int max = Math.max(1, ClientRingData.maxCharge);
        int charge = Math.max(0, Math.min(ClientRingData.charge, max));
        int filled = charge * BAR_WIDTH / max;
        int percent = charge * 100 / max;

        g.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, 0xCC000000);   // moldura
        g.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF1A1A1A);                    // fundo
        g.fill(x, y, x + filled, y + BAR_HEIGHT, color);                           // carga
        g.fill(x, y, x + filled, y + 2, 0x55FFFFFF);                               // reflexo

        // Pisca em vermelho quando a carga está baixa
        boolean low = percent <= 15;
        boolean blink = mc.level != null && (mc.level.getGameTime() / 8) % 2 == 0;
        int textColor = low && blink ? 0xFFFF4040 : 0xFFFFFFFF;
        g.drawString(font, percent + "%", x + BAR_WIDTH + 5, y - 1, textColor, true);
        y += BAR_HEIGHT + 5;

        if (low) {
            g.drawString(font, Component.translatable("hud.lanterna.low_charge"), x, y, textColor, true);
            y += 10;
        }

        if (ClientRingData.flight) {
            g.drawString(font, Component.translatable("hud.lanterna.flight_on"), x, y, color, true);
        }
    };

    private RingHudOverlay() {}
}
