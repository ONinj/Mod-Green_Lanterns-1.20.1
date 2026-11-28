package com.lanterna.client.fonte;

import com.lanterna.client.hud.Hud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * 0.60.4: o diálogo da entidade presa na Fonte. As falas aparecem letra por letra numa faixa escura embaixo da tela
 * (o jogador continua vendo a cela e os olhos dela); clique ou espaço passa para a próxima. A última ameaça a Tropa do
 * jogador.
 */
public class PrisaoScreen extends Screen {
    static final int FALAS = 5;
    private final String tropa, alvo;
    private int fala;
    private long inicio = System.currentTimeMillis();
    /** Quanto a câmera treme (some aos poucos). */
    public static float tremor;

    public static void abrir(String tropa, String alvo) { Minecraft.getInstance().setScreen(new PrisaoScreen(tropa, alvo)); tremor = 1; }

    private PrisaoScreen(String tropa, String alvo) {
        super(Component.translatable("prisao.lanterna." + tropa + ".nome"));
        this.tropa = tropa;
        this.alvo = alvo;
    }

    private int cor() {
        var t = com.lanterna.fonte.Desafios.tropa(tropa);
        return t == null ? 0xFFFFFF : t.cor;
    }

    private Component texto() {
        String k = "prisao.lanterna." + tropa + "." + (fala + 1);
        return fala == FALAS - 1 ? Component.translatable(k, Component.translatable("corps.lanterna." + alvo + ".full")) : Component.translatable(k);
    }

    private void proxima() {
        String t = texto().getString();
        long letras = (System.currentTimeMillis() - inicio) / 28;
        if (letras < t.length()) { inicio = 0; return; }   // primeiro mostra a fala inteira
        fala++;
        inicio = System.currentTimeMillis();
        tremor = 0.6f;
        if (minecraft != null && minecraft.player != null) minecraft.player.playSound(SoundEvents.WARDEN_HEARTBEAT, 1f, 0.55f + fala * 0.05f);
        if (fala >= FALAS) onClose();
    }

    @Override
    public boolean mouseClicked(double x, double y, int b) { proxima(); return true; }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 32 || key == 257) { proxima(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (fala >= FALAS) return;
        int h = 74, y = height - h - 18;
        // escurece a borda da tela, como se a sala inteira se fechasse
        for (int k = 0; k < 30; k++) {
            int a = (int) (110 * (1 - k / 30f));
            g.fill(0, k * 3, width, k * 3 + 3, a << 24);
        }
        g.fill(20, y, width - 20, y + h, 0xD8000000);
        g.fill(20, y, width - 20, y + 1, Hud.argb(cor(), 255));
        g.drawString(font, title, 32, y + 8, Hud.argb(cor(), 255), true);
        String t = texto().getString();
        int n = inicio == 0 ? t.length() : (int) Math.min(t.length(), (System.currentTimeMillis() - inicio) / 28);
        Hud.wrapped(g, Hud.body(Component.literal(t.substring(0, n))), 32, y + 24, width - 64, 0xFFF0F0F0, 11);
        g.drawString(font, Component.translatable("prisao.lanterna.continuar", fala + 1, FALAS), width - 32 - font.width(Component.translatable("prisao.lanterna.continuar", fala + 1, FALAS)),
                y + h - 12, 0xFF8A949C, false);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
