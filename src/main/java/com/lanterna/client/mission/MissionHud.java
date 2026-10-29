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
    private static final int WIDTH = 168;
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

    private static void drawTracker(GuiGraphics g, Mission m, Waypoints.Target target, int width) {
        int x = width - WIDTH - 8;
        int y = topOffset;
        var font = Hud.font();
        List<FormattedCharSequence> title = font.split(Hud.bold(m.title()), WIDTH - 14);
        int h = 16 + title.size() * 10 + m.objectives().size() * 11 + (target != null ? 22 : 0) + 4;
        Hud.panel(g, x, y, WIDTH, h, m.color(), 0.95f);
        Component kind = Component.translatable("hud.lanterna.mission." + m.line());
        g.drawString(font, Hud.caps(kind), x + 7, y + 4, Hud.argb(m.color(), 255), false);
        int ty = y + 15;
        for (FormattedCharSequence line : title) {
            g.drawString(font, line, x + 7, ty, 0xFFF2F2F2, true);
            ty += 10;
        }
        ty += 2;
        for (int i = 0; i < m.objectives().size(); i++) {
            Objective o = m.objectives().get(i);
            int p = ClientLoreData.DATA.progress(m.id(), i);
            boolean done = p >= o.count();
            Component box = Component.literal(done ? "✔ " : "• ");
            Component label = Hud.body(o.label(p));
            g.drawString(font, box, x + 7, ty, done ? 0xFF7CFF8A : Hud.argb(m.color(), 255), true);
            int lw = WIDTH - 22;
            String s = label.getString();
            Component shown = font.width(label) > lw ? Hud.body(font.plainSubstrByWidth(s, lw - 6) + "…") : label;
            g.drawString(font, shown, x + 16, ty, done ? 0xFF6A7A6E : 0xFFD8DEE4, true);
            ty += 11;
        }
        if (target != null) {
            ty += 2;
            g.fill(x + 7, ty - 1, x + WIDTH - 7, ty, 0x30FFFFFF);
            Component hint = Hud.body(target.hint());
            String hs = hint.getString();
            Component hintShown = font.width(hint) > WIDTH - 14 ? Hud.body(font.plainSubstrByWidth(hs, WIDTH - 20) + "…") : hint;
            g.drawString(font, hintShown, x + 7, ty + 2, 0xFFB8C4CC, true);
            Component dist = Hud.bold(Component.literal(Hud.distance(target.distance())));
            g.drawString(font, dist, x + 7, ty + 12, Hud.argb(m.color(), 255), true);
            Component keys = Hud.body(Component.translatable("hud.lanterna.mission.keys"));
            Hud.textRight(g, keys, x + WIDTH - 7, ty + 12, 0xFF7A8A94);
        }
    }

    /** Marcador do objetivo na tela (preso na borda quando está fora de vista). */
    private static void drawMarker(GuiGraphics g, Waypoints.Target t, int w, int h) {
        float[] s = Waypoints.project(t.pos(), w, h);
        if (s == null) return;
        float x = s[0], y = s[1];
        boolean behind = s[2] > 0;
        int margin = 18;
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
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        if (off) {
            float ang = (float) Math.toDegrees(Math.atan2(y - cy, x - cx)) + 90;
            Component arrow = Component.literal(Hud.arrow(ang));
            g.drawString(font, arrow, -font.width(arrow) / 2, -4, color, true);
        } else {
            float pulse = 0.85f + 0.15f * Mth.sin((System.currentTimeMillis() % 100000) / 180f);
            g.pose().scale(pulse * 1.6f, pulse * 1.6f, 1);
            Component diamond = Component.literal("◆");
            g.drawString(font, diamond, -font.width(diamond) / 2, -4, color, true);
            g.pose().scale(1 / (pulse * 1.6f), 1 / (pulse * 1.6f), 1);
            Component d = Hud.bold(Component.literal(Hud.distance(t.distance())));
            g.drawString(font, d, -font.width(d) / 2, 9, 0xFFFFFFFF, true);
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
