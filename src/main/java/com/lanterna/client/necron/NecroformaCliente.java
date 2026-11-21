package com.lanterna.client.necron;

import com.lanterna.LanternaMod;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * 0.58.2: a Forma do Necron no cliente. Quem está transformado é desenhado como o modelo do Necron (a foice na mão,
 * os farrapos balançando, a pose de voo quando voa), um pouco maior que um jogador. Em primeira pessoa a mão some e a
 * borda da tela escurece em roxo.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NecroformaCliente {
    /** entidade → ticks que faltam. */
    private static final Map<Integer, Integer> ATIVOS = new HashMap<>();
    /** entidade → ticks desde que se transformou (para o surgimento). */
    private static final Map<Integer, Integer> IDADE = new HashMap<>();
    private static Atores atores;
    private static float animAntes;
    public static final float ESCALA = 1.0f;

    public static void receber(int entidade, int ticks) {
        if (ticks > 0) {
            ATIVOS.put(entidade, ticks);
            IDADE.putIfAbsent(entidade, 0);
        } else {
            ATIVOS.remove(entidade);
            IDADE.remove(entidade);
        }
    }

    public static boolean ativa(Player p) { return ATIVOS.containsKey(p.getId()); }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (Minecraft.getInstance().level == null) { ATIVOS.clear(); IDADE.clear(); return; }
        // o Arauto transformado balançou o braço: avisa o servidor (o corte da foice)
        var mc = Minecraft.getInstance();
        if (mc.player != null && ativa(mc.player)) {
            float anim = mc.player.getAttackAnim(0);
            if (anim > 0 && animAntes <= 0) com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.morte.Necroforma.Golpe());
            animAntes = anim;
        }
        ATIVOS.replaceAll((k, v) -> v - 1);
        ATIVOS.values().removeIf(v -> v <= 0);
        IDADE.keySet().retainAll(ATIVOS.keySet());
        IDADE.replaceAll((k, v) -> v + 1);
    }

    @SubscribeEvent
    public static void desenhar(RenderPlayerEvent.Pre e) {
        Player p = e.getEntity();
        if (!ativa(p)) return;
        e.setCanceled(true);
        if (atores == null) atores = new Atores();
        float pt = e.getPartialTick();
        float t = p.tickCount + pt;
        int idade = IDADE.getOrDefault(p.getId(), 100);
        float surge = Mth.clamp((idade + pt) / 12f, 0, 1);
        var ps = e.getPoseStack();
        ps.pushPose();
        float corpo = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        ps.mulPose(Axis.YP.rotationDegrees(180 - corpo));
        // voando com o anel: deita o corpo como o voo do mod deita o jogador
        boolean voa = com.lanterna.client.flight.ClientFlight.isFlying(p) && !p.onGround();
        float tilt = voa ? com.lanterna.client.flight.ClientFlight.tilt(p, pt) : 0;
        if (Math.abs(tilt) > 0.001f) {
            float ang = tilt * (-90f - p.getViewXRot(pt));
            ps.translate(0, 1.1, 0);
            ps.mulPose(Axis.XP.rotationDegrees(ang));
            ps.translate(0, -1.1, 0);
        }
        float s = ESCALA * (0.6f + 0.4f * surge);
        ps.scale(s, s, s);
        // ele não tem pernas: paira um pouco acima do chão, balançando
        ps.translate(0, 0.08 + 0.05 * Math.sin(t * 0.08), 0);
        float cj = com.lanterna.client.construct.CastAnim.conjurando(p.getId(), pt);
        Atores.estadoForcado = PoseNecron.estado(p, pt, Mth.clamp(tilt, 0, 1), cj, Mth.clamp((idade + pt) / 26f, 0, 1));
        atores.necron(ps, e.getMultiBufferSource(), t, 0.05f, 1f);
        Atores.estadoForcado = null;
        ps.popPose();
    }

    @SubscribeEvent
    public static void semMao(RenderHandEvent e) {
        var mc = Minecraft.getInstance();
        if (mc.player != null && ativa(mc.player)) e.setCanceled(true);
    }

    /** Em primeira pessoa: a borda da tela escurece em roxo e o tempo restante aparece embaixo. */
    @SubscribeEvent
    public static void tela(RenderGuiOverlayEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !ativa(mc.player) || e.getOverlay() != net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.type()) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        if (mc.options.getCameraType().isFirstPerson()) {
            for (int k = 0; k < 24; k++) {
                int a = (int) (70 * (1 - k / 24f));
                int c = (a << 24) | 0x1A0526;
                g.fill(0, k * 2, w, k * 2 + 2, c);
                g.fill(0, h - k * 2 - 2, w, h - k * 2, c);
                g.fill(k * 2, 0, k * 2 + 2, h, c);
                g.fill(w - k * 2 - 2, 0, w - k * 2, h, c);
            }
        }
        int resta = ATIVOS.getOrDefault(mc.player.getId(), 0);
        int bw = 100, x = w / 2 - bw / 2, y = h - 58;
        g.fill(x - 1, y - 1, x + bw + 1, y + 4, 0xC0000000);
        g.fill(x, y, x + (int) (bw * resta / (float) com.lanterna.morte.Necroforma.DURACAO), y + 3, 0xFF8A3CC8);
    }

    private NecroformaCliente() {}
}
