package com.lanterna.client;

import com.lanterna.client.flight.Destinations;
import com.lanterna.client.hud.Hud;
import com.lanterna.client.mission.ClientLoreData;
import com.lanterna.construct.Affinity;
import com.lanterna.corps.CorpsDefinition;
import com.lanterna.lore.PlanetLore;
import com.lanterna.space.SpaceBodies;
import com.lanterna.space.SpaceTravel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * HUD do anel (canto superior esquerdo):
 *
 *   TROPA DOS LANTERNAS VERDES
 *   Bateria  [██████████░░░░]  72%
 *   Afinidade ◆◆◇◇◇  Aceito
 *   Oa  ↗ 12,4 mil blocos
 *   VOO · Bateria pronta (R)
 */
public final class RingHudOverlay {
    private static final int W = 170;

    public static final IGuiOverlay HUD = (ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !ClientRingData.hasRing) return;
        var font = Hud.font();
        int color = ClientRingData.color;
        int x = 6, y = 6;
        Hud.panel(g, x, y, W, 64, color, 0.92f);
        x += 8;

        // Nome da Tropa
        Component name = Hud.caps(Component.translatable(CorpsDefinition.fullNameKey(ClientRingData.corps)));
        if (font.width(name) > W - 16) name = Hud.caps(Component.translatable(CorpsDefinition.nameKey(ClientRingData.corps)));
        g.drawString(font, name, x, y + 5, Hud.argb(color, 255), false);
        if (!ClientRingData.active) {
            Component off = Hud.body(Component.translatable("hud.lanterna.power_off"));
            Hud.textRight(g, off, 6 + W - 6, y + 5, 0xFF7A8890);
        }

        // Bateria
        int max = Math.max(1, ClientRingData.maxCharge);
        int charge = Mth.clamp(ClientRingData.charge, 0, max);
        int percent = charge * 100 / max;
        boolean low = percent <= 15;
        boolean blink = mc.level != null && (mc.level.getGameTime() / 8) % 2 == 0;
        g.drawString(font, Hud.body(Component.translatable("hud.lanterna.battery")), x, y + 17, 0xFF9AA8B0, false);
        Hud.bar(g, x + 44, y + 19, 86, 5, charge / (float) max, low && blink ? 0xFF4040 : color, true);
        Hud.textRight(g, Hud.bold(Component.literal(percent + "%")), 6 + W - 6, y + 17, low && blink ? 0xFFFF5050 : 0xFFFFFFFF);

        // Afinidade
        int pts = ClientLoreData.DATA.affinity();
        int lvl = Affinity.level(pts);
        g.drawString(font, Hud.body(Component.translatable("hud.lanterna.affinity")), x, y + 28, 0xFF9AA8B0, false);
        StringBuilder pips = new StringBuilder();
        for (int i = 1; i <= Affinity.MAX_LEVEL; i++) pips.append(i <= lvl ? "◆" : "◇");
        g.drawString(font, Component.literal(pips.toString()), x + 44, y + 28, Hud.argb(color, 255), false);
        Component lname = Hud.body(Affinity.levelName(lvl));
        Hud.textRight(g, lname, 6 + W - 6, y + 28, 0xFFD8DEE4);
        g.fill(x + 44, y + 37, x + 44 + (int) (48 * Affinity.progress(pts)), y + 38, Hud.argb(color, 200));

        // Planeta da Tropa e distância
        g.drawString(font, home(mc), x, y + 40, 0xFFE0E6EA, false);

        // Voo e bateria de bolso
        Component status;
        if (ClientRingData.pocketStatus == 0) status = Component.translatable("hud.lanterna.pocket_ready");
        else if (ClientRingData.pocketStatus > 0) status = Component.translatable("hud.lanterna.pocket_cooldown", ClientRingData.pocketStatus);
        else status = Component.empty();
        Component flight = ClientRingData.active && ClientRingData.flight ? Component.translatable("hud.lanterna.flight_on") : Component.empty();
        Component line = flight.getString().isEmpty() ? status
                : status.getString().isEmpty() ? flight : flight.copy().append(Component.literal("  ·  ")).append(status);
        g.drawString(font, Hud.body(line), x, y + 52, low && blink ? 0xFFFF5050 : 0xFFB8C4CC, false);
        if (low) {
            Component warn = Hud.bold(Component.translatable("hud.lanterna.low_charge"));
            g.drawString(font, warn, 6, y + 68, blink ? 0xFFFF4040 : 0xFF902020, true);
        }
    };

    /** "Oa ↗ 12,4 mil blocos" (ou "Você está em Oa"). */
    private static Component home(Minecraft mc) {
        PlanetLore lore = PlanetLore.byCorps(ClientRingData.corps);
        if (lore == null) return Component.empty();
        if (lore == PlanetLore.GENESE && !ClientLoreData.geneseRevealed()) return Hud.body(Component.translatable("hud.lanterna.home_unknown"));
        SpaceBodies.Body body = SpaceBodies.byId(lore.planet);
        if (body == null) return Component.empty();
        var player = mc.player;
        Component planet = Hud.bold(body.displayName()).withStyle(s -> s.withColor(lore.color()));
        if (body.dimension() != null && player.level().dimension().equals(body.dimension())) {
            return Hud.body(Component.translatable("hud.lanterna.home_here", planet));
        }
        Vec3 here = Destinations.spacePosition(player);
        double dist = Math.max(0, here.distanceTo(body.center()) - body.radius());
        String arrow;
        if (player.level().dimension().equals(SpaceBodies.SPACE)) {
            Vec3 rel = body.center().subtract(player.position());
            float yaw = (float) Math.toDegrees(Math.atan2(-rel.x, rel.z));
            arrow = Hud.arrow(Mth.wrapDegrees(yaw - player.getYRot()));
        } else {
            arrow = "⇡";
            dist += Math.max(0, SpaceTravel.EXIT_HEIGHT - player.getY());
        }
        return planet.copy().append(Hud.body(Component.literal("  " + arrow + " " + Hud.distance(dist))));
    }

    private RingHudOverlay() {}
}
