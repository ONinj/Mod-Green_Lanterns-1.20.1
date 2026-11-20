package com.lanterna.client.necron;

import com.lanterna.LanternaMod;
import com.lanterna.necron.Pedidos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 0.55.1: o pedido de ajuda (no mundo: o Lanterna de outra Tropa desce e fala, com câmera de cinema e legendas) e a
 * queda de uma Tropa (abre a tela de cinema no espaço).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CenaPedido {
    private static int t = -1, lanterna = -1;
    private static String tropa = "red", planeta = "ysmault";
    private static final int FALA = 60, POR_FALA = 75;

    public static void receber(Pedidos.Pacote m) {
        tropa = m.tropa();
        planeta = m.planeta();
        Minecraft.getInstance().getToasts().clear();
        if (m.tipo() == 1) {
            Minecraft.getInstance().setScreen(new CinemaQueda(tropa, planeta));
            return;
        }
        lanterna = m.lanterna();
        t = 0;
        ultimaFala = "";
    }

    public static boolean ativa() { return t >= 0; }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || t < 0) return;
        if (Minecraft.getInstance().level == null) { t = -1; return; }
        if (++t > Pedidos.DUR_PEDIDO + 2) t = -1;
    }

    private static Vec3 lerp(Entity e, float pt) {
        return new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
    }

    public static double[] camera(Entity e, float pt) {
        var mc = Minecraft.getInstance();
        if (t < 0 || e != mc.player || mc.level == null) return null;
        Entity a = mc.level.getEntity(lanterna);
        if (a == null) return null;
        float tt = t + pt;
        Vec3 pa = lerp(a, pt), pj = lerp(e, pt);
        Vec3 meio = pa.add(0, 1.4, 0).lerp(pj.add(0, 1.3, 0), 0.5);
        Vec3 eixo = pa.subtract(pj).multiply(1, 0, 1);
        if (eixo.lengthSqr() < 0.01) eixo = new Vec3(0, 0, 1);
        eixo = eixo.normalize();
        Vec3 lado = new Vec3(-eixo.z, 0, eixo.x);
        Vec3 alvo = tt < 50 ? pa.add(0, 1, 0).lerp(meio, tt / 50f) : meio;
        Vec3 cam = meio.add(lado.scale(5.4)).add(eixo.scale(-1.2 + 0.4 * Math.sin(tt * 0.01))).add(0, 1.0, 0);
        var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(meio, cam, net.minecraft.world.level.ClipContext.Block.VISUAL,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) cam = hit.getLocation().add(meio.subtract(hit.getLocation()).normalize().scale(0.4));
        Vec3 d = alvo.subtract(cam);
        return new double[]{cam.x, cam.y, cam.z, Math.toDegrees(Math.atan2(-d.x, d.z)), Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)))};
    }

    @SubscribeEvent
    public static void parado(MovementInputUpdateEvent e) {
        if (t < 0) return;
        var in = e.getInput();
        in.forwardImpulse = 0; in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = in.jumping = in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void semMao(net.minecraftforge.client.event.RenderHandEvent e) {
        if (t >= 0) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void hud(RenderGuiOverlayEvent.Pre e) {
        if (t < 0) return;
        if (e.getOverlay() == net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.type()) textos(e.getGuiGraphics(), t + e.getPartialTick());
        e.setCanceled(true);
    }

    private static float faixa(float x, float a, float b) { return Mth.clamp((x - a) / (b - a), 0, 1); }

    private static String ultimaFala = "";

    private static void textos(GuiGraphics g, float tt) {
        var mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight(), fx = h / 9;
        g.fill(0, 0, w, fx, 0xFF000000);
        g.fill(0, h - fx, w, h, 0xFF000000);
        int i = (int) ((tt - FALA) / POR_FALA);
        if (tt < FALA || i >= 3) return;
        float de = FALA + i * POR_FALA, ate = de + POR_FALA;
        int al = Math.max(4, (int) (Math.min(faixa(tt, de, de + 8), 1 - faixa(tt, ate - 8, ate)) * 255));
        String propria = com.lanterna.client.ClientRingData.corps == null ? "green" : com.lanterna.client.ClientRingData.corps.getPath();
        String chave = "necron.lanterna.pedido_" + (i + 1);
        if (!chave.equals(ultimaFala) || t < FALA + 2) {
            if (!chave.equals(ultimaFala)) com.lanterna.client.voz.Falas.falar(chave, "lanterna", com.lanterna.client.voz.Falas.tomDe(lanterna));
            ultimaFala = chave;
        }
        Component txt = Component.translatable(chave, Component.translatable("corps.lanterna." + propria),
                Component.translatable("planet.lanterna." + planeta));
        int c = com.lanterna.vida.Emocoes.cor(tropa);
        int cr = (c >> 16) & 255, cg = (c >> 8) & 255, cb = c & 255;
        int claro = ((cr + (255 - cr) * 45 / 100) << 16) | ((cg + (255 - cg) * 45 / 100) << 8) | (cb + (255 - cb) * 45 / 100);
        var linhas = mc.font.split(txt, w - 40);
        int y = h - fx / 2 - linhas.size() * (mc.font.lineHeight + 1) / 2;
        for (var l : linhas) { g.drawCenteredString(mc.font, l, w / 2, y, (al << 24) | claro); y += mc.font.lineHeight + 1; }
    }

    private CenaPedido() {}
}
