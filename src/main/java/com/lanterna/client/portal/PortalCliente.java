package com.lanterna.client.portal;

import com.lanterna.LanternaMod;
import com.lanterna.client.construct.CastAnim;
import com.lanterna.portal.PortalEstelar;
import com.lanterna.portal.PortalPackets;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * A animação da Porta Estelar para quem viaja: câmera em terceira pessoa, o Lanterna sobe, estende o braço, a porta
 * nasce, ele voa para dentro (clarão), aparece no planeta saindo da outra porta e a câmera volta ao normal.
 * O movimento é feito aqui no cliente (fica liso); o servidor só faz a troca de mundo.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PortalCliente {
    /** -1 parado, 0 saindo, 1 chegando. */
    private static int fase = -1;
    private static int t;
    private static Vec3 inicio, porta;
    private static float yaw;
    private static int cor;
    private static CameraType camAntes;
    private static int espera;
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> mundo;

    public static boolean ativo() {
        return fase >= 0;
    }

    public static void receber(PortalPackets.Anim m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean eu = m.entity() == mc.player.getId();
        if (m.fase() == 0) CastAnim.segurar(m.entity(), PortalEstelar.T_ATRAVESSA);
        if (!eu) return;
        switch (m.fase()) {
            case 0 -> {
                fase = 0;
                t = 0;
                inicio = mc.player.position();
                mundo = mc.level.dimension();
                porta = new Vec3(m.x(), m.y(), m.z());
                yaw = m.yaw();
                cor = m.cor();
                if (camAntes == null) camAntes = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                if (mc.screen != null) mc.setScreen(null);
            }
            case 1 -> {
                fase = 1;
                t = 0;
                espera = 0;
                // na chegada a câmera fica na frente: o Lanterna sai da porta vindo na direção dela
                if (camAntes == null) camAntes = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                porta = new Vec3(m.x(), m.y(), m.z());
                yaw = m.yaw();
                cor = m.cor();
                CastAnim.segurar(m.entity(), 16);
            }
            default -> {
                // fim mandado pelo servidor: só vale se a animação de chegada já acabou ou nem começou
                if (fase != 1) terminar();
            }
        }
    }

    private static void terminar() {
        Minecraft mc = Minecraft.getInstance();
        fase = -1;
        if (camAntes != null) mc.options.setCameraType(camAntes);
        camAntes = null;
    }

    private static double suave(double k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || fase < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            fase = -1;
            return;
        }
        // Chegando num mundo que ainda está carregando: o tempo da animação só anda com o chão pronto
        if (fase == 1 && (mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen
                || !mc.level.hasChunkAt(mc.player.blockPosition()))) {
            espera++;
            if (espera < 400) return;
        }
        t++;
        if (t > 120) {
            terminar();
            return;
        }
        var p = mc.player;
        Vec3 fwd = Vec3.directionFromRotation(0, yaw);
        Vec3 alvo;
        if (fase == 0) {
            // passou da porta (ou já trocou de mundo): fica parado esperando a chegada
            if (t > com.lanterna.portal.PortalEstelar.T_ATRAVESSA || !mc.level.dimension().equals(mundo)) {
                p.setDeltaMovement(Vec3.ZERO);
                return;
            }
            Vec3 alto = inicio.add(0, 1.4, 0);
            if (t <= 10) alvo = inicio.lerp(alto, suave(t / 10.0));
            else if (t <= 32) alvo = alto.add(0, Math.sin(t * 0.3) * 0.06, 0);
            else {
                double k = (t - 32) / 12.0;
                alvo = alto.lerp(porta.add(fwd.scale(0.8)).add(0, 0.2, 0), k * k);
            }
            // energia correndo da mão até a porta
            if (t > 8 && t < 34) {
                Vec3 mao = CastAnim.mao(p, 1f);
                Vec3 centro = porta.add(0, 1.7, 0);
                var d = new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 0.9f);
                for (int i = 0; i < 2; i++) {
                    double k = p.getRandom().nextDouble();
                    Vec3 q = mao.lerp(centro, k);
                    mc.level.addParticle(d, q.x, q.y, q.z, 0, 0, 0);
                }
            }
        } else {
            Vec3 dentro = porta.add(0, 0.2, 0);
            Vec3 fora = porta.add(fwd.scale(3.2)).add(0, 0.05, 0);
            alvo = dentro.lerp(fora, suave(t / 18.0));
            if (t == 1) for (int i = 0; i < 30; i++)
                mc.level.addParticle(ParticleTypes.END_ROD, porta.x, porta.y + 1.7, porta.z,
                        fwd.x * 0.3 + (p.getRandom().nextDouble() - 0.5) * 0.3, (p.getRandom().nextDouble() - 0.5) * 0.3, fwd.z * 0.3 + (p.getRandom().nextDouble() - 0.5) * 0.3);
            if (t >= 22) {
                terminar();
                return;
            }
        }
        p.setPos(alvo.x, alvo.y, alvo.z);
        p.setDeltaMovement(Vec3.ZERO);
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        p.yBodyRot = yaw;
        p.setXRot(6f);
        p.fallDistance = 0;
    }

    /** Clarão branco ao atravessar e bordas na cor do anel durante a viagem. */
    public static final IGuiOverlay HUD = (ForgeGui gui, GuiGraphics g, float pt, int w, int h) -> {
        if (fase < 0) return;
        float tt = t + pt;
        float flash;
        if (fase == 0) flash = Mth.clamp((tt - 36) / 8f, 0, 1);
        else flash = Mth.clamp(1 - tt / 8f, 0, 1);
        int borda = (int) (Math.min(w, h) * 0.2f);
        float vin = fase == 0 ? Mth.clamp(tt / 10f, 0, 1) * 0.5f : Mth.clamp(1 - tt / 20f, 0, 1) * 0.5f;
        for (int i = 0; i < borda; i += 2) {
            float k = 1f - i / (float) borda;
            int a = (int) (255 * vin * k * k);
            int c = (a << 24) | (cor & 0xFFFFFF);
            g.fill(0, i, w, i + 2, c);
            g.fill(0, h - i - 2, w, h - i, c);
            g.fill(i, 0, i + 2, h, c);
            g.fill(w - i - 2, 0, w - i, h, c);
        }
        if (flash > 0) g.fill(0, 0, w, h, ((int) (255 * flash) << 24) | 0xF4FFF6);
    };

    private PortalCliente() {}
}
