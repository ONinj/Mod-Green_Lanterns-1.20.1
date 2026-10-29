package com.lanterna.client.construct;

import com.lanterna.client.render.LanternaRenderTypes;
import com.lanterna.construct.ConstructEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Desenha os construtos como formas de energia na cor da Tropa. */
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
        float age = e.age() + pt;
        float life = e.life();
        float alpha = Mth.clamp(age / 3f, 0, 1) * Mth.clamp((life - age) / 5f, 0, 1);
        float s = e.scale();
        Vec3 origin = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        LivingEntity owner = e.owner();
        switch (e.kind()) {
            case BEAM -> {
                Vec3 end = e.end().subtract(origin);
                float w = 0.09f + 0.02f * Mth.sin(age * 1.7f);
                EnergyShapes.tube(pose, glow, Vec3.ZERO, end, w * 2.6f, color, 0.35f * alpha);
                EnergyShapes.tube(pose, body, Vec3.ZERO, end, w * 1.4f, color, 0.65f * alpha);
                EnergyShapes.tube(pose, glow, Vec3.ZERO, end, w * 0.6f, 0xFFFFFF, 0.8f * alpha);
                pose.pushPose();
                pose.translate(end.x, end.y, end.z);
                float f = 0.25f + 0.08f * Mth.sin(age * 2.3f);
                EnergyShapes.box(pose, body, glow, -f, -f, -f, f, f, f, color, alpha);
                pose.popPose();
                // anel no punho
                EnergyShapes.box(pose, body, glow, -0.12f, -0.12f, -0.12f, 0.12f, 0.12f, 0.12f, color, alpha);
            }
            case HAMMER -> hammer(e, pose, body, glow, color, age, alpha, s, owner);
            case FIST -> {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
                pose.mulPose(Axis.XP.rotationDegrees(e.getXRot()));
                pose.scale(s, s, s);
                fist(pose, body, glow, color, alpha);
                pose.popPose();
            }
            case BOLT -> {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
                pose.mulPose(Axis.XP.rotationDegrees(e.getXRot()));
                EnergyShapes.box(pose, body, glow, -0.09f, -0.09f, -0.45f, 0.09f, 0.09f, 0.45f, color, alpha);
                pose.popPose();
            }
            case SHIELD -> {
                pose.pushPose();
                float h = owner != null ? owner.getBbHeight() : 1.8f;
                pose.translate(0, h / 2, 0);
                float r = Math.max(1.3f, h * 0.75f) * s;
                EnergyShapes.sphere(pose, body, r, color, 0.16f * alpha, age);
                EnergyShapes.sphere(pose, glow, r * 1.02f, color, 0.08f * alpha, age * 1.3f);
                pose.popPose();
            }
            case PICKAXE -> {
                Vec3 at = e.end().subtract(origin);
                pose.pushPose();
                pose.translate(at.x, at.y, at.z);
                if (owner != null) pose.mulPose(Axis.YP.rotationDegrees(-owner.getYRot()));
                pose.translate(0, 0, -0.9);
                pose.mulPose(Axis.XP.rotationDegrees(-60 + 70 * Math.abs(Mth.sin(age * 0.45f))));
                EnergyShapes.box(pose, body, glow, -0.06f, 0, -0.06f, 0.06f, 1.3f, 0.06f, color, alpha);
                EnergyShapes.box(pose, body, glow, -0.08f, 1.15f, -0.65f, 0.08f, 1.35f, 0.65f, color, alpha);
                EnergyShapes.box(pose, body, glow, -0.05f, 1.18f, 0.6f, 0.05f, 1.3f, 0.8f, color, alpha);
                EnergyShapes.box(pose, body, glow, -0.05f, 1.18f, -0.8f, 0.05f, 1.3f, -0.6f, color, alpha);
                pose.popPose();
            }
            case GRAPPLE -> {
                Vec3 start = owner != null ? owner.getEyePosition(pt).add(0, -0.4, 0).subtract(origin) : Vec3.ZERO;
                Vec3 end = e.end().subtract(origin);
                Vec3 d = end.subtract(start);
                int links = (int) Math.max(2, d.length() / 0.5);
                for (int i = 0; i < links; i++) {
                    Vec3 a = start.add(d.scale(i / (double) links)), b = start.add(d.scale((i + 0.7) / links));
                    EnergyShapes.tube(pose, i % 2 == 0 ? body : glow, a, b, i % 2 == 0 ? 0.07f : 0.05f, color, 0.8f * alpha);
                }
                pose.pushPose();
                pose.translate(end.x, end.y, end.z);
                EnergyShapes.box(pose, body, glow, -0.25f, -0.25f, -0.25f, 0.25f, 0.25f, 0.25f, color, alpha);
                pose.popPose();
            }
            case BLADE -> {
                float h = owner != null ? owner.getBbHeight() * 0.55f : 1;
                for (int i = 0; i < 3; i++) {
                    pose.pushPose();
                    pose.translate(0, h, 0);
                    pose.mulPose(Axis.YP.rotationDegrees(age * 28 + i * 120));
                    pose.translate(0, 0, 2.4 * s);
                    pose.mulPose(Axis.ZP.rotationDegrees(12));
                    EnergyShapes.box(pose, body, glow, -0.9f * s, -0.04f, -0.22f, 0.9f * s, 0.04f, 0.22f, color, alpha);
                    pose.popPose();
                }
            }
            case HAND -> {
                Vec3 start = owner != null ? owner.getEyePosition(pt).add(0, -0.4, 0).subtract(origin) : Vec3.ZERO;
                Vec3 at = e.end().subtract(origin);
                EnergyShapes.tube(pose, body, start, at, 0.08f, color, 0.6f * alpha);
                pose.pushPose();
                pose.translate(at.x, at.y, at.z);
                Vec3 d = at.subtract(start);
                pose.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(Math.atan2(d.x, d.z))));
                pose.scale(s * 1.4f, s * 1.4f, s * 1.4f);
                hand(pose, body, glow, color, alpha);
                pose.popPose();
            }
        }
    }

    /** Martelo gigante: levanta e desce até o ponto, acertando no tick 11. */
    private static void hammer(ConstructEntity e, PoseStack pose, VertexConsumer body, VertexConsumer glow, int color,
                               float age, float alpha, float s, LivingEntity owner) {
        float swing = Mth.clamp(age / 11f, 0, 1);
        swing = swing * swing * swing;
        float angle = Mth.lerp(swing, -25f, 90f);
        float len = 4.2f * s;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
        pose.translate(0, 0.2, -len);
        pose.mulPose(Axis.XP.rotationDegrees(angle));
        float hw = 0.22f * s;
        EnergyShapes.box(pose, body, glow, -hw, 0, -hw, hw, len, hw, color, alpha);
        float hx = 1.3f * s, hy = 0.8f * s, hz = 0.8f * s;
        EnergyShapes.box(pose, body, glow, -hx, len - hy, -hz, hx, len + hy, hz, color, alpha);
        EnergyShapes.box(pose, body, glow, -hx - 0.15f * s, len - hy * 0.7f, -hz * 0.7f, -hx, len + hy * 0.7f, hz * 0.7f, color, alpha);
        EnergyShapes.box(pose, body, glow, hx, len - hy * 0.7f, -hz * 0.7f, hx + 0.15f * s, len + hy * 0.7f, hz * 0.7f, color, alpha);
        pose.popPose();
        if (age > 11 && age < 18) {
            float k = (age - 11) / 7f;
            float r = 3.5f * s * k;
            pose.pushPose();
            pose.translate(0, 0.1, 0);
            EnergyShapes.box(pose, body, glow, -r, 0, -r, r, 0.08f, r, color, alpha * (1 - k));
            pose.popPose();
        }
    }

    private static void fist(PoseStack pose, VertexConsumer body, VertexConsumer glow, int c, float a) {
        EnergyShapes.box(pose, body, glow, -0.45f, -0.4f, -0.1f, 0.45f, 0.4f, 0.5f, c, a);
        for (int i = 0; i < 4; i++) {
            float x = -0.42f + i * 0.215f;
            EnergyShapes.box(pose, body, glow, x, -0.05f, 0.5f, x + 0.19f, 0.38f, 0.78f, c, a);
        }
        EnergyShapes.box(pose, body, glow, -0.62f, -0.35f, 0.15f, -0.42f, 0.05f, 0.55f, c, a);
        EnergyShapes.box(pose, body, glow, -0.3f, -0.3f, -0.9f, 0.3f, 0.3f, -0.1f, c, a * 0.7f);
    }

    private static void hand(PoseStack pose, VertexConsumer body, VertexConsumer glow, int c, float a) {
        EnergyShapes.box(pose, body, glow, -0.4f, -0.45f, -0.08f, 0.4f, 0.35f, 0.08f, c, a);
        for (int i = 0; i < 4; i++) {
            float x = -0.38f + i * 0.2f;
            EnergyShapes.box(pose, body, glow, x, 0.35f, -0.07f, x + 0.16f, 0.85f, 0.07f, c, a);
        }
        EnergyShapes.box(pose, body, glow, -0.62f, -0.25f, -0.07f, -0.4f, 0.2f, 0.07f, c, a);
    }

    @Override
    public ResourceLocation getTextureLocation(ConstructEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
