package com.lanterna.client.morte;

import com.lanterna.LanternaMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * O frenesi do Anel Preto do lado do jogador: o teclado e o mouse não mandam mais (MouseHandlerMixin). O anel vira o
 * jogador para o alvo e voa até ele; a tela fica escura com a borda vermelha pulsando.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClienteFrenesi {
    private static boolean ativo;
    private static int alvo = -1;
    private static long inicio;

    public static boolean ativo() {
        return ativo;
    }

    public static void receber(boolean ligado, int novoAlvo) {
        if (ligado && !ativo) inicio = System.currentTimeMillis();
        ativo = ligado;
        alvo = novoAlvo;
        var mc = Minecraft.getInstance();
        if (ligado && mc.screen != null) mc.setScreen(null);
    }

    /** Nada do teclado: nem andar, nem pular, nem agachar. */
    @SubscribeEvent
    public static void entrada(MovementInputUpdateEvent e) {
        if (!ativo) return;
        var in = e.getInput();
        in.forwardImpulse = 0; in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = in.jumping = in.shiftKeyDown = false;
    }

    /** Sem abrir inventário nem menus (só o menu de pausa, para poder sair do jogo). */
    @SubscribeEvent
    public static void tela(ScreenEvent.Opening e) {
        if (ativo && !(e.getNewScreen() instanceof net.minecraft.client.gui.screens.PauseScreen)
                && !(e.getNewScreen() instanceof net.minecraft.client.gui.screens.DeathScreen)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !ativo) return;
        var mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || mc.level == null) { ativo = false; return; }
        Entity t = alvo >= 0 ? mc.level.getEntity(alvo) : null;
        if (t == null) {
            // procurando: plana devagar, girando a cabeça
            p.setYRot(p.getYRot() + 4f);
            p.setXRot(Mth.approach(p.getXRot(), 10f, 3f));
            p.setDeltaMovement(p.getLookAngle().multiply(0.35, 0, 0.35).add(0, Math.max(-0.05, p.getDeltaMovement().y * 0.5), 0));
            return;
        }
        Vec3 olho = p.getEyePosition(), mira = t.position().add(0, t.getBbHeight() * 0.6, 0);
        Vec3 d = mira.subtract(olho);
        double hd = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, hd) * Mth.RAD_TO_DEG);
        p.setYRot(Mth.approachDegrees(p.getYRot(), yaw, 18f));
        p.setXRot(Mth.approach(p.getXRot(), pitch, 12f));
        p.yHeadRot = p.getYRot();
        // voa até o alvo (desacelera colado nele); se bater numa parede, sobe
        double dist = d.length();
        double vel = dist > 6 ? 0.9 : dist > 2.6 ? 0.45 : 0.0;
        Vec3 alvoPe = t.position().add(0, 0.2, 0).subtract(p.position());
        Vec3 v = alvoPe.normalize().scale(vel);
        if (p.horizontalCollision) v = v.add(0, 0.55, 0);
        p.setDeltaMovement(v.x, Math.max(-0.6, Math.min(0.6, v.y + 0.04)), v.z);
        p.fallDistance = 0;
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        if (!ativo) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        float t = (System.currentTimeMillis() - inicio) / 1000f;
        float pulso = 0.5f + 0.5f * Mth.sin(t * 5f);
        int a = (int) (90 + 70 * pulso);
        int borda = 40;
        for (int i = 0; i < borda; i++) {
            int al = (int) (a * (1 - i / (float) borda));
            int c = (al << 24) | 0x5A0000;
            g.fill(0, i, w, i + 1, c);
            g.fill(0, h - 1 - i, w, h - i, c);
            g.fill(i, 0, i + 1, h, c);
            g.fill(w - 1 - i, 0, w - i, h, c);
        }
        Component txt = Component.translatable("morte.lanterna.frenesi.hud");
        var font = Minecraft.getInstance().font;
        g.drawString(font, txt, (w - font.width(txt)) / 2, 28, 0xFF000000 | (int) (150 + 100 * pulso) << 16, true);
    }

    private ClienteFrenesi() {}
}
