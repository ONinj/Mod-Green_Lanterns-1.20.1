package com.lanterna.client.screen;

import com.lanterna.LanternaMod;
import com.lanterna.client.ClientVisibleRings;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.item.PowerRingItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * As duas abas em cima do inventário, no canto direito:
 *   [baú]  Inventário
 *   [anel] Lanterna
 */
public final class LanternTabs {
    public static final ResourceLocation TEXTURE = LanternaMod.id("textures/gui/lantern.png");

    public static final int TAB_W = 28;
    private static final int TAB_H = 28;          // aba de trás
    private static final int TAB_H_SELECTED = 32; // aba da frente (encosta no painel)

    public enum Tab { INVENTORY, LANTERN }

    private static int tabX(int left, Tab tab) {
        return left + 176 - (tab == Tab.INVENTORY ? 2 : 1) * (TAB_W + 1);
    }

    private static int tabY(int top) {
        return top - TAB_H;
    }

    /** Desenha as abas de trás (chamar antes do painel). */
    public static void renderBack(GuiGraphics g, int left, int top, Tab selected) {
        for (Tab tab : Tab.values()) {
            if (tab != selected) drawTab(g, left, top, tab, false);
        }
    }

    /** Desenha a aba selecionada (chamar depois do painel, para cobrir a borda). */
    public static void renderFront(GuiGraphics g, int left, int top, Tab selected) {
        drawTab(g, left, top, selected, true);
    }

    private static void drawTab(GuiGraphics g, int left, int top, Tab tab, boolean selected) {
        int x = tabX(left, tab);
        int y = tabY(top);
        int h = selected ? TAB_H_SELECTED : TAB_H;
        g.blit(TEXTURE, x, y, selected ? TAB_W : 0, 166, TAB_W, h);
        g.renderItem(icon(tab), x + 6, y + (selected ? 8 : 9));
    }

    private static ItemStack icon(Tab tab) {
        if (tab == Tab.INVENTORY) return new ItemStack(Items.CHEST);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ItemStack worn = ClientVisibleRings.get(mc.player.getId());
            if (!worn.isEmpty()) return worn;
        }
        return PowerRingItem.create(BuiltinCorps.GREEN.id(), BuiltinCorps.GREEN.color(), -1);
    }

    /** Qual aba está embaixo do mouse (ou null). */
    public static Tab hovered(int left, int top, double mouseX, double mouseY) {
        for (Tab tab : Tab.values()) {
            int x = tabX(left, tab);
            int y = tabY(top);
            if (mouseX >= x && mouseX < x + TAB_W && mouseY >= y && mouseY < y + TAB_H) return tab;
        }
        return null;
    }

    public static void renderTooltip(GuiGraphics g, Font font, int left, int top, int mouseX, int mouseY) {
        Tab tab = hovered(left, top, mouseX, mouseY);
        if (tab != null) {
            g.renderTooltip(font, Component.translatable(tab == Tab.INVENTORY
                    ? "gui.lanterna.tab.inventory" : "gui.lanterna.tab.lantern"), mouseX, mouseY);
        }
    }

    private LanternTabs() {}
}
