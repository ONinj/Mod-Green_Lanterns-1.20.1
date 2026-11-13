package com.lanterna.client.hud;

import com.lanterna.LanternaMod;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Vida numa linha só: em vez de várias fileiras de corações, uma fileira de 10 que vai trocando de cor a cada 20 de
 * vida (vermelho, laranja, amarelo, verde, ciano, azul, roxo, rosa, branco...). A camada de baixo aparece por trás da
 * de cima, e no meio vai o número (vida/máxima, e a absorção em dourado).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CoracoesHud {
    private static final ResourceLocation MOLDURA = LanternaMod.id("textures/gui/hud/coracao_moldura.png");
    private static final ResourceLocation CHEIO = LanternaMod.id("textures/gui/hud/coracao.png");
    private static final ResourceLocation MEIO = LanternaMod.id("textures/gui/hud/coracao_meio.png");
    private static final int[] CORES = {0xE82830, 0xFF8A1A, 0xFFD21A, 0x3CDC46, 0x2ED8D8, 0x3A7BFF, 0xA04BFF, 0xFF5AC8, 0xF4F4F4};
    private static float mostrada = -1, rastro = -1;
    private static long ultimo;

    /** Tira os corações do Minecraft (a nossa linha entra no lugar). */
    @SubscribeEvent
    public static void antes(RenderGuiOverlayEvent.Pre e) {
        if (e.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_HEALTH.id())) e.setCanceled(true);
    }

    public static final IGuiOverlay HUD = (ForgeGui gui, GuiGraphics g, float pt, int width, int height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !gui.shouldDrawSurvivalElements() || !(mc.getCameraEntity() instanceof Player p)) return;
        gui.setupOverlayRenderState(true, false);
        float hp = Math.max(0, p.getHealth()), max = Math.max(1, p.getMaxHealth()), abs = p.getAbsorptionAmount();
        // a vida mostrada desliza até o valor certo; o que foi perdido fica um instante em branco
        long agora = System.currentTimeMillis();
        float dt = Math.min(0.1f, (agora - ultimo) / 1000f);
        ultimo = agora;
        if (mostrada < 0) { mostrada = hp; rastro = hp; }
        mostrada += (hp - mostrada) * Math.min(1f, dt * 12f);
        if (hp >= rastro) rastro = hp; else rastro += (hp - rastro) * Math.min(1f, dt * 2.2f);

        int x = width / 2 - 91, y = height - gui.leftHeight;
        gui.leftHeight += 11;
        boolean pisca = p.hurtTime > 0 && (p.hurtTime / 2) % 2 == 0;
        int efeito = p.hasEffect(MobEffects.WITHER) ? 0x3A3A3A : p.hasEffect(MobEffects.POISON) ? 0x8AA020 : p.isFullyFrozen() ? 0x9AD8FF : -1;

        // molduras
        for (int i = 0; i < 10; i++) blit(g, MOLDURA, x + i * 8, y, pisca ? 0xFFB0B0 : 0xFFFFFF, 1f);
        // o que acabou de ser perdido (branco)
        camada(g, x, y, rastro, 0xFFFFFF, 0.55f);
        // camadas: cada 20 de vida é uma fileira; a de baixo cheia, a de cima até onde der
        int camadas = (int) Math.ceil(mostrada / 20f);
        if (camadas >= 2) desenharCheia(g, x, y, cor(camadas - 2, efeito));
        float resto = mostrada - Math.max(0, camadas - 1) * 20f;
        camada(g, x, y, resto, cor(Math.max(0, camadas - 1), efeito), 1f);
        // absorção: um contorno dourado sobre os primeiros corações
        if (abs > 0) {
            int n = Math.min(10, (int) Math.ceil(abs / 2f));
            for (int i = 0; i < n; i++) blit(g, MOLDURA, x + i * 8, y - 1, 0xFFD040, 0.9f);
        }
        // número no meio
        String txt = (int) Math.ceil(hp) + "/" + (int) max;
        Component t = Component.literal(txt);
        var font = mc.font;
        int tw = font.width(t);
        int tx = x + 40 - tw / 2;
        g.drawString(font, t, tx, y + 1, hp <= max * 0.25f && (p.tickCount / 6) % 2 == 0 ? 0xFFFF6060 : 0xFFFFFFFF, true);
        if (abs > 0) g.drawString(font, Component.literal("+" + (int) Math.ceil(abs)), x + 82, y + 1, 0xFFFFD040, true);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    };

    private static int cor(int camada, int efeito) {
        return efeito >= 0 ? efeito : CORES[camada % CORES.length];
    }

    private static void desenharCheia(GuiGraphics g, int x, int y, int cor) {
        for (int i = 0; i < 10; i++) blit(g, CHEIO, x + i * 8, y, Mth.color(((cor >> 16) & 255) / 255f * 0.6f, ((cor >> 8) & 255) / 255f * 0.6f, (cor & 255) / 255f * 0.6f), 1f);
    }

    /** Desenha "vida" pontos (0 a 20) numa fileira, na cor dada. */
    private static void camada(GuiGraphics g, int x, int y, float vida, int cor, float alpha) {
        if (vida <= 0) return;
        int pontos = Math.min(20, Math.round(vida));
        for (int i = 0; i < 10; i++) {
            int falta = pontos - i * 2;
            if (falta <= 0) break;
            blit(g, falta >= 2 ? CHEIO : MEIO, x + i * 8, y, cor, alpha);
        }
    }

    private static void blit(GuiGraphics g, ResourceLocation tex, int x, int y, int cor, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f, alpha);
        g.blit(tex, x, y, 0, 0, 9, 9, 9, 9);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private CoracoesHud() {}
}
