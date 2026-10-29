package com.lanterna.client.mission;

import com.lanterna.client.ClientRingData;
import com.lanterna.client.flight.Autopilot;
import com.lanterna.client.flight.Destinations;
import com.lanterna.client.hud.Hud;
import com.lanterna.construct.Affinity;
import com.lanterna.construct.ConstructType;
import com.lanterna.lore.PlanetLore;
import com.lanterna.lore.entity.Race;
import com.lanterna.mission.Mission;
import com.lanterna.mission.Missions;
import com.lanterna.mission.Objective;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.lore.MissionActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Diário do Lanterna (tecla M): missões (em andamento e concluídas), o respeito de cada povo
 * e a afinidade com o anel (com os construtos liberados em cada nível).
 */
public class JournalScreen extends Screen {
    private static final int W = 400, H = 240;
    private enum Tab { MISSIONS, PEOPLE, RING }

    private Tab tab = Tab.MISSIONS;
    private String selected;
    private int scroll;
    private final List<Mission> list = new ArrayList<>();

    public JournalScreen() {
        super(Component.translatable("journal.lanterna.title"));
    }

    /** Abre direto na aba do anel. */
    public static JournalScreen ringTab() {
        JournalScreen s = new JournalScreen();
        s.tab = Tab.RING;
        return s;
    }

    @Override
    protected void init() {
        list.clear();
        for (String id : ClientLoreData.DATA.active().keySet()) {
            Mission m = Missions.get(id);
            if (m != null) list.add(m);
        }
        List<String> done = new ArrayList<>(ClientLoreData.DATA.completed());
        java.util.Collections.reverse(done);
        for (String id : done) {
            Mission m = Missions.get(id);
            if (m != null && !list.contains(m)) list.add(m);
        }
        if (selected == null) {
            Mission t = ClientLoreData.tracked();
            selected = t != null ? t.id() : (list.isEmpty() ? null : list.get(0).id());
        }
        int left = (width - W) / 2, top = (height - H) / 2;
        int tx = left + 4;
        for (Tab t : Tab.values()) {
            Component label = Component.translatable("journal.lanterna.tab." + t.name().toLowerCase());
            addRenderableWidget(Button.builder(label, b -> {
                tab = t;
                rebuildWidgets();
            }).bounds(tx, top - 22, 90, 20).build());
            tx += 94;
        }
        if (tab == Tab.MISSIONS && selected != null && ClientLoreData.DATA.isActive(selected)) {
            Mission m = Missions.get(selected);
            int bx = left + 140, by = top + H - 26;
            addRenderableWidget(Button.builder(Component.translatable("journal.lanterna.track"), b -> {
                ModNetwork.sendToServer(new MissionActionPacket("track", selected));
                ClientLoreData.DATA.setTracked(selected);
            }).bounds(bx, by, 80, 20).build());
            bx += 84;
            if (m != null && !m.main() && !m.line().equals("inicio")) {
                addRenderableWidget(Button.builder(Component.translatable("journal.lanterna.abandon"), b -> {
                    ModNetwork.sendToServer(new MissionActionPacket("abandon", selected));
                    onClose();
                }).bounds(bx, by, 70, 20).build());
                bx += 74;
            }
            String planet = firstPlanet(m);
            if (planet != null && ClientRingData.hasRing) {
                addRenderableWidget(Button.builder(Component.translatable("journal.lanterna.autopilot"), b -> {
                    Destinations.Destination d = Destinations.byId(planet);
                    Component err = d == null ? Component.translatable("autopilot.lanterna.error.no_target") : Autopilot.start(d);
                    if (err != null && minecraft.player != null) minecraft.player.displayClientMessage(err, true);
                    onClose();
                }).bounds(bx, by, 96, 20).build());
            }
        }
    }

    private String firstPlanet(Mission m) {
        if (m == null) return null;
        for (int i = 0; i < m.objectives().size(); i++) {
            Objective o = m.objectives().get(i);
            if (ClientLoreData.DATA.progress(m.id(), i) < o.count() && o.planet() != null) return o.planet();
        }
        return null;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int left = (width - W) / 2, top = (height - H) / 2;
        int color = ClientRingData.hasRing ? ClientRingData.color : 0x8AA0B0;
        Hud.panel(g, left, top, W, H, color, 1f);
        switch (tab) {
            case MISSIONS -> missions(g, left, top, mx, my);
            case PEOPLE -> people(g, left, top);
            case RING -> ring(g, left, top);
        }
        super.render(g, mx, my, pt);
    }

    private void missions(GuiGraphics g, int left, int top, int mx, int my) {
        int y = top + 8;
        g.drawString(font, Hud.caps(Component.translatable("journal.lanterna.tab.missions")), left + 8, y, 0xFFB8C4CC, false);
        y += 14;
        int rows = (H - 30) / 12;
        for (int i = scroll; i < Math.min(list.size(), scroll + rows); i++) {
            Mission m = list.get(i);
            boolean active = ClientLoreData.DATA.isActive(m.id());
            boolean sel = m.id().equals(selected);
            if (sel) g.fill(left + 4, y - 2, left + 134, y + 10, Hud.argb(m.color(), 60));
            String t = font.plainSubstrByWidth(m.title().getString(), 118);
            g.drawString(font, Hud.body(Component.literal((active ? "◆ " : "✔ ") + t)), left + 8, y,
                    active ? Hud.argb(m.color(), 255) : 0xFF5E6A70, false);
            y += 12;
        }
        g.fill(left + 136, top + 6, left + 137, top + H - 6, 0x30FFFFFF);
        Mission m = Missions.get(selected);
        if (m == null) {
            g.drawString(font, Hud.body(Component.translatable("journal.lanterna.empty")), left + 144, top + 30, 0xFF8A98A4, false);
            return;
        }
        int x = left + 144, w = W - 152;
        y = top + 8;
        g.drawString(font, Hud.caps(Component.translatable("hud.lanterna.mission." + m.line())), x, y, Hud.argb(m.color(), 255), false);
        y += 13;
        y += Hud.wrapped(g, Hud.bold(m.title()), x, y, w, 0xFFFFFFFF, 10) + 4;
        y += Hud.wrapped(g, Hud.body(m.description()), x, y, w, 0xFFC8D0D6, 10) + 6;
        for (int i = 0; i < m.objectives().size(); i++) {
            Objective o = m.objectives().get(i);
            boolean active = ClientLoreData.DATA.isActive(m.id());
            int p = active ? ClientLoreData.DATA.progress(m.id(), i) : o.count();
            boolean done = p >= o.count();
            g.drawString(font, Component.literal(done ? "✔" : "•"), x, y, done ? 0xFF7CFF8A : Hud.argb(m.color(), 255), true);
            y += Hud.wrapped(g, Hud.body(o.label(p)), x + 10, y, w - 10, done ? 0xFF6A7A6E : 0xFFE0E6EA, 10) + 1;
        }
    }

    private void people(GuiGraphics g, int left, int top) {
        int y = top + 8;
        g.drawString(font, Hud.caps(Component.translatable("journal.lanterna.tab.people")), left + 8, y, 0xFFB8C4CC, false);
        y += 16;
        for (PlanetLore p : PlanetLore.values()) {
            if (p == PlanetLore.GENESE) continue;
            Race race = Race.ofPlanet(p.planet);
            String key = race == null ? p.race : race.repKey();
            int rep = ClientLoreData.DATA.rep(key);
            g.drawString(font, Hud.bold(Component.translatable("race.lanterna." + p.race)), left + 10, y, Hud.argb(p.color(), 255), true);
            g.drawString(font, Hud.body(p.planetName()), left + 100, y, 0xFF8A98A4, false);
            int need = Missions.repNeeded(p.race);
            Hud.bar(g, left + 160, y + 2, 90, 5, Math.min(1f, Math.max(0, rep) / (float) Math.max(need, 5)), p.color(), false);
            g.drawString(font, Hud.body(Component.literal(String.valueOf(rep))), left + 256, y, rep < 0 ? 0xFFFF6060 : 0xFFE0E6EA, false);
            g.drawString(font, Hud.body(Component.translatable("journal.lanterna.how." + p.race)), left + 276, y, 0xFF7A8890, false);
            y += 22;
        }
    }

    private void ring(GuiGraphics g, int left, int top) {
        int y = top + 8;
        g.drawString(font, Hud.caps(Component.translatable("journal.lanterna.tab.ring")), left + 8, y, 0xFFB8C4CC, false);
        y += 16;
        if (!ClientRingData.hasRing) {
            g.drawString(font, Hud.body(Component.translatable("journal.lanterna.no_ring")), left + 10, y, 0xFF8A98A4, false);
            return;
        }
        int pts = ClientLoreData.DATA.affinity();
        int lvl = Affinity.level(pts);
        g.drawString(font, Hud.bold(Component.translatable("journal.lanterna.affinity", Affinity.levelName(lvl))), left + 10, y,
                Hud.argb(ClientRingData.color, 255), true);
        y += 12;
        Hud.bar(g, left + 10, y, 200, 6, Affinity.progress(pts), ClientRingData.color, true);
        String next = lvl >= Affinity.MAX_LEVEL ? "MAX" : pts + " / " + Affinity.THRESHOLDS[lvl + 1];
        g.drawString(font, Hud.body(Component.literal(next)), left + 216, y - 1, 0xFFB8C4CC, false);
        y += 12;
        y += Hud.wrapped(g, Hud.body(Component.translatable("journal.lanterna.affinity_help")), left + 10, y, W - 20, 0xFF9AA8B0, 10) + 4;
        // Bônus passivos deste nível
        g.drawString(font, Hud.body(Component.translatable("journal.lanterna.passives",
                Affinity.foodSeconds(lvl), Affinity.regenSeconds(lvl), String.format("%.1f", Affinity.punchBonus(lvl)),
                String.format("%.1f", Affinity.power(pts)))), left + 10, y, Hud.argb(ClientRingData.color, 255), false);
        y += 14;
        for (ConstructType t : ConstructType.values()) {
            boolean ok = lvl >= t.unlockLevel;
            Component line = Component.literal(ok ? "◆ " : "◇ ").append(t.displayName())
                    .append(Component.literal("  ")).append(Component.translatable("journal.lanterna.unlock", Affinity.levelName(t.unlockLevel)));
            int col = (t.ordinal() % 2) * 190;
            g.drawString(font, Hud.body(line), left + 10 + col, y, ok ? Hud.argb(t.category.color, 255) : 0xFF5E6A70, false);
            if (t.ordinal() % 2 == 1) y += 11;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int left = (width - W) / 2, top = (height - H) / 2;
        if (tab == Tab.MISSIONS && mx >= left + 4 && mx <= left + 134 && my >= top + 20) {
            int idx = (int) ((my - top - 20) / 12) + scroll;
            if (idx >= 0 && idx < list.size()) {
                selected = list.get(idx).id();
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, list.size() - 10), scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
