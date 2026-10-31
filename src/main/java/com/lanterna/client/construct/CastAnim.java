package com.lanterna.client.construct;

import com.lanterna.LanternaMod;
import com.lanterna.construct.CastAnimPacket;
import com.lanterna.construct.ConstructEntity;
import com.lanterna.construct.ConstructType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Animações de fazer construtos (terceira e primeira pessoa).
 *
 *   Estocada  (punho, rajada, aríete, jaula, cristal, mão, gancho, ponte, teleporte, avareza): puxa o punho e estende o braço
 *   Golpe     (martelo, ceifa): os dois braços sobem por cima da cabeça e descem com tudo
 *   Empurrão  (parede, escudo, pavor, esperança, vida): os dois braços abrem para a frente, como quem solta uma onda
 *   Varrida   (lâminas): o braço corta de um lado ao outro
 *   Mira      (laser, picareta, napalm): enquanto durar, o braço do anel aponta para onde você olha, tremendo de leve
 *
 * No começo de cada um, o anel dá um clarão na cor da Tropa.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CastAnim {
    public enum Estilo { ESTOCADA, GOLPE, EMPURRAO, VARRIDA, MIRA }

    private record Anim(Estilo estilo, long inicio, int dur, int cor) {}

    private static final Map<Integer, Anim> ANIMS = new HashMap<>();
    /** Quem está segurando um construto contínuo agora (laser, picareta, napalm). */
    private static final Map<Integer, Integer> MIRANDO = new HashMap<>();

    public static Estilo estilo(ConstructType t) {
        return switch (t) {
            case MARTELO, CEIFA -> Estilo.GOLPE;
            case PAREDE, ESCUDO, PAVOR, ESPERANCA, VIDA -> Estilo.EMPURRAO;
            case LAMINA -> Estilo.VARRIDA;
            case LASER, PICARETA, NAPALM -> Estilo.MIRA;
            default -> Estilo.ESTOCADA;
        };
    }

    private static int duracao(Estilo e) {
        return switch (e) {
            case GOLPE, EMPURRAO -> 14;
            case MIRA -> 8;
            default -> 10;
        };
    }

    public static void receber(CastAnimPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ConstructType t = ConstructType.byId(m.type());
        if (t == null) return;
        Estilo e = estilo(t);
        ANIMS.put(m.entity(), new Anim(e, mc.level.getGameTime(), duracao(e), m.color()));
        Entity ent = mc.level.getEntity(m.entity());
        if (ent instanceof LivingEntity le) clarao(le, m.color(), e);
    }

    /** Clarão do anel: faíscas na cor da Tropa saindo da mão. */
    private static void clarao(LivingEntity e, int cor, Estilo estilo) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 mao = mao(e, 1f);
        var d = new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 1.0f);
        var r = e.getRandom();
        int n = estilo == Estilo.EMPURRAO || estilo == Estilo.GOLPE ? 22 : 12;
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, b = r.nextDouble() * Math.PI - Math.PI / 2;
            double v = 0.08 + r.nextDouble() * 0.12;
            mc.level.addParticle(d, mao.x, mao.y, mao.z, Math.cos(a) * Math.cos(b) * v, Math.sin(b) * v, Math.sin(a) * Math.cos(b) * v);
        }
        for (int i = 0; i < 3; i++) mc.level.addParticle(ParticleTypes.END_ROD, mao.x, mao.y, mao.z,
                (r.nextDouble() - 0.5) * 0.1, (r.nextDouble() - 0.5) * 0.1, (r.nextDouble() - 0.5) * 0.1);
        if (estilo == Estilo.EMPURRAO) {
            // anel de energia se abrindo em volta do corpo
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2 / 24;
                mc.level.addParticle(d, e.getX() + Math.cos(a) * 0.6, e.getY() + 1.0, e.getZ() + Math.sin(a) * 0.6,
                        Math.cos(a) * 0.35, 0, Math.sin(a) * 0.35);
            }
        }
    }

    /** Posição da mão do anel no mundo (aproximada pelo corpo, ou perto da câmera em primeira pessoa). */
    public static Vec3 mao(LivingEntity e, float pt) {
        Minecraft mc = Minecraft.getInstance();
        boolean direita = !(e instanceof Player p) || p.getMainArm() == HumanoidArm.RIGHT;
        float side = direita ? 1 : -1;
        if (e == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Vec3 look = e.getViewVector(pt);
            Vec3 right = look.cross(new Vec3(0, 1, 0));
            if (right.lengthSqr() < 1e-4) right = new Vec3(1, 0, 0);
            right = right.normalize();
            Vec3 up = right.cross(look).normalize();
            return e.getEyePosition(pt).add(look.scale(0.55)).add(right.scale(0.3 * side)).add(up.scale(-0.22));
        }
        float yaw = Mth.lerp(pt, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 pos = e.getPosition(pt);
        Anim a = ANIMS.get(e.getId());
        boolean estendido = MIRANDO.containsKey(e.getId()) || (a != null && e.level().getGameTime() - a.inicio < a.dur);
        Vec3 look = e.getViewVector(pt);
        Vec3 ombro = pos.add(0, e.getBbHeight() * 0.76, 0).add(right.scale(-0.36 * side));
        return estendido ? ombro.add(look.scale(0.72)) : ombro.add(0, -0.6, 0);
    }

    public static boolean mirando(int entityId) {
        return MIRANDO.containsKey(entityId);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ANIMS.clear();
            MIRANDO.clear();
            return;
        }
        long now = mc.level.getGameTime();
        ANIMS.values().removeIf(a -> now - a.inicio > a.dur + 2);
        MIRANDO.clear();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ConstructEntity c && (c.kind() == ConstructEntity.Kind.BEAM || c.kind() == ConstructEntity.Kind.STREAM
                    || c.kind() == ConstructEntity.Kind.PICKAXE)) {
                LivingEntity o = c.owner();
                if (o != null) MIRANDO.put(o.getId(), c.color());
            }
        }
    }

    // ===================== Terceira pessoa =====================

    public static void apply(HumanoidModel<?> model, LivingEntity entity, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean direita = !(entity instanceof Player p) || p.getMainArm() == HumanoidArm.RIGHT;
        ModelPart arm = direita ? model.rightArm : model.leftArm;
        ModelPart outro = direita ? model.leftArm : model.rightArm;
        float side = direita ? 1 : -1;
        float age = entity.tickCount + pt;
        if (MIRANDO.containsKey(entity.getId())) {
            // aponta para onde olha
            arm.xRot = model.head.xRot - Mth.HALF_PI + Mth.sin(age * 1.9f) * 0.02f;
            arm.yRot = model.head.yRot + Mth.cos(age * 2.3f) * 0.02f;
            arm.zRot = 0;
            outro.xRot = Mth.lerp(0.5f, outro.xRot, -0.5f);
            outro.zRot = 0.15f * side;
            return;
        }
        Anim a = ANIMS.get(entity.getId());
        if (a == null) return;
        float t = (mc.level.getGameTime() - a.inicio + pt) / a.dur;
        if (t < 0 || t > 1) return;
        float env = t < 0.15f ? t / 0.15f : t > 0.75f ? (1 - t) / 0.25f : 1f;
        env = Mth.clamp(env, 0, 1);
        switch (a.estilo) {
            case ESTOCADA -> {
                // puxa (até 0,25) e estende com força (0,25-0,45), segura e volta
                float puxa = t < 0.25f ? t / 0.25f : 0f;
                float x = model.head.xRot - Mth.HALF_PI + 0.5f * puxa;
                arm.xRot = Mth.lerp(env, arm.xRot, x);
                arm.yRot = Mth.lerp(env, arm.yRot, model.head.yRot - 0.1f * side);
                arm.zRot = Mth.lerp(env, arm.zRot, 0);
                outro.xRot = Mth.lerp(env, outro.xRot, 0.35f);
            }
            case GOLPE -> {
                float sobe = t < 0.45f ? t / 0.45f : 1f;
                float desce = t < 0.45f ? 0f : Mth.clamp((t - 0.45f) / 0.15f, 0, 1);
                float x = Mth.lerp(desce, -2.9f * sobe, -0.55f);
                arm.xRot = Mth.lerp(env, arm.xRot, x);
                outro.xRot = Mth.lerp(env, outro.xRot, x);
                arm.zRot = Mth.lerp(env, arm.zRot, 0.15f * side);
                outro.zRot = Mth.lerp(env, outro.zRot, -0.15f * side);
                arm.yRot = outro.yRot = 0;
            }
            case EMPURRAO -> {
                float abre = Mth.clamp(t / 0.35f, 0, 1);
                arm.xRot = Mth.lerp(env, arm.xRot, -1.35f - 0.2f * abre);
                outro.xRot = Mth.lerp(env, outro.xRot, -1.35f - 0.2f * abre);
                arm.yRot = Mth.lerp(env, arm.yRot, -0.45f * abre * side);
                outro.yRot = Mth.lerp(env, outro.yRot, 0.45f * abre * side);
                arm.zRot = Mth.lerp(env, arm.zRot, 0);
                outro.zRot = Mth.lerp(env, outro.zRot, 0);
            }
            case VARRIDA -> {
                float k = Mth.clamp((t - 0.1f) / 0.6f, 0, 1);
                arm.xRot = Mth.lerp(env, arm.xRot, -1.5f);
                arm.yRot = Mth.lerp(env, arm.yRot, Mth.lerp(k, 1.1f, -1.0f) * side);
                arm.zRot = 0;
            }
            case MIRA -> {
                arm.xRot = Mth.lerp(env, arm.xRot, model.head.xRot - Mth.HALF_PI);
                arm.yRot = Mth.lerp(env, arm.yRot, model.head.yRot);
            }
        }
    }

    // ===================== Primeira pessoa =====================

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().isEmpty()) return;
        PoseStack ps = event.getPoseStack();
        float side = mc.player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        float age = mc.player.tickCount + event.getPartialTick();
        if (MIRANDO.containsKey(mc.player.getId())) {
            // braço erguido na frente, tremendo com a energia
            ps.translate(-0.12f * side + Mth.sin(age * 2.1f) * 0.004f, 0.16f + Mth.cos(age * 2.7f) * 0.004f, -0.12f);
            ps.mulPose(Axis.XP.rotationDegrees(-8));
            return;
        }
        Anim a = ANIMS.get(mc.player.getId());
        if (a == null) return;
        float t = (mc.level.getGameTime() - a.inicio + event.getPartialTick()) / a.dur;
        if (t < 0 || t > 1) return;
        float env = t < 0.15f ? t / 0.15f : t > 0.75f ? (1 - t) / 0.25f : 1f;
        switch (a.estilo) {
            case ESTOCADA -> {
                float puxa = t < 0.25f ? t / 0.25f : 0f;
                float vai = t < 0.25f ? 0 : Mth.clamp((t - 0.25f) / 0.15f, 0, 1);
                ps.translate(-0.1f * side * env, 0.12f * env, (0.18f * puxa - 0.4f * vai) * env);
            }
            case GOLPE -> {
                float sobe = t < 0.45f ? t / 0.45f : 1f;
                float desce = t < 0.45f ? 0f : Mth.clamp((t - 0.45f) / 0.15f, 0, 1);
                ps.translate(-0.15f * side * env, (0.45f * sobe - 0.55f * desce) * env, -0.15f * env);
                ps.mulPose(Axis.XP.rotationDegrees((40 * sobe - 70 * desce) * env));
            }
            case EMPURRAO -> {
                float abre = Mth.clamp(t / 0.35f, 0, 1);
                ps.translate(-0.18f * side * env, 0.16f * env, -0.3f * abre * env);
                ps.mulPose(Axis.ZP.rotationDegrees(-15 * side * abre * env));
            }
            case VARRIDA -> {
                float k = Mth.clamp((t - 0.1f) / 0.6f, 0, 1);
                ps.translate(Mth.lerp(k, 0.15f, -0.45f) * side * env, 0.1f * env, -0.15f * env);
                ps.mulPose(Axis.YP.rotationDegrees(Mth.lerp(k, -30, 40) * side * env));
            }
            case MIRA -> ps.translate(-0.12f * side * env, 0.16f * env, -0.12f * env);
        }
    }

    private CastAnim() {}
}
