package com.lanterna.client.necron;

import com.lanterna.LanternaMod;
import com.lanterna.particle.GlowOrbOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 0.59.1: as faixas de luz que saem da caveira para a boca do Necron enquanto ele se alimenta (o chefe e a Forma do
 * Necron). A pose fica no PoseNecron; aqui só as partículas, em coordenadas do modelo convertidas para o mundo.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AlimentoNecron {
    /** Onde ficam a caveira erguida e a boca, no modelo (unidades de 1/16 de bloco; frente = -z). */
    static double CAV_FRENTE = 14.0, CAV_ALTURA = 35.5, BOCA_FRENTE = 3.2, BOCA_ALTURA = 32.5, CAV_LADO = 0;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        for (Entity n : mc.level.getEntitiesOfClass(com.lanterna.necron.NecronEntity.class, mc.player.getBoundingBox().inflate(64)))
            emitir(n, 1.6);
        for (Player p : mc.level.players())
            if (NecroformaCliente.ativa(p) && p.distanceToSqr(mc.player) < 64 * 64) emitir(p, NecroformaCliente.ESCALA);
    }

    private static Vec3 ponto(Entity n, double frente, double altura, double lado, double escala) {
        double yaw = Math.toRadians(n instanceof net.minecraft.world.entity.LivingEntity l ? l.yBodyRot : n.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double f = frente / 16 * escala, s = lado / 16 * escala;
        return new Vec3(n.getX() + fx * f - fz * s, n.getY() + altura / 16 * escala, n.getZ() + fz * f + fx * s);
    }

    private static void emitir(Entity n, double escala) {
        float q = PoseNecron.quieto(n);
        if (q < 0.5f) return;
        float g = PoseNecron.alimento(n);
        var l = n.level();
        var r = n.level().getRandom();
        // a caveira surge numa nuvem escura e, no fim, vira pó
        if (g >= 0.1f && g < 0.13f) {
            Vec3 c = ponto(n, 6, 20, 8, escala);
            for (int i = 0; i < 6; i++) l.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, c.x, c.y, c.z,
                    (r.nextDouble() - 0.5) * 0.05, 0.02, (r.nextDouble() - 0.5) * 0.05);
        }
        if (g >= 0.8f && g < 0.88f) {
            Vec3 c = ponto(n, CAV_FRENTE, CAV_ALTURA, CAV_LADO, escala);
            for (int i = 0; i < 4; i++) l.addParticle(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.FALLING_DUST,
                    net.minecraft.world.level.block.Blocks.BONE_BLOCK.defaultBlockState()), c.x + (r.nextDouble() - 0.5) * 0.4, c.y, c.z + (r.nextDouble() - 0.5) * 0.4, 0, 0, 0);
        }
        if (g < 0.36f || g > 0.78f) return;
        // faixas de luz: pontos ao longo do caminho caveira → boca, ondulando, puxados para a boca
        Vec3 a = ponto(n, CAV_FRENTE, CAV_ALTURA, CAV_LADO, escala), b = ponto(n, BOCA_FRENTE, BOCA_ALTURA, 0, escala);
        Vec3 d = b.subtract(a);
        for (int k = 0; k < 3; k++) {
            double u = r.nextDouble();
            double fase = n.tickCount * 0.5 + k * 2.1;
            double onda = Math.sin(u * Math.PI) * 0.12 * escala;
            Vec3 p = a.add(d.scale(u)).add(Math.sin(fase + u * 6) * onda, Math.cos(fase * 1.3 + u * 5) * onda, Math.cos(fase + u * 6) * onda);
            Vec3 v = d.scale(0.06);
            int cor = k == 0 ? 0xD8FFE8 : k == 1 ? 0x9A5CFF : 0x6FFFB0;
            l.addParticle(GlowOrbOptions.of(cor, (float) (0.05 * escala + r.nextDouble() * 0.03)), p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }

    private AlimentoNecron() {}
}
