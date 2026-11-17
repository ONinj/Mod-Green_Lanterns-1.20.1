package com.lanterna.client.vida;

import com.lanterna.client.hud.Hud;
import com.lanterna.lore.LoreItems;
import com.lanterna.network.ModNetwork;
import com.lanterna.vida.Emocoes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 0.53: a Árvore das Emoções do Anel da Vida. Uma linha por aspecto emocional: o nível (0 a 6), o que falta para o
 * próximo (a Insígnia do lendário ou Fragmentos Lendários), quantos construtos daquela Tropa o anel já tem, e os botões
 * de fortalecer e de treinar (o traje e a luz do anel passam a ser daquela Tropa). Embaixo, o preparo para Dois Anéis.
 */
public class EmocoesScreen extends Screen {
    private static final int W = 330, LINHA = 22;
    private final Screen pai;
    private int x0, y0, h;

    public EmocoesScreen(@Nullable Screen pai) {
        super(Component.translatable("emocoes.lanterna.titulo"));
        this.pai = pai;
    }

    @Override
    protected void init() {
        h = 40 + Emocoes.ASPECTOS.length * LINHA + 38;
        x0 = (width - W) / 2;
        y0 = Math.max(4, (height - h) / 2);
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        boolean vida = ClientEmocoes.comAnelDaVida();
        for (int i = 0; i < Emocoes.ASPECTOS.length; i++) {
            String a = Emocoes.ASPECTOS[i];
            int y = y0 + 34 + i * LINHA;
            int n = ClientEmocoes.nivel(a);
            if (n < Emocoes.MAX) {
                Component rot = n == 0 ? Component.translatable("emocoes.lanterna.despertar") : Component.translatable("emocoes.lanterna.fortalecer");
                Button b = addRenderableWidget(Button.builder(rot, x -> { ModNetwork.sendToServer(new Emocoes.Acao("subir", a)); }).bounds(x0 + W - 128, y + 2, 64, 16).build());
                b.active = vida;
            }
            boolean treinando = a.equals(ClientEmocoes.treino());
            Button t = addRenderableWidget(Button.builder(Component.translatable(treinando ? "emocoes.lanterna.treinando" : "emocoes.lanterna.treinar"),
                    x -> ModNetwork.sendToServer(new Emocoes.Acao("treinar", treinando ? "" : a))).bounds(x0 + W - 62, y + 2, 56, 16).build());
            t.active = vida && n >= 1;
        }
        if (!ClientEmocoes.doisAneis()) {
            Button d = addRenderableWidget(Button.builder(Component.translatable("emocoes.lanterna.dois_botao", Emocoes.CUSTO_DOIS),
                    x -> ModNetwork.sendToServer(new Emocoes.Acao("dois", ""))).bounds(x0 + W - 128, y0 + h - 30, 122, 16).build());
            d.active = vida;
        }
    }

    private int tick;

    @Override
    public void tick() {
        // o servidor manda o estado novo depois de cada ação: refaz os botões
        if (++tick % 10 == 0) rebuild();
    }

    private static int contar(Item item) {
        var p = net.minecraft.client.Minecraft.getInstance().player;
        if (p == null) return 0;
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int branco = 0xF2F2F2;
        Hud.panel(g, x0, y0, W, h, branco, 0.96f);
        g.drawString(font, Hud.caps(title), x0 + 10, y0 + 8, 0xFFFFFFFF, false);
        int frag = contar(LoreItems.FRAGMENTO_LENDARIO.get());
        Component fr = Component.translatable("emocoes.lanterna.fragmentos", frag);
        g.drawString(font, Hud.body(fr), x0 + W - 10 - font.width(fr), y0 + 8, 0xFFE0C060, false);
        Component sub = ClientEmocoes.comAnelDaVida() ? Component.translatable("emocoes.lanterna.sub") : Component.translatable("emocoes.lanterna.so_vida");
        Hud.textFit(g, Hud.body(sub), x0 + 10, y0 + 20, W - 20, 0xFF9AA8B4, false);

        for (int i = 0; i < Emocoes.ASPECTOS.length; i++) {
            String a = Emocoes.ASPECTOS[i];
            int y = y0 + 34 + i * LINHA, cor = Emocoes.cor(a), n = ClientEmocoes.nivel(a);
            boolean treinando = a.equals(ClientEmocoes.treino());
            g.fill(x0 + 6, y, x0 + W - 6, y + LINHA - 3, treinando ? Hud.argb(cor, 50) : 0x40000000);
            g.fill(x0 + 6, y, x0 + 9, y + LINHA - 3, Hud.argb(cor, 255));
            g.drawString(font, Hud.bold(Component.translatable("emocoes.lanterna.aspecto." + a)), x0 + 14, y + 3, Hud.argb(cor, 255), false);
            // os 6 níveis
            for (int k = 0; k < Emocoes.MAX; k++) {
                int bx = x0 + 14 + k * 9;
                g.fill(bx, y + 13, bx + 7, y + 18, k < n ? Hud.argb(cor, 255) : 0x60FFFFFF);
            }
            int liberados = Emocoes.limite(a, n), total = Emocoes.daTropa(a).size();
            Component info;
            if (n == 0) {
                var l = Emocoes.lendario(a);
                info = Component.translatable("emocoes.lanterna.precisa_insignia", l == null ? "?" : Component.translatable("lendario.lanterna." + l.id));
            } else if (n < Emocoes.MAX) {
                info = Component.translatable("emocoes.lanterna.proximo", Emocoes.CUSTO[n + 1], liberados, total);
            } else {
                info = Component.translatable("emocoes.lanterna.maximo", total);
            }
            Hud.textFit(g, Hud.body(info), x0 + 72, y + 8, W - 72 - 134, 0xFFC8D0D8, false);
        }

        int yd = y0 + h - 34;
        g.fill(x0 + 6, yd - 2, x0 + W - 6, yd - 1, 0x40FFFFFF);
        Component dois = ClientEmocoes.doisAneis() ? Component.translatable("emocoes.lanterna.dois_ok")
                : Component.translatable("emocoes.lanterna.dois_info");
        Hud.textFit(g, Hud.body(dois), x0 + 10, yd + 4, ClientEmocoes.doisAneis() ? W - 20 : W - 145, ClientEmocoes.doisAneis() ? 0xFFFFFFFF : 0xFFB0BCC6, false);
        if (ClientEmocoes.dominada())
            Hud.textFit(g, Hud.body(Component.translatable("emocoes.lanterna.dominada_titulo")), x0 + 10, yd + 16, W - 20, 0xFFFFFFFF, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(pai);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
