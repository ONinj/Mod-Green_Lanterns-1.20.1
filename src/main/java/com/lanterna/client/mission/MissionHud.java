package com.lanterna.client.mission;

import com.lanterna.client.hud.Hud;
import com.lanterna.mission.Mission;
import com.lanterna.mission.Missions;
import com.lanterna.mission.Objective;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;

/**
 * HUD de missão (lado direito): missão acompanhada, objetivos com progresso e para onde ir,
 * um marcador na tela apontando o objetivo (também no espaço) e o aviso grande de missão nova/concluída.
 */
public final class MissionHud {
    private static final int WIDTH = 184;
    private static Mission toastMission;
    private static boolean toastDone;
    private static long toastStart = -1000;
    /** Altura usada pelo HUD do espaço (o painel de missão fica logo abaixo). */
    public static int topOffset = 8;

    public static void toast(boolean completed, String mission) {
        toastMission = Missions.get(mission);
        toastDone = completed;
        toastStart = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
    }

    public static final IGuiOverlay HUD = (ForgeGui gui, GuiGraphics g, float pt, int width, int height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.level == null) return;
        drawToast(g, width, mc.level.getGameTime() + pt);
        Mission m = ClientLoreData.tracked();
        if (m == null) return;
        Waypoints.Target target = Waypoints.current();
        if (target != null) drawMarker(g, target, width, height);
        drawTracker(g, m, target, width);
    };

    /**
     * Painel da missão: faixa com o tipo da missão, título, objetivos (o atual em destaque, com barra de progresso;
     * os feitos riscados; os próximos apagados) e, embaixo, para onde ir agora com seta relativa à sua visão.
     */
    private static void drawTracker(GuiGraphics g, Mission m, Waypoints.Target target, int width) {
        int x = width - WIDTH - 8;
        int y = topOffset;
        var font = Hud.font();
        int c = m.color();
        List<FormattedCharSequence> title = font.split(Hud.bold(m.title()), WIDTH - 16);
        int atual = -1;
        for (int i = 0; i < m.objectives().size(); i++) {
            if (ClientLoreData.DATA.progress(m.id(), i) < m.objectives().get(i).count()) { atual = i; break; }
        }
        int objH = 0;
        for (int i = 0; i < m.objectives().size(); i++) {
            Objective o = m.objectives().get(i);
            objH += 11 + (i == atual && o.count() > 1 ? 5 : 0);
        }
        int h = 18 + title.size() * 10 + 4 + objH + (target != null ? 28 : 0) + 4;
        Hud.panel(g, x, y, WIDTH, h, c, 0.95f);
        // faixa do topo
        g.fillGradient(x + 2, y + 1, x + WIDTH - 1, y + 13, Hud.argb(c, 90), Hud.argb(c, 10));
        Component kind = Component.translatable("hud.lanterna.mission." + m.line());
        g.drawString(font, Hud.caps(kind), x + 8, y + 3, Hud.argb(Hud.mix(c, 0xFFFFFF, 0.35f), 255), false);
        Hud.textRight(g, Hud.body(Component.translatable("hud.lanterna.mission.diario")), x + WIDTH - 6, y + 3, 0xFF8A98A4);
        int ty = y + 17;
        for (FormattedCharSequence line : title) {
            g.drawString(font, line, x + 8, ty, 0xFFFFFFFF, true);
            ty += 10;
        }
        ty += 3;
        for (int i = 0; i < m.objectives().size(); i++) {
            Objective o = m.objectives().get(i);
            int p = ClientLoreData.DATA.progress(m.id(), i);
            boolean done = p >= o.count();
            boolean now = i == atual;
            int lw = WIDTH - 24;
            Component label = Hud.body(o.label(p));
            String str = label.getString();
            Component shown = font.width(label) > lw ? Hud.body(font.plainSubstrByWidth(str, lw - 6) + "…") : label;
            if (done) {
                g.drawString(font, Component.literal("✔"), x + 8, ty, 0xFF6FD98A, true);
                g.drawString(font, shown.copy().withStyle(net.minecraft.ChatFormatting.STRIKETHROUGH), x + 18, ty, 0xFF5E6E66, false);
            } else if (now) {
                float pulse = 0.6f + 0.4f * Mth.sin((System.currentTimeMillis() % 100000) / 220f);
                g.fill(x + 5, ty - 2, x + WIDTH - 5, ty + 9 + (o.count() > 1 ? 5 : 0), Hud.argb(c, 28));
                g.drawString(font, Component.literal("▶"), x + 8, ty, Hud.argb(c, (int) (255 * pulse)), true);
                g.drawString(font, shown, x + 18, ty, 0xFFFFFFFF, true);
                if (o.count() > 1) Hud.bar(g, x + 18, ty + 10, WIDTH - 30, 2, Math.min(p, o.count()) / (float) o.count(), c, false);
            } else {
                g.drawString(font, Component.literal("○"), x + 8, ty, 0xFF5A6670, false);
                g.drawString(font, shown, x + 18, ty, 0xFF8A98A4, false);
            }
            ty += 11 + (now && o.count() > 1 ? 5 : 0);
        }
        if (target != null) {
            ty += 2;
            g.fill(x + 7, ty, x + WIDTH - 7, ty + 1, Hud.argb(c, 50));
            ty += 4;
            // seta relativa para onde você está olhando
            var mc = Minecraft.getInstance();
            String seta = "•";
            if (mc.player != null) {
                var d = target.pos().subtract(mc.player.getEyePosition());
                float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
                seta = Hud.arrow(Mth.wrapDegrees(yaw - mc.player.getYRot()));
            }
            g.pose().pushPose();
            g.pose().translate(x + 13, ty + 9, 0);
            g.pose().scale(1.6f, 1.6f, 1);
            Component arrow = Component.literal(seta);
            g.drawString(font, arrow, -font.width(arrow) / 2, -4, Hud.argb(c, 255), true);
            g.pose().popPose();
            Component dist = Hud.bold(Component.literal(Hud.distance(target.distance())));
            g.drawString(font, dist, x + 25, ty, 0xFFFFFFFF, true);
            Component hint = Hud.body(target.hint());
            String hs = hint.getString();
            int hw = WIDTH - 32;
            Component hintShown = font.width(hint) > hw ? Hud.body(font.plainSubstrByWidth(hs, hw - 6) + "…") : hint;
            g.drawString(font, hintShown, x + 25, ty + 10, 0xFFB8C4CC, true);
        }
    }

    /** Marcador do objetivo na tela: losango com anel pulsando, o que fazer em cima e a distância embaixo.
     *  Fora da tela, uma seta na borda com a distância. */
    private static void drawMarker(GuiGraphics g, Waypoints.Target t, int w, int h) {
        float[] s = Waypoints.project(t.pos(), w, h);
        if (s == null) return;
        float x = s[0], y = s[1];
        boolean behind = s[2] > 0;
        int margin = 22;
        boolean off = behind || x < margin || x > w - margin || y < margin || y > h - margin;
        float cx = w / 2f, cy = h / 2f;
        if (off) {
            float dx = x - cx, dy = y - cy;
            if (Math.abs(dx) < 1e-3 && Math.abs(dy) < 1e-3) dy = -1;
            float scale = Math.min((cx - margin) / Math.max(1e-3f, Math.abs(dx)), (cy - margin) / Math.max(1e-3f, Math.abs(dy)));
            x = cx + dx * scale;
            y = cy + dy * scale;
        }
        int color = Hud.argb(t.color(), 255);
        var font = Hud.font();
        float pulse = 0.5f + 0.5f * Mth.sin((System.currentTimeMillis() % 100000) / 200f);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        Component d = Hud.bold(Component.literal(Hud.distance(t.distance())));
        if (off) {
            float ang = (float) Math.toDegrees(Math.atan2(y - cy, x - cx)) + 90;
            g.pose().pushPose();
            g.pose().scale(1.8f, 1.8f, 1);
            Component arrow = Component.literal(Hud.arrow(ang));
            g.drawString(font, arrow, -font.width(arrow) / 2, -4, color, true);
            g.pose().popPose();
            int dy = y > cy ? -18 : 10;
            g.drawString(font, d, -font.width(d) / 2, dy, 0xFFFFFFFF, true);
        } else {
            // anel pulsando em volta do losango
            int r = 7 + (int) (pulse * 3);
            int a = (int) (90 * (1 - pulse)) + 40;
            for (int i = 0; i < 24; i++) {
                double an = i * Math.PI * 2 / 24;
                int px = (int) Math.round(Math.cos(an) * r), py = (int) Math.round(Math.sin(an) * r);
                g.fill(px, py, px + 1, py + 1, Hud.argb(t.color(), a));
            }
            g.pose().pushPose();
            g.pose().scale(1.7f, 1.7f, 1);
            Component diamond = Component.literal("◆");
            g.drawString(font, diamond, -font.width(diamond) / 2, -4, color, true);
            g.pose().popPose();
            Component o = Hud.body(t.label());
            String os = o.getString();
            Component oShown = font.width(o) > 150 ? Hud.body(font.plainSubstrByWidth(os, 144) + "…") : o;
            int ow = font.width(oShown);
            g.fill(-ow / 2 - 3, -24, ow / 2 + 3, -13, 0x90000000);
            g.drawString(font, oShown, -ow / 2, -22, 0xFFFFFFFF, false);
            g.drawString(font, d, -font.width(d) / 2, 12, 0xFFFFFFFF, true);
        }
        g.pose().popPose();
    }

    private static void drawToast(GuiGraphics g, int width, float now) {
        if (toastMission == null) return;
        float t = (now - toastStart) / 20f;
        if (t < 0 || t > 5f) return;
        float a = t < 0.4f ? t / 0.4f : t > 4f ? (5f - t) : 1f;
        int alpha = (int) (Mth.clamp(a, 0, 1) * 255);
        if (alpha < 8) return;
        int color = toastMission.color();
        Component head = Hud.caps(Component.translatable(toastDone ? "hud.lanterna.mission.completed" : "hud.lanterna.mission.new"));
        Component title = Hud.bold(toastMission.title());
        var font = Hud.font();
        int w = Math.max(font.width(head), font.width(title) * 3 / 2) + 40;
        int x = width / 2 - w / 2, y = 34;
        Hud.panel(g, x, y, w, 36, color, a);
        g.drawString(font, head, width / 2 - font.width(head) / 2, y + 5, Hud.argb(color, alpha), false);
        g.pose().pushPose();
        g.pose().translate(width / 2f, y + 17, 0);
        g.pose().scale(1.5f, 1.5f, 1);
        g.drawString(font, title, -font.width(title) / 2, 0, Hud.argb(0xFFFFFF, alpha), true);
        g.pose().popPose();
    }

    private MissionHud() {}
}
