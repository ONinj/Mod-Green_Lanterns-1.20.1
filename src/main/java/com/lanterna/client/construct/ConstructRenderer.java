package com.lanterna.client.construct;

import com.lanterna.client.render.LanternaRenderTypes;
import com.lanterna.construct.ConstructEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Desenha os construtos como "luz sólida" na cor da Tropa: entram com um pulo de escala, têm contornos brilhantes,
 * deixam imagens fantasmas quando se movem e soltam clarões onde nascem e onde batem.
 */
public class ConstructRenderer extends EntityRenderer<ConstructEntity> {

    public ConstructRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0;
    }

    @Override
    public boolean shouldRender(ConstructEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(ConstructEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        VertexConsumer body = buffers.getBuffer(LanternaRenderTypes.ENERGY);
        VertexConsumer glow = buffers.getBuffer(LanternaRenderTypes.GLOW);
        int color = e.color();
        int gc = EnergyShapes.brilho(color);
        float age = e.age() + pt;
        EnergyShapes.tempo = age;
        float life = e.life();
        float alpha = Mth.clamp(age / 3f, 0, 1) * Mth.clamp((life - age) / 5f, 0, 1);
        float s = e.scale();
        float grow = EnergyShapes.entrada(age, 5f);
        Vec3 origin = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        LivingEntity owner = e.owner();
        switch (e.kind()) {
            case BEAM -> beam(e, pose, body, glow, color, gc, age, alpha, origin, owner, pt);
            case HAMMER -> hammer(e, pose, body, glow, color, gc, age, alpha, s);
            case FIST -> {
                Vec3 v = e.getDeltaMovement();
                for (int k = 2; k >= 0; k--) {
                    pose.pushPose();
                    pose.translate(-v.x * k * 0.45, -v.y * k * 0.45, -v.z * k * 0.45);
                    pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
                    pose.mulPose(Axis.XP.rotationDegrees(e.getXRot()));
                    pose.mulPose(Axis.ZP.rotationDegrees(age * 9));
                    float g = s * grow * (1 - k * 0.12f);
                    pose.scale(g, g, g);
                    fist(pose, body, glow, color, alpha * (k == 0 ? 1f : 0.3f / k));
                    pose.popPose();
                }
            }
            case BOLT -> {
                Vec3 v = e.getDeltaMovement();
                for (int k = 2; k >= 0; k--) {
                    pose.pushPose();
                    pose.translate(-v.x * k * 0.3, -v.y * k * 0.3, -v.z * k * 0.3);
                    pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
                    pose.mulPose(Axis.XP.rotationDegrees(e.getXRot()));
                    pose.mulPose(Axis.ZP.rotationDegrees(45 + age * 20));
                    float g = grow * (1 - k * 0.2f);
                    pose.scale(g, g, g);
                    EnergyShapes.box(pose, body, glow, -0.1f, -0.1f, -0.5f, 0.1f, 0.1f, 0.5f, color, alpha * (k == 0 ? 1f : 0.35f / k));
                    pose.popPose();
                }
                EnergyShapes.clarao(pose, glow, Vec3.ZERO, 0.35f, gc, 0.5f * alpha, 0);
            }
            case RAM -> {
                Vec3 v = e.getDeltaMovement();
                for (int k = 3; k >= 0; k--) {
                    pose.pushPose();
                    pose.translate(-v.x * k * 0.6, -v.y * k * 0.6, -v.z * k * 0.6);
                    pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
                    pose.mulPose(Axis.XP.rotationDegrees(e.getXRot()));
                    float g = s * grow;
                    pose.scale(g, g, g);
                    ram(pose, body, glow, color, alpha * (k == 0 ? 1f : 0.28f / k), age);
                    pose.popPose();
                }
            }
            case SHIELD -> {
                pose.pushPose();
                float h = owner != null ? owner.getBbHeight() : 1.8f;
                pose.translate(0, h / 2, 0);
                float r = Math.max(1.3f, h * 0.75f) * s * Math.min(1f, grow);
                r *= 1 + 0.015f * Mth.sin(age * 0.3f);
                EnergyShapes.sphere(pose, body, r, color, 0.14f * alpha, age);
                EnergyShapes.sphere(pose, glow, r * 1.02f, gc, 0.07f * alpha, age * 1.3f);
                EnergyShapes.grade(pose, glow, r * 1.01f, EnergyShapes.clarear(gc, 0.3f), 0.35f * alpha, age * 0.01f);
                pose.popPose();
            }
            case PICKAXE -> {
                Vec3 at = e.end().subtract(origin);
                pose.pushPose();
                pose.translate(at.x, at.y, at.z);
                if (owner != null) pose.mulPose(Axis.YP.rotationDegrees(-owner.getYRot()));
                pose.translate(0, 0, -0.9);
                pose.mulPose(Axis.XP.rotationDegrees(-60 + 70 * Math.abs(Mth.sin(age * 0.45f))));
                pose.scale(grow, grow, grow);
                EnergyShapes.box(pose, body, glow, -0.06f, 0, -0.06f, 0.06f, 1.3f, 0.06f, color, alpha);
                EnergyShapes.box(pose, body, glow, -0.08f, 1.15f, -0.6f, 0.08f, 1.35f, 0.6f, color, alpha);
                EnergyShapes.box(pose, body, glow, -0.05f, 1.17f, 0.55f, 0.05f, 1.31f, 0.85f, color, alpha);
                EnergyShapes.box(pose, body, glow, -0.05f, 1.17f, -0.85f, 0.05f, 1.31f, -0.55f, color, alpha);
                pose.popPose();
            }
            case GRAPPLE -> {
                Vec3 start = owner != null ? CastAnim.mao(owner, pt).subtract(origin) : Vec3.ZERO;
                Vec3 end = e.end().subtract(origin);
                Vec3 d = end.subtract(start);
                double len = d.length();
                int links = (int) Math.max(2, len / 0.42);
                double shown = Math.min(1, age / 4.0);
                Vec3 dir = d.normalize();
                float yawL = (float) Math.toDegrees(Math.atan2(dir.x, dir.z));
                float pitchL = (float) -Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1, 1)));
                for (int i = 0; i < links * shown; i++) {
                    Vec3 a = start.add(d.scale((i + 0.5) / links));
                    pose.pushPose();
                    pose.translate(a.x, a.y, a.z);
                    pose.mulPose(Axis.YP.rotationDegrees(yawL));
                    pose.mulPose(Axis.XP.rotationDegrees(pitchL));
                    pose.mulPose(Axis.ZP.rotationDegrees(i % 2 == 0 ? 0 : 90));
                    EnergyShapes.box(pose, body, glow, -0.12f, -0.035f, -0.2f, 0.12f, 0.035f, 0.2f, color, alpha * 0.9f);
                    pose.popPose();
                }
                pose.pushPose();
                pose.translate(end.x, end.y, end.z);
                pose.mulPose(Axis.YP.rotationDegrees(yawL));
                for (int k = 0; k < 3; k++) {
                    pose.pushPose();
                    pose.mulPose(Axis.ZP.rotationDegrees(k * 120));
                    pose.mulPose(Axis.XP.rotationDegrees(-35));
                    EnergyShapes.box(pose, body, glow, -0.05f, 0, -0.05f, 0.05f, 0.45f, 0.05f, color, alpha);
                    pose.popPose();
                }
                pose.popPose();
                EnergyShapes.clarao(pose, glow, end, 0.5f, gc, 0.6f * alpha, 4);
            }
            case BLADE -> {
                float h = owner != null ? owner.getBbHeight() * 0.55f : 1;
                for (int ghost = 2; ghost >= 0; ghost--) {
                    for (int i = 0; i < 3; i++) {
                        pose.pushPose();
                        pose.translate(0, h, 0);
                        pose.mulPose(Axis.YP.rotationDegrees(age * 28 + i * 120 - ghost * 9));
                        pose.translate(0, 0, 2.4 * s * Math.min(1f, grow));
                        pose.mulPose(Axis.ZP.rotationDegrees(12));
                        blade(pose, body, glow, color, s, alpha * (ghost == 0 ? 1f : 0.25f / ghost));
                        pose.popPose();
                    }
                }
            }
            case HAND -> {
                Vec3 start = owner != null ? CastAnim.mao(owner, pt).subtract(origin) : Vec3.ZERO;
                Vec3 at = e.end().subtract(origin);
                EnergyShapes.tube(pose, body, start, at, 0.07f, 0.11f, color, 0.55f * alpha, 8);
                EnergyShapes.tube(pose, glow, start, at, 0.03f, 0.04f, EnergyShapes.clarear(gc, 0.4f), 0.6f * alpha, 6);
                pose.pushPose();
                pose.translate(at.x, at.y, at.z);
                Vec3 d = at.subtract(start);
                pose.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(Math.atan2(d.x, d.z))));
                float g = s * 1.4f * grow;
                pose.scale(g, g, g);
                hand(pose, body, glow, color, alpha);
                pose.popPose();
            }
            case FEAR -> fear(e, pose, body, glow, color, age, life, s);
            case NOVA -> nova(pose, body, glow, color, gc, age, life, s);
            case STREAM -> stream(e, pose, body, glow, color, age, alpha, origin, owner, pt);
            case CRYSTAL -> crystal(e, pose, body, glow, color, gc, age, alpha);
            case TENDRIL -> tendril(e, pose, body, glow, color, gc, age, alpha, origin, owner, pt);
            case DEADHANDS -> deadHands(e, pose, body, glow, color, gc, age, alpha, origin, s);
        }
    }

    // ===================== Laser do punho =====================

    private static void beam(ConstructEntity e, PoseStack pose, VertexConsumer body, VertexConsumer glow, int color, int gc,
                             float age, float alpha, Vec3 origin, LivingEntity owner, float pt) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 start = owner != null ? CastAnim.mao(owner, pt) : origin;
        Vec3 endW = e.end();
        // Para quem atira, o fim é calculado aqui mesmo (sem esperar o servidor): o feixe gruda na mira
        if (owner != null && owner == mc.player) {
            Vec3 eye = owner.getEyePosition(pt), look = owner.getViewVector(pt);
            Vec3 far = eye.add(look.scale(48));
            BlockHitResult hit = owner.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            Vec3 stop = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
            double best = eye.distanceToSqr(stop);
            endW = stop;
            for (Entity t : owner.level().getEntities(owner, new AABB(eye, stop).inflate(1), x -> x instanceof LivingEntity && x.isAlive())) {
                var c = t.getBoundingBox().inflate(0.3).clip(eye, stop);
                if (c.isPresent() && c.get().distanceToSqr(eye) < best) {
                    best = c.get().distanceToSqr(eye);
                    endW = c.get();
                }
            }
        }
        Vec3 a = start.subtract(origin);
        Vec3 full = endW.subtract(origin);
        Vec3 d = full.subtract(a);
        double len = d.length();
        if (len < 0.05) return;
        Vec3 dir = d.scale(1 / len);
        double shown = len * Mth.clamp(age / 4f, 0, 1);   // o feixe "corre" até o alvo ao ligar
        Vec3 b = a.add(dir.scale(shown));
        float w = 0.045f + 0.008f * Mth.sin(age * 1.7f) + 0.004f * Mth.sin(age * 5.3f);
        EnergyShapes.tube(pose, glow, a, b, w * 3.2f, w * 3.6f, gc, 0.14f * alpha, 8);
        EnergyShapes.tube(pose, glow, a, b, w * 1.8f, w * 2f, gc, 0.3f * alpha, 8);
        EnergyShapes.tube(pose, body, a, b, w, w, color, 0.75f * alpha, 8);
        EnergyShapes.tube(pose, glow, a, b, w * 0.42f, w * 0.42f, 0xFFFFFF, 0.95f * alpha, 6);
        // Duas espirais de energia girando em volta do feixe
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dir.cross(up).normalize(), v = dir.cross(u).normalize();
        int pts = (int) Math.min(140, shown / 0.3);
        for (int strand = 0; strand < 2; strand++) {
            Vec3 prev = null;
            for (int i = 0; i <= pts; i++) {
                double t = i * 0.3;
                double ang = t * 2.2 - age * 0.55 + strand * Math.PI;
                double rr = w * 2.4 * (0.8 + 0.2 * Math.sin(t * 0.7 + age * 0.3));
                Vec3 p = a.add(dir.scale(t)).add(u.scale(Math.cos(ang) * rr)).add(v.scale(Math.sin(ang) * rr));
                if (prev != null) EnergyShapes.tube(pose, glow, prev, p, w * 0.22f, w * 0.22f, EnergyShapes.clarear(gc, 0.5f), 0.55f * alpha, 4);
                prev = p;
            }
        }
        // Pulsos correndo do punho até o alvo
        for (int k = 0; k < 5; k++) {
            double t = (age * 1.1 + k * shown / 5) % Math.max(0.1, shown);
            Vec3 p0 = a.add(dir.scale(t)), p1 = a.add(dir.scale(Math.min(shown, t + 0.7)));
            EnergyShapes.tube(pose, glow, p0, p1, w * 1.5f, w * 1.5f, EnergyShapes.clarear(gc, 0.4f), 0.5f * alpha, 6);
        }
        // Clarões: no punho e onde bate
        EnergyShapes.clarao(pose, glow, a, 0.22f + 0.04f * Mth.sin(age * 2f), gc, 0.85f * alpha, 4);
        if (shown >= len - 0.01) EnergyShapes.clarao(pose, glow, b, 0.45f + 0.1f * Mth.sin(age * 2.6f), gc, 0.9f * alpha, 6);
    }

    // ===================== Martelo =====================

    private static void hammer(ConstructEntity e, PoseStack pose, VertexConsumer body, VertexConsumer glow, int color, int gc,
                               float age, float alpha, float s) {
        float swing = Mth.clamp(age / 11f, 0, 1);
        swing = swing * swing * swing;
        float angle = Mth.lerp(swing, -25f, 90f);
        float len = 4.2f * s;
        float g = Math.min(1f, EnergyShapes.entrada(age, 4f));
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
        pose.translate(0, 0.2, -len);
        pose.mulPose(Axis.XP.rotationDegrees(angle));
        pose.scale(g, g, g);
        float hw = 0.2f * s;
        EnergyShapes.box(pose, body, glow, -hw, 0, -hw, hw, len, hw, color, alpha);
        EnergyShapes.box(pose, body, glow, -hw * 1.6f, len * 0.08f, -hw * 1.6f, hw * 1.6f, len * 0.16f, hw * 1.6f, color, alpha);
        float hx = 1.3f * s, hy = 0.8f * s, hz = 0.8f * s;
        EnergyShapes.box(pose, body, glow, -hx, len - hy, -hz, hx, len + hy, hz, color, alpha);
        EnergyShapes.box(pose, body, glow, -hx * 0.82f, len - hy * 1.12f, -hz * 0.82f, hx * 0.82f, len + hy * 1.12f, hz * 0.82f, color, alpha * 0.6f);
        EnergyShapes.box(pose, body, glow, -hx - 0.18f * s, len - hy * 0.7f, -hz * 0.7f, -hx, len + hy * 0.7f, hz * 0.7f, color, alpha);
        EnergyShapes.box(pose, body, glow, hx, len - hy * 0.7f, -hz * 0.7f, hx + 0.18f * s, len + hy * 0.7f, hz * 0.7f, color, alpha);
        pose.popPose();
        if (age > 11 && age < 20) {
            float k = (age - 11) / 9f;
            float r = 4.2f * s * (1 - (1 - k) * (1 - k));
            float a = alpha * (1 - k);
            EnergyShapes.anel(pose, glow, r, 0.6f * s, 0.35f * (1 - k) + 0.05f, gc, 0.8f * a);
            EnergyShapes.anel(pose, body, r * 0.7f, 0.4f * s, 0.15f, color, 0.4f * a);
            EnergyShapes.disco(pose, glow, r, gc, 0.5f * a, 0f);
        }
    }

    // ===================== Formas =====================

    private static void fist(PoseStack pose, VertexConsumer body, VertexConsumer glow, int c, float a) {
        // palma
        EnergyShapes.box(pose, body, glow, -0.42f, -0.38f, -0.1f, 0.42f, 0.36f, 0.5f, c, a);
        // dedos dobrados (duas falanges)
        for (int i = 0; i < 4; i++) {
            float x = -0.41f + i * 0.21f;
            EnergyShapes.box(pose, body, glow, x, 0.02f, 0.5f, x + 0.19f, 0.36f, 0.78f, c, a);
            EnergyShapes.box(pose, body, glow, x + 0.01f, -0.26f, 0.62f, x + 0.18f, 0.04f, 0.8f, c, a * 0.85f);
        }
        // polegar
        EnergyShapes.box(pose, body, glow, -0.6f, -0.32f, 0.15f, -0.4f, 0.04f, 0.6f, c, a);
        EnergyShapes.box(pose, body, glow, -0.58f, -0.3f, 0.55f, -0.18f, -0.1f, 0.72f, c, a * 0.9f);
        // punho e antebraço
        EnergyShapes.box(pose, body, glow, -0.32f, -0.3f, -0.55f, 0.32f, 0.3f, -0.1f, c, a * 0.8f);
        EnergyShapes.box(pose, body, glow, -0.26f, -0.26f, -1.0f, 0.26f, 0.26f, -0.55f, c, a * 0.5f);
    }

    private static void hand(PoseStack pose, VertexConsumer body, VertexConsumer glow, int c, float a) {
        EnergyShapes.box(pose, body, glow, -0.4f, -0.45f, -0.08f, 0.4f, 0.35f, 0.08f, c, a);
        for (int i = 0; i < 4; i++) {
            float x = -0.38f + i * 0.2f;
            float h = i == 1 || i == 2 ? 0.88f : 0.8f;
            EnergyShapes.box(pose, body, glow, x, 0.35f, -0.07f, x + 0.16f, h, 0.07f, c, a);
        }
        EnergyShapes.box(pose, body, glow, -0.62f, -0.25f, -0.07f, -0.4f, 0.2f, 0.07f, c, a);
    }

    private static void blade(PoseStack pose, VertexConsumer body, VertexConsumer glow, int c, float s, float a) {
        // lâmina curva em três partes, mais fina na ponta
        EnergyShapes.box(pose, body, glow, -0.9f * s, -0.04f, -0.24f, -0.25f * s, 0.04f, 0.2f, c, a);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-14));
        EnergyShapes.box(pose, body, glow, -0.28f * s, -0.035f, -0.2f, 0.4f * s, 0.035f, 0.16f, c, a);
        pose.popPose();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-30));
        EnergyShapes.box(pose, body, glow, 0.32f * s, -0.03f, -0.1f, 0.95f * s, 0.03f, 0.08f, c, a);
        pose.popPose();
    }

    /** Aríete (Verde): cunha enorme de vontade com aletas. */
    private static void ram(PoseStack pose, VertexConsumer body, VertexConsumer glow, int c, float a, float age) {
        EnergyShapes.box(pose, body, glow, -0.55f, -0.55f, -1.6f, 0.55f, 0.55f, 0.6f, c, a);
        for (int i = 0; i < 4; i++) {
            float k = 0.5f - i * 0.12f;
            EnergyShapes.box(pose, body, glow, -k, -k, 0.6f + i * 0.3f, k, k, 0.9f + i * 0.3f, c, a);
        }
        for (int i = 0; i < 4; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(45 + i * 90 + age * 6));
            EnergyShapes.box(pose, body, glow, -0.06f, 0.55f, -1.5f, 0.06f, 1.05f, -0.4f, c, a * 0.85f);
            pose.popPose();
        }
    }

    // ===================== Construtos das Tropas =====================

    /** Onda do Pavor (Amarelo): anel com espinhos que corre pelo chão, por cima de uma sombra. */
    private static void fear(ConstructEntity e, PoseStack pose, VertexConsumer body, VertexConsumer glow, int c, float age, float life, float raio) {
        float k = Mth.clamp(age / (life * 0.75f), 0, 1);
        float ease = 1 - (1 - k) * (1 - k);
        float r = raio * ease;
        float a = Mth.clamp(1.2f - k, 0, 1);
        EnergyShapes.disco(pose, body, r, 0x201400, 0.45f * a, 0.05f * a);
        EnergyShapes.anel(pose, glow, r, 0.8f, 0.4f, c, 0.9f * a);
        int n = 40;
        for (int i = 0; i < n; i++) {
            double ang = Math.PI * 2 * i / n;
            float hgt = (0.6f + 1.4f * (float) Math.abs(Math.sin(i * 12.9898 + 4.1))) * (1 - k * 0.6f);
            pose.pushPose();
            pose.translate(Math.cos(ang) * r, 0, Math.sin(ang) * r);
            pose.mulPose(Axis.YP.rotationDegrees((float) -Math.toDegrees(ang)));
            pose.mulPose(Axis.ZP.rotationDegrees(-25));
            EnergyShapes.box(pose, body, glow, -0.06f, 0, -0.06f, 0.06f, hgt, 0.06f, c, a);
            pose.popPose();
        }
        // olho do medo no meio, abrindo e fechando
        float olho = Mth.clamp(1 - Math.abs(k - 0.3f) * 3, 0, 1);
        if (olho > 0) {
            EnergyShapes.clarao(pose, glow, new Vec3(0, 2.2, 0), 0.9f * olho, c, 0.8f * olho, 8);
        }
    }

    /** Nova (Azul/Branco/teleporte Índigo): esfera de luz que se expande e some, com coluna de luz. */
    private static void nova(PoseStack pose, VertexConsumer body, VertexConsumer glow, int c, int gc, float age, float life, float raio) {
        float k = Mth.clamp(age / life, 0, 1);
        float ease = 1 - (float) Math.pow(1 - k, 3);
        float r = Math.max(0.3f, raio * ease);
        float a = 1 - k;
        EnergyShapes.sphere(pose, glow, r, gc, 0.12f * a, age);
        EnergyShapes.grade(pose, glow, r, EnergyShapes.clarear(gc, 0.4f), 0.25f * a, age * 0.03f);
        pose.pushPose();
        pose.translate(0, -0.95, 0);
        EnergyShapes.anel(pose, glow, r, 0.6f, 0.25f, gc, 0.7f * a);
        EnergyShapes.disco(pose, body, r, c, 0.25f * a, 0f);
        pose.popPose();
        if (raio > 5) EnergyShapes.tube(pose, glow, new Vec3(0, -1, 0), new Vec3(0, 14 * (1 - k * 0.5f), 0), 0.9f * (1 - k), 0.1f, gc, 0.35f * a, 10);
        EnergyShapes.clarao(pose, glow, Vec3.ZERO, 1.2f * (1 - k) + 0.2f, gc, 0.9f * a, 8);
    }

    /** Jorro de Napalm (Vermelho): cone de plasma que engrossa, tremendo como fogo. */
    private static void stream(ConstructEntity e, PoseStack pose, VertexConsumer body, VertexConsumer glow, int c, float age, float alpha,
                               Vec3 origin, LivingEntity owner, float pt) {
        Vec3 start = owner != null ? CastAnim.mao(owner, pt).subtract(origin) : Vec3.ZERO;
        Vec3 end = e.end().subtract(origin);
        Vec3 d = end.subtract(start);
        double len = d.length();
        if (len < 0.1) return;
        Vec3 dir = d.scale(1 / len);
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dir.cross(up).normalize(), v = dir.cross(u).normalize();
        int seg = 14;
        int quente = EnergyShapes.clarear(c, 0.35f), laranja = 0xFF8A1E;
        for (int i = 0; i < seg; i++) {
            double t0 = len * i / seg, t1 = len * (i + 1) / seg;
            float r0 = 0.12f + (float) t0 * 0.2f, r1 = 0.12f + (float) t1 * 0.2f;
            Vec3 j0 = u.scale(Math.sin(age * 1.3 + i) * 0.08 * i / seg).add(v.scale(Math.cos(age * 1.7 + i * 1.3) * 0.08 * i / seg));
            Vec3 j1 = u.scale(Math.sin(age * 1.3 + i + 1) * 0.08 * (i + 1) / seg).add(v.scale(Math.cos(age * 1.7 + (i + 1) * 1.3) * 0.08 * (i + 1) / seg));
            Vec3 p0 = start.add(dir.scale(t0)).add(j0), p1 = start.add(dir.scale(t1)).add(j1);
            float fade = 1f - (float) i / seg * 0.6f;
            EnergyShapes.tube(pose, body, p0, p1, r0, r1, c, 0.5f * alpha * fade, 8);
            EnergyShapes.tube(pose, glow, p0, p1, r0 * 1.35f, r1 * 1.35f, i % 2 == 0 ? laranja : quente, 0.22f * alpha * fade, 8);
            EnergyShapes.tube(pose, glow, p0, p1, r0 * 0.35f, r1 * 0.35f, 0xFFE0A0, 0.6f * alpha * fade, 6);
        }
        EnergyShapes.clarao(pose, glow, start, 0.3f, laranja, 0.9f * alpha, 5);
    }

    /** Cristal do Amor (Violeta): aglomerado de cristais em volta do alvo. */
    private static void crystal(ConstructEntity e, PoseStack pose, VertexConsumer body, VertexConsumer glow, int c, int gc, float age, float alpha) {
        Entity t = e.target();
        float h = t != null ? t.getBbHeight() + 0.5f : 2.2f;
        float w = t != null ? Math.max(0.7f, t.getBbWidth() * 0.9f) : 0.8f;
        float g = Math.min(1f, EnergyShapes.entrada(age, 6f));
        int n = 9;
        for (int i = 0; i < n; i++) {
            double ang = Math.PI * 2 * i / n + i * 0.37;
            float lean = 10 + (i * 37 % 19);
            float hh = h * (0.65f + 0.35f * (float) Math.abs(Math.sin(i * 7.31))) * g;
            pose.pushPose();
            pose.translate(Math.cos(ang) * w * 0.55, 0, Math.sin(ang) * w * 0.55);
            pose.mulPose(Axis.YP.rotationDegrees((float) -Math.toDegrees(ang)));
            pose.mulPose(Axis.ZP.rotationDegrees(lean));
            pose.mulPose(Axis.YP.rotationDegrees(45));
            float r = 0.18f + 0.06f * (i % 3);
            EnergyShapes.box(pose, body, glow, -r, 0, -r, r, hh, r, c, alpha);
            pose.translate(0, hh, 0);
            EnergyShapes.box(pose, body, glow, -r * 0.55f, 0, -r * 0.55f, r * 0.55f, r * 1.4f, r * 0.55f, c, alpha * 0.9f);
            pose.popPose();
        }
        EnergyShapes.sphere(pose, glow, w * 0.9f * g, gc, 0.06f * alpha, age);
    }

    /** Garra da Avareza (Laranja): tentáculo ondulando até o alvo, com garra na ponta. */
    private static void tendril(ConstructEntity e, PoseStack pose, VertexConsumer body, VertexConsumer glow, int c, int gc, float age, float alpha,
                                Vec3 origin, LivingEntity owner, float pt) {
        Vec3 start = owner != null ? CastAnim.mao(owner, pt).subtract(origin) : Vec3.ZERO;
        Vec3 end = e.end().subtract(origin);
        Vec3 d = end.subtract(start);
        double len = d.length();
        if (len < 0.1) return;
        Vec3 dir = d.scale(1 / len);
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dir.cross(up).normalize(), v = dir.cross(u).normalize();
        double shown = Math.min(1, age / 5.0);
        int seg = 18;
        Vec3 prev = start;
        for (int i = 1; i <= seg * shown; i++) {
            double t = (double) i / seg;
            double amp = Math.sin(t * Math.PI) * 0.6;
            Vec3 p = start.add(d.scale(t)).add(u.scale(Math.sin(t * 9 - age * 0.6) * amp)).add(v.scale(Math.cos(t * 7 - age * 0.5) * amp * 0.6));
            float rr = 0.12f * (1 - (float) t * 0.5f);
            EnergyShapes.tube(pose, body, prev, p, rr, rr * 0.9f, c, 0.6f * alpha, 6);
            EnergyShapes.tube(pose, glow, prev, p, rr * 0.4f, rr * 0.35f, EnergyShapes.clarear(gc, 0.5f), 0.7f * alpha, 4);
            prev = p;
        }
        if (shown >= 1) {
            // pulso de vida voltando para quem suga
            double t = 1 - (age * 0.08 % 1);
            EnergyShapes.clarao(pose, glow, start.add(d.scale(t)), 0.25f, gc, 0.8f * alpha, 0);
            EnergyShapes.clarao(pose, glow, end, 0.35f, gc, 0.6f * alpha, 3);
        }
    }

    /** Mãos dos Mortos (Preto): sombra no chão e mãos esqueléticas brotando e agarrando. */
    private static void deadHands(ConstructEntity e, PoseStack pose, VertexConsumer body, VertexConsumer glow, int c, int gc, float age, float alpha,
                                  Vec3 origin, float s) {
        Vec3 at = e.end().subtract(origin);
        float r = 4f * s;
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        EnergyShapes.disco(pose, body, r * Math.min(1f, age / 6f), 0x05000A, 0.75f * alpha, 0.15f * alpha);
        EnergyShapes.anel(pose, glow, r * Math.min(1f, age / 6f), 0.25f, 0.06f, gc, 0.6f * alpha);
        int n = 11;
        for (int i = 0; i < n; i++) {
            double ang = i * 2.39996;
            double dist = r * Math.sqrt((i + 0.5) / n) * 0.9;
            float sobe = Mth.clamp((age - i * 1.5f) / 8f, 0, 1);
            float agarra = Mth.sin(age * 0.25f + i) * 20;
            pose.pushPose();
            pose.translate(Math.cos(ang) * dist, -0.4 + 1.3 * sobe, Math.sin(ang) * dist);
            pose.mulPose(Axis.YP.rotationDegrees(i * 47));
            pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(age * 0.1f + i) * 12));
            // antebraço
            EnergyShapes.box(pose, body, glow, -0.07f, -1.1f, -0.07f, 0.07f, 0.1f, 0.07f, gc, alpha * 0.9f);
            // palma
            EnergyShapes.box(pose, body, glow, -0.18f, 0.1f, -0.05f, 0.18f, 0.36f, 0.05f, gc, alpha);
            for (int f = 0; f < 4; f++) {
                pose.pushPose();
                pose.translate(-0.15 + f * 0.1, 0.36, 0);
                pose.mulPose(Axis.XP.rotationDegrees(agarra + 15));
                EnergyShapes.box(pose, body, glow, -0.025f, 0, -0.025f, 0.025f, 0.28f, 0.025f, gc, alpha);
                pose.popPose();
            }
            pose.popPose();
        }
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(ConstructEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
