package com.lanterna.client.construct;

import com.lanterna.LanternaMod;
import com.lanterna.construct.ModEfeitos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tela de quem está com Pavor: bordas amarelas e escuras pulsando no ritmo do coração, a câmera tremendo e o som
 * do coração batendo. Com Encanto, as bordas ficam rosadas e a visão balança devagar.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PavorTela {

    private static float forca(MobEffectInstance e) {
        if (e == null) return 0f;
        return e.getDuration() < 30 ? e.getDuration() / 30f : 1f;
    }

    public static final IGuiOverlay HUD = (ForgeGui gui, GuiGraphics g, float pt, int w, int h) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        float pavor = forca(mc.player.getEffect(ModEfeitos.PAVOR.get()));
        float encanto = forca(mc.player.getEffect(ModEfeitos.ENCANTO.get()));
        if (pavor <= 0 && encanto <= 0) return;
        float t = mc.player.tickCount + pt;
        int cor;
        float a;
        if (pavor > 0) {
            float batida = (float) Math.pow(Math.max(0, Mth.sin(t * 0.39f)), 6);
            a = pavor * (0.45f + 0.35f * batida);
            cor = 0xC8A000;
        } else {
            a = encanto * (0.3f + 0.1f * Mth.sin(t * 0.12f));
            cor = 0xE058FF;
        }
        int faixa = (int) (Math.min(w, h) * 0.28f);
        for (int i = 0; i < faixa; i += 2) {
            float k = 1f - i / (float) faixa;
            int alpha = (int) (255 * a * k * k);
            int c = (alpha << 24) | cor;
            g.fill(0, i, w, i + 2, c);
            g.fill(0, h - i - 2, w, h - i, c);
            g.fill(i, 0, i + 2, h, c);
            g.fill(w - i - 2, 0, w - i, h, c);
        }
        if (pavor > 0) {
            var txt = com.lanterna.client.hud.Hud.caps(net.minecraft.network.chat.Component.translatable("effect.lanterna.pavor.tela"));
            int tw = mc.font.width(txt);
            float tremor = Mth.sin(t * 3.1f) * 1.5f;
            g.drawString(mc.font, txt, (int) (w / 2f - tw / 2f + tremor), h / 2 + 28, ((int) (200 * pavor) << 24) | 0xFFE060, true);
        }
    };

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float t = mc.player.tickCount + (float) event.getPartialTick();
        float pavor = forca(mc.player.getEffect(ModEfeitos.PAVOR.get()));
        if (pavor > 0) {
            event.setRoll(event.getRoll() + Mth.sin(t * 1.7f) * 1.6f * pavor);
            event.setYaw(event.getYaw() + Mth.sin(t * 2.3f) * 0.6f * pavor);
            event.setPitch(event.getPitch() + Mth.cos(t * 2.9f) * 0.5f * pavor);
        }
        float encanto = forca(mc.player.getEffect(ModEfeitos.ENCANTO.get()));
        if (encanto > 0) event.setRoll(event.getRoll() + Mth.sin(t * 0.15f) * 3f * encanto);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.player == null || mc.isPaused()) return;
        if (mc.player.hasEffect(ModEfeitos.PAVOR.get()) && mc.player.tickCount % 16 == 0)
            mc.player.playSound(SoundEvents.WARDEN_HEARTBEAT, 0.9f, 1.15f);
    }

    private PavorTela() {}
}
