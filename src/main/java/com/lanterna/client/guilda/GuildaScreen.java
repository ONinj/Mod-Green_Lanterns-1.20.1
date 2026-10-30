package com.lanterna.client.guilda;

import com.lanterna.client.hud.Hud;
import com.lanterna.guilda.GuildaAcaoPacket;
import com.lanterna.guilda.GuildaPainelPacket;
import com.lanterna.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Painel do Minerador / General da guilda. */
public class GuildaScreen extends Screen {
    private static final int W = 300;
    private final GuildaPainelPacket d;
    private int H;

    public GuildaScreen(GuildaPainelPacket d) {
        super(texto(d.tituloJson()));
        this.d = d;
    }

    public static void abrir(GuildaPainelPacket m) {
        Minecraft.getInstance().setScreen(new GuildaScreen(m));
    }

    static Component texto(String json) {
        Component c = Component.Serializer.fromJson(json);
        return c == null ? Component.empty() : c;
    }

    @Override
    protected void init() {
        H = 40 + d.linhasJson().size() * 13 + 8 + d.acoes().size() * 22 + 28;
        int left = (width - W) / 2, top = (height - H) / 2;
        int y = top + 34 + d.linhasJson().size() * 13 + 6;
        for (int i = 0; i < d.acoes().size(); i++) {
            String acao = d.acoes().get(i);
            Button b = Button.builder(texto(d.rotulosJson().get(i)), btn -> ModNetwork.sendToServer(new GuildaAcaoPacket(d.planeta(), acao)))
                    .bounds(left + 20, y, W - 40, 18).build();
            b.active = d.ativos().get(i);
            addRenderableWidget(b);
            y += 22;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + W / 2 - 40, top + H - 24, 80, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int left = (width - W) / 2, top = (height - H) / 2;
        Hud.panel(g, left, top, W, H, d.cor(), 0.95f);
        g.drawString(font, Hud.title(getTitle()), left + 10, top + 8, Hud.argb(d.cor(), 255), false);
        g.fill(left + 10, top + 22, left + W - 10, top + 23, 0x30FFFFFF);
        int y = top + 30;
        for (String l : d.linhasJson()) {
            g.drawString(font, Hud.body(texto(l)), left + 12, y, 0xFFE0E8EC, false);
            y += 13;
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
