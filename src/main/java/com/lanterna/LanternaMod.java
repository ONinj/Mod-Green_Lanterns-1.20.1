package com.lanterna;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.network.ModNetwork;
import com.lanterna.registry.ModBlockEntities;
import com.lanterna.registry.ModBlocks;
import com.lanterna.registry.ModCreativeTabs;
import com.lanterna.registry.ModItems;
import com.lanterna.registry.ModMenus;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(LanternaMod.MODID)
public class LanternaMod {
    public static final String MODID = "lanterna";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LanternaMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModMenus.MENUS.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(ModCapabilities::register);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, LanternaConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
