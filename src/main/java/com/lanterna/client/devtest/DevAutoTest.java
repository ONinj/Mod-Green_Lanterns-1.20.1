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
            // ---- Uniforme 3D vestido pelo anel (transformação) ----
            at(5, () -> { look(0, 0); sideCamera(true, 0, 3.0, 0.3); });
            at(5, () -> ModNetwork.sendToServer(new com.lanterna.network.TogglePowerPacket()));
            at(40, () -> shot("u0_off"));
            at(5, () -> ModNetwork.sendToServer(new com.lanterna.network.TogglePowerPacket()));
            at(3, () -> shot("u1_forming"));
            at(3, () -> shot("u2_forming"));
            at(3, () -> shot("u3_forming"));
            at(3, () -> shot("u4_forming"));
            at(3, () -> shot("u5_forming"));
            at(3, () -> shot("u6_forming"));
            at(3, () -> shot("u7_forming"));
            at(25, () -> shot("u8_done"));
            at(3, () -> sideCamera(true, 180, 3.0, 0.3));
            at(5, () -> shot("u9_back"));
            at(3, () -> sideCamera(true, 90, 3.0, 0.3));
            at(5, () -> shot("u10_side"));
            at(3, () -> sideCamera(false, 0, 0, 0));
            // ---- Uniformes das 9 Tropas em suportes de armadura ----
            at(5, () -> {
                String[] t = {"verde", "vermelho", "laranja", "amarelo", "azul", "indigo", "violeta", "preto", "branco"};
                for (int i = 0; i < t.length; i++) {
                    int x = -8 + i * 2;
                    String items = "[{id:\"lanterna:tropa_" + t[i] + "_boots\",Count:1b},{id:\"lanterna:tropa_" + t[i] + "_leggings\",Count:1b},"
                            + "{id:\"lanterna:tropa_" + t[i] + "_chestplate\",Count:1b},{id:\"lanterna:tropa_" + t[i] + "_helmet\",Count:1b}]";
                    cmd("summon armor_stand " + x + ".5 150 7.5 {ShowArms:1b,NoBasePlate:1b,Rotation:[180f,0f],ArmorItems:" + items + "}");
                }
                // Baterias de cada Tropa
                String[] c = {"green", "red", "orange", "yellow", "blue", "violet", "black"};
                for (int i = 0; i < c.length; i++) {
                    int x = -6 + i * 2;
                    cmd("setblock " + x + " 150 4 lanterna:power_battery[corps=" + c[i] + "]{Corps:\"lanterna:" + c[i] + "\"}");
                }
            });
            at(20, () -> { camera(CameraType.FIRST_PERSON); look(0, 12); });
            at(10, () -> shot("b1_batteries_uniforms"));
            at(5, () -> cmd("tp @s 0.5 150 1.5 0 30"));
            at(10, () -> shot("b2_batteries_close"));
            at(5, () -> cmd("tp @s 0.5 150 4.5 0 10"));
            at(10, () -> shot("b3_uniforms_close"));
            at(5, () -> { cmd("tp @s -7.5 151 5.5 0 15"); });
            at(10, () -> shot("b4_uniforms_left"));
            at(5, () -> { cmd("tp @s 4.5 151 5.5 0 15"); });
            at(10, () -> shot("b5_uniforms_right"));
            at(5, () -> { cmd("time set 18000"); cmd("tp @s 0.5 150 4.5 0 10"); });
            at(20, () -> shot("b6_night_glow"));
            at(5, () -> cmd("time set 6000"));
            // ---- Cajado da Compaixão no slot de bateria (mão esquerda) ----
            at(5, () -> {
                cmd("tp @s 0.5 150 0.5 0 0");
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    var sp = server.getPlayerList().getPlayers().get(0);
                    com.lanterna.power.EquipmentHelper.take(sp, com.lanterna.capability.RingData.BATTERY_SLOT);
                    com.lanterna.power.EquipmentHelper.equip(sp, com.lanterna.capability.RingData.BATTERY_SLOT,
                            new net.minecraft.world.item.ItemStack(com.lanterna.registry.ModItems.COMPASSION_STAFF.get()));
                });
            });
            at(10, () -> sideCamera(true, 0, 3.0, 0.3));
            at(10, () -> shot("s1_staff_front"));
            at(3, () -> sideCamera(true, -60, 3.0, 0.3));
            at(5, () -> shot("s2_staff_side"));
            at(3, () -> sideCamera(false, 0, 0, 0));
            // ---- Anéis das 9 Tropas ----
            at(5, () -> {
                String[] c = {"green", "yellow", "red", "blue", "violet", "indigo", "orange", "black", "white"};
                cmd("clear @s");
                for (String k : c) cmd("give @s lanterna:power_ring{Corps:\"lanterna:" + k + "\"}");
                cmd("give @s lanterna:compassion_staff");
            });
            at(10, () -> { camera(CameraType.FIRST_PERSON); look(0, 0); mc.player.getInventory().selected = 0; });
            at(10, () -> shot("r1_rings_hotbar"));
            at(5, () -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
            at(10, () -> shot("r2_inventory"));
            at(5, () -> mc.setScreen(null));
            at(5, () -> { camera(CameraType.THIRD_PERSON_FRONT); mc.player.getInventory().selected = 2; });
            at(10, () -> shot("r3_ring_in_hand"));
            at(5, () -> { camera(CameraType.FIRST_PERSON); mc.player.getInventory().selected = 0; });
            // ---- Teste de animação do Blockbench (osso filho girando no capacete verde) ----
            at(5, () -> { cmd("tp @s 0.5 150 0.5 0 0"); sideCamera(true, 20, 2.2, 0.8); });
            at(20, () -> shot("t1_anim_a"));
            at(10, () -> shot("t2_anim_b"));
            at(10, () -> shot("t3_anim_c"));
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
