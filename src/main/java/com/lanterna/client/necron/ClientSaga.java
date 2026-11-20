package com.lanterna.client.necron;

import com.lanterna.LanternaMod;
import com.lanterna.client.flight.Autopilot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 0.54.1: a saga do Necron do lado do cliente: Ryut partida (para desenhar no espaço e esconder do mapa) e o
 * chamado de Ryut (um painel de transmissão na tela, com chiado, e o piloto automático travado até lá).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientSaga {
    /** Ryut já se partiu neste mundo. */
    public static boolean ryutPartida;

    private static final int DUR_PAINEL = 220;
    private static int painel = -1;

    /** 0 = o chamado aparece; 2 = acabou (solta o piloto). */
    public static void chamado(int fase) {
        if (fase == 0) {
            painel = 0;
            var mc = Minecraft.getInstance();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.5f, 0.6f));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_DEACTIVATE, 0.4f, 0.8f));
        } else {
            painel = -1;
            Autopilot.destravar();
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (Minecraft.getInstance().level == null) { painel = -1; return; }
        if (painel >= 0) {
            painel++;
            if (painel % 40 == 20 && painel < DUR_PAINEL - 30)
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SOUL_ESCAPE, 0.6f + 0.1f * (painel % 3), 0.7f));
            if (painel > DUR_PAINEL) painel = -1;
        }
    }

    /** O painel do chamado: uma transmissão roxa e chiada no alto da tela. */
    @SubscribeEvent
    public static void hud(RenderGuiOverlayEvent.Post e) {
        if (painel < 0 || e.getOverlay() != net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.type()) return;
        var mc = Minecraft.getInstance();
        GuiGraphics g = e.getGuiGraphics();
        float t = painel + e.getPartialTick();
        float a = Math.min(Mth.clamp(t / 12f, 0, 1), Mth.clamp((DUR_PAINEL - t) / 20f, 0, 1));
        if (a <= 0.01f) return;
        int w = g.guiWidth();
        int pw = Math.min(330, w - 20), ph = 70;
        int x = (w - pw) / 2, y = 14;
        int al = (int) (a * 220);
        g.fill(x, y, x + pw, y + ph, (al << 24) | 0x0C0612);
        int borda = ((int) (a * (170 + 60 * Mth.sin(t * 0.3f))) << 24) | 0x8A3CC8;
        g.fill(x, y, x + pw, y + 1, borda);
        g.fill(x, y + ph - 1, x + pw, y + ph, borda);
        g.fill(x, y, x + 1, y + ph, borda);
        g.fill(x + pw - 1, y, x + pw, y + ph, borda);
        // chiado: linhas que correm pelo painel
        var rnd = net.minecraft.util.RandomSource.create((long) (t / 2));
        for (int i = 0; i < 6; i++) {
            int ly = y + 2 + rnd.nextInt(ph - 4);
            int lx = x + 2 + rnd.nextInt(pw / 2);
            g.fill(lx, ly, lx + 20 + rnd.nextInt(pw / 2), ly + 1, ((int) (a * 60) << 24) | 0xB070FF);
        }
        // o símbolo: um círculo partido
        int cx = x + 30, cy = y + ph / 2;
        for (int k = 0; k < 40; k++) {
            double ang = Math.PI * 2 * k / 40;
            if (Math.abs(Math.sin(ang)) < 0.12) continue;
            int px = cx + (int) (Math.cos(ang) * 16), py = cy + (int) (Math.sin(ang) * 16);
            g.fill(px, py, px + 2, py + 2, ((int) (a * 255) << 24) | 0xA050E0);
        }
        int ta = Math.max(4, (int) (a * 255));
        g.drawString(mc.font, Component.translatable("necron.lanterna.chamado_titulo"), x + 58, y + 12, (ta << 24) | 0xD8B8FF);
        g.drawString(mc.font, Component.translatable("necron.lanterna.chamado_painel_1"), x + 58, y + 28, (ta << 24) | 0xB8A8C8);
        g.drawString(mc.font, Component.translatable("necron.lanterna.chamado_painel_2"), x + 58, y + 42, (ta << 24) | 0x9A8AAA);
    }

    private ClientSaga() {}
}
