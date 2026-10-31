package com.lanterna.client.portal;

import com.lanterna.client.hud.Hud;
import com.lanterna.network.ModNetwork;
import com.lanterna.portal.PortalPackets;
import com.lanterna.space.SpaceBodies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tela da Porta Estelar: os planetas que você já visitou (e a Terra), com busca. Escolha um e aperte
 * "Abrir a porta". Mostra o custo em carga e quanto falta para a porta poder ser aberta de novo.
 */
public class PortalScreen extends Screen {
    private static final int W = 300, ROW = 18, LINHAS = 9;
    private final PortalPackets.Lista dados;
    private final List<String> filtrados = new ArrayList<>();
    private String escolhido;
    private int rolagem;
    private EditBox busca;
    private Button abrir;
    private final long aberto = System.currentTimeMillis();

    public PortalScreen(PortalPackets.Lista dados) {
        super(Component.translatable("portal.lanterna.titulo"));
        this.dados = dados;
        filtrados.addAll(dados.destinos());
    }

    public static void abrir(PortalPackets.Lista m) {
        Minecraft.getInstance().setScreen(new PortalScreen(m));
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - (64 + LINHAS * ROW + 34)) / 2; }

    private static Component nome(String id) {
        SpaceBodies.Body b = SpaceBodies.byId(id);
        return b == null ? Component.literal(id) : b.displayName();
    }

    private static int corDe(String id) {
        SpaceBodies.Body b = SpaceBodies.byId(id);
        return b == null ? 0xFFFFFF : b.atmosphere();
    }

    @Override
    protected void init() {
        int l = left(), t = top();
        busca = new EditBox(font, l + 10, t + 26, W - 20, 14, Component.translatable("starmap.lanterna.search"));
        busca.setHint(Component.translatable("starmap.lanterna.search").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        busca.setResponder(q -> {
            filtrados.clear();
            String s = q.toLowerCase(Locale.ROOT).trim();
            for (String id : dados.destinos()) if (s.isEmpty() || nome(id).getString().toLowerCase(Locale.ROOT).contains(s)) filtrados.add(id);
            rolagem = 0;
        });
        addRenderableWidget(busca);
        int by = t + 46 + LINHAS * ROW + 6;
        abrir = Button.builder(Component.translatable("portal.lanterna.abrir"), b -> {
            if (escolhido == null) return;
            ModNetwork.sendToServer(new PortalPackets.Pedido(escolhido));
            onClose();
        }).bounds(l + W / 2 - 100, by, 120, 20).build();
        addRenderableWidget(abrir);
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(l + W / 2 + 26, by, 74, 20).build());
        atualizar();
    }

    private long espera() {
        return Math.max(0, dados.esperaMs() - (System.currentTimeMillis() - aberto));
    }

    private void atualizar() {
        if (abrir != null) abrir.active = escolhido != null && espera() == 0 && dados.carga() >= dados.custo();
    }

    @Override
    public void tick() {
        atualizar();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int l = left(), t = top();
        int h = 64 + LINHAS * ROW + 34;
        int cor = dados.cor();
        Hud.panel(g, l, t, W, h, cor, 0.95f);
        g.drawString(font, Hud.title(getTitle()), l + 10, t + 8, Hud.argb(cor, 255), false);
        Component info = Hud.body(Component.translatable("portal.lanterna.custo", dados.custo(), dados.carga()));
        Hud.textRight(g, info, l + W - 10, t + 9, dados.carga() >= dados.custo() ? 0xFFB8C4CC : 0xFFE07060);
        int y0 = t + 46;
        for (int i = 0; i < LINHAS; i++) {
            int idx = i + rolagem;
            if (idx >= filtrados.size()) break;
            String id = filtrados.get(idx);
            int y = y0 + i * ROW;
            boolean hov = mx >= l + 8 && mx < l + W - 8 && my >= y && my < y + ROW - 2;
            boolean sel = id.equals(escolhido);
            g.fill(l + 8, y, l + W - 8, y + ROW - 2, sel ? Hud.argb(cor, 90) : hov ? 0x40FFFFFF : 0x30000000);
            int c = 0xFF000000 | corDe(id);
            g.fill(l + 14, y + 4, l + 22, y + 12, c);
            g.drawString(font, Hud.bold(nome(id)), l + 28, y + 4, sel ? 0xFFFFFFFF : 0xFFD8E0E4, false);
            SpaceBodies.Body b = SpaceBodies.byId(id);
            Component tipo = Component.translatable(b != null && b.procedural() != null ? "portal.lanterna.tipo.gerado"
                    : id.equals("earth") ? "portal.lanterna.tipo.casa" : "portal.lanterna.tipo.tropa");
            Hud.textRight(g, Hud.body(tipo), l + W - 14, y + 4, 0xFF8A98A4);
        }
        if (filtrados.isEmpty()) g.drawString(font, Hud.body(Component.translatable("portal.lanterna.nenhum")), l + 14, y0 + 4, 0xFF8A98A4, false);
        long e = espera();
        Component pe = e > 0 ? Component.translatable("portal.lanterna.espera", com.lanterna.guilda.Invasao.tempo(e))
                : escolhido == null ? Component.translatable("portal.lanterna.escolha") : Component.translatable("portal.lanterna.pronto", nome(escolhido));
        Hud.textCenter(g, Hud.body(pe), l + W / 2, y0 + LINHAS * ROW - 8 + 2, e > 0 ? 0xFFFFC040 : Hud.argb(cor, 255));
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        int l = left(), y0 = top() + 46;
        for (int i = 0; i < LINHAS; i++) {
            int idx = i + rolagem;
            if (idx >= filtrados.size()) break;
            int y = y0 + i * ROW;
            if (mx >= l + 8 && mx < l + W - 8 && my >= y && my < y + ROW - 2) {
                escolhido = filtrados.get(idx);
                if (minecraft != null && minecraft.player != null) minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.4f);
                atualizar();
                return true;
            }
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        rolagem = Mth.clamp(rolagem - (int) Math.signum(d), 0, Math.max(0, filtrados.size() - LINHAS));
        return true;
    }

    /** Só para o teste automático. */
    public void escolher(String id) {
        escolhido = id;
        atualizar();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
