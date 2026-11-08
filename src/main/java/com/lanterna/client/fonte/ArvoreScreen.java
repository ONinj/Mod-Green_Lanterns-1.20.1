package com.lanterna.client.fonte;

import com.lanterna.LanternaMod;
import com.lanterna.client.ClientRingData;
import com.lanterna.client.construct.ConstructWheelScreen;
import com.lanterna.client.hud.Hud;
import com.lanterna.construct.ConstructType;
import com.lanterna.construct.fonte.ArvoreDados;
import com.lanterna.construct.fonte.ArvoreDaLuz;
import com.lanterna.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * A Árvore da Luz da Tropa do anel em uso: três ramos (Ataque, Proteção, Domínio) que crescem de baixo para cima,
 * cada nó um construto novo ou uma passiva. Clique num nó liberado para gastar Essência da Fonte.
 */
public class ArvoreScreen extends Screen {
    private static final int W = 340;
    private int H = 300, passo = 33;
    private ArvoreDados.No hovered;
    private float abre;

    public ArvoreScreen() {
        super(Component.translatable("arvore.lanterna.titulo"));
    }

    private List<ArvoreDados.No> arvore() {
        return ArvoreDados.ARVORES.get(ClientRingData.corps.getPath());
    }

    private static String tropaNome() {
        for (var t : com.lanterna.baterias.Tropa.values()) if (t.corps().id().equals(ClientRingData.corps)) return t.nome;
        return "";
    }

    private int nx(ArvoreDados.No n, int left) { return left + 62 + "APD".indexOf(n.ramo()) * 108; }
    private int ny(ArvoreDados.No n, int top) { return top + H - 24 - (n.tier() - 1) * passo; }

    private boolean pode(ArvoreDados.No n, List<ArvoreDados.No> arv) {
        if (ClientFonte.tem(n.id())) return false;
        for (ArvoreDados.No x : arv) if (x.ramo() == n.ramo() && x.tier() < n.tier() && !ClientFonte.tem(x.id())) return false;
        return true;
    }

    @Override
    public void tick() { abre = Math.min(1f, abre + 0.2f); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        // cabe em telas baixas: os degraus encolhem
        passo = Mth.clamp((height - 84) / 7, 26, 33);
        H = 60 + passo * 7 - 6;
        int left = (width - W) / 2, top = (height - H) / 2;
        int cor = ClientRingData.color;
        Hud.panel(g, left, top, W, H, cor, 0.96f);
        g.drawString(font, Hud.title(Component.translatable("arvore.lanterna.titulo")), left + 10, top + 8, Hud.argb(cor, 255), false);
        List<ArvoreDados.No> arv = arvore();
        boolean selo = ClientFonte.selos.contains(tropaNome());
        Component ess = Hud.bold(Component.translatable("arvore.lanterna.essencia", ClientFonte.essencia));
        Hud.textRight(g, ess, left + W - 10, top + 9, 0xFFFFE6A0);
        if (arv == null) {
            Hud.wrapped(g, Hud.body(Component.translatable("arvore.lanterna.sem_arvore")), left + 10, top + 40, W - 20, 0xFFE0E6EA, 10);
            return;
        }
        if (!selo) Hud.textFit(g, Hud.body(Component.translatable("arvore.lanterna.sem_selo")), left + 10, top + 22, W - 20, 0xFFFF8080, false);
        else Hud.textFit(g, Hud.body(Component.translatable("arvore.lanterna.ajuda")), left + 10, top + 22, W - 20, 0xFF9AA8B0, false);
        String[] ramos = {"A", "P", "D"};
        for (int i = 0; i < 3; i++) {
            Component nome = Hud.caps(Component.translatable("arvore.lanterna.ramo." + ramos[i]));
            Hud.textCenter(g, nome, left + 62 + i * 108, top + 36, 0xFFD8DEE4);
        }
        // linhas do ramo
        for (ArvoreDados.No n : arv) {
            for (ArvoreDados.No m : arv) if (m.ramo() == n.ramo() && m.tier() > n.tier()) {
                boolean proximo = arv.stream().noneMatch(x -> x.ramo() == n.ramo() && x.tier() > n.tier() && x.tier() < m.tier());
                if (!proximo) continue;
                int x = nx(n, left), y0 = ny(m, top) + 12, y1 = ny(n, top) - 12;
                boolean ok = ClientFonte.tem(n.id());
                g.fill(x - 1, y0, x + 1, y1, ok ? Hud.argb(cor, 220) : 0x60808890);
            }
        }
        hovered = null;
        long t = System.currentTimeMillis();
        for (ArvoreDados.No n : arv) {
            int x = nx(n, left), y = ny(n, top);
            boolean tem = ClientFonte.tem(n.id());
            boolean livre = selo && pode(n, arv);
            boolean hov = Math.abs(mx - x) <= 13 && Math.abs(my - y) <= 13;
            if (hov) hovered = n;
            int borda = tem ? cor : livre ? Hud.mix(cor, 0xFFFFFF, 0.3f + 0.3f * Mth.sin(t / 220f)) : 0x404448;
            g.fill(x - 14, y - 14, x + 14, y + 14, Hud.argb(borda, hov ? 255 : tem ? 220 : livre ? 200 : 140));
            g.fill(x - 12, y - 12, x + 12, y + 12, Hud.argb(tem ? 0x10241A : 0x0E1216, 245));
            if (n.tipo() == 'c') {
                ConstructType ct = ConstructType.byId(n.id());
                if (ct != null) ConstructWheelScreen.desenharIcone(g, ct, x - 11, y - 11, 22, !tem && !livre, tem ? cor : 0xB0B8C0);
            } else {
                float r = ((cor >> 16) & 255) / 255f, gg = ((cor >> 8) & 255) / 255f, b = (cor & 255) / 255f;
                if (tem) g.setColor(0.35f + 0.65f * r, 0.35f + 0.65f * gg, 0.35f + 0.65f * b, 1f);
                else g.setColor(livre ? 0.75f : 0.3f, livre ? 0.78f : 0.3f, livre ? 0.8f : 0.3f, 1f);
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                g.blit(iconePassiva(n.id()), x - 11, y - 11, 22, 22, 0, 0, 64, 64, 64, 64);
                g.setColor(1f, 1f, 1f, 1f);
            }
            if (!tem) g.drawString(font, Component.literal(String.valueOf(n.custo())), x + 14 - font.width(String.valueOf(n.custo())) - 1, y + 5, livre ? 0xFFFFE6A0 : 0xFF707880, true);
        }
        // detalhe do nó embaixo
        if (hovered != null) {
            // ficha do nó ao lado do mouse (sem sair da tela; a altura acompanha o texto)
            int pw = Math.min(220, width - 8);
            Component nome = hovered.tipo() == 'c' ? Component.translatable("construct.lanterna." + hovered.id()) : Component.translatable("arvore.lanterna.passiva." + hovered.id());
            Component desc = hovered.tipo() == 'c' ? Component.translatable("construct.lanterna." + hovered.id() + ".desc") : Component.translatable("arvore.lanterna.passiva." + hovered.id() + ".desc");
            Component estado = Hud.body(ClientFonte.tem(hovered.id()) ? Component.translatable("arvore.lanterna.tem")
                    : Component.translatable("arvore.lanterna.custa", hovered.custo()));
            Component custo = null;
            if (hovered.tipo() == 'c') {
                ConstructType ct = ConstructType.byId(hovered.id());
                if (ct != null) custo = Hud.body(Component.translatable("construct.lanterna.cost", ct.cost, String.format(java.util.Locale.ROOT, "%.1f", ct.cooldown / 20f)));
            }
            int linhas = Hud.lines(Hud.body(desc), pw - 16);
            int ph = 18 + linhas * 9 + (custo != null ? 11 : 0) + 4;
            int bx = mx + 14 + pw <= width - 4 ? mx + 14 : mx - pw - 14;
            bx = Mth.clamp(bx, 4, Math.max(4, width - pw - 4));
            int by = Mth.clamp(my + 6, 4, Math.max(4, height - ph - 4));
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            Hud.panel(g, bx, by, pw, ph, cor, 0.98f);
            int ew = font.width(estado);
            Hud.textFit(g, Hud.bold(nome), bx + 8, by + 5, pw - 24 - ew, Hud.argb(cor, 255), false);
            Hud.textRight(g, estado, bx + pw - 8, by + 5, 0xFFFFE6A0);
            int hy = by + 16 + Hud.wrapped(g, Hud.body(desc), bx + 8, by + 16, pw - 16, 0xFFE0E6EA, 9);
            if (custo != null) Hud.textFit(g, custo, bx + 8, hy + 2, pw - 16, 0xFF9AA8B0, false);
            int left0 = left;
            g.pose().popPose();
            left = left0;
        }
        super.render(g, mx, my, pt);
    }

    static ResourceLocation iconePassiva(String id) {
        String f = id.replaceAll("[0-9]", "");
        return LanternaMod.id("textures/gui/construct/p_" + f + ".png");
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        List<ArvoreDados.No> arv = arvore();
        if (hovered != null && arv != null && pode(hovered, arv) && ClientFonte.essencia >= hovered.custo()) {
            ModNetwork.sendToServer(new ArvoreDaLuz.Comprar(hovered.id()));
            if (minecraft != null && minecraft.player != null) minecraft.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
