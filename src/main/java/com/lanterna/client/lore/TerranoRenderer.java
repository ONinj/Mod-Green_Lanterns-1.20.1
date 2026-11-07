package com.lanterna.client.lore;

import com.lanterna.LanternaMod;
import com.lanterna.lore.entity.RaceEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/** Os humanos da Terra (Vila Aurora, Base Vigília): modelo de jogador com skins próprias por função. */
public class TerranoRenderer extends HumanoidMobRenderer<RaceEntity, PlayerModel<RaceEntity>> {
    private static final ResourceLocation[] MORADORES = new ResourceLocation[4];

    static {
        for (int i = 0; i < 4; i++) MORADORES[i] = LanternaMod.id("textures/entity/terrano/terrano_morador_" + i + ".png");
    }

    public TerranoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(RaceEntity e) {
        String r = e.role();
        if (r.equals("cientista") || r.equals("assistente")) return LanternaMod.id("textures/entity/terrano/terrano_cientista.png");
        if (r.equals("seguranca")) return LanternaMod.id("textures/entity/terrano/terrano_seguranca.png");
        return MORADORES[Math.floorMod(e.getUUID().hashCode(), 4)];
    }
}
