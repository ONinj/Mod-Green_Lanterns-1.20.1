package com.lanterna.client.space;

import com.lanterna.client.ClientRingData;
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
 * Navegação no espaço (canto superior direito): os 4 corpos mais próximos,
 * com uma seta mostrando para que lado ele está e a distância.
 * Também avisa do oxigênio.
 */
public final class SpaceHud {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    public static final IGuiOverlay HUD = (ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !mc.player.level().dimension().equals(SpaceBodies.SPACE)) return;
        Font font = mc.font;
        Vec3 pos = mc.player.position();

        List<SpaceBodies.Body> list = new ArrayList<>();
        for (SpaceBodies.Body b : SpaceBodies.BODIES) if (b.landable()) list.add(b);
        list.sort(Comparator.comparingDouble(b -> b.center().distanceTo(pos) - b.radius()));

        int x = width - 8;
        int y = 8;
        Component title = Component.translatable("space.lanterna.hud.title");
        g.drawString(font, title, x - font.width(title), y, 0xFFB8C8FF, true);
        y += 12;

        for (int i = 0; i < Math.min(4, list.size()); i++) {
            SpaceBodies.Body b = list.get(i);
            Vec3 rel = b.center().subtract(pos);
            double dist = Math.max(0, rel.length() - b.radius());
            float targetYaw = (float) Math.toDegrees(Math.atan2(-rel.x, rel.z));
            float diff = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
            String arrow = ARROWS[Math.floorMod(Math.round(diff / 45f), 8)];
            Component line = Component.literal(arrow + " ").append(Component.translatable(b.nameKey()))
                    .append(Component.literal(String.format(" %,d m", (int) dist)));
            int color = 0xFF000000 | b.atmosphere();
            g.drawString(font, line, x - font.width(line), y, color, true);
            y += 10;
        }

        // Oxigênio
        boolean safe = mc.player.isCreative() || (ClientRingData.hasRing && ClientRingData.active);
        Component oxygen = Component.translatable(safe ? "space.lanterna.hud.oxygen_ok" : "space.lanterna.hud.oxygen_none");
        boolean blink = (mc.player.tickCount / 8) % 2 == 0;
        g.drawString(font, oxygen, x - font.width(oxygen), y + 4, safe ? 0xFF9FE0A0 : (blink ? 0xFFFF4040 : 0xFF802020), true);
    };

    private SpaceHud() {}
}
