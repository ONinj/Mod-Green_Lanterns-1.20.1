package com.lanterna.client.space;

import com.lanterna.LanternaMod;
import com.lanterna.naves.NaveEntity;
import com.lanterna.naves.NaveTipo;
import com.lanterna.space.SpaceBodies;
import com.lanterna.space.VisaoLonge;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Desenha os jogadores do espaço que estão longe demais para o Minecraft mostrar ({@link VisaoLonge}).
 * Quanto mais longe, mais simples (e mais leve):
 *  - até 64 blocos: o jogador de verdade (o Minecraft desenha; aqui não faz nada);
 *  - até ~450: o boneco com a skin dele (ou a nave que ele pilota), com a aura da Tropa;
 *  - até 9.000: uma luz na cor da Tropa, sempre do tamanho mínimo para ser vista, com o rastro do voo;
 * e o nome com a distância aparece no painel do espaço, embaixo da luz.
 * Entre um pacote e outro (5 por segundo) a posição é prevista pela velocidade, então o movimento fica liso.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VisaoLongeCliente {
    private static final double MODELO_ATE = 450, PERTO = 64, ESCALA_LONGE = 140;

    static final class Remoto {
        UUID uuid;
        String nome;
        int cor;
        String nave = "", pintura = "";
        float yaw;
        Vec3 alvo = Vec3.ZERO, vel = Vec3.ZERO, pos, antes;
        int idade;               // ticks desde o último pacote
        final Deque<Vec3> rastro = new ArrayDeque<>();
        NaveEntity naveFalsa;
    }

    private static final Map<UUID, Remoto> REMOTOS = new HashMap<>();
    /** Teste: 0 tudo, 1 só luz, 2 nada. */
    public static int debugModo = 0;
    private static PlayerModel<Player> largo, fino;

    private static boolean teste;

    public static void receber(List<VisaoLonge.Visto> vistos) {
        if (!teste) atualizar(vistos);
    }

    private static void atualizar(List<VisaoLonge.Visto> vistos) {
        java.util.Set<UUID> agora = new java.util.HashSet<>();
        for (VisaoLonge.Visto v : vistos) {
            agora.add(v.uuid());
            Remoto r = REMOTOS.computeIfAbsent(v.uuid(), k -> new Remoto());
            r.uuid = v.uuid();
            r.nome = v.nome();
            r.cor = v.cor();
            r.yaw = v.yaw();
            if (!v.nave().equals(r.nave)) r.naveFalsa = null;
            r.nave = v.nave();
            r.pintura = v.pintura();
            r.alvo = new Vec3(v.x(), v.y(), v.z());
            r.vel = new Vec3(v.vx(), v.vy(), v.vz());
            r.idade = 0;
            if (r.pos == null || r.pos.distanceTo(r.alvo) > 4000) {
                r.pos = r.alvo;
                r.antes = r.alvo;
                r.rastro.clear();
            }
        }
        REMOTOS.keySet().removeIf(k -> !agora.contains(k));
    }

    public static void limpar() {
        REMOTOS.clear();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().equals(SpaceBodies.SPACE)) {
            if (!REMOTOS.isEmpty()) REMOTOS.clear();
            return;
        }
        for (var it = REMOTOS.values().iterator(); it.hasNext(); ) {
            Remoto r = it.next();
            if (++r.idade > 40) { it.remove(); continue; }   // sem notícia há 2 s: sumiu
            r.antes = r.pos;
            // previsão: onde ele deve estar agora, e a posição mostrada corre atrás dela suave
            Vec3 previsto = r.alvo.add(r.vel.scale(Math.min(r.idade, 10)));
            r.pos = r.pos.add(r.vel).lerp(previsto, 0.35);
            // rastro: um ponto a cada 2 ticks, mais longo quanto mais rápido
            if (mc.level.getGameTime() % 2 == 0) {
                r.rastro.addFirst(r.pos);
                int max = r.vel.length() > 8 ? 40 : 24;
                while (r.rastro.size() > max) r.rastro.removeLast();
            }
        }
    }

    /** O jogador de verdade já está carregado e perto: o Minecraft desenha ele. */
    private static boolean real(Minecraft mc, Remoto r, Vec3 cam) {
        Player p = mc.level.getPlayerByUUID(r.uuid);
        return p != null && p.position().distanceTo(cam) < PERTO + 16;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || debugModo == 2) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || REMOTOS.isEmpty() || !mc.level.dimension().equals(SpaceBodies.SPACE)) return;
        Camera camera = e.getCamera();
        Vec3 cam = camera.getPosition();
        float pt = e.getPartialTick();
        float t = (mc.level.getGameTime() + pt) / 20f;
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        for (Remoto r : REMOTOS.values()) {
            if (r.pos == null || real(mc, r, cam) || !r.nave.isEmpty()) continue;
            Vec3 pos = r.antes.lerp(r.pos, pt);
            if (pos.distanceTo(cam) < MODELO_ATE) boneco(ps, buf, cam, r, pos, t);
        }
        buf.endBatch();
    }

    /**
     * No céu (antes do mundo): o rastro e a luz de cada um, com o mesmo truque dos planetas (tudo puxado para perto da
     * câmera mantendo o tamanho aparente), então aparecem a qualquer distância.
     */
    public static void desenharCeu(Matrix4f pose, Vec3 cam, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || REMOTOS.isEmpty() || debugModo == 2) return;
        float t = (mc.level.getGameTime() + pt) / 20f;
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        com.mojang.blaze3d.systems.RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        var b = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
        b.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        int n = 0;
        for (Remoto r : REMOTOS.values()) {
            if (r.pos == null || real(mc, r, cam)) continue;
            Vec3 pos = r.antes.lerp(r.pos, pt);
            double d = pos.distanceTo(cam);
            rastro(b, pose, cam, r, pos);
            boolean nave = !r.nave.isEmpty();
            // perto: aura em volta do boneco; longe: a luz, sempre de uns 5 pixels pelo menos
            double raio = d < MODELO_ATE && !nave ? Math.max(1.4, d * 0.011) : Math.max(nave ? 5 : 3, d * 0.012);
            float a = d < MODELO_ATE && !nave ? (float) Mth.lerp(Mth.clamp((d - 64) / 300, 0, 1), 0.35, 0.9) + 0.08f * Mth.sin(t * 3) : 1f;
            luz(b, pose, cam, pos.add(0, 1, 0), r.cor, raio, a);
            n++;
        }
        if (n > 0) com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(b.end());
        else b.end();
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
    }

    /** Ponto da cena: puxado para perto da câmera mantendo o tamanho aparente (o céu é desenhado a até 150 blocos). */
    private static Vec3 cena(Vec3 cam, Vec3 p) {
        Vec3 rel = p.subtract(cam);
        double d = rel.length();
        return d > ESCALA_LONGE ? rel.scale(ESCALA_LONGE / d) : rel;
    }

    private static double fator(Vec3 cam, Vec3 p) {
        double d = p.distanceTo(cam);
        return d > ESCALA_LONGE ? ESCALA_LONGE / d : 1;
    }

    /** Brilho redondo virado para a câmera (miolo claro, borda na cor). */
    private static void luz(com.mojang.blaze3d.vertex.BufferBuilder vc, Matrix4f m, Vec3 cam, Vec3 p, int cor, double raio, float alpha) {
        Vec3 c = cena(cam, p);
        // nunca menor que uns 8 pixels de raio na tela
        float rr = (float) Math.max(raio * fator(cam, p), c.length() * 0.03);
        Vec3 toCam = c.scale(-1).normalize();
        Vec3 right = new Vec3(0, 1, 0).cross(toCam);
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = toCam.cross(right).normalize();
        float r = ((cor >> 16) & 255) / 255f, g = ((cor >> 8) & 255) / 255f, b = (cor & 255) / 255f;
        int n = 20;
        for (int camada = 0; camada < 2; camada++) {
            float raioC = camada == 0 ? rr : rr * 0.4f;
            float cr = camada == 0 ? r : Mth.lerp(0.7f, r, 1f), cg = camada == 0 ? g : Mth.lerp(0.7f, g, 1f), cb = camada == 0 ? b : Mth.lerp(0.7f, b, 1f);
            float a = camada == 0 ? alpha * 0.8f : alpha;
            for (int i = 0; i < n; i++) {
                double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
                Vec3 p0 = c.add(right.scale(Math.cos(a0) * raioC)).add(up.scale(Math.sin(a0) * raioC));
                Vec3 p1 = c.add(right.scale(Math.cos(a1) * raioC)).add(up.scale(Math.sin(a1) * raioC));
                vc.vertex(m, (float) c.x, (float) c.y, (float) c.z).color(cr, cg, cb, a).endVertex();
                vc.vertex(m, (float) c.x, (float) c.y, (float) c.z).color(cr, cg, cb, a).endVertex();
                vc.vertex(m, (float) p0.x, (float) p0.y, (float) p0.z).color(cr, cg, cb, 0f).endVertex();
                vc.vertex(m, (float) p1.x, (float) p1.y, (float) p1.z).color(cr, cg, cb, 0f).endVertex();
            }
        }
    }

    /** Rastro do voo: uma fita de luz virada para a câmera, sumindo no fim. */
    private static void rastro(com.mojang.blaze3d.vertex.BufferBuilder vc, Matrix4f m, Vec3 cam, Remoto r, Vec3 cabeca) {
        if (r.rastro.size() < 2 || r.vel.length() < 0.4) return;
        float cr = ((r.cor >> 16) & 255) / 255f, cg = ((r.cor >> 8) & 255) / 255f, cb = (r.cor & 255) / 255f;
        List<Vec3> pts = new ArrayList<>();
        pts.add(cabeca.add(0, 1, 0));
        for (Vec3 p : r.rastro) pts.add(p.add(0, 1, 0));
        int n = pts.size();
        for (int i = 0; i < n - 1; i++) {
            Vec3 a = pts.get(i), b = pts.get(i + 1);
            double da = a.distanceTo(cam), db = b.distanceTo(cam);
            float wa = (float) (Math.max(0.4, da * 0.0025) * (1 - i / (float) n)), wb = (float) (Math.max(0.4, db * 0.0025) * (1 - (i + 1) / (float) n));
            Vec3 sa = cena(cam, a), sb = cena(cam, b);
            float fa = (float) fator(cam, a), fb = (float) fator(cam, b);
            Vec3 dir = sb.subtract(sa);
            if (dir.lengthSqr() < 1e-8) continue;
            Vec3 lado = dir.cross(sa.add(sb).scale(-0.5)).normalize();
            Vec3 a0 = sa.add(lado.scale(wa * fa)), a1 = sa.subtract(lado.scale(wa * fa));
            Vec3 b0 = sb.add(lado.scale(wb * fb)), b1 = sb.subtract(lado.scale(wb * fb));
            float alA = 0.6f * (1 - i / (float) n), alB = 0.6f * (1 - (i + 1) / (float) n);
            vc.vertex(m, (float) a0.x, (float) a0.y, (float) a0.z).color(cr, cg, cb, alA).endVertex();
            vc.vertex(m, (float) a1.x, (float) a1.y, (float) a1.z).color(cr, cg, cb, alA).endVertex();
            vc.vertex(m, (float) b1.x, (float) b1.y, (float) b1.z).color(cr, cg, cb, alB).endVertex();
            vc.vertex(m, (float) b0.x, (float) b0.y, (float) b0.z).color(cr, cg, cb, alB).endVertex();
        }
    }

    private static PlayerModel<Player> modelo(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (largo == null) {
            largo = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
            fino = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
        }
        var c = mc.getConnection();
        PlayerInfo i = c == null ? null : c.getPlayerInfo(id);
        boolean slim = i != null ? "slim".equals(i.getModelName()) : "slim".equals(net.minecraft.client.resources.DefaultPlayerSkin.getSkinModelName(id));
        return slim ? fino : largo;
    }

    /** O boneco com a skin, na pose de voo (inclinado para frente quando está rápido). */
    private static void boneco(PoseStack ps, MultiBufferSource buf, Vec3 cam, Remoto r, Vec3 pos, float t) {
        PlayerModel<Player> m = modelo(r.uuid);
        double v = r.vel.length();
        float yaw = v > 0.2 ? (float) Math.toDegrees(Math.atan2(-r.vel.x, r.vel.z)) : r.yaw;
        float incl = (float) Mth.clamp(v * 30, 0, 75);
        pose(m, t, v);
        ps.pushPose();
        ps.translate(pos.x - cam.x, pos.y - cam.y + 1.0, pos.z - cam.z);
        ps.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        ps.mulPose(Axis.XP.rotationDegrees(incl));
        ps.scale(-1, -1, 1);
        ps.translate(0, -0.5f, 0);
        var skin = com.lanterna.client.voz.LigacaoHud.pele(r.uuid);
        m.renderToBuffer(ps, buf.getBuffer(RenderType.entityTranslucent(skin)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        ps.popPose();
    }

    private static void pose(PlayerModel<Player> m, float t, double v) {
        m.young = false; m.crouching = false; m.riding = false;
        m.head.xRot = v > 1 ? -0.9f : 0; m.head.yRot = 0; m.head.zRot = 0;
        m.body.xRot = 0; m.body.yRot = 0; m.body.zRot = 0;
        float braco = v > 1 ? 0.12f : 0.05f;
        m.rightArm.xRot = (float) Math.sin(t * 1.3) * 0.05f; m.rightArm.yRot = 0; m.rightArm.zRot = braco;
        m.leftArm.xRot = -(float) Math.sin(t * 1.3) * 0.05f; m.leftArm.yRot = 0; m.leftArm.zRot = -braco;
        m.rightLeg.xRot = 0.05f; m.rightLeg.yRot = 0; m.rightLeg.zRot = 0.03f;
        m.leftLeg.xRot = -0.05f; m.leftLeg.yRot = 0; m.leftLeg.zRot = -0.03f;
        m.hat.copyFrom(m.head); m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm); m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg); m.leftPants.copyFrom(m.leftLeg);
        m.setAllVisible(true);
    }

    /** Nome e distância embaixo de cada um (no painel do espaço). */
    public static void nomes(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || REMOTOS.isEmpty()) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Remoto r : REMOTOS.values()) {
            if (r.pos == null || real(mc, r, cam)) continue;
            double d = r.pos.distanceTo(cam);
            float[] s = com.lanterna.client.mission.Waypoints.project(r.pos.add(0, 1, 0), w, h);
            if (s == null) continue;
            String txt = r.nome + "  " + com.lanterna.client.hud.Hud.distance(d);
            int tw = mc.font.width(txt);
            int x = (int) s[0] - tw / 2, y = (int) s[1] + 8;
            g.fill(x - 2, y - 1, x + tw + 2, y + 9, 0x60000000);
            g.drawString(mc.font, Component.literal(txt), x, y, 0xFF000000 | r.cor, false);
        }
    }

    public static String depurar() {
        StringBuilder sb = new StringBuilder();
        for (Remoto r : REMOTOS.values()) sb.append(r.nome).append('=').append(r.pos).append(" nave=").append(r.naveFalsa != null).append("; ");
        return sb.toString();
    }

    /** Teste: inclui jogadores falsos. */
    public static void testar(List<VisaoLonge.Visto> falsos) {
        teste = true;
        atualizar(falsos);
    }

    private VisaoLongeCliente() {}
}
