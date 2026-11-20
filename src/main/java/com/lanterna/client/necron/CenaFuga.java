package com.lanterna.client.necron;

import com.lanterna.LanternaMod;
import com.lanterna.necron.Fuga;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 0.54.3: a cena da fuga e a conversa no planeta da Tropa, no mundo de verdade, com câmera de cinema, faixas pretas,
 * escurecimentos e legendas. O servidor move o jogador e o Lanterna; aqui só a câmera, a pose, o som e os textos.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CenaFuga {
    private static int t = -1;
    private static int aliado = -1, necron = -1;
    private static Vec3 fenda = Vec3.ZERO;
    private static String tropa = "green";
    private static float pt;

    public static void receber(Fuga.Cena m) {
        aliado = m.aliado();
        tropa = m.tropa();
        Minecraft.getInstance().getToasts().clear();
        if (m.fase() == 0) { t = 0; necron = m.necron(); }
        else { t = Fuga.CASA; necron = -1; fenda = new Vec3(m.fx() + 0.5, m.fy(), m.fz() + 0.5); }
    }

    public static boolean ativa() { return t >= 0; }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || t < 0) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { t = -1; return; }
        // a tela de "carregando o mundo" não conta tempo
        if (mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen) return;
        t++;
        sons(t);
        if (t > Fuga.FIM + 5) t = -1;
    }

    private static void som(net.minecraft.sounds.SoundEvent s, float vol, float tom) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, tom, vol));
    }

    private static void sons(int t) {
        if (t == 2) { som(SoundEvents.WARDEN_HEARTBEAT, 1f, 0.7f); som(SoundEvents.PLAYER_HURT, 0.8f, 0.7f); }
        if (t == 20 || t == 34) som(SoundEvents.WARDEN_HEARTBEAT, 1f, 0.6f);
        if (t == Fuga.CHEGA - 15) som(SoundEvents.FIREWORK_ROCKET_LAUNCH, 1f, 0.6f);
        if (t == Fuga.SOBE + 2) som(SoundEvents.ELYTRA_FLYING, 0.6f, 1.2f);
        if (t == Fuga.PRETO) som(SoundEvents.BEACON_DEACTIVATE, 0.8f, 0.6f);
        if (t == Fuga.CASA + 10) som(SoundEvents.BEACON_AMBIENT, 1f, 1.2f);
        if (t == Fuga.ALERTA + 2) { som(SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, 0.5f); som(SoundEvents.AMBIENT_CAVE.value(), 1f, 0.5f); }
        if (t == Fuga.ALERTA + 12 || t == Fuga.ALERTA + 82) som(SoundEvents.WITHER_AMBIENT, 0.8f, 0.4f);
    }

    // ===================== câmera =====================

    private static Vec3 lerp(Entity e, float pt) {
        return new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
    }

    private static double[] olhar(Vec3 cam, Vec3 alvo) {
        // a câmera não atravessa o chão nem paredes: para um pouco antes do bloco
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(alvo, cam, net.minecraft.world.level.ClipContext.Block.VISUAL,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS)
                cam = hit.getLocation().add(alvo.subtract(hit.getLocation()).normalize().scale(0.4));
        }
        Vec3 d = alvo.subtract(cam);
        return new double[]{cam.x, cam.y, cam.z, Math.toDegrees(Math.atan2(-d.x, d.z)), Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)))};
    }

    /** Câmera de cinema durante a cena: {x, y, z, yaw, pitch}. */
    public static double[] camera(Entity e, float partial) {
        var mc = Minecraft.getInstance();
        if (t < 0 || e != mc.player || mc.level == null) return null;
        pt = partial;
        float tt = t + partial;
        Vec3 eu = lerp(e, partial).add(0, 1.1, 0);
        Entity a = aliado >= 0 ? mc.level.getEntity(aliado) : null;
        Entity n = necron >= 0 ? mc.level.getEntity(necron) : null;
        if (tt < Fuga.CHEGA) {
            // de baixo, atrás do jogador caído: o Necron enorme na frente
            Vec3 paraN = n == null ? new Vec3(0, 0, 1) : n.position().subtract(e.position()).multiply(1, 0, 1).normalize();
            Vec3 cam = eu.add(paraN.scale(-3.6)).add(new Vec3(-paraN.z, 0, paraN.x).scale(1.6)).add(0, 0.7, 0);
            Vec3 alvo = n == null ? eu.add(paraN.scale(6)) : lerp(n, partial).add(0, 2.4, 0);
            return olhar(cam, alvo.lerp(eu, 0.25));
        }
        if (tt < Fuga.SOBE) {
            // de lado: o Lanterna, o escudo e a foice
            Vec3 paraN = n == null ? new Vec3(0, 0, 1) : n.position().subtract(e.position()).multiply(1, 0, 1).normalize();
            Vec3 lado = new Vec3(-paraN.z, 0, paraN.x);
            Vec3 cam = eu.add(lado.scale(6.5)).add(paraN.scale(1.5)).add(0, 0.8, 0);
            return olhar(cam, eu.add(paraN.scale(1.5)));
        }
        if (tt < Fuga.CASA) {
            // de baixo, vendo os dois subirem e o Necron lá embaixo
            Vec3 de = eu.add(0, -7 - (tt - Fuga.SOBE) * 0.25, 0).add(4, 0, 4);
            return olhar(de, eu);
        }
        // o alerta: a câmera sobe dos dois para a fenda no céu, sobre a Bateria Central
        if (tt >= Fuga.ALERTA) {
            float k = faixa(tt, Fuga.ALERTA, Fuga.ALERTA + 50);
            Vec3 base = eu.add(0, 0.6, 0);
            Vec3 cam = base.add(fenda.subtract(base).multiply(1, 0, 1).normalize().scale(-4)).add(0, 1.2 + 2 * k, 0);
            Vec3 alvo = base.lerp(fenda, 0.25 + 0.75 * k);
            return olhar(cam, alvo);
        }
        // a conversa: em volta dos dois, devagar
        if (a == null) return null;
        Vec3 pa = lerp(a, partial), pj = lerp(e, partial);
        Vec3 meio = pa.add(0, 1.3, 0).lerp(pj.add(0, 1.3, 0), 0.5);
        // de lado para os dois (eles ficam de frente um para o outro), girando devagar
        Vec3 eixo = pa.subtract(pj).multiply(1, 0, 1);
        if (eixo.lengthSqr() < 0.01) eixo = new Vec3(0, 0, 1);
        eixo = eixo.normalize();
        Vec3 lado = new Vec3(-eixo.z, 0, eixo.x);
        int fala = falaAtual(tt);
        double gira = Math.sin((tt - Fuga.CASA) * 0.006) * 0.5;
        double r = fala % 2 == 0 ? 5.2 : 6.5;
        Vec3 dir = lado.scale(Math.cos(gira)).add(eixo.scale(Math.sin(gira) + (fala % 2 == 0 ? 0.35 : -0.2)));
        Vec3 cam = meio.add(dir.normalize().scale(r)).add(0, 0.9, 0);
        return olhar(cam, meio);
    }

    private static int falaAtual(float tt) {
        return (int) ((tt - Fuga.FALA) / Fuga.POR_FALA);
    }

    /** Sem a mão de primeira pessoa por cima da cena. */
    @SubscribeEvent
    public static void semMao(net.minecraftforge.client.event.RenderHandEvent e) {
        if (t >= 0) e.setCanceled(true);
    }

    // ===================== controles e pose =====================

    @SubscribeEvent
    public static void parado(MovementInputUpdateEvent e) {
        if (t < 0) return;
        var in = e.getInput();
        in.forwardImpulse = 0; in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = in.jumping = in.shiftKeyDown = false;
    }

    /** Caído: braços largados. Sendo carregado: um braço por cima do ombro do Lanterna. */
    public static void bracos(HumanoidModel<?> m, LivingEntity e) {
        var mc = Minecraft.getInstance();
        if (t < 0 || e != mc.player || t > Fuga.CASA) return;
        if (t < Fuga.SOBE) {
            m.head.xRot = 0.5f;
            m.rightArm.xRot = 0.25f; m.leftArm.xRot = 0.15f;
            m.rightArm.zRot = 0.12f; m.leftArm.zRot = -0.12f;
        } else {
            m.rightArm.xRot = -2.6f; m.rightArm.zRot = 0.3f;
            m.leftArm.xRot = 0.2f;
            m.rightLeg.xRot = 0.35f; m.leftLeg.xRot = -0.2f;
            m.head.xRot = 0.6f;
        }
    }

    // ===================== textos =====================

    @SubscribeEvent
    public static void hud(RenderGuiOverlayEvent.Pre e) {
        if (t < 0) return;
        if (e.getOverlay() == net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.type()) textos(e.getGuiGraphics(), t + e.getPartialTick());
        e.setCanceled(true);
    }

    private static float faixa(float x, float a, float b) { return Mth.clamp((x - a) / (b - a), 0, 1); }

    private static void textos(GuiGraphics g, float tt) {
        var mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        // escuro: a vista turva no começo; o apagão da fuga; acordando em casa
        float preto = 0;
        if (tt < 30) preto = 0.35f * (1 - tt / 30f);
        if (tt > Fuga.PRETO - 20 && tt < Fuga.FALA) preto = tt < Fuga.CASA ? faixa(tt, Fuga.PRETO - 20, Fuga.PRETO) : 1 - faixa(tt, Fuga.CASA + 10, Fuga.FALA);
        if (tt > Fuga.FIM - 20) preto = faixa(tt, Fuga.FIM - 20, Fuga.FIM) * 0.6f;
        // vermelho da dor no começo
        if (tt < Fuga.CHEGA) {
            int vr = (int) (90 * (0.6f + 0.4f * Mth.sin(tt * 0.3f)) * (1 - tt / Fuga.CHEGA));
            g.fillGradient(0, 0, w, h, (vr << 24) | 0x400000, (vr << 24) | 0x200000);
        }
        if (preto > 0) g.fill(0, 0, w, h, ((int) (preto * 255) << 24));
        int fx = h / 9;
        g.fill(0, 0, w, fx, 0xFF000000);
        g.fill(0, h - fx, w, h, 0xFF000000);
        String chave = null;
        float de = 0, ate = 0;
        if (tt > 6 && tt < Fuga.CHEGA - 4) { chave = "necron.lanterna.fuga.cai"; de = 6; ate = Fuga.CHEGA - 4; }
        else if (tt > Fuga.CHEGA && tt < Fuga.SOBE + 30) { chave = "necron.lanterna.fuga.aguenta"; de = Fuga.CHEGA; ate = Fuga.SOBE + 30; }
        else if (tt > Fuga.SOBE + 32 && tt < Fuga.PRETO) { chave = "necron.lanterna.fuga.segura"; de = Fuga.SOBE + 32; ate = Fuga.PRETO; }
        else if (tt > Fuga.CASA - 25 && tt < Fuga.FALA - 2) { chave = "necron.lanterna.fuga.acorda"; de = Fuga.CASA - 25; ate = Fuga.FALA - 2; }
        else if (tt >= Fuga.ALERTA + 10 && tt < Fuga.ALERTA + Fuga.NALERTAS * Fuga.POR_ALERTA) {
            int i = (int) ((tt - Fuga.ALERTA) / Fuga.POR_ALERTA);
            chave = "necron.lanterna.alerta_" + (i + 1);
            de = Math.max(Fuga.ALERTA + 10, Fuga.ALERTA + i * Fuga.POR_ALERTA); ate = Fuga.ALERTA + (i + 1) * Fuga.POR_ALERTA;
        }
        else if (tt >= Fuga.FALA && tt < Fuga.FALA + Fuga.NFALAS * Fuga.POR_FALA) {
            int i = falaAtual(tt);
            chave = "necron.lanterna.fuga.fala_" + (i + 1);
            de = Fuga.FALA + i * Fuga.POR_FALA; ate = de + Fuga.POR_FALA;
        }
        if (chave == null) return;
        float a = Math.min(faixa(tt, de, de + 8), 1 - faixa(tt, ate - 8, ate));
        int al = Math.max(4, (int) (a * 255));
        Component txt = Component.translatable(chave, Component.translatable("corps.lanterna." + tropa));
        // falas longas quebram em duas linhas
        var linhas = mc.font.split(txt, w - 40);
        int y = h - fx / 2 - linhas.size() * (mc.font.lineHeight + 1) / 2;
        boolean fala = chave.contains("fala_") || chave.endsWith("aguenta") || chave.endsWith("segura") || chave.endsWith("alerta_3");
        boolean voz = chave.endsWith("alerta_1") || chave.endsWith("alerta_2");
        int corTxt = voz ? 0xC9A0FF : fala ? clarear(com.lanterna.vida.Emocoes.cor(tropa)) : 0xE6E6EE;
        for (var l : linhas) {
            g.drawCenteredString(mc.font, l, w / 2, y, (al << 24) | (corTxt & 0xFFFFFF));
            y += mc.font.lineHeight + 1;
        }
    }

    private static int clarear(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return ((r + (255 - r) * 45 / 100) << 16) | ((g + (255 - g) * 45 / 100) << 8) | (b + (255 - b) * 45 / 100);
    }

    private CenaFuga() {}
}
