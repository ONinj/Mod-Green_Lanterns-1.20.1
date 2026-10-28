package com.lanterna.client.devtest;

import com.lanterna.LanternaMod;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.OpenLanternMenuPacket;
import com.lanterna.network.ToggleFlightPacket;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * SÓ PARA TESTE (não vai no .jar): roda um roteiro dentro do jogo e tira prints.
 * Ativado com -Dlanterna.autotest=true.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DevAutoTest {
    private static final boolean ENABLED = Boolean.getBoolean("lanterna.autotest");
    private static final List<Step> STEPS = new ArrayList<>();
    private static int tick;
    private static int stepIndex;
    private static int waitUntil;

    private record Step(int delay, Runnable action) {}

    // Câmera lateral de teste: um suporte de armadura invisível acompanhando o jogador
    private static net.minecraft.world.entity.decoration.ArmorStand sideCam;
    private static double camSide = 90, camDist = 4.5, camUp = 0.6;

    private static void sideCamera(boolean on, double side, double dist, double up) {
        Minecraft mc = Minecraft.getInstance();
        if (!on) { mc.setCameraEntity(mc.player); sideCam = null; return; }
        camSide = side; camDist = dist; camUp = up;
        sideCam = new net.minecraft.world.entity.decoration.ArmorStand(net.minecraft.world.entity.EntityType.ARMOR_STAND, mc.level);
        sideCam.setInvisible(true);
        updateSideCam();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(sideCam);
    }

    private static void updateSideCam() {
        Minecraft mc = Minecraft.getInstance();
        if (sideCam == null || mc.player == null) return;
        double yaw = Math.toRadians(mc.player.getYRot() + camSide);
        double x = mc.player.getX() - Math.sin(yaw) * camDist;
        double z = mc.player.getZ() + Math.cos(yaw) * camDist;
        double y = mc.player.getY() + camUp - sideCam.getEyeHeight() + 0.9;
        sideCam.setPos(x, y, z);
        sideCam.xo = x; sideCam.yo = y; sideCam.zo = z;
        sideCam.xOld = x; sideCam.yOld = y; sideCam.zOld = z;
        double dx = mc.player.getX() - x, dz = mc.player.getZ() - z;
        double dy = (mc.player.getY() + 0.9) - (y + sideCam.getEyeHeight());
        float yawDeg = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        sideCam.setYRot(yawDeg); sideCam.yRotO = yawDeg; sideCam.setYHeadRot(yawDeg);
        sideCam.setXRot(pitch); sideCam.xRotO = pitch;
    }

    private static void at(int delay, Runnable action) { STEPS.add(new Step(delay, action)); }

    private static void cmd(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.connection.sendCommand(command);
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "lanterna_" + name + ".png", mc.getMainRenderTarget(), msg -> {});
        LanternaMod.LOGGER.info("AUTOTEST shot {}", name);
    }

    private static void look(float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.setYRot(yaw); mc.player.setXRot(pitch);
        mc.player.yRotO = yaw; mc.player.xRotO = pitch;
        mc.player.setYHeadRot(yaw); mc.player.setYBodyRot(yaw);
    }

    private static void hold(KeyMapping key, boolean down) {
        KeyMapping.set(key.getKey(), down);
    }

    private static void camera(CameraType type) {
        Minecraft.getInstance().options.setCameraType(type);
    }

    static {
        if (ENABLED) {
            Minecraft mc = Minecraft.getInstance();
            at(60, () -> {
                mc.options.hideGui = false;
                mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
                cmd("gamemode survival"); cmd("time set 6000"); cmd("weather clear");
                cmd("effect give @s resistance 99999 4 true"); cmd("effect give @s saturation 99999 1 true");
                cmd("gamerule doMobSpawning false"); cmd("kill @e[type=!player]");
                cmd("lanterna resetar"); cmd("clear @s");
                cmd("tp @s 0 -60 0");
                look(0, 10);
            });
            at(30, () -> cmd("lanterna escolher lanterna:green"));
            at(200, () -> { camera(CameraType.THIRD_PERSON_FRONT); look(0, 15); });
            at(40, () -> shot("a1_ring_hand_front"));
            at(5, () -> { look(-60, 15); });
            at(10, () -> shot("a2_ring_hand_angle"));
            at(5, () -> { camera(CameraType.THIRD_PERSON_BACK); look(0, 10); });
            at(10, () -> shot("a3_back_aura"));
            // Desligar e ligar o anel (G)
            at(5, () -> ModNetwork.sendToServer(new com.lanterna.network.TogglePowerPacket()));
            at(20, () -> { camera(CameraType.THIRD_PERSON_FRONT); shot("a4_ring_off"); });
            at(5, () -> ModNetwork.sendToServer(new com.lanterna.network.TogglePowerPacket()));
            // Voo
            at(20, () -> { camera(CameraType.FIRST_PERSON); ModNetwork.sendToServer(new ToggleFlightPacket()); });
            at(10, () -> hold(mc.options.keyJump, true));
            at(30, () -> hold(mc.options.keyJump, false));
            at(10, () -> { look(0, 0); sideCamera(true, 90, 4.0, 0.3); });
            at(15, () -> shot("b1_hover_side"));
            at(3, () -> hold(mc.options.keyUp, true));
            at(30, () -> shot("b2_flying_side"));
            at(3, () -> look(0, 30));
            at(15, () -> shot("b3_diving_side"));
            at(3, () -> look(0, -35));
            at(15, () -> shot("b4_climbing_side"));
            at(3, () -> { look(0, 0); hold(mc.options.keySprint, true); });
            at(20, () -> shot("b5_boost_side"));
            at(3, () -> sideCamera(true, 35, 4.0, 0.8));
            at(10, () -> shot("b6_boost_front_angle"));
            at(3, () -> sideCamera(true, 150, 4.0, 1.2));
            at(10, () -> shot("b7_boost_back_angle"));
            at(3, () -> { hold(mc.options.keySprint, false); hold(mc.options.keyUp, false); sideCamera(true, 60, 3.5, 0.3); });
            at(35, () -> shot("b8_stopped_hover"));
            at(3, () -> { sideCamera(false, 0, 0, 0); ModNetwork.sendToServer(new ToggleFlightPacket()); });
            at(40, () -> {});
            // Amarelo (escuridão)
            at(10, () -> { cmd("lanterna resetar"); cmd("tp @s 0 -60 0"); look(0, 20); camera(CameraType.THIRD_PERSON_BACK); });
            at(20, () -> cmd("lanterna escolher lanterna:yellow"));
            at(60, () -> shot("c1_yellow"));
            at(80, () -> {});
            at(10, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        tick++;
        updateSideCam();
        if (stepIndex >= STEPS.size()) return;
        if (waitUntil == 0) waitUntil = tick + STEPS.get(stepIndex).delay();
        if (tick >= waitUntil) {
            try {
                STEPS.get(stepIndex).action().run();
            } catch (Exception e) {
                LanternaMod.LOGGER.error("AUTOTEST step {} failed", stepIndex, e);
            }
            stepIndex++;
            waitUntil = 0;
        }
    }

    private DevAutoTest() {}
}
