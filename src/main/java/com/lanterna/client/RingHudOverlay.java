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
 * HUD do anel (canto superior esquerdo), enxuto: só o que se olha no meio da luta.
 *
 *   ● VERDE              Nv 50
 *   [████████████░░░░]    72%
 *   ▔▔▔▔▔▔ (experiência, fininha)
 *
 * Voo, bateria de bolso e anel desligado aparecem como etiquetas pequenas só quando importam; afinidade, posto e o
 * caminho de casa ficam no perfil (P) e no diário (M).
 */
public final class RingHudOverlay {
    private static float suave = 1f, rastro = 1f;
    private static long ultimoQuadro = System.currentTimeMillis();

    private static final int W = 124;

    public static final IGuiOverlay HUD = (ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !ClientRingData.hasRing) return;
        var font = Hud.font();
        int color = ClientRingData.color;
        int x = 6, y = 6;
        int max = Math.max(1, ClientRingData.maxCharge);
        int charge = Mth.clamp(ClientRingData.charge, 0, max);
        int percent = charge * 100 / max;
        boolean low = percent <= 15;
        boolean blink = mc.level != null && (mc.level.getGameTime() / 8) % 2 == 0;

        // etiquetas (só quando têm algo a dizer)
        java.util.List<Component> tags = new java.util.ArrayList<>();
        if (!ClientRingData.active) tags.add(Component.translatable("hud.lanterna.tag.desligado"));
        else if (ClientRingData.flight) tags.add(Component.translatable("hud.lanterna.tag.voo"));
        if (ClientRingData.pocketStatus == 0) tags.add(Component.translatable("hud.lanterna.tag.bolso"));
        else if (ClientRingData.pocketStatus > 0) tags.add(Component.translatable("hud.lanterna.tag.bolso_espera", ClientRingData.pocketStatus));
        int h = 30 + (tags.isEmpty() ? 0 : 11);
        Hud.panel(g, x, y, W, h, color, 0.85f);
        x += 7;

        // linha 1: cor da Tropa + nome curto · nível
        int xp = ClientLoreData.DATA.xp();
        int nivel = com.lanterna.tropa.Nivel.nivel(xp);
        g.fill(x, y + 6, x + 5, y + 11, Hud.argb(ClientRingData.active ? color : 0x606870, 255));
        Component nv = Hud.bold(Component.translatable("hud.lanterna.nv", nivel));
        Component name = Hud.caps(Component.translatable(CorpsDefinition.nameKey(ClientRingData.corps)));
        Hud.textFit(g, name, x + 8, y + 5, W - 22 - font.width(nv), Hud.argb(color, 255), false);
        Hud.textRight(g, nv, 6 + W - 6, y + 5, 0xFFFFD870);

        // linha 2: bateria (barra suave; o que foi gasto fica um instante em branco atrás)
        int bw = W - 14 - 30;
        float alvo = charge / (float) max;
        long agora = System.currentTimeMillis();
        float dt = Math.min(0.1f, (agora - ultimoQuadro) / 1000f);
        ultimoQuadro = agora;
        suave += (alvo - suave) * Math.min(1f, dt * 10f);
        if (alvo >= rastro) rastro = alvo; else rastro += (alvo - rastro) * Math.min(1f, dt * 2.5f);
        int by = y + 16;
        Hud.bar(g, x, by, bw, 6, suave, low && blink ? 0xFF4040 : color, true);
        if (rastro > suave + 0.002f) g.fill(x + Math.round(bw * suave), by, x + Math.round(bw * rastro), by + 6, 0x90FFFFFF);
        com.lanterna.client.fonte.ClientFonte.barraSobrecarga(g, x, by, bw, max, color);
        int extra = com.lanterna.client.fonte.ClientFonte.sobrecarga * 100 / max;
        Hud.textRight(g, Hud.bold(Component.literal((percent + extra) + "%")), 6 + W - 6, by - 1,
                extra > 0 ? Hud.argb(com.lanterna.client.construct.EnergyShapes.clarear(color, 0.6f), 255) : low && blink ? 0xFFFF5050 : 0xFFFFFFFF);

        // experiência: um fio dourado embaixo da bateria
        g.fill(x, by + 9, x + bw, by + 10, 0x40FFD870);
        g.fill(x, by + 9, x + Math.round(bw * com.lanterna.tropa.Nivel.progresso(xp)), by + 10, 0xFFFFD040);

        // etiquetas
        if (!tags.isEmpty()) {
            int tx = x;
            for (Component t : tags) {
                Component tc = Hud.body(t);
                int tw = font.width(tc) + 6;
                if (tx + tw > 6 + W - 4) break;
                g.fill(tx, y + h - 12, tx + tw, y + h - 3, Hud.argb(color, 60));
                g.drawString(font, tc, tx + 3, y + h - 11, 0xFFE0E6EA, false);
                tx += tw + 3;
            }
        }
        if (low) {
            Component warn = Hud.bold(Component.translatable("hud.lanterna.low_charge"));
            g.drawString(font, warn, 6, y + h + 3, blink ? 0xFFFF4040 : 0xFF902020, true);
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
