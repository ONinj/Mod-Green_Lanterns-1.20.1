package com.lanterna.client.necron;

import com.lanterna.LanternaMod;
import com.lanterna.necron.Cenas;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.List;

/**
 * 0.58.0: as cutscenes no mundo da saga do Necron. Cada cena tem planos de câmera (posição e para onde olhar), faixas
 * de cinema, legendas com entrada e saída suaves, transições (branco, preto, roxo) e os sons dos momentos fortes.
 *
 *   queda      o Necron vencido: fica de pé, cai de joelhos e se desfaz em almas; a câmera sobe e mostra a cratera
 *   despertar  o Embrião acorda no Santuário, sobe pelo poço, desce do céu sobre a cratera e solta a onda de luz
 *   juramento  o Arauto ajoelha diante do Necron (linha corrompida)
 *   noite      o Embrião apagado entra no peito do Necron e a noite cobre a Terra
 *   espectro   a sombra do jogador se levanta do chão
 *   parallax   no escuro, um olho amarelo se abre (a visão de Korugar; e a versão curta, no anel negro)
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CenaMundo {
    private static String id = "";
    private static int t = -1, dur;
    private static Vec3[] a = new Vec3[0];
    private static Atores atores;

    public static void receber(String nome, List<Vec3> ancoras) {
        id = nome;
        a = ancoras.toArray(new Vec3[0]);
        dur = Cenas.duracao(nome);
        t = 0;
        var mc = Minecraft.getInstance();
        mc.getToasts().clear();
        if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) mc.setScreen(null);
    }

    public static boolean ativa() { return t >= 0; }

    private static Vec3 anc(int i) { return i < a.length ? a[i] : Minecraft.getInstance().player.position(); }

    private static float faixa(float x, float de, float ate) { return Mth.clamp((x - de) / (ate - de), 0, 1); }

    private static float suave(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }

    /** Para onde olhar: yaw e pitch do Minecraft. */
    private static double[] olhar(Vec3 cam, Vec3 alvo) {
        Vec3 d = alvo.subtract(cam);
        return new double[]{cam.x, cam.y, cam.z, Math.toDegrees(Math.atan2(-d.x, d.z)), Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)))};
    }

    /** Direção horizontal de a para b (normalizada). */
    private static Vec3 dir(Vec3 de, Vec3 para) {
        Vec3 d = para.subtract(de).multiply(1, 0, 1);
        return d.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : d.normalize();
    }

    private static Vec3 lado(Vec3 d) { return new Vec3(-d.z, 0, d.x); }

    // ===================== tick: sons e partículas da cena =====================

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || t < 0) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { t = -1; return; }
        t++;
        efeitos(mc);
        if (t > dur) { t = -1; Atores.ajoelhar = 0; }
    }

    private static void som(SoundEvent s, float vol, float tom) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, tom, vol));
    }

    private static void efeitos(Minecraft mc) {
        var l = mc.level;
        switch (id) {
            case "queda" -> {
                Vec3 n = anc(0);
                if (t == 95) som(SoundEvents.ANVIL_LAND, 0.6f, 0.5f);
                if (t > 140 && t < 210) for (int k = 0; k < 6; k++)
                    l.addParticle(t % 3 == 0 ? ParticleTypes.SOUL : ParticleTypes.SCULK_SOUL, n.x + (l.random.nextDouble() - 0.5) * 1.6,
                            n.y + 0.2 + l.random.nextDouble() * 2.6 * (1 - faixa(t, 150, 205)), n.z + (l.random.nextDouble() - 0.5) * 1.6, 0, 0.06 + l.random.nextDouble() * 0.05, 0);
                if (t == 140) som(SoundEvents.SOUL_ESCAPE, 1f, 0.6f);
                if (t == 205) { som(SoundEvents.BEACON_ACTIVATE, 1f, 1.2f); for (int k = 0; k < 80; k++)
                    l.addParticle(ParticleTypes.END_ROD, n.x, n.y + 1.2, n.z, (l.random.nextDouble() - 0.5) * 0.5, l.random.nextDouble() * 0.4, (l.random.nextDouble() - 0.5) * 0.5); }
            }
            case "despertar" -> {
                if (t < 100 && t % (t < 50 ? 20 : 10) == 0) som(SoundEvents.WARDEN_HEARTBEAT, 1f, 1.2f + t / 200f);
                if (t == 170) som(SoundEvents.BEACON_POWER_SELECT, 1f, 1.5f);
                if (t == 400) som(SoundEvents.END_PORTAL_SPAWN, 0.8f, 1.4f);
            }
            case "juramento" -> {
                if (t % 30 == 0 && t < 200) som(SoundEvents.WARDEN_HEARTBEAT, 0.9f, 0.6f);
                if (t == 200) som(SoundEvents.WITHER_SPAWN, 0.7f, 0.6f);
            }
            case "noite" -> {
                if (t == 150) som(SoundEvents.BEACON_DEACTIVATE, 1f, 0.5f);
                if (t == 160) som(SoundEvents.WITHER_SPAWN, 0.9f, 0.4f);
                if (t > 40 && t < 155) {
                    Vec3 e = embriaoEscuro(t);
                    l.addParticle(ParticleTypes.SCULK_SOUL, e.x + (l.random.nextDouble() - 0.5) * 0.6, e.y + (l.random.nextDouble() - 0.5) * 0.6, e.z + (l.random.nextDouble() - 0.5) * 0.6, 0, 0.01, 0);
                }
            }
            case "espectro" -> {
                if (t == 2) som(SoundEvents.SOUL_ESCAPE, 1f, 0.5f);
                if (t == 55) som(SoundEvents.WARDEN_EMERGE, 0.8f, 1.2f);
                if (t < 60) { Vec3 s = anc(0); for (int k = 0; k < 4; k++)
                    l.addParticle(ParticleTypes.SCULK_SOUL, s.x + (l.random.nextDouble() - 0.5), s.y + 0.1, s.z + (l.random.nextDouble() - 0.5), 0, 0.05, 0); }
            }
            case "parallax", "parallax_anel" -> {
                int ritmo = t < 100 ? 24 : 16;
                if (t > 20 && t % ritmo == 0) som(SoundEvents.WARDEN_HEARTBEAT, 0.8f, 1.6f);
                if (t == 60) som(SoundEvents.ELDER_GUARDIAN_CURSE, 0.35f, 0.7f);
            }
            default -> {}
        }
    }

    // ===================== câmera =====================

    public static double[] camera(Entity e, float pt) {
        var mc = Minecraft.getInstance();
        if (t < 0 || e != mc.player || mc.level == null) return null;
        float tt = t + pt;
        return switch (id) {
            case "queda" -> camQueda(tt);
            case "despertar" -> camDespertar(tt);
            case "juramento" -> camJuramento(tt);
            case "noite" -> camNoite(tt);
            case "espectro" -> camEspectro(tt);
            default -> null; // parallax: a tela fica escura (a câmera não importa)
        };
    }

    private static double[] camQueda(float tt) {
        Vec3 n = anc(0), frente = dir(n, anc(2)), lado = lado(frente);
        if (tt < 85) {
            // de baixo, de frente para ele, chegando devagar
            float k = suave(tt / 85f);
            double ang = -0.35 + 0.35 * k;
            Vec3 d = frente.scale(Math.cos(ang)).add(lado.scale(Math.sin(ang)));
            return olhar(n.add(d.scale(8 - 2.5 * k)).add(0, 0.6, 0), n.add(0, 2.7, 0));
        }
        if (tt < 150) {
            // de lado: ele cai de joelhos
            float k = faixa(tt, 85, 150);
            return olhar(n.add(lado.scale(4.6)).add(frente.scale(1.2 + k)).add(0, 1.3, 0), n.add(0, 1.9 - 0.9 * Atores.ajoelhar, 0));
        }
        // sobe e mostra a cratera inteira
        float k = suave(faixa(tt, 150, 230));
        Vec3 cam = n.add(frente.scale(6 + 24 * k)).add(lado.scale(3 * k)).add(0, 3 + 24 * k, 0);
        return olhar(cam, n.add(0, 1, 0).lerp(anc(1), k * 0.5));
    }

    /** Onde o Embrião está na cena do Despertar (o mesmo caminho que o mundo desenha). */
    private static Vec3 embriaoDespertar(float tt) {
        BlockPos c = BlockPos.containing(anc(0)), cr = BlockPos.containing(anc(1));
        return RenderFenda.posEmbriao(c, cr, tt);
    }

    private static double[] camDespertar(float tt) {
        Vec3 c = anc(0);
        if (tt < 100) {
            // gira em volta do Embrião, dentro da câmara
            double ang = tt * 0.012;
            Vec3 cam = c.add(Math.cos(ang) * 4.6, -2.6 + 0.6 * Math.sin(tt * 0.02), Math.sin(ang) * 4.6);
            return olhar(cam, embriaoDespertar(tt));
        }
        if (tt < 185) {
            // do fundo da câmara, olhando para cima: ele sobe pelo poço
            return olhar(c.add(2.5, -3.5, 1.5), embriaoDespertar(tt));
        }
        Vec3 cr = anc(1), vista = cr.add(40, 26, 6);
        if (tt < 400) return olhar(vista, embriaoDespertar(tt));
        // a onda: a câmera recua e sobe
        float k = suave(faixa(tt, 400, 470));
        return olhar(vista.lerp(cr.add(75, 58, 10), k), embriaoDespertar(tt).lerp(cr.add(0, 4, 0), k * 0.8));
    }

    private static double[] camJuramento(float tt) {
        Vec3 n = anc(0), j = anc(1), frente = dir(j, n), lado = lado(frente);
        if (tt < 75) return olhar(j.subtract(frente.scale(4.2)).add(lado.scale(1.6)).add(0, 1.4, 0), n.add(0, 3, 0));
        if (tt < 155) {
            float k = faixa(tt, 75, 155);
            return olhar(n.subtract(frente.scale(3.6 - 1.2 * k)).add(lado.scale(0.8)).add(0, 1.3, 0), n.add(0, 3.1, 0));
        }
        double ang = (tt - 155) * 0.03;
        return olhar(j.add(Math.cos(ang) * 2.2, 6.5, Math.sin(ang) * 2.2), j.add(0, 0.8, 0));
    }

    /** O Embrião apagado: sai das mãos do Arauto e entra no peito do Necron. */
    private static Vec3 embriaoEscuro(float tt) {
        Vec3 de = anc(2).add(0, 1.5, 0), ate = anc(0).add(0, 2.3, 0);
        return de.lerp(ate, suave(faixa(tt, 20, 150))).add(0, 0.8 * Math.sin(faixa(tt, 20, 150) * Math.PI), 0);
    }

    private static double[] camNoite(float tt) {
        Vec3 n = anc(0), j = anc(2), frente = dir(j, n), lado = lado(frente);
        Vec3 meio = n.lerp(j, 0.5);
        if (tt < 80) return olhar(meio.add(lado.scale(7)).add(0, 1.8, 0), meio.add(0, 1.8, 0));
        if (tt < 160) {
            float k = faixa(tt, 80, 160);
            return olhar(n.add(lado.scale(4.2 - k)).subtract(frente.scale(1.5)).add(0, 2.2, 0), embriaoEscuro(Math.min(tt, 150)));
        }
        float k = suave(faixa(tt, 160, 340));
        Vec3 cr = anc(1);
        Vec3 cam = n.add(lado.scale(4 + 40 * k)).add(0, 2.5 + 60 * k, 0);
        return olhar(cam, n.add(0, 2, 0).lerp(cr, k).add(0, -10 * faixa(tt, 340, 420), 0));
    }

    private static double[] camEspectro(float tt) {
        Vec3 s = anc(0), j = anc(1), frente = dir(s, j), lado = lado(frente);
        if (tt < 60) return olhar(s.add(frente.scale(3.2)).add(lado.scale(1.2)).add(0, 0.4, 0), s.add(0, 0.6 + 1.2 * suave(tt / 60f), 0));
        float k = suave(faixa(tt, 60, 110));
        return olhar(s.subtract(frente.scale(1.8)).add(lado.scale(0.9)).add(0, 2.0, 0), j.add(0, 1.4, 0).lerp(s.add(0, 1.6, 0), 0.2 * (1 - k)));
    }

    // ===================== mundo: o Necron vencido e o Embrião apagado =====================

    @SubscribeEvent
    public static void desenhar(RenderLevelStageEvent e) {
        if (t < 0 || e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!id.equals("queda") && !id.equals("noite")) return;
        if (atores == null) atores = new Atores();
        float tt = t + e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        var ps = e.getPoseStack();
        var buf = Minecraft.getInstance().renderBuffers().bufferSource();
        if (id.equals("queda") && tt < 205) {
            Vec3 n = anc(0), frente = dir(n, anc(2));
            Atores.ajoelhar = suave(faixa(tt, 90, 132));
            float some = faixa(tt, 145, 205);
            ps.pushPose();
            ps.translate(n.x - cam.x, n.y - cam.y - 0.95 * Atores.ajoelhar - 0.6 * some, n.z - cam.z);
            // de frente para quem lutou (o modelo do GeckoLib olha para -z)
            ps.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(Math.atan2(frente.x, frente.z)) + 180));
            float esc = 1.6f * (1 - 0.25f * some);
            ps.scale(esc, esc, esc);
            atores.necron(ps, buf, tt, 0.2f + 0.8f * some, 1f - some);
            ps.popPose();
            buf.endBatch();
        }
        if (id.equals("noite") && tt > 20 && tt < 152) {
            Vec3 p = embriaoEscuro(tt);
            float esc = 0.9f * (1 - 0.75f * faixa(tt, 100, 150));
            ps.pushPose();
            ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            ps.mulPose(Axis.YP.rotationDegrees(tt * 2));
            ps.scale(esc, esc, esc);
            atores.embriao(ps, buf, tt * 0.3f, 0.16f, 0.1f, 0.22f, 0.05f);
            ps.popPose();
            buf.endBatch();
        }
    }

    // ===================== tela: faixas, legendas, transições e a visão de Parallax =====================

    private record Legenda(int de, int ate, String chave) {}

    private static Legenda[] legendas() {
        return switch (id) {
            case "queda" -> new Legenda[]{new Legenda(20, 85, "necron.lanterna.cena_queda_1"), new Legenda(92, 148, "necron.lanterna.cena_queda_2"),
                    new Legenda(160, 226, "necron.lanterna.cena_queda_3")};
            case "despertar" -> new Legenda[]{new Legenda(15, 95, "necron.lanterna.desperta_1"), new Legenda(110, 168, "necron.lanterna.desperta_2"),
                    new Legenda(300, 395, "necron.lanterna.cena_despertar_3"), new Legenda(405, 465, "necron.lanterna.desperta_3")};
            case "juramento" -> new Legenda[]{new Legenda(4, 74, "necron.lanterna.c1_fala_1"), new Legenda(80, 152, "necron.lanterna.c1_fala_2"),
                    new Legenda(158, 222, "necron.lanterna.c1_fala_3")};
            case "noite" -> new Legenda[]{new Legenda(4, 76, "necron.lanterna.c4_fala_1"), new Legenda(84, 156, "necron.lanterna.c4_fala_2"),
                    new Legenda(250, 330, "necron.lanterna.c4_epilogo_1")};
            case "espectro" -> new Legenda[]{new Legenda(52, 108, "necron.lanterna.espectro_fala")};
            case "parallax" -> new Legenda[]{new Legenda(70, 190, "necron.lanterna.parallax_1")};
            case "parallax_anel" -> new Legenda[]{new Legenda(40, 116, "necron.lanterna.c4_epilogo_3")};
            default -> new Legenda[0];
        };
    }

    @SubscribeEvent
    public static void hud(RenderGuiOverlayEvent.Pre e) {
        if (t < 0) return;
        if (e.getOverlay() == net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.type()) tela(e.getGuiGraphics(), t + e.getPartialTick());
        // títulos (o servidor manda alguns nos momentos fortes) continuam aparecendo
        if (e.getOverlay() != net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.TITLE_TEXT.type()) e.setCanceled(true);
    }

    private static void tela(GuiGraphics g, float tt) {
        var mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        boolean parallax = id.startsWith("parallax");
        // transições
        int fadeCor = -1;
        float fade = 0;
        switch (id) {
            case "despertar" -> { fade = faixa(tt, 168, 180) * (1 - faixa(tt, 188, 205)); fadeCor = 0xFFFFFF; }
            case "queda" -> { fade = faixa(tt, 200, 212) * 0.85f * (1 - faixa(tt, 214, 230)); fadeCor = 0xFFFFFF; }
            case "juramento" -> { fade = faixa(tt, 196, 202) * 0.7f * (1 - faixa(tt, 204, 220)); fadeCor = 0x3A0A55; }
            case "noite" -> { fade = faixa(tt, 148, 158) * (1 - faixa(tt, 162, 185)); fadeCor = 0x1A0026; }
            default -> {}
        }
        // começo e fim suaves
        float borda = faixa(tt, 0, 10) * (1 - faixa(tt, dur - 10, dur));
        if (parallax) {
            float preto = faixa(tt, 0, 18) * (1 - faixa(tt, dur - 18, dur));
            g.fill(0, 0, w, h, ((int) (preto * 255) << 24));
            olho(g, w, h, tt, preto);
        }
        int fx = (int) (h / 9 * borda);
        g.fill(0, 0, w, fx, 0xFF000000);
        g.fill(0, h - fx, w, h, 0xFF000000);
        if (fade > 0.01f) g.fill(0, 0, w, h, ((int) (fade * 255) << 24) | (fadeCor & 0xFFFFFF));
        // legendas
        for (Legenda lg : legendas()) {
            if (tt < lg.de || tt > lg.ate) continue;
            float al = Math.min(faixa(tt, lg.de, lg.de + 8), 1 - faixa(tt, lg.ate - 8, lg.ate));
            int a8 = Math.max(4, (int) (al * 255));
            var linhas = mc.font.split(Component.translatable(lg.chave), w - 60);
            int y = h - fx / 2 - linhas.size() * (mc.font.lineHeight + 1) / 2;
            if (parallax) y = h * 3 / 4;
            int cor = parallax ? 0xF2D35A : id.equals("juramento") || id.equals("noite") ? 0xD8B8F0 : 0xF0F0F0;
            for (var l : linhas) { g.drawCenteredString(mc.font, l, w / 2, y, (a8 << 24) | cor); y += mc.font.lineHeight + 1; }
        }
    }

    /** O olho de Parallax: amarelo, de pupila em fenda, abrindo devagar no escuro. */
    private static void olho(GuiGraphics g, int w, int h, float tt, float preto) {
        boolean anel = id.equals("parallax_anel");
        float abre = suave(faixa(tt, anel ? 20 : 30, anel ? 60 : 100)) * (1 - faixa(tt, dur - 22, dur - 6));
        if (abre <= 0.01f) return;
        int cx = w / 2, cy = anel ? h / 2 : h * 2 / 5;
        float larg = (anel ? 0.18f : 0.32f) * w, alt = larg * 0.42f * abre;
        float pulso = 0.85f + 0.15f * Mth.sin(tt * 0.18f);
        // brilho em volta
        for (int k = 6; k >= 1; k--) {
            float s = 1 + k * 0.09f;
            elipse(g, cx, cy, larg * s / 2, alt * s / 2 + k, (int) (18 * pulso * preto) << 24 | 0xE0A010);
        }
        // a íris, do dourado ao âmbar
        elipse(g, cx, cy, larg / 2, alt / 2, (int) (235 * preto) << 24 | 0xF2C230);
        elipse(g, cx, cy, larg * 0.34f, alt * 0.48f, (int) (240 * preto) << 24 | 0xE89A12);
        // a pupila em fenda
        float pw = larg * 0.035f * (1.3f - 0.5f * pulso);
        elipse(g, cx, cy, pw, alt * 0.48f, (int) (250 * preto) << 24 | 0x140A00);
        // o reflexo
        g.fill((int) (cx - larg * 0.12f), (int) (cy - alt * 0.22f), (int) (cx - larg * 0.12f + 3), (int) (cy - alt * 0.22f + 2), (int) (200 * preto * abre) << 24 | 0xFFF6D0);
        // faíscas de medo subindo
        var r = net.minecraft.util.RandomSource.create(7);
        for (int i = 0; i < 40; i++) {
            float x = r.nextFloat() * w, vel = 0.2f + r.nextFloat() * 0.6f;
            float y = (r.nextFloat() * h - tt * vel * 2) % h;
            if (y < 0) y += h;
            int al = (int) (120 * preto * abre * r.nextFloat());
            g.fill((int) x, (int) y, (int) x + 1, (int) y + 1, al << 24 | 0xF2C230);
        }
    }

    private static void elipse(GuiGraphics g, int cx, int cy, float rx, float ry, int argb) {
        if (rx < 0.5f || ry < 0.5f) return;
        for (int y = (int) -ry; y <= (int) ry; y++) {
            float k = 1 - (y * y) / (ry * ry);
            if (k <= 0) continue;
            int hw = (int) (rx * Math.sqrt(k));
            g.fill(cx - hw, cy + y, cx + hw, cy + y + 1, argb);
        }
    }

    // ===================== o jogador parado =====================

    @SubscribeEvent
    public static void parado(MovementInputUpdateEvent e) {
        if (t < 0) return;
        var in = e.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = in.jumping = false;
        // no Juramento ele se ajoelha
        in.shiftKeyDown = id.equals("juramento") && t > 60;
    }

    @SubscribeEvent
    public static void semMao(net.minecraftforge.client.event.RenderHandEvent e) {
        if (t >= 0) e.setCanceled(true);
    }

    private CenaMundo() {}
}
