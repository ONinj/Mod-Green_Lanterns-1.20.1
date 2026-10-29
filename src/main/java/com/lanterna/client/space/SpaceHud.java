package com.lanterna.client.space;

import com.lanterna.client.ClientRingData;
import com.lanterna.client.hud.Hud;
import com.lanterna.client.mission.ClientLoreData;
import com.lanterna.client.mission.MissionHud;
import com.lanterna.space.SpaceBodies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Navegação no espaço (canto superior direito): os corpos mais próximos com seta, nome e distância
 * em colunas, e o estado do oxigênio. Fontes do HUD (Rajdhani/Orbitron).
 */
public final class SpaceHud {
    private static final int W = 176;

    public static final IGuiOverlay HUD = (ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) -> {
        Minecraft mc = Minecraft.getInstance();
        MissionHud.topOffset = 8;
        if (mc.player == null || mc.options.hideGui || !mc.player.level().dimension().equals(SpaceBodies.SPACE)) return;
        Font font = mc.font;
        Vec3 pos = mc.player.position();

        List<SpaceBodies.Body> list = new ArrayList<>();
        for (SpaceBodies.Body b : SpaceBodies.near(pos, 1)) {
            if (!b.landable()) continue;
            if (SpaceBodies.isHidden(b.id()) && !ClientLoreData.geneseRevealed()) continue;
            list.add(b);
        }
        list.sort(Comparator.comparingDouble(b -> b.center().distanceTo(pos) - b.radius()));
        int rows = Math.min(5, list.size());
        int x = width - W - 8, y = 8;
        int h = 22 + rows * 12 + 16;
        int accent = ClientRingData.hasRing ? ClientRingData.color : 0x8AB4FF;
        Hud.panel(g, x, y, W, h, accent, 0.9f);

        Component title = Hud.caps(Component.translatable("space.lanterna.hud.title"));
        g.drawString(font, title, x + 8, y + 5, Hud.argb(accent, 255), false);
        Component speed = Hud.body(Component.literal(String.format(java.util.Locale.ROOT, "%.0f b/s",
                com.lanterna.client.flight.ClientFlight.localSpeed() * 20)));
        Hud.textRight(g, speed, x + W - 8, y + 5, 0xFF8A98A4);
        g.fill(x + 8, y + 16, x + W - 8, y + 17, 0x28FFFFFF);

        int ly = y + 21;
        for (int i = 0; i < rows; i++) {
            SpaceBodies.Body b = list.get(i);
            Vec3 rel = b.center().subtract(pos);
            double dist = Math.max(0, rel.length() - b.radius());
            float targetYaw = (float) Math.toDegrees(Math.atan2(-rel.x, rel.z));
            float diff = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
            int color = 0xFF000000 | b.atmosphere();
            g.drawString(font, Component.literal(Hud.arrow(diff)), x + 8, ly, color, true);
            g.fill(x + 19, ly + 3, x + 22, ly + 6, color);
            Component name = Hud.bold(b.displayName());
            String ns = name.getString();
            if (font.width(name) > 88) name = Hud.bold(Component.literal(font.plainSubstrByWidth(ns, 84) + "…"));
            g.drawString(font, name, x + 26, ly, i == 0 ? 0xFFFFFFFF : 0xFFD0D8DE, true);
            Hud.textRight(g, Hud.body(Component.literal(Hud.distance(dist))), x + W - 8, ly, color);
            ly += 12;
        }

        // Oxigênio
        boolean safe = mc.player.isCreative() || (ClientRingData.hasRing && ClientRingData.active);
        boolean blink = (mc.player.tickCount / 8) % 2 == 0;
        g.fill(x + 8, ly + 1, x + W - 8, ly + 2, 0x20FFFFFF);
        Component oxygen = Hud.body(Component.translatable(safe ? "space.lanterna.hud.oxygen_ok" : "space.lanterna.hud.oxygen_none"));
        g.drawString(font, oxygen, x + 8, ly + 4, safe ? 0xFF9FE0A0 : (blink ? 0xFFFF4040 : 0xFF802020), true);
        MissionHud.topOffset = y + h + 6;
    };

    private SpaceHud() {}
}
