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

    /** Cajado da Compaixão: a "bateria" da Tropa Índigo (item, não bloco). */
    public static final RegistryObject<Item> COMPASSION_STAFF = ITEMS.register("compassion_staff",
            () -> new com.lanterna.item.CompassionStaffItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant()));

    // ---- Liga dos alienígenas dos planetas gerados ----
    public static final RegistryObject<Item> ALIEN_ALLOY = gem("alien_alloy", Rarity.UNCOMMON);

    public static final RegistryObject<Item> ALIEN_SPAWN_EGG = ITEMS.register("alien_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.ALIEN, 0x5CFF7A, 0x1E3A2A, new Item.Properties()));

    private static RegistryObject<Item> gem(String name, Rarity rarity) {
        return ITEMS.register(name, () -> new com.lanterna.item.PlanetMaterialItem(new Item.Properties().rarity(rarity)));
    }

    private ModItems() {}
}
