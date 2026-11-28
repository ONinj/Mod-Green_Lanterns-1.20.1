package com.lanterna.client.fonte;

import com.lanterna.client.hud.Hud;
import com.lanterna.fonte.Desafios;
import com.lanterna.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 0.60.3: o Altar dos Desafios, no Salão das Memórias da Fonte. Lista os quatro Lanternas lendários das histórias da
 * Tropa, com a história de cada um, se já foi vencido e quanta Essência dá; "Desafiar" chama o Eco dele.
 */
public class DesafiosScreen extends Screen {
    private final Desafios.Abrir d;

    public static void abrir(Desafios.Abrir d) { Minecraft.getInstance().setScreen(new DesafiosScreen(d)); }

    private DesafiosScreen(Desafios.Abrir d) {
        super(Component.translatable("desafio.lanterna.titulo"));
        this.d = d;
    }

    private int x0() { return width / 2 - 210; }
    private int y0() { return Math.max(8, height / 2 - 118); }

    @Override
    protected void init() {
        for (int i = 0; i < Desafios.N; i++) {
            int ii = i;
            boolean espera = d.espera()[i] > 0 && (d.vencidos() & (1 << i)) != 0;
            Button b = Button.builder(Component.translatable(espera ? "desafio.lanterna.botao_espera" : "desafio.lanterna.botao", d.espera()[i] / 60 + 1),
                    x -> { ModNetwork.sendToServer(new Desafios.Desafiar(d.tropa(), ii)); onClose(); }).bounds(x0() + 420 - 96, y0() + 34 + i * 48 + 12, 88, 20).build();
            b.active = !espera;
            addRenderableWidget(b);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = x0(), y = y0(), w = 420, h = 236;
        Hud.panel(g, x, y, w, h, d.cor(), 0.95f);
        Hud.textCenter(g, Hud.title(title.copy()), width / 2, y + 8, Hud.argb(d.cor(), 255));
        Hud.textCenter(g, Hud.body(Component.translatable("desafio.lanterna.essencia", d.essencia())), width / 2, y + 20, 0xFFB8C4CC);
        for (int i = 0; i < Desafios.N; i++) {
            int ly = y + 34 + i * 48;
            boolean ven = (d.vencidos() & (1 << i)) != 0;
            g.fill(x + 8, ly, x + w - 8, ly + 44, ven ? 0x30FFFFFF : 0x18FFFFFF);
            for (int k = 0; k <= i; k++) g.fill(x + 14 + k * 6, ly + 6, x + 18 + k * 6, ly + 10, Hud.argb(d.cor(), 255));   // nível
            g.drawString(font, Component.translatable("desafio.lanterna." + d.tropa() + "." + i + ".nome"), x + 14 + 4 * 6 + 6, ly + 4, Hud.argb(d.cor(), 255), true);
            Hud.wrapped(g, Hud.body(Component.translatable("desafio.lanterna." + d.tropa() + "." + i + ".historia")), x + 14, ly + 15, w - 130, 0xFFC8D0D8, 9);
            Component pr = ven ? Component.translatable("desafio.lanterna.vencido", 3) : Component.translatable("desafio.lanterna.premio", new int[]{8, 12, 18, 25}[i]);
            g.drawString(font, pr, x + w - 96, ly + 3, ven ? 0xFF9BE7A0 : 0xFFFFD070, false);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
