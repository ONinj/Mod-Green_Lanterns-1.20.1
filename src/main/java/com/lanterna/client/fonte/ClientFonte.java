package com.lanterna.client.fonte;

import com.lanterna.LanternaMod;
import com.lanterna.client.ClientRingData;
import com.lanterna.client.construct.EnergyShapes;
import com.lanterna.client.hud.Hud;
import com.lanterna.client.render.LanternaRenderTypes;
import com.lanterna.fonte.Fonte;
import com.lanterna.fonte.FontePackets;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * A Fonte no cliente: painel da Pressão e do Banho de Luz (dentro da Fonte) e a aura da sobrecarga — chamas de luz
 * na cor da Tropa subindo em volta do corpo, um halo pulsando e faíscas, visível para todo mundo.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientFonte {
    public static int pressao, banho, sobrecarga, essencia;
    public static boolean emFonte, bloqueado;
    /** Nós da Árvore da Luz do anel em uso e Tropas com Selo da Fonte. */
    public static java.util.Set<String> nos = new java.util.HashSet<>(), selos = new java.util.HashSet<>();

    public static boolean tem(String no) { return nos.contains(no); }

    private record AuraInfo(int cor, long ate) {}

    private static final Map<Integer, AuraInfo> AURAS = new HashMap<>();

    public static void estado(FontePackets.Estado m) {
        pressao = m.pressao();
        banho = m.banho();
        sobrecarga = m.sobrecarga();
        emFonte = m.emFonte();
        bloqueado = m.bloqueado();
        essencia = m.essencia();
        nos = new java.util.HashSet<>(java.util.Arrays.asList(m.nos().split(",")));
        nos.remove("");
        selos = new java.util.HashSet<>(java.util.Arrays.asList(m.selos().split(",")));
        selos.remove("");
    }

    public static void aura(FontePackets.Aura m) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (m.ligada()) AURAS.put(m.entidade(), new AuraInfo(m.cor(), mc.level.getGameTime() + 120));
        else AURAS.remove(m.entidade());
    }

    // ===================== HUD =====================

    public static final IGuiOverlay HUD = (gui, g, pt, w, h) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !emFonte) return;
        int cor = ClientRingData.color;
        int pw = 150, x = w - pw - 8, y = 8;
        Hud.panel(g, x, y, pw, 46, cor, 0.9f);
        float p = Math.min(1f, pressao / (float) Fonte.PRESSAO_MAX);
        boolean perigo = p >= 0.9f;
        boolean pisca = mc.level != null && (mc.level.getGameTime() / 8) % 2 == 0;
        g.drawString(mc.font, Hud.bold(Component.translatable("hud.lanterna.fonte.pressao")), x + 8, y + 6, 0xFFE8EEF2, false);
        Hud.textRight(g, Hud.body(Component.literal(Math.round(p * 100) + "%  ·  " + tempo(Fonte.PRESSAO_MAX - pressao))), x + pw - 8, y + 6,
                perigo && pisca ? 0xFFFF5050 : 0xFFB8C4CC);
        Hud.bar(g, x + 8, y + 16, pw - 16, 4, p, perigo ? 0xFF4040 : Hud.mix(cor, 0xFF5040, Mth.clamp((p - 0.5f) * 2f, 0, 1)), true);
        float b = Math.min(1f, banho / (float) Fonte.BANHO);
        g.drawString(mc.font, Hud.bold(Component.translatable("hud.lanterna.fonte.banho")), x + 8, y + 26, 0xFFE8EEF2, false);
        Component bt = b >= 1f ? Component.translatable("hud.lanterna.fonte.banho_ok") : Component.literal(tempo(Fonte.BANHO - banho));
        Hud.textRight(g, Hud.body(bt), x + pw - 8, y + 26, b >= 1f ? Hud.argb(cor, 255) : 0xFFB8C4CC);
        Hud.bar(g, x + 8, y + 36, pw - 16, 4, b, b >= 1f ? EnergyShapes.clarear(cor, 0.5f) : cor, false);
    };

    private static String tempo(int ticks) {
        int s = Math.max(0, ticks / 20);
        return s >= 3600 ? String.format("%dh%02d", s / 3600, (s / 60) % 60) : String.format("%d:%02d", s / 60, s % 60);
    }

    /** Barra extra da sobrecarga por cima da barra do anel (chamada pelo HUD do anel). */
    public static void barraSobrecarga(GuiGraphics g, int x, int y, int w, int max, int cor) {
        if (sobrecarga <= 0) return;
        float f = Math.min(1f, sobrecarga / (max * Fonte.SOBRECARGA));
        long t = System.currentTimeMillis();
        int brilho = Hud.argb(EnergyShapes.clarear(cor, 0.55f + 0.2f * Mth.sin(t / 180f)), 255);
        g.fill(x, y - 2, x + Math.round(w * f), y, brilho);
    }

    // ===================== Aura =====================

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Post e) {
        Player p = e.getEntity();
        AuraInfo a = AURAS.get(p.getId());
        if (a == null || p.isInvisible()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.getGameTime() > a.ate) { AURAS.remove(p.getId()); return; }
        float t = p.tickCount + e.getPartialTick();
        EnergyShapes.tempo = t;
        PoseStack pose = e.getPoseStack();
        VertexConsumer glow = e.getMultiBufferSource().getBuffer(LanternaRenderTypes.BRILHO);
        int c = EnergyShapes.brilho(a.cor);
        float h = p.getBbHeight();
        float pulso = 0.85f + 0.15f * Mth.sin(t * 0.25f);
        pose.pushPose();
        // 0.59.2: banhado pela luz, a aura é maior e evapora do corpo, como suor num dia frio
        // halo do corpo (elipse vertical, maior que antes)
        pose.pushPose();
        pose.translate(0, h * 0.5f, 0);
        pose.scale(0.8f, h * 0.72f, 0.8f);
        EnergyShapes.sphere(pose, glow, 1f, c, 0.09f * pulso, t);
        EnergyShapes.sphere(pose, glow, 0.85f, EnergyShapes.clarear(c, 0.4f), 0.07f * pulso, t * 1.4f);
        EnergyShapes.sphere(pose, glow, 0.68f, EnergyShapes.clarear(c, 0.65f), 0.05f * pulso, t * 0.8f);
        pose.popPose();
        // o vapor: nuvenzinhas que saem de pontos do corpo (ombros, cabeça, braços, costas), sobem, se abrem e somem
        float[][] pontos = {{0.22f, 1.42f, 0}, {-0.22f, 1.42f, 0}, {0, 1.75f, 0.05f}, {0.34f, 1.05f, 0}, {-0.34f, 1.05f, 0},
                {0.1f, 1.2f, 0.15f}, {-0.1f, 1.2f, 0.15f}, {0.12f, 0.75f, -0.1f}, {-0.12f, 0.75f, -0.1f}, {0, 1.35f, -0.14f}};
        float corpo = Mth.rotLerp(e.getPartialTick(), p.yBodyRotO, p.yBodyRot);
        double cy = Math.cos(Math.toRadians(-corpo)), sy = Math.sin(Math.toRadians(-corpo));
        for (int i = 0; i < 34; i++) {
            float[] o = pontos[i % pontos.length];
            float fase = (t * (0.018f + 0.004f * (i % 3)) + i * 0.618f) % 1f;
            double dx = o[0] * cy - o[2] * sy, dz = o[0] * sy + o[2] * cy;
            // sobe ondulando, levado por um vento fraco, e se espalha
            double sob = fase * 1.1, deriva = Math.sin(t * 0.05 + i * 1.7) * 0.18 * fase;
            float raio = 0.09f + 0.42f * fase;
            float al = Mth.sin(fase * Mth.PI) * (1 - fase * 0.6f) * 0.5f;
            pose.pushPose();
            pose.translate(dx * h / 1.8 + deriva, o[1] * h / 1.8 + sob, dz * h / 1.8 + Math.cos(t * 0.04 + i) * 0.1 * fase);
            // vapor: claro (como fumaça de suor), tingido com a cor da Tropa; mais forte perto do corpo
            EnergyShapes.sphere(pose, glow, raio, EnergyShapes.clarear(c, i % 3 == 0 ? 0.7f : 0.45f), al, t + i * 13);
            EnergyShapes.sphere(pose, glow, raio * 0.55f, c, al * 0.8f, t + i * 7);
            pose.popPose();
        }
        // chamas de luz subindo
        for (int i = 0; i < 12; i++) {
            double ang = i * 2.39996 + t * 0.03;
            float fase = ((t * 0.045f + i * 0.37f) % 1f);
            double r = 0.5 + 0.1 * Math.sin(t * 0.2 + i);
            float y0 = fase * h * 0.95f, y1 = y0 + 0.4f + 0.3f * (1 - fase);
            float al = Mth.sin(fase * Mth.PI) * 0.45f;
            Vec3 a0 = new Vec3(Math.cos(ang) * r, y0, Math.sin(ang) * r), a1 = new Vec3(Math.cos(ang) * r * 0.75, y1, Math.sin(ang) * r * 0.75);
            EnergyShapes.tube(pose, glow, a0, a1, 0.08f, 0.01f, c, al, 4);
        }
        // anel nos pés
        EnergyShapes.anel(pose, glow, 0.7f + 0.05f * Mth.sin(t * 0.3f), 0.18f, 0.02f, c, 0.55f * pulso);
        pose.popPose();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        var mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused() || AURAS.isEmpty()) return;
        for (var en : AURAS.entrySet()) {
            var ent = mc.level.getEntity(en.getKey());
            if (ent == null) continue;
            // em primeira pessoa, as partículas da própria aura ficariam na frente da câmera
            if (ent == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            int c = en.getValue().cor;
            var r = mc.level.random;
            // faíscas de luz e fiapos de vapor que sobem devagar e se desfazem
            if (r.nextInt(2) == 0) mc.level.addParticle(new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f), 1.2f),
                    ent.getX() + (r.nextDouble() - 0.5) * 1.0, ent.getY() + r.nextDouble() * ent.getBbHeight(),
                    ent.getZ() + (r.nextDouble() - 0.5) * 1.0, 0, 0.05, 0);
            mc.level.addParticle(com.lanterna.particle.GlowOrbOptions.of(c, 0.12f + r.nextFloat() * 0.1f),
                    ent.getX() + (r.nextDouble() - 0.5) * 0.7, ent.getY() + 0.6 + r.nextDouble() * ent.getBbHeight() * 0.8,
                    ent.getZ() + (r.nextDouble() - 0.5) * 0.7, (r.nextDouble() - 0.5) * 0.01, 0.025 + r.nextDouble() * 0.02, (r.nextDouble() - 0.5) * 0.01);
        }
    }

    private ClientFonte() {}
}
