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

/**
 * Desenha o uniforme da Tropa por cima da skin, um pouquinho maior que o corpo.
 *
 * Transformação: o uniforme se forma pixel por pixel, subindo dos pés até a cabeça
 * (veja {@link UniformDissolve}). Os pixels recém-formados brilham na cor da Tropa.
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

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        float p = UniformTransition.progress(player, partialTick);
        if (p <= 0f || player.isInvisible()) return;

        // Uniforme 3D da Tropa (GeckoLib). A skin da pasta config/lanterna/uniformes, se existir, tem prioridade.
        String corpsId = UniformTransition.corpsOf(player);
        String tropa = com.lanterna.armor.ModArmorItems.tropaOf(com.lanterna.LanternaMod.id(corpsId));
        String traje = com.lanterna.client.visual.ClientVisual.of(player.getId()).traje();
        if ("nenhum".equals(traje)) return; // escolheu ficar sem uniforme
        if (!UniformTextures.hasCustom(corpsId) && UniformDissolve3D.exists(tropa)) {
            render3D(poseStack, light, player, tropa, p);
            return;
        }

        PlayerModel<AbstractClientPlayer> model = "slim".equals(player.getModelName()) ? slim : wide;
        getParentModel().copyPropertiesTo(model);
        model.hat.visible = false;
        model.jacket.visible = false;
        model.leftSleeve.visible = false;
        model.rightSleeve.visible = false;
        model.leftPants.visible = false;
        model.rightPants.visible = false;

        String corps = UniformTransition.corpsOf(player);
        int color = ClientVisibleRings.of(player.getId()).color();
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0f);
        ResourceLocation texture = UniformTextures.get(corps);
        ResourceLocation[] stage = p < 1f ? UniformDissolve.stage(corps, p) : null;

        // Uniforme: inteiro, ou só os pixels que já se formaram (subindo dos pés até a cabeça)
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(stage != null ? stage[0] : texture));
        model.renderToBuffer(poseStack, vc, light, overlay, 1f, 1f, 1f, 1f);

        // Peças 3D do uniforme (capacete, ombreiras, botas...): aparecem quando a frente passa por elas
        UniformPieces.render(model, corps, color, p, poseStack, buffer, light);

        // Pixels recém-formados brilhando na cor da Tropa
        if (stage != null) {
            float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;
            VertexConsumer glow = buffer.getBuffer(RenderType.entityTranslucentEmissive(stage[1]));
            model.renderToBuffer(poseStack, glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    Math.min(1f, r + 0.35f), Math.min(1f, g + 0.35f), Math.min(1f, b + 0.35f), 1f);
        }

        resetScale(model);
    }

    private static RingUniformRenderer renderer3D;
    private static final java.util.Map<String, net.minecraft.world.item.ItemStack> PIECES = new java.util.HashMap<>();
    private static final net.minecraft.world.entity.EquipmentSlot[] SLOTS = {
            net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.entity.EquipmentSlot.LEGS,
            net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.HEAD};

    /** Veste o uniforme 3D: máscara, peitoral, calça e botas da Tropa (menos onde já há armadura de verdade). */
    private void render3D(PoseStack poseStack, int light, AbstractClientPlayer player, String tropa, float p) {
        if (renderer3D == null) renderer3D = new RingUniformRenderer();
        int color = ClientVisibleRings.of(player.getId()).color();
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0f);
        // Traje escolhido na Personalização: o padrão da Tropa ou um dos trajes "segunda pele" (a, b)
        String var = com.lanterna.client.visual.ClientVisual.of(player.getId()).traje();
        boolean traje = var.equals("a") || var.equals("b");
        ResourceLocation[] stage = p < 1f && !traje ? UniformDissolve3D.stage(tropa, p) : null;
        boolean formando = p < 1f;
        org.joml.Matrix4f layerPose = new org.joml.Matrix4f(poseStack.last().pose());

        for (var slot : SLOTS) {
            if (!player.getItemBySlot(slot).isEmpty()) continue;
            net.minecraft.world.item.ItemStack piece = PIECES.computeIfAbsent(tropa + "|" + var + "|" + slot, k -> {
                var item = traje ? com.lanterna.armor.ModTrajes.peca(tropa, var, slot) : com.lanterna.armor.ModArmorItems.pieceFor(tropa, slot);
                return item == null ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(item);
            });
            if (piece.isEmpty()) continue;
            renderer3D.prepForRender(player, piece, slot, getParentModel());
            if (traje && formando) {
                // traje: forma cubinho por cubinho, subindo dos pés à cabeça
                renderer3D.setSemBase(true);
                renderer3D.setup(RingUniformRenderer.Mode.DETAIL, p, color, null, layerPose);
                renderer3D.renderToBuffer(poseStack, null, light, overlay, 1f, 1f, 1f, 1f);
                renderer3D.setSemBase(false);
            } else if (stage == null) {
                renderer3D.setup(RingUniformRenderer.Mode.NORMAL, 1f, color, null, layerPose);
                renderer3D.renderToBuffer(poseStack, null, light, overlay, 1f, 1f, 1f, 1f);
            } else {
                for (var mode : new RingUniformRenderer.Mode[]{RingUniformRenderer.Mode.BASE,
                        RingUniformRenderer.Mode.DETAIL, RingUniformRenderer.Mode.BAND}) {
                    renderer3D.setup(mode, p, color, stage, layerPose);
                    renderer3D.renderToBuffer(poseStack, null, light, overlay, 1f, 1f, 1f, 1f);
                }
            }
        }
        renderer3D.reset();
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
