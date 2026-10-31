package com.lanterna.client.flight;

import com.lanterna.client.ClientRingData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * HUD do voo (embaixo, no centro, acima da barra de itens):
 *   velocidade · carga da hiperpropulsão · estado do piloto automático
 * E o clarão na tela ao romper a barreira.
 */
public final class FlightHud {

    public static final IGuiOverlay HUD = (ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Clarão da hiperpropulsão
        float flash = HyperEffects.flashAlpha(partialTick);
        if (flash > 0.01f) {
            int a = (int) (Math.min(1f, flash) * 140);
            int c = ClientFlight.lighten(ClientRingData.color, 0.7f);
            g.fill(0, 0, width, height, (a << 24) | c);
        }

        if (mc.options.hideGui || !ClientFlight.isFlying(mc.player)) return;
        Font font = mc.font;
        int color = 0xFF000000 | ClientRingData.color;
        // Acima da mensagem da barra de ação (que fica em height - 68)
        int y = height - 84;
        if (mc.player.isCreative()) y += 16;

        // Piloto automático
        if (Autopilot.active() && Autopilot.target() != null) {
            String phase = Autopilot.phase() == null ? "" : Autopilot.phase().name().toLowerCase();
            Component line = Component.translatable("autopilot.lanterna.hud",
                    Autopilot.target().name(),
                    Component.translatable("autopilot.lanterna.phase." + phase),
                    formatDistance(Autopilot.remaining(mc.player)));
            int w = font.width(line);
            Component cancel = Component.translatable("autopilot.lanterna.cancel_hint");
            int w2 = font.width(cancel);
            y -= 12;
            g.fill(width / 2 - Math.max(w, w2) / 2 - 4, y - 3, width / 2 + Math.max(w, w2) / 2 + 4, y + 20, 0x88000000);
            g.drawString(font, line, width / 2 - w / 2, y, color, true);
            g.drawString(font, cancel, width / 2 - w2 / 2, y + 10, 0xFFB0B8C0, true);
            y -= 14;
        }

        // Velocidade e hiperpropulsão
        double bps = ClientFlight.localSpeed() * 20;
        if (bps > 8) {
            Component speed = Component.translatable("flight.lanterna.speed", String.format("%,d", (int) bps));
            if (ClientFlight.hyperOn()) {
                boolean blink = (mc.player.tickCount / 5) % 2 == 0;
                Component hyper = Component.translatable("flight.lanterna.hyper");
                g.drawString(font, hyper, width / 2 - font.width(hyper) / 2, y, blink ? 0xFFFFFFFF : color, true);
                y -= 10;
            }
            g.drawString(font, speed, width / 2 - font.width(speed) / 2, y, 0xFFE0E0E0, true);
            y -= 10;
        }

        // Carregando a hiperpropulsão
        float charge = ClientFlight.hyperCharge();
        if (charge > 0f && !ClientFlight.hyperOn()) {
            int w = 80;
            int x = width / 2 - w / 2;
            g.fill(x - 1, y - 1, x + w + 1, y + 4, 0xCC000000);
            g.fill(x, y, x + (int) (w * Math.min(1f, charge)), y + 3, color);
            Component label = Component.translatable("flight.lanterna.hyper_charging");
            g.drawString(font, label, width / 2 - font.width(label) / 2, y - 10, 0xFFE0E0E0, true);
        }
    };

    public static String formatDistance(double blocks) {
        if (blocks >= 10000) return String.format("%.1f km", blocks / 1000.0);
        return String.format("%,d m", (int) blocks);
    }

    private FlightHud() {}
}
