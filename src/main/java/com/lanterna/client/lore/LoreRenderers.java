package com.lanterna.client.lore;

import com.lanterna.client.construct.ConstructRenderer;
import com.lanterna.construct.ConstructRegistry;
import com.lanterna.lore.LoreEntities;
import com.lanterna.lore.entity.Race;
import net.minecraftforge.client.event.EntityRenderersEvent;

/** Liga cada entidade da lore ao seu modelo 3D (nomes iguais aos arquivos em geo/entity). */
public final class LoreRenderers {

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        for (Race r : Race.values()) {
            event.registerEntityRenderer(LoreEntities.typeOf(r), ctx -> new LoreRenderer<>(ctx, r.id, 1.0f, r.translucent));
        }
        event.registerEntityRenderer(LoreEntities.ECO_CINZENTO.get(), ctx -> new LoreRenderer<>(ctx, "eco_cinzento", 1.0f, true));
        event.registerEntityRenderer(LoreEntities.BROTO.get(), ctx -> new LoreRenderer<>(ctx, "broto", 1.0f, false));
        event.registerEntityRenderer(LoreEntities.ALVO_TREINO.get(), ctx -> new LoreRenderer<>(ctx, "alvo_treino", 1.0f, false));
        event.registerEntityRenderer(LoreEntities.VORGATH.get(), ctx -> new LoreRenderer<>(ctx, "vorgath", 2.0f, false));
        event.registerEntityRenderer(LoreEntities.MAMMOK.get(), ctx -> new LoreRenderer<>(ctx, "mammok", 1.6f, false));
        event.registerEntityRenderer(LoreEntities.THESSARA.get(), ctx -> new LoreRenderer<>(ctx, "thessara", 1.5f, true));
        event.registerEntityRenderer(LoreEntities.VIGIA_PARTIDO.get(), ctx -> new LoreRenderer<>(ctx, "vigia_partido", 1.5f, true));
        event.registerEntityRenderer(LoreEntities.MORROW.get(), ctx -> new LoreRenderer<>(ctx, "morrow", 1.6f, true));
        event.registerEntityRenderer(LoreEntities.PENITENTE.get(), ctx -> new LoreRenderer<>(ctx, "penitente", 1.5f, false));
        event.registerEntityRenderer(LoreEntities.NYRIELLE.get(), ctx -> new LoreRenderer<>(ctx, "nyrielle", 1.4f, true));
        event.registerEntityRenderer(LoreEntities.DEVORADOR_DE_ECOS.get(), ctx -> new LoreRenderer<>(ctx, "devorador_de_ecos", 2.0f, true));
        event.registerEntityRenderer(LoreEntities.NULO.get(), ctx -> new LoreRenderer<>(ctx, "nulo", 1.0f, true));
        event.registerEntityRenderer(ConstructRegistry.CONSTRUCT.get(), ConstructRenderer::new);
    }

    private LoreRenderers() {}
}
