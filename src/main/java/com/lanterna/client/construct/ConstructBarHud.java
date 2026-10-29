package com.lanterna.client.construct;

import com.lanterna.client.ClientRingData;
import com.lanterna.client.KeyBindings;
import com.lanterna.client.hud.Hud;
import com.lanterna.construct.ConstructType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Barra de construtos ao lado da barra de itens: o construto da tecla X e os quatro presets (Alt+1..4),
 * com a recarga de cada um.
 */
public final class ConstructBarHud {
    private static final int SLOT = 20;

    public static final IGuiOverlay HUD = (ForgeGui gui, GuiGraphics g, float pt, int width, int height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientRingData.hasRing || !ClientRingData.active || mc.player.isSpectator()) return;
        int right = width / 2 - 91 - 8;
        int y = height - SLOT - 2;
        int x = right - SLOT * 5 - 10;
        if (x < 4) return;
        Hud.panel(g, x - 4, y - 4, SLOT * 5 + 18, SLOT + 8, ClientRingData.color, 0.85f);
        slot(g, x, y, ClientConstructs.selected(), KeyBindings.CONSTRUCT_USE.getTranslatedKeyMessage(), true);
        for (int i = 0; i < 4; i++) {
            ConstructType t = ConstructType.byId(ClientConstructs.data().preset(i));
            slot(g, x + SLOT + 10 + i * SLOT, y, t, Component.literal(String.valueOf(i + 1)), false);
        }
    };

    private static void slot(GuiGraphics g, int x, int y, ConstructType t, Component key, boolean main) {
        g.fill(x, y, x + SLOT - 2, y + SLOT - 2, main ? Hud.argb(ClientRingData.color, 80) : 0x80000000);
        if (t == null) return;
        boolean locked = ClientConstructs.level() < t.unlockLevel;
        g.pose().pushPose();
        g.pose().translate(x + 1, y + 1, 0);
        g.pose().scale(16 / 32f, 16 / 32f, 1);
        if (locked) g.setColor(0.35f, 0.35f, 0.35f, 1f);
        g.blit(ConstructWheelScreen.icon(t), 0, 0, 0, 0, 32, 32, 32, 32);
        g.setColor(1f, 1f, 1f, 1f);
        g.pose().popPose();
        float cd = ClientConstructs.cooldown(t);
        if (cd > 0) {
            int h = (int) ((SLOT - 2) * cd);
            g.fill(x, y + SLOT - 2 - h, x + SLOT - 2, y + SLOT - 2, 0xA0000000);
        }
        if (ClientConstructs.isHolding(t)) g.fill(x, y + SLOT - 3, x + SLOT - 2, y + SLOT - 2, Hud.argb(ClientRingData.color, 255));
        var font = Hud.font();
        String k = key.getString();
        if (k.length() > 2) k = k.substring(0, 1);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.drawString(font, Component.literal(k), x + SLOT - 2 - font.width(k), y + SLOT - 10, 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    private ConstructBarHud() {}
}
