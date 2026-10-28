package com.lanterna.client.recharge;

import com.lanterna.LanternaMod;
import com.lanterna.item.PowerBatteryItem;
import com.lanterna.network.RechargeAnimPacket;
import com.lanterna.particle.GlowOrbOptions;
import com.lanterna.power.RechargeTimeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Animação de recarga pela bateria do slot (tecla R), vista por todos os jogadores:
 *
 *   1. Um portal na cor da Tropa abre na altura da cintura, na frente do lado do anel.
 *   2. A bateria (o mesmo modelo 3D do bloco) sobe de dentro do portal, girando devagar.
 *   3. O braço do anel vai até a bateria e encosta nela.
 *   4. Um fluxo de energia sai da bateria para a mão; no meio, o anel enche (clarão).
 *   5. O braço volta, a bateria desce e o portal fecha.
 */
public final class ClientRecharge {
    private static final ResourceLocation PORTAL = LanternaMod.id("textures/effect/recharge_portal.png");
    private static final ResourceLocation PORTAL_RING = LanternaMod.id("textures/effect/recharge_portal_ring.png");

    /** Ajustes de posição (em blocos, a partir dos pés do jogador). */
    private static final double FORWARD = 0.55;   // para frente
    private static final double SIDE = 0.32;      // para o lado do braço do anel
    private static final double HEIGHT = 0.70;    // altura da cintura
    private static final float PORTAL_RADIUS = 0.42f;
    private static final float BATTERY_SCALE = 0.62f;

    private record Anim(long start, String corps, int color, ItemStack battery) {}

    private static final Map<Integer, Anim> ACTIVE = new HashMap<>();

    public static void start(RechargeAnimPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ResourceLocation corps = ResourceLocation.tryParse(msg.corps());
        if (corps == null) return;
        ACTIVE.put(msg.entityId(), new Anim(mc.level.getGameTime(), msg.corps(), msg.color(),
                PowerBatteryItem.create(corps, msg.color())));

        Entity e = mc.level.getEntity(msg.entityId());
        if (e != null) {
            mc.level.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    SoundSource.PLAYERS, 0.6f, 1.6f, false);
            mc.level.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.PLAYERS, 0.5f, 1.8f, false);
        }
    }

    /** Ticks desde o início (com fração), ou -1 se esse jogador não está recarregando. */
    public static float time(Entity entity, float partialTick) {
        Anim a = ACTIVE.get(entity.getId());
        if (a == null || entity.level() == null) return -1;
        return (entity.level().getGameTime() - a.start()) + partialTick;
    }

    // ===================== Geometria =====================

    private static Vec3 forward(float yawDeg) {
        float yaw = yawDeg * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    private static Vec3 side(Player player, float yawDeg) {
        float yaw = yawDeg * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return player.getMainArm() == HumanoidArm.RIGHT ? right : right.scale(-1);
    }

    private static Vec3 portalCenter(Player player, float partialTick) {
        float yaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        Vec3 pos = player.getPosition(partialTick);
        return pos.add(forward(yaw).scale(FORWARD)).add(side(player, yaw).scale(SIDE)).add(0, HEIGHT, 0);
    }

    /** Onde a mão do anel fica quando encosta na bateria (aproximado). */
    private static Vec3 handPosition(Player player, float partialTick) {
        return portalCenter(player, partialTick).add(0, 0.38, 0);
    }

    // ===================== Curvas da animação =====================

    private static float ramp(float t, float a, float b) {
        return Mth.clamp((t - a) / (b - a), 0f, 1f);
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }

    /** Abertura do portal: 0 = fechado, 1 = aberto. */
    private static float portalOpen(float t) {
        return smooth(ramp(t, 0, RechargeTimeline.PORTAL_OPEN_END)) * (1f - smooth(ramp(t, RechargeTimeline.CLOSE_START, RechargeTimeline.TOTAL)));
    }

    /** Quanto a bateria saiu do portal: 0 = dentro, 1 = totalmente fora. */
    private static float batteryOut(float t) {
        return smooth(ramp(t, RechargeTimeline.RISE_START, RechargeTimeline.RISE_END))
                * (1f - smooth(ramp(t, RechargeTimeline.SINK_START, RechargeTimeline.SINK_END)));
    }

    /** Braço esticado até a bateria: 0 = normal, 1 = encostado. */
    public static float reach(float t) {
        if (t < 0) return 0;
        return smooth(ramp(t, RechargeTimeline.REACH_START, RechargeTimeline.REACH_END))
                * (1f - smooth(ramp(t, RechargeTimeline.RETRACT_START, RechargeTimeline.RETRACT_END)));
    }

    // ===================== Tick (partículas e sons) =====================

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        Iterator<Map.Entry<Integer, Anim>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Anim> entry = it.next();
            Anim anim = entry.getValue();
            long t = mc.level.getGameTime() - anim.start();
            Entity e = mc.level.getEntity(entry.getKey());
            if (t > RechargeTimeline.TOTAL + 2 || !(e instanceof Player player)) {
                it.remove();
                continue;
            }
            particles(player, anim, (int) t);
        }
    }

    private static void particles(Player player, Anim anim, int t) {
        var rnd = player.getRandom();
        var level = player.level();
        Vec3 portal = portalCenter(player, 1f);
        int color = anim.color();

        // Faíscas girando na borda do portal enquanto ele está aberto
        float open = portalOpen(t);
        if (open > 0.2f && t % 2 == 0) {
            for (int i = 0; i < 3; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double r = PORTAL_RADIUS * open;
                level.addParticle(GlowOrbOptions.of(color, 0.06f + rnd.nextFloat() * 0.05f),
                        portal.x + Math.cos(a) * r, portal.y + 0.02, portal.z + Math.sin(a) * r,
                        -Math.sin(a) * 0.03, 0.02, Math.cos(a) * 0.03);
            }
        }

        // Fluxo de energia: da bateria para a mão do anel
        if (t >= RechargeTimeline.FLOW_START && t < RechargeTimeline.FLOW_END) {
            Vec3 from = portal.add(0, 0.2, 0);
            Vec3 to = handPosition(player, 1f);
            for (int i = 0; i < 4; i++) {
                double k = rnd.nextDouble();
                Vec3 p = from.lerp(to, k).add((rnd.nextDouble() - 0.5) * 0.12, (rnd.nextDouble() - 0.5) * 0.12, (rnd.nextDouble() - 0.5) * 0.12);
                Vec3 v = to.subtract(p).scale(0.25);
                level.addParticle(GlowOrbOptions.of(rnd.nextInt(4) == 0 ? 0xFFFFFF : color, 0.05f + rnd.nextFloat() * 0.06f),
                        p.x, p.y, p.z, v.x, v.y, v.z);
            }
            if (t % 10 == 0) {
                level.playLocalSound(portal.x, portal.y, portal.z, SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS, 0.8f, 1.2f + (t - RechargeTimeline.FLOW_START) * 0.02f, false);
            }
        }

        // Clarão quando a carga entra no anel
        if (t == RechargeTimeline.CHARGE_AT) {
            Vec3 hand = handPosition(player, 1f);
            for (int i = 0; i < 30; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, b = rnd.nextDouble() * Math.PI - Math.PI / 2;
                Vec3 dir = new Vec3(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b));
                level.addParticle(GlowOrbOptions.of(i % 3 == 0 ? 0xFFFFFF : color, 0.12f + rnd.nextFloat() * 0.1f),
                        hand.x, hand.y, hand.z, dir.x * 0.18, dir.y * 0.18, dir.z * 0.18);
            }
        }

        if (t == RechargeTimeline.CLOSE_START) {
            level.playLocalSound(portal.x, portal.y, portal.z, SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.PLAYERS, 0.5f, 1.8f, false);
        }
    }

    // ===================== Desenho (portal + bateria) =====================

    public static void render(PoseStack poseStack, MultiBufferSource buffer, Vec3 camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ACTIVE.isEmpty()) return;

        for (Map.Entry<Integer, Anim> entry : ACTIVE.entrySet()) {
            Entity e = mc.level.getEntity(entry.getKey());
            if (!(e instanceof Player player) || player.isInvisible()) continue;

            Anim anim = entry.getValue();
            float t = (mc.level.getGameTime() - anim.start()) + partialTick;
            if (t < 0 || t > RechargeTimeline.TOTAL) continue;

            Vec3 center = portalCenter(player, partialTick).subtract(camera);
            float yaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
            int color = anim.color();
            float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;

            poseStack.pushPose();
            poseStack.translate(center.x, center.y, center.z);

            // Portal: disco deitado, com redemoinho girando e um anel girando ao contrário
            float open = portalOpen(t);
            if (open > 0.001f) {
                float size = PORTAL_RADIUS * open;
                float spin = t * 9f;
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(-yaw + spin));
                quad(poseStack, buffer.getBuffer(RenderType.entityTranslucentEmissive(PORTAL)), size, r, g, b, 0.9f * open);
                poseStack.popPose();
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(-yaw - spin * 1.6f));
                quad(poseStack, buffer.getBuffer(RenderType.entityTranslucentEmissive(PORTAL_RING)), size * 1.15f, r, g, b, 0.95f * open);
                poseStack.popPose();
            }

            // Bateria: sobe de dentro do portal, crescendo, e gira devagar
            float out = batteryOut(t);
            if (out > 0.001f) {
                float scale = BATTERY_SCALE * (0.15f + 0.85f * out);
                float lift = -0.25f + out * 0.5f + Mth.sin(t * 0.25f) * 0.015f * out;
                poseStack.pushPose();
                poseStack.translate(0, lift, 0);
                poseStack.mulPose(Axis.YP.rotationDegrees(-yaw + t * 2.5f));
                poseStack.scale(scale, scale, scale);
                mc.getItemRenderer().renderStatic(anim.battery(), ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, poseStack, buffer, mc.level, 0);
                poseStack.popPose();
            }
            poseStack.popPose();
        }
    }

    /** Quadrado deitado (no chão do portal), visto de cima e de baixo. */
    private static void quad(PoseStack poseStack, VertexConsumer vc, float half, float r, float g, float b, float a) {
        Matrix4f m = poseStack.last().pose();
        var n = poseStack.last().normal();
        int light = LightTexture.FULL_BRIGHT;
        // de cima
        vc.vertex(m, -half, 0, -half).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, -half, 0, half).color(r, g, b, a).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, half, 0, half).color(r, g, b, a).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, half, 0, -half).color(r, g, b, a).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        // de baixo
        vc.vertex(m, half, -0.001f, -half).color(r, g, b, a).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, -1, 0).endVertex();
        vc.vertex(m, half, -0.001f, half).color(r, g, b, a).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, -1, 0).endVertex();
        vc.vertex(m, -half, -0.001f, half).color(r, g, b, a).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, -1, 0).endVertex();
        vc.vertex(m, -half, -0.001f, -half).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, -1, 0).endVertex();
    }

    // ===================== Pose do braço =====================

    /** Chamado no fim da animação do modelo: o braço do anel desce e vai até a bateria. */
    public static void applyArm(HumanoidModel<?> model, LivingEntity entity, float partialTick) {
        if (!(entity instanceof Player player)) return;
        float t = time(player, partialTick);
        float k = reach(t);
        if (k <= 0f) return;

        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        ModelPart arm = right ? model.rightArm : model.leftArm;
        float side = right ? 1f : -1f;
        // Toque pulsando de leve enquanto a energia passa
        float pulse = (t > RechargeTimeline.FLOW_START && t < RechargeTimeline.FLOW_END) ? Mth.sin(t * 0.8f) * 0.04f : 0f;
        arm.xRot = Mth.lerp(k, arm.xRot, -0.95f + pulse);
        arm.yRot = Mth.lerp(k, arm.yRot, 0.18f * side);
        arm.zRot = Mth.lerp(k, arm.zRot, 0.0f);
        // Olha para a bateria
        model.head.xRot = Mth.lerp(k * 0.8f, model.head.xRot, 0.55f);
        model.hat.copyFrom(model.head);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private ClientRecharge() {}
}
