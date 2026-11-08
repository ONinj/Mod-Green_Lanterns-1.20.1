package com.lanterna.client.voz;

import com.lanterna.client.hud.Hud;
import com.lanterna.voz.Ligacoes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** O comunicador: lista quem está online para ligar, e controla a ligação atual. */
public class ComunicadorScreen extends Screen {
    private final List<PlayerInfo> online = new ArrayList<>();
    private int rolagem;

    public ComunicadorScreen() {
        super(Component.translatable("ligacao.lanterna.titulo"));
    }

    @Override
    protected void init() {
        online.clear();
        var c = Minecraft.getInstance().getConnection();
        if (c != null && minecraft != null && minecraft.player != null)
            for (PlayerInfo i : c.getOnlinePlayers()) if (!i.getProfile().getId().equals(minecraft.player.getUUID())) online.add(i);
        online.sort((a, b) -> a.getProfile().getName().compareToIgnoreCase(b.getProfile().getName()));
    }

    private int pw() { return 260; }
    private int x0() { return (width - pw()) / 2; }
    private int ph() { return Math.min(220, height - 20); }
    private int y0() { return Math.max(10, height / 2 - ph() / 2); }

    private boolean naLigacao(UUID id) {
        for (var p : ClientLigacao.membros) if (p.id().equals(id)) return true;
        for (var p : ClientLigacao.tocando) if (p.id().equals(id)) return true;
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int cor = LigacaoHud.cor(), x = x0(), y = y0(), pw = pw(), ph = ph();
        Hud.panel(g, x, y, pw, ph, cor, 0.96f);
        g.drawString(font, Hud.title(title.copy()), x + 10, y + 8, Hud.argb(cor, 255), false);
        int livre = pw - 30 - font.width(Hud.title(title.copy()));
        Hud.textRight(g, Hud.fit(Hud.body(Component.translatable(ClientLigacao.voz || Ligacoes.svc() ? "ligacao.lanterna.voz_svc" : "ligacao.lanterna.sem_voz")), livre), x + pw - 10, y + 9, 0xFF8A949C);
        int ly = y + 26;
        if (ClientLigacao.modo != 0) {
            // ligação atual
            g.drawString(font, Hud.bold(Component.translatable(ClientLigacao.modo == 2 ? "ligacao.lanterna.chamando_voce" : ClientLigacao.modo == 3 ? "ligacao.lanterna.em_ligacao" : "ligacao.lanterna.chamando")), x + 10, ly, 0xFFFFFFFF, false);
            ly += 14;
            for (var p : ClientLigacao.membros) {
                LigacaoHud.rosto(g, p.id(), x + 12, ly, 12, p.falando(), cor);
                g.drawString(font, Hud.body(Component.literal(p.nome())), x + 30, ly + 2, 0xFFE0E6EA, false);
                ly += 16;
            }
            if (ClientLigacao.modo == 2) {
                botao(g, x + 10, ly + 2, 115, Component.translatable("ligacao.lanterna.atender"), 0x43E06A, mx, my);
                botao(g, x + 135, ly + 2, 115, Component.translatable("ligacao.lanterna.recusar"), 0xE04848, mx, my);
            } else botao(g, x + 10, ly + 2, 240, Component.translatable("ligacao.lanterna.desligar"), 0xE04848, mx, my);
            ly += 26;
            g.fill(x + 10, ly, x + pw - 10, ly + 1, Hud.argb(cor, 80));
            ly += 6;
        }
        g.drawString(font, Hud.caps(Component.translatable("ligacao.lanterna.online")), x + 10, ly, 0xFF9AA8B0, false);
        ly += 12;
        if (online.isEmpty()) Hud.wrapped(g, Hud.body(Component.translatable("ligacao.lanterna.ninguem")), x + 10, ly, pw - 20, 0xFF8A949C, 10);
        int max = Math.max(1, (y + ph - 8 - ly) / 18);
        for (int i = rolagem; i < Math.min(online.size(), rolagem + max); i++) {
            PlayerInfo pi = online.get(i);
            UUID id = pi.getProfile().getId();
            LigacaoHud.rosto(g, id, x + 12, ly, 12, false, cor);
            Hud.textFit(g, Hud.body(Component.literal(pi.getProfile().getName())), x + 30, ly + 2, pw - 120, 0xFFE0E6EA, false);
            if (!naLigacao(id) && ClientLigacao.modo != 2)
                botao(g, x + pw - 80, ly - 1, 70, Component.translatable(ClientLigacao.modo == 0 ? "ligacao.lanterna.ligar" : "ligacao.lanterna.adicionar"), cor, mx, my);
            ly += 18;
        }
        super.render(g, mx, my, pt);
    }

    private void botao(GuiGraphics g, int x, int y, int w, Component t, int cor, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 14;
        g.fill(x, y, x + w, y + 14, Hud.argb(cor, hov ? 230 : 150));
        Hud.textCenter(g, Hud.bold(t), x + w / 2, y + 3, 0xFFFFFFFF);
    }

    private static boolean dentro(double mx, double my, int x, int y, int w) {
        return mx >= x && mx < x + w && my >= y && my < y + 14;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        int x = x0(), y = y0(), pw = pw(), ly = y + 26;
        if (ClientLigacao.modo != 0) {
            ly += 14 + ClientLigacao.membros.size() * 16;
            if (ClientLigacao.modo == 2) {
                if (dentro(mx, my, x + 10, ly + 2, 115)) { ClientLigacao.acao(Ligacoes.ATENDER, new UUID(0, 0)); return true; }
                if (dentro(mx, my, x + 135, ly + 2, 115)) { ClientLigacao.acao(Ligacoes.RECUSAR, new UUID(0, 0)); return true; }
            } else if (dentro(mx, my, x + 10, ly + 2, 240)) { ClientLigacao.acao(Ligacoes.DESLIGAR, new UUID(0, 0)); return true; }
            ly += 32;
        }
        ly += 12;
        int max = Math.max(1, (y + ph() - 8 - ly) / 18);
        for (int i = rolagem; i < Math.min(online.size(), rolagem + max); i++) {
            UUID id = online.get(i).getProfile().getId();
            if (!naLigacao(id) && ClientLigacao.modo != 2 && dentro(mx, my, x + pw - 80, ly - 1, 70)) {
                ClientLigacao.acao(Ligacoes.LIGAR, id);
                if (minecraft != null && minecraft.player != null) minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.2f);
                return true;
            }
            ly += 18;
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        rolagem = Math.max(0, Math.min(Math.max(0, online.size() - 5), rolagem - (int) Math.signum(d)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
