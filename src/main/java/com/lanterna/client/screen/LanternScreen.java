package com.lanterna.client.screen;

import com.lanterna.client.ClientRingData;
import com.lanterna.corps.CorpsDefinition;
import com.lanterna.menu.LanternMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.TogglePowerPacket;
import com.lanterna.network.ToggleFlightPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Tela da aba "Lanterna":
 *
 *   +-------------------------------------+
 *   | [personagem]   Lanterna             |
 *   |                [anel]    Anel        |
 *   |                          Carga 85%   |
 *   |                [bateria] Bateria     |
 *   |                                     |
 *   | inventário ...                      |
 *   +-------------------------------------+
 */
public class LanternScreen extends AbstractContainerScreen<LanternMenu> {
    private float mouseX;
    private float mouseY;

    public LanternScreen(LanternMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    private Button powerButton;
    private Button flightButton;

    @Override
    protected void init() {
        super.init();
        // Botões: ligar/desligar o anel e o voo, sem precisar lembrar das teclas
        powerButton = addRenderableWidget(Button.builder(Component.empty(),
                b -> ModNetwork.sendToServer(new TogglePowerPacket())).bounds(leftPos + 80, topPos + 62, 45, 14).build());
        flightButton = addRenderableWidget(Button.builder(Component.empty(),
                b -> ModNetwork.sendToServer(new ToggleFlightPacket())).bounds(leftPos + 126, topPos + 62, 45, 14).build());
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
        powerButton.setMessage(Component.translatable(ring && ClientRingData.active ? "gui.lanterna.button.ring_on" : "gui.lanterna.button.ring_off"));
        flightButton.setMessage(Component.translatable(ring && ClientRingData.flight ? "gui.lanterna.button.flight_on" : "gui.lanterna.button.flight_off"));
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
        LanternTabs.renderTooltip(g, font, leftPos, topPos, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        LanternTabs.renderBack(g, leftPos, topPos, LanternTabs.Tab.LANTERN);
        g.blit(LanternTabs.TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        LanternTabs.renderFront(g, leftPos, topPos, LanternTabs.Tab.LANTERN);

        // Personagem olhando para o mouse, como no inventário normal
        if (minecraft != null && minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, leftPos + 51, topPos + 75, 30,
                    (float) (leftPos + 51) - this.mouseX, (float) (topPos + 75 - 50) - this.mouseY, minecraft.player);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int textX = LanternMenu.RING_X + 22;
        g.drawString(font, title, 86, 7, 0x404040, false);

        g.drawString(font, Component.translatable("gui.lanterna.slot.ring"), textX, LanternMenu.RING_Y + 1, 0x404040, false);
        if (ClientRingData.hasRing) {
            int pct = ClientRingData.charge * 100 / Math.max(1, ClientRingData.maxCharge);
            int color = 0xFF000000 | darken(ClientRingData.color);
            g.drawString(font, Component.translatable("gui.lanterna.charge", pct), textX, LanternMenu.RING_Y + 10, color, false);
        } else {
            g.drawString(font, Component.translatable("gui.lanterna.empty"), textX, LanternMenu.RING_Y + 10, 0x808080, false);
        }

        g.drawString(font, Component.translatable("gui.lanterna.slot.battery"), textX, LanternMenu.BATTERY_Y + 1, 0x404040, false);
        Component batteryInfo = switch (Integer.signum(ClientRingData.pocketStatus)) {
            case 0 -> Component.translatable("gui.lanterna.battery_ready");
            case 1 -> Component.translatable("gui.lanterna.battery_wait", ClientRingData.pocketStatus);
            default -> Component.translatable("gui.lanterna.empty");
        };
        g.drawString(font, batteryInfo, textX, LanternMenu.BATTERY_Y + 10, 0x808080, false);

    }

    /** Escurece a cor da Tropa para dar leitura no fundo cinza. */
    private static int darken(int rgb) {
        int r = ((rgb >> 16) & 0xFF) * 6 / 10;
        int gg = ((rgb >> 8) & 0xFF) * 6 / 10;
        int b = (rgb & 0xFF) * 6 / 10;
        return (r << 16) | (gg << 8) | b;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && LanternTabs.hovered(leftPos, topPos, mouseX, mouseY) == LanternTabs.Tab.INVENTORY
                && minecraft != null && minecraft.player != null) {
            // Volta para o inventário normal
            minecraft.player.closeContainer();
            minecraft.setScreen(minecraft.player.isCreative()
                    ? new CreativeModeInventoryScreen(minecraft.player, minecraft.player.connection.enabledFeatures(),
                        minecraft.options.operatorItemsTab().get())
                    : new InventoryScreen(minecraft.player));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        // Clique nas abas não conta como "fora da tela" (senão jogaria o item no chão)
        return LanternTabs.hovered(left, top, mouseX, mouseY) == null
                && super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }
}
