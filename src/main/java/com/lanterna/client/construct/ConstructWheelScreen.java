package com.lanterna.client.construct;

import com.lanterna.LanternaMod;
import com.lanterna.client.ClientRingData;
import com.lanterna.client.KeyBindings;
import com.lanterna.client.hud.Hud;
import com.lanterna.construct.Affinity;
import com.lanterna.construct.ConstructType;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.lore.ConstructSelectPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Roda de construtos (segure Z): os construtos em volta de um círculo, coloridos pela categoria.
 * Passe o mouse para ver o que cada um faz; soltar Z (ou clicar) escolhe o construto da tecla X;
 * com o mouse em cima, as teclas 1 a 4 gravam o construto num preset (Alt+1..4 usa na hora).
 */
public class ConstructWheelScreen extends Screen {
    private static final int OUTER = 92, INNER = 38;
    private ConstructType hovered;
    private float open;
    private int flash = -1;
    private long flashUntil;

    public ConstructWheelScreen() {
        super(Component.translatable("construct.lanterna.wheel"));
    }

    public static ResourceLocation icon(ConstructType t) {
        return LanternaMod.id("textures/gui/construct/" + t.id + ".png");
    }

    /** Só para o teste automático: mantém a roda aberta sem o Z apertado. */
    public static boolean pinned;

    @Override
    public void tick() {
        open = Math.min(1f, open + 0.25f);
        // Soltou o Z: escolhe o que está embaixo do mouse
        if (!pinned && !InputConstants.isKeyDown(minecraft.getWindow().getWindow(), KeyBindings.CONSTRUCT_WHEEL.getKey().getValue())) {
            if (hovered != null) select(hovered);
            onClose();
        }
    }

    private void select(ConstructType t) {
        if (ClientConstructs.level() < t.unlockLevel) return;
        ModNetwork.sendToServer(new ConstructSelectPacket(-1, t.id));
        ClientConstructs.data().setSelected(t.id);
        if (minecraft.player != null) minecraft.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.6f);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int cx = width / 2, cy = height / 2;
        int color = ClientRingData.color;
        float k = Mth.clamp(open + pt * 0.25f, 0, 1);
        g.fillGradient(0, 0, width, height, 0x60000000, 0x90000000);
        disc(g, cx, cy, (int) (OUTER * 1.18f * k), Hud.argb(0x0A0E12, 170));
        ring(g, cx, cy, (int) (OUTER * 1.18f * k), Hud.argb(color, 160));
        ring(g, cx, cy, (int) (INNER * k), Hud.argb(color, 90));

        ConstructType[] all = ConstructType.daRoda(ClientRingData.corps).toArray(new ConstructType[0]);
        double dx = mx - cx, dy = my - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        hovered = null;
        if (dist > INNER * 0.6) {
            double ang = Math.atan2(dy, dx) + Math.PI / 2;
            if (ang < 0) ang += Math.PI * 2;
            int idx = (int) Math.round(ang / (Math.PI * 2 / all.length)) % all.length;
            hovered = all[idx];
        }
        int lvl = ClientConstructs.level();
        ConstructType sel = ClientConstructs.selected();
        for (int i = 0; i < all.length; i++) {
            ConstructType t = all[i];
            double a = i * Math.PI * 2 / all.length - Math.PI / 2;
            int r = (int) (OUTER * 0.78f * k);
            int ix = cx + (int) (Math.cos(a) * r), iy = cy + (int) (Math.sin(a) * r);
            boolean locked = lvl < t.unlockLevel;
            boolean hov = t == hovered;
            int size = hov ? 30 : 24;
            int frame = locked ? 0x404448 : t.corps != null ? color : t.category.color;
            g.fill(ix - size / 2 - 2, iy - size / 2 - 2, ix + size / 2 + 2, iy + size / 2 + 2, Hud.argb(frame, hov ? 230 : 120));
            g.fill(ix - size / 2, iy - size / 2, ix + size / 2, iy + size / 2, Hud.argb(0x101418, 235));
            if (t == sel) g.fill(ix - size / 2, iy + size / 2 - 2, ix + size / 2, iy + size / 2, Hud.argb(color, 255));
            g.pose().pushPose();
            g.pose().translate(ix - size / 2f + 2, iy - size / 2f + 2, 0);
            float s = (size - 4) / 32f;
            g.pose().scale(s, s, 1);
            if (locked) g.setColor(0.35f, 0.35f, 0.35f, 1f);
            g.blit(icon(t), 0, 0, 0, 0, 32, 32, 32, 32);
            g.setColor(1f, 1f, 1f, 1f);
            g.pose().popPose();
            // Número do preset (se este construto estiver num)
            for (int p = 0; p < 4; p++) {
                if (t.id.equals(ClientConstructs.data().preset(p))) {
                    boolean fl = flash == p && System.currentTimeMillis() < flashUntil;
                    g.drawString(font, Component.literal(String.valueOf(p + 1)), ix + size / 2 - 5, iy - size / 2 - 1,
                            fl ? 0xFFFFFFFF : 0xFFFFD35A, true);
                }
            }
        }

        // Centro: o que o construto faz
        ConstructType info = hovered != null ? hovered : sel;
        Component name = Hud.caps(info.displayName());
        g.drawString(font, name, cx - font.width(name) / 2, cy - 22, Hud.argb(info.category.color, 255), false);
        Component cat = Hud.body(info.category.label());
        g.drawString(font, cat, cx - font.width(cat) / 2, cy - 11, 0xFF9AA8B0, false);
        Component cost = Hud.body(Component.translatable(info.hold ? "construct.lanterna.cost_hold" : "construct.lanterna.cost",
                info.cost, String.format(java.util.Locale.ROOT, "%.1f", info.cooldown / 20f)));
        g.drawString(font, cost, cx - font.width(cost) / 2, cy + 1, 0xFFD8DEE4, false);
        if (lvl < info.unlockLevel) {
            Component lock = Hud.body(Component.translatable("construct.lanterna.locked", Affinity.levelName(info.unlockLevel)));
            g.drawString(font, lock, cx - font.width(lock) / 2, cy + 12, 0xFFFF7070, false);
        }

        // Descrição e presets embaixo da roda
        int by = cy + (int) (OUTER * 1.18f) + 8;
        int bw = 300;
        Hud.panel(g, cx - bw / 2, by, bw, 52, color, 0.95f);
        Hud.wrapped(g, Hud.body(info.description()), cx - bw / 2 + 8, by + 5, bw - 16, 0xFFE0E6EA, 10);
        StringBuilder presets = new StringBuilder();
        for (int p = 0; p < 4; p++) {
            ConstructType t = ConstructType.byId(ClientConstructs.data().preset(p));
            presets.append("Alt+").append(p + 1).append(": ").append(t != null ? t.displayName().getString() : "-").append("   ");
        }
        g.drawString(font, Hud.body(Component.literal(presets.toString().trim())), cx - bw / 2 + 8, by + 30, 0xFFFFD35A, false);
        g.drawString(font, Hud.body(Component.translatable("construct.lanterna.wheel_help")), cx - bw / 2 + 8, by + 40, 0xFF7A8A94, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (hovered != null) {
            select(hovered);
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (hovered != null && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_4) {
            int p = key - GLFW.GLFW_KEY_1;
            if (ClientConstructs.level() >= hovered.unlockLevel) {
                ModNetwork.sendToServer(new ConstructSelectPacket(p, hovered.id));
                ClientConstructs.data().setPreset(p, hovered.id);
                flash = p;
                flashUntil = System.currentTimeMillis() + 600;
                if (minecraft.player != null) minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.4f);
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** Disco cheio (linhas horizontais). */
    private static void disc(GuiGraphics g, int cx, int cy, int r, int argb) {
        for (int y = -r; y <= r; y++) {
            int hw = (int) Math.sqrt(r * r - y * y);
            g.fill(cx - hw, cy + y, cx + hw, cy + y + 1, argb);
        }
    }

    /** Anel fino. */
    private static void ring(GuiGraphics g, int cx, int cy, int r, int argb) {
        int steps = Math.max(24, r * 3);
        for (int i = 0; i < steps; i++) {
            double a = i * Math.PI * 2 / steps;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, argb);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
