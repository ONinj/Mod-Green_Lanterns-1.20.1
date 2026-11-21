package com.lanterna.client.necron;

import com.lanterna.LanternaMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.BiConsumer;

/**
 * Os modelos 3D da cutscene (GeckoLib, sem entidade no mundo): o Embrião da Vida e o Necron. A cena mexe nos ossos
 * pelo código (farrapos da túnica, capa, mandíbula, cordão), então os arquivos de animação ficam vazios.
 */
final class Atores {
    private final Rend embriao = new Rend("embriao_vida", (m, t) -> {
        osso(m, "cabeca", 0.06f * Mth.sin(t * 0.04f), 0.05f * Mth.sin(t * 0.03f), 0);
        for (int i = 1; i <= 8; i++) osso(m, "cordao_" + i, 0.12f * Mth.sin(t * 0.05f + i * 0.7f), 0, 0.1f * Mth.sin(t * 0.04f + i));
    });
    // 0.58.3: as poses do Necron ficam no PoseNecron (as mesmas do chefe e da Forma do Necron)
    private final Rend necron = new Rend("necron", (m, t) -> PoseNecron.aplicar(m::getBone,
            estadoForcado != null ? estadoForcado : new PoseNecron.Estado(t, andar, Math.max(vento, voo), golpe, ajoelhar, vento > 0.5f)));

    static float vento;
    /** 0.58.0: 0 = de pé; 1 = de joelhos (a cena da queda do Necron). */
    static float ajoelhar;
    /** 0.58.3: andando (0..1), voando (0..1) e o golpe da foice (0..1), para a Forma do Necron. */
    static float andar, voo, golpe;
    /** 0.58.4: o estado completo (com transições), quando quem desenha já o calculou. */
    static PoseNecron.Estado estadoForcado;

    void embriao(PoseStack ps, MultiBufferSource buf, float t, float r, float g, float b, float brilho) {
        embriao.cor(r, g, b, 1f, brilho * 0.3f);
        embriao.desenhar(ps, buf, t, LightTexture.FULL_BRIGHT);
    }

    /** escuro: 0 = em plena luz, 1 = quase só a silhueta (as órbitas e a pérola continuam acesas). */
    void necron(PoseStack ps, MultiBufferSource buf, float t, float escuro, float brilho) {
        float k = 1 - 0.75f * escuro;
        vento = 0;
        necron.cor(k, k * 0.96f, k * 1.02f, 1f, brilho);
        necron.desenhar(ps, buf, t, LightTexture.FULL_BRIGHT);
        vento = 0;
    }

    /** Voando pelo espaço (túnica e capa ao vento). */
    void necronVoando(PoseStack ps, MultiBufferSource buf, float t) {
        vento = 1;
        necron.cor(0.8f, 0.78f, 0.85f, 1f, 1f);
        necron.desenhar(ps, buf, t, LightTexture.FULL_BRIGHT);
        vento = 0;
    }

    private static void osso(GeoModel<Ator> m, String nome, float rx, float ry, float rz) {
        m.getBone(nome).ifPresent(b -> {
            b.setRotX(b.getInitialSnapshot().getRotX() + rx);
            b.setRotY(b.getInitialSnapshot().getRotY() + ry);
            b.setRotZ(b.getInitialSnapshot().getRotZ() + rz);
        });
    }

    // ===================== GeckoLib =====================

    static final class Ator implements GeoAnimatable {
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        float tempo;
        @Override public void registerControllers(AnimatableManager.ControllerRegistrar c) {}
        @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
        @Override public double getTick(Object o) { return tempo; }
    }

    static final class Modelo extends GeoModel<Ator> {
        private final ResourceLocation geo, tex, anim;
        private final BiConsumer<Modelo, Float> pose;

        Modelo(String nome, BiConsumer<Modelo, Float> pose) {
            this.geo = LanternaMod.id("geo/entity/" + nome + ".geo.json");
            this.tex = LanternaMod.id("textures/entity/" + nome + ".png");
            this.anim = LanternaMod.id("animations/entity/" + nome + ".animation.json");
            this.pose = pose;
        }

        @Override public ResourceLocation getModelResource(Ator a) { return geo; }
        @Override public ResourceLocation getTextureResource(Ator a) { return tex; }
        @Override public ResourceLocation getAnimationResource(Ator a) { return anim; }

        @Override
        public void setCustomAnimations(Ator a, long id, AnimationState<Ator> st) {
            pose.accept(this, a.tempo);
        }
    }

    private static final class Rend extends GeoObjectRenderer<Ator> {
        private final Ator ator = new Ator();
        private final ResourceLocation brilhoTex;
        private float r = 1, g = 1, b = 1, a = 1, brilho = 1;

        Rend(String nome, BiConsumer<Modelo, Float> pose) {
            super(new Modelo(nome, pose));
            this.brilhoTex = LanternaMod.id("textures/entity/" + nome + "_glowmask.png");
            addRenderLayer(new GeoRenderLayer<>(this) {
                @Override
                public void render(PoseStack ps, Ator an, BakedGeoModel model, RenderType type, MultiBufferSource bufs, VertexConsumer buffer,
                                   float pt, int light, int overlay) {
                    if (brilho <= 0.01f) return;
                    RenderType olhos = RenderType.eyes(brilhoTex);
                    // o reRender centraliza de novo (como um bloco): desfaz para o brilho cair em cima do modelo
                    ps.pushPose();
                    ps.translate(-0.5, -0.51, -0.5);
                    getRenderer().reRender(model, ps, bufs, an, olhos, bufs.getBuffer(olhos), pt, LightTexture.FULL_BRIGHT, overlay,
                            brilho, brilho, brilho, 1f);
                    ps.popPose();
                }
            });
        }

        void cor(float r, float g, float b, float a, float brilho) {
            this.r = r; this.g = g; this.b = b; this.a = a; this.brilho = brilho;
        }

        void desenhar(PoseStack ps, MultiBufferSource buf, float t, int luz) {
            ator.tempo = t;
            ps.pushPose();
            ps.translate(-0.5, -0.51, -0.5);   // o GeoObjectRenderer centraliza como um bloco
            render(ps, ator, buf, null, null, luz);
            ps.popPose();
        }

        @Override
        public RenderType getRenderType(Ator an, ResourceLocation texture, @Nullable MultiBufferSource bufs, float pt) {
            return RenderType.entityCutoutNoCull(texture);
        }

        @Override
        public void actuallyRender(PoseStack ps, Ator an, BakedGeoModel model, RenderType type, MultiBufferSource bufs, VertexConsumer buffer,
                                   boolean reRender, float pt, int light, int overlay, float red, float green, float blue, float alpha) {
            if (!reRender) { red *= r; green *= g; blue *= b; alpha *= a; }
            super.actuallyRender(ps, an, model, type, bufs, buffer, reRender, pt, light, OverlayTexture.NO_OVERLAY, red, green, blue, alpha);
        }
    }
}
