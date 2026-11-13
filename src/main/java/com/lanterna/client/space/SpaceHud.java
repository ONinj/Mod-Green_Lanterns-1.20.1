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
    private static final int W = 146;

    public static final IGuiOverlay HUD = (ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) -> {
        Minecraft mc = Minecraft.getInstance();
        MissionHud.topOffset = 8;
        if (mc.player == null || mc.options.hideGui || !mc.player.level().dimension().equals(SpaceBodies.SPACE)) return;
        Font font = mc.font;
        VisaoLongeCliente.nomes(g, width, height);
        Vec3 pos = mc.player.position();

        List<SpaceBodies.Body> list = new ArrayList<>();
        for (SpaceBodies.Body b : SpaceBodies.near(pos, 1)) {
            if (!b.landable()) continue;
            if (SpaceBodies.isHidden(b.id()) && !ClientLoreData.geneseRevealed()) continue;
            list.add(b);
        }
        list.sort(Comparator.comparingDouble(b -> b.center().distanceTo(pos) - b.radius()));
        int rows = Math.min(3, list.size());
        int x = width - W - 6, y = 6;
        // o sol ou buraco negro mais perto (até uns 6 mil blocos da zona de perigo)
        com.lanterna.space.Perigos.Perigo perigo = null;
        double perigoDist = 6000;
        for (var p : com.lanterna.space.Perigos.near(pos, 1)) {
            double d = p.centro().distanceTo(pos) - p.raio();
            if (d < perigoDist) { perigoDist = d; perigo = p; }
        }
        boolean safe = mc.player.isCreative() || (ClientRingData.hasRing && ClientRingData.active);
        int h = 20 + rows * 11 + (perigo != null ? 11 : 0) + (safe ? 0 : 12) + 2;
        int accent = ClientRingData.hasRing ? ClientRingData.color : 0x8AB4FF;
        Hud.panel(g, x, y, W, h, accent, 0.82f);

        Component title = Hud.caps(Component.translatable("space.lanterna.hud.title"));
        g.drawString(font, title, x + 7, y + 5, Hud.argb(accent, 255), false);
        Component speed = Hud.body(Component.literal(String.format(java.util.Locale.ROOT, "%.0f b/s",
                com.lanterna.client.flight.ClientFlight.localSpeed() * 20)));
        Hud.textRight(g, speed, x + W - 7, y + 5, 0xFF8A98A4);
        int ly = y + 18;
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
            if (font.width(name) > 70) name = Hud.bold(Component.literal(font.plainSubstrByWidth(ns, 66) + "…"));
            g.drawString(font, name, x + 26, ly, i == 0 ? 0xFFFFFFFF : 0xFFD0D8DE, true);
            Hud.textRight(g, Hud.body(Component.literal(Hud.distance(dist))), x + W - 7, ly, color);
            ly += 11;
        }

        if (perigo != null) {
            Vec3 rel = perigo.centro().subtract(pos);
            float diff = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-rel.x, rel.z)) - mc.player.getYRot());
            boolean perto = rel.length() < perigo.alcance();
            int color = perto && (mc.player.tickCount / 6) % 2 == 0 ? 0xFFFF3020 : 0xFF000000 | perigo.cor();
            g.drawString(font, Component.literal(Hud.arrow(diff)), x + 8, ly, color, true);
            Component name = Hud.bold(Component.literal("☢ ").append(perigo.nomeExibido()));
            if (font.width(name) > 76) name = Hud.bold(Component.literal(font.plainSubstrByWidth(name.getString(), 72) + "…"));
            g.drawString(font, name, x + 19, ly, color, true);
            Hud.textRight(g, Hud.body(Component.literal(Hud.distance(Math.max(0, perigoDist)))), x + W - 7, ly, color);
            ly += 11;
        }

        // Oxigênio: só aparece quando falta
        if (!safe) {
            boolean blink = (mc.player.tickCount / 8) % 2 == 0;
            Component oxygen = Hud.bold(Component.translatable("space.lanterna.hud.oxygen_none"));
            g.drawString(font, oxygen, x + 8, ly + 2, blink ? 0xFFFF4040 : 0xFF802020, true);
        }
        MissionHud.topOffset = y + h + 6;
    };

    private SpaceHud() {}
}
