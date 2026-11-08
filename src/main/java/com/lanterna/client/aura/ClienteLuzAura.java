package com.lanterna.client.aura;

import com.lanterna.LanternaMod;
import com.lanterna.client.ClientRingData;
import com.lanterna.client.ClientVisibleRings;
import com.lanterna.client.render.LanternaRenderTypes;
import com.lanterna.client.visual.ClientVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * A cor da luz da aura: em volta de quem está com "iluminar" ligado, o chão e as paredes que a luz alcança ganham o
 * tom da Tropa (vermelho para a Fúria, verde para a Vontade...), forte perto e sumindo como a luz de uma tocha.
 * Só pinta onde a luz de verdade chega (a luz do bloco precisa ser tão forte quanto a da aura ali), então parede no
 * meio corta a cor, como uma tocha.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClienteLuzAura {
    private static final int R = 9;
    private static final float E = 0.004f;

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 cam = e.getCamera().getPosition();
        var buf = mc.renderBuffers().bufferSource();
        boolean algum = false;
        for (Player p : mc.level.players()) {
            if (p.isSpectator() || p.distanceToSqr(cam) > 80 * 80) continue;
            if (!ClientVisual.of(p.getId()).iluminar()) continue;
            int cor;
            if (p == mc.player) {
                if (!ClientRingData.hasRing || !ClientRingData.active) continue;
                cor = ClientRingData.color;
            } else {
                var v = ClientVisibleRings.of(p.getId());
                if (!v.active()) continue;
                cor = v.color();
            }
            if (!algum) { algum = true; }
            pintar(e.getPoseStack(), buf.getBuffer(LanternaRenderTypes.LUZ), mc, p, cor, cam, e.getPartialTick());
        }
        if (algum) buf.endBatch(LanternaRenderTypes.LUZ);
    }

    private static void pintar(PoseStack ps, VertexConsumer vc, Minecraft mc, Player p, int cor, Vec3 cam, float pt) {
        var level = mc.level;
        Vec3 fonte = p.getPosition(pt).add(0, p.getEyeHeight() * 0.85, 0);
        BlockPos centro = BlockPos.containing(fonte);
        float cr = ((cor >> 16) & 255) / 255f, cg = ((cor >> 8) & 255) / 255f, cb = (cor & 255) / 255f;
        // de dia a cor aparece pouco; no escuro ela toma conta
        float dia = level.getSkyDarken(pt);
        Matrix4f m = ps.last().pose();
        float tremor = 0.94f + 0.06f * (float) Math.sin((p.tickCount + pt) * 0.35f) * (float) Math.sin((p.tickCount + pt) * 0.13f + 1.7f);
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos(), viz = new BlockPos.MutableBlockPos();
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            if (dx * dx + dz * dz > R * R) continue;
            for (int dy = -5; dy <= 4; dy++) {
                q.set(centro.getX() + dx, centro.getY() + dy, centro.getZ() + dz);
                BlockState s = level.getBlockState(q);
                if (s.isAir() || !s.isSolidRender(level, q)) continue;
                for (Direction d : Direction.values()) {
                    viz.setWithOffset(q, d);
                    BlockState n = level.getBlockState(viz);
                    if (n.isSolidRender(level, viz)) continue;
                    // a face precisa estar virada para a luz
                    Vec3 centroFace = Vec3.atCenterOf(q).add(d.getStepX() * 0.5, d.getStepY() * 0.5, d.getStepZ() * 0.5);
                    Vec3 ate = fonte.subtract(centroFace);
                    if (ate.x * d.getStepX() + ate.y * d.getStepY() + ate.z * d.getStepZ() <= 0.05) continue;
                    double dist = ate.length();
                    if (dist > R) continue;
                    // só onde a luz de verdade chega (parede no meio corta)
                    int bl = level.getBrightness(LightLayer.BLOCK, viz);
                    int esperado = (int) Math.max(0, 14 - Math.round(Math.abs(viz.getX() - centro.getX()) + Math.abs(viz.getY() - centro.getY()) + Math.abs(viz.getZ() - centro.getZ())));
                    if (bl < esperado - 2) continue;
                    int sky = level.getBrightness(LightLayer.SKY, viz);
                    float ambiente = sky / 15f * dia;
                    float forca = (1f - 0.85f * ambiente) * 0.85f * tremor;
                    face(vc, m, q, d, fonte, cam, cr, cg, cb, forca);
                }
            }
        }
    }

    /** Uma face do bloco, com a intensidade calculada em cada canto (a luz cai suave pela face). */
    private static void face(VertexConsumer vc, Matrix4f m, BlockPos b, Direction d, Vec3 fonte, Vec3 cam,
                             float cr, float cg, float cb, float forca) {
        float x0 = b.getX(), y0 = b.getY(), z0 = b.getZ(), x1 = x0 + 1, y1 = y0 + 1, z1 = z0 + 1;
        float[][] c = switch (d) {
            case UP -> new float[][]{{x0, y1 + E, z0}, {x0, y1 + E, z1}, {x1, y1 + E, z1}, {x1, y1 + E, z0}};
            case DOWN -> new float[][]{{x0, y0 - E, z0}, {x1, y0 - E, z0}, {x1, y0 - E, z1}, {x0, y0 - E, z1}};
            case NORTH -> new float[][]{{x0, y0, z0 - E}, {x0, y1, z0 - E}, {x1, y1, z0 - E}, {x1, y0, z0 - E}};
            case SOUTH -> new float[][]{{x0, y0, z1 + E}, {x1, y0, z1 + E}, {x1, y1, z1 + E}, {x0, y1, z1 + E}};
            case WEST -> new float[][]{{x0 - E, y0, z0}, {x0 - E, y0, z1}, {x0 - E, y1, z1}, {x0 - E, y1, z0}};
            case EAST -> new float[][]{{x1 + E, y0, z0}, {x1 + E, y1, z0}, {x1 + E, y1, z1}, {x1 + E, y0, z1}};
        };
        for (float[] v : c) {
            double dx = v[0] - fonte.x, dy = v[1] - fonte.y, dz = v[2] - fonte.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            float k = (float) Math.max(0, 1 - dist / R);
            k = k * k * forca;
            vc.vertex(m, (float) (v[0] - cam.x), (float) (v[1] - cam.y), (float) (v[2] - cam.z)).color(cr * k, cg * k, cb * k, 1f).endVertex();
        }
    }

    private ClienteLuzAura() {}
}
