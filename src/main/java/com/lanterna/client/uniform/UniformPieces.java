package com.lanterna.client.uniform;

import com.lanterna.LanternaMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;

import java.util.HashSet;
import java.util.Set;

/**
 * Peças 3D do uniforme (feitas no Blockbench, formato "Java Block/Item"), uma por parte do corpo:
 *
 *   assets/lanterna/models/uniform/<tropa>/<parte>.json     só para essa Tropa
 *   assets/lanterna/models/uniform/all/<parte>.json         para todas as Tropas (se a Tropa não tiver a sua)
 *
 *   <parte> = head, body, right_arm, left_arm, right_leg, left_leg
 *
 * Cada peça acompanha a parte do corpo (anda, vira, voa junto) e aparece com a transformação.
 * Faces marcadas com "Tint" no Blockbench são pintadas na cor da Tropa.
 *
 * POSIÇÃO no Blockbench (1 unidade = 1 pixel do jogador, personagem olhando para o NORTE):
 *   head       a cabeça ocupa  (4,0,4)  até (12,8,12)   -> o pescoço é o ponto (8,0,8)
 *   body       o tronco ocupa  (4,4,6)  até (12,16,10)  -> o pescoço é o ponto (8,16,8)
 *   right_arm  o braço ocupa   (7,2,6)  até (11,14,10)  -> lado de fora em X=11 (leste), ombro em (8,12,8)
 *   left_arm   o braço ocupa   (5,2,6)  até (9,14,10)   -> lado de fora em X=5 (oeste), ombro em (8,12,8)
 *   right_leg  a perna ocupa   (6,0,6)  até (10,12,10)  -> o quadril é o ponto (8,12,8)
 *   left_leg   a perna ocupa   (6,0,6)  até (10,12,10)  -> o quadril é o ponto (8,12,8)
 * Os modelos-guia em "blockbench_templates/" já têm essa caixa de referência.
 */
public final class UniformPieces {
    public static final String[] PARTS = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};

    /** Altura do ponto de encaixe (em pixels) de cada parte no modelo do Blockbench. */
    private static float jointY(String part) {
        return switch (part) {
            case "head" -> 0f;
            case "body" -> 16f;
            default -> 12f;
        };
    }

    private static final Set<ResourceLocation> AVAILABLE = new HashSet<>();

    /** Registra todos os modelos de peça que existirem nos recursos (mod ou resource pack). */
    public static void register(ModelEvent.RegisterAdditional event) {
        AVAILABLE.clear();
        var resources = Minecraft.getInstance().getResourceManager()
                .listResources("models/uniform", path -> path.getPath().endsWith(".json"));
        for (ResourceLocation file : resources.keySet()) {
            if (!file.getNamespace().equals(LanternaMod.MODID)) continue;
            String path = file.getPath().substring("models/".length(), file.getPath().length() - ".json".length());
            ResourceLocation model = new ResourceLocation(file.getNamespace(), path);
            event.register(model);
            AVAILABLE.add(model);
        }
        if (!AVAILABLE.isEmpty()) LanternaMod.LOGGER.info("Lanterna: {} peças 3D de uniforme encontradas", AVAILABLE.size());
    }

    private static ResourceLocation find(String corps, String part) {
        ResourceLocation own = LanternaMod.id("uniform/" + corps + "/" + part);
        if (AVAILABLE.contains(own)) return own;
        ResourceLocation all = LanternaMod.id("uniform/all/" + part);
        return AVAILABLE.contains(all) ? all : null;
    }

    public static boolean hasAny() {
        return !AVAILABLE.isEmpty();
    }

    /** Desenha as peças 3D presas às partes do corpo (já com a escala da transformação aplicada). */
    public static void render(PlayerModel<?> model, String corps, int color, PoseStack poseStack,
                              MultiBufferSource buffer, int light) {
        if (AVAILABLE.isEmpty()) return;
        ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;
        var vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));

        for (int i = 0; i < PARTS.length; i++) {
            ModelPart part = parts[i];
            if (!part.visible) continue;
            ResourceLocation id = find(corps, PARTS[i]);
            if (id == null) continue;
            BakedModel baked = Minecraft.getInstance().getModelManager().getModel(id);

            poseStack.pushPose();
            part.translateAndRotate(poseStack);
            // Blockbench (Y para cima, frente no norte) -> parte do corpo do jogador (Y para baixo)
            poseStack.scale(-1f, -1f, 1f);
            poseStack.translate(-0.5f, -jointY(PARTS[i]) / 16f, -0.5f);
            Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(poseStack.last(), vc, null, baked,
                    r, g, b, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout());
            poseStack.popPose();
        }
    }

    private UniformPieces() {}
}
