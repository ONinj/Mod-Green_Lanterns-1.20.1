package com.lanterna.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Tipos de desenho próprios do mod. */
public final class LanternaRenderTypes extends RenderType {

    /**
     * Energia translúcida na cor da Tropa (fica verde mesmo contra o céu claro), sem esconder
     * o que está atrás, visível dos dois lados. Usado na cúpula, no tubo e no cone da barreira.
     */
    public static final RenderType ENERGY = create("lanterna_energy", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 65536, false, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .createCompositeState(false));

    /** Brilho somado (riscos de luz): clareia o que está atrás. */
    public static final RenderType GLOW = create("lanterna_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 65536, false, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .createCompositeState(false));

    /** Energia dos construtos com textura (favos e veios de luz que correm), tingida pela cor do vértice. */
    public static final RenderType ENERGIA = create("lanterna_energia", DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS, 262144, false, true,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_TEX_SHADER)
                    .setTextureState(new TextureStateShard(com.lanterna.LanternaMod.id("textures/misc/energia.png"), true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .createCompositeState(false));

    /** Brilho somado com textura de veios (contornos, clarões, halos). */
    public static final RenderType BRILHO = create("lanterna_brilho", DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS, 262144, false, true,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_TEX_SHADER)
                    .setTextureState(new TextureStateShard(com.lanterna.LanternaMod.id("textures/misc/brilho.png"), true, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .createCompositeState(false));

    /** Luz colorida da aura no chão e nas paredes: cor por vértice, somada (sem textura), por cima dos blocos. */
    public static final RenderType LUZ = create("lanterna_luz_aura", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 65536, false, true,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .createCompositeState(false));

    private LanternaRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                                boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }
}
