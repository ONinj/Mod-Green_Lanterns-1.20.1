package com.lanterna.client.mogo;

import com.lanterna.client.escolha.Geo;
import com.lanterna.mogo.Ferida;
import com.lanterna.mogo.MogoBlocos;
import com.lanterna.mogo.OlhoMogo;
import com.lanterna.mogo.RochaMogo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Desenho das entidades do Mogo: rochas que giram, os olhos de cristal da Prova e a Ferida do núcleo. */
public final class MogoRenderers {
    private static final int JADE = 0x3CFF7A, PODRE = 0xFFC830;
    private static final int LUZ_MAX = 0xF000F0;

    /** Bloco inteiro desenhado centrado na origem. */
    static void bloco(PoseStack ps, MultiBufferSource buf, BlockState s, int luz) {
        ps.pushPose();
        ps.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(s, ps, buf, luz, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    /** Prepara o Geo para desenhar brilhos em volta da entidade (espaço já transladado para ela). */
    static void geo(PoseStack ps, MultiBufferSource buf, Entity e, float pt) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 p = e.getPosition(pt);
        Geo.buf = buf;
        Geo.ps = ps;
        Geo.cam = new Vector3f((float) (cam.x - p.x), (float) (cam.y - p.y), (float) (cam.z - p.z));
        Geo.tempo = e.tickCount + pt;
    }

    // ---------------------------------------------------------------------------------------------

    public static class Rocha extends EntityRenderer<RochaMogo> {
        public Rocha(EntityRendererProvider.Context c) {
            super(c);
            shadowRadius = 0;
        }

        @Override
        public void render(RochaMogo r, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
            float tam = r.tam();
            float t = r.tickCount + pt;
            ps.pushPose();
            ps.translate(0, tam * 0.5f, 0);
            BlockState[] pedaco = switch (r.tipo()) {
                case RochaMogo.PEDRA -> new BlockState[]{Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.ROOTED_DIRT.defaultBlockState()};
                case RochaMogo.ESTILHACO -> new BlockState[]{MogoBlocos.CORRUPCAO.get().defaultBlockState(), MogoBlocos.FOCO.get().defaultBlockState()};
                default -> new BlockState[]{Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.TUFF.defaultBlockState()};
            };
            // um bloco grande e uns pedaços menores grudados: forma irregular que gira
            ps.mulPose(Axis.XP.rotationDegrees(r.giroX + t * 2.3f));
            ps.mulPose(Axis.YP.rotationDegrees(r.giroY + t * 1.7f));
            ps.pushPose();
            ps.scale(tam, tam * 0.85f, tam * 0.9f);
            bloco(ps, buf, pedaco[0], luz);
            ps.popPose();
            int sem = r.getId();
            for (int k = 0; k < 3; k++) {
                float a = (sem * 37 + k * 120) % 360;
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees(a));
                ps.mulPose(Axis.ZP.rotationDegrees(a * 0.7f));
                ps.translate(tam * 0.42f, tam * 0.1f * (k - 1), 0);
                float s = tam * (0.45f + 0.12f * k);
                ps.scale(s, s, s);
                bloco(ps, buf, pedaco[k == 2 ? 1 : 0], luz);
                ps.popPose();
            }
            ps.popPose();
            if (r.tipo() != RochaMogo.CINTURAO) {
                ps.pushPose();
                geo(ps, buf, r, pt);
                Geo.bola(0, tam * 0.5f, 0, tam * 1.1f, r.tipo() == RochaMogo.ESTILHACO ? PODRE : JADE, 0.35f);
                ps.popPose();
            }
            super.render(r, yaw, pt, ps, buf, luz);
        }

        @Override
        public ResourceLocation getTextureLocation(RochaMogo r) {
            return InventoryMenu.BLOCK_ATLAS;
        }
    }

    // ---------------------------------------------------------------------------------------------

    public static class Olho extends EntityRenderer<OlhoMogo> {
        public Olho(EntityRendererProvider.Context c) {
            super(c);
            shadowRadius = 0.6f;
        }

        @Override
        public void render(OlhoMogo o, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
            float t = o.tickCount + pt;
            float dor = o.hurtTime > 0 ? (o.hurtTime - pt) / 10f : 0;
            ps.pushPose();
            // pilar de jade com pedras girando em volta
            ps.pushPose();
            ps.translate(0, 0.7, 0);
            ps.scale(0.9f, 1.4f, 0.9f);
            ps.mulPose(Axis.YP.rotationDegrees(t * 0.6f));
            bloco(ps, buf, MogoBlocos.JADE.get().defaultBlockState(), LUZ_MAX);
            ps.popPose();
            for (int k = 0; k < 4; k++) {
                ps.pushPose();
                float a = t * 2.4f + k * 90;
                ps.translate(Mth.cos(a * Mth.DEG_TO_RAD) * 1.3f, 0.8f + 0.5f * Mth.sin(t * 0.08f + k), Mth.sin(a * Mth.DEG_TO_RAD) * 1.3f);
                ps.mulPose(Axis.XP.rotationDegrees(t * 4 + k * 40));
                ps.scale(0.32f, 0.32f, 0.32f);
                bloco(ps, buf, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), luz);
                ps.popPose();
            }
            // o olho: flutua acima do pilar e segue o jogador
            geo(ps, buf, o, pt);
            float pisca = Mth.sin(t * 0.05f) > 0.97f ? 0.15f : 1f;
            Geo.olho(0, 2.1f, 0, 0.9f, 0.55f * pisca, dor > 0 ? 0xFFFFFF : JADE, 0.9f);
            Geo.bola(0, 2.1f, 0, 1.4f + dor, JADE, 0.35f + 0.4f * dor);
            Geo.bola(0, 0.7f, 0, 1.6f, JADE, 0.12f);
            ps.popPose();
            super.render(o, yaw, pt, ps, buf, luz);
        }

        @Override
        public ResourceLocation getTextureLocation(OlhoMogo o) {
            return InventoryMenu.BLOCK_ATLAS;
        }
    }

    // ---------------------------------------------------------------------------------------------

    public static class FeridaR extends EntityRenderer<Ferida> {
        public FeridaR(EntityRendererProvider.Context c) {
            super(c);
            shadowRadius = 0;
        }

        @Override
        public boolean shouldRender(Ferida f, net.minecraft.client.renderer.culling.Frustum fr, double x, double y, double z) {
            return true;
        }

        @Override
        public void render(Ferida f, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
            float t = f.tickCount + pt;
            boolean casulo = f.emCasulo();
            float dor = f.hurtTime > 0 ? (f.hurtTime - pt) / 10f : 0;
            ps.pushPose();
            ps.translate(0, 2.1, 0);
            // massa central pulsando
            float pulso = 1 + 0.06f * Mth.sin(t * 0.21f) + 0.05f * dor;
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(t * 0.8f));
            ps.mulPose(Axis.XP.rotationDegrees(Mth.sin(t * 0.03f) * 18));
            ps.scale(2.0f * pulso, 2.2f * pulso, 2.0f * pulso);
            bloco(ps, buf, MogoBlocos.FOCO.get().defaultBlockState(), LUZ_MAX);
            ps.popPose();
            // tentáculos de pedaços corrompidos girando em camadas
            int n = 14;
            for (int k = 0; k < n; k++) {
                float camada = (k % 3) - 1;
                float a = t * (1.2f + 0.3f * (k % 3)) * (k % 2 == 0 ? 1 : -1) + k * 360f / n;
                float r = 2.3f + 0.4f * Mth.sin(t * 0.07f + k);
                if (casulo) r = 1.75f;
                ps.pushPose();
                ps.translate(Mth.cos(a * Mth.DEG_TO_RAD) * r, camada * (casulo ? 0.9f : 1.3f), Mth.sin(a * Mth.DEG_TO_RAD) * r);
                ps.mulPose(Axis.YP.rotationDegrees(-a));
                ps.mulPose(Axis.ZP.rotationDegrees(t * 3 + k * 25));
                float s = casulo ? 1.1f : 0.55f + 0.15f * (k % 3);
                ps.scale(s, s, s);
                bloco(ps, buf, (k % 4 == 0 ? Blocks.BLACKSTONE : MogoBlocos.CORRUPCAO.get()).defaultBlockState(), casulo ? luz : LUZ_MAX);
                ps.popPose();
            }
            geo(ps, buf, f, pt);
            Geo.cam.sub(0, 2.1f, 0);
            Geo.bola(0, 0, 0, 3.4f * pulso, PODRE, casulo ? 0.2f : 0.45f);
            Geo.bola(0, 0, 0, 1.4f, 0xFFFFFF, 0.25f + 0.5f * dor);
            if (casulo) {
                // a casca: um brilho seco que pisca
                float k = 0.5f + 0.5f * Mth.sin(t * 0.3f);
                Geo.bola(0, 0, 0, 3.0f, 0xC0A060, 0.25f * k);
            }
            ps.popPose();
            super.render(f, yaw, pt, ps, buf, luz);
        }

        @Override
        public ResourceLocation getTextureLocation(Ferida f) {
            return InventoryMenu.BLOCK_ATLAS;
        }
    }

    private MogoRenderers() {}
}
