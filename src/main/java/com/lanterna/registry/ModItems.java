package com.lanterna.registry;

import com.lanterna.LanternaMod;
import com.lanterna.item.PowerBatteryItem;
import com.lanterna.item.PowerRingItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, LanternaMod.MODID);

    public static final RegistryObject<Item> POWER_RING = ITEMS.register("power_ring",
            () -> new PowerRingItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final RegistryObject<Item> POWER_BATTERY = ITEMS.register("power_battery",
            () -> new PowerBatteryItem(ModBlocks.POWER_BATTERY.get(),
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant()));

    private ModItems() {}
}
