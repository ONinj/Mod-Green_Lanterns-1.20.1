package com.lanterna.client.entidades;

import com.lanterna.LanternaMod;
import com.lanterna.baterias.BateriaNucleoBlockEntity;
import com.lanterna.baterias.Tropa;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * 0.60.3: as entidades vivem DENTRO da Fonte (a dimensão atrás da Bateria), em tamanho real, nadando no céu dela (não
 * aparecem mais em volta da Bateria). 0.60.0: as entidades emocionais presas dentro das Baterias Centrais (Íon, Parallax, o Açougueiro, Ofidiano, Adara,
 * o Prosélito e o Predador). Cada uma nada em círculos dentro do vidro do núcleo, brilhando e meio transparente, com a
 * animação "nadar" em quadros-chave (ferramentas/modelo_entidades.py). Só no cliente; o servidor não sabe delas.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
public final class EntidadesBateria {
    /** Altura do centro do núcleo (blocos acima do bloco "núcleo") e o raio do círculo em que ela nada. */
    static final double ALTURA = 15.6, RAIO = 5.6;
    /** Tamanho de cada uma (o modelo tem ~1/16 de bloco por unidade). */
    static final Map<Tropa, Float> ESCALA = new EnumMap<>(Tropa.class);
    static {
        ESCALA.put(Tropa.VERDE, 1.9f); ESCALA.put(Tropa.AMARELO, 2.6f); ESCALA.put(Tropa.VERMELHO, 3.0f); ESCALA.put(Tropa.LARANJA, 2.5f);
        ESCALA.put(Tropa.AZUL, 3.2f); ESCALA.put(Tropa.INDIGO, 3.2f); ESCALA.put(Tropa.VIOLETA, 2.6f);
    }

    static final class Ator implements GeoAnimatable {
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        float tempo;
        @Override public void registerControllers(AnimatableManager.ControllerRegistrar c) {}
        @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
        @Override public double getTick(Object o) { return tempo; }
    }

    static final class Modelo extends GeoModel<Ator> {
        final String nome;
        final ResourceLocation geo, tex, anim;
        Modelo(String nome) {
            this.nome = nome;
            geo = LanternaMod.id("geo/entity/" + nome + ".geo.json");
            tex = LanternaMod.id("textures/entity/" + nome + ".png");
            anim = LanternaMod.id("animations/entity/" + nome + ".animation.json");
        }
        @Override public ResourceLocation getModelResource(Ator a) { return geo; }
        @Override public ResourceLocation getTextureResource(Ator a) { return tex; }
        @Override public ResourceLocation getAnimationResource(Ator a) { return anim; }

        @Override
        public void setCustomAnimations(Ator a, long id, AnimationState<Ator> st) {
            Map<String, float[]> rot = new HashMap<>(), pos = new HashMap<>();
            Quadros.amostrar(nome, "nadar", a.tempo / 20f, rot, pos, 1);
            for (var en : rot.entrySet()) {
                float[] r = en.getValue();
                getBone(en.getKey()).ifPresent(b -> {
                    b.setRotX(b.getInitialSnapshot().getRotX() + r[0]);
                    b.setRotY(b.getInitialSnapshot().getRotY() + r[1]);
                    b.setRotZ(b.getInitialSnapshot().getRotZ() + r[2]);
                });
            }
        }
    }

    static final class Rend extends GeoObjectRenderer<Ator> {
        final Ator ator = new Ator();
        /** true = o brilho visto através do vidro (sem profundidade). */
        boolean atraves;
        Rend(String nome) { super(new Modelo(nome)); }

        /** Através do vidro: um pouco transparente, para a cor da Tropa continuar aparecendo. */
        @Override
        public software.bernie.geckolib.core.object.Color getRenderColor(Ator an, float pt, int luz) {
            return atraves ? software.bernie.geckolib.core.object.Color.ofRGBA(1f, 1f, 1f, 0.6f) : software.bernie.geckolib.core.object.Color.WHITE;
        }

        @Override
        public RenderType getRenderType(Ator an, ResourceLocation texture, @Nullable MultiBufferSource bufs, float pt) {
            return atraves ? TipoAtraves.de(texture) : RenderType.entityTranslucentEmissive(texture);
        }
    }

    private static final Map<Tropa, Rend> RENDS = new EnumMap<>(Tropa.class);

    /** Chamado pelo desenho da Bateria (em coordenadas do bloco do núcleo). */
    public static void desenhar(BateriaNucleoBlockEntity be, PoseStack ps, MultiBufferSource buf, float pt) {
        if (be.getLevel() == null) return;
        Tropa t = be.getTropa();
        Rend r = RENDS.computeIfAbsent(t, k -> new Rend("entidade_" + k.nome));
        float tempo = be.getLevel().getGameTime() + pt;
        // nada em volta do centro, devagar, subindo e descendo um pouco
        double ang = tempo * 0.012 + t.ordinal();
        double x = Math.cos(ang) * RAIO, z = Math.sin(ang) * RAIO, y = ALTURA + Math.sin(tempo * 0.02) * 0.8;
        float s = ESCALA.getOrDefault(t, 1.8f);
        ps.pushPose();
        ps.translate(0.5 + x, y, 0.5 + z);
        // a frente (-z do modelo) aponta na direção em que ela anda (tangente do círculo)
        ps.mulPose(Axis.YP.rotation((float) (-ang)));
        ps.scale(s, s, s);
        ps.translate(0, -0.6, 0);
        r.ator.tempo = tempo;
        ps.translate(-0.5, -0.51, -0.5);   // o GeoObjectRenderer centraliza como um bloco
        // o brilho através do vidro (só de perto: a até 80 blocos)
        var cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (cam.distanceToSqr(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + ALTURA, be.getBlockPos().getZ() + 0.5) < 80 * 80) {
            r.atraves = true;
            r.render(ps, r.ator, buf, null, null, LightTexture.FULL_BRIGHT);
            r.atraves = false;
        }
        r.render(ps, r.ator, buf, null, null, LightTexture.FULL_BRIGHT);
        ps.popPose();
    }

    // ===================== 0.60.3: em tamanho real, dentro da Fonte (a dimensão da Bateria) =====================

    /** Quanto maior que na vitrine (o modelo inteiro fica com uns 35 a 50 blocos). */
    static final float GIGANTE = 8.5f;

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void naFonte(net.minecraftforge.client.event.RenderLevelStageEvent e) {
        if (e.getStage() != net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;
        String nome = com.lanterna.fonte.Fonte.fonteDe(mc.level);
        if (nome == null) return;
        Tropa t = null;
        for (Tropa x : Tropa.values()) if (x.nome.equals(nome)) t = x;
        if (t == null) return;
        Rend r = RENDS.computeIfAbsent(t, k -> new Rend("entidade_" + k.nome));
        float tempo = mc.level.getGameTime() + e.getPartialTick();
        // nada num círculo grande no céu da Fonte, em volta da plataforma e da ponte da Ilha do Coração
        double ang = tempo * 0.0016;
        double x = Math.cos(ang) * 95, z = 70 + Math.sin(ang) * 95, y = 160 + Math.sin(tempo * 0.004) * 10;
        var cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        var buf = mc.renderBuffers().bufferSource();
        float s = ESCALA.getOrDefault(t, 2f) * GIGANTE;
        ps.pushPose();
        ps.translate(x - cam.x, y - cam.y, z - cam.z);
        ps.mulPose(Axis.YP.rotation((float) (-ang)));
        ps.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(tempo * 0.01) * 6));
        ps.scale(s, s, s);
        r.ator.tempo = tempo;
        ps.translate(-0.5, -0.51, -0.5);
        r.render(ps, r.ator, buf, null, null, LightTexture.FULL_BRIGHT);
        ps.popPose();
        buf.endBatch();
    }

    // ===================== teste: vitrine (uma entidade parada no ar, sem a bateria) =====================

    public static Tropa vitrine;
    public static net.minecraft.world.phys.Vec3 vitrinePos = net.minecraft.world.phys.Vec3.ZERO;
    public static float vitrineGiro;

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void vitrine(net.minecraftforge.client.event.RenderLevelStageEvent e) {
        if (vitrine == null || e.getStage() != net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;
        Rend r = RENDS.computeIfAbsent(vitrine, k -> new Rend("entidade_" + k.nome));
        var cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        var buf = mc.renderBuffers().bufferSource();
        ps.pushPose();
        ps.translate(vitrinePos.x - cam.x, vitrinePos.y - cam.y, vitrinePos.z - cam.z);
        ps.mulPose(Axis.YP.rotationDegrees(vitrineGiro));
        float s = ESCALA.getOrDefault(vitrine, 2f) * 0.75f;
        ps.scale(s, s, s);
        r.ator.tempo = mc.level.getGameTime() + e.getPartialTick();
        ps.translate(-0.5, -0.51, -0.5);
        r.render(ps, r.ator, buf, null, null, LightTexture.FULL_BRIGHT);
        ps.popPose();
        buf.endBatch();
    }

    private EntidadesBateria() {}
}
