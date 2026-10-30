package com.lanterna.client.tropa;

import com.lanterna.client.hud.Hud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayDeque;
import java.util.Deque;

/** "+120 XP · Pirata" subindo no meio da tela, um em cima do outro. */
public final class TropaHud {
    private record Ganho(int xp, String motivo, long start) {}
    private static final Deque<Ganho> GANHOS = new ArrayDeque<>();

    public static void ganhou(int xp, String motivo) {
        if (GANHOS.size() > 5) GANHOS.removeFirst();
        GANHOS.addLast(new Ganho(xp, motivo, System.currentTimeMillis()));
    }

    public static final IGuiOverlay HUD = (ForgeGui gui, GuiGraphics g, float pt, int width, int height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || GANHOS.isEmpty()) return;
        long now = System.currentTimeMillis();
        GANHOS.removeIf(e -> now - e.start > 3500);
        int i = 0;
        for (Ganho e : GANHOS) {
            float t = (now - e.start) / 3500f;
            int alpha = (int) (255 * Math.min(1f, (1f - t) * 3f));
            if (alpha < 8) continue;
            int y = height / 2 + 30 + i * 11 - (int) (t * 14);
            Component c = Hud.bold(Component.literal("+" + e.xp + " XP")).append(Hud.body(Component.literal(e.motivo.isEmpty() ? "" : "  · " + e.motivo)));
            Hud.textCenter(g, c, width / 2, y, (alpha << 24) | 0xFFD870);
            i++;
        }
    };

    private TropaHud() {}
}
