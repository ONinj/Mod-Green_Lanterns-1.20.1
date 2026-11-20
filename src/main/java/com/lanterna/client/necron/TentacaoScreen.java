package com.lanterna.client.necron;

import com.lanterna.necron.Tentacao;
import com.lanterna.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * 0.57.0: a pergunta da Morte. Tela escura, a voz do Necron em três linhas e as duas respostas. Não fecha com Esc:
 * é preciso escolher.
 */
public class TentacaoScreen extends Screen {
    private final String tropa;
    private int t;
    private Button sim, nao;
    /** Só para o teste automático. */
    public static TentacaoScreen ultima;

    public TentacaoScreen(String tropa) {
        super(Component.translatable("necron.lanterna.tentacao_titulo"));
        this.tropa = tropa;
        ultima = this;
    }

    public static void abrir(String tropa) {
        var mc = Minecraft.getInstance();
        mc.setScreen(new TentacaoScreen(tropa));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6f, 0.5f));
    }

    @Override
    protected void init() {
        int w = Math.min(220, width / 2 - 20), y = height - 60;
        sim = Button.builder(Component.translatable("necron.lanterna.tentacao_sim").withStyle(ChatFormatting.DARK_RED), b -> responder(true))
                .bounds(width / 2 - w - 8, y, w, 20).build();
        nao = Button.builder(Component.translatable("necron.lanterna.tentacao_nao").withStyle(ChatFormatting.WHITE), b -> responder(false))
                .bounds(width / 2 + 8, y, w, 20).build();
        sim.active = nao.active = t > 60;
        addRenderableWidget(sim);
        addRenderableWidget(nao);
    }

    public void responder(boolean s) {
        ModNetwork.sendToServer(new Tentacao.Resposta(s));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s ? SoundEvents.WITHER_SPAWN : SoundEvents.BEACON_ACTIVATE, s ? 0.6f : 1.2f, 0.6f));
        ultima = null;
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void tick() {
        t++;
        if (t == 61) sim.active = nao.active = true;
        if (t % 30 == 0) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.6f, 0.5f));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        float tt = t + pt;
        int a = (int) (Mth.clamp(tt / 30f, 0, 1) * 235);
        g.fillGradient(0, 0, width, height, (a << 24) | 0x05000A, (a << 24) | 0x1A0526);
        // o olho da Morte pulsando no meio
        float pulso = 0.6f + 0.4f * Mth.sin(tt * 0.1f);
        int r = (int) (height * 0.12f * pulso);
        int cx = width / 2, cy = height / 3;
        for (int k = r; k > 0; k -= 2) {
            int al = (int) (90 * (1 - k / (float) r) * Mth.clamp(tt / 40f, 0, 1));
            g.fill(cx - k, cy - k / 3, cx + k, cy + k / 3, (al << 24) | 0x6A2C9A);
        }
        String[] linhas = {"necron.lanterna.tentacao_1", "necron.lanterna.tentacao_2", "necron.lanterna.tentacao_3"};
        int y = height / 2 - 10;
        for (int i = 0; i < linhas.length; i++) {
            float k = Mth.clamp((tt - 20 - i * 15) / 20f, 0, 1);
            if (k <= 0) continue;
            int al = Math.max(4, (int) (k * 255));
            var txt = font.split(Component.translatable(linhas[i], Component.translatable("necron.lanterna.tropa." + tropa)), width - 60);
            for (var l : txt) { g.drawCenteredString(font, l, width / 2, y, (al << 24) | 0xC9A8E8); y += 11; }
            y += 4;
        }
        Component titulo = Component.translatable("necron.lanterna.tentacao_titulo").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
        g.drawCenteredString(font, titulo, width / 2, 20, 0xFFFFFFFF);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean isPauseScreen() { return false; }
}
