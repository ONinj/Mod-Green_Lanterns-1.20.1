package com.lanterna.client.uniform;

import com.lanterna.client.ClientVisibleRings;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Desenha o uniforme da Tropa por cima da skin, um pouquinho maior que o corpo.
 *
 * Transformação (estilo nanotecnologia): cada parte do corpo "cresce" coberta pelo uniforme,
 * nesta ordem: braço do anel (da mão para o ombro) -> peito -> cabeça e o outro braço -> pernas.
 * A parte que está sendo coberta brilha na cor da Tropa.
 */
public class UniformLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final PlayerModel<AbstractClientPlayer> wide;
    private final PlayerModel<AbstractClientPlayer> slim;

    public UniformLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                        PlayerModel<AbstractClientPlayer> wide, PlayerModel<AbstractClientPlayer> slim) {
        super(parent);
        this.wide = wide;
        this.slim = slim;
    }

    private record Stage(float start, float end) {
        float local(float p) { return Mth.clamp((p - start) / (end - start), 0f, 1f); }
    }

    private static final Stage MAIN_ARM = new Stage(0.00f, 0.35f);
    private static final Stage BODY = new Stage(0.22f, 0.55f);
    private static final Stage HEAD = new Stage(0.42f, 0.72f);
    private static final Stage OTHER_ARM = new Stage(0.45f, 0.78f);
    private static final Stage LEGS = new Stage(0.62f, 1.00f);

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        float p = UniformTransition.progress(player, partialTick);
        if (p <= 0f || player.isInvisible()) return;

        PlayerModel<AbstractClientPlayer> model = "slim".equals(player.getModelName()) ? slim : wide;
        getParentModel().copyPropertiesTo(model);
        model.hat.visible = false;
        model.jacket.visible = false;
        model.leftSleeve.visible = false;
        model.rightSleeve.visible = false;
        model.leftPants.visible = false;
        model.rightPants.visible = false;

        boolean rightMain = player.getMainArm() == HumanoidArm.RIGHT;
        ModelPart mainArm = rightMain ? model.rightArm : model.leftArm;
        ModelPart otherArm = rightMain ? model.leftArm : model.rightArm;

        float qMain = MAIN_ARM.local(p), qBody = BODY.local(p), qHead = HEAD.local(p);
        float qOther = OTHER_ARM.local(p), qLegs = LEGS.local(p);

        // Braço do anel: cresce a partir da MÃO (o braço vai de -2 a 10 a partir do ombro)
        grow(mainArm, qMain, 12f, 10f);
        grow(model.body, qBody, 12f, 0f);
        grow(model.head, qHead, 8f, 0f);
        grow(otherArm, qOther, 12f, 0f);
        grow(model.rightLeg, qLegs, 12f, 0f);
        grow(model.leftLeg, qLegs, 12f, 0f);

        ResourceLocation texture = UniformTextures.get(UniformTransition.corpsOf(player));
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(texture));
        model.renderToBuffer(poseStack, vc, light, LivingEntityRenderer.getOverlayCoords(player, 0f), 1f, 1f, 1f, 1f);

        // Brilho da nanotecnologia nas partes que estão se formando
        if (p < 1f) {
            int color = ClientVisibleRings.of(player.getId()).color();
            float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;
            glowOnly(model, mainArm, otherArm, qMain, qBody, qHead, qOther, qLegs);
            float alpha = 0.55f + 0.25f * Mth.sin(ageInTicks * 1.3f);
            VertexConsumer glow = buffer.getBuffer(RenderType.entityTranslucentEmissive(texture));
            model.renderToBuffer(poseStack, glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, r, g, b, alpha);
        }

        resetScale(model);
    }

    /**
     * Mostra só a fração q da parte. anchor = de onde ela cresce (em pixels a partir do pivô):
     * 0 = do pivô (ombro, quadril, pescoço), 10 = da ponta da mão.
     */
    private static void grow(ModelPart part, float q, float length, float anchor) {
        part.visible = q > 0f;
        part.yScale = Math.max(q, 0.001f);
        if (anchor != 0f) part.y += anchor * (1f - q);
    }

    /** Na passada de brilho, só aparecem as partes que ainda estão se formando. */
    private static void glowOnly(PlayerModel<?> model, ModelPart mainArm, ModelPart otherArm,
                                 float qMain, float qBody, float qHead, float qOther, float qLegs) {
        mainArm.visible = qMain > 0f && qMain < 1f;
        model.body.visible = qBody > 0f && qBody < 1f;
        model.head.visible = qHead > 0f && qHead < 1f;
        otherArm.visible = qOther > 0f && qOther < 1f;
        model.rightLeg.visible = qLegs > 0f && qLegs < 1f;
        model.leftLeg.visible = qLegs > 0f && qLegs < 1f;
    }

    private static void resetScale(PlayerModel<?> model) {
        for (ModelPart part : new ModelPart[]{model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg}) {
            part.xScale = 1f;
            part.yScale = 1f;
            part.zScale = 1f;
            part.visible = true;
        }
    }
}
