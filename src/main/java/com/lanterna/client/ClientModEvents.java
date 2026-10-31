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
        event.register(KeyBindings.STAR_MAP);
        event.register(KeyBindings.CONSTRUCT_WHEEL);
        event.register(KeyBindings.CONSTRUCT_USE);
        for (var k : KeyBindings.PRESETS) event.register(k);
        event.register(KeyBindings.JOURNAL);
        event.register(KeyBindings.NAVE_DESCER);
        event.register(KeyBindings.NAVE_PORTA);
        event.register(KeyBindings.NAVE_HANGAR);
        event.register(KeyBindings.TRAVAR_MIRA);
        event.register(KeyBindings.TECNICAS);
        for (var k : KeyBindings.TECNICA) event.register(k);
    }

    /** Liga a tela da aba "Lanterna". */
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        com.lanterna.client.naves.ClientNaves.init();
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.LANTERN.get(), LanternScreen::new);
            // Modelo do anel e da bateria de cada Tropa (overrides "lanterna:corps" nos modelos de item)
            net.minecraft.client.renderer.item.ItemProperties.register(ModItems.POWER_RING.get(), com.lanterna.LanternaMod.id("corps"),
                    (stack, level, entity, seed) -> corpsIndex(com.lanterna.item.PowerRingItem.getCorps(stack)));
            net.minecraft.client.renderer.item.ItemProperties.register(ModItems.POWER_BATTERY.get(), com.lanterna.LanternaMod.id("corps"),
                    (stack, level, entity, seed) -> corpsIndex(PowerBatteryItem.getCorps(stack)));
        });
        UniformTextures.createFolder();
    }

    /** 1 = verde, 2 = amarelo ... 9 = branco (ordem de BuiltinCorps). */
    private static float corpsIndex(net.minecraft.resources.ResourceLocation corps) {
        var values = com.lanterna.corps.BuiltinCorps.values();
        for (int i = 0; i < values.length; i++) if (values[i].id().equals(corps)) return i + 1;
        return 1;
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
                renderer.addLayer(new StaffLayer(renderer));
            }
        }
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.lanterna.registry.ModEntities.ALIEN.get(), com.lanterna.client.alien.AlienRenderer::new);
        com.lanterna.client.lore.LoreRenderers.register(event);
        event.registerEntityRenderer(com.lanterna.especies.EspeciesEntities.LANTERNA_NPC.get(),
                ctx -> new com.lanterna.especies.client.EspecieRenderer<>(ctx, LanternaMod.MODID) {
                    @Override
                    protected float getDeathMaxRotation(com.lanterna.especies.LanternaEntity e) {
                        return 0f; // a animação misc.death já derruba o corpo
                    }
                });
        event.registerEntityRenderer(com.lanterna.naves.NaveEntities.NAVE.get(),
                ctx -> new com.lanterna.naves.client.NaveRenderer<>(ctx, LanternaMod.MODID));
        event.registerEntityRenderer(com.lanterna.tropa.TropaEntities.TIRO_PLASMA.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(com.lanterna.naves.NaveEntities.ASSENTO.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerBlockEntityRenderer(com.lanterna.guilda.GuildaItems.BANDEIRA_BE.get(), com.lanterna.client.guilda.BandeiraRenderer::new);
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

    /** Peças 3D do uniforme feitas no Blockbench (assets/lanterna/models/uniform/...). */
    @SubscribeEvent
    public static void registerModels(net.minecraftforge.client.event.ModelEvent.RegisterAdditional event) {
        com.lanterna.client.uniform.UniformPieces.register(event);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.GLOW_ORB.get(), GlowOrbParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("pavor", com.lanterna.client.construct.PavorTela.HUD);
        event.registerAboveAll("ring_hud", RingHudOverlay.HUD);
        event.registerAboveAll("mira_travada", com.lanterna.client.combat.MiraTravada.HUD);
        event.registerAboveAll("tecnicas", com.lanterna.client.cidade.ClientTecnicas.HUD);
        event.registerAboveAll("selection_cutscene", CutsceneOverlay.OVERLAY);
        event.registerAboveAll("space_nav", com.lanterna.client.space.SpaceHud.HUD);
        event.registerAboveAll("guilda_planeta", com.lanterna.client.guilda.GuildaHud.HUD);
        event.registerAboveAll("flight_hud", com.lanterna.client.flight.FlightHud.HUD);
        event.registerAboveAll("mission_hud", com.lanterna.client.mission.MissionHud.HUD);
        event.registerAboveAll("construct_bar", com.lanterna.client.construct.ConstructBarHud.HUD);
        event.registerAboveAll("xp_ganho", com.lanterna.client.tropa.TropaHud.HUD);
    }

    /** Pinta a pedra do anel (camada 1) e o núcleo da bateria (tint 0) na cor da Tropa. */
    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex >= 0 ? PowerRingItem.getColor(stack) : -1,
                ModItems.POWER_RING.get());
        event.register((stack, tintIndex) -> tintIndex >= 0 ? PowerBatteryItem.getColor(stack) : -1,
                ModItems.POWER_BATTERY.get());
        // Itens dos blocos das naves (faixas no tintindex 0) e chave/insígnia (camada 1) na cor da Tropa
        for (var b : com.lanterna.naves.bloco.NaveBlocks.TINGIDOS) {
            event.register((stack, tint) -> tint == 0 ? 0x46E66E : -1, b.get().asItem());
        }
        event.register((stack, tint) -> tint == 1 ? com.lanterna.tropa.TropaItems.corDaTropa(stack) : -1,
                com.lanterna.tropa.TropaItems.CHAVE_NAVE.get(), com.lanterna.tropa.TropaItems.INSIGNIA_GUARDA_HONRA.get());
    }

    @SubscribeEvent
    public static void blockColors(RegisterColorHandlersEvent.Block event) {
        for (var b : com.lanterna.naves.bloco.NaveBlocks.TINGIDOS) {
            event.register((state, level, pos, tint) -> tint == 0 ? com.lanterna.client.naves.ClientNaves.corBloco(pos) : -1, b.get());
        }
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex < 0 || level == null || pos == null) return -1;
            if (level.getBlockEntity(pos) instanceof PowerBatteryBlockEntity battery) {
                return battery.getColor();
            }
            return -1;
        }, ModBlocks.POWER_BATTERY.get());
        // Blocos dos construtos: cor da Tropa de quem fez
        event.register((state, level, pos, tintIndex) -> {
            if (level != null && pos != null && level.getBlockEntity(pos) instanceof com.lanterna.construct.ConstructBlock.Entity be) {
                return be.color();
            }
            return 0x2BFF4A;
        }, com.lanterna.construct.ConstructRegistry.CONSTRUCT_BLOCK.get());
    }

    private ClientModEvents() {}
}
