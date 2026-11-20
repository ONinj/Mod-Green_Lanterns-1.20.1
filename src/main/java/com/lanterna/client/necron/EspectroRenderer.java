package com.lanterna.client.necron;

import com.lanterna.LanternaMod;
import com.lanterna.necron.EspectroEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 0.57.0: o Espectro é o próprio jogador: a pele dele (a mesma do jogo, larga ou fina), por cima o traje negro da
 * Morte com o brilho, o Anel da Morte na mão e almas saindo do corpo.
 */
public class EspectroRenderer extends LivingEntityRenderer<EspectroEntity, PlayerModel<EspectroEntity>> {
    private final PlayerModel<EspectroEntity> largo, fino;

    public EspectroRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        largo = model;
        fino = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
        addLayer(new Traje(this));
    }

    @Nullable
    private static PlayerInfo info(EspectroEntity e) {
        UUID u = e.dono();
        var c = Minecraft.getInstance().getConnection();
        return u == null || c == null ? null : c.getPlayerInfo(u);
    }

    static boolean fino(EspectroEntity e) {
        PlayerInfo i = info(e);
        if (i != null) return "slim".equals(i.getModelName());
        return e.dono() != null && "slim".equals(DefaultPlayerSkin.getSkinModelName(e.dono()));
    }

    @Override
    public ResourceLocation getTextureLocation(EspectroEntity e) {
        PlayerInfo i = info(e);
        if (i != null) return i.getSkinLocation();
        return e.dono() == null ? DefaultPlayerSkin.getDefaultSkin() : DefaultPlayerSkin.getDefaultSkin(e.dono());
    }

    @Override
    public void render(EspectroEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
        model = fino(e) ? fino : largo;
        super.render(e, yaw, pt, ps, buf, luz);
    }

    @Override
    protected boolean shouldShowName(EspectroEntity e) {
        return e.hasCustomName() && Minecraft.getInstance().player != null && Minecraft.getInstance().player.distanceToSqr(e) < 24 * 24;
    }

    /** O traje negro da Morte por cima da pele, com o brilho. */
    private static final class Traje extends RenderLayer<EspectroEntity, PlayerModel<EspectroEntity>> {
        Traje(RenderLayerParent<EspectroEntity, PlayerModel<EspectroEntity>> pai) { super(pai); }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int luz, EspectroEntity e, float limb, float limbAmt, float pt, float age, float head, float pitch) {
            String arq = "textures/skin_traje/traje_preto_a_" + (fino(e) ? "slim" : "wide");
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(LanternaMod.id(arq + ".png")));
            getParentModel().renderToBuffer(ps, vc, luz, LivingEntityRenderer.getOverlayCoords(e, 0), 1f, 1f, 1f, 1f);
            VertexConsumer g = buf.getBuffer(RenderType.eyes(LanternaMod.id(arq + "_glow.png")));
            getParentModel().renderToBuffer(ps, g, 0xF000F0, OverlayTexture.NO_OVERLAY, 0.8f, 0.6f, 1f, 1f);
        }
    }
}
