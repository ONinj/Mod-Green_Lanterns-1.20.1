package com.lanterna.client.uniform;

import com.lanterna.LanternaMod;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

/** Modelos do uniforme: o corpo do jogador um pouco maior (para ficar por cima da skin). */
public final class UniformModels {
    public static final ModelLayerLocation WIDE = new ModelLayerLocation(LanternaMod.id("uniform_wide"), "main");
    public static final ModelLayerLocation SLIM = new ModelLayerLocation(LanternaMod.id("uniform_slim"), "main");

    /** 0.3 pixel a mais: cobre inclusive a segunda camada da skin (que tem 0.25). */
    private static final CubeDeformation INFLATE = new CubeDeformation(0.3f);

    public static LayerDefinition wide() {
        return LayerDefinition.create(PlayerModel.createMesh(INFLATE, false), 64, 64);
    }

    public static LayerDefinition slim() {
        return LayerDefinition.create(PlayerModel.createMesh(INFLATE, true), 64, 64);
    }

    private UniformModels() {}
}
