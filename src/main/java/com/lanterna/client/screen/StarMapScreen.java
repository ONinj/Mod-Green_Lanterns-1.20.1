package com.lanterna.client.screen;

import com.lanterna.client.ClientRingData;
import com.lanterna.client.flight.Autopilot;
import com.lanterna.client.flight.Destinations;
import com.lanterna.client.flight.FlightHud;
import com.lanterna.space.SpaceBodies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * Mapa estelar (tecla J): mostra a Terra e os planetas vistos de cima, onde você está,
 * e a lista de destinos com distância e tempo estimado. Escolha um e clique em "Voo automático".
 *
 * Mapa: roda do mouse = zoom, arrastar = mover, clique num planeta = escolher.
 */
public class StarMapScreen extends Screen {
    private static final int LIST_W = 150;

    private int left, top, panelW, panelH, mapX, mapY, mapW, mapH, listX;
    private double viewX, viewZ, zoom; // zoom = blocos por pixel
    private String selected;
    private int listScroll;
    private boolean dragging;
    private Button goButton, stopButton;
    private List<Destinations.Destination> destinations;

    public StarMapScreen() {
        super(Component.translatable("starmap.lanterna.title"));
    }

    @Override
    protected void init() {
        panelW = Math.min(440, width - 20);
        panelH = Math.min(260, height - 20);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        mapX = left + 8;
        mapY = top + 22;
        mapW = panelW - LIST_W - 24;
        mapH = panelH - 54;
        listX = mapX + mapW + 8;

        destinations = Destinations.all();
        Vec3 here = here();
        sortByDistance(here);
        if (zoom == 0) {
            viewX = here.x;
            viewZ = here.z;
            double far = 1000;
            for (var d : destinations) far = Math.max(far, Math.hypot(d.center().x - here.x, d.center().z - here.z));
            zoom = Math.min(far * 1.1 / (Math.min(mapW, mapH) / 2.0), 400);
            if (Autopilot.active() && Autopilot.target() != null) selected = Autopilot.target().id();
        }

        int by = top + panelH - 26;
        goButton = addRenderableWidget(Button.builder(Component.translatable("starmap.lanterna.go"), b -> go())
                .bounds(listX, by, LIST_W, 20).build());
        stopButton = addRenderableWidget(Button.builder(Component.translatable("starmap.lanterna.stop"), b -> {
            Autopilot.stop(Component.translatable("autopilot.lanterna.cancelled"));
            onClose();
        }).bounds(mapX, by, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(mapX + mapW - 70, by, 70, 20).build());
        updateButtons();
    }

    private Vec3 here() {
        return Minecraft.getInstance().player != null ? Destinations.spacePosition(Minecraft.getInstance().player) : Vec3.ZERO;
    }

    private void sortByDistance(Vec3 here) {
        destinations.sort(Comparator.comparingDouble(d -> d.center().distanceTo(here) - d.radius()));
    }

    private void updateButtons() {
        var player = Minecraft.getInstance().player;
        Destinations.Destination d = selected == null ? null : Destinations.byId(selected);
        goButton.active = d != null && player != null && !Destinations.isHere(player, d)
                && ClientRingData.hasRing && ClientRingData.active;
        stopButton.visible = Autopilot.active();
    }

    private void go() {
        Destinations.Destination d = Destinations.byId(selected);
        Component error = Autopilot.start(d);
        if (error != null) {
            if (minecraft != null && minecraft.player != null) minecraft.player.displayClientMessage(error, true);
        }
        onClose();
    }

    // ===================== Desenho =====================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int accent = 0xFF000000 | ClientRingData.color;
        g.fill(left - 1, top - 1, left + panelW + 1, top + panelH + 1, accent);
        g.fill(left, top, left + panelW, top + panelH, 0xF0080B14);
        g.drawString(font, title, left + 8, top + 7, accent, false);
        Component hint = Component.translatable("starmap.lanterna.hint");
        g.drawString(font, hint, left + panelW - 8 - font.width(hint), top + 7, 0xFF7A8499, false);

        renderMap(g, mouseX, mouseY);
        renderList(g, mouseX, mouseY);
        updateButtons();
        super.render(g, mouseX, mouseY, partialTick);
    }

    private int sx(double x) { return mapX + mapW / 2 + (int) Math.round((x - viewX) / zoom); }
    private int sy(double z) { return mapY + mapH / 2 + (int) Math.round((z - viewZ) / zoom); }

    private void renderMap(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0xFF02040A);
        g.enableScissor(mapX, mapY, mapX + mapW, mapY + mapH);

        // Estrelas de fundo (fixas na tela)
        for (int i = 0; i < 90; i++) {
            int x = mapX + (int) (hash(i * 3 + 1) * mapW), y = mapY + (int) (hash(i * 7 + 2) * mapH);
            int b = 60 + (int) (hash(i * 11 + 5) * 120);
            g.fill(x, y, x + 1, y + 1, 0xFF000000 | (b << 16) | (b << 8) | Math.min(255, b + 30));
        }

        // Grade
        double step = gridStep();
        double x0 = Math.floor((viewX - mapW / 2.0 * zoom) / step) * step;
        for (double x = x0; sx(x) < mapX + mapW; x += step) g.fill(sx(x), mapY, sx(x) + 1, mapY + mapH, 0x18FFFFFF);
        double z0 = Math.floor((viewZ - mapH / 2.0 * zoom) / step) * step;
        for (double z = z0; sy(z) < mapY + mapH; z += step) g.fill(mapX, sy(z), mapX + mapW, sy(z) + 1, 0x18FFFFFF);

        Vec3 here = here();
        Destinations.Destination sel = selected == null ? null : Destinations.byId(selected);

        // Rota até o destino escolhido
        if (sel != null) {
            int ax = sx(here.x), ay = sy(here.z), bx = sx(sel.center().x), by = sy(sel.center().z);
            int steps = Math.max(1, (int) Math.hypot(bx - ax, by - ay) / 4);
            int c = 0xFF000000 | ClientRingData.color;
            for (int i = 0; i < steps; i += 2) {
                int x = ax + (bx - ax) * i / steps, y = ay + (by - ay) * i / steps;
                g.fill(x, y, x + 2, y + 1, c);
            }
        }

        // Planetas
        for (var d : destinations) {
            int x = sx(d.center().x), y = sy(d.center().z);
            int r = Math.max(3, (int) (d.radius() / zoom));
            disc(g, x, y, r, 0xFF000000 | darken(d.color(), 0.55f));
            disc(g, x - r / 4, y - r / 4, Math.max(1, r * 2 / 3), 0xFF000000 | d.color());
            if (d.id().equals(selected)) ring(g, x, y, r + 3, 0xFFFFFFFF);
            boolean hover = Math.hypot(mouseX - x, mouseY - y) <= r + 3;
            if (hover && !d.id().equals(selected)) ring(g, x, y, r + 3, 0x88FFFFFF);
            Component name = d.name();
            g.drawString(font, name, x - font.width(name) / 2, y + r + 3, hover || d.id().equals(selected) ? 0xFFFFFFFF : 0xFFB8C0D0, true);
        }

        // Você
        int px = sx(here.x), py = sy(here.z);
        float yaw = minecraft != null && minecraft.player != null ? minecraft.player.getYRot() : 0;
        double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
        for (int i = 0; i < 7; i++) {
            int x = px + (int) Math.round(fx * i), y = py + (int) Math.round(fz * i);
            g.fill(x - 1, y - 1, x + 1, y + 1, 0xFFFFFFFF);
        }
        g.fill(px - 2, py - 2, px + 3, py + 3, 0xFFFFFFFF);
        Component you = Component.translatable("starmap.lanterna.you");
        g.drawString(font, you, px - font.width(you) / 2, py - 12, 0xFFFFFFFF, true);

        g.disableScissor();

        // Escala
        String scale = FlightHud.formatDistance(step);
        g.fill(mapX + 6, mapY + mapH - 8, mapX + 6 + (int) (step / zoom), mapY + mapH - 7, 0xAAFFFFFF);
        g.drawString(font, scale, mapX + 6, mapY + mapH - 18, 0xAAFFFFFF, false);
    }

    private double gridStep() {
        double target = zoom * 60;
        double[] steps = {100, 250, 500, 1000, 2500, 5000, 10000, 25000, 50000, 100000};
        for (double s : steps) if (s >= target) return s;
        return 250000;
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        int y = mapY;
        int rowH = 24;
        int visible = Math.max(1, (mapH) / rowH);
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, destinations.size() - visible));
        Vec3 here = here();
        var player = Minecraft.getInstance().player;
        g.enableScissor(listX, mapY, listX + LIST_W, mapY + mapH);
        for (int i = listScroll; i < destinations.size() && i < listScroll + visible; i++) {
            var d = destinations.get(i);
            boolean sel = d.id().equals(selected);
            boolean hover = mouseX >= listX && mouseX < listX + LIST_W && mouseY >= y && mouseY < y + rowH - 2;
            g.fill(listX, y, listX + LIST_W, y + rowH - 2, sel ? 0x55FFFFFF : hover ? 0x2AFFFFFF : 0x14FFFFFF);
            disc(g, listX + 8, y + 7, 4, 0xFF000000 | d.color());
            g.drawString(font, d.name(), listX + 16, y + 3, 0xFFFFFFFF, false);
            boolean isHere = player != null && Destinations.isHere(player, d);
            Component info;
            if (isHere) {
                info = Component.translatable("starmap.lanterna.here");
            } else {
                double dist = Math.max(0, d.center().distanceTo(here) - d.radius());
                info = Component.literal(FlightHud.formatDistance(dist) + " · ")
                        .append(Component.translatable("starmap.lanterna.eta", eta(dist, player)));
            }
            g.drawString(font, info, listX + 16, y + 13, 0xFF8C96AA, false);
            y += rowH;
        }
        g.disableScissor();
    }

    /** Tempo estimado de viagem, em segundos. */
    private static int eta(double dist, net.minecraft.world.entity.player.Player player) {
        double seconds;
        // Hiperpropulsão: ~3 s para acelerar, ~1.700 blocos/s, ~2 s freando
        if (dist > 900) seconds = 5 + dist / 1700.0;
        else seconds = dist / 110.0;
        if (player != null && !player.level().dimension().equals(SpaceBodies.SPACE)) seconds += 12;
        return (int) Math.ceil(seconds + 6);
    }

    // ===================== Mouse =====================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        // Lista
        if (mx >= listX && mx < listX + LIST_W && my >= mapY && my < mapY + mapH) {
            int idx = listScroll + (int) ((my - mapY) / 24);
            if (idx >= 0 && idx < destinations.size()) select(destinations.get(idx));
            return true;
        }
        // Mapa
        if (inMap(mx, my)) {
            for (var d : destinations) {
                int x = sx(d.center().x), y = sy(d.center().z);
                int r = Math.max(3, (int) (d.radius() / zoom));
                if (Math.hypot(mx - x, my - y) <= r + 4) {
                    select(d);
                    return true;
                }
            }
            dragging = true;
            return true;
        }
        return false;
    }

    private void select(Destinations.Destination d) {
        selected = d.id();
        if (minecraft != null) minecraft.getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.4f));
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            viewX -= dx * zoom;
            viewZ -= dy * zoom;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (inMap(mx, my)) {
            double wx = viewX + (mx - mapX - mapW / 2.0) * zoom, wz = viewZ + (my - mapY - mapH / 2.0) * zoom;
            zoom = Mth.clamp(zoom * (delta > 0 ? 0.8 : 1.25), 0.5, 2000);
            viewX = wx - (mx - mapX - mapW / 2.0) * zoom;
            viewZ = wz - (my - mapY - mapH / 2.0) * zoom;
            return true;
        }
        if (mx >= listX && mx < listX + LIST_W) {
            listScroll -= (int) Math.signum(delta);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private boolean inMap(double mx, double my) {
        return mx >= mapX && mx < mapX + mapW && my >= mapY && my < mapY + mapH;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ===================== Desenho de círculos =====================

    private static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int color) {
        int n = Math.max(24, r * 6);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static int darken(int rgb, float k) {
        int r = (int) (((rgb >> 16) & 0xFF) * k), g = (int) (((rgb >> 8) & 0xFF) * k), b = (int) ((rgb & 0xFF) * k);
        return (r << 16) | (g << 8) | b;
    }

    private static double hash(int n) {
        n = (n << 13) ^ n;
        n = n * (n * n * 15731 + 789221) + 1376312589;
        return ((n & 0x7fffffff) / (double) 0x7fffffff);
    }
}
