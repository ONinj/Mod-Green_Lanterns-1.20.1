package com.lanterna.client.mogo;

import com.lanterna.LanternaMod;
import com.lanterna.client.escolha.Geo;
import com.lanterna.mogo.MogoDefesa;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** O feixe de luz que o Mogo atira em quem ele ataca: grosso, verde, com o núcleo branco, por menos de um segundo. */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClienteRaioMogo {
    private static final int VIDA = 18, JADE = 0x3CFF7A;

    private record Ativo(Vec3 de, int alvo, long inicio) {}

    private static final List<Ativo> ATIVOS = new ArrayList<>();

    public static void novo(MogoDefesa.Raio m) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ATIVOS.add(new Ativo(new Vec3(m.x(), m.y(), m.z()), m.alvo(), mc.level.getGameTime()));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ATIVOS.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { ATIVOS.clear(); return; }
        float pt = e.getPartialTick();
        long agora = mc.level.getGameTime();
        Vec3 cam = e.getCamera().getPosition();
        var buf = mc.renderBuffers().bufferSource();
        PoseStack ps = e.getPoseStack();
        ATIVOS.removeIf(a -> agora - a.inicio > VIDA);
        for (Ativo a : ATIVOS) {
            Entity alvo = mc.level.getEntity(a.alvo);
            Vec3 ate = alvo != null ? alvo.getPosition(pt).add(0, alvo.getBbHeight() * 0.5, 0) : a.de;
            float t = (agora - a.inicio + pt) / VIDA;
            float abre = Math.min(1f, t * 6f), fecha = 1f - Math.max(0f, (t - 0.6f) / 0.4f);
            float f = abre * fecha;
            if (f <= 0.01f) continue;
            Vec3 d = ate.subtract(a.de);
            float comp = (float) d.length();
            if (comp < 0.5f) continue;
            Vector3f dir = new Vector3f((float) d.x, (float) d.y, (float) d.z).normalize();
            ps.pushPose();
            ps.translate(a.de.x - cam.x, a.de.y - cam.y, a.de.z - cam.z);
            ps.mulPose(new Quaternionf().rotationTo(0, 1, 0, dir.x, dir.y, dir.z));
            Geo.buf = buf;
            Geo.ps = ps;
            Geo.cam = new Vector3f((float) (cam.x - a.de.x), (float) (cam.y - a.de.y), (float) (cam.z - a.de.z));
            Geo.tempo = agora + pt;
            float pulso = 1f + 0.08f * (float) Math.sin((agora + pt) * 2.1f);
            Geo.tubo(5.5f * f * pulso, 0, comp, JADE, 0.16f * f, 0.10f * f, 0.05f, true);
            Geo.tubo(2.6f * f, 0, comp, JADE, 0.45f * f, 0.35f * f, 0.07f, true);
            Geo.tubo(0.9f * f, 0, comp, 0xFFFFFF, 0.8f * f, 0.7f * f, 0.09f, true);
            // clarão no começo e no impacto
            Geo.bola(0, 0, 0, 14f * f, JADE, 0.5f * f);
            Geo.bola(0, comp, 0, 4f * f, 0xFFFFFF, 0.7f * f);
            ps.popPose();
        }
        buf.endBatch();
    }

    private ClienteRaioMogo() {}
}
