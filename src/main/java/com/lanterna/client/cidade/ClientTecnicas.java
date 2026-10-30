package com.lanterna.client.cidade;

import com.lanterna.cidade.CidadeMissoes;
import com.lanterna.cidade.TecnicaPacket;
import com.lanterna.cidade.Tecnicas;
import com.lanterna.client.ClientRingData;
import com.lanterna.client.KeyBindings;
import com.lanterna.client.mission.ClientLoreData;
import com.lanterna.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.HashMap;
import java.util.Map;

/** Teclas das técnicas (Y, U, I), a tela para equipar (O) e o HUD com as técnicas equipadas. */
public final class ClientTecnicas {
    private static final Map<String, Long> USADA = new HashMap<>();

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        while (KeyBindings.TECNICAS.consumeClick()) mc.setScreen(new Tela());
        for (int i = 0; i < 3; i++) {
            while (KeyBindings.TECNICA[i].consumeClick()) usar(i);
        }
    }

    private static void usar(int slot) {
        String t = ClientLoreData.DATA.slotTecnica(slot);
        ModNetwork.sendToServer(new TecnicaPacket(0, slot, ""));
        if (!t.isEmpty() && espera(t) <= 0 && ClientRingData.active) USADA.put(t, System.currentTimeMillis());
    }

    /** 0..1 do tempo de espera que falta (estimado no cliente). */
    static float espera(String t) {
        Long u = USADA.get(t);
        var info = Tecnicas.INFO.get(t);
        if (u == null || info == null) return 0;
        float total = info.espera() * 50f;
        return Mth.clamp(1f - (System.currentTimeMillis() - u) / total, 0, 1);
    }

    /** HUD: as técnicas equipadas, à esquerda da barra de itens. */
    public static final IGuiOverlay HUD = (gui, g, pt, w, h) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !ClientRingData.hasRing) return;
        var d = ClientLoreData.DATA;
        if (d.tecnicas().isEmpty()) return;
        int n = d.honra() ? 3 : 2;
        int x = w / 2 - 91 - 8 - n * 26, y = h - 24;
        String[] teclas = {KeyBindings.TECNICA[0].getTranslatedKeyMessage().getString(), KeyBindings.TECNICA[1].getTranslatedKeyMessage().getString(),
                KeyBindings.TECNICA[2].getTranslatedKeyMessage().getString()};
        int cor = 0xFF000000 | ClientRingData.color;
        for (int i = 0; i < n; i++) {
            int bx = x + i * 26;
            g.fill(bx, y, bx + 24, y + 22, 0xB0101418);
            g.fill(bx, y, bx + 24, y + 1, cor);
            String t = d.slotTecnica(i);
            if (!t.isEmpty()) {
                String sigla = Component.translatable("tecnica.lanterna." + t).getString();
                sigla = sigla.length() > 3 ? sigla.substring(0, 3) : sigla;
                g.drawString(mc.font, sigla, bx + 12 - mc.font.width(sigla) / 2, y + 4, 0xFFFFFFFF, true);
                float e = espera(t);
                if (e > 0) g.fill(bx, y + 22 - Math.round(22 * e), bx + 24, y + 22, 0x90000000);
            }
            g.drawString(mc.font, teclas[i], bx + 12 - mc.font.width(teclas[i]) / 2, y + 13, 0xFF9AA8B0, false);
        }
    };

    /** Tela das técnicas: o que já aprendeu, quem ensina o resto, e em que tecla cada uma fica. */
    public static class Tela extends Screen {
        private static final int W = 360, ROW = 34;

        public Tela() {
            super(Component.translatable("tecnica.lanterna.tela"));
        }

        @Override
        protected void init() {
            var d = ClientLoreData.DATA;
            int left = (width - W) / 2, top = (height - (50 + 6 * ROW)) / 2;
            int n = d.honra() ? 3 : 2;
            int i = 0;
            for (String t : CidadeMissoes.TECNICAS) {
                int y = top + 26 + i * ROW;
                if (d.tecnicas().contains(t)) {
                    for (int s = 0; s < n; s++) {
                        int slot = s;
                        Button b = Button.builder(Component.literal(KeyBindings.TECNICA[s].getTranslatedKeyMessage().getString()),
                                bt -> { ModNetwork.sendToServer(new TecnicaPacket(1, slot, t)); onClose(); })
                                .bounds(left + W - 10 - (n - s) * 30, y + 6, 26, 18).build();
                        b.active = !d.slotTecnica(s).equals(t);
                        addRenderableWidget(b);
                    }
                }
                i++;
            }
            addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 40, top + 30 + 6 * ROW, 80, 18).build());
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            renderBackground(g);
            var d = ClientLoreData.DATA;
            int left = (width - W) / 2, top = (height - (50 + 6 * ROW)) / 2;
            int cor = 0xFF000000 | ClientRingData.color;
            g.fill(left - 1, top - 1, left + W + 1, top + 52 + 6 * ROW + 1, cor);
            g.fill(left, top, left + W, top + 52 + 6 * ROW, 0xF0080B14);
            g.drawString(font, title, left + 10, top + 8, cor, false);
            int i = 0;
            for (String t : CidadeMissoes.TECNICAS) {
                int y = top + 26 + i * ROW;
                boolean tem = d.tecnicas().contains(t);
                g.fill(left + 6, y, left + W - 6, y + ROW - 4, tem ? 0x30FFFFFF : 0x18FFFFFF);
                g.drawString(font, Component.translatable("tecnica.lanterna." + t), left + 12, y + 4, tem ? 0xFFFFFFFF : 0xFF7A8499, false);
                Component sub = tem ? Component.translatable("tecnica.lanterna." + t + ".desc")
                        : Component.translatable("tecnica.lanterna.ensina", Component.translatable("cidade.lanterna.npc.capitao_" + t));
                g.drawString(font, sub, left + 12, y + 16, 0xFF9AA8B0, false);
                i++;
            }
            super.render(g, mx, my, pt);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    private ClientTecnicas() {}
}
