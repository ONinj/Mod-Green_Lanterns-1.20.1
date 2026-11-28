package com.lanterna.client.entidades;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * 0.60.0: a entidade vista ATRAVÉS do vidro da Bateria: brilho translúcido sem teste de profundidade (como uma silhueta
 * de luz presa lá dentro). O desenho normal (com profundidade) vem por cima, para quando ela está à vista.
 */
public final class TipoAtraves extends RenderType {
    private static final Map<ResourceLocation, RenderType> CACHE = new HashMap<>();

    private TipoAtraves(String n, VertexFormat f, VertexFormat.Mode m, int b, boolean c, boolean s, Runnable a, Runnable d) {
        super(n, f, m, b, c, s, a, d);
    }

    public static RenderType de(ResourceLocation tex) {
        return CACHE.computeIfAbsent(tex, t -> create("lanterna_entidade_atraves", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new TextureStateShard(t, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setOverlayState(OVERLAY)
                        .createCompositeState(false)));
    }
}
