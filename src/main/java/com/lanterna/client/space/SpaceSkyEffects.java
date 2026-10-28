package com.lanterna.client.space;

import com.lanterna.space.SpaceBodies;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Céu do espaço: fundo preto, milhares de estrelas e os corpos celestes (Terra, planetas, Sol).
 *
 * Os planetas são desenhados como esferas de verdade, iluminadas pelo Sol (lado dia e lado noite),
 * com uma atmosfera brilhando na borda. Mesmo a milhares de blocos de distância eles aparecem
 * no tamanho certo, e vão crescendo conforme você se aproxima.
 */
public class SpaceSkyEffects extends DimensionSpecialEffects {
    /** Distância máxima em que os corpos são desenhados (mantém tudo dentro do campo de visão da câmera). */
    private static final double DRAW_LIMIT = 150.0;
    private static final int LAT = 32;
    private static final int LON = 64;

    private VertexBuffer stars;

    public SpaceSkyEffects() {
        super(Float.NaN, false, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return Vec3.ZERO;
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack,
                                double camX, double camY, double camZ, Matrix4f projectionMatrix) {
        return true; // sem nuvens no espaço
    }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, net.minecraft.client.renderer.LightTexture lightTexture,
                                     double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        drawStars(poseStack, projectionMatrix);
        drawBodies(level, poseStack, camera, ticks + partialTick);

        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        return true;
    }

    // ===================== Estrelas =====================

    private void drawStars(PoseStack poseStack, Matrix4f projection) {
        if (stars == null) buildStars();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        stars.bind();
        stars.drawWithShader(poseStack.last().pose(), projection, GameRenderer.getPositionColorShader());
        VertexBuffer.unbind();
    }

    private void buildStars() {
        RandomSource r = RandomSource.create(10842L);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < 3500; ++i) {
            double x = r.nextFloat() * 2 - 1, y = r.nextFloat() * 2 - 1, z = r.nextFloat() * 2 - 1;
            double size = 0.08 + r.nextFloat() * 0.22;
            double len = x * x + y * y + z * z;
            if (len >= 1.0 || len <= 0.01) continue;
            len = 1.0 / Math.sqrt(len);
            x *= len; y *= len; z *= len;
            double px = x * 100, py = y * 100, pz = z * 100;
            double yaw = Math.atan2(x, z), sinYaw = Math.sin(yaw), cosYaw = Math.cos(yaw);
            double pitch = Math.atan2(Math.sqrt(x * x + z * z), y), sinPitch = Math.sin(pitch), cosPitch = Math.cos(pitch);
            double spin = r.nextDouble() * Math.PI * 2, sinSpin = Math.sin(spin), cosSpin = Math.cos(spin);

            // Cor: quase todas brancas, algumas azuladas, amareladas ou avermelhadas
            int kind = r.nextInt(10);
            float bright = 0.55f + r.nextFloat() * 0.45f;
            float cr = bright, cg = bright, cb = bright;
            if (kind == 0) { cr *= 0.7f; cg *= 0.8f; }
            else if (kind == 1) { cb *= 0.7f; }
            else if (kind == 2) { cg *= 0.7f; cb *= 0.6f; }

            for (int j = 0; j < 4; ++j) {
                double a = ((j & 2) - 1) * size;
                double c = ((j + 1 & 2) - 1) * size;
                double d = a * cosSpin - c * sinSpin;
                double e = c * cosSpin + a * sinSpin;
                double f = d * sinPitch;
                double g = -d * cosPitch;
                double h = g * sinYaw - e * cosYaw;
                double k = e * sinYaw + g * cosYaw;
                b.vertex(px + h, py + f, pz + k).color(cr, cg, cb, 1f).endVertex();
            }
        }
        BufferBuilder.RenderedBuffer rendered = b.end();
        stars = new VertexBuffer(VertexBuffer.Usage.STATIC);
        stars.bind();
        stars.upload(rendered);
        VertexBuffer.unbind();
    }

    // ===================== Corpos celestes =====================

    private void drawBodies(ClientLevel level, PoseStack poseStack, Camera camera, float time) {
        Vec3 cam = camera.getPosition();
        List<SpaceBodies.Body> sorted = new ArrayList<>(SpaceBodies.BODIES);
        sorted.sort(Comparator.comparingDouble((SpaceBodies.Body b) -> b.center().distanceToSqr(cam)).reversed());

        Matrix4f pose = poseStack.last().pose();
        RenderSystem.enableCull();
        for (SpaceBodies.Body body : sorted) {
            Vec3 rel = body.center().subtract(cam);
            double dist = rel.length();
            if (dist <= body.radius()) continue;

            // Encolhe o corpo inteiro (inclusive o lado de trás) para caber no alcance da câmera,
            // mantendo o mesmo tamanho aparente
            double scale = Math.min(1.0, DRAW_LIMIT / (dist + body.radius()));
            Vec3 pos = rel.scale(scale);
            float radius = (float) (body.radius() * scale);
            boolean isSun = body == SpaceBodies.SUN;
            Vec3 sunDir = SpaceBodies.SUN.center().subtract(body.center()).normalize();
            float spin = isSun ? 0f : time * 0.00012f * (float) (300.0 / body.radius());

            // Planeta
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, body.texture());
            RenderSystem.defaultBlendFunc();
            BufferBuilder b = Tesselator.getInstance().getBuilder();
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int i = 0; i < LAT; i++) {
                for (int j = 0; j < LON; j++) {
                    // Ordem anti-horária vista de fora: desenha só o lado virado para a câmera
                    vertex(b, pose, pos, radius, i, j, sunDir, isSun, spin);
                    vertex(b, pose, pos, radius, i, j + 1, sunDir, isSun, spin);
                    vertex(b, pose, pos, radius, i + 1, j + 1, sunDir, isSun, spin);
                    vertex(b, pose, pos, radius, i + 1, j, sunDir, isSun, spin);
                }
            }
            BufferUploader.drawWithShader(b.end());

            // Atmosfera: brilho na borda (mais forte onde a luz passa de raspão)
            // Bem perto da superfície a atmosfera vira só um leve brilho (senão vira névoa)
            float closeness = (float) Mth.clamp((dist - body.radius()) / (body.radius() * 0.6), 0.15, 1.0);
            drawAtmosphere(pose, pos, radius * (isSun ? 1.25f : 1.06f), body.atmosphere(), (isSun ? 0.9f : 0.75f) * closeness);
        }
    }

    private static void vertex(BufferBuilder b, Matrix4f pose, Vec3 center, float radius, int lat, int lon,
                               Vec3 sunDir, boolean isSun, float spin) {
        float theta = (float) Math.PI * lat / LAT;            // 0 = polo norte
        float phi = (float) Math.PI * 2 * lon / LON;
        float nx = Mth.sin(theta) * Mth.cos(phi);
        float ny = Mth.cos(theta);
        float nz = Mth.sin(theta) * Mth.sin(phi);
        float light;
        if (isSun) {
            light = 1f;
        } else {
            float d = (float) (nx * sunDir.x + ny * sunDir.y + nz * sunDir.z);
            // Lado da noite não fica preto: um pouco de luz refletida das estrelas
            light = Mth.clamp(0.22f + Math.max(0f, d) * 0.9f, 0.22f, 1f);
        }
        b.vertex(pose, (float) (center.x + nx * radius), (float) (center.y + ny * radius), (float) (center.z + nz * radius))
                .uv((float) lon / LON + spin, (float) lat / LAT)
                .color(light, light, light, 1f)
                .endVertex();
    }

    private static void drawAtmosphere(Matrix4f pose, Vec3 center, float radius, int color, float strength) {
        float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, bl = (color & 0xFF) / 255f;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        Vec3 toCam = center.scale(-1).normalize();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int lat = 24, lon = 48;
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                atmoVertex(b, pose, center, radius, i, j, lat, lon, toCam, r, g, bl, strength);
                atmoVertex(b, pose, center, radius, i, j + 1, lat, lon, toCam, r, g, bl, strength);
                atmoVertex(b, pose, center, radius, i + 1, j + 1, lat, lon, toCam, r, g, bl, strength);
                atmoVertex(b, pose, center, radius, i + 1, j, lat, lon, toCam, r, g, bl, strength);
            }
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.defaultBlendFunc();
    }

    private static void atmoVertex(BufferBuilder b, Matrix4f pose, Vec3 center, float radius, int i, int j, int lat, int lon,
                                   Vec3 toCam, float r, float g, float bl, float strength) {
        float theta = (float) Math.PI * i / lat;
        float phi = (float) Math.PI * 2 * j / lon;
        float nx = Mth.sin(theta) * Mth.cos(phi), ny = Mth.cos(theta), nz = Mth.sin(theta) * Mth.sin(phi);
        float facing = (float) Math.abs(nx * toCam.x + ny * toCam.y + nz * toCam.z);
        float rim = (1f - facing);
        float alpha = Mth.clamp(rim * rim * strength, 0f, 1f);
        b.vertex(pose, (float) (center.x + nx * radius), (float) (center.y + ny * radius), (float) (center.z + nz * radius))
                .color(r, g, bl, alpha).endVertex();
    }
}
