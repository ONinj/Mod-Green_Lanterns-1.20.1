package com.lanterna.client.necron;

import com.lanterna.LanternaMod;
import com.lanterna.necron.NecronEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 0.58.3: o Necron chefe, com as mesmas animações da Forma do Necron e das cutscenes (PoseNecron): parado, andando,
 * voando, o golpe da foice (quando ele ataca) e a cabeça seguindo quem ele olha.
 */
public class NecronRenderer extends GeoEntityRenderer<NecronEntity> {
    public NecronRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(LanternaMod.id("necron"), false) {
            @Override
            public void setCustomAnimations(NecronEntity n, long id, AnimationState<NecronEntity> st) {
                float pt = st.getPartialTick();
                float t = n.tickCount + pt;
                float anda = PoseNecron.andar(n, pt);
                // rápido (os saltos da batalha) é voo; devagar é deslizar
                float voo = Mth.clamp((anda - 0.6f) * 2.5f, 0, 1);
                PoseNecron.aplicar(this::getBone, PoseNecron.estado(n, pt, voo, -1, 1));
                var dados = st.getData(DataTickets.ENTITY_MODEL_DATA);
                if (dados != null) getBone("cabeca").ifPresent(b -> {
                    b.setRotX(b.getRotX() - dados.headPitch() * Mth.DEG_TO_RAD * 0.6f);
                    b.setRotY(b.getRotY() - dados.netHeadYaw() * Mth.DEG_TO_RAD * 0.6f);
                });
            }
        });
        withScale(1.6f);
        shadowRadius = 0.8f;
    }
}
