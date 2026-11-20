package com.lanterna.client.necron;

import com.lanterna.LanternaMod;
import com.lanterna.item.PowerRingItem;
import com.lanterna.vida.Emocoes;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.54: a cutscene do começo da saga do Necron. É uma "tela de cinema": o mundo some e a cena é desenhada aqui, com
 * câmera própria, planetas, partículas, legendas e sons. Nada disso vai para o servidor (cada jogador vê a sua).
 *
 * Planos (em ticks, 20 por segundo):
 *   0-240     o Embrião da Vida brilhando no espaço; ele escurece
 *   240-680   o jogador estende a mão; o Anel da Vida se desfaz em sete anéis que giram em volta dele e partem para o
 *             espaço; só o anel da Tropa dele volta para o dedo
 *   680-770   cartão: o Nulo caiu, a prisão se rompeu
 *   770-960   Ryut racha e se parte ao meio; de dentro sai o Necron
 *   960-1070  o rosto do Necron (título)
 *   1070-1270 o Necron voando pelo espaço até a Terra
 *   1270-1450 a Terra: ele cai como um cometa negro; o impacto
 *   1450-1520 fim
 */
public final class CinemaNecron extends Screen {
    public static final int DURACAO = com.lanterna.necron.SagaNecron.DURACAO;
    // marcos do plano do jogador
    static final int B0 = 240, ESTENDE = 280, DESFAZ = 352, PUM = 492, VOLTA0 = 568, VOLTA = 618, B1 = 680;
    static final int D0 = 770, RACHA = 880, D1 = 960, E1 = 1070, F1 = 1270, IMPACTO = 1392, G1 = 1450;

    static final ResourceLocation BRILHO = LanternaMod.id("cinema/brilho");
    private static final ResourceLocation TEX_TERRA = LanternaMod.id("textures/space/earth.png");
    private static final ResourceLocation TEX_RYUT = LanternaMod.id("textures/space/ryut.png");
    static boolean texturaPronta;

    private final String tropa;
    private final int indiceTropa, corTropa;
    private final boolean revendo;
    private int ticks;
    private float pt;
    private final RandomSource rnd = RandomSource.create(4242L);
    private final List<Parte> partes = new ArrayList<>();
    private final float[][] estrelas;
    private final float[][] nebulosas;
    private final ItemStack[] aneis = new ItemStack[7];
    private final Vec3[] rumos = new Vec3[7];

    private static Atores atores;

    private CinemaNecron(String tropa, boolean revendo) {
        super(Component.translatable("necron.lanterna.titulo"));
        this.tropa = tropa;
        this.revendo = revendo;
        int idx = 3;
        for (int i = 0; i < 7; i++) if (Emocoes.ASPECTOS[i].equals(tropa)) idx = i;
        this.indiceTropa = idx;
        this.corTropa = Emocoes.cor(Emocoes.ASPECTOS[idx]);
        for (int i = 0; i < 7; i++) {
            String a = Emocoes.ASPECTOS[i];
            aneis[i] = PowerRingItem.create(LanternaMod.id(a), Emocoes.cor(a), 100);
            double ang = Math.PI * 2 * i / 7 + 0.3;
            rumos[i] = new Vec3(Math.cos(ang), 0.55 + 0.25 * Math.sin(i * 1.7), Math.sin(ang)).normalize();
        }
        RandomSource r = RandomSource.create(10842L);
        estrelas = new float[2600][];
        for (int i = 0; i < estrelas.length; i++) {
            double x, y, z, l;
            do { x = r.nextFloat() * 2 - 1; y = r.nextFloat() * 2 - 1; z = r.nextFloat() * 2 - 1; l = x * x + y * y + z * z; } while (l > 1 || l < 0.01);
            l = Math.sqrt(l);
            int tipo = r.nextInt(10);
            float b = 0.5f + r.nextFloat() * 0.5f;
            float cr = b, cg = b, cb = b;
            if (tipo == 0) { cr *= 0.7f; cg *= 0.82f; } else if (tipo == 1) { cb *= 0.7f; } else if (tipo == 2) { cg *= 0.75f; cb *= 0.65f; }
            estrelas[i] = new float[]{(float) (x / l), (float) (y / l), (float) (z / l), 0.6f + r.nextFloat() * 1.8f, cr, cg, cb, r.nextFloat() * 6.28f};
        }
        nebulosas = new float[][]{
                {-0.6f, 0.3f, -0.74f, 520, 0.45f, 0.25f, 0.75f}, {0.7f, -0.2f, -0.68f, 420, 0.2f, 0.35f, 0.8f},
                {0.1f, 0.6f, 0.8f, 600, 0.6f, 0.2f, 0.45f}, {-0.8f, -0.4f, 0.45f, 460, 0.15f, 0.45f, 0.6f},
                {0.4f, 0.85f, -0.3f, 380, 0.5f, 0.4f, 0.7f}};
    }

    /** O pacote do servidor abre a cena. */
    public static void abrir(String tropa, boolean revendo) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        com.lanterna.client.flight.Autopilot.destravar();
        com.lanterna.client.flight.Autopilot.stop(null);
        mc.setScreen(new CinemaNecron(tropa, revendo));
    }

    /** Este cliente está vendo a cena. */
    public static boolean ativa() {
        return Minecraft.getInstance().screen instanceof CinemaNecron;
    }

    /** Tick da cena aberta (-1 sem cena); usado pelos testes. */
    public static int tempoAtual() {
        return Minecraft.getInstance().screen instanceof CinemaNecron c ? c.ticks : -1;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return revendo; }

    // ===================== tempo =====================

    @Override
    public void tick() {
        ticks++;
        sons(ticks);
        emitir(ticks);
        for (int i = partes.size() - 1; i >= 0; i--) {
            Parte p = partes.get(i);
            p.idade++;
            p.x += p.vx; p.y += p.vy; p.z += p.vz;
            p.vx *= p.atrito; p.vy *= p.atrito; p.vz *= p.atrito;
            if (p.idade >= p.vida) partes.remove(i);
        }
        if (ticks >= DURACAO) onClose();
    }

    private float t() { return ticks + pt; }

    private static float suave(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
    private static float faixa(float t, float a, float b) { return suave((t - a) / (b - a)); }

    // ===================== desenho =====================

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        this.pt = Mth.clamp(partial, 0, 1);
        Minecraft mc = Minecraft.getInstance();
        float t = t();
        g.fill(0, 0, width, height, 0xFF000000);
        g.flush();
        if (!texturaPronta) criarBrilho();
        if (atores == null) atores = new Atores();

        Matrix4f projAntes = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting ordemAntes = RenderSystem.getVertexSorting();
        int lw = mc.getWindow().getWidth(), lh = mc.getWindow().getHeight();
        Camera cam = camera(t);
        Matrix4f proj = new Matrix4f().setPerspective((float) Math.toRadians(cam.fov), lw / (float) Math.max(1, lh), 0.05f, 4000f);
        RenderSystem.setProjectionMatrix(proj, VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        FogRenderer.setupNoFog();
        RenderSystem.clear(256, Minecraft.ON_OSX);

        Matrix4f vista = new Matrix4f().lookAt(cam.pos.toVector3f(), cam.alvo.toVector3f(), cam.cima.toVector3f());
        Matrix4f giro = new Matrix4f(vista).setTranslation(0, 0, 0);
        Vector3f direita = new Vector3f(vista.m00(), vista.m10(), vista.m20());
        Vector3f cima = new Vector3f(vista.m01(), vista.m11(), vista.m21());

        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        fundo(giro, direita, cima, t);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        planetas(vista, cam, direita, cima, t);
        RenderSystem.clear(256, Minecraft.ON_OSX);

        // atores (modelos 3D, jogador, anéis)
        PoseStack ps = new PoseStack();
        ps.last().pose().set(vista);
        ps.last().normal().set(new Matrix3f(vista));
        Lighting.setupLevel(giro);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        RenderSystem.enableDepthTest();
        atores(ps, buf, t);
        buf.endBatch();

        // partículas e brilhos por cima (com profundidade, sem escrever nela)
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        efeitos(vista, cam, direita, cima, t);
        particulas(vista, direita, cima);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 1);

        mv.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(projAntes, ordemAntes);
        Lighting.setupFor3DItems();
        RenderSystem.clear(256, Minecraft.ON_OSX);

        textos(g, t);
    }

    // ===================== câmera =====================

    private record Camera(Vec3 pos, Vec3 alvo, Vec3 cima, float fov) {}

    private static Vec3 lerp(float k, Vec3 a, Vec3 b) { return a.add(b.subtract(a).scale(k)); }

    private Camera camera(float t) {
        Vec3 up = new Vec3(0, 1, 0);
        Camera c;
        if (t < B0) {
            // o Embrião: aproximação lenta girando em volta dele
            float k = t / B0;
            double a = Math.toRadians(-30 + 70 * k);
            double r = 4.6 - 1.6 * suave(k);
            c = new Camera(new Vec3(Math.sin(a) * r, 0.9 + 0.5 * Math.sin(k * 2), Math.cos(a) * r), new Vec3(0, 0.7, 0), up, 50);
        } else if (t < B1) {
            Vec3 alvo = new Vec3(0, 1.1, 0);
            if (t < DESFAZ) {
                // de frente, aproximando da mão que se estende
                float k = faixa(t, B0, DESFAZ);
                double a = Math.toRadians(25 - 45 * k);
                double r = 3.6 - 1.2 * k;
                c = new Camera(new Vec3(Math.sin(a) * r, 1.5 - 0.15 * k, Math.cos(a) * r), lerp(k, alvo, mao()), up, 55);
            } else if (t < PUM) {
                // os sete anéis girando: a câmera gira junto, de baixo
                float k = faixa(t, DESFAZ, PUM);
                double a = Math.toRadians(-20 + 160 * k);
                double r = 3.4 + 0.6 * k;
                c = new Camera(new Vec3(Math.sin(a) * r, 0.4 + 0.5 * k, Math.cos(a) * r), alvo, up, 62);
            } else if (t < VOLTA0) {
                // "pum": a câmera recua e olha para cima, para os rastros
                float k = faixa(t, PUM, VOLTA0);
                c = new Camera(new Vec3(0.8, 0.6 - 0.3 * k, 4.2 + 5 * k), alvo.add(0, 2.5 * k, 0), up, 62 + 8 * k);
            } else {
                // o anel volta: de perto da mão
                float k = faixa(t, VOLTA0, B1);
                double a = Math.toRadians(50 - 30 * k);
                double r = 2.4 + 1.4 * k;
                c = new Camera(new Vec3(Math.sin(a) * r, 1.6, Math.cos(a) * r), lerp(k, mao(), alvo), up, 55);
            }
        } else if (t < D0) {
            c = new Camera(new Vec3(0, 0, 10), Vec3.ZERO, up, 60);
        } else if (t < RACHA + 50) {
            // Ryut de longe, aproximando
            float k = faixa(t, D0, RACHA + 50);
            c = new Camera(new Vec3(150 - 30 * k, 60 - 15 * k, 700 - 160 * k), Vec3.ZERO, up, 55);
        } else if (t < D1) {
            // dentro da fenda: o Necron sai das duas metades
            float k = faixa(t, RACHA + 50, D1);
            c = new Camera(new Vec3(6 - 4 * k, 3 + 1 * k, 70 - 10 * k), new Vec3(0, 0, -40 + 40 * suave(k)), up, 60);
        } else if (t < E1) {
            // o rosto
            float k = faixa(t, D1, E1);
            double a = Math.toRadians(-20 + 30 * k);
            double r = 3.0 - 0.8 * k;
            c = new Camera(new Vec3(Math.sin(a) * r, 2.75 + 0.1 * k, Math.cos(a) * r), new Vec3(0, 2.6, 0), up, 48);
        } else if (t < F1) {
            // voando: câmera de lado, girando para trás dele
            float k = faixa(t, E1, F1);
            double a = Math.toRadians(70 + 100 * k);
            double r = 4.2 + 1.0 * Math.sin(k * 3);
            c = new Camera(new Vec3(Math.sin(a) * r, 1.4 + 0.6 * k, Math.cos(a) * r), new Vec3(0, 1.2, 0), up, 58);
        } else {
            // a Terra
            float k = faixa(t, F1, G1);
            c = new Camera(new Vec3(-140 + 60 * k, 260 - 60 * k, 1600 - 120 * k), new Vec3(0, 60, 0), up, 52);
        }
        // tremida (rachadura, partida dos anéis, impacto)
        float tremor = 0;
        if (t > RACHA - 70 && t < RACHA + 40) tremor = t < RACHA ? 0.35f * faixa(t, RACHA - 70, RACHA) : 1.4f * (1 - faixa(t, RACHA, RACHA + 40));
        if (t > PUM && t < PUM + 16) tremor = 0.06f * (1 - (t - PUM) / 16f);
        if (t > IMPACTO && t < IMPACTO + 40) tremor = 6f * (1 - (t - IMPACTO) / 40f);
        if (tremor > 0) {
            float s = t * 1.7f;
            Vec3 d = new Vec3(Mth.sin(s * 3.1f), Mth.sin(s * 2.3f + 1), Mth.sin(s * 2.7f + 2)).scale(tremor * (t < D0 ? 1 : t < E1 ? 2.5 : 1));
            c = new Camera(c.pos.add(d), c.alvo.add(d.scale(0.5)), c.cima, c.fov);
        }
        return c;
    }

    // ===================== fundo: estrelas e nebulosas =====================

    private void fundo(Matrix4f giro, Vector3f dir, Vector3f cima, float t) {
        float fade = 1f;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, BRILHO);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (float[] n : nebulosas) {
            float tint = t >= D0 && t < E1 ? 0.6f : 1f;
            sprite(b, giro, dir, cima, n[0] * 900, n[1] * 900, n[2] * 900, n[3], n[4] * tint, n[5] * tint, n[6], 0.22f * fade);
        }
        boolean voando = t >= E1 && t < F1;
        for (float[] s : estrelas) {
            float pisca = 0.75f + 0.25f * Mth.sin(t * 0.15f + s[7]);
            sprite(b, giro, dir, cima, s[0] * 900, s[1] * 900, s[2] * 900, s[3] * (voando ? 1.2f : 1f), s[4], s[5], s[6], pisca);
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.defaultBlendFunc();
    }

    static void sprite(BufferBuilder b, Matrix4f m, Vector3f dir, Vector3f cima, double x, double y, double z, float tam,
                               float r, float g, float bl, float a) {
        float rx = dir.x * tam, ry = dir.y * tam, rz = dir.z * tam;
        float ux = cima.x * tam, uy = cima.y * tam, uz = cima.z * tam;
        b.vertex(m, (float) x - rx - ux, (float) y - ry - uy, (float) z - rz - uz).uv(0, 1).color(r, g, bl, a).endVertex();
        b.vertex(m, (float) x + rx - ux, (float) y + ry - uy, (float) z + rz - uz).uv(1, 1).color(r, g, bl, a).endVertex();
        b.vertex(m, (float) x + rx + ux, (float) y + ry + uy, (float) z + rz + uz).uv(1, 0).color(r, g, bl, a).endVertex();
        b.vertex(m, (float) x - rx + ux, (float) y - ry + uy, (float) z - rz + uz).uv(0, 0).color(r, g, bl, a).endVertex();
    }

    // ===================== planetas =====================

    private static final Vec3 SOL = new Vec3(0.6, 0.35, 0.72).normalize();

    private void planetas(Matrix4f m, Camera cam, Vector3f dir, Vector3f cima, float t) {
        if (t >= B0 && t < B1) {
            // a Terra lá embaixo, atrás do jogador
            esfera(m, new Vec3(0, -1100, -1500), 700, TEX_TERRA, t * 0.0002f, 0, 64, null, 1, 1, 1, 0.18f);
            atmosfera(m, cam.pos, new Vec3(0, -1100, -1500), 735, 0x6FB5FF, 0.8f);
        } else if (t >= D0 && t < D1) {
            ryut(m, cam, t);
        } else if (t >= D1 && t < E1) {
            // atrás do rosto, Ryut partida bem longe
            Vec3 c = new Vec3(-180, 40, 900);
            float sep = 60;
            Quaternionf q = new Quaternionf().rotationY((float) Math.toRadians(90));
            metade(m, c, 160, sep, q, true, t);
            metade(m, c, 160, sep, q, false, t);
        } else if (t >= E1 && t < F1) {
            // a Terra aparecendo à frente (o Necron voa para -z)
            float k = faixa(t, E1, F1);
            Vec3 c = new Vec3(30, -40, -2600 + 1500 * k);
            esfera(m, c, 600, TEX_TERRA, t * 0.0002f, 0, 64, null, 1, 1, 1, 0.18f);
            atmosfera(m, cam.pos, c, 630, 0x6FB5FF, 0.8f);
        } else if (t >= F1) {
            esfera(m, Vec3.ZERO, 600, TEX_TERRA, 0.3f + t * 0.0001f, 0, 64, null, 1, 1, 1, 0.16f);
            atmosfera(m, cam.pos, Vec3.ZERO, 632, 0x6FB5FF, 0.9f);
        }
    }

    /** Ryut: inteira, rachando (luz roxa pelas fendas), e se partindo ao meio. */
    private void ryut(Matrix4f m, Camera cam, float t) {
        float sep = t < RACHA ? 0 : (float) (Math.pow((t - RACHA) / 80.0, 0.7) * 70);
        Quaternionf q = new Quaternionf().rotationY((float) Math.toRadians(90)).rotateZ((float) Math.toRadians(8));
        if (sep <= 0.01f) {
            esfera(m, Vec3.ZERO, 160, TEX_RYUT, t * 0.0004f, 0, 64, q, 1, 1, 1, 0.12f);
            atmosfera(m, cam.pos, Vec3.ZERO, 168, 0x8A8A9A, 0.5f);
        } else {
            metade(m, Vec3.ZERO, 160, sep, q, true, t);
            metade(m, Vec3.ZERO, 160, sep, q, false, t);
        }
    }

    private void metade(Matrix4f m, Vec3 c, float r, float sep, Quaternionf q, boolean a, float t) {
        // cada metade se afasta e gira um pouco
        Quaternionf qq = new Quaternionf(q).rotateX((float) Math.toRadians((a ? 1 : -1) * sep * 0.12));
        Vector3f lado = qq.transform(new Vector3f(0, 0, a ? 1 : -1));
        Vec3 cc = c.add(lado.x * sep, lado.y * sep - sep * 0.08, lado.z * sep);
        esfera(m, cc, r, TEX_RYUT, 0, a ? 0 : 32, a ? 32 : 64, qq, 1, 1, 1, 0.12f);
        // a face partida: o núcleo da Morte (escuro, com brilho roxo nas bordas)
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int n = 48;
        float pulso = 0.8f + 0.2f * Mth.sin(t * 0.3f);
        for (int i = 0; i < n; i++) {
            float a0 = (float) (Math.PI * 2 * i / n), a1 = (float) (Math.PI * 2 * (i + 1) / n);
            Vector3f p0 = qq.transform(new Vector3f(Mth.cos(a0) * r, Mth.sin(a0) * r, 0));
            Vector3f p1 = qq.transform(new Vector3f(Mth.cos(a1) * r, Mth.sin(a1) * r, 0));
            Vector3f p0m = qq.transform(new Vector3f(Mth.cos(a0) * r * 0.55f, Mth.sin(a0) * r * 0.55f, 0));
            Vector3f p1m = qq.transform(new Vector3f(Mth.cos(a1) * r * 0.55f, Mth.sin(a1) * r * 0.55f, 0));
            // anel de fora (crosta) e miolo
            tri(b, m, cc, p0, 0.16f, 0.15f, 0.17f, p1, 0.16f, 0.15f, 0.17f, p1m, 0.32f * pulso, 0.1f, 0.42f * pulso);
            tri(b, m, cc, p0, 0.16f, 0.15f, 0.17f, p1m, 0.32f * pulso, 0.1f, 0.42f * pulso, p0m, 0.32f * pulso, 0.1f, 0.42f * pulso);
            tri(b, m, cc, new Vector3f(), 0.05f, 0.0f, 0.08f, p0m, 0.32f * pulso, 0.1f, 0.42f * pulso, p1m, 0.32f * pulso, 0.1f, 0.42f * pulso);
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
    }

    private static void tri(BufferBuilder b, Matrix4f m, Vec3 c, Vector3f p, float r, float g, float bl, Vector3f p2, float r2, float g2, float b2,
                            Vector3f p3, float r3, float g3, float b3) {
        b.vertex(m, (float) c.x + p.x, (float) c.y + p.y, (float) c.z + p.z).color(r, g, bl, 1f).endVertex();
        b.vertex(m, (float) c.x + p2.x, (float) c.y + p2.y, (float) c.z + p2.z).color(r2, g2, b2, 1f).endVertex();
        b.vertex(m, (float) c.x + p3.x, (float) c.y + p3.y, (float) c.z + p3.z).color(r3, g3, b3, 1f).endVertex();
    }

    static void esfera(Matrix4f m, Vec3 c, float r, ResourceLocation tex, float giro, int j0, int j1, Quaternionf q,
                               float tr, float tg, float tb, float noite) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        int lat = 32, lon = 64;
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < lat; i++)
            for (int j = j0; j < j1; j++) {
                ponto(b, m, c, r, i, j, lat, lon, giro, q, tr, tg, tb, noite);
                ponto(b, m, c, r, i, j + 1, lat, lon, giro, q, tr, tg, tb, noite);
                ponto(b, m, c, r, i + 1, j + 1, lat, lon, giro, q, tr, tg, tb, noite);
                ponto(b, m, c, r, i + 1, j, lat, lon, giro, q, tr, tg, tb, noite);
            }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
    }

    private static void ponto(BufferBuilder b, Matrix4f m, Vec3 c, float r, int i, int j, int lat, int lon, float giro, Quaternionf q,
                              float tr, float tg, float tb, float noite) {
        float th = (float) Math.PI * i / lat, ph = (float) Math.PI * 2 * j / lon;
        Vector3f n = new Vector3f(Mth.sin(th) * Mth.cos(ph), Mth.cos(th), Mth.sin(th) * Mth.sin(ph));
        if (q != null) q.transform(n);
        float luz = Mth.clamp(noite + Math.max(0f, (float) (n.x * SOL.x + n.y * SOL.y + n.z * SOL.z)) * 0.95f, noite, 1f);
        b.vertex(m, (float) c.x + n.x * r, (float) c.y + n.y * r, (float) c.z + n.z * r)
                .uv((float) j / lon + giro, (float) i / lat).color(luz * tr, luz * tg, luz * tb, 1f).endVertex();
    }

    static void atmosfera(Matrix4f m, Vec3 camPos, Vec3 c, float r, int cor, float forca) {
        float cr = ((cor >> 16) & 255) / 255f, cg = ((cor >> 8) & 255) / 255f, cb = (cor & 255) / 255f;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        Vec3 paraCam = camPos.subtract(c).normalize();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int lat = 24, lon = 48;
        for (int i = 0; i < lat; i++)
            for (int j = 0; j < lon; j++) {
                int[][] vs = {{i, j}, {i, j + 1}, {i + 1, j + 1}, {i + 1, j}};
                for (int[] v : vs) {
                    float th = (float) Math.PI * v[0] / lat, ph = (float) Math.PI * 2 * v[1] / lon;
                    float nx = Mth.sin(th) * Mth.cos(ph), ny = Mth.cos(th), nz = Mth.sin(th) * Mth.sin(ph);
                    float f = (float) Math.abs(nx * paraCam.x + ny * paraCam.y + nz * paraCam.z);
                    float a = Mth.clamp((1 - f) * (1 - f) * forca, 0, 1);
                    b.vertex(m, (float) c.x + nx * r, (float) c.y + ny * r, (float) c.z + nz * r).color(cr, cg, cb, a).endVertex();
                }
            }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    // ===================== atores =====================

    /** Onde fica a mão do anel com o braço esticado (o jogador fica na origem olhando para +z). */
    private Vec3 mao() {
        Player p = Minecraft.getInstance().player;
        double lado = p != null && p.getMainArm() == HumanoidArm.LEFT ? 1 : -1;
        return new Vec3(0.33 * lado, 1.38, 0.66);
    }

    private void atores(PoseStack ps, MultiBufferSource.BufferSource buf, float t) {
        Minecraft mc = Minecraft.getInstance();
        if (t < B0) {
            // o Embrião
            float esc = 1 - faixa(t, 100, 215) * 0.88f;
            float vida = 1 - faixa(t, 100, 215);
            float bate = 1 + 0.05f * batida(t);
            ps.pushPose();
            ps.translate(0, 0.05 + 0.06 * Mth.sin(t * 0.05f), 0);
            ps.mulPose(Axis.YP.rotationDegrees(t * 0.35f + 200));
            ps.mulPose(Axis.ZP.rotationDegrees(8 * Mth.sin(t * 0.02f)));
            ps.scale(1.3f * bate, 1.3f * bate, 1.3f * bate);
            atores.embriao(ps, buf, t, esc * 0.86f, esc * 0.84f, Math.min(1, esc * 0.9f), vida);
            ps.popPose();
        } else if (t < B1 && mc.player != null) {
            jogador(ps, buf, t);
            aneis(ps, buf, t);
        } else if (t >= RACHA + 50 && t < D1) {
            // o Necron saindo da fenda, vindo para a câmera
            float k = faixa(t, RACHA + 50, D1 - 8);
            ps.pushPose();
            ps.translate(0, -1 + 1 * k, -60 + 112 * k);
            ps.mulPose(Axis.YP.rotationDegrees(180));
            ps.mulPose(Axis.XP.rotationDegrees(-25 * (1 - k)));
            ps.scale(1.5f, 1.5f, 1.5f);
            atores.necron(ps, buf, t, 1f - 0.7f * k, 1f);
            ps.popPose();
        } else if (t >= D1 && t < E1) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(180));
            ps.scale(1.25f, 1.25f, 1.25f);
            atores.necron(ps, buf, t, 0.15f, faixa(t, D1 + 30, D1 + 60));
            ps.popPose();
        } else if (t >= E1 && t < F1) {
            ps.pushPose();
            // deitado, de cabeça para a frente (-z), balançando de leve como quem rasga o vento
            ps.translate(0, 1.2 + 0.12 * Mth.sin(t * 0.08f), 0);
            ps.mulPose(Axis.ZP.rotationDegrees(5 * Mth.sin(t * 0.03f)));
            ps.mulPose(Axis.XP.rotationDegrees(-72 + 3 * Mth.sin(t * 0.05f)));
            ps.translate(0, -1.2, 0);
            ps.scale(1.25f, 1.25f, 1.25f);
            atores.necronVoando(ps, buf, t);
            ps.popPose();
        }
    }

    /** Batida do coração do Embrião (cada vez mais lenta quando escurece). */
    private static float batida(float t) {
        float per = 22 + 18 * faixa(t, 100, 220);
        float f = (t % per) / per;
        return f < 0.12f ? Mth.sin(f / 0.12f * Mth.PI) : f < 0.3f ? 0.5f * Mth.sin((f - 0.12f) / 0.18f * Mth.PI) : 0;
    }

    private void jogador(PoseStack ps, MultiBufferSource.BufferSource buf, float t) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        float yb = p.yBodyRot, ybo = p.yBodyRotO, yh = p.yHeadRot, yho = p.yHeadRotO, xr = p.getXRot(), xro = p.xRotO;
        p.yBodyRot = p.yBodyRotO = 0;
        p.yHeadRot = p.yHeadRotO = 0;
        p.setXRot(t > ESTENDE && t < VOLTA + 30 ? 8 : -5);
        p.xRotO = p.getXRot();
        var disp = mc.getEntityRenderDispatcher();
        disp.setRenderShadow(false);
        ps.pushPose();
        ps.translate(0, 0.05 * Mth.sin(t * 0.06f), 0);
        disp.render(p, 0, 0, 0, 0, pt, ps, buf, LightTexture.FULL_BRIGHT);
        ps.popPose();
        disp.setRenderShadow(true);
        p.yBodyRot = yb; p.yBodyRotO = ybo; p.yHeadRot = yh; p.yHeadRotO = yho; p.setXRot(xr); p.xRotO = xro;
    }

    /** Onde está cada um dos sete anéis. */
    private Vec3 posAnel(int i, float t) {
        Vec3 mao = mao();
        if (t < PUM) {
            float k = faixa(t, DESFAZ, DESFAZ + 40);
            float vel = 0.06f + 0.14f * faixa(t, DESFAZ, PUM);
            double ang = Math.PI * 2 * i / 7 + (t - DESFAZ) * vel * (1 + 0.5 * faixa(t, PUM - 40, PUM));
            double r = 1.55 * k - 0.35 * faixa(t, PUM - 25, PUM);
            double y = 1.1 + 0.55 * Math.sin(ang * 2 + t * 0.05) * k;
            Vec3 roda = new Vec3(Math.cos(ang) * r, y, Math.sin(ang) * r);
            return lerp(k, mao, roda);
        }
        double ang = Math.PI * 2 * i / 7 + (PUM - DESFAZ) * 0.2 * 1.5;
        Vec3 saida = new Vec3(Math.cos(ang) * 1.2, 1.1, Math.sin(ang) * 1.2);
        float s = t - PUM;
        Vec3 longe = saida.add(rumos[i].scale(0.35 * s * s + 2 * s));
        if (i != indiceTropa || t < VOLTA0) return longe;
        // o anel da Tropa volta
        float k = faixa(t, VOLTA0, VOLTA);
        float s0 = VOLTA0 - PUM;
        Vec3 de = saida.add(rumos[i].scale(0.35 * s0 * s0 + 2 * s0));
        float kk = k * k;
        return de.add(mao.subtract(de).scale(kk));
    }

    private void aneis(PoseStack ps, MultiBufferSource.BufferSource buf, float t) {
        if (t < DESFAZ || t > VOLTA + 4) return;
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < 7; i++) {
            if (t > VOLTA0 + 30 && i != indiceTropa) continue;
            Vec3 p = posAnel(i, t);
            if (p.length() > 300) continue;
            ps.pushPose();
            ps.translate(p.x, p.y, p.z);
            ps.mulPose(Axis.YP.rotationDegrees(t * 9 + i * 50));
            ps.mulPose(Axis.XP.rotationDegrees(25 * Mth.sin(t * 0.1f + i)));
            float esc = 0.55f * faixa(t, DESFAZ, DESFAZ + 18);
            if (i == indiceTropa && t > VOLTA0) esc = Mth.lerp(faixa(t, VOLTA - 12, VOLTA), 0.55f, 0.2f);
            ps.scale(esc, esc, esc);
            mc.getItemRenderer().renderStatic(aneis[i], ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, i);
            ps.popPose();
        }
    }

    // ===================== efeitos (brilhos grandes) =====================

    private void efeitos(Matrix4f m, Camera cam, Vector3f dir, Vector3f cima, float t) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, BRILHO);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        // primeiro as sombras (mistura normal), depois as luzes (aditivas)
        RenderSystem.defaultBlendFunc();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int nSombras = 0;
        if (t < B0) {
            // o halo do Embrião vira uma névoa escura
            float esc = faixa(t, 120, 230);
            sprite(b, m, dir, cima, 0, 0.75, 0, 2.6f, 0.05f, 0f, 0.08f, 0.85f * esc); nSombras++;
        }
        if (t >= F1 && t < G1) {
            Vec3 alvo = impacto();
            float k = faixa(t, IMPACTO, IMPACTO + 50);
            if (t > IMPACTO) { sprite(b, m, dir, cima, alvo.x, alvo.y, alvo.z, 30 + 110 * k, 0.03f, 0, 0.05f, 0.85f); nSombras++; }
            if (t < IMPACTO) {
                Vec3 c = cometa(t);
                sprite(b, m, dir, cima, c.x, c.y, c.z, 5.5f, 0.02f, 0, 0.03f, 1f); nSombras++;
            }
        }
        if (nSombras > 0) BufferUploader.drawWithShader(b.end()); else b.end();

        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        if (t < B0) {
            float vida = 1 - faixa(t, 100, 215);
            float bate = batida(t);
            sprite(b, m, dir, cima, 0, 0.75, 0, 2.2f + 0.3f * bate, 1, 1, 1, 0.55f * vida + 0.1f);
            sprite(b, m, dir, cima, 0, 0.75, 0, 5.5f + 0.6f * bate, 0.85f, 0.9f, 1, 0.3f * vida);
            sprite(b, m, dir, cima, 0, 0.75, 0, 1.4f, 0.5f + 0.5f * vida, 0.3f + 0.7f * vida, 0.6f + 0.4f * vida, 0.3f + 0.3f * bate * vida);
        } else if (t < B1) {
            Vec3 mao = mao();
            if (t > ESTENDE && t < DESFAZ + 6) {
                float k = faixa(t, ESTENDE + 20, DESFAZ);
                float trem = t > DESFAZ - 30 ? 0.3f * Mth.sin(t * 2.5f) : 0;
                sprite(b, m, dir, cima, mao.x, mao.y, mao.z, 0.2f + 0.55f * k + trem * 0.15f, 1, 1, 1, 0.4f + 0.6f * k);
            }
            if (t > DESFAZ - 2 && t < DESFAZ + 20) {
                float k = (t - DESFAZ + 2) / 22f;
                sprite(b, m, dir, cima, mao.x, mao.y, mao.z, 0.5f + 6 * k, 1, 1, 1, 1 - k);
            }
            for (int i = 0; i < 7 && t > DESFAZ && t < VOLTA + 4; i++) {
                if (t > VOLTA0 + 30 && i != indiceTropa) continue;
                Vec3 p = posAnel(i, t);
                int c = Emocoes.cor(Emocoes.ASPECTOS[i]);
                sprite(b, m, dir, cima, p.x, p.y, p.z, 0.45f, r(c), g(c), bl(c), 0.8f);
            }
            if (t > PUM - 2 && t < PUM + 16) {
                float k = (t - PUM + 2) / 18f;
                sprite(b, m, dir, cima, 0, 1.1, 0, 1 + 8 * k, 1, 1, 1, 0.9f * (1 - k));
            }
            if (t > VOLTA - 2 && t < VOLTA + 40) {
                float k = (t - VOLTA + 2) / 42f;
                sprite(b, m, dir, cima, mao.x, mao.y, mao.z, 0.4f + 5 * k, r(corTropa), g(corTropa), bl(corTropa), 1 - k);
                sprite(b, m, dir, cima, 0, 1, 0, 2.5f, r(corTropa), g(corTropa), bl(corTropa), 0.35f * (1 - k));
            }
            if (t > VOLTA) {
                // a aura da Tropa em volta do jogador
                sprite(b, m, dir, cima, 0, 1, 0, 1.6f, r(corTropa), g(corTropa), bl(corTropa), 0.18f + 0.05f * Mth.sin(t * 0.2f));
            }
        } else if (t >= D0 && t < D1) {
            // luz roxa saindo das rachaduras e da fenda
            float k = faixa(t, RACHA - 80, RACHA);
            float pisca = 0.7f + 0.3f * Mth.sin(t * 0.9f) * Mth.sin(t * 0.37f);
            if (t < RACHA + 50) sprite(b, m, dir, cima, 0, 0, 0, 175 + 30 * k, 0.45f, 0.1f, 0.7f, 0.5f * k * pisca);
            if (t >= RACHA && t < RACHA + 30) {
                float f = (t - RACHA) / 30f;
                sprite(b, m, dir, cima, 0, 0, 0, 120 + 500 * f, 0.85f, 0.7f, 1, 1 - f);
            }
            if (t >= RACHA + 50) {
                // dentro da fenda: a luz do núcleo atrás do Necron
                sprite(b, m, dir, cima, 0, 0, -60, 60, 0.4f, 0.1f, 0.6f, 0.7f * pisca);
            }
        } else if (t >= D1 && t < E1) {
            // uma luz roxa fraca atrás dele
            sprite(b, m, dir, cima, 0, 2.6, -1.5, 2.4f, 0.35f, 0.1f, 0.55f, 0.35f * faixa(t, D1 + 30, D1 + 70));
        } else if (t >= E1 && t < F1) {
            sprite(b, m, dir, cima, 0, 1.2, 0, 2.8f, 0.3f, 0.08f, 0.45f, 0.35f);
        } else if (t >= F1 && t < G1) {
            Vec3 alvo = impacto();
            if (t < IMPACTO) {
                Vec3 c = cometa(t);
                float ar = faixa(t, IMPACTO - 50, IMPACTO - 10);
                sprite(b, m, dir, cima, c.x, c.y, c.z, 14 + 12 * ar, 0.5f + 0.5f * ar, 0.2f + 0.3f * ar, 0.7f - 0.5f * ar, 0.8f);
            } else {
                float k = faixa(t, IMPACTO, IMPACTO + 35);
                sprite(b, m, dir, cima, alvo.x, alvo.y, alvo.z, 40 + 320 * k, 0.8f, 0.6f, 1, 1 - k);
                sprite(b, m, dir, cima, alvo.x, alvo.y, alvo.z, 60, 0.5f, 0.15f, 0.8f, 0.6f * (1 - faixa(t, IMPACTO + 20, G1)));
                // a onda de choque correndo pela superfície
                Vec3 n = alvo.normalize();
                Vec3 u = n.cross(new Vec3(0, 1, 0)).normalize(), v = n.cross(u);
                float raio = 20 + 160 * faixa(t, IMPACTO, IMPACTO + 55);
                for (int i = 0; i < 64; i++) {
                    double a = Math.PI * 2 * i / 64;
                    Vec3 q = alvo.add(u.scale(Math.cos(a) * raio)).add(v.scale(Math.sin(a) * raio));
                    q = q.normalize().scale(602);
                    sprite(b, m, dir, cima, q.x, q.y, q.z, 9, 0.6f, 0.3f, 0.9f, 0.7f * (1 - faixa(t, IMPACTO + 25, IMPACTO + 58)));
                }
            }
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.defaultBlendFunc();
    }

    private static float r(int c) { return ((c >> 16) & 255) / 255f; }
    private static float g(int c) { return ((c >> 8) & 255) / 255f; }
    private static float bl(int c) { return (c & 255) / 255f; }

    /** Onde o Necron cai na Terra (lado virado para a câmera). */
    private static Vec3 impacto() { return new Vec3(-0.12, 0.32, 0.94).normalize().scale(601); }

    private static Vec3 cometa(float t) {
        Vec3 alvo = impacto();
        Vec3 de = alvo.add(300, 380, 250);
        float k = faixa(t, F1 + 10, IMPACTO);
        k = k * k;
        return de.add(alvo.subtract(de).scale(k));
    }

    // ===================== partículas =====================

    private static final class Parte {
        double x, y, z, vx, vy, vz;
        float r, g, b, a, tam, atrito = 0.96f;
        int idade, vida;
        boolean escura;
    }

    private Parte parte(Vec3 p, Vec3 v, int cor, float tam, int vida) {
        Parte q = new Parte();
        q.x = p.x; q.y = p.y; q.z = p.z; q.vx = v.x; q.vy = v.y; q.vz = v.z;
        q.r = r(cor); q.g = g(cor); q.b = bl(cor); q.a = 1; q.tam = tam; q.vida = vida;
        partes.add(q);
        return q;
    }

    private Vec3 aleatorio(double s) {
        double a = rnd.nextDouble() * Math.PI * 2, b = Math.acos(rnd.nextDouble() * 2 - 1);
        return new Vec3(Math.sin(b) * Math.cos(a) * s, Math.cos(b) * s, Math.sin(b) * Math.sin(a) * s);
    }

    /** Solta as partículas de cada momento (a cada tick). */
    private void emitir(int t) {
        if (t < B0) {
            // poeira de luz em volta do Embrião; depois, fios escuros sendo puxados para dentro
            if (t < 160 && t % 2 == 0) {
                Vec3 p = aleatorio(1.4 + rnd.nextDouble()).add(0, 0.75, 0);
                parte(p, aleatorio(0.004), 0xFFFFFF, 0.05f + rnd.nextFloat() * 0.05f, 60).atrito = 1f;
            }
            if (t > 110 && t < 225) {
                for (int i = 0; i < 2; i++) {
                    Vec3 de = aleatorio(2.2 + rnd.nextDouble() * 0.8).add(0, 0.75, 0);
                    Vec3 v = new Vec3(0, 0.75, 0).subtract(de).scale(0.035);
                    Parte q = parte(de, v, 0x1A0828, 0.12f, 30);
                    q.escura = true;
                    q.atrito = 1.02f;
                }
            }
        } else if (t < B1) {
            Vec3 mao = mao();
            if (t > DESFAZ - 34 && t < DESFAZ) {
                // o anel trinca: faíscas brancas
                for (int i = 0; i < 3; i++) parte(mao, aleatorio(0.03 + rnd.nextDouble() * 0.03), 0xFFFFFF, 0.04f, 12);
            }
            if (t == DESFAZ) {
                for (int i = 0; i < 120; i++) {
                    int c = Emocoes.cor(Emocoes.ASPECTOS[i % 7]);
                    parte(mao, aleatorio(0.06 + rnd.nextDouble() * 0.1), i % 3 == 0 ? 0xFFFFFF : c, 0.07f, 30 + rnd.nextInt(20));
                }
            }
            if (t > DESFAZ && t < VOLTA + 2) {
                for (int i = 0; i < 7; i++) {
                    if (t > VOLTA0 && i != indiceTropa) continue;
                    Vec3 p = posAnel(i, t);
                    if (p.length() > 260) continue;
                    int c = Emocoes.cor(Emocoes.ASPECTOS[i]);
                    float tam = t > PUM ? 0.18f + Math.min(1.2f, (t - PUM) * 0.02f) : 0.07f;
                    parte(p, aleatorio(0.003), c, tam, t > PUM ? 26 : 16).atrito = 0.9f;
                }
            }
            if (t == PUM) {
                for (int i = 0; i < 160; i++) {
                    int c = Emocoes.cor(Emocoes.ASPECTOS[i % 7]);
                    parte(new Vec3(0, 1.1, 0), aleatorio(0.15 + rnd.nextDouble() * 0.2), c, 0.09f, 40);
                }
            }
            if (t == VOLTA) {
                for (int i = 0; i < 90; i++) parte(mao, aleatorio(0.05 + rnd.nextDouble() * 0.12), i % 4 == 0 ? 0xFFFFFF : corTropa, 0.07f, 35);
            }
            if (t > VOLTA && t < B1 && t % 2 == 0) {
                double a = t * 0.4;
                parte(new Vec3(Math.cos(a) * 0.7, 0.1 + (t - VOLTA) % 40 / 20.0, Math.sin(a) * 0.7), new Vec3(0, 0.01, 0), corTropa, 0.06f, 30);
            }
        } else if (t >= D0 && t < D1) {
            if (t >= RACHA && t < RACHA + 45) {
                // pedaços de Ryut voando
                for (int i = 0; i < 12; i++) {
                    Vec3 p = aleatorio(80 + rnd.nextDouble() * 70);
                    Parte q = parte(new Vec3(p.x * 0.15, p.y, p.z), new Vec3(p.x * 0.03, p.y * 0.02, p.z * 0.02).add(aleatorio(1.5)), 0x6A6470, 2.5f + rnd.nextFloat() * 4, 120);
                    q.escura = true; q.atrito = 0.995f;
                }
                for (int i = 0; i < 6; i++) parte(aleatorio(30), aleatorio(1.5 + rnd.nextDouble() * 1.5), 0x8A40C0, 5f, 40);
            }
            if (t >= RACHA + 50) {
                // névoa roxa escorrendo pela fenda, perto da câmera
                for (int i = 0; i < 3; i++) {
                    Vec3 p = new Vec3((rnd.nextDouble() - 0.5) * 30, (rnd.nextDouble() - 0.5) * 30, -80 + rnd.nextDouble() * 60);
                    Parte q = parte(p, new Vec3(0, 0, 0.6), 0x5A1E80, 0.8f + rnd.nextFloat() * 1.2f, 60);
                    q.a = 0.25f; q.atrito = 1f;
                }
            }
        } else if (t >= D1 && t < E1) {
            if (t % 3 == 0) {
                // fumaça escura saindo da túnica
                Vec3 p = new Vec3((rnd.nextDouble() - 0.5) * 1.2, rnd.nextDouble() * 1.5, (rnd.nextDouble() - 0.5) * 1.2);
                Parte q = parte(p, new Vec3(0, 0.01, 0), 0x140A1C, 0.35f, 40);
                q.escura = true; q.a = 0.6f;
            }
        } else if (t >= E1 && t < F1) {
            // estrelas passando (riscos) e o rastro negro atrás dele
            for (int i = 0; i < 6; i++) {
                Vec3 p = new Vec3((rnd.nextDouble() - 0.5) * 60, (rnd.nextDouble() - 0.5) * 40, -60 - rnd.nextDouble() * 20);
                Parte q = parte(p, new Vec3(0, 0, 3.2), 0xC8D2FF, 0.08f, 40);
                q.atrito = 1f;
            }
            for (int i = 0; i < 3; i++) {
                Vec3 p = new Vec3((rnd.nextDouble() - 0.5) * 0.6, 0.6 + rnd.nextDouble() * 1.2, 0.8);
                Parte q = parte(p, new Vec3((rnd.nextDouble() - 0.5) * 0.02, 0, 0.25), i == 0 ? 0x6A2AA0 : 0x120818, 0.4f + rnd.nextFloat() * 0.3f, 30);
                q.escura = i != 0; q.atrito = 1f;
            }
        } else if (t >= F1 && t < G1) {
            if (t < IMPACTO) {
                Vec3 c = cometa(t);
                float ar = faixa(t, IMPACTO - 50, IMPACTO - 10);
                for (int i = 0; i < 4; i++) {
                    Parte q = parte(c.add(aleatorio(3)), aleatorio(0.4), 0x100616, 6 + rnd.nextFloat() * 5, 50);
                    q.escura = true; q.atrito = 0.97f;
                }
                if (ar > 0) for (int i = 0; i < 3; i++) parte(c.add(aleatorio(2)), aleatorio(0.5), i == 0 ? 0xFF9A40 : 0xB050FF, 5 + 4 * ar, 18);
            }
            if (t == IMPACTO) {
                Vec3 a = impacto();
                for (int i = 0; i < 120; i++) {
                    Parte q = parte(a, aleatorio(2 + rnd.nextDouble() * 4).add(a.normalize().scale(2)), i % 2 == 0 ? 0x1A0A24 : 0x8040C0, 8 + rnd.nextFloat() * 8, 70);
                    q.escura = i % 2 == 0; q.atrito = 0.95f;
                }
            }
        }
    }

    private void particulas(Matrix4f m, Vector3f dir, Vector3f cima) {
        if (partes.isEmpty()) return;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, BRILHO);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        for (int passo = 0; passo < 2; passo++) {
            boolean escuras = passo == 0;
            if (escuras) RenderSystem.defaultBlendFunc();
            else RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            int n = 0;
            for (Parte p : partes) {
                if (p.escura != escuras) continue;
                float vida = (p.idade + pt) / p.vida;
                float a = p.a * (vida < 0.15f ? vida / 0.15f : 1 - (vida - 0.15f) / 0.85f);
                double x = p.x + p.vx * pt, y = p.y + p.vy * pt, z = p.z + p.vz * pt;
                sprite(b, m, dir, cima, x, y, z, p.tam, p.r, p.g, p.b, Mth.clamp(a, 0, 1));
                n++;
            }
            if (n > 0) BufferUploader.drawWithShader(b.end()); else b.end();
        }
        RenderSystem.defaultBlendFunc();
    }

    // ===================== textos, legendas e escurecimento =====================

    private record Legenda(int de, int ate, String chave) {}

    private static final Legenda[] LEGENDAS = {
            new Legenda(30, 112, "necron.lanterna.leg.vida"),
            new Legenda(122, 222, "necron.lanterna.leg.escurece"),
            new Legenda(286, 350, "necron.lanterna.leg.anel"),
            new Legenda(372, 470, "necron.lanterna.leg.digno"),
            new Legenda(500, 566, "necron.lanterna.leg.partem"),
            new Legenda(580, 670, "necron.lanterna.leg.fica"),
            new Legenda(690, 760, "necron.lanterna.leg.nulo"),
            new Legenda(790, 872, "necron.lanterna.leg.ryut"),
            new Legenda(1090, 1180, "necron.lanterna.leg.voa"),
            new Legenda(1190, 1262, "necron.lanterna.leg.vida_terra"),
            new Legenda(1290, 1380, "necron.lanterna.leg.terra"),
    };

    private float preto(float t) {
        // escurecimentos entre os planos
        float a = 1 - faixa(t, 0, 40);
        a = Math.max(a, faixa(t, B0 - 22, B0) * (1 - faixa(t, B0, B0 + 26)) * (t < B0 + 26 ? 1 : 0));
        if (t >= B1 - 24 && t < D0 + 30) a = Math.max(a, t < D0 ? faixa(t, B1 - 24, B1) : 1 - faixa(t, D0, D0 + 30));
        if (t >= D1 - 6 && t < D1 + 8) a = Math.max(a, 1 - Math.abs(t - D1) / 8f);
        if (t >= E1 - 14 && t < E1 + 14) a = Math.max(a, 1 - Math.abs(t - E1) / 14f);
        if (t >= F1 - 14 && t < F1 + 14) a = Math.max(a, 1 - Math.abs(t - F1) / 14f);
        if (t >= G1 - 30) a = Math.max(a, faixa(t, G1 - 30, G1));
        return Mth.clamp(a, 0, 1);
    }

    private void textos(GuiGraphics g, float t) {
        // escurecimento
        int pa = (int) (preto(t) * 255);
        if (pa > 0) g.fill(0, 0, width, height, pa << 24);
        // clarão branco do impacto e da partida de Ryut
        float clarao = 0;
        if (t >= RACHA && t < RACHA + 12) clarao = 1 - (t - RACHA) / 12f;
        if (t >= IMPACTO && t < IMPACTO + 14) clarao = 1 - (t - IMPACTO) / 14f;
        if (clarao > 0) g.fill(0, 0, width, height, ((int) (clarao * 200) << 24) | 0xE8DCFF);
        // faixas de cinema
        int faixaAlt = height / 9;
        g.fill(0, 0, width, faixaAlt, 0xFF000000);
        g.fill(0, height - faixaAlt, width, height, 0xFF000000);

        // legendas
        for (Legenda l : LEGENDAS) {
            if (t < l.de || t > l.ate) continue;
            float a = Math.min(faixa(t, l.de, l.de + 10), 1 - faixa(t, l.ate - 10, l.ate));
            int alfa = Math.max(4, (int) (a * 255));
            Component c = Component.translatable(l.chave);
            int y = height - faixaAlt / 2 - font.lineHeight / 2;
            g.drawCenteredString(font, c, width / 2, y, (alfa << 24) | 0xE6E6EE);
        }
        // título do Necron
        if (t > D1 + 50 && t < E1 - 6) {
            float a = Math.min(faixa(t, D1 + 50, D1 + 70), 1 - faixa(t, E1 - 22, E1 - 6));
            int alfa = Math.max(4, (int) (a * 255));
            g.pose().pushPose();
            g.pose().translate(width / 2f, height * 0.7f, 0);
            g.pose().scale(4f, 4f, 1f);
            g.drawCenteredString(font, Component.translatable("necron.lanterna.nome"), 0, 0, (alfa << 24) | 0xD9C8FF);
            g.pose().popPose();
            g.drawCenteredString(font, Component.translatable("necron.lanterna.nome_sub"), width / 2, (int) (height * 0.7f) + 40, (alfa << 24) | 0x9A8AAA);
        }
        // fim
        if (t > G1 - 10) {
            float a = Math.min(faixa(t, G1 - 10, G1 + 10), 1 - faixa(t, DURACAO - 12, DURACAO));
            int alfa = Math.max(4, (int) (a * 255));
            g.pose().pushPose();
            g.pose().translate(width / 2f, height * 0.45f, 0);
            g.pose().scale(2f, 2f, 1f);
            g.drawCenteredString(font, Component.translatable("necron.lanterna.chegou"), 0, 0, (alfa << 24) | 0xE0D6F0);
            g.pose().popPose();
            g.drawCenteredString(font, Component.translatable("necron.lanterna.continua"), width / 2, (int) (height * 0.45f) + 26, (alfa << 24) | 0x8A7A9A);
        }
        if (revendo) g.drawString(font, Component.translatable("necron.lanterna.esc"), 6, 6, 0x60FFFFFF);
    }

    // ===================== sons =====================

    private static void som(SoundEvent s, float vol, float tom) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, tom, vol));
    }

    private void sons(int t) {
        if (t == 2) som(SoundEvents.BEACON_AMBIENT, 1f, 1.6f);
        if (t < 225 && t > 20) {
            float per = 22 + 18 * faixa(t, 100, 220);
            if ((int) (t % per) == 0) som(SoundEvents.WARDEN_HEARTBEAT, 0.9f - 0.4f * faixa(t, 100, 220), 1.4f - 0.5f * faixa(t, 100, 220));
        }
        if (t == 30) som(SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.5f);
        if (t == 120) som(SoundEvents.SCULK_CATALYST_BLOOM, 1f, 0.6f);
        if (t == 170) som(SoundEvents.SOUL_ESCAPE, 1f, 0.5f);
        if (t == 210) som(SoundEvents.BEACON_DEACTIVATE, 1f, 0.5f);
        if (t == ESTENDE) som(SoundEvents.BEACON_POWER_SELECT, 0.8f, 0.6f);
        if (t > DESFAZ - 34 && t < DESFAZ && t % 6 == 0) som(SoundEvents.AMETHYST_BLOCK_HIT, 0.9f, 0.6f + (t - DESFAZ + 34) * 0.03f);
        if (t == DESFAZ) { som(SoundEvents.GLASS_BREAK, 1f, 0.8f); som(SoundEvents.AMETHYST_BLOCK_BREAK, 1f, 0.6f); som(SoundEvents.TOTEM_USE, 0.5f, 1.6f); }
        if (t > DESFAZ && t < PUM && t % 16 == 0) som(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7f, 0.6f + 0.6f * (t - DESFAZ) / (PUM - DESFAZ));
        if (t == PUM) { som(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1f, 0.7f); som(SoundEvents.FIREWORK_ROCKET_LAUNCH, 1f, 0.8f); som(SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 1.8f); }
        if (t == VOLTA0 + 20) som(SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, 1f, 0.9f);
        if (t == VOLTA) { som(SoundEvents.ARMOR_EQUIP_NETHERITE, 1f, 1f); som(SoundEvents.BEACON_ACTIVATE, 0.9f, 1.3f); }
        if (t == B1 + 10) som(SoundEvents.AMBIENT_CAVE.value(), 1f, 0.6f);
        if (t == D0 + 10) som(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), 1f, 0.7f);
        if (t > RACHA - 80 && t < RACHA && t % 18 == 0) som(SoundEvents.WARDEN_DIG, 1f, 0.5f);
        if (t == RACHA - 30) som(SoundEvents.WARDEN_EMERGE, 1f, 0.6f);
        if (t == RACHA) { som(SoundEvents.GENERIC_EXPLODE, 1f, 0.4f); som(SoundEvents.WITHER_BREAK_BLOCK, 1f, 0.5f); som(SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, 0.5f); }
        if (t == RACHA + 40) som(SoundEvents.SOUL_ESCAPE, 1f, 0.4f);
        if (t == D1 + 50) { som(SoundEvents.WITHER_SPAWN, 0.7f, 0.6f); }
        if (t == D1 + 90) som(SoundEvents.WITHER_AMBIENT, 0.8f, 0.4f);
        if (t > E1 && t < F1 && t % 40 == 0) som(SoundEvents.ELYTRA_FLYING, 0.5f, 0.5f);
        if (t == F1 + 20) som(SoundEvents.PHANTOM_SWOOP, 1f, 0.4f);
        if (t == IMPACTO - 40) som(SoundEvents.BLAZE_SHOOT, 1f, 0.4f);
        if (t == IMPACTO) { som(SoundEvents.GENERIC_EXPLODE, 1f, 0.5f); som(SoundEvents.DRAGON_FIREBALL_EXPLODE, 1f, 0.5f); som(SoundEvents.WITHER_DEATH, 0.5f, 0.6f); }
        if (t == G1) som(SoundEvents.BELL_RESONATE, 1f, 0.5f);
    }

    // ===================== braço estendido (via HumanoidModelMixin) =====================

    /** Na cena, o jogador estende a mão do anel e deixa as pernas soltas, flutuando. */
    public static void bracos(HumanoidModel<?> model, LivingEntity e) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof CinemaNecron c) || e != mc.player) return;
        float t = c.t();
        if (t < B0 || t > B1) return;
        float k = faixa(t, ESTENDE, ESTENDE + 30) * (1 - faixa(t, VOLTA + 20, VOLTA + 50) * 0.6f);
        boolean dir = mc.player.getMainArm() != HumanoidArm.LEFT;
        var braco = dir ? model.rightArm : model.leftArm;
        braco.xRot = Mth.lerp(k, braco.xRot, -1.45f + 0.03f * Mth.sin(t * 0.3f));
        braco.yRot = Mth.lerp(k, braco.yRot, dir ? -0.08f : 0.08f);
        braco.zRot = Mth.lerp(k, braco.zRot, 0);
        var outro = dir ? model.leftArm : model.rightArm;
        outro.xRot = 0.1f;
        outro.zRot = (dir ? -1 : 1) * 0.18f;
        model.rightLeg.xRot = 0.12f + 0.04f * Mth.sin(t * 0.05f);
        model.leftLeg.xRot = -0.05f + 0.04f * Mth.sin(t * 0.05f + 1);
        model.rightLeg.yRot = model.leftLeg.yRot = 0;
    }

    /** O anel some do dedo entre o Anel da Vida se desfazer e o anel da Tropa voltar. */
    public static boolean escondeAnel(Player p) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof CinemaNecron c) || p != mc.player) return false;
        float t = c.t();
        return t >= DESFAZ && t < VOLTA;
    }

    // ===================== textura do brilho =====================

    static void criarBrilho() {
        texturaPronta = true;
        NativeImage img = new NativeImage(128, 128, false);
        for (int y = 0; y < 128; y++)
            for (int x = 0; x < 128; x++) {
                double d = Math.hypot(x - 63.5, y - 63.5) / 64.0;
                double a = d >= 1 ? 0 : Math.exp(-d * d * 4.5) * (1 - d * d);
                int al = (int) (Mth.clamp(a, 0, 1) * 255);
                img.setPixelRGBA(x, y, (al << 24) | 0xFFFFFF);
            }
        DynamicTexture tex = new DynamicTexture(img);
        Minecraft.getInstance().getTextureManager().register(BRILHO, tex);
        tex.bind();
        tex.setFilter(true, false);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().getSoundManager().stop();
        super.onClose();
    }
}
