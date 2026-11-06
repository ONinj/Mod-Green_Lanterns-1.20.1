package com.lanterna.client.juramento;

import com.lanterna.client.hud.Hud;
import com.lanterna.juramento.Juramento;
import com.lanterna.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A tela do juramento: os versos aparecem e o jogador recita em voz alta. Cada palavra só acende enquanto o microfone
 * capta a voz por tempo suficiente para dizê-la (palavras longas pedem mais tempo). Funciona em português e inglês
 * (o texto vem do idioma do jogo).
 */
public class JuramentoScreen extends Screen {
    private final String tipo, tropa;
    private final int cor;
    private final List<List<String>> versos = new ArrayList<>();
    private int verso, palavra;
    private float progresso;
    private long ultimo = System.currentTimeMillis();
    private long fim = -1;
    private float brilho;
    private int total;
    /** Teste automático: fala sozinho. */
    public static boolean autoFalar;

    public static void abrir(String tipo, String tropa, int cor) {
        Minecraft.getInstance().setScreen(new JuramentoScreen(tipo, tropa, cor));
    }

    private JuramentoScreen(String tipo, String tropa, int cor) {
        super(Component.translatable(tipo.equals(Juramento.FONTE) ? "juramento.lanterna.titulo_fonte" : "juramento.lanterna.titulo"));
        this.tipo = tipo;
        this.tropa = tropa;
        this.cor = cor;
        String base = tipo.equals(Juramento.FONTE) ? "juramento.lanterna." : "juramento_anel.lanterna.";
        for (int i = 1; i <= 4; i++) {
            String linha = I18n.get(base + tropa + "." + i);
            List<String> ws = new ArrayList<>();
            for (String w : linha.trim().split("\\s+")) if (!w.isEmpty()) ws.add(w);
            versos.add(ws);
            total += ws.size();
        }
    }

    @Override
    protected void init() {
        Microfone.ligar();
    }

    @Override
    public void removed() {
        Microfone.desligar();
        if (fim < 0) ModNetwork.sendToServer(new Juramento.Feito(tipo, tropa, -1));
    }

    /** Tempo de fala para dizer a palavra (s): letras contam, pontuação não. */
    private static float tempo(String w) {
        int letras = 0;
        for (char c : w.toCharArray()) if (Character.isLetterOrDigit(c)) letras++;
        return Mth.clamp(0.16f + 0.055f * letras, 0.2f, 0.7f);
    }

    private boolean falando() {
        if (autoFalar) Microfone.simular();
        if (Microfone.falando()) return true;
        // sem microfone nenhum: segurar espaço (e falar) continua
        return !Microfone.disponivel() && GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
    }

    @Override
    public void tick() {
        long agora = System.currentTimeMillis();
        float dt = Math.min(0.1f, (agora - ultimo) / 1000f);
        ultimo = agora;
        brilho = Math.max(0, brilho - 0.08f);
        if (fim >= 0) {
            if (agora - fim > 2600) onClose();
            return;
        }
        if (verso >= versos.size()) return;
        List<String> ws = versos.get(verso);
        if (falando()) progresso += dt;
        if (progresso >= tempo(ws.get(palavra))) {
            progresso = 0;
            brilho = 1;
            palavra++;
            if (palavra >= ws.size()) {
                palavra = 0;
                verso++;
                if (minecraft != null && minecraft.player != null) minecraft.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 0.8f + verso * 0.15f);
                if (verso >= versos.size()) {
                    fim = agora;
                    ModNetwork.sendToServer(new Juramento.Feito(tipo, tropa, total));
                    if (minecraft != null && minecraft.player != null) minecraft.player.playSound(SoundEvents.BEACON_ACTIVATE, 1f, 1.2f);
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // fundo escuro com um brilho da cor da Tropa vindo de baixo
        g.fillGradient(0, 0, width, height, 0xC0000000, Hud.argb(cor, 0x50 + (int) (brilho * 60)));
        int w = Math.min(width - 40, 420), x = (width - w) / 2;
        int h = 210, y = Math.max(10, (height - h) / 2);
        Hud.panel(g, x, y, w, h, cor, 0.95f);
        Hud.textCenter(g, Hud.title(title.copy()), width / 2, y + 10, Hud.argb(cor, 255));
        Hud.wrapped(g, Hud.body(Component.translatable("juramento.lanterna.instrucao")), x + 12, y + 26, w - 24, 0xFFB8C4CC, 10);
        // versos, palavra por palavra
        int ly = y + 54;
        for (int v = 0; v < versos.size(); v++) {
            List<String> ws = versos.get(v);
            int larg = 0;
            for (String s : ws) larg += font.width(s) + font.width(" ");
            int px = width / 2 - larg / 2;
            for (int i = 0; i < ws.size(); i++) {
                String s = ws.get(i);
                boolean dita = v < verso || (v == verso && i < palavra) || fim >= 0;
                boolean atual = v == verso && i == palavra && fim < 0;
                int c = dita ? Hud.argb(cor, 255) : atual ? 0xFFFFFFFF : v == verso ? 0xFF8A949C : 0xFF4A5258;
                if (atual) {
                    float f = Mth.clamp(progresso / tempo(s), 0, 1);
                    g.fill(px - 1, ly + 10, px + (int) (font.width(s) * f), ly + 11, Hud.argb(cor, 255));
                    g.fill(px - 2, ly - 2, px + font.width(s) + 1, ly + 10, Hud.argb(cor, 40 + (int) (40 * Math.sin(System.currentTimeMillis() / 150.0))));
                }
                g.drawString(font, s, px, ly, c, dita);
                px += font.width(s) + font.width(" ");
            }
            ly += 22;
        }
        // medidor do microfone
        int my0 = y + h - 34;
        boolean fala = Microfone.falando() || autoFalar;
        float nv = autoFalar ? 0.6f : Microfone.nivel();
        int barras = 24;
        for (int i = 0; i < barras; i++) {
            float lim = (i + 1) / (float) barras;
            int bh = 3 + i / 3;
            int bx = width / 2 - barras * 3 + i * 6;
            g.fill(bx, my0 + 12 - bh, bx + 4, my0 + 12, nv * 1.6f >= lim ? Hud.argb(cor, 255) : 0x40FFFFFF);
        }
        Component estado;
        if (fim >= 0) estado = Component.translatable("juramento.lanterna.feito");
        else if (!Microfone.disponivel() && Microfone.falhou()) estado = Component.translatable("juramento.lanterna.sem_microfone");
        else estado = Component.translatable(fala ? "juramento.lanterna.ouvindo" : "juramento.lanterna.silencio");
        Hud.textCenter(g, Hud.body(estado), width / 2, my0 + 16, fala || fim >= 0 ? Hud.argb(cor, 255) : 0xFFB8C4CC);
        if (Microfone.svcConectado) Hud.textCenter(g, Hud.body(Component.translatable("juramento.lanterna.svc_ptt")), width / 2, y + h + 6, 0xFF8A949C);
        Hud.textCenter(g, Hud.body(Component.translatable("juramento.lanterna.sair")), width / 2, y + h + 18, 0xFF6A747C);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
