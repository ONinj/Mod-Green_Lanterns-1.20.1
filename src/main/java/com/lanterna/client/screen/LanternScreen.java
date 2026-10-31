package com.lanterna.client.screen;

import com.lanterna.client.ClientRingData;
import com.lanterna.client.hud.Hud;
import com.lanterna.client.visual.PerfilScreen;
import com.lanterna.menu.LanternMenu;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.ToggleFlightPacket;
import com.lanterna.network.TogglePowerPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Tela da Lanterna (tecla K), no estilo dos HUDs do mod:
 *
 *   [Inventário] [Lanterna] [Estatísticas] [Visual]
 *   +----------------------------------------------+
 *   | [personagem]  LANTERNA VERDE                 |
 *   |   (brilho)    [anel]    carga ▮▮▮▮▮▯ 85%     |
 *   |               [bateria] pronta (R)           |
 *   |               [ANEL LIGADO] [VOO]            |
 *   | inventário ...                               |
 *   +----------------------------------------------+
 */
public class LanternScreen extends AbstractContainerScreen<LanternMenu> {
    private float mouseX, mouseY;
    private Toggle powerButton, flightButton;

    public LanternScreen(LanternMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    private int cor() {
        return ClientRingData.hasRing ? ClientRingData.color : 0x8FA3B0;
    }

    @Override
    protected void init() {
        super.init();
        powerButton = addRenderableWidget(new Toggle(leftPos + 80, topPos + 62, 45, 13, () -> ModNetwork.sendToServer(new TogglePowerPacket())));
        flightButton = addRenderableWidget(new Toggle(leftPos + 127, topPos + 62, 44, 13, () -> ModNetwork.sendToServer(new ToggleFlightPacket())));
        updateButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    private void updateButtons() {
        if (powerButton == null) return;
        boolean ring = ClientRingData.hasRing;
        powerButton.active = ring;
        flightButton.active = ring && ClientRingData.active;
        powerButton.on = ring && ClientRingData.active;
        flightButton.on = ring && ClientRingData.flight;
        powerButton.setMessage(Component.translatable(powerButton.on ? "gui.lanterna.button.ring_on" : "gui.lanterna.button.ring_off"));
        flightButton.setMessage(Component.translatable(flightButton.on ? "gui.lanterna.button.flight_on" : "gui.lanterna.button.flight_off"));
        powerButton.setTooltip(Tooltip.create(Component.translatable("gui.lanterna.button.ring_tip")));
        flightButton.setTooltip(Tooltip.create(Component.translatable("gui.lanterna.button.flight_tip")));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    // ===================== Abas =====================

    private static final String[] ABAS = {"gui.lanterna.tab.inventory", "gui.lanterna.tab.lantern", "perfil.lanterna.aba.estatisticas", "perfil.lanterna.aba.visual"};

    private int[] abaX() {
        int[] xs = new int[ABAS.length + 1];
        int x = leftPos;
        for (int i = 0; i < ABAS.length; i++) {
            xs[i] = x;
            x += font.width(Hud.caps(Component.translatable(ABAS[i]))) + 12 + 2;
        }
        xs[ABAS.length] = x;
        return xs;
    }

    private int abaEm(double mx, double my) {
        int[] xs = abaX();
        if (my < topPos - 15 || my >= topPos) return -1;
        for (int i = 0; i < ABAS.length; i++) if (mx >= xs[i] && mx < xs[i + 1] - 2) return i;
        return -1;
    }

    private void abas(GuiGraphics g) {
        int[] xs = abaX();
        int c = cor();
        for (int i = 0; i < ABAS.length; i++) {
            boolean sel = i == 1, hov = abaEm(mouseX, mouseY) == i;
            int x0 = xs[i], x1 = xs[i + 1] - 2;
            g.fill(x0, topPos - (sel ? 16 : 14), x1, topPos, sel ? Hud.argb(0x0A0E12, 235) : hov ? 0xD0182028 : 0xB0101418);
            if (sel) g.fill(x0, topPos - 16, x1, topPos - 15, Hud.argb(c, 255));
            Component nome = Hud.caps(Component.translatable(ABAS[i]));
            g.drawString(font, nome, x0 + 6, topPos - (sel ? 11 : 10), sel ? Hud.argb(c, 255) : 0xFF9AA8B4, false);
        }
    }

    // ===================== Fundo =====================

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        int c = cor();
        abas(g);
        int l = leftPos, t = topPos;
        Hud.panel(g, l, t, imageWidth, imageHeight, c, 0.97f);
        // caixa do personagem com brilho da Tropa
        g.fill(l + 7, t + 7, l + 76, t + 69, 0x60000000);
        g.fillGradient(l + 7, t + 7, l + 76, t + 69, Hud.argb(c, 10), Hud.argb(c, 60));
        g.fill(l + 7, t + 68, l + 76, t + 69, Hud.argb(c, 200));
        // molduras dos slots
        for (Slot s : menu.slots) {
            int x = l + s.x - 1, y = t + s.y - 1;
            boolean equip = s.index < 2;
            g.fill(x, y, x + 18, y + 18, equip ? Hud.argb(c, 70) : 0x30FFFFFF);
            g.fill(x + 1, y + 1, x + 17, y + 17, equip ? Hud.argb(Hud.mix(c, 0x000000, 0.7f), 230) : 0xC0080B0E);
            if (equip) g.fill(x, y + 17, x + 18, y + 18, Hud.argb(c, 230));
        }
        // separador do inventário
        g.fill(l + 7, t + 81, l + imageWidth - 7, t + 82, Hud.argb(c, 50));
        if (minecraft != null && minecraft.player != null) {
            g.enableScissor(l + 7, t + 7, l + 76, t + 68);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, l + 41, t + 66, 28,
                    (float) (l + 41) - this.mouseX, (float) (t + 66 - 46) - this.mouseY, minecraft.player);
            g.disableScissor();
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        int c = cor();
        Component titulo = ClientRingData.hasRing
                ? Hud.caps(Component.translatable(com.lanterna.corps.CorpsDefinition.nameKey(ClientRingData.corps)))
                : Hud.caps(title);
        g.drawString(font, titulo, 82, 6, Hud.argb(c, 255), false);
        int textX = LanternMenu.RING_X + 21;
        g.drawString(font, Hud.bold(Component.translatable("gui.lanterna.slot.ring")), textX, LanternMenu.RING_Y - 1, 0xFFE8EEF2, false);
        if (ClientRingData.hasRing) {
            float f = ClientRingData.charge / (float) Math.max(1, ClientRingData.maxCharge);
            Hud.bar(g, textX, LanternMenu.RING_Y + 9, 50, 3, f, c, true);
            g.drawString(font, Hud.body(Component.literal(Math.round(f * 100) + "%")), textX, LanternMenu.RING_Y + 13 - 0, 0xFFB8C4CC, false);
        } else {
            g.drawString(font, Hud.body(Component.translatable("gui.lanterna.empty")), textX, LanternMenu.RING_Y + 9, 0xFF808A94, false);
        }
        g.drawString(font, Hud.bold(Component.translatable("gui.lanterna.slot.battery")), textX, LanternMenu.BATTERY_Y + 1, 0xFFE8EEF2, false);
        Component batteryInfo = switch (Integer.signum(ClientRingData.pocketStatus)) {
            case 0 -> Component.translatable("gui.lanterna.battery_ready");
            case 1 -> Component.translatable("gui.lanterna.battery_wait", ClientRingData.pocketStatus);
            default -> Component.translatable("gui.lanterna.empty");
        };
        g.drawString(font, Hud.body(batteryInfo), textX, LanternMenu.BATTERY_Y + 10, ClientRingData.pocketStatus == 0 ? Hud.argb(c, 255) : 0xFF8A98A4, false);
        g.drawString(font, Hud.caps(playerInventoryTitle), 8, 72, 0xFF8A98A4, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int a = button == 0 ? abaEm(mx, my) : -1;
        if (a >= 0 && minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.3f);
            switch (a) {
                case 0 -> {
                    minecraft.player.closeContainer();
                    minecraft.setScreen(minecraft.player.isCreative()
                            ? new CreativeModeInventoryScreen(minecraft.player, minecraft.player.connection.enabledFeatures(),
                            minecraft.options.operatorItemsTab().get())
                            : new InventoryScreen(minecraft.player));
                }
                case 2, 3 -> {
                    minecraft.player.closeContainer();
                    PerfilScreen.abrir(a == 2 ? PerfilScreen.Aba.ESTATISTICAS : PerfilScreen.Aba.VISUAL);
                }
                default -> {}
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int left, int top, int button) {
        // Clique nas abas não conta como "fora da tela" (senão jogaria o item no chão)
        return abaEm(mx, my) < 0 && super.hasClickedOutside(mx, my, left, top, button);
    }

    /** Botão liga/desliga no estilo do HUD (aceso na cor da Tropa quando ligado). */
    private class Toggle extends AbstractButton {
        boolean on;
        private final Runnable acao;

        Toggle(int x, int y, int w, int h, Runnable acao) {
            super(x, y, w, h, Component.empty());
            this.acao = acao;
        }

        @Override
        public void onPress() {
            acao.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int c = cor();
            int bg = !active ? 0x50000000 : on ? Hud.argb(c, isHovered() ? 140 : 100) : isHovered() ? 0x50FFFFFF : 0x70000000;
            g.fill(getX(), getY(), getX() + width, getY() + height, bg);
            g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, active ? Hud.argb(c, on ? 255 : 120) : 0x30FFFFFF);
            Component m = Hud.bold(getMessage());
            Hud.textCenter(g, m, getX() + width / 2, getY() + (height - 8) / 2, active ? (on ? 0xFFFFFFFF : 0xFFB8C4CC) : 0xFF606870);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
