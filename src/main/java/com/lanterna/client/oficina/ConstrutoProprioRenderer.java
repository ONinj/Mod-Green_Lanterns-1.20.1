package com.lanterna.client.oficina;

import com.lanterna.client.construct.EnergyShapes;
import com.lanterna.client.render.LanternaRenderTypes;
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
import org.joml.Vector3f;

/**
 * Desenha os construtos desenhados pelo jogador, com as animações de cada comportamento:
 *  montar     um fio de luz sai da mão do dono até o construto; a peça aparece da base para cima com uma faixa
 *             de luz passando na frente, cresce com um pulo e solta um clarão;
 *  arma       segura inclinada; no golpe, varre da direita para a esquerda deixando cópias de luz e um leque;
 *  escudo     na frente, acompanhando o olhar, respirando de leve;
 *  disparo    gira no próprio eixo voando, com cópias de luz ficando para trás;
 *  queda      de cabeça para baixo, com rastro de velocidade; no impacto achata e solta uma onda no chão;
 *  veículo    inclina nas curvas e acelerações, com o brilho dos propulsores atrás;
 *  estrutura  sobe do chão;
 *  desmontar  uma faixa de luz apaga a peça de fora para dentro e ela estilhaça em faíscas.
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

    /** Golpe em 3 tempos: prepara (puxa para a direita), varre para a esquerda, volta devagar. {arco, deita} */
    private static float[] golpe(float g) {
        float arco = 0, deita = -35f;
        if (g >= 0 && g < 1.5f) {
            float t = suave(g / 1.5f);
            arco = Mth.lerp(t, 0f, -80f);
            deita = Mth.lerp(t, -35f, -50f);
        } else if (g >= 1.5f && g < 4.5f) {
            float t = suave((g - 1.5f) / 3f);
            arco = Mth.lerp(t, -80f, 72f);
            deita = Mth.lerp(t, -50f, -88f);
        } else if (g >= 4.5f && g < 11f) {
            float t = suave((g - 4.5f) / 6.5f);
            arco = Mth.lerp(t, 72f, 0f);
            deita = Mth.lerp(t, -88f, -35f);
        }
        return new float[]{arco, deita};
    }

    @Override
    public void render(ConstrutoProprio e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        if (e.projeto().vazio()) return;
        float age = e.idade() + pt;
        float mostra = Mth.clamp(age / ConstrutoProprio.MONTAR, 0, 1) * 1.05f;
        float some = 1.05f;
        float alfa = 1f;
        if (age > e.vida()) {
            float d = Mth.clamp((age - e.vida()) / ConstrutoProprio.DESMONTAR, 0, 1);
            some = 1.05f - d * 1.1f;
            alfa = 1 - d * 0.5f;
        }
        float k = e.projeto().bloco();
        float[] cx = e.caixaLocal();
        float altura = cx[4] - cx[1];
        int cor = e.cor();
        Malha malha = Malha.de(e.projeto());
        Vec3 origem = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        LivingEntity dono = e.dono();
        EnergyShapes.tempo = age;
        Matrix4f base = new Matrix4f(ps.last().pose());
        float pop = e.idade() < ConstrutoProprio.MONTAR ? EnergyShapes.entrada(age, ConstrutoProprio.MONTAR) : 1f;

        ps.pushPose();
        switch (e.comp()) {
            case ARMA -> {
                if (dono == null) break;
                Vec3 m = ConstrutoProprio.mao(dono, pt);
                ps.translate(m.x - origem.x, m.y - origem.y, m.z - origem.z);
                float yawD = Mth.rotLerp(pt, dono.yHeadRotO, dono.yHeadRot);
                float pitch = Mth.lerp(pt, dono.xRotO, dono.getXRot());
                ps.mulPose(Axis.YP.rotationDegrees(180 - yawD));
                float g = age - e.golpe();
                // cópias de luz ficando para trás na varrida
                if (g >= 1.5f && g < 5.2f) {
                    for (int i = 1; i <= 4; i++) {
                        float gi = g - i * 0.28f;
                        if (gi < 1.5f) break;
                        float[] p = golpe(gi);
                        ps.pushPose();
                        ps.mulPose(Axis.YP.rotationDegrees(p[0]));
                        ps.mulPose(Axis.XP.rotationDegrees(-pitch * 0.6f + p[1]));
                        malha.fantasma(ps, buf, cor, 0.32f * (1 - i / 5f), k, age);
                        ps.popPose();
                    }
                }
                float[] p = golpe(g);
                if (g >= 1.5f && g < 4.7f) rastro(ps, buf, cor, altura, g - 1.5f, suave((g - 1.5f) / 3f));
                ps.mulPose(Axis.YP.rotationDegrees(p[0]));
                ps.mulPose(Axis.XP.rotationDegrees(-pitch * 0.6f + p[1]));
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
                    float pulso = 1 + 0.02f * Mth.sin(age * 0.25f);
                    ps.scale(pulso, pulso, pulso);
                    ps.translate(0, -altura / 2, 0);
                }
            }
            case PROJETIL -> {
                Vec3 v = e.getDeltaMovement();
                for (int i = 1; i <= 4 && age > ConstrutoProprio.MONTAR; i++) {
                    ps.pushPose();
                    ps.translate(-v.x * i * 0.4, -v.y * i * 0.4, -v.z * i * 0.4);
                    projetilPose(ps, e, altura, age - i * 0.4f);
                    malha.fantasma(ps, buf, cor, 0.3f * (1 - i / 5f), k, age);
                    ps.popPose();
                }
                projetilPose(ps, e, altura, age);
            }
            case QUEDA -> {
                boolean noChao = e.getY() <= e.alvoY() + 0.01;
                if (!noChao && age > ConstrutoProprio.MONTAR) {
                    // rastro de velocidade: cópias acima, esticadas
                    float vy = (float) -e.getDeltaMovement().y;
                    for (int i = 1; i <= 4 && vy > 0.15f; i++) {
                        ps.pushPose();
                        ps.translate(0, vy * i * 0.55f, 0);
                        quedaPose(ps, e, altura, age, false);
                        ps.scale(1, 1 + vy * 0.25f, 1);
                        malha.fantasma(ps, buf, cor, 0.26f * (1 - i / 5f), k, age);
                        ps.popPose();
                    }
                }
                if (noChao && e.pousou >= 0) onda(ps, buf.getBuffer(LanternaRenderTypes.BRILHO), cor, age - e.pousou, Math.max(2.5f, (cx[3] - cx[0]) * 1.6f));
                quedaPose(ps, e, altura, age, noChao);
            }
            case VEICULO -> {
                float yawV = Mth.rotLerp(pt, e.yRotO, e.getYRot());
                ps.mulPose(Axis.YP.rotationDegrees(180 - yawV));
                Vec3 v = e.getDeltaMovement();
                float vira = Mth.wrapDegrees(e.getYRot() - e.yRotO);
                ps.mulPose(Axis.ZP.rotationDegrees(Mth.clamp(vira * 1.5f, -12, 12)));
                ps.mulPose(Axis.XP.rotationDegrees(Mth.clamp((float) v.y * -30f, -10, 10)));
                ps.translate(0, Mth.sin(age * 0.2f) * 0.03f, 0);
                // propulsores: dois brilhos atrás, maiores com a velocidade
                float vel = (float) Math.sqrt(v.x * v.x + v.z * v.z);
                float tam = (0.18f + Math.min(0.5f, vel * 0.9f)) * (0.9f + 0.1f * Mth.sin(age * 1.7f)) * Math.min(1, pop);
                float yb = cx[1] + (cx[4] - cx[1]) * 0.3f, zb = cx[5] + 0.06f;
                for (float s : new float[]{-1, 1}) {
                    Vector3f p = ps.last().pose().transformPosition(new Vector3f(s * (cx[3] - cx[0]) * 0.28f, yb, zb));
                    bola(buf.getBuffer(LanternaRenderTypes.BRILHO), p, tam, Malha.vivo(EnergyShapes.brilho(cor)), 0.85f * alfa);
                }
            }
            case ESTRUTURA -> {
                ps.mulPose(Axis.YP.rotationDegrees(180 - e.getYRot()));
                float sobe = suave(age / 12f);
                ps.translate(0, -(1 - sobe) * altura, 0);
            }
        }
        ps.scale(pop, pop, pop);
        malha.desenhar(ps, buf, cor, alfa, k, mostra, some, age);
        Vector3f centro = ps.last().pose().transformPosition(new Vector3f(0, altura / 2, 0));
        ps.popPose();

        // fio de luz da mão do dono até a peça enquanto ela se monta
        float fim = ConstrutoProprio.MONTAR + 4;
        if (dono != null && age < fim && e.comp() != com.lanterna.construct.oficina.Comportamento.ARMA) {
            Vec3 m = ConstrutoProprio.mao(dono, pt).subtract(origem);
            Vector3f mao = base.transformPosition(new Vector3f((float) m.x, (float) m.y, (float) m.z));
            fio(buf.getBuffer(LanternaRenderTypes.BRILHO), mao, centro, Malha.vivo(EnergyShapes.brilho(cor)), 1 - age / fim, age);
        }
        // clarão de quando monta
        if (age < ConstrutoProprio.MONTAR + 4) {
            float a = 1 - Mth.clamp(age / (ConstrutoProprio.MONTAR + 4), 0, 1);
            bola(buf.getBuffer(LanternaRenderTypes.BRILHO), centro, Math.max(0.45f, altura * 0.42f) * (0.5f + 0.5f * a), Malha.vivo(EnergyShapes.brilho(cor)), 0.55f * a);
        }
        super.render(e, yaw, pt, ps, buf, light);
    }

    private static void projetilPose(PoseStack ps, ConstrutoProprio e, float altura, float age) {
        ps.mulPose(Axis.YP.rotationDegrees(180 - e.getYRot()));
        ps.mulPose(Axis.XP.rotationDegrees(-e.getXRot()));
        ps.translate(0, altura / 2, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(age * 24));
        ps.translate(0, -altura / 2, 0);
    }

    private static void quedaPose(PoseStack ps, ConstrutoProprio e, float altura, float age, boolean noChao) {
        ps.mulPose(Axis.YP.rotationDegrees(180 - e.getYRot()));
        ps.translate(0, altura, 0);
        // de ponta-cabeça: o topo do desenho é o que bate
        ps.mulPose(Axis.XP.rotationDegrees(180));
        if (!noChao) {
            ps.translate(0, altura / 2, 0);
            ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin(age * 0.35f) * 6));
            ps.mulPose(Axis.XP.rotationDegrees(Mth.cos(age * 0.3f) * 5));
            ps.translate(0, -altura / 2, 0);
        } else {
            float t = e.pousou >= 0 ? age - e.pousou : 99;
            // achata no impacto e volta com um quique
            float sq = 1 - 0.22f * (float) Math.exp(-t * 0.45f) * Mth.cos(t * 0.9f);
            ps.translate(0, altura, 0);
            ps.scale(1 / Mth.sqrt(sq), sq, 1 / Mth.sqrt(sq));
            ps.translate(0, -altura, 0);
        }
    }

    /** Onda de choque no chão depois do impacto: anel que abre e se apaga. */
    private static void onda(PoseStack ps, VertexConsumer vc, int cor, float t, float raioMax) {
        if (t < 0 || t > 14) return;
        float k = t / 14f;
        float r = raioMax * (1 - (1 - k) * (1 - k));
        float w = 0.25f + 0.5f * (1 - k);
        float a = (1 - k) * 0.9f;
        int c = Malha.vivo(EnergyShapes.brilho(cor));
        float cr = ((c >> 16) & 255) / 255f, cg = ((c >> 8) & 255) / 255f, cb = (c & 255) / 255f;
        Matrix4f m = ps.last().pose();
        int seg = 40;
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            float ri = Math.max(0, r - w);
            v(vc, m, Mth.cos(a0) * ri, 0.06f, Mth.sin(a0) * ri, cr, cg, cb, 0);
            v(vc, m, Mth.cos(a0) * r, 0.06f, Mth.sin(a0) * r, Math.min(1, cr + 0.4f), Math.min(1, cg + 0.4f), Math.min(1, cb + 0.4f), a);
            v(vc, m, Mth.cos(a1) * r, 0.06f, Mth.sin(a1) * r, Math.min(1, cr + 0.4f), Math.min(1, cg + 0.4f), Math.min(1, cb + 0.4f), a);
            v(vc, m, Mth.cos(a1) * ri, 0.06f, Mth.sin(a1) * ri, cr, cg, cb, 0);
            float ro = r + w * 0.4f;
            v(vc, m, Mth.cos(a0) * r, 0.06f, Mth.sin(a0) * r, cr, cg, cb, a * 0.6f);
            v(vc, m, Mth.cos(a0) * ro, 0.06f, Mth.sin(a0) * ro, cr, cg, cb, 0);
            v(vc, m, Mth.cos(a1) * ro, 0.06f, Mth.sin(a1) * ro, cr, cg, cb, 0);
            v(vc, m, Mth.cos(a1) * r, 0.06f, Mth.sin(a1) * r, cr, cg, cb, a * 0.6f);
        }
    }

    /** Rastro de luz da ponta da arma durante o golpe (um leque translúcido). */
    private static void rastro(PoseStack ps, MultiBufferSource buf, int cor, float altura, float g, float ida) {
        if (g > 3.2f) return;
        VertexConsumer vc = buf.getBuffer(LanternaRenderTypes.BRILHO);
        int c = Malha.vivo(EnergyShapes.brilho(cor));
        float r = ((c >> 16) & 255) / 255f, gg = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        float alfa = 0.55f * (1 - g / 3.2f);
        Matrix4f m = ps.last().pose();
        float ate = Mth.lerp(ida, -80f, 72f), de = Math.max(-80f, ate - 85f);
        int n = 14;
        float raioFora = altura * 1.02f, raioDentro = altura * 0.3f;
        for (int i = 0; i < n; i++) {
            float a0 = (float) Math.toRadians(Mth.lerp(i / (float) n, de, ate)), a1 = (float) Math.toRadians(Mth.lerp((i + 1) / (float) n, de, ate));
            float f0 = alfa * (i / (float) n), f1 = alfa * ((i + 1) / (float) n);
            f0 *= f0 / alfa; f1 *= f1 / alfa;
            v(vc, m, Mth.sin(a0) * raioDentro, 0.1f, -Mth.cos(a0) * raioDentro, r, gg, b, 0);
            v(vc, m, Mth.sin(a0) * raioFora, 0.1f, -Mth.cos(a0) * raioFora, Math.min(1, r + 0.3f), Math.min(1, gg + 0.3f), Math.min(1, b + 0.3f), f0);
            v(vc, m, Mth.sin(a1) * raioFora, 0.1f, -Mth.cos(a1) * raioFora, Math.min(1, r + 0.3f), Math.min(1, gg + 0.3f), Math.min(1, b + 0.3f), f1);
            v(vc, m, Mth.sin(a1) * raioDentro, 0.1f, -Mth.cos(a1) * raioDentro, r, gg, b, 0);
        }
    }

    /** Brilho redondo virado para a câmera, desenhado direto no espaço da câmera. */
    private static void bola(VertexConsumer vc, Vector3f p, float tam, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        int seg = 18;
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            raw(vc, p.x, p.y, p.z, Math.min(1, r + 0.35f), Math.min(1, g + 0.35f), Math.min(1, b + 0.35f), alpha);
            raw(vc, p.x, p.y, p.z, Math.min(1, r + 0.35f), Math.min(1, g + 0.35f), Math.min(1, b + 0.35f), alpha);
            raw(vc, p.x + Mth.cos(a1) * tam, p.y + Mth.sin(a1) * tam, p.z, r, g, b, 0);
            raw(vc, p.x + Mth.cos(a0) * tam, p.y + Mth.sin(a0) * tam, p.z, r, g, b, 0);
        }
    }

    /** Fio de luz ondulando entre dois pontos (espaço da câmera), sempre virado para o olho. */
    private static void fio(VertexConsumer vc, Vector3f a, Vector3f b, int rgb, float forca, float tempo) {
        if (forca <= 0.01f) return;
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, bl = (rgb & 255) / 255f;
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        if (len < 0.05f) return;
        d.div(len);
        int n = 16;
        Vector3f ant = null, antL = null;
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            float onda = Mth.sin(t * 9f - tempo * 0.9f) * 0.06f * Mth.sin(t * Mth.PI);
            Vector3f p = new Vector3f(a).lerp(b, t);
            p.y += Mth.sin(t * Mth.PI) * len * 0.12f + onda;
            Vector3f lado = new Vector3f(d).cross(p).normalize().mul(0.035f + 0.02f * Mth.sin(t * Mth.PI));
            if (ant != null) {
                float al = forca * (0.35f + 0.65f * Mth.sin(t * Mth.PI));
                raw(vc, ant.x - antL.x, ant.y - antL.y, ant.z - antL.z, r, g, bl, 0);
                raw(vc, p.x - lado.x, p.y - lado.y, p.z - lado.z, r, g, bl, 0);
                raw(vc, p.x, p.y, p.z, 1, 1, 1, al);
                raw(vc, ant.x, ant.y, ant.z, 1, 1, 1, al);
                raw(vc, ant.x, ant.y, ant.z, 1, 1, 1, al);
                raw(vc, p.x, p.y, p.z, 1, 1, 1, al);
                raw(vc, p.x + lado.x, p.y + lado.y, p.z + lado.z, r, g, bl, 0);
                raw(vc, ant.x + antL.x, ant.y + antL.y, ant.z + antL.z, r, g, bl, 0);
            }
            ant = p;
            antL = lado;
        }
    }

    private static void raw(VertexConsumer vc, float x, float y, float z, float r, float g, float b, float a) {
        vc.vertex(x, y, z).color(r, g, b, a).uv(0.5f, 0.5f).endVertex();
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
        vc.vertex(m, x, y, z).color(r, g, b, a).uv(x * 0.5f, z * 0.5f).endVertex();
    }
}
