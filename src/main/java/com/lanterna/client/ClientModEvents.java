package com.lanterna.client;

import com.lanterna.LanternaMod;
import com.lanterna.block.PowerBatteryBlockEntity;
import com.lanterna.item.PowerBatteryItem;
import com.lanterna.item.PowerRingItem;
import com.lanterna.registry.ModBlocks;
import com.lanterna.registry.ModItems;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import com.lanterna.client.screen.LanternScreen;
import com.lanterna.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import com.lanterna.client.space.SpaceSkyEffects;
import com.lanterna.client.uniform.UniformLayer;
import com.lanterna.client.uniform.UniformModels;
import com.lanterna.client.uniform.UniformTextures;
import net.minecraft.client.model.PlayerModel;
import com.lanterna.client.flight.GlowOrbParticle;
import com.lanterna.registry.ModParticles;

/** Eventos de cliente no barramento do mod (registro). */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.TOGGLE_FLIGHT);
        event.register(KeyBindings.POCKET_RECHARGE);
        event.register(KeyBindings.OPEN_LANTERN);
        event.register(KeyBindings.TOGGLE_POWER);
    }

    /** Liga a tela da aba "Lanterna". */
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenus.LANTERN.get(), LanternScreen::new));
        UniformTextures.createFolder();
    }

    /** Adiciona o anel na mão em todos os modelos de jogador (Steve e Alex). */
    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                // Uniforme primeiro, anel por cima dele
                renderer.addLayer(new UniformLayer(renderer,
                        new PlayerModel<>(event.getEntityModels().bakeLayer(UniformModels.WIDE), false),
                        new PlayerModel<>(event.getEntityModels().bakeLayer(UniformModels.SLIM), true)));
                renderer.addLayer(new RingHandLayer(renderer));
            }
        }
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(UniformModels.WIDE, UniformModels::wide);
        event.registerLayerDefinition(UniformModels.SLIM, UniformModels::slim);
    }

    /** Céu do espaço (estrelas, Terra, planetas, Sol). */
    @SubscribeEvent
    public static void registerSkies(RegisterDimensionSpecialEffectsEvent event) {
        event.register(LanternaMod.id("space"), new SpaceSkyEffects());
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.GLOW_ORB.get(), GlowOrbParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("ring_hud", RingHudOverlay.HUD);
        event.registerAboveAll("selection_cutscene", CutsceneOverlay.OVERLAY);
        event.registerAboveAll("space_nav", com.lanterna.client.space.SpaceHud.HUD);
    }

    /** Pinta a pedra do anel (camada 1) e o núcleo da bateria (tint 0) na cor da Tropa. */
    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 1 ? PowerRingItem.getColor(stack) : -1,
                ModItems.POWER_RING.get());
        event.register((stack, tintIndex) -> tintIndex == 0 ? PowerBatteryItem.getColor(stack) : -1,
                ModItems.POWER_BATTERY.get());
    }

    @SubscribeEvent
    public static void blockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0 || level == null || pos == null) return -1;
            if (level.getBlockEntity(pos) instanceof PowerBatteryBlockEntity battery) {
                return battery.getColor();
            }
            return -1;
        }, ModBlocks.POWER_BATTERY.get());
    }

    private ClientModEvents() {}
}
