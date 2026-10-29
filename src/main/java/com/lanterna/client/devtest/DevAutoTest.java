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
            at(200, () -> { look(0, 0); cmd("lanterna carga 1000"); cmd("time set 6000"); });
            // ---- Aldeão num planeta vira alienígena ----
            at(5, () -> cmd("execute in lanterna:oa run forceload add 0 0"));
            at(10, () -> cmd("execute in lanterna:oa run summon minecraft:villager 0 120 0"));
            at(10, () -> cmd("execute in lanterna:oa run data get entity @e[type=lanterna:alien,limit=1,x=0,y=100,z=0,distance=..60] Species"));
            at(5, () -> cmd("execute in lanterna:oa run forceload remove 0 0"));
            // ---- Alienígenas de cada planeta ----
            at(5, () -> {
                String[] species = {"oa", "korugar", "ysmault", "odym", "zamaron", "nok", "okaara", "ryut", "p2_1", "pm2_1"};
                for (int i = 0; i < species.length; i++) {
                    double x = -6.75 + i * 1.5;
                    cmd("summon lanterna:alien " + x + " 150 5.5 {NoAI:1b,Rotation:[180f,0f],Species:\"" + species[i] + "\"}");
                }
            });
            at(30, () -> { camera(CameraType.THIRD_PERSON_BACK); look(0, 10); });
            at(20, () -> shot("a1_aliens"));
            at(2, () -> { sideCamera(true, 0, 3.5, 0.2); });
            at(1, () -> sideCamera(false, 0, 0, 0));
            at(5, () -> { camera(CameraType.FIRST_PERSON); look(0, 15); });
            at(10, () -> shot("a2_aliens_close"));
            at(5, () -> cmd("tp @s -6.25 150 3 0 20"));
            at(10, () -> {
                var list = mc.level.getEntitiesOfClass(com.lanterna.alien.AlienEntity.class, mc.player.getBoundingBox().inflate(12));
                for (var alien : list) if (alien.speciesId().equals("oa")) { mc.gameMode.interact(mc.player, alien, net.minecraft.world.InteractionHand.MAIN_HAND); break; }
            });
            at(20, () -> shot("a3_trade_oa"));
            at(5, () -> mc.setScreen(null));
            at(5, () -> cmd("tp @s 5.75 150 3 0 20"));
            at(10, () -> {
                var list = mc.level.getEntitiesOfClass(com.lanterna.alien.AlienEntity.class, mc.player.getBoundingBox().inflate(12));
                for (var alien : list) if (alien.speciesId().equals("p2_1")) { mc.gameMode.interact(mc.player, alien, net.minecraft.world.InteractionHand.MAIN_HAND); break; }
            });
            at(20, () -> shot("a4_trade_generated"));
            at(5, () -> { mc.setScreen(null); cmd("kill @e[type=lanterna:alien]"); cmd("tp @s 0.5 150 0.5 0 0"); });
            // ---- Mapa estelar com planetas gerados ----
            at(10, () -> mc.setScreen(new com.lanterna.client.screen.StarMapScreen()));
            at(20, () -> shot("s1_starmap"));
            // ---- Piloto automático até o planeta gerado mais perto ----
            at(5, () -> {
                mc.setScreen(null);
                var here = com.lanterna.client.flight.Destinations.spacePosition(mc.player);
                com.lanterna.client.flight.Destinations.Destination best = null;
                for (var d : com.lanterna.client.flight.Destinations.all()) {
                    if (!d.id().startsWith("p")) continue;
                    if (best == null || d.center().distanceTo(here) < best.center().distanceTo(here)) best = d;
                }
                LanternaMod.LOGGER.info("AUTOTEST destino {} {} {} r={}", best == null ? null : best.id(), best == null ? null : best.name().getString(), best == null ? null : best.center(), best == null ? 0 : best.radius());
                com.lanterna.client.flight.Autopilot.start(best);
                camera(CameraType.THIRD_PERSON_BACK);
            });
            at(30, () -> shot("g1_trip"));
            at(30, () -> shot("g2_trip"));
            at(30, () -> shot("g3_trip"));
            at(30, () -> shot("g4_trip"));
            at(30, () -> shot("g5_trip"));
            at(30, () -> shot("g6_trip"));
            at(30, () -> shot("g7_trip"));
            at(30, () -> shot("g8_trip"));
            at(30, () -> shot("g9_trip"));
            at(30, () -> shot("g10_trip"));
            at(30, () -> shot("g11_trip"));
            at(30, () -> shot("g12_trip"));
            at(30, () -> shot("g13_trip"));
            at(30, () -> shot("g14_trip"));
            at(30, () -> shot("g15_trip"));
            at(30, () -> shot("g16_trip"));
            at(30, () -> shot("g17_trip"));
            at(30, () -> shot("g18_trip"));
            at(30, () -> shot("g19_trip"));
            at(30, () -> shot("g20_trip"));
            at(30, () -> shot("g21_trip"));
            at(30, () -> shot("g22_trip"));
            at(30, () -> shot("g23_trip"));
            at(30, () -> shot("g24_trip"));
            at(30, () -> shot("g25_trip"));
            at(30, () -> shot("g26_trip"));
            at(30, () -> shot("g27_trip"));
            at(30, () -> shot("g28_trip"));
            at(30, () -> shot("g29_trip"));
            at(30, () -> shot("g30_trip"));
            at(30, () -> shot("g31_trip"));
            at(30, () -> shot("g32_trip"));
            at(30, () -> shot("g33_trip"));
            at(30, () -> shot("g34_trip"));
            at(30, () -> shot("g35_trip"));
            at(30, () -> shot("g36_trip"));
            at(30, () -> shot("g37_trip"));
            at(30, () -> shot("g38_trip"));
            at(30, () -> shot("g39_trip"));
            at(30, () -> shot("g40_trip"));
            at(30, () -> shot("g41_trip"));
            at(30, () -> shot("g42_trip"));
            at(30, () -> shot("g43_trip"));
            at(30, () -> shot("g44_trip"));
            at(30, () -> shot("g45_trip"));
            at(5, () -> { camera(CameraType.FIRST_PERSON); look(mc.player.getYRot(), 20); });
            at(40, () -> shot("g90_planet_view"));
            at(5, () -> look(mc.player.getYRot() + 120, 15));
            at(20, () -> shot("g91_planet_view"));
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
