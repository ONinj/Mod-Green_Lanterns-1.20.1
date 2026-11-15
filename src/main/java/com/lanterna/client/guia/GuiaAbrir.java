package com.lanterna.client.guia;

import com.lanterna.LanternaMod;
import com.lanterna.client.ClientRingData;
import com.lanterna.client.KeyBindings;
import com.lanterna.client.hud.Hud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Abre o Guia do Portador: pela tecla (F9), pelo comando /guia (ou /guide) e, quem nunca abriu, vê um aviso
 * discreto no alto da tela por alguns segundos depois de ganhar o anel.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GuiaAbrir {
    private static int abrirEm = -1, avisoTicks;
    private static boolean avisou;
    private static String capitulo;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { avisou = false; return; }
        while (KeyBindings.GUIA.consumeClick()) if (mc.screen == null) mc.setScreen(new GuiaScreen());
        if (abrirEm >= 0 && --abrirEm < 0) mc.setScreen(capitulo == null ? new GuiaScreen() : GuiaScreen.em(capitulo));
        if (!avisou && ClientRingData.hasRing && !GuiaProgresso.jaAbriu() && mc.player.tickCount > 100) {
            avisou = true;
            avisoTicks = 20 * 14;
        }
        if (avisoTicks > 0 && (GuiaProgresso.jaAbriu() || --avisoTicks <= 0)) avisoTicks = 0;
    }

    /** O aviso no alto da tela. */
    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        if (avisoTicks <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null) return;
        GuiGraphics g = e.getGuiGraphics();
        float a = Math.min(1f, Math.min(avisoTicks, 20 * 14 - avisoTicks + 1) / 15f);
        int cor = ClientRingData.color;
        Component l1 = Hud.caps(Component.translatable("guia.lanterna.aviso_titulo"));
        Component l2 = Hud.body(Component.translatable("guia.lanterna.aviso", KeyBindings.GUIA.getTranslatedKeyMessage().getString()));
        int w = Math.max(mc.font.width(l1), mc.font.width(l2)) + 34, h = 28;
        // logo abaixo do painel do anel (no alto, ficaria por cima dele e do painel da missão)
        int x = 10 + (int) ((1 - a) * -30), y = 74;
        Hud.panel(g, x, y, w, h, cor, a);
        g.renderItem(GuiaDados.item("minecraft:writable_book"), x + 6, y + 6);
        g.drawString(mc.font, l1, x + 28, y + 5, Hud.argb(Hud.mix(cor, 0xFFFFFF, 0.5f), (int) (255 * a)), true);
        g.drawString(mc.font, l2, x + 28, y + 16, Hud.argb(0xD0D6DC, Math.max(8, (int) (255 * a))), false);
    }

    @SubscribeEvent
    public static void comandos(RegisterClientCommandsEvent e) {
        for (String nome : new String[]{"guide"}) {
            e.getDispatcher().register(Commands.literal(nome)
                    .executes(c -> { capitulo = null; abrirEm = 2; return 1; })
                    .then(Commands.argument("chapter", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .suggests((c, b) -> { GuiaDados.capitulos().forEach(x -> b.suggest(x.id())); return b.buildFuture(); })
                            .executes(c -> { capitulo = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "chapter"); abrirEm = 2; return 1; })));
        }
    }

    private GuiaAbrir() {}
}
