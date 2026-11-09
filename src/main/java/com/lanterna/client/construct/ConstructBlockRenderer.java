package com.lanterna.client.construct;

import com.lanterna.client.render.LanternaRenderTypes;
import com.lanterna.construct.ConstructBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Um bloco de construto se formando: primeiro só as arestas de luz aparecem e crescem do centro, depois o miolo
 * enche de energia e dá um clarão quando fica sólido. Os blocos da mesma parede começam em tempos diferentes
 * (os mais perto do anel antes), então a parede "corre" a partir do fio que sai do anel.
 */
public class ConstructBlockRenderer implements BlockEntityRenderer<ConstructBlock.Entity> {

    public ConstructBlockRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(ConstructBlock.Entity be, float pt, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        if (be.getLevel() == null || !be.getBlockState().getValue(ConstructBlock.FORMANDO)) return;
        float t = (be.getLevel().getGameTime() + pt - be.formaDe()) / ConstructBlock.FORMA;
        if (t <= 0) return;
        t = Math.min(1f, t);
        int color = be.color();
        int gc = EnergyShapes.brilho(color);
        VertexConsumer body = buf.getBuffer(LanternaRenderTypes.ENERGIA);
        VertexConsumer glow = buf.getBuffer(LanternaRenderTypes.BRILHO);
        EnergyShapes.tempo = be.getLevel().getGameTime() + pt;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        // arestas: crescem rápido do centro até o tamanho do bloco
        float aresta = Mth.clamp(t * 1.8f, 0, 1);
        float h = 0.5f * (0.25f + 0.75f * EnergyShapes.entrada(aresta * 5f, 5f));
        float w = 0.035f;
        int bc = color;
        for (int ax = 0; ax < 3; ax++) {
            for (int sa = -1; sa <= 1; sa += 2) {
                for (int sb = -1; sb <= 1; sb += 2) {
                    float a = sa * h, b = sb * h;
                    switch (ax) {
                        case 0 -> EnergyShapes.box(pose, body, glow, -h, a - w, b - w, h, a + w, b + w, bc, 0.95f);
                        case 1 -> EnergyShapes.box(pose, body, glow, a - w, -h, b - w, a + w, h, b + w, bc, 0.95f);
                        default -> EnergyShapes.box(pose, body, glow, a - w, b - w, -h, a + w, b + w, h, bc, 0.95f);
                    }
                }
            }
        }
        // miolo: enche depois das arestas
        float miolo = Mth.clamp((t - 0.35f) / 0.65f, 0, 1);
        if (miolo > 0) {
            float m = 0.5f * miolo;
            EnergyShapes.box(pose, body, glow, -m, -m, -m, m, m, m, color, 0.35f + 0.5f * miolo);
        }
        // clarão no fim
        if (t > 0.75f) EnergyShapes.clarao(pose, glow, Vec3.ZERO, 0.9f, gc, (t - 0.75f) * 3.2f, 4);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(ConstructBlock.Entity be) {
        return false;
    }

    static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
