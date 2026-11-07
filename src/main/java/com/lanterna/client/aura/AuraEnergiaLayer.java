package com.lanterna.client.aura;

import com.lanterna.LanternaMod;
import com.lanterna.client.flight.AuraOutline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Aura do anel ligado: uma armadura de energia translúcida em volta do corpo, na cor da Tropa. São duas cascas do
 * próprio modelo do jogador (uma colada e outra mais larga e mais fraca), com uma textura de energia suave correndo
 * devagar e pulsando, somadas por cima (brilham, não escondem o personagem). O contorno continua igual.
 */
public class AuraEnergiaLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ModelLayerLocation PERTO = new ModelLayerLocation(LanternaMod.id("aura_energia"), "perto");
    public static final ModelLayerLocation MEIO = new ModelLayerLocation(LanternaMod.id("aura_energia"), "meio");
    public static final ModelLayerLocation LONGE = new ModelLayerLocation(LanternaMod.id("aura_energia"), "longe");
    private static final ResourceLocation TEXTURA = LanternaMod.id("textures/entity/aura_energia.png");

    private final HumanoidModel<AbstractClientPlayer> perto, meio, longe;

    public AuraEnergiaLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> pai, EntityModelSet modelos) {
        super(pai);
        perto = new HumanoidModel<>(modelos.bakeLayer(PERTO));
        meio = new HumanoidModel<>(modelos.bakeLayer(MEIO));
        longe = new HumanoidModel<>(modelos.bakeLayer(LONGE));
    }

    public static LayerDefinition perto() {
        return LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(0.55f), 0), 64, 32);
    }

    public static LayerDefinition meio() {
        return LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(1.25f), 0), 64, 32);
    }

    public static LayerDefinition longe() {
        return LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(2.1f), 0), 64, 32);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int luz, AbstractClientPlayer p, float limb, float limbAmt,
                       float pt, float idade, float cabeca, float pitch) {
        if (p.isInvisible()) return;
        int cor = AuraOutline.color(p);
        if (cor == -1) return;
        float r = ((cor >> 16) & 255) / 255f, g = ((cor >> 8) & 255) / 255f, b = (cor & 255) / 255f;
        float pulso = 0.85f + 0.15f * Mth.sin(idade * 0.12f);
        // três cascas cada vez mais largas e mais fracas: o brilho vai sumindo para fora, sem borda marcada
        casca(perto, ps, buf, p, limb, limbAmt, pt, idade, cabeca, pitch, r, g, b, 0.085f * pulso, 0.004f);
        casca(meio, ps, buf, p, limb, limbAmt, pt, idade, cabeca, pitch, r, g, b, 0.06f * pulso, -0.003f);
        casca(longe, ps, buf, p, limb, limbAmt, pt, idade, cabeca, pitch, r, g, b, 0.04f * pulso, 0.002f);
    }

    private void casca(HumanoidModel<AbstractClientPlayer> m, PoseStack ps, MultiBufferSource buf, AbstractClientPlayer p,
                       float limb, float limbAmt, float pt, float idade, float cabeca, float pitch,
                       float r, float g, float b, float forca, float vel) {
        getParentModel().copyPropertiesTo(m);
        m.prepareMobModel(p, limb, limbAmt, pt);
        m.setupAnim(p, limb, limbAmt, idade, cabeca, pitch);
        // copia de novo a pose final do pai (braços erguidos, voo, poses do mod)
        getParentModel().copyPropertiesTo(m);
        m.hat.visible = false; // a casca do chapéu fazia uma caixa marcada em cima da cabeça
        float u = idade * vel, v = idade * vel * 1.4f;
        VertexConsumer vc = buf.getBuffer(RenderType.energySwirl(TEXTURA, u % 1f, v % 1f));
        // mistura somada: a cor vezes a força é o quanto brilha
        m.renderToBuffer(ps, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, r * forca, g * forca, b * forca, 1f);
    }
}
