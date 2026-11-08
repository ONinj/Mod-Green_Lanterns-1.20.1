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
    private static float suave = 1f, rastro = 1f;
    private static long ultimoQuadro = System.currentTimeMillis();

    private static final int W = 170;

    public static final IGuiOverlay HUD = (ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !ClientRingData.hasRing) return;
        var font = Hud.font();
        int color = ClientRingData.color;
        int x = 6, y = 6;
        // Voo e bateria de bolso
        Component status;
        if (ClientRingData.pocketStatus == 0) status = Component.translatable("hud.lanterna.pocket_ready");
        else if (ClientRingData.pocketStatus > 0) status = Component.translatable("hud.lanterna.pocket_cooldown", ClientRingData.pocketStatus);
        else status = Component.empty();
        Component flight = ClientRingData.active && ClientRingData.flight ? Component.translatable("hud.lanterna.flight_on") : Component.empty();
        Component line = flight.getString().isEmpty() ? status
                : status.getString().isEmpty() ? flight : flight.copy().append(Component.literal("  ·  ")).append(status);
        java.util.List<net.minecraft.util.FormattedCharSequence> linhas = font.split(Hud.body(line), W - 16);
        int extraH = Math.max(0, linhas.size() - 1) * 10;
        Hud.panel(g, x, y, W, 76 + extraH, color, 0.92f);
        x += 8;

        // Nome da Tropa
        Component name = Hud.caps(Component.translatable(CorpsDefinition.fullNameKey(ClientRingData.corps)));
        if (font.width(name) > W - 16) name = Hud.caps(Component.translatable(CorpsDefinition.nameKey(ClientRingData.corps)));
        int offW = ClientRingData.active ? 0 : font.width(Hud.body(Component.translatable("hud.lanterna.power_off"))) + 6;
        Hud.textFit(g, name, x, y + 5, W - 16 - offW, Hud.argb(color, 255), false);
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
        // barra suave: a carga desliza até o valor novo, e o que foi gasto fica um instante em branco atrás
        float alvo = charge / (float) max;
        long agora = System.currentTimeMillis();
        float dt = Math.min(0.1f, (agora - ultimoQuadro) / 1000f);
        ultimoQuadro = agora;
        suave += (alvo - suave) * Math.min(1f, dt * 10f);
        if (alvo >= rastro) rastro = alvo; else rastro += (alvo - rastro) * Math.min(1f, dt * 2.5f);
        if (rastro > suave + 0.002f) {
            int rx0 = x + 44 + Math.round(86 * suave), rx1 = x + 44 + Math.round(86 * rastro);
            g.fill(rx0, y + 19, rx1, y + 24, 0xB0FFFFFF);
        }
        Hud.bar(g, x + 44, y + 19, 86, 5, suave, low && blink ? 0xFF4040 : color, true);
        if (rastro > suave + 0.002f) g.fill(x + 44 + Math.round(86 * suave), y + 19, x + 44 + Math.round(86 * rastro), y + 24, 0x90FFFFFF);
        com.lanterna.client.fonte.ClientFonte.barraSobrecarga(g, x + 44, y + 19, 86, max, color);
        int extra = com.lanterna.client.fonte.ClientFonte.sobrecarga * 100 / max;
        Hud.textRight(g, Hud.bold(Component.literal(extra > 0 ? (percent + extra) + "%" : percent + "%")), 6 + W - 6, y + 17,
                extra > 0 ? Hud.argb(com.lanterna.client.construct.EnergyShapes.clarear(color, 0.6f), 255) : low && blink ? 0xFFFF5050 : 0xFFFFFFFF);

        // Afinidade
        int pts = ClientLoreData.DATA.affinity();
        int lvl = Affinity.level(pts);
        g.drawString(font, Hud.body(Component.translatable("hud.lanterna.affinity")), x, y + 28, 0xFF9AA8B0, false);
        StringBuilder pips = new StringBuilder();
        for (int i = 1; i <= Affinity.MAX_LEVEL; i++) pips.append(i <= lvl ? "◆" : "◇");
        g.drawString(font, Component.literal(pips.toString()), x + 44, y + 28, Hud.argb(color, 255), false);
        Component lname = Hud.fit(Hud.body(Affinity.levelName(lvl)), W - 14 - 44 - font.width(pips.toString()) - 4);
        Hud.textRight(g, lname, 6 + W - 6, y + 28, 0xFFD8DEE4);
        g.fill(x + 44, y + 37, x + 44 + (int) (48 * Affinity.progress(pts)), y + 38, Hud.argb(color, 200));

        // Nível do Lanterna e posto
        int xp = ClientLoreData.DATA.xp();
        int nivel = com.lanterna.tropa.Nivel.nivel(xp);
        g.drawString(font, Hud.body(Component.translatable("hud.lanterna.nivel", nivel)), x, y + 40, 0xFFFFD870, false);
        int postoW = Math.min(font.width(Hud.body(Component.translatable("posto.lanterna." + com.lanterna.tropa.Nivel.posto(nivel)))), W - 14 - 44 - 24);
        Hud.bar(g, x + 44, y + 42, Math.max(20, Math.min(50, W - 14 - 44 - postoW - 6)), 4, com.lanterna.tropa.Nivel.progresso(xp), 0xFFD040, false);
        // o posto vai à direita da barra de nível; se o nome for longo a barra encolhe
        Component posto = Hud.body(Component.translatable("posto.lanterna." + com.lanterna.tropa.Nivel.posto(nivel)));
        posto = Hud.fit(posto, W - 14 - 44 - 24);
        Hud.textRight(g, posto, 6 + W - 6, y + 40, 0xFFD8DEE4);

        // Planeta da Tropa e distância
        Hud.textFit(g, home(mc), x, y + 52, W - 16, 0xFFE0E6EA, false);

        for (int i = 0; i < linhas.size(); i++) g.drawString(font, linhas.get(i), x, y + 64 + i * 10, low && blink ? 0xFFFF5050 : 0xFFB8C4CC, false);
        if (low) {
            Component warn = Hud.bold(Component.translatable("hud.lanterna.low_charge"));
            g.drawString(font, warn, 6, y + 80 + extraH, blink ? 0xFFFF4040 : 0xFF902020, true);
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
