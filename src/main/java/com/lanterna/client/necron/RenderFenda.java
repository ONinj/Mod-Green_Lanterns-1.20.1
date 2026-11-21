package com.lanterna.client.necron;

import com.lanterna.LanternaMod;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * 0.55.2: a fenda da Morte desenhada como um portal de verdade (não partículas): um disco em espiral girando (duas
 * camadas em sentidos opostos), o olho escuro no meio, a borda acesa e faixas de luz roxa caindo da borda e
 * balançando. Também a cúpula de luz do escudo na cena do resgate.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RenderFenda {
    private static final ResourceLocation ESPIRAL = LanternaMod.id("cinema/fenda_espiral"), FAIXA = LanternaMod.id("cinema/fenda_faixa");
    private static boolean pronto;
    /** Raio do portal (blocos). */
    private static final float R = 13;

    private static void texturas() {
        pronto = true;
        // espiral: braços claros e escuros girando para o centro, transparente na borda
        NativeImage e = new NativeImage(256, 256, false);
        for (int y = 0; y < 256; y++)
            for (int x = 0; x < 256; x++) {
                double dx = (x - 127.5) / 128.0, dy = (y - 127.5) / 128.0, r = Math.sqrt(dx * dx + dy * dy);
                if (r >= 1) { e.setPixelRGBA(x, y, 0); continue; }
                double th = Math.atan2(dy, dx);
                double braco = 0.5 + 0.5 * Math.sin(th * 4 + Math.log(r + 0.02) * 9);
                double fino = 0.5 + 0.5 * Math.sin(th * 9 - r * 30);
                double v = Math.pow(braco, 3) * 0.85 + Math.pow(fino, 6) * 0.35;
                double borda = Math.min(1, (1 - r) * 4);
                double a = v * borda * Math.min(1, r * 3.5);
                int cr = (int) Mth.clamp(110 + 145 * v * r, 0, 255), cg = (int) Mth.clamp(30 + 120 * Math.pow(v, 2) * r, 0, 255), cb = (int) Mth.clamp(170 + 85 * v, 0, 255);
                int al = (int) Mth.clamp(a * 255, 0, 255);
                e.setPixelRGBA(x, y, (al << 24) | (cb << 16) | (cg << 8) | cr);
            }
        DynamicTexture te = new DynamicTexture(e);
        Minecraft.getInstance().getTextureManager().register(ESPIRAL, te);
        te.bind();
        te.setFilter(true, false);
        // faixa: uma tira de luz (clara no meio, some nas bordas e na ponta de baixo)
        NativeImage f = new NativeImage(32, 128, false);
        for (int y = 0; y < 128; y++)
            for (int x = 0; x < 32; x++) {
                double u = (x - 15.5) / 16.0, v = y / 127.0;
                double a = Math.exp(-u * u * 9) * Math.pow(1 - v, 1.4) * (0.75 + 0.25 * Math.sin(v * 40));
                int al = (int) Mth.clamp(a * 255, 0, 255);
                int c = (int) (200 + 55 * Math.exp(-u * u * 40));
                f.setPixelRGBA(x, y, (al << 24) | (255 << 16) | ((int) (c * 0.55) << 8) | (int) (c * 0.8));
            }
        DynamicTexture tf = new DynamicTexture(f);
        Minecraft.getInstance().getTextureManager().register(FAIXA, tf);
        tf.bind();
        tf.setFilter(true, false);
    }

    @SubscribeEvent
    public static void desenhar(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var planeta = com.lanterna.lore.PlanetLore.byDimension(mc.level.dimension());
        boolean terra = mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
        java.util.List<BlockPos> fs = new java.util.ArrayList<>();
        BlockPos embriao = null, voo = null;
        long tDesp = -1, tFim = -1;
        for (var en : ClientSaga.fendas.entrySet()) {
            String k = en.getKey();
            if (planeta != null ? k.equals(planeta.planet) : terra && k.startsWith("earth")) fs.add(en.getValue());
            if (terra && k.equals("embriao")) embriao = en.getValue();
            if (k.equals("embriaovoo")) voo = en.getValue();
            if (k.equals("embriaot")) tDesp = en.getValue().getX();
            if (k.equals("embriaofim")) tFim = en.getValue().getX();
        }
        boolean preso = ClientSaga.fendas.containsKey("embriaopreso");
        BlockPos noite = ClientSaga.fendas.get("noite");
        Vec3 cam0 = e.getCamera().getPosition();
        float agora = mc.level.getGameTime() + e.getPartialTick();
        float onda = -1;
        Vec3 ondaC = null;
        if (terra && voo != null && tDesp >= 0 && ClientSaga.cratera != null) {
            onda = embriaoVoando(e, voo, ClientSaga.cratera, cam0, agora, tDesp, tFim);
            ondaC = Vec3.atBottomCenterOf(ClientSaga.cratera).add(0, 3, 0);
        } else if (embriao != null && embriao.distToCenterSqr(cam0) < 96 * 96) desenharEmbriao(e, embriao, cam0, agora, preso);
        // 0.57.0: a Noite Eterna (linha corrompida): uma onda escura saindo da cratera
        if (terra && noite != null && ClientSaga.cratera != null) {
            float u = agora - noite.getX();
            if (u >= 0 && u < 260) {
                if (!CinemaNecron.texturaPronta) CinemaNecron.criarBrilho();
                Matrix4f m0 = e.getPoseStack().last().pose();
                RenderSystem.enableBlend();
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.disableCull();
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                RenderSystem.setShaderTexture(0, CinemaNecron.BRILHO);
                RenderSystem.defaultBlendFunc();
                BufferBuilder bb = Tesselator.getInstance().getBuilder();
                bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                Vec3 c = Vec3.atBottomCenterOf(ClientSaga.cratera).add(0, 3, 0).subtract(cam0);
                float raio = 8 + u * 2.6f, alfa = 0.9f * (1 - u / 260f);
                disco(bb, m0, c, raio, 0, 0.12f, 0.02f, 0.2f, alfa, 0);
                disco(bb, m0, c.add(0, 8, 0), raio * 0.85f, 0, 0.2f, 0.05f, 0.3f, alfa * 0.7f, 0);
                BufferUploader.drawWithShader(bb.end());
                RenderSystem.enableCull();
                RenderSystem.depthMask(true);
                RenderSystem.disableBlend();
            }
        }
        boolean escudo = CenaFuga.escudoAtivo();
        if (onda >= 0) {
            // a onda de luz branca se espalhando a partir da cratera
            if (!CinemaNecron.texturaPronta) CinemaNecron.criarBrilho();
            Matrix4f m0 = e.getPoseStack().last().pose();
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, CinemaNecron.BRILHO);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            float raio = 8 + onda * 2.4f, alfa = 0.85f * (1 - onda / com.lanterna.necron.Purificacao.ONDA);
            Vec3 c = ondaC.subtract(cam0);
            disco(bb, m0, c, raio, 0, 1f, 1f, 0.92f, alfa, 0);
            disco(bb, m0, c.add(0, 6, 0), raio * 0.8f, 0, 0.9f, 1f, 0.85f, alfa * 0.6f, 0);
            BufferUploader.drawWithShader(bb.end());
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
        if (fs.isEmpty() && !escudo) return;
        if (!pronto) texturas();
        if (!CinemaNecron.texturaPronta) CinemaNecron.criarBrilho();
        float tempo = mc.level.getGameTime() + e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        Matrix4f m = e.getPoseStack().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        for (BlockPos f : fs) if (f.distToCenterSqr(cam) < 400 * 400) portal(m, Vec3.atCenterOf(f).subtract(cam), tempo);
        if (escudo) CenaFuga.desenharEscudo(m, cam, e.getPartialTick());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static Atores atores;

    /** 0.56.7: o Embrião da Vida dormindo no Santuário, girando devagar e batendo como um coração. */
    private static void desenharEmbriao(RenderLevelStageEvent e, BlockPos b, Vec3 cam, float t, boolean preso) {
        if (atores == null) atores = new Atores();
        var ps = e.getPoseStack();
        var buf = Minecraft.getInstance().renderBuffers().bufferSource();
        float bate = 1 + 0.05f * Mth.sin(t * 0.12f) * Mth.sin(t * 0.12f);
        ps.pushPose();
        ps.translate(b.getX() + 0.5 - cam.x, b.getY() - 1.6 + 0.15 * Mth.sin(t * 0.05f) - cam.y, b.getZ() + 0.5 - cam.z);
        ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(t * 0.35f));
        ps.scale(1.2f * bate, 1.2f * bate, 1.2f * bate);
        // preso pela Morte (linha corrompida): apagado, quase só a silhueta
        if (preso) atores.embriao(ps, buf, t * 0.3f, 0.16f, 0.1f, 0.22f, 0.05f);
        else atores.embriao(ps, buf, t, 0.86f, 0.84f, 0.9f, 1f);
        ps.popPose();
        buf.endBatch();
    }

    private static float suave(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }

    /**
     * 0.58.0: onde o Embrião está, u ticks depois de acordar. Ele sobe pelo poço até sumir no céu sobre o Santuário e
     * depois desce do céu sobre a cratera (assim quem está num lugar ou no outro vê; antes ele atravessava ~900 blocos
     * por cima de pedaços do mundo que ninguém tinha carregado).
     */
    static Vec3 posEmbriao(BlockPos camara, BlockPos cratera, float u) {
        int voo = com.lanterna.necron.Purificacao.VOO, chega = com.lanterna.necron.Purificacao.CHEGA;
        Vec3 de = new Vec3(camara.getX() + 0.5, camara.getY() - 1.6, camara.getZ() + 0.5);
        Vec3 ate = new Vec3(cratera.getX() + 0.5, cratera.getY() + com.lanterna.necron.Purificacao.ALTO, cratera.getZ() + 0.5);
        if (u < voo) return de;
        int meio = voo + 90;
        if (u < meio) {
            float k = (u - voo) / (float) (meio - voo);
            return de.add(0, 180 * k * k, 0);
        }
        float k = suave((u - meio) / (float) (chega - meio));
        float desce = 1 - (1 - k) * (1 - k);
        return ate.add(0, 260 * (1 - desce), 0);
    }

    /**
     * 0.56.9: o Embrião acorda no Santuário (bate mais forte), sai da Terra voando até o céu sobre a cratera e fica lá,
     * enorme e aceso; no fim do Ritual da Terra ele sobe para o espaço. Devolve há quanto tempo a onda de luz começou
     * (ou -1 se ela não está passando agora).
     */
    private static float embriaoVoando(RenderLevelStageEvent e, BlockPos camara, BlockPos cratera, Vec3 cam, float agora, long tDesp, long tFim) {
        if (atores == null) atores = new Atores();
        float u = agora - tDesp;
        int voo = com.lanterna.necron.Purificacao.VOO, chega = com.lanterna.necron.Purificacao.CHEGA;
        Vec3 pos = posEmbriao(camara, cratera, u);
        float k0 = u < voo ? 0 : suave((u - voo) / (chega - voo));
        float esc = u < voo ? 1.2f * (1 + 0.12f * Mth.sin(u * (u < 50 ? 0.3f : 0.6f))) : 1.2f + 4.8f * k0;
        pos = pos.add(0, 0.6 * Mth.sin(agora * 0.04f), 0);
        float alfa = 1f;
        if (tFim >= 0) {
            float v = agora - tFim;
            if (v > 280) return -1;
            pos = pos.add(0, v * v * 0.006, 0);
            alfa = 1 - suave((v - 180) / 100);
        }
        if (pos.distanceToSqr(cam) < 700 * 700 && alfa > 0.02f) {
            var ps = e.getPoseStack();
            var buf = Minecraft.getInstance().renderBuffers().bufferSource();
            float bate = 1 + 0.06f * Mth.sin(agora * 0.15f) * Mth.sin(agora * 0.15f);
            ps.pushPose();
            ps.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
            ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(agora * 0.35f));
            ps.scale(esc * bate, esc * bate, esc * bate);
            float luz = u < voo ? 0.85f + 0.15f * (u / voo) : 1f;
            atores.embriao(ps, buf, agora, luz * alfa, luz * alfa, Math.min(1, luz * 1.05f) * alfa, 1f + (u > voo ? 1.5f : 0));
            ps.popPose();
            buf.endBatch();
        }
        float o = u - chega;
        return o >= 0 && o < com.lanterna.necron.Purificacao.ONDA ? o : -1;
    }

    private static void portal(Matrix4f m, Vec3 c, float tempo) {
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        // 1) o olho escuro (mistura normal): um disco preto-roxo que apaga o céu atrás
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, CinemaNecron.BRILHO);
        RenderSystem.defaultBlendFunc();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        disco(b, m, c, R * 1.25f, 0, 0.02f, 0f, 0.05f, 0.95f, 0.02f);
        BufferUploader.drawWithShader(b.end());
        // 2) a espiral (aditiva), duas camadas girando em sentidos opostos
        RenderSystem.setShaderTexture(0, ESPIRAL);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        disco(b, m, c, R, tempo * 0.012f, 0.9f, 0.55f, 1f, 1f, 0f);
        disco(b, m, c, R * 0.8f, -tempo * 0.02f + 1.3f, 0.7f, 0.35f, 1f, 0.7f, -0.3f);
        BufferUploader.drawWithShader(b.end());
        // 3) a borda acesa (anel de brilhos) e o clarão do meio
        RenderSystem.setShaderTexture(0, CinemaNecron.BRILHO);
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48 + tempo * 0.01;
            float pul = 0.6f + 0.4f * Mth.sin(tempo * 0.15f + i * 1.7f);
            plano(b, m, c.add(Math.cos(a) * R, 0, Math.sin(a) * R), 2.4f, 0.75f, 0.3f, 1f, 0.55f * pul);
        }
        plano(b, m, c.add(0, -0.4, 0), R * 0.55f, 0.55f, 0.15f, 0.9f, 0.35f + 0.1f * Mth.sin(tempo * 0.07f));
        BufferUploader.drawWithShader(b.end());
        // 4) faixas de luz caindo da borda, balançando (sempre viradas para quem olha)
        RenderSystem.setShaderTexture(0, FAIXA);
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Vec3 olho = c.scale(-1);
        for (int i = 0; i < 14; i++) {
            double a = Math.PI * 2 * i / 14 + tempo * 0.004 + Math.sin(i * 3.1) * 0.2;
            double rr = R * (0.55 + 0.4 * ((i * 7) % 5) / 4.0);
            Vec3 topo = c.add(Math.cos(a) * rr, -0.3, Math.sin(a) * rr);
            float comp = 14 + 10 * (0.5f + 0.5f * Mth.sin(tempo * 0.03f + i * 2.3f));
            Vec3 balanco = new Vec3(Math.sin(tempo * 0.04 + i) * 1.6, 0, Math.cos(tempo * 0.035 + i * 1.3) * 1.6);
            Vec3 fundo = topo.add(balanco).add(0, -comp, 0);
            float alfa = 0.6f + 0.35f * (0.5f + 0.5f * Mth.sin(tempo * 0.2f + i * 5.1f));
            faixa(b, m, topo, fundo, olho, 1.8f + (i % 3) * 0.6f, alfa);
            faixa(b, m, topo, fundo, olho, 0.5f, Math.min(1, alfa + 0.2f));   // o fio claro no meio da faixa
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.defaultBlendFunc();
    }

    /** Disco horizontal com a textura girando. */
    private static void disco(BufferBuilder b, Matrix4f m, Vec3 c, float r, float giro, float cr, float cg, float cb, float a, float dy) {
        float cs = Mth.cos(giro) * r, sn = Mth.sin(giro) * r;
        float x = (float) c.x, y = (float) c.y + dy, z = (float) c.z;
        b.vertex(m, x - cs + sn, y, z - sn - cs).uv(0, 0).color(cr, cg, cb, a).endVertex();
        b.vertex(m, x + cs + sn, y, z + sn - cs).uv(1, 0).color(cr, cg, cb, a).endVertex();
        b.vertex(m, x + cs - sn, y, z + sn + cs).uv(1, 1).color(cr, cg, cb, a).endVertex();
        b.vertex(m, x - cs - sn, y, z - sn + cs).uv(0, 1).color(cr, cg, cb, a).endVertex();
    }

    /** Um brilho deitado (horizontal). */
    private static void plano(BufferBuilder b, Matrix4f m, Vec3 c, float r, float cr, float cg, float cb, float a) {
        disco(b, m, c, r, 0, cr, cg, cb, a, 0);
    }

    /** Uma tira de luz de "topo" a "fundo", larga "w", virada para o olho. */
    static void faixa(BufferBuilder b, Matrix4f m, Vec3 topo, Vec3 fundo, Vec3 olho, float w, float a) {
        Vec3 eixo = fundo.subtract(topo);
        Vec3 meio = topo.add(fundo).scale(0.5);
        Vec3 lado = eixo.cross(olho.subtract(meio));
        if (lado.lengthSqr() < 1e-6) lado = new Vec3(1, 0, 0);
        lado = lado.normalize().scale(w / 2);
        b.vertex(m, (float) (topo.x - lado.x), (float) (topo.y - lado.y), (float) (topo.z - lado.z)).uv(0, 0).color(0.72f, 0.28f, 1f, a).endVertex();
        b.vertex(m, (float) (topo.x + lado.x), (float) (topo.y + lado.y), (float) (topo.z + lado.z)).uv(1, 0).color(0.72f, 0.28f, 1f, a).endVertex();
        b.vertex(m, (float) (fundo.x + lado.x), (float) (fundo.y + lado.y), (float) (fundo.z + lado.z)).uv(1, 1).color(0.72f, 0.28f, 1f, a).endVertex();
        b.vertex(m, (float) (fundo.x - lado.x), (float) (fundo.y - lado.y), (float) (fundo.z - lado.z)).uv(0, 1).color(0.72f, 0.28f, 1f, a).endVertex();
    }

    private RenderFenda() {}
}
