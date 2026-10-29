package com.lanterna.client.lore;

import com.lanterna.LanternaMod;
import com.lanterna.lore.PlanetLore;
import com.lanterna.lore.entity.Race;
import com.lanterna.lore.entity.RaceEntity;
import com.lanterna.lore.entity.boss.Bosses;
import com.lanterna.lore.entity.boss.LoreBoss;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renderizador das criaturas e bosses do pacote Lore3D (GeckoLib): escala do boss, brilho (_glowmask),
 * modo translúcido para cristal/água/fumaça, e os ossos especiais de cada um:
 *
 *  Brakkari chifre_1..6 pela quantidade de chifres · Skrell'Vath mascara_lider só nos líderes (e somem na névoa)
 *  Veltari reverência · Zaëlith fio_de_luz apontando para o par · Penitente rosto_1..8 somem quando curados
 *  Nyrielle orbita_1..6 somem quando o cristal é quebrado · Devorador cabeca_* só dos bosses já derrotados
 *  Thessara: só as ilusões projetam sombra · ecos: pretos e menores
 */
public class LoreRenderer<T extends LivingEntity & GeoAnimatable> extends GeoEntityRenderer<T> {
    private final boolean translucent;
    private final float baseScale;

    public LoreRenderer(EntityRendererProvider.Context ctx, String name, float scale, boolean translucent) {
        super(ctx, new DefaultedEntityGeoModel<>(LanternaMod.id(name), true));
        this.translucent = translucent;
        this.baseScale = scale;
        this.shadowRadius = 0.5f * scale;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture, @Nullable MultiBufferSource buffers, float pt) {
        boolean fade = animatable instanceof LoreBoss b && b.isEcho();
        return translucent || fade || isSkrell(animatable) ? RenderType.entityTranslucent(texture) : super.getRenderType(animatable, texture, buffers, pt);
    }

    private static boolean isSkrell(Object e) {
        return e instanceof RaceEntity r && r.race() == Race.SKRELL_VATH;
    }

    @Override
    public void render(T entity, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float s = baseScale;
        if (entity instanceof LoreBoss b && b.isEcho()) s *= 0.6f;
        if (entity instanceof Bosses.Vigia v && v.isSmall()) s *= 0.75f;
        withScale(s);
        shadowRadius = 0.5f * s * Math.max(1, entity.getBbWidth());
        if (entity instanceof Bosses.Thessara t) shadowRadius = t.isIllusion() ? 2.0f : 0f;
        super.render(entity, yaw, pt, pose, buffers, light);
    }

    @Override
    public Color getRenderColor(T entity, float pt, int light) {
        if (entity instanceof LoreBoss b && b.isEcho()) return Color.ofRGBA(40, 36, 52, 220);
        if (entity instanceof Bosses.Morrow m && m.deathTime > 0) return Color.ofRGBA(120, 190, 255, 255);
        if (isSkrell(entity)) {
            // Na névoa de Korugar, quase invisíveis de longe
            Entity cam = Minecraft.getInstance().getCameraEntity();
            if (cam != null && PlanetLore.byDimension(entity.level().dimension()) == PlanetLore.KORUGAR) {
                float d = entity.distanceTo(cam);
                int a = (int) (255 * Mth.clamp(1f - (d - 5f) / 12f, 0.12f, 1f));
                return Color.ofRGBA(255, 255, 255, a);
            }
        }
        return super.getRenderColor(entity, pt, light);
    }

    @Override
    public void renderRecursively(PoseStack pose, T entity, GeoBone bone, RenderType type, MultiBufferSource buffers,
                                  VertexConsumer vc, boolean reRender, float pt, int light, int overlay,
                                  float r, float g, float b, float a) {
        String n = bone.getName();
        if (entity instanceof RaceEntity race) {
            switch (race.race()) {
                case BRAKKARI -> {
                    if (n.startsWith("chifre_")) bone.setHidden(index(n) > Math.max(1, race.getVariant()));
                }
                case SKRELL_VATH -> {
                    if (n.equals("mascara_lider")) bone.setHidden(!race.hasFlag(RaceEntity.LEADER_MASK));
                }
                case VELTARI -> {
                    if (n.equals("body")) bone.setRotX(race.hasFlag(RaceEntity.BOW) ? -0.5f : 0f);
                }
                case ZAELITH -> {
                    if (n.equals("fio_de_luz")) aimThread(entity, race, bone, pt);
                }
                default -> {}
            }
        } else if (entity instanceof Bosses.Penitente p) {
            if (n.startsWith("rosto_")) bone.setHidden((p.state() & (1 << (index(n) - 1))) != 0);
        } else if (entity instanceof Bosses.Nyrielle ny) {
            if (n.startsWith("orbita_")) bone.setHidden((ny.state() & (1 << (index(n) - 1))) != 0);
        } else if (entity instanceof Bosses.Devorador d) {
            if (n.startsWith("cabeca_")) {
                int bit = -1;
                String boss = n.substring(7);
                for (int i = 0; i < Bosses.Devorador.HEADS.length; i++) {
                    if (Bosses.Devorador.HEADS[i].startsWith(boss)) bit = i;
                }
                bone.setHidden(bit < 0 || (d.state() & (1 << bit)) == 0);
            }
        }
        super.renderRecursively(pose, entity, bone, type, buffers, vc, reRender, pt, light, overlay, r, g, b, a);
    }

    /** O fio de luz do Zaëlith aponta e estica até o par. */
    private void aimThread(T entity, RaceEntity race, GeoBone bone, float pt) {
        Entity mate = entity.level().getEntity(race.pairId());
        if (mate == null) {
            bone.setHidden(true);
            return;
        }
        bone.setHidden(false);
        Vec3 d = mate.getPosition(pt).subtract(entity.getPosition(pt));
        float bodyYaw = Mth.rotLerp(pt, entity.yBodyRotO, entity.yBodyRot);
        float target = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float rel = Mth.wrapDegrees(target - bodyYaw);
        bone.setRotY((float) Math.toRadians(-rel));
        float len = (float) Math.sqrt(d.x * d.x + d.z * d.z);
        bone.setScaleZ(Math.max(0.05f, len * 16f / 16f / baseScale));
    }

    private static int index(String bone) {
        int u = bone.lastIndexOf('_');
        try {
            return Integer.parseInt(bone.substring(u + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
