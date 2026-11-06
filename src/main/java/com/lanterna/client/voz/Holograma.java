package com.lanterna.client.voz;

import com.lanterna.LanternaMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * O holograma da ligação: cada pessoa na chamada aparece como uma figura de luz translúcida, na cor do anel,
 * com linhas de varredura, um disco de projeção embaixo e o nome em cima. Mexe a cabeça e gesticula quando fala.
 *
 *   primeira pessoa: flutua à frente e à esquerda da visão (pequena, para não atrapalhar);
 *   terceira pessoa: projetada ao lado do personagem, saindo do anel.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class Holograma {
    private static PlayerModel<Player> largo, fino;
    /** Teste: mostra o holograma mesmo sem ligação, com este nome/uuid. */
    public static UUID testeId;
    public static String testeNome;

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

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        Minecraft mc = Minecraft.getInstance();
        boolean primeira = mc.options.getCameraType() == CameraType.FIRST_PERSON;
        // terceira pessoa: no mundo (some atrás de paredes); primeira: por cima de tudo, como um visor
        if (e.getStage() != (primeira ? RenderLevelStageEvent.Stage.AFTER_WEATHER : RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)) return;
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        List<ClientLigacao.Pessoa> outros = new java.util.ArrayList<>(ClientLigacao.outros());
        if (outros.isEmpty() && testeId != null) outros.add(new ClientLigacao.Pessoa(testeId, testeNome, (System.currentTimeMillis() / 900) % 2 == 0));
        if (outros.isEmpty()) return;
        Camera cam = e.getCamera();
        float pt = e.getPartialTick();
        Vec3 cp = cam.getPosition();
        Vector3f f = cam.getLookVector(), l = cam.getLeftVector(), u = cam.getUpVector();
        if (primeira) {
            mc.renderBuffers().bufferSource().endBatch();
            com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        }
        PoseStack ps = e.getPoseStack();
        // depois da chuva a rotação da câmera já está na matriz do RenderSystem: não aplicar duas vezes
        if (!com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix().equals(new Matrix4f().identity(), 1e-4f)) ps = new PoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        int cor = LigacaoHud.cor();
        float t = (mc.player.tickCount + pt) / 20f;
        for (int i = 0; i < outros.size(); i++) {
            Vec3 base;
            float escala;
            if (primeira) {
                // à frente-esquerda da visão (acompanha para onde se olha), cada um um pouco mais para a direita
                escala = 0.3f;
                Vec3 fv = new Vec3(f.x(), f.y(), f.z()), lv = new Vec3(l.x(), l.y(), l.z()), uv = new Vec3(u.x(), u.y(), u.z());
                base = cp.add(fv.scale(1.5)).add(lv.scale(0.78 - i * 0.42)).add(uv.scale(0.1)).add(0, -0.95 * escala, 0);
            } else {
                Player p = mc.player;
                Vec3 pe = p.getPosition(pt);
                Vec3 lh = new Vec3(l.x(), 0, l.z()).normalize();
                Vec3 fh = new Vec3(f.x(), 0, f.z()).normalize();
                base = pe.add(lh.scale(1.25 + i * 0.85)).add(fh.scale(0.9)).add(0, 0.75 + Math.sin(t * 1.3 + i) * 0.04, 0);
                escala = 0.68f;
                feixe(ps, buf, cp, p.getPosition(pt).add(0, 0.9, 0).add(lh.scale(0.35)), base, cor, t);
            }
            desenhar(ps, buf, cp, base, escala, outros.get(i), cor, t + i * 1.7f, mc.font);
        }
        buf.endBatch();
    }

    private static void desenhar(PoseStack ps, MultiBufferSource buf, Vec3 cp, Vec3 base, float s, ClientLigacao.Pessoa pessoa, int cor, float t, Font font) {
        float r = ((cor >> 16) & 255) / 255f, g = ((cor >> 8) & 255) / 255f, b = (cor & 255) / 255f;
        // mistura com branco/ciano para parecer luz
        float tr = Mth.lerp(0.45f, r, 0.75f), tg = Mth.lerp(0.45f, g, 1f), tb = Mth.lerp(0.45f, b, 1f);
        float pisca = 0.78f + 0.08f * (float) Math.sin(t * 23) + ((int) (t * 7) % 11 == 0 ? -0.25f : 0);
        double dx = cp.x - base.x, dz = cp.z - base.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        ps.pushPose();
        ps.translate(base.x - cp.x, base.y - cp.y, base.z - cp.z);
        // disco de projeção
        disco(ps, buf, s * 0.75f, cor, t);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        ps.scale(-s, -s, s);
        ps.translate(0, -1.501f, 0);
        PlayerModel<Player> m = modelo(pessoa.id());
        pose(m, pessoa.falando(), t);
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(LigacaoHud.pele(pessoa.id())));
        m.renderToBuffer(ps, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tr, tg, tb, pisca);
        ps.popPose();
        // varredura: faixas de luz subindo pelo corpo, viradas para a câmera
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        VertexConsumer lv = buf.getBuffer(RenderType.lightning());
        Matrix4f mat = ps.last().pose();
        float alt = 1.9f * s, larg = 0.42f * s;
        for (int k = 0; k < 3; k++) {
            float y = ((t * 0.55f + k / 3f) % 1f) * alt;
            float h = 0.025f * s * 4;
            quad(lv, mat, -larg, y, larg, y + h, 0.05f * s, r, g, b, 0.22f);
        }
        ps.popPose();
        // nome
        ps.pushPose();
        ps.translate(0, 2.05f * s, 0);
        ps.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        float ts = 0.012f * Math.max(0.6f, s * 1.6f);
        ps.scale(-ts, -ts, ts);
        Component nome = Component.literal((pessoa.falando() ? "▶ " : "") + pessoa.nome());
        int w = font.width(nome);
        font.drawInBatch(nome, -w / 2f, 0, 0xFF000000 | (pessoa.falando() ? 0x7CF5A0 : cor), false, ps.last().pose(), buf,
                Font.DisplayMode.NORMAL, 0x50000000, LightTexture.FULL_BRIGHT);
        ps.popPose();
        ps.popPose();
    }

    private static void pose(PlayerModel<Player> m, boolean falando, float t) {
        m.young = false;
        m.crouching = false;
        m.riding = false;
        m.head.xRot = falando ? (float) Math.sin(t * 9) * 0.12f : (float) Math.sin(t * 0.8f) * 0.05f;
        m.head.yRot = (float) Math.sin(t * 0.6f) * 0.15f;
        m.head.zRot = 0;
        m.body.xRot = 0; m.body.yRot = 0; m.body.zRot = 0;
        m.rightArm.xRot = falando ? -0.35f + (float) Math.sin(t * 5) * 0.3f : (float) Math.sin(t * 1.1f) * 0.05f;
        m.rightArm.yRot = 0;
        m.rightArm.zRot = 0.06f + (float) Math.cos(t * 1.1f) * 0.04f;
        m.leftArm.xRot = (float) -Math.sin(t * 1.1f) * 0.05f;
        m.leftArm.yRot = 0;
        m.leftArm.zRot = -0.06f - (float) Math.cos(t * 1.1f) * 0.04f;
        m.rightLeg.xRot = 0; m.rightLeg.yRot = 0; m.rightLeg.zRot = 0;
        m.leftLeg.xRot = 0; m.leftLeg.yRot = 0; m.leftLeg.zRot = 0;
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
        m.setAllVisible(true);
    }

    private static void disco(PoseStack ps, MultiBufferSource buf, float raio, int cor, float t) {
        float r = ((cor >> 16) & 255) / 255f, g = ((cor >> 8) & 255) / 255f, b = (cor & 255) / 255f;
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();
        int n = 32;
        for (int anel = 0; anel < 2; anel++) {
            float r0 = raio * (anel == 0 ? 0.82f : 0.45f), r1 = raio * (anel == 0 ? 1f : 0.55f);
            float a = anel == 0 ? 0.45f : 0.3f + 0.15f * (float) Math.sin(t * 4);
            for (int i = 0; i < n; i++) {
                double a0 = (i / (double) n) * Math.PI * 2 + t * (anel == 0 ? 0.8 : -1.4), a1 = ((i + 1) / (double) n) * Math.PI * 2 + t * (anel == 0 ? 0.8 : -1.4);
                if (anel == 0 && i % 8 == 7) continue; // falhas no anel externo
                vc.vertex(m, (float) Math.cos(a0) * r0, 0.01f, (float) Math.sin(a0) * r0).color(r, g, b, a).endVertex();
                vc.vertex(m, (float) Math.cos(a0) * r1, 0.01f, (float) Math.sin(a0) * r1).color(r, g, b, a).endVertex();
                vc.vertex(m, (float) Math.cos(a1) * r1, 0.01f, (float) Math.sin(a1) * r1).color(r, g, b, a).endVertex();
                vc.vertex(m, (float) Math.cos(a1) * r0, 0.01f, (float) Math.sin(a1) * r0).color(r, g, b, a).endVertex();
            }
        }
        // cone de luz subindo do disco
        for (int i = 0; i < n; i++) {
            double a0 = i / (double) n * Math.PI * 2, a1 = (i + 1) / (double) n * Math.PI * 2;
            float rb = raio * 0.8f, rt = raio * 0.25f, h = raio * 1.1f;
            vc.vertex(m, (float) Math.cos(a0) * rb, 0.02f, (float) Math.sin(a0) * rb).color(r, g, b, 0.16f).endVertex();
            vc.vertex(m, (float) Math.cos(a0) * rt, h, (float) Math.sin(a0) * rt).color(r, g, b, 0f).endVertex();
            vc.vertex(m, (float) Math.cos(a1) * rt, h, (float) Math.sin(a1) * rt).color(r, g, b, 0f).endVertex();
            vc.vertex(m, (float) Math.cos(a1) * rb, 0.02f, (float) Math.sin(a1) * rb).color(r, g, b, 0.16f).endVertex();
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, float r, float g, float b, float a) {
        vc.vertex(m, x0, y0, z).color(r, g, b, a).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).endVertex();
    }

    /** Feixe fino do anel até o disco do holograma (terceira pessoa). */
    private static void feixe(PoseStack ps, MultiBufferSource buf, Vec3 cp, Vec3 de, Vec3 ate, int cor, float t) {
        float r = ((cor >> 16) & 255) / 255f, g = ((cor >> 8) & 255) / 255f, b = (cor & 255) / 255f;
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();
        Vec3 d = ate.subtract(de);
        Vec3 ver = de.subtract(cp);
        Vec3 lado = d.cross(ver).normalize().scale(0.012);
        float a = 0.35f + 0.15f * (float) Math.sin(t * 11);
        Vec3 a0 = de.subtract(cp), a1 = ate.subtract(cp);
        vc.vertex(m, (float) (a0.x - lado.x), (float) (a0.y - lado.y), (float) (a0.z - lado.z)).color(r, g, b, a).endVertex();
        vc.vertex(m, (float) (a0.x + lado.x), (float) (a0.y + lado.y), (float) (a0.z + lado.z)).color(r, g, b, a).endVertex();
        vc.vertex(m, (float) (a1.x + lado.x * 4), (float) (a1.y + lado.y * 4), (float) (a1.z + lado.z * 4)).color(r, g, b, a * 0.5f).endVertex();
        vc.vertex(m, (float) (a1.x - lado.x * 4), (float) (a1.y - lado.y * 4), (float) (a1.z - lado.z * 4)).color(r, g, b, a * 0.5f).endVertex();
    }

    private Holograma() {}
}
