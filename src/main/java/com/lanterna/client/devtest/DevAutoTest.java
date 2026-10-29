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
        double ox = -Math.sin(yaw) * camDist, oz = Math.cos(yaw) * camDist;
        double oy = camUp - sideCam.getEyeHeight() + 0.9;
        // Posição atual e anterior seguem o jogador (para a câmera não "ficar para trás" em alta velocidade)
        sideCam.setPos(mc.player.getX() + ox, mc.player.getY() + oy, mc.player.getZ() + oz);
        sideCam.xo = sideCam.xOld = mc.player.xo + ox;
        sideCam.yo = sideCam.yOld = mc.player.yo + oy;
        sideCam.zo = sideCam.zOld = mc.player.zo + oz;
        double dy = 0.9 - (oy + sideCam.getEyeHeight());
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(ox * ox + oz * oz)));
        float look = (float) Math.toDegrees(Math.atan2(ox, -oz));
        sideCam.setYRot(look); sideCam.yRotO = look; sideCam.setYHeadRot(look);
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
        LanternaMod.LOGGER.info("AUTOTEST shot {} dim={} pos={} screen={}", name,
                mc.player != null ? mc.player.level().dimension().location() : null,
                mc.player != null ? mc.player.position() : null, mc.screen);
        if (mc.player != null) LanternaMod.LOGGER.info("AUTOTEST state fwd={} up={} flight={} active={} auto={} hyper={} speed={} chunk={}",
                mc.player.input.forwardImpulse, mc.options.keyUp.isDown(), com.lanterna.client.ClientRingData.flight,
                com.lanterna.client.ClientRingData.active, com.lanterna.client.flight.Autopilot.active(),
                com.lanterna.client.flight.ClientFlight.hyperOn(), com.lanterna.client.flight.ClientFlight.localSpeed(),
                mc.level.hasChunkAt(mc.player.getBlockX(), mc.player.getBlockZ()));
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
            at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
            at(60, () -> {
                mc.options.hideGui = false;
                mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
                cmd("gamemode survival"); cmd("time set 6000"); cmd("weather clear");
                cmd("effect give @s resistance 99999 4 true"); cmd("effect give @s saturation 99999 1 true");
                cmd("gamerule doMobSpawning false"); cmd("kill @e[type=!player]");
                cmd("lanterna resetar"); cmd("clear @s");
                cmd("execute in minecraft:overworld run tp @s 0.5 150 0.5"); cmd("effect give @s slow_falling 30 0 true");
                look(0, 0);
            });
            at(15, () -> { cmd("fill -8 149 -8 8 149 8 minecraft:smooth_stone"); cmd("fill -8 150 -8 8 160 8 minecraft:air"); cmd("tp @s 0.5 150 0.5"); });
            at(20, () -> cmd("lanterna escolher lanterna:green"));
            at(200, () -> { look(0, 0); cmd("lanterna carga 1000"); });
            // ---- Mapa estelar ----
            at(5, () -> mc.setScreen(new com.lanterna.client.screen.StarMapScreen()));
            at(20, () -> shot("p0_starmap"));
            // ---- Manobras: parafuso e freio ----
            at(5, () -> { mc.setScreen(null); ModNetwork.sendToServer(new ToggleFlightPacket()); });
            at(30, () -> { hold(mc.options.keyUp, true); hold(mc.options.keySprint, true); sideCamera(true, 120, 5, 0.8); });
            at(30, () -> { hold(mc.options.keyLeft, true); });
            at(1, () -> hold(mc.options.keyLeft, false));
            at(2, () -> hold(mc.options.keyLeft, true));
            at(1, () -> hold(mc.options.keyLeft, false));
            at(5, () -> shot("p1_barrel_roll"));
            at(6, () -> { hold(mc.options.keyUp, false); hold(mc.options.keySprint, false); hold(mc.options.keyDown, true); });
            at(4, () -> shot("p2_air_brake"));
            at(10, () -> hold(mc.options.keyDown, false));
            // ---- Piloto automático: Terra -> Oa ----
            at(20, () -> { look(0, 0); com.lanterna.client.flight.Autopilot.start(com.lanterna.client.flight.Destinations.byId("oa")); sideCamera(false, 0, 0, 0); camera(CameraType.THIRD_PERSON_BACK); });
            at(25, () -> shot("p3_auto_ascend"));
            at(25, () -> shot("p4_auto_ascend2"));
            at(15, () -> shot("p5_auto_t"));
            at(15, () -> shot("p6_auto_t"));
            at(15, () -> shot("p7_auto_t"));
            at(15, () -> shot("p8_auto_t"));
            at(15, () -> shot("p9_auto_t"));
            at(15, () -> shot("p10_auto_t"));
            at(15, () -> shot("p11_auto_t"));
            at(15, () -> shot("p12_auto_t"));
            at(15, () -> shot("p13_auto_t"));
            at(15, () -> shot("p14_auto_t"));
            at(15, () -> shot("p15_auto_t"));
            at(15, () -> shot("p16_auto_t"));
            at(3, () -> camera(CameraType.FIRST_PERSON));
            at(3, () -> shot("p17_auto_firstperson"));
            at(3, () -> camera(CameraType.THIRD_PERSON_BACK));
            at(25, () -> shot("p18_auto_late"));
            at(25, () -> shot("p19_auto_late"));
            at(25, () -> shot("p20_auto_late"));
            at(25, () -> shot("p21_auto_late"));
            at(25, () -> shot("p22_auto_late"));
            at(25, () -> shot("p23_auto_late"));
            at(25, () -> shot("p24_auto_late"));
            at(25, () -> shot("p25_auto_late"));
            at(25, () -> shot("p26_auto_late"));
            at(25, () -> shot("p27_auto_late"));
            at(25, () -> shot("p28_auto_late"));
            at(25, () -> shot("p29_auto_late"));
            at(30, () -> shot("p30_auto_late"));
            at(30, () -> shot("p31_auto_late"));
            at(30, () -> shot("p32_auto_late"));
            at(30, () -> shot("p33_auto_late"));
            at(30, () -> shot("p34_auto_late"));
            at(30, () -> shot("p35_auto_late"));
            at(30, () -> shot("p36_auto_late"));
            at(30, () -> shot("p37_auto_late"));
            at(30, () -> shot("p38_auto_late"));
            at(30, () -> shot("p39_auto_late"));
            // ---- Hiperpropulsão manual no espaço ----
            at(10, () -> { com.lanterna.client.flight.Autopilot.stop(null); cmd("execute in lanterna:space run tp @s 0 0 0 90 0"); });
            at(40, () -> { look(90, 0); hold(mc.options.keyUp, true); hold(mc.options.keySprint, true); camera(CameraType.THIRD_PERSON_BACK); });
            at(30, () -> shot("h1_charging"));
            at(20, () -> shot("h2_hyper"));
            at(20, () -> shot("h3_hyper"));
            at(5, () -> camera(CameraType.THIRD_PERSON_FRONT));
            at(5, () -> shot("h4_hyper_front"));
            at(5, () -> camera(CameraType.FIRST_PERSON));
            at(5, () -> shot("h5_firstperson"));
            at(10, () -> shot("h6_firstperson"));
            at(5, () -> { hold(mc.options.keyUp, false); hold(mc.options.keySprint, false); });
            at(30, () -> shot("h7_after"));
            at(60, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        // Mundo superplano de teste: aceita o aviso de "configurações experimentais" sozinho
        if (mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen confirm) {
            for (var child : confirm.children()) {
                if (child instanceof net.minecraft.client.gui.components.Button button
                        && button.getMessage().getString().equals("Proceed")) {
                    button.onPress();
                    return;
                }
            }
        }
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
