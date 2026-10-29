package com.lanterna.client.hud;

import com.lanterna.LanternaMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Desenho dos HUDs do mod: fontes próprias (Rajdhani para o texto, Orbitron para os títulos — ambas
 * com licença aberta OFL), painéis translúcidos com borda na cor da Tropa e barras com brilho.
 */
public final class Hud {
    public static final ResourceLocation BODY = LanternaMod.id("hud");
    public static final ResourceLocation BOLD = LanternaMod.id("hud_bold");
    public static final ResourceLocation TITLE = LanternaMod.id("hud_title");

    public static MutableComponent body(Component c) {
        return c.copy().withStyle(s -> s.withFont(BODY));
    }

    public static MutableComponent bold(Component c) {
        return c.copy().withStyle(s -> s.withFont(BOLD));
    }

    public static MutableComponent title(Component c) {
        return c.copy().withStyle(s -> s.withFont(TITLE));
    }

    public static MutableComponent body(String s) {
        return Component.literal(s).withStyle(Style.EMPTY.withFont(BODY));
    }

    public static MutableComponent title(String s) {
        return Component.literal(s).withStyle(Style.EMPTY.withFont(TITLE));
    }

    /** Título em maiúsculas (a Orbitron fica bonita assim). */
    public static MutableComponent caps(Component c) {
        return Component.literal(c.getString().toUpperCase(java.util.Locale.ROOT)).withStyle(Style.EMPTY.withFont(TITLE));
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static int argb(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** Painel escuro translúcido com um filete de cor à esquerda e cantos chanfrados. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int color, float alpha) {
        int a = (int) (alpha * 255);
        int bg = argb(0x0A0E12, (int) (a * 0.72f));
        g.fill(x + 1, y, x + w - 1, y + h, bg);
        g.fill(x, y + 1, x + 1, y + h - 1, bg);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, bg);
        g.fillGradient(x + 1, y, x + w - 1, y + 1, argb(color, (int) (a * 0.9f)), argb(color, (int) (a * 0.1f)));
        g.fill(x, y + 2, x + 2, y + h - 2, argb(color, a));
        g.fillGradient(x + 1, y + h - 1, x + w - 1, y + h, argb(0xFFFFFF, (int) (a * 0.06f)), argb(0xFFFFFF, (int) (a * 0.02f)));
    }

    /** Barra com fundo, preenchimento em degradê, brilho em cima e marcas de 10%. */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color, boolean ticks) {
        fraction = Math.max(0, Math.min(1, fraction));
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xC0000000);
        g.fill(x, y, x + w, y + h, 0xFF14181C);
        int filled = Math.round(w * fraction);
        if (filled > 0) {
            g.fillGradient(x, y, x + filled, y + h, argb(mix(color, 0xFFFFFF, 0.25f), 255), argb(mix(color, 0x000000, 0.35f), 255));
            g.fill(x, y, x + filled, y + 1, 0x60FFFFFF);
            g.fill(x + filled - 1, y, x + filled, y + h, argb(mix(color, 0xFFFFFF, 0.6f), 255));
        }
        if (ticks) {
            for (int i = 1; i < 10; i++) {
                int tx = x + w * i / 10;
                g.fill(tx, y, tx + 1, y + h, 0x50000000);
            }
        }
    }

    public static void text(GuiGraphics g, Component c, int x, int y, int color) {
        g.drawString(font(), c, x, y, color, true);
    }

    public static void textRight(GuiGraphics g, Component c, int right, int y, int color) {
        g.drawString(font(), c, right - font().width(c), y, color, true);
    }

    public static void textCenter(GuiGraphics g, Component c, int cx, int y, int color) {
        g.drawString(font(), c, cx - font().width(c) / 2, y, color, true);
    }

    /** Quebra o texto em linhas e desenha; devolve a altura usada. */
    public static int wrapped(GuiGraphics g, Component c, int x, int y, int width, int color, int lineHeight) {
        List<FormattedCharSequence> lines = font().split(c, width);
        for (int i = 0; i < lines.size(); i++) g.drawString(font(), lines.get(i), x, y + i * lineHeight, color, true);
        return lines.size() * lineHeight;
    }

    /** Distância bonita: 850 m, 12,4 mil, 1,2 mi. */
    public static String distance(double blocks) {
        if (blocks < 1000) return String.format(java.util.Locale.ROOT, "%d blocos", (int) blocks);
        if (blocks < 1_000_000) return String.format(new java.util.Locale("pt", "BR"), "%.1f mil blocos", blocks / 1000.0);
        return String.format(new java.util.Locale("pt", "BR"), "%.2f mi blocos", blocks / 1_000_000.0);
    }

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    /** Seta para um ângulo relativo (graus, 0 = em frente). */
    public static String arrow(float relativeYaw) {
        return ARROWS[Math.floorMod(Math.round(relativeYaw / 45f), 8)];
    }

    private Hud() {}
}
