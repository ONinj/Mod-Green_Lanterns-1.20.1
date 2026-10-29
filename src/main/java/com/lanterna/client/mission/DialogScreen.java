package com.lanterna.client.mission;

import com.lanterna.client.hud.Hud;
import com.lanterna.lore.entity.Race;
import com.lanterna.mission.Mission;
import com.lanterna.mission.Missions;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.lore.DialogActionPacket;
import com.lanterna.network.lore.OpenDialogPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Conversa com um NPC: retrato animado, nome, falas aparecendo letra por letra e botões.
 * Veltari falam por pensamento (texto em itálico); Ves'Kai falam em símbolos para quem não tem o anel índigo.
 */
public class DialogScreen extends Screen {
    private static final int W = 380, H = 168;
    private final OpenDialogPacket msg;
    private final List<Component> lines = new ArrayList<>();
    private int ticks;

    public static void open(OpenDialogPacket msg) {
        Minecraft.getInstance().setScreen(new DialogScreen(msg));
    }

    public DialogScreen(OpenDialogPacket msg) {
        super(Component.translatable("race.lanterna." + msg.race()));
        this.msg = msg;
        for (String raw : msg.lines()) lines.add(line(raw));
    }

    private Component line(String raw) {
        String key = raw;
        Object arg = null;
        int bar = raw.indexOf('|');
        if (bar > 0) {
            key = raw.substring(0, bar);
            String a = raw.substring(bar + 1);
            if (a.startsWith("item:")) {
                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(a.substring(5)));
                arg = item != null ? item.getDescription() : Component.literal("?");
            }
        }
        MutableComponent c = arg != null ? Component.translatable(key, arg) : Component.translatable(key);
        if (msg.race().equals("veltari")) c = Component.literal("≈ ").append(c).append(" ≈").withStyle(s -> s.withItalic(true));
        return c;
    }

    private int color() {
        return switch (msg.race()) {
            case "brakkari" -> 0xFF5A3A;
            case "ghallos" -> 0xFFB040;
            case "skrell_vath" -> 0xFFE01A;
            case "veltari", "guardiao" -> 0x4AFF7A;
            case "aelinos" -> 0x6FB8FF;
            case "ves_kai" -> 0x8A7BFF;
            case "zaelith" -> 0xF080FF;
            default -> 0xC8C8D0;
        };
    }

    @Override
    protected void init() {
        int left = (width - W) / 2, top = height - H - 24;
        int bx = left + 112, by = top + H - 26;
        int bw = (W - 120) / Math.max(2, msg.options().size()) - 4;
        bw = Math.min(bw, 120);
        for (String opt : msg.options()) {
            Component label = optionLabel(opt);
            addRenderableWidget(Button.builder(label, b -> choose(opt)).bounds(bx, by, Math.max(bw, Math.min(150, font.width(label) + 12)), 20).build());
            bx += Math.max(bw, Math.min(150, font.width(label) + 12)) + 4;
        }
    }

    private Component optionLabel(String opt) {
        if (opt.startsWith("accept:")) {
            Mission m = Missions.get(opt.substring(7));
            return Component.translatable("dialog.lanterna.option.accept", m != null ? m.title() : Component.literal("?"));
        }
        return Component.translatable("dialog.lanterna.option." + opt);
    }

    private void choose(String opt) {
        if (!opt.equals("bye")) ModNetwork.sendToServer(new DialogActionPacket(msg.entityId(), opt));
        onClose();
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        int left = (width - W) / 2, top = height - H - 24;
        g.fillGradient(0, height / 2, width, height, 0x00000000, 0xA0000000);
        int color = color();
        Hud.panel(g, left, top, W, H, color, 1f);
        // Retrato
        g.fill(left + 8, top + 8, left + 104, top + H - 8, 0x60000000);
        Entity e = minecraft.level != null ? minecraft.level.getEntity(msg.entityId()) : null;
        if (e instanceof LivingEntity le) {
            float h = Math.max(1.2f, le.getBbHeight());
            int scale = (int) Math.min(48, 95 / h);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, left + 56, top + H - 16, scale,
                    left + 56 - mouseX, top + 50 - mouseY, le);
        }
        // Nome
        Component name = e != null ? e.getDisplayName() : title;
        g.drawString(font, Hud.caps(name), left + 112, top + 9, Hud.argb(color, 255), false);
        Component sub = Component.translatable("race.lanterna." + msg.race())
                .append(Component.literal(" · ")).append(Component.translatable("planet.lanterna." + msg.planet()));
        g.drawString(font, Hud.body(sub), left + 112, top + 20, 0xFF8A98A4, false);
        // Falas (letra por letra)
        int budget = (int) ((ticks + pt) * 2.2f);
        int y = top + 34;
        int maxW = W - 122;
        for (Component line : lines) {
            Component shown = line;
            if (msg.symbols()) shown = line.copy().withStyle(Style.EMPTY.withFont(new ResourceLocation("minecraft", "alt")));
            String s = shown.getString();
            if (budget <= 0) break;
            Component partial = budget >= s.length() ? shown : Component.literal(s.substring(0, budget)).withStyle(shown.getStyle());
            budget -= s.length();
            y += Hud.wrapped(g, Hud.body(partial), left + 112, y, maxW, 0xFFE8ECEF, 10) + 4;
            if (y > top + H - 34) break;
        }
        if (msg.symbols()) {
            Hud.wrapped(g, Hud.body(Component.translatable("dialog.lanterna.ves_kai.gesture")), left + 112, Math.min(y, top + H - 44),
                    maxW, 0xFF9A90C8, 10);
        }
        super.render(g, mouseX, mouseY, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Raças "neutras" ficam com o nome traduzido. */
    public static Component raceName(Race r) {
        return Component.translatable("race.lanterna." + r.id);
    }
}
