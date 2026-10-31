package com.lanterna.client.visual;

import com.lanterna.client.ClientRingData;
import com.lanterna.client.hud.Hud;
import com.lanterna.network.ModNetwork;
import com.lanterna.visual.Visual;
import com.lanterna.visual.VisualPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Perfil do Lanterna (tecla H, ou as abas da tela K):
 *   ESTATÍSTICAS  ficha do personagem (nível, posto, vida, armadura, carga, afinidade, força, voo, jornada...)
 *   VISUAL        o uniforme que o anel veste (padrão da Tropa, os dois trajes "segunda pele" ou nenhum)
 *                 e a altura da máscara no rosto, com o rosto ampliado para acertar nos olhos da skin.
 * Tudo vem do servidor (a ficha) e o visual escolhido é salvo no servidor e visto pelos outros jogadores.
 */
public class PerfilScreen extends Screen {
    private static final int W = 372, H = 232;
    private static VisualPackets.Ficha ficha;
    private static PerfilScreen aberta;

    public enum Aba { ESTATISTICAS, VISUAL }
    private Aba aba;
    private int rolagem;
    private int alturaConteudo;

    public PerfilScreen(Aba aba) {
        super(Component.translatable("perfil.lanterna.titulo"));
        this.aba = aba;
    }

    public static void abrir(Aba aba) {
        Minecraft.getInstance().setScreen(new PerfilScreen(aba));
    }

    public static void receberFicha(VisualPackets.Ficha f) {
        ficha = f;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2 + 8; }
    private int cor() { return ClientRingData.hasRing ? ClientRingData.color : 0x8FA3B0; }

    @Override
    protected void init() {
        aberta = this;
        ModNetwork.sendToServer(new VisualPackets.FichaPedido());
    }

    private int pedidoTick;

    @Override
    public void tick() {
        // a carga, a vida etc. mudam: atualiza a ficha de vez em quando
        if (++pedidoTick % 20 == 0) ModNetwork.sendToServer(new VisualPackets.FichaPedido());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ===================== Desenho =====================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int l = left(), t = top(), c = cor();
        abas(g, l, t, mx, my);
        Hud.panel(g, l, t, W, H, c, 0.97f);
        // brilho suave da cor da Tropa atrás do personagem
        g.fillGradient(l + 4, t + 4, l + 118, t + H - 4, Hud.argb(c, 36), Hud.argb(c, 4));
        g.fill(l + 118, t + 8, l + 119, t + H - 8, 0x30FFFFFF);

        // Personagem (segue o mouse)
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, l + 60, t + 160, 58, l + 60 - mx, t + 60 - my, mc.player);
            Component nome = Hud.bold(mc.player.getName());
            Hud.textCenter(g, nome, l + 60, t + 172, 0xFFFFFFFF);
            Component tropa = ClientRingData.hasRing ? Hud.body(Component.translatable(com.lanterna.corps.CorpsDefinition.nameKey(ClientRingData.corps)))
                    : Hud.body(Component.translatable("perfil.lanterna.sem_anel"));
            Hud.textCenter(g, tropa, l + 60, t + 183, Hud.argb(c, 255));
            if (ClientRingData.hasRing) {
                Hud.bar(g, l + 18, t + 200, 84, 4, ClientRingData.charge / (float) Math.max(1, ClientRingData.maxCharge), c, true);
                Hud.textCenter(g, Hud.body(Component.translatable("perfil.lanterna.carga_curta", ClientRingData.charge * 100 / Math.max(1, ClientRingData.maxCharge))),
                        l + 60, t + 208, 0xFFB8C4CC);
            }
        }

        int cx = l + 128, cy = t + 10, cw = W - 138;
        g.enableScissor(cx - 2, t + 6, cx + cw + 2, t + H - 6);
        if (aba == Aba.ESTATISTICAS) estatisticas(g, cx, cy - rolagem, cw);
        else visual(g, cx, cy, cw, mx, my);
        g.disableScissor();
        if (aba == Aba.ESTATISTICAS && alturaConteudo > H - 16) {
            // barra de rolagem
            int trilho = H - 20, alt = Math.max(16, trilho * (H - 16) / alturaConteudo);
            int y = t + 10 + (int) ((trilho - alt) * (rolagem / (float) Math.max(1, alturaConteudo - (H - 16))));
            g.fill(l + W - 5, y, l + W - 3, y + alt, Hud.argb(c, 140));
        }
        super.render(g, mx, my, pt);
    }

    private static final String[] ABAS = {"perfil.lanterna.aba.lanterna", "perfil.lanterna.aba.estatisticas", "perfil.lanterna.aba.visual"};

    private void abas(GuiGraphics g, int l, int t, int mx, int my) {
        int x = l + 6;
        for (int i = 0; i < ABAS.length; i++) {
            Component nome = Hud.caps(Component.translatable(ABAS[i]));
            int w = font.width(nome) + 16;
            boolean sel = (i == 1 && aba == Aba.ESTATISTICAS) || (i == 2 && aba == Aba.VISUAL);
            boolean hov = mx >= x && mx < x + w && my >= t - 16 && my < t;
            g.fill(x, t - (sel ? 17 : 15), x + w, t, sel ? Hud.argb(0x0A0E12, 235) : hov ? 0xC0182028 : 0xA0101418);
            if (sel) g.fill(x, t - 17, x + w, t - 16, Hud.argb(cor(), 255));
            g.drawString(font, nome, x + 8, t - (sel ? 12 : 11), sel ? Hud.argb(cor(), 255) : 0xFF9AA8B4, false);
            x += w + 3;
        }
    }

    private int abaEm(double mx, double my) {
        int l = left(), t = top(), x = l + 6;
        for (int i = 0; i < ABAS.length; i++) {
            int w = font.width(Hud.caps(Component.translatable(ABAS[i]))) + 16;
            if (mx >= x && mx < x + w && my >= t - 17 && my < t) return i;
            x += w + 3;
        }
        return -1;
    }

    private void estatisticas(GuiGraphics g, int x, int y, int w) {
        int y0 = y;
        if (ficha == null) {
            g.drawString(font, Hud.body(Component.translatable("perfil.lanterna.carregando")), x, y, 0xFF9AA8B4, false);
            return;
        }
        String secao = "";
        for (VisualPackets.Linha ln : ficha.linhas()) {
            if (!ln.secao().equals(secao)) {
                secao = ln.secao();
                if (y != y0) y += 5;
                Component h = Hud.caps(Component.translatable("perfil.lanterna.secao." + secao));
                g.drawString(font, h, x, y, Hud.argb(cor(), 255), false);
                g.fill(x + font.width(h) + 6, y + 4, x + w, y + 5, Hud.argb(cor(), 60));
                y += 12;
            }
            g.drawString(font, Hud.body(ln.rotulo()), x + 2, y, 0xFF9AA8B4, false);
            Component v = Hud.bold(ln.valor());
            int vw = font.width(v);
            int vx = x + w - vw;
            g.drawString(font, v, vx, y, ln.barra() >= 0 ? 0xFFFFFFFF : Hud.argb(Hud.mix(ln.cor(), 0xFFFFFF, 0.55f), 255), false);
            y += 10;
            if (ln.barra() >= 0) {
                Hud.bar(g, x + 2, y, w - 4, 3, ln.barra(), ln.cor(), false);
                y += 7;
            }
        }
        alturaConteudo = y - y0 + 8;
    }

    // ---- Visual ----

    private String tropa() {
        return ClientRingData.hasRing ? com.lanterna.armor.ModArmorItems.tropaOf(ClientRingData.corps) : null;
    }

    private ClientVisual.Look meu() {
        var p = Minecraft.getInstance().player;
        return p == null ? new ClientVisual.Look("padrao", 0) : ClientVisual.of(p.getId());
    }

    private Component nomeTraje(String t) {
        String tr = tropa();
        if (t.equals("a") || t.equals("b")) return tr == null ? Component.literal(t.toUpperCase()) : Component.translatable("traje.lanterna." + tr + "_" + t);
        return Component.translatable("perfil.lanterna.traje." + t);
    }

    private void visual(GuiGraphics g, int x, int y, int w, int mx, int my) {
        int c = cor();
        g.drawString(font, Hud.caps(Component.translatable("perfil.lanterna.uniforme")), x, y, Hud.argb(c, 255), false);
        y += 12;
        if (tropa() == null) {
            Hud.wrapped(g, Hud.body(Component.translatable("perfil.lanterna.visual_sem_anel")), x, y, w, 0xFF9AA8B4, 10);
            return;
        }
        // 4 cartões: padrão, traje A, traje B, nenhum
        ClientVisual.Look look = meu();
        int cw = (w - 6) / 2, ch = 22;
        for (int i = 0; i < Visual.TRAJES.size(); i++) {
            String t = Visual.TRAJES.get(i);
            int bx = x + (i % 2) * (cw + 6), by = y + (i / 2) * (ch + 4);
            boolean sel = look.traje().equals(t), hov = mx >= bx && mx < bx + cw && my >= by && my < by + ch;
            g.fill(bx, by, bx + cw, by + ch, sel ? Hud.argb(c, 70) : hov ? 0x40FFFFFF : 0x50000000);
            g.fill(bx, by, bx + 2, by + ch, sel ? Hud.argb(c, 255) : 0x40FFFFFF);
            g.drawString(font, Hud.bold(nomeTraje(t)), bx + 7, by + 3, sel ? 0xFFFFFFFF : 0xFFD8E0E4, false);
            g.drawString(font, Hud.body(Component.translatable("perfil.lanterna.traje_desc." + (t.length() == 1 ? "segunda_pele" : t))), bx + 7, by + 12, 0xFF8A98A4, false);
        }
        y += 2 * (ch + 4) + 8;

        // Máscara
        g.drawString(font, Hud.caps(Component.translatable("perfil.lanterna.mascara")), x, y, Hud.argb(c, 255), false);
        y += 12;
        int boxX = x, boxY = y, box = 92;
        // rosto ampliado
        g.fill(boxX, boxY, boxX + box, boxY + box, 0x70000000);
        g.fill(boxX, boxY, boxX + box, boxY + 1, Hud.argb(c, 160));
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            g.enableScissor(boxX, boxY, boxX + box, boxY + box);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, boxX + box / 2, boxY + 395, 230, 0f, 0f, mc.player);
            g.disableScissor();
        }
        int rx = boxX + box + 10;
        float px = look.mascara() / 4f;
        Component val = Hud.title(String.format(java.util.Locale.ROOT, "%+.2f px", px).replace('.', ','));
        g.drawString(font, val, rx, boxY + 4, 0xFFFFFFFF, false);
        Hud.wrapped(g, Hud.body(Component.translatable("perfil.lanterna.mascara_dica")), rx, boxY + 18, w - box - 10, 0xFF8A98A4, 10);
        botoes(g, rx, boxY + 62, mx, my);
    }

    private static final String[] BOTOES = {"▲", "▼", "perfil.lanterna.zerar"};

    private void botoes(GuiGraphics g, int x, int y, int mx, int my) {
        for (int i = 0; i < 3; i++) {
            int bx = x + i * 30, bw = i == 2 ? 60 : 26;
            if (i == 2) bx = x + 60;
            boolean hov = mx >= bx && mx < bx + bw && my >= y && my < y + 18;
            g.fill(bx, y, bx + bw, y + 18, hov ? Hud.argb(cor(), 90) : 0x60000000);
            g.fill(bx, y + 17, bx + bw, y + 18, Hud.argb(cor(), 200));
            Component tx = i == 2 ? Hud.body(Component.translatable(BOTOES[2])) : Component.literal(BOTOES[i]);
            Hud.textCenter(g, tx, bx + bw / 2, y + 5, 0xFFFFFFFF);
        }
    }

    // ===================== Cliques =====================

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        int a = abaEm(mx, my);
        if (a >= 0) {
            clique();
            if (a == 0) {
                ModNetwork.sendToServer(new com.lanterna.network.OpenLanternMenuPacket());
            } else {
                aba = a == 1 ? Aba.ESTATISTICAS : Aba.VISUAL;
                rolagem = 0;
            }
            return true;
        }
        if (aba == Aba.VISUAL && tropa() != null) {
            int x = left() + 128, y = top() + 22, w = W - 138;
            int cw = (w - 6) / 2, ch = 22;
            ClientVisual.Look look = meu();
            for (int i = 0; i < Visual.TRAJES.size(); i++) {
                int bx = x + (i % 2) * (cw + 6), by = y + (i / 2) * (ch + 4);
                if (mx >= bx && mx < bx + cw && my >= by && my < by + ch) {
                    escolher(Visual.TRAJES.get(i), look.mascara());
                    return true;
                }
            }
            int by = y + 2 * (ch + 4) + 8 + 12 + 62, rx = x + 92 + 10;
            if (my >= by && my < by + 18) {
                if (mx >= rx && mx < rx + 26) { escolher(look.traje(), Math.min(Visual.MASCARA_MAX, look.mascara() + 1)); return true; }
                if (mx >= rx + 30 && mx < rx + 56) { escolher(look.traje(), Math.max(-Visual.MASCARA_MAX, look.mascara() - 1)); return true; }
                if (mx >= rx + 60 && mx < rx + 120) { escolher(look.traje(), 0); return true; }
            }
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        if (aba == Aba.ESTATISTICAS) rolagem = Mth.clamp(rolagem - (int) (d * 12), 0, Math.max(0, alturaConteudo - (H - 16)));
        return true;
    }

    /** Aplica na hora (para ver no boneco) e salva no servidor. */
    public void escolher(String traje, int mascara) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        ClientVisual.set(p.getId(), traje, mascara);
        ModNetwork.sendToServer(new VisualPackets.Pedido(traje, mascara));
        clique();
    }

    private void clique() {
        var p = Minecraft.getInstance().player;
        if (p != null) p.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.3f);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (com.lanterna.client.KeyBindings.PERFIL.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
