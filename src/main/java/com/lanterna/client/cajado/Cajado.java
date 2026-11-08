package com.lanterna.client.cajado;

import com.lanterna.LanternaMod;
import com.lanterna.client.ClientVisibleRings;
import com.lanterna.client.recharge.ClientRecharge;
import com.lanterna.item.CompassionStaffItem;
import com.lanterna.particle.GlowOrbOptions;
import com.lanterna.power.RechargeTimeline;
import com.lanterna.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Como o Cajado da Compaixão aparece no personagem:
 *
 *   no slot de bateria   preso nas costas, na diagonal;
 *   segurando na mão     em pé, na frente do corpo, segurado com as duas mãos;
 *   recarregando (R)     o cajado aparece na mão do lado oposto ao anel, em pé; a mão do anel sobe e encosta o anel
 *                        no cristal do topo; a energia passa e o cajado volta para as costas.
 *                        Em primeira pessoa o cajado sobe pela esquerda e a mão do anel vai até o cristal.
 *
 * As posições são em pixels do modelo (1/16 de bloco), com Y para baixo e a frente em -Z, a partir do pescoço.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class Cajado {
    /** Escala do modelo (ele tem quase 3 blocos de altura). */
    private static final float ESCALA = 0.56f;
    /** Meia altura do cajado em pixels, já com a escala (o modelo vai de -1,47 a +1,47 bloco do centro). */
    private static final float MEIO = 1.475f * ESCALA * 16f;

    private static ItemStack modelo;

    static ItemStack modelo() {
        if (modelo == null) modelo = new ItemStack(ModItems.COMPASSION_STAFF.get());
        return modelo;
    }

    public static boolean eCajado(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof CompassionStaffItem;
    }

    private static boolean segurando(LivingEntity e) {
        return eCajado(e.getMainHandItem()) || eCajado(e.getOffhandItem());
    }

    /** Tempo da recarga índigo (com fração) ou -1. */
    static float recarga(LivingEntity e, float pt) {
        if (!ClientRecharge.indigo(e)) return -1;
        return ClientRecharge.time(e, pt);
    }

    private static float rampa(float t, float a, float b) {
        float x = Mth.clamp((t - a) / (b - a), 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    /** O cajado na mão durante a recarga: 0 = sumido, 1 = inteiro. */
    static float aparece(float t) {
        return rampa(t, 0, 10) * (1 - rampa(t, 66, 76));
    }

    /** A mão do anel no cristal: 0 = parada, 1 = encostada. */
    static float alcance(float t) {
        return rampa(t, 12, 26) * (1 - rampa(t, 56, 66));
    }

    // ===================== Braços =====================

    /** Aponta o braço (pivô no ombro) para um ponto do modelo, misturando com a pose atual. */
    private static void mirar(ModelPart braco, float tx, float ty, float tz, float k) {
        if (k <= 0) return;
        float dx = tx - braco.x, dy = ty - braco.y, dz = tz - braco.z;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-3f) return;
        dx /= len; dy /= len; dz /= len;
        float a = (float) Math.asin(Mth.clamp(dz, -1, 1));
        float c = (float) Mth.atan2(-dx, dy);
        braco.xRot = Mth.lerp(k, braco.xRot, a);
        braco.yRot = Mth.lerp(k, braco.yRot, 0);
        braco.zRot = Mth.lerp(k, braco.zRot, c);
    }

    private static boolean anelNaDireita(LivingEntity e) {
        return !(e instanceof Player p) || p.getMainArm() == HumanoidArm.RIGHT;
    }

    /** Fim da animação do modelo humano: pose dos braços com o cajado. */
    public static void bracos(HumanoidModel<?> m, LivingEntity e, float pt) {
        float t = recarga(e, pt);
        boolean direita = anelNaDireita(e);
        float lado = direita ? 1 : -1; // 1: anel na direita (-X do modelo)
        if (t >= 0) {
            ModelPart anel = direita ? m.rightArm : m.leftArm, outro = direita ? m.leftArm : m.rightArm;
            float ap = Math.min(1, aparece(t) * 1.4f), al = alcance(t);
            // o cajado fica do lado oposto ao anel, em pé na frente
            float cx = 4 * lado, cy = 10, cz = -7;
            mirar(outro, cx, cy - 1, cz, ap);
            // a mão do anel vai até o cristal do topo
            float topo = cy - MEIO;
            mirar(anel, cx - 1.2f * lado, topo + 1.5f, cz, al);
            m.head.xRot = Mth.lerp(al * 0.8f, m.head.xRot, 0.12f);
            m.head.yRot = Mth.lerp(al * 0.8f, m.head.yRot, 0.35f * lado);
            m.hat.copyFrom(m.head);
            copiarMangas(m);
            return;
        }
        if (segurando(e)) {
            // em pé, na frente, com as duas mãos
            float cx = -2 * lado;
            mirar(direita ? m.rightArm : m.leftArm, cx, 9, -7, 1);
            mirar(direita ? m.leftArm : m.rightArm, cx, 4, -8, 1);
            // balança de leve ao andar
            float bal = Mth.cos(e.walkAnimation.position(pt) * 0.6662f) * 0.08f * e.walkAnimation.speed(pt);
            m.rightArm.xRot += bal;
            m.leftArm.xRot += bal;
            copiarMangas(m);
        }
    }

    private static void copiarMangas(HumanoidModel<?> m) {
        if (m instanceof PlayerModel<?> pm) {
            pm.leftSleeve.copyFrom(pm.leftArm);
            pm.rightSleeve.copyFrom(pm.rightArm);
        }
    }

    // ===================== Desenho no personagem (camada) =====================

    /** Desenha o cajado no personagem. Chamado pela StaffLayer. */
    public static void desenhar(PlayerModel<AbstractClientPlayer> m, PoseStack ps, MultiBufferSource buf, int luz, AbstractClientPlayer p, float pt) {
        if (p.isInvisible()) return;
        float t = recarga(p, pt);
        boolean direita = anelNaDireita(p);
        float lado = direita ? 1 : -1;
        var itens = Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer();
        if (t >= 0) {
            float ap = aparece(t);
            if (ap > 0.01f) {
                ps.pushPose();
                m.body.translateAndRotate(ps);
                ps.translate(4 * lado / 16f, 10 / 16f, -7 / 16f);
                ps.mulPose(Axis.ZP.rotationDegrees(180));
                float s = ESCALA * ap;
                ps.scale(s, s, s);
                itens.renderItem(p, modelo(), ItemDisplayContext.NONE, false, ps, buf, luz);
                ps.popPose();
            }
            if (ap > 0.5f) return; // o das costas "foi" para a mão
        }
        if (segurando(p)) {
            ps.pushPose();
            m.body.translateAndRotate(ps);
            ps.translate(-2 * lado / 16f, 9 / 16f, -7 / 16f);
            ps.mulPose(Axis.ZP.rotationDegrees(180));
            ps.mulPose(Axis.XP.rotationDegrees(-6)); // topo um pouco para a frente
            ps.scale(ESCALA, ESCALA, ESCALA);
            itens.renderItem(p, modelo(), ItemDisplayContext.NONE, false, ps, buf, luz);
            ps.popPose();
            return;
        }
        if (!ClientVisibleRings.of(p.getId()).staff()) return;
        // nas costas, na diagonal
        ps.pushPose();
        m.body.translateAndRotate(ps);
        ps.translate(0, 6 / 16f, (p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty() ? 2.9f : 3.9f) / 16f);
        ps.mulPose(Axis.ZP.rotationDegrees(180 + 38));
        ps.scale(ESCALA * 0.92f, ESCALA * 0.92f, ESCALA * 0.92f);
        itens.renderItem(p, modelo(), ItemDisplayContext.NONE, false, ps, buf, luz);
        ps.popPose();
    }

    // ===================== Partículas da recarga =====================

    /** Ponto do modelo (pixels, a partir do pescoço) no mundo. */
    private static Vec3 noMundo(Player p, float px, float py, float pz) {
        float yaw = p.yBodyRot;
        Vec3 v = new Vec3(-px / 16.0, -(py / 16.0 - 1.501) - (p.isCrouching() ? 0.2 : 0), pz / 16.0)
                .yRot((float) Math.toRadians(-(180 - yaw)));
        return p.position().add(v);
    }

    public static void particulas(Player p, int cor, int t) {
        var r = p.getRandom();
        var l = p.level();
        float lado = anelNaDireita(p) ? 1 : -1;
        Vec3 cristal = noMundo(p, 4 * lado, 10 - MEIO, -7);
        if (t == 1) {
            // o cajado se forma na mão: faíscas subindo pelo cabo
            l.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9f, 0.7f, false);
        }
        if (t < 12) {
            Vec3 base = noMundo(p, 4 * lado, 10 + MEIO, -7);
            for (int i = 0; i < 3; i++) {
                Vec3 q = base.lerp(cristal, r.nextDouble());
                l.addParticle(GlowOrbOptions.of(cor, 0.05f + r.nextFloat() * 0.04f), q.x, q.y, q.z, 0, 0.01, 0);
            }
        }
        boolean minhaVisao = p == net.minecraft.client.Minecraft.getInstance().player
                && net.minecraft.client.Minecraft.getInstance().options.getCameraType().isFirstPerson();
        if (t >= 26 && t < 56) {
            // o anel encostado no cristal: a luz gira em volta e entra no anel
            for (int i = 0; i < (minhaVisao ? 1 : 3); i++) {
                double a = r.nextDouble() * Math.PI * 2, d = 0.25 + r.nextDouble() * 0.2;
                Vec3 q = cristal.add(Math.cos(a) * d, (r.nextDouble() - 0.5) * 0.3, Math.sin(a) * d);
                Vec3 v = cristal.subtract(q).scale(0.18);
                l.addParticle(GlowOrbOptions.of(r.nextInt(4) == 0 ? 0xFFFFFF : cor, 0.05f + r.nextFloat() * 0.05f), q.x, q.y, q.z, v.x, v.y, v.z);
            }
            if (t % 8 == 0)
                l.playLocalSound(cristal.x, cristal.y, cristal.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.0f + (t - 26) * 0.025f, false);
        }
        if (t == RechargeTimeline.CHARGE_AT) {
            for (int i = 0; i < (minhaVisao ? 10 : 36); i++) {
                double a = r.nextDouble() * Math.PI * 2, b = r.nextDouble() * Math.PI - Math.PI / 2;
                Vec3 dir = new Vec3(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b));
                l.addParticle(GlowOrbOptions.of(i % 3 == 0 ? 0xFFFFFF : cor, 0.12f + r.nextFloat() * 0.1f),
                        cristal.x, cristal.y, cristal.z, dir.x * 0.16, dir.y * 0.16, dir.z * 0.16);
            }
        }
    }

    // ===================== Primeira pessoa =====================

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void primeiraPessoa(RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer p = mc.player;
        if (p == null) return;
        float pt = e.getPartialTick();
        float t = recarga(p, pt);
        if (t < 0) return;
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        PoseStack ps = e.getPoseStack();
        MultiBufferSource buf = e.getMultiBufferSource();
        int luz = e.getPackedLight();
        boolean direita = p.getMainArm() == HumanoidArm.RIGHT;
        float lado = direita ? 1 : -1;
        float ap = aparece(t), al = alcance(t);
        // cajado: sobe pelo lado oposto ao anel até ficar em pé, com o cristal perto do meio da tela
        if (ap > 0.01f) {
            ps.pushPose();
            float sobe = 1 - ap;
            ps.translate(-0.42f * lado, -0.62f - sobe * 1.1f, -1.15f);
            ps.mulPose(Axis.ZP.rotationDegrees(-9 * lado));
            ps.mulPose(Axis.YP.rotationDegrees(t * 1.5f));
            float s = 0.42f;
            ps.scale(s, s, s);
            mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(p, modelo(), ItemDisplayContext.NONE, false, ps, buf, luz);
            ps.popPose();
        }
        // braço do anel: na mesma base do braço normal do Minecraft (sem girar em volta de um ponto longe, que jogava o
        // braço para trás); ele só desliza para o meio e para cima, vira um pouco para dentro e encosta o anel no cristal
        if (al > 0.01f || ap > 0) {
            ps.pushPose();
            float f = lado;
            float baixo = 1 - Math.max(al, 0.25f * ap);
            ps.translate(f * (0.64f - 0.78f * al), -0.6f + 0.5f * al - baixo * 0.35f, -0.72f - 0.18f * al);
            ps.mulPose(Axis.YP.rotationDegrees(f * (45.0F - 18f * al)));
            ps.mulPose(Axis.ZP.rotationDegrees(f * -14f * al));
            ps.translate(f * -1.0F, 3.6F, 3.5F);
            ps.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
            ps.mulPose(Axis.XP.rotationDegrees(200.0F));
            ps.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
            ps.translate(f * 5.6F, 0.0F, 0.0F);
            PlayerRenderer r = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(p);
            if (direita) r.renderRightHand(ps, buf, luz, p);
            else r.renderLeftHand(ps, buf, luz, p);
            // o anel no dedo (a parte que encosta no cristal)
            com.lanterna.client.RingHandLayer.noBraco(ps, buf, p, direita ? r.getModel().rightArm : r.getModel().leftArm, direita);
            ps.popPose();
        }
    }

    private Cajado() {}
}
