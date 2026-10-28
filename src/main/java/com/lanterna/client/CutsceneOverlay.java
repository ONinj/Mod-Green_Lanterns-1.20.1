package com.lanterna.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Efeito de tela da entrega do anel:
 *  - bordas da tela vão se tingindo na cor da Tropa (vinheta)
 *  - a cor pulsa, como um coração (vermelho) ou uma luz respirando (demais)
 *  - no final, um clarão forte quando o anel encaixa no dedo
 */
public final class CutsceneOverlay {

    public static final IGuiOverlay OVERLAY = (ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) -> {
        if (!ClientCutscene.active() || Minecraft.getInstance().player == null) return;

        float p = ClientCutscene.progress(partialTick);
        int rgb = ClientCutscene.color() & 0xFFFFFF;
        float time = (Minecraft.getInstance().player.tickCount + partialTick);

        // Pulso: vermelho bate como coração, as outras respiram devagar
        float pulse = ClientCutscene.isRed()
                ? (float) Math.pow(Math.max(0, Math.sin(time * 0.35)), 6)
                : 0.5f + 0.5f * Mth.sin(time * 0.15f);

        String style = ClientCutscene.style();
        boolean dark = style.equals("yellow") || style.equals("black");

        // Medo e morte: o mundo escurece por inteiro, e a cor só aparece em lampejos
        if (dark) {
            int black = (int) Mth.clamp(60 + 150 * p, 0, 215);
            g.fill(0, 0, width, height, black << 24);
            float flicker = style.equals("yellow")
                    ? (Mth.sin(time * 1.7f) > 0.75f ? 1f : 0f)
                    : 0.4f + 0.3f * Mth.sin(time * 0.08f);
            int edgeA = (int) (flicker * 150 * p);
            int band = Math.max(16, (int) (height * 0.12f));
            g.fillGradient(0, 0, width, band, (edgeA << 24) | rgb, rgb);
            g.fillGradient(0, height - band, width, height, rgb, (edgeA << 24) | rgb);
        }

        // Vida: tudo vai clareando até ficar branco
        if (style.equals("white")) {
            int glow = (int) Mth.clamp(p * p * 170, 0, 170);
            g.fill(0, 0, width, height, (glow << 24) | 0xFFFFFF);
        }

        // Vinheta nas bordas (no medo e na morte ela é bem fraca, para o escuro dominar)
        float edgeScale = dark ? 0.35f : 1f;
        int edgeAlpha = (int) (Mth.clamp((40 + 120 * p) + pulse * 50, 0, 220) * edgeScale);
        int edge = (edgeAlpha << 24) | rgb;
        int clear = rgb; // alpha 0
        int band = Math.max(20, (int) (height * (0.18f + 0.22f * p)));
        int side = Math.max(20, (int) (width * (0.12f + 0.15f * p)));

        g.fillGradient(0, 0, width, band, edge, clear);                     // topo
        g.fillGradient(0, height - band, width, height, clear, edge);       // base
        fillHorizontalGradient(g, 0, 0, side, height, edge, clear);         // esquerda
        fillHorizontalGradient(g, width - side, 0, width, height, clear, edge); // direita

        // Tinta suave na tela inteira, cresce com o progresso
        int washAlpha = (int) (Mth.clamp(p * 50 + pulse * 15, 0, 90) * edgeScale);
        g.fill(0, 0, width, height, (washAlpha << 24) | rgb);

        // Clarão final (últimos 8 ticks)
        int left = ClientCutscene.ticksLeft();
        if (left <= 8) {
            float f = 1f - (left - partialTick) / 8f;
            int flashAlpha = (int) Mth.clamp(220 * Mth.sin((float) Math.PI * Mth.clamp(f, 0, 1)), 0, 230);
            int flashColor = blend(rgb, 0xFFFFFF, 0.55f);
            g.fill(0, 0, width, height, (flashAlpha << 24) | flashColor);
        }
    };

    /** O GuiGraphics só tem gradiente vertical; aqui fazemos o horizontal em faixas. */
    private static void fillHorizontalGradient(GuiGraphics g, int x1, int y1, int x2, int y2, int from, int to) {
        int w = x2 - x1;
        int steps = Math.max(1, Math.min(w, 32));
        for (int i = 0; i < steps; i++) {
            float t = (i + 0.5f) / steps;
            int sx1 = x1 + w * i / steps;
            int sx2 = x1 + w * (i + 1) / steps;
            g.fill(sx1, y1, sx2, y2, lerpArgb(from, to, t));
        }
    }

    private static int lerpArgb(int a, int b, float t) {
        int aa = (a >>> 24), ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24), br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    private static int blend(int rgbA, int rgbB, float t) {
        return lerpArgb(rgbA, rgbB, t) & 0xFFFFFF;
    }

    private CutsceneOverlay() {}
}
