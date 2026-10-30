package com.lanterna.client.guilda;

import com.lanterna.client.hud.Hud;
import com.lanterna.guilda.AbrirSimboloPacket;
import com.lanterna.guilda.Guilda;
import com.lanterna.guilda.GuildaAcaoPacket;
import com.lanterna.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Editor do símbolo da guilda: grade 16x16, 16 cores (a primeira apaga). Botão esquerdo pinta, direito apaga.
 * Ao lado, a prévia no estandarte e no escudo.
 */
public class SimboloScreen extends Screen {
    private static final int CEL = 11, GRADE = 16 * CEL;
    private final AbrirSimboloPacket d;
    private final byte[] px;
    private int cor = 1;

    public SimboloScreen(AbrirSimboloPacket d) {
        super(Component.translatable("guilda.lanterna.editor", d.nome()));
        this.d = d;
        this.px = d.simbolo().length == 256 ? d.simbolo().clone() : new byte[256];
    }

    public static void abrir(AbrirSimboloPacket m) {
        Minecraft.getInstance().setScreen(new SimboloScreen(m));
    }

    private int gx() { return width / 2 - GRADE / 2 - 60; }
    private int gy() { return height / 2 - GRADE / 2; }

    @Override
    protected void init() {
        int x = gx() + GRADE + 20;
        addRenderableWidget(Button.builder(Component.translatable("guilda.lanterna.editor.limpar"), b -> java.util.Arrays.fill(px, (byte) 0))
                .bounds(x, gy() + GRADE - 62, 120, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("guilda.lanterna.editor.salvar"), b -> {
            ModNetwork.sendToServer(new GuildaAcaoPacket("", "simbolo", px.clone()));
            onClose();
        }).bounds(x, gy() + GRADE - 40, 120, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x, gy() + GRADE - 18, 120, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x0 = gx(), y0 = gy();
        Hud.panel(g, x0 - 10, y0 - 26, GRADE + 160, GRADE + 36 + 30, d.cor(), 0.95f);
        g.drawString(font, Hud.title(getTitle()), x0, y0 - 18, Hud.argb(d.cor(), 255), false);
        // grade (fundo xadrez = transparente)
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int idx = px[y * 16 + x];
            int c = idx == 0 ? (((x + y) & 1) == 0 ? 0xFF2A3034 : 0xFF22272A) : Guilda.PALETA[idx];
            g.fill(x0 + x * CEL, y0 + y * CEL, x0 + x * CEL + CEL - 1, y0 + y * CEL + CEL - 1, c);
        }
        // paleta
        int py = y0 + GRADE + 6;
        for (int i = 0; i < 16; i++) {
            int px0 = x0 + i * CEL;
            int c = i == 0 ? 0xFF555555 : Guilda.PALETA[i];
            if (i == cor) g.fill(px0 - 1, py - 1, px0 + CEL, py + CEL, 0xFFFFFFFF);
            g.fill(px0, py, px0 + CEL - 1, py + CEL - 1, c);
            if (i == 0) g.drawString(font, "x", px0 + 3, py + 1, 0xFFFF6060, false);
        }
        g.drawString(font, Hud.body(Component.translatable("guilda.lanterna.editor.dica")), x0, py + CEL + 4, 0xFF9AA8B0, false);
        // prévia: estandarte e escudo
        int ex = x0 + GRADE + 20, ey = y0;
        int fundo = ClientGuildas.escurecer(d.cor(), 0.45f);
        g.fill(ex, ey, ex + 48, ey + 64, 0xFF000000 | d.cor());
        g.fill(ex + 2, ey + 2, ex + 46, ey + 62, fundo);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int idx = px[y * 16 + x];
            if (idx > 0) g.fill(ex + 8 + x * 2, ey + 16 + y * 2, ex + 10 + x * 2, ey + 18 + y * 2, Guilda.PALETA[idx]);
        }
        int cx = ex + 92, cy = ey + 32, r = 28;
        for (int y = -r; y <= r; y++) for (int x = -r; x <= r; x++) {
            double dd = Math.sqrt(x * x + y * y);
            if (dd > r) continue;
            if (dd > r - 3) g.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, Hud.argb(d.cor(), 200));
            else if (dd > r - 10) g.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, Hud.argb(d.cor(), (int) (60 * (dd - r + 10) / 7)));
        }
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int idx = px[y * 16 + x];
            if (idx > 0) g.fill(cx - 16 + x * 2, cy - 16 + y * 2, cx - 14 + x * 2, cy - 14 + y * 2, Guilda.PALETA[idx]);
        }
        super.render(g, mx, my, pt);
    }

    private boolean pintar(double mx, double my, int botao) {
        int x0 = gx(), y0 = gy();
        int x = (int) ((mx - x0) / CEL), y = (int) ((my - y0) / CEL);
        if (mx >= x0 && my >= y0 && x < 16 && y < 16) {
            px[y * 16 + x] = (byte) (botao == 1 ? 0 : cor);
            return true;
        }
        int py = y0 + GRADE + 6;
        if (my >= py && my < py + CEL && mx >= x0 && mx < x0 + 16 * CEL) {
            cor = (int) ((mx - x0) / CEL);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        return pintar(mx, my, b) || super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        int x0 = gx(), y0 = gy();
        if (mx >= x0 && my >= y0 && mx < x0 + GRADE && my < y0 + GRADE) return pintar(mx, my, b);
        return super.mouseDragged(mx, my, b, dx, dy);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
