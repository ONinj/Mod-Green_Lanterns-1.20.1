package com.lanterna.client.tropa;

import com.lanterna.client.hud.Hud;
import com.lanterna.mission.Mission;
import com.lanterna.mission.Missions;
import com.lanterna.network.ModNetwork;
import com.lanterna.tropa.ContratoPacket;
import com.lanterna.tropa.Contratos;
import com.lanterna.tropa.QuadroPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Quadro de contratos: as 4 ofertas do dia no planeta, com aceitar / desistir. */
public class QuadroScreen extends Screen {
    private static final int W = 380, ROW = 40;
    private int H = 222;
    private final QuadroPacket data;

    public QuadroScreen(QuadroPacket data) {
        super(Component.translatable("contrato.lanterna.quadro"));
        this.data = data;
    }

    public static void abrir(QuadroPacket p) {
        Minecraft.getInstance().setScreen(new QuadroScreen(p));
    }

    @Override
    protected void init() {
        H = Math.min(height - 8, 64 + data.ids().size() * ROW);
        int left = (width - W) / 2, top = (height - H) / 2;
        for (int i = 0; i < data.ids().size(); i++) {
            String id = data.ids().get(i);
            int st = data.estado().get(i);
            int y = top + 28 + i * ROW;
            if (st == 0) {
                Button b = Button.builder(Component.translatable("contrato.lanterna.aceitar"),
                        btn -> ModNetwork.sendToServer(new ContratoPacket(id, true))).bounds(left + W - 84, y + 5, 76, 18).build();
                b.active = data.ativos() < Contratos.MAX_ATIVOS;
                addRenderableWidget(b);
            } else if (st == 1) {
                addRenderableWidget(Button.builder(Component.translatable("contrato.lanterna.desistir"),
                        btn -> ModNetwork.sendToServer(new ContratoPacket(id, false))).bounds(left + W - 84, y + 5, 76, 18).build());
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + W / 2 - 40, top + H - 24, 80, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int left = (width - W) / 2, top = (height - H) / 2;
        Hud.panel(g, left, top, W, H, 0xFFD040, 0.95f);
        g.drawString(font, Hud.title(Component.translatable("contrato.lanterna.quadro_de",
                Component.translatable("planet.lanterna." + data.planet()))), left + 10, top + 8, 0xFFFFD870, false);
        Hud.textRight(g, Hud.body(Component.translatable("contrato.lanterna.ativos", data.ativos(), Contratos.MAX_ATIVOS)),
                left + W - 10, top + 9, 0xFFB8C4CC);
        for (int i = 0; i < data.ids().size(); i++) {
            String id = data.ids().get(i);
            Mission m = Missions.get(id);
            if (m == null) continue;
            int y = top + 28 + i * ROW;
            int st = data.estado().get(i);
            g.fill(left + 6, y, left + W - 6, y + ROW - 3, 0x40000000);
            g.drawString(font, Hud.bold(m.title()), left + 12, y + 4, st == 2 ? 0xFF6A7880 : 0xFFFFFFFF, false);
            Hud.wrapped(g, Hud.body(m.description()), left + 12, y + 16, W - 110, 0xFF9AA8B0, 9);
            Component tag = st == 2 ? Component.translatable("contrato.lanterna.feito")
                    : Component.translatable("contrato.lanterna.recomendado", Contratos.recomendado(id));
            Hud.textRight(g, Hud.body(tag), left + W - 10, y + 27, st == 2 ? 0xFF60D070 : 0xFFB8A060);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
