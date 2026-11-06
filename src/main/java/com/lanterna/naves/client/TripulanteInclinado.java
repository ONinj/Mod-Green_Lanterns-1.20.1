package com.lanterna.naves.client;

import com.lanterna.LanternaMod;
import com.lanterna.naves.NaveEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Quem está sentado numa nave inclina junto com ela: o corpo gira com o nariz e com o lado da nave (o assento já
 * acompanha em NaveEntity.positionRider), e em primeira pessoa a visão de quem está a bordo inclina de lado nas curvas.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TripulanteInclinado {
    private static final Map<LivingEntity, PoseStack> GIRADOS = new IdentityHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void antes(RenderLivingEvent.Pre<?, ?> e) {
        if (e.isCanceled() || !(e.getEntity().getVehicle() instanceof NaveEntity n)) return;
        float pt = e.getPartialTick();
        float pitch = n.visPitch(pt), roll = n.visRoll(pt);
        if (Math.abs(pitch) < 0.01f && Math.abs(roll) < 0.01f) return;
        float giro = 180 - Mth.rotLerp(pt, n.yRotO, n.getYRot());
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        // mesma inclinação da nave, no espaço do mundo, em volta dos pés de quem está sentado
        ps.mulPose(Axis.YP.rotationDegrees(giro));
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.mulPose(Axis.ZP.rotationDegrees(roll));
        ps.mulPose(Axis.YP.rotationDegrees(-giro));
        GIRADOS.put(e.getEntity(), ps);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void depois(RenderLivingEvent.Post<?, ?> e) {
        PoseStack ps = GIRADOS.remove(e.getEntity());
        if (ps != null) ps.popPose();
    }

    /** Primeira pessoa: a visão inclina de lado com a nave (um pouco menos que o casco, para não enjoar). */
    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.getCameraType() != CameraType.FIRST_PERSON) return;
        if (!(mc.player.getVehicle() instanceof NaveEntity n)) return;
        float pt = (float) e.getPartialTick();
        float roll = n.visRoll(pt);
        if (Math.abs(roll) < 0.01f) return;
        // o roll da nave é visto de frente; relativo ao olhar de quem pilota, conta o quanto o olhar está alinhado ao nariz
        float rel = Mth.wrapDegrees(mc.player.getViewYRot(pt) - Mth.rotLerp(pt, n.yRotO, n.getYRot()));
        float alinhado = Mth.cos(rel * Mth.DEG_TO_RAD);
        e.setRoll(e.getRoll() - roll * 0.6f * alinhado);
    }

    private TripulanteInclinado() {}
}
