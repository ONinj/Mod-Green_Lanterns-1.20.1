package com.lanterna.client.construct;

import com.lanterna.construct.ConstructEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Construtos que se seguram (picareta e broca), o fio de luz que liga o anel aos construtos e a energia se juntando
 * quando um construto nasce.
 */
final class Ferramentas {

    /** Para onde o dono está mirando (para quem segura, calculado aqui mesmo: o desenho não espera o servidor). */
    static Vec3 alvo(ConstructEntity e, LivingEntity owner, float pt, double alcance) {
        if (owner == Minecraft.getInstance().player) {
            Vec3 eye = owner.getEyePosition(pt), far = eye.add(owner.getViewVector(pt).scale(alcance));
            BlockHitResult hit = owner.level().clip(new ClipContext(eye, far, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, owner));
            return hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
        }
        return e.end();
    }

    /** Tubo visto dos dois lados (o tubo simples some quando visto por dentro/de costas). */
    static void cilindro(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, float r0, float r1, int rgb, float alpha, int lados) {
        EnergyShapes.tube(pose, vc, a, b, r0, r1, rgb, alpha, lados);
        EnergyShapes.tube(pose, vc, b, a, r1, r0, rgb, alpha, lados);
    }

    /** Gira a pose para o eixo +Z apontar na direção dada. */
    static void apontar(PoseStack pose, Vec3 dir) {
        float yaw = (float) Math.toDegrees(Math.atan2(dir.x, dir.z));
        float pitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1, 1)));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
    }

    /**
     * O fio do anel: nasce no anel e corre até o construto em poucos ticks, fino e brilhante, com pulsos de luz indo
     * do anel para a ponta; na ponta, um pequeno anel de encaixe. a e b em coordenadas locais.
     */
    static void fio(PoseStack pose, VertexConsumer body, VertexConsumer glow, Vec3 a, Vec3 b, int color, int gc,
                    float age, float alpha) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.05) return;
        double corre = Mth.clamp(age / 4f, 0, 1);
        Vec3 fim = a.add(d.scale(corre));
        float w = 0.016f + 0.004f * Mth.sin(age * 2.1f);
        EnergyShapes.tube(pose, glow, a, fim, w * 4f, w * 3f, gc, 0.16f * alpha, 6);
        cilindro(pose, body, a, fim, w, w * 0.8f, EnergyShapes.clarear(color, 0.55f), 0.95f * alpha, 4);
        // pulsos correndo pelo fio
        for (int k = 0; k < 3; k++) {
            double f = ((age * 0.09) + k / 3.0) % 1.0;
            if (f > corre) continue;
            EnergyShapes.clarao(pose, glow, a.add(d.scale(f)), 0.09f, gc, 0.8f * alpha, 0);
        }
        EnergyShapes.clarao(pose, glow, a, 0.14f, gc, 0.7f * alpha, 0);
        if (corre >= 1) {
            pose.pushPose();
            pose.translate(b.x, b.y, b.z);
            apontar(pose, d.normalize());
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.translate(0, -0.02, 0);
            float pulso = 1 + 0.15f * Mth.sin(age * 0.6f);
            EnergyShapes.anel(pose, glow, 0.16f * pulso, 0.05f, 0.04f, gc, 0.8f * alpha);
            pose.popPose();
            EnergyShapes.clarao(pose, glow, b, 0.3f, gc, 0.5f * alpha, 3);
        }
    }

    /** Energia se juntando no centro do construto nos primeiros ticks (pontos que espiralam para dentro). */
    static void juntando(PoseStack pose, VertexConsumer glow, Vec3 c, int gc, float age, float raio, int sem) {
        if (age > 9) return;
        float t = age / 9f;
        for (int k = 0; k < 10; k++) {
            float a = k * 2.399f + sem + age * 0.35f;
            float r = raio * (1 - t) * (0.7f + 0.3f * Mth.sin(k * 1.7f));
            float y = (Mth.sin(k * 3.1f) * 0.8f) * raio * (1 - t);
            Vec3 p = c.add(Mth.cos(a) * r, y, Mth.sin(a) * r);
            EnergyShapes.clarao(pose, glow, p, 0.12f, gc, 0.9f * (0.4f + 0.6f * t), 0);
        }
        EnergyShapes.clarao(pose, glow, c, 0.25f + 0.6f * t, gc, 0.5f * t, 0);
    }

    // ===================== Picareta =====================

    /**
     * Picareta segurada na frente da mão, apontada para onde o dono olha, batendo sem parar; o fio do anel liga a
     * mão ao cabo.
     */
    static void picareta(ConstructEntity e, PoseStack pose, VertexConsumer body, VertexConsumer glow, int color, int gc,
                         float age, float alpha, Vec3 origin, LivingEntity owner, float pt, float grow) {
        Vec3 mao = CastAnim.mao(owner, pt);
        Vec3 look = owner.getViewVector(pt);
        Vec3 cabo = mao.add(look.scale(0.28));
        fio(pose, body, glow, mao.subtract(origin), cabo.subtract(origin), color, gc, age, alpha);
        pose.pushPose();
        pose.translate(cabo.x - origin.x, cabo.y - origin.y, cabo.z - origin.z);
        apontar(pose, look);
        // a batida: levanta e desce de novo (gira em volta da mão)
        float golpe = Mth.abs(Mth.sin(age * 0.42f));
        pose.mulPose(Axis.XP.rotationDegrees(-55 + 70 * golpe));
        float g = 1.05f * grow;
        pose.scale(g, g, g);
        // cabo para a frente, cabeça atravessada na ponta
        EnergyShapes.box(pose, body, glow, -0.045f, -0.045f, 0f, 0.045f, 0.045f, 0.95f, color, alpha);
        EnergyShapes.box(pose, body, glow, -0.06f, -0.42f, 0.86f, 0.06f, 0.42f, 1.0f, color, alpha);
        EnergyShapes.box(pose, body, glow, -0.04f, 0.38f, 0.84f, 0.04f, 0.62f, 0.98f, color, alpha);
        EnergyShapes.box(pose, body, glow, -0.04f, -0.62f, 0.84f, 0.04f, -0.38f, 0.98f, color, alpha);
        EnergyShapes.clarao(pose, glow, new Vec3(0, -0.62, 0.92), 0.12f, gc, 0.6f * alpha * golpe, 0);
        pose.popPose();
    }

    // ===================== Broca =====================

    /**
     * Broca: um míssil de luz que vai da mão até a parede que está sendo cortada (preso ao anel pelo fio), com três
     * coroas dentadas na ponta girando em sentidos alternados e aletas na cauda. Encostada num bloco, solta faíscas.
     */
    static void broca(ConstructEntity e, PoseStack pose, VertexConsumer body, VertexConsumer glow, int color, int gc,
                      float age, float alpha, Vec3 origin, LivingEntity owner, float pt, float grow) {
        Vec3 mao = CastAnim.mao(owner, pt);
        Vec3 look = owner.getViewVector(pt);
        Vec3 alvo = alvo(e, owner, pt, 4.5);
        double dist = alvo.distanceTo(mao);
        boolean encostada = dist < 4.4;
        // a broca avança até a ponta encostar no bloco (sem passar da mão)
        double frente = encostada ? Mth.clamp(dist - 1.45 * 1.35, 0.35, 4) : 0.9;
        Vec3 cauda = mao.add(look.scale(frente));
        fio(pose, body, glow, mao.subtract(origin), cauda.subtract(origin), color, gc, age, alpha);
        pose.pushPose();
        pose.translate(cauda.x - origin.x, cauda.y - origin.y, cauda.z - origin.z);
        apontar(pose, look);
        float g = 1.35f * grow;
        pose.scale(g, g, g);
        float tremor = encostada ? 0.012f * Mth.sin(age * 9f) : 0;
        pose.translate(tremor, tremor * 0.7f, 0);
        int claro = EnergyShapes.clarear(color, 0.25f);
        // corpo do míssil: prisma de oito lados (duas caixas, uma girada 45°), afinando na cauda
        for (int k = 0; k < 2; k++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(k * 45));
            EnergyShapes.box(pose, body, body, -0.15f, -0.15f, 0.12f, 0.15f, 0.15f, 0.95f, color, alpha);
            EnergyShapes.box(pose, body, body, -0.1f, -0.1f, 0f, 0.1f, 0.1f, 0.12f, color, alpha);
            pose.popPose();
        }
        // faixas de luz correndo pelo corpo, da cauda para a ponta
        for (int k = 0; k < 2; k++) {
            float z = 0.2f + ((age * 0.06f + k / 2f) % 1f) * 0.7f;
            EnergyShapes.box(pose, body, glow, -0.165f, -0.165f, z, 0.165f, 0.165f, z + 0.05f, claro, 0.5f * alpha);
        }
        // aletas na cauda
        for (int k = 0; k < 4; k++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(45 + k * 90));
            EnergyShapes.box(pose, body, body, -0.015f, 0.12f, 0.0f, 0.015f, 0.3f, 0.32f, color, alpha);
            pose.popPose();
        }
        // motor da cauda
        EnergyShapes.clarao(pose, glow, new Vec3(0, 0, -0.02), 0.16f, gc, 0.5f * alpha, 4);
        // três coroas dentadas, cada vez menores, girando em sentidos alternados
        float giro = age * (encostada ? 38f : 14f);
        float[][] coroas = {{0.95f, 0.15f, 0.15f}, {1.1f, 0.11f, 0.14f}, {1.24f, 0.07f, 0.13f}};
        for (int c = 0; c < coroas.length; c++) {
            float z0 = coroas[c][0], r = coroas[c][1], comp = coroas[c][2];
            pose.pushPose();
            pose.translate(0, 0, z0);
            pose.mulPose(Axis.ZP.rotationDegrees(c % 2 == 0 ? giro : -giro * 1.3f));
            for (int k = 0; k < 2; k++) {
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotationDegrees(k * 45));
                EnergyShapes.box(pose, body, body, -r, -r, 0, r, r, comp, claro, alpha);
                pose.popPose();
            }
            // dentes em volta, inclinados para a frente
            int dentes = 8 - c * 2;
            for (int d = 0; d < dentes; d++) {
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotationDegrees(d * 360f / dentes + 22.5f));
                pose.translate(0, r * 1.05f, comp * 0.5f);
                pose.mulPose(Axis.XP.rotationDegrees(-40));
                EnergyShapes.box(pose, body, body, -0.022f, 0f, -0.03f, 0.022f, 0.07f, 0.04f, claro, alpha);
                pose.popPose();
            }
            pose.popPose();
        }
        // ponta
        EnergyShapes.box(pose, body, body, -0.03f, -0.03f, 1.37f, 0.03f, 0.03f, 1.48f, claro, alpha);
        pose.popPose();
        if (encostada) {
            Vec3 p = alvo.subtract(origin);
            EnergyShapes.clarao(pose, glow, p, 0.45f + 0.1f * Mth.sin(age * 1.7f), gc, 0.85f * alpha, 6);
            if (e.level().random.nextInt(2) == 0) {
                Vec3 v = new Vec3(e.level().random.nextGaussian(), e.level().random.nextGaussian(), e.level().random.nextGaussian()).scale(0.12).subtract(look.scale(0.15));
                e.level().addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, alvo.x, alvo.y, alvo.z, v.x, v.y, v.z);
            }
        }
    }

    private Ferramentas() {}
}
