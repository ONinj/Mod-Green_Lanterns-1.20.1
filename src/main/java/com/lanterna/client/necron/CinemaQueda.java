package com.lanterna.client.necron;

import com.lanterna.LanternaMod;
import com.lanterna.necron.Pedidos;
import com.lanterna.vida.Emocoes;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 0.55.1: a queda de uma Tropa, vista do espaço. O planeta brilha com a luz da Tropa; fios escuros chegam de todos os
 * lados e se enrolam nele; a atmosfera fica roxa, o planeta escurece e a luz da Tropa se apaga num último clarão.
 */
public final class CinemaQueda extends Screen {
    private final String tropa, planeta;
    private final int cor;
    private int ticks;
    private float pt;
    private final float[][] estrelas = new float[1500][];
    private final float[][] fios = new float[60][];

    public CinemaQueda(String tropa, String planeta) {
        super(Component.translatable("necron.lanterna.titulo"));
        this.tropa = tropa;
        this.planeta = planeta;
        this.cor = Emocoes.cor(tropa);
        RandomSource r = RandomSource.create(planeta.hashCode());
        for (int i = 0; i < estrelas.length; i++) {
            double x, y, z, l;
            do { x = r.nextFloat() * 2 - 1; y = r.nextFloat() * 2 - 1; z = r.nextFloat() * 2 - 1; l = x * x + y * y + z * z; } while (l > 1 || l < 0.01);
            l = Math.sqrt(l);
            estrelas[i] = new float[]{(float) (x / l), (float) (y / l), (float) (z / l), 0.7f + r.nextFloat() * 1.6f, 0.5f + r.nextFloat() * 0.5f};
        }
        for (int i = 0; i < fios.length; i++) {
            double a = r.nextDouble() * Math.PI * 2, b = Math.acos(r.nextDouble() * 2 - 1);
            fios[i] = new float[]{(float) (Math.sin(b) * Math.cos(a)), (float) Math.cos(b), (float) (Math.sin(b) * Math.sin(a)), r.nextFloat() * 40, 0.6f + r.nextFloat() * 0.8f};
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    private float t() { return ticks + pt; }
    private static float faixa(float x, float a, float b) { return Mth.clamp((x - a) / (b - a), 0, 1); }

    @Override
    public void tick() {
        ticks++;
        var sm = Minecraft.getInstance().getSoundManager();
        if (ticks == 2) sm.play(SimpleSoundInstance.forUI(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), 0.7f, 1f));
        if (ticks == 40) sm.play(SimpleSoundInstance.forUI(SoundEvents.WITHER_AMBIENT, 0.5f, 0.8f));
        if (ticks == 120) sm.play(SimpleSoundInstance.forUI(SoundEvents.BEACON_DEACTIVATE, 0.5f, 1f));
        if (ticks == 150) sm.play(SimpleSoundInstance.forUI(SoundEvents.GENERIC_EXPLODE, 0.6f, 0.8f));
        if (ticks >= Pedidos.DUR_QUEDA) onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        pt = Mth.clamp(partial, 0, 1);
        float t = t();
        var mc = Minecraft.getInstance();
        g.fill(0, 0, width, height, 0xFF000000);
        g.flush();
        if (!CinemaNecron.texturaPronta) CinemaNecron.criarBrilho();
        Matrix4f projAntes = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting ordemAntes = RenderSystem.getVertexSorting();
        int lw = mc.getWindow().getWidth(), lh = mc.getWindow().getHeight();
        RenderSystem.setProjectionMatrix(new Matrix4f().setPerspective((float) Math.toRadians(50), lw / (float) Math.max(1, lh), 0.5f, 4000f), VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        FogRenderer.setupNoFog();
        RenderSystem.clear(256, Minecraft.ON_OSX);

        // a câmera se aproxima devagar, girando
        float k = faixa(t, 0, Pedidos.DUR_QUEDA);
        double ang = 0.6 + k * 0.5, dist = 380 - 90 * k;
        Vec3 cam = new Vec3(Math.sin(ang) * dist, 60 - 30 * k, Math.cos(ang) * dist);
        Matrix4f vista = new Matrix4f().lookAt(cam.toVector3f(), new Vector3f(), new Vector3f(0, 1, 0));
        Matrix4f giro = new Matrix4f(vista).setTranslation(0, 0, 0);
        Vector3f dir = new Vector3f(vista.m00(), vista.m10(), vista.m20()), cima = new Vector3f(vista.m01(), vista.m11(), vista.m21());

        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, CinemaNecron.BRILHO);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (float[] s : estrelas) CinemaNecron.sprite(b, giro, dir, cima, s[0] * 900, s[1] * 900, s[2] * 900, s[3], s[4], s[4], s[4], 1f);
        BufferUploader.drawWithShader(b.end());

        // o planeta escurecendo
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        float escuro = faixa(t, 50, 160);
        float tint = 1 - 0.68f * escuro;
        ResourceLocation tex = LanternaMod.id("textures/space/" + planeta + ".png");
        CinemaNecron.esfera(vista, Vec3.ZERO, 100, tex, t * 0.0006f, 0, 64, null, tint, tint * (1 - 0.3f * escuro), tint * (1 - 0.1f * escuro), 0.15f);
        int atmo = mistura(cor, 0x6A1AA0, escuro);
        CinemaNecron.atmosfera(vista, cam, Vec3.ZERO, 106, atmo, 0.9f + 0.4f * escuro);

        // fios escuros vindo de todo lado e se enrolando no planeta; e a luz da Tropa piscando até apagar
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, CinemaNecron.BRILHO);
        RenderSystem.defaultBlendFunc();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (float[] f : fios) {
            float chegada = faixa(t, 20 + f[3], 110 + f[3]);
            if (chegada <= 0) continue;
            for (int i = 0; i < 10; i++) {
                float s = chegada - i * 0.035f;
                if (s <= 0) continue;
                double r = 420 - 312 * s;
                double gira = s * 2.2 * f[4];
                double x = f[0] * Math.cos(gira) - f[2] * Math.sin(gira), z = f[0] * Math.sin(gira) + f[2] * Math.cos(gira);
                CinemaNecron.sprite(b, vista, dir, cima, x * r, f[1] * r, z * r, 7 - i * 0.4f, 0.04f, 0f, 0.07f, 0.85f * (1 - i / 10f));
            }
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float vida = 1 - faixa(t, 110, 150);
        float pisca = 0.7f + 0.3f * Mth.sin(t * (0.3f + escuro));
        Vec3 bat = cam.normalize().scale(101);
        CinemaNecron.sprite(b, vista, dir, cima, bat.x, bat.y, bat.z, 9 + 4 * pisca, r(cor), g(cor), bl(cor), vida * pisca);
        if (t > 145 && t < 175) {
            float f = (t - 145) / 30f;
            CinemaNecron.sprite(b, vista, dir, cima, bat.x, bat.y, bat.z, 20 + 160 * f, r(cor), g(cor), bl(cor), 1 - f);
        }
        BufferUploader.drawWithShader(b.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        mv.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(projAntes, ordemAntes);
        com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
        RenderSystem.clear(256, Minecraft.ON_OSX);

        // escurecimentos, faixas e legendas
        float preto = Math.max(1 - faixa(t, 0, 25), faixa(t, Pedidos.DUR_QUEDA - 25, Pedidos.DUR_QUEDA));
        if (preto > 0) g.fill(0, 0, width, height, (int) (preto * 255) << 24);
        int fx = height / 9;
        g.fill(0, 0, width, fx, 0xFF000000);
        g.fill(0, height - fx, width, height, 0xFF000000);
        Component c = null;
        float de = 0, ate = 0;
        Component nomeTropa = Component.translatable("corps.lanterna." + tropa), nomePlaneta = Component.translatable("planet.lanterna." + planeta);
        if (t > 15 && t < 105) { c = Component.translatable("necron.lanterna.queda_1", nomePlaneta, nomeTropa); de = 15; ate = 105; }
        else if (t > 110 && t < Pedidos.DUR_QUEDA - 10) { c = Component.translatable("necron.lanterna.queda_2", nomeTropa); de = 110; ate = Pedidos.DUR_QUEDA - 10; }
        if (c != null) {
            String chave = ((net.minecraft.network.chat.contents.TranslatableContents) c.getContents()).getKey();
            if (!chave.equals(ultimaFala)) { ultimaFala = chave; com.lanterna.client.voz.Falas.falar(chave, "narrador"); }
            int al = Math.max(4, (int) (Math.min(faixa(t, de, de + 10), 1 - faixa(t, ate - 10, ate)) * 255));
            g.drawCenteredString(font, c, width / 2, height - fx / 2 - font.lineHeight / 2, (al << 24) | 0xE6E0F0);
        }
    }

    private String ultimaFala = "";

    private static int mistura(int a, int b, float k) {
        int r = (int) Mth.lerp(k, (a >> 16) & 255, (b >> 16) & 255), g = (int) Mth.lerp(k, (a >> 8) & 255, (b >> 8) & 255), bl = (int) Mth.lerp(k, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    private static float r(int c) { return ((c >> 16) & 255) / 255f; }
    private static float g(int c) { return ((c >> 8) & 255) / 255f; }
    private static float bl(int c) { return (c & 255) / 255f; }
}
