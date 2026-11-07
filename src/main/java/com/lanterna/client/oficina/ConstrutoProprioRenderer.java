package com.lanterna.client.oficina;

import com.lanterna.client.construct.EnergyShapes;
import com.lanterna.client.render.LanternaRenderTypes;
import com.lanterna.construct.oficina.Comportamento;
import com.lanterna.construct.oficina.ConstrutoProprio;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Desenha os construtos desenhados pelo jogador, com as animações de cada comportamento:
 *  montar     os cubinhos aparecem a partir da base, crescendo, com um clarão;
 *  arma       segura inclinada; no golpe, varre da direita para a esquerda com rastro de luz e volta;
 *  escudo     na frente, acompanhando o olhar, respirando de leve;
 *  disparo    gira no próprio eixo voando;
 *  queda      de cabeça para baixo, balança na descida, achata no impacto;
 *  veículo    inclina nas curvas e acelerações;
 *  estrutura  sobe do chão;
 *  desmontar  some de fora para dentro.
 */
public class ConstrutoProprioRenderer extends EntityRenderer<ConstrutoProprio> {
    public ConstrutoProprioRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(ConstrutoProprio e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(ConstrutoProprio e) {
        return new ResourceLocation("minecraft", "textures/misc/white.png");
    }

    private static float suave(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    @Override
    public void render(ConstrutoProprio e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        if (e.projeto().vazio()) return;
        float age = e.idade() + pt;
        float mostra = Mth.clamp(age / ConstrutoProprio.MONTAR, 0, 1) * 1.05f;
        float some = 1f;
        float alfa = 1f;
        if (age > e.vida()) {
            float d = Mth.clamp((age - e.vida()) / ConstrutoProprio.DESMONTAR, 0, 1);
            some = 1.05f - d * 1.1f;
            alfa = 1 - d * 0.6f;
        }
        float k = e.projeto().bloco();
        float[] cx = e.caixaLocal();
        float altura = cx[4] - cx[1];
        Vec3 origem = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        LivingEntity dono = e.dono();
        ps.pushPose();
        switch (e.comp()) {
            case ARMA -> {
                if (dono == null) break;
                Vec3 m = ConstrutoProprio.mao(dono, pt);
                ps.translate(m.x - origem.x, m.y - origem.y, m.z - origem.z);
                float yawD = Mth.rotLerp(pt, dono.yHeadRotO, dono.yHeadRot);
                float pitch = Mth.lerp(pt, dono.xRotO, dono.getXRot());
                ps.mulPose(Axis.YP.rotationDegrees(180 - yawD));
                // golpe em 3 tempos: prepara (puxa para a direita), varre para a esquerda, volta devagar
                float g = age - e.golpe();
                float arco = 0, deita = -35f;
                if (g >= 0 && g < 1.5f) {
                    float t = suave(g / 1.5f);
                    arco = Mth.lerp(t, 0f, -80f);
                    deita = Mth.lerp(t, -35f, -50f);
                } else if (g >= 1.5f && g < 4.5f) {
                    float t = suave((g - 1.5f) / 3f);
                    arco = Mth.lerp(t, -80f, 72f);
                    deita = Mth.lerp(t, -50f, -88f);
                    rastro(ps, buf, e, k, altura, g - 1.5f, t);
                } else if (g >= 4.5f && g < 11f) {
                    float t = suave((g - 4.5f) / 6.5f);
                    arco = Mth.lerp(t, 72f, 0f);
                    deita = Mth.lerp(t, -88f, -35f);
                }
                ps.mulPose(Axis.YP.rotationDegrees(arco));
                ps.mulPose(Axis.XP.rotationDegrees(-pitch * 0.6f + deita));
                // respira de leve parada
                ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin(age * 0.08f) * 2f));
            }
            case ESCUDO -> {
                if (dono != null) {
                    Vec3 olho = dono.getEyePosition(pt);
                    Vec3 olhar = dono.getViewVector(pt);
                    Vec3 p = olho.add(olhar.scale(1.3 + (cx[5] - cx[2]) * 0.3)).subtract(0, altura / 2, 0);
                    ps.translate(p.x - origem.x, p.y - origem.y, p.z - origem.z);
                    float yawD = Mth.rotLerp(pt, dono.yHeadRotO, dono.yHeadRot);
                    float pitch = Mth.lerp(pt, dono.xRotO, dono.getXRot());
                    ps.mulPose(Axis.YP.rotationDegrees(180 - yawD));
                    ps.translate(0, altura / 2, 0);
                    ps.mulPose(Axis.XP.rotationDegrees(-pitch));
                    float pulso = 1 + 0.025f * Mth.sin(age * 0.25f);
                    ps.scale(pulso, pulso, pulso);
                    ps.translate(0, -altura / 2, 0);
                }
            }
            case PROJETIL -> {
                ps.mulPose(Axis.YP.rotationDegrees(180 - e.getYRot()));
                ps.mulPose(Axis.XP.rotationDegrees(-e.getXRot()));
                ps.translate(0, -altura / 2, 0);
                ps.translate(0, altura / 2, 0);
                ps.mulPose(Axis.ZP.rotationDegrees(age * 24));
                ps.translate(0, -altura / 2, 0);
            }
            case QUEDA -> {
                ps.mulPose(Axis.YP.rotationDegrees(180 - e.getYRot()));
                boolean noChao = e.getY() <= e.alvoY() + 0.01;
                ps.translate(0, altura, 0);
                // de ponta-cabeça: o topo do desenho é o que bate
                ps.mulPose(Axis.XP.rotationDegrees(180));
                if (!noChao) {
                    ps.translate(0, altura / 2, 0);
                    ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin(age * 0.35f) * 6));
                    ps.mulPose(Axis.XP.rotationDegrees(Mth.cos(age * 0.3f) * 5));
                    ps.translate(0, -altura / 2, 0);
                } else {
                    float sq = 1 - 0.15f * Mth.clamp(1 - (age - e.vida() + 6) / 6f, 0, 1);
                    ps.scale(1 / sq, sq, 1 / sq);
                }
            }
            case VEICULO -> {
                float yawV = Mth.rotLerp(pt, e.yRotO, e.getYRot());
                ps.mulPose(Axis.YP.rotationDegrees(180 - yawV));
                Vec3 v = e.getDeltaMovement();
                float vira = Mth.wrapDegrees(e.getYRot() - e.yRotO);
                ps.mulPose(Axis.ZP.rotationDegrees(Mth.clamp(vira * 1.5f, -12, 12)));
                ps.mulPose(Axis.XP.rotationDegrees(Mth.clamp((float) v.y * -30f, -10, 10)));
                ps.translate(0, Mth.sin(age * 0.2f) * 0.03f, 0);
            }
            case ESTRUTURA -> {
                ps.mulPose(Axis.YP.rotationDegrees(180 - e.getYRot()));
                float sobe = suave(age / 12f);
                ps.translate(0, -(1 - sobe) * altura, 0);
            }
        }
        EnergyShapes.tempo = age;
        VertexConsumer corpo = buf.getBuffer(LanternaRenderTypes.ENERGIA);
        VertexConsumer brilho = buf.getBuffer(LanternaRenderTypes.BRILHO);
        float pop = e.idade() < ConstrutoProprio.MONTAR ? EnergyShapes.entrada(age, ConstrutoProprio.MONTAR) : 1f;
        ps.scale(pop, pop, pop);
        Malha.de(e.projeto()).desenhar(ps, corpo, brilho, e.cor(), alfa, k, mostra, some, age);
        // clarão de quando monta
        if (age < ConstrutoProprio.MONTAR + 4) {
            float a = 1 - Mth.clamp(age / (ConstrutoProprio.MONTAR + 4), 0, 1);
            EnergyShapes.clarao(ps, brilho, new Vec3(0, altura / 2, 0), Math.max(0.6f, altura * 0.7f), EnergyShapes.brilho(e.cor()), 0.7f * a, 0);
        }
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    /** Rastro de luz da ponta da arma durante o golpe (um leque translúcido). */
    private static void rastro(PoseStack ps, MultiBufferSource buf, ConstrutoProprio e, float k, float altura, float g, float ida) {
        if (g > 3.2f) return;
        VertexConsumer vc = buf.getBuffer(LanternaRenderTypes.BRILHO);
        int c = EnergyShapes.brilho(e.cor());
        float r = ((c >> 16) & 255) / 255f, gg = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        float alfa = 0.4f * (1 - g / 3.2f);
        Matrix4f m = ps.last().pose();
        float ate = Mth.lerp(ida, -80f, 72f), de = Math.max(-80f, ate - 75f);
        int n = 10;
        float raioFora = altura, raioDentro = altura * 0.35f;
        for (int i = 0; i < n; i++) {
            float a0 = (float) Math.toRadians(Mth.lerp(i / (float) n, de, ate)), a1 = (float) Math.toRadians(Mth.lerp((i + 1) / (float) n, de, ate));
            float f0 = alfa * (i / (float) n), f1 = alfa * ((i + 1) / (float) n);
            // varrendo no plano horizontal, na frente (-Z), à altura da mão
            v(vc, m, Mth.sin(a0) * raioDentro, 0.1f, -Mth.cos(a0) * raioDentro, r, gg, b, f0 * 0.2f);
            v(vc, m, Mth.sin(a0) * raioFora, 0.1f, -Mth.cos(a0) * raioFora, r, gg, b, f0);
            v(vc, m, Mth.sin(a1) * raioFora, 0.1f, -Mth.cos(a1) * raioFora, r, gg, b, f1);
            v(vc, m, Mth.sin(a1) * raioDentro, 0.1f, -Mth.cos(a1) * raioDentro, r, gg, b, f1 * 0.2f);
        }
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
        vc.vertex(m, x, y, z).color(r, g, b, a).uv(x * 0.5f, z * 0.5f).endVertex();
    }
}
