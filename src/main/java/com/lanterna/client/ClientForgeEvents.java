package com.lanterna.client;

import com.lanterna.LanternaMod;
import com.lanterna.client.screen.LanternTabs;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.OpenLanternMenuPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import com.lanterna.client.flight.ClientFlight;
import com.lanterna.client.uniform.UniformTransition;
import com.lanterna.client.recharge.ClientRecharge;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import com.lanterna.network.TogglePowerPacket;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import com.lanterna.network.PocketRechargePacket;
import com.lanterna.network.ToggleFlightPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Eventos de cliente durante o jogo. */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        while (KeyBindings.TOGGLE_FLIGHT.consumeClick()) {
            if (ClientRingData.hasRing) {
                ModNetwork.sendToServer(new ToggleFlightPacket());
            }
        }
        while (KeyBindings.POCKET_RECHARGE.consumeClick()) {
            if (ClientRingData.hasRing) {
                ModNetwork.sendToServer(new PocketRechargePacket());
            }
        }
        while (KeyBindings.OPEN_LANTERN.consumeClick()) {
            ModNetwork.sendToServer(new OpenLanternMenuPacket());
        }
        while (KeyBindings.TOGGLE_POWER.consumeClick()) {
            if (ClientRingData.hasRing) {
                ModNetwork.sendToServer(new TogglePowerPacket());
            }
        }
        ClientCutscene.tick();

        // Pose de voo e partículas de todos os jogadores visíveis
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && !mc.isPaused()) {
            for (Player player : mc.level.players()) {
                ClientFlight.tickPose(player);
                UniformTransition.tick(player);
            }
            ClientFlight.tickShake();
            ClientRecharge.tick();
        }
    }

    // ===================== Abas no inventário (sobrevivência e criativo) =====================

    /** Desenha as abas "Inventário" e "Lanterna" no inventário, e a aba lateral no criativo. */
    @SubscribeEvent
    public static void onInventoryRender(ScreenEvent.Render.Post event) {
        GuiGraphics g = event.getGuiGraphics();
        if (event.getScreen() instanceof CreativeModeInventoryScreen creative) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            LanternTabs.renderSide(g, creative.getGuiLeft(), creative.getGuiTop(), creative.getXSize());
            g.pose().popPose();
            if (LanternTabs.hoveredSide(creative.getGuiLeft(), creative.getGuiTop(), creative.getXSize(),
                    event.getMouseX(), event.getMouseY())) {
                g.renderTooltip(Minecraft.getInstance().font, Component.translatable("gui.lanterna.tab.lantern"),
                        event.getMouseX(), event.getMouseY());
            }
            return;
        }
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        g.pose().pushPose();
        g.pose().translate(0, 0, 300); // por cima dos itens
        LanternTabs.renderBack(g, left, top, LanternTabs.Tab.INVENTORY);
        LanternTabs.renderFront(g, left, top, LanternTabs.Tab.INVENTORY);
        g.pose().popPose();
        LanternTabs.renderTooltip(g, Minecraft.getInstance().font, left, top, event.getMouseX(), event.getMouseY());
    }

    /** Clique na aba "Lanterna" abre a tela do anel. */
    @SubscribeEvent
    public static void onInventoryClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0) return;
        if (event.getScreen() instanceof CreativeModeInventoryScreen creative) {
            if (LanternTabs.hoveredSide(creative.getGuiLeft(), creative.getGuiTop(), creative.getXSize(),
                    event.getMouseX(), event.getMouseY())) {
                ModNetwork.sendToServer(new OpenLanternMenuPacket());
                event.setCanceled(true);
            }
            return;
        }
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        LanternTabs.Tab tab = LanternTabs.hovered(screen.getGuiLeft(), screen.getGuiTop(), event.getMouseX(), event.getMouseY());
        if (tab == null) return;
        if (tab == LanternTabs.Tab.LANTERN) ModNetwork.sendToServer(new OpenLanternMenuPacket());
        event.setCanceled(true); // não deixa o clique na aba jogar o item da mão no chão
    }

    // ===================== Voo do anel =====================

    /** Física do voo do jogador local (antes do Minecraft mover o jogador). */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.side.isClient()
                && event.player == Minecraft.getInstance().player) {
            ClientFlight.tickLocalPlayer((LocalPlayer) event.player);
        }
    }

    /** A câmera inclina um pouco nas curvas do voo. */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ClientFlight.isFlying(mc.player)) return;
        float bank = ClientFlight.bank(mc.player, (float) event.getPartialTick());
        float amount = mc.options.getCameraType().isFirstPerson() ? 0.3f : 0.15f;
        event.setRoll(event.getRoll() - bank * amount);

        // Tremor leve em altíssima velocidade e no estrondo sônico
        float shake = ClientFlight.shakeAmount();
        if (shake > 0) {
            var rnd = mc.player.getRandom();
            event.setYaw(event.getYaw() + (rnd.nextFloat() - 0.5f) * shake);
            event.setPitch(event.getPitch() + (rnd.nextFloat() - 0.5f) * shake);
        }
    }

    /** O campo de visão abre com a velocidade (sensação de velocidade). */
    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getPlayer() != mc.player || !ClientFlight.isFlying(mc.player)) return;
        float extra = (float) Math.min(0.3, ClientFlight.localSpeed() * 0.2);
        event.setNewFovModifier(1.0f + extra);
    }

    /** Em primeira pessoa, a mão do anel desce e avança até a bateria durante a recarga. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean ringHand = (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND);
        if (!ringHand) return;
        float k = ClientRecharge.reach(ClientRecharge.time(mc.player, event.getPartialTick()));
        if (k <= 0f) return;
        var pose = event.getPoseStack();
        pose.translate(0, -0.35f * k, -0.25f * k);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-35f * k));
    }

    /** Desenha o portal e a bateria da recarga no mundo. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        ClientRecharge.render(event.getPoseStack(), buffer, event.getCamera().getPosition(), event.getPartialTick());
        buffer.endBatch();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientRingData.reset();
        ClientCutscene.reset();
        ClientVisibleRings.clear();
        ClientFlight.clear();
        UniformTransition.clear();
        ClientRecharge.clear();
    }

    private ClientForgeEvents() {}
}
