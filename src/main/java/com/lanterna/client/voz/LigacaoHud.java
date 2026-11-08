package com.lanterna.client.voz;

import com.lanterna.client.ClientRingData;
import com.lanterna.client.hud.Hud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.UUID;

/** Painel da ligação (estilo app de voz): quem está na chamada, quem está falando, tempo, e o aviso de chamada. */
public final class LigacaoHud {
    public static final IGuiOverlay HUD = (gui, g, pt, w, h) -> desenhar(g, w, h);

    static int cor() {
        return ClientRingData.hasRing ? ClientRingData.color : 0x5AE0FF;
    }

    public static ResourceLocation pele(UUID id) {
        var c = Minecraft.getInstance().getConnection();
        PlayerInfo i = c == null ? null : c.getPlayerInfo(id);
        return i != null ? i.getSkinLocation() : net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin(id);
    }

    /** Rosto com o anel verde de "falando" em volta. */
    public static void rosto(GuiGraphics g, UUID id, int x, int y, int tam, boolean falando, int cor) {
        if (falando) {
            float p = 0.6f + 0.4f * (float) Math.sin(System.currentTimeMillis() / 120.0);
            g.fill(x - 2, y - 2, x + tam + 2, y + tam + 2, Hud.argb(0x43E06A, (int) (255 * p)));
        } else g.fill(x - 1, y - 1, x + tam + 1, y + tam + 1, Hud.argb(cor, 120));
        PlayerFaceRenderer.draw(g, pele(id), x, y, tam);
    }

    private static void desenhar(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || ClientLigacao.modo == 0) return;
        int cor = cor();
        var font = mc.font;
        if (ClientLigacao.modo == 2 && !ClientLigacao.membros.isEmpty()) {
            // chamada chegando: cartão no topo, no meio
            var quem = ClientLigacao.membros.get(0);
            int pw = 230, ph = 44, x = (w - pw) / 2, y = h / 2 + 22;
            float pulso = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 200.0);
            Hud.panel(g, x, y, pw, ph, Hud.mix(cor, 0xFFFFFF, pulso * 0.3f), 0.95f);
            rosto(g, quem.id(), x + 8, y + 8, 28, true, cor);
            Hud.textFit(g, Hud.bold(Component.literal(quem.nome())), x + 44, y + 8, pw - 50, 0xFFFFFFFF, false);
            Hud.textFit(g, Hud.body(Component.translatable("ligacao.lanterna.chamando_voce")), x + 44, y + 19, pw - 50, 0xFFB8C4CC, false);
            String tecla = com.lanterna.client.KeyBindings.COMUNICADOR.getTranslatedKeyMessage().getString();
            Hud.textFit(g, Hud.body(Component.translatable("ligacao.lanterna.atender_recusar", tecla, tecla)), x + 44, y + 30, pw - 50, Hud.argb(cor, 255), false);
            return;
        }
        // painel da ligação, à esquerda, no meio da tela
        var lista = new java.util.ArrayList<ClientLigacao.Pessoa>(ClientLigacao.membros);
        lista.addAll(ClientLigacao.tocando);
        int pw = 150, linha = 20, ph = 22 + lista.size() * linha + 12;
        int x = 6, y = Math.max(110, h - 34 - ph);
        Hud.panel(g, x, y, pw, ph, cor, 0.9f);
        boolean em = ClientLigacao.modo == 3;
        int s = ClientLigacao.segundosAgora();
        String cab = em ? String.format("%02d:%02d", s / 60, s % 60) : "...";
        g.fill(x + 7, y + 8, x + 11, y + 12, em ? 0xFF43E06A : Hud.argb(cor, 200));
        Hud.textFit(g, Hud.bold(Component.translatable(em ? "ligacao.lanterna.em_ligacao" : "ligacao.lanterna.chamando")), x + 15, y + 6, pw - 22 - font.width(cab) - 4, 0xFFFFFFFF, false);
        Hud.textRight(g, Hud.body(Component.literal(cab)), x + pw - 7, y + 6, 0xFFB8C4CC);
        int ly = y + 22;
        for (var p : lista) {
            boolean tocando = ClientLigacao.tocando.contains(p);
            rosto(g, p.id(), x + 8, ly, 14, p.falando(), cor);
            int c = tocando ? 0xFF8A949C : p.falando() ? 0xFF7CF5A0 : 0xFFE0E6EA;
            Hud.textFit(g, Hud.body(Component.literal(p.nome())), x + 28, ly + 3, pw - 50, c, false);
            if (tocando) Hud.textRight(g, Hud.body(Component.literal("...")), x + pw - 7, ly + 3, 0xFF8A949C);
            else if (p.falando()) {
                for (int i = 0; i < 3; i++) {
                    int bh = 2 + (int) (Math.abs(Math.sin(System.currentTimeMillis() / 90.0 + i)) * 7);
                    g.fill(x + pw - 18 + i * 4, ly + 11 - bh, x + pw - 16 + i * 4, ly + 11, 0xFF43E06A);
                }
            }
            ly += linha;
        }
        Hud.textFit(g, Hud.body(Component.translatable(ClientLigacao.voz ? "ligacao.lanterna.voz_svc" : "ligacao.lanterna.sem_voz")), x + 8, y + ph - 11, pw - 14,
                ClientLigacao.voz ? 0xFF6A747C : 0xFFB08A60, false);
    }

    private LigacaoHud() {}
}
