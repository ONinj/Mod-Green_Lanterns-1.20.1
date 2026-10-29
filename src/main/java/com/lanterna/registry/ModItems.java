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

    // ---- Materiais dos planetas (moeda das trocas com os alienígenas; base de receitas futuras) ----
    public static final RegistryObject<Item> OA_CRYSTAL = gem("oa_crystal", Rarity.RARE);
    public static final RegistryObject<Item> KORUGAR_AMBER = gem("korugar_amber", Rarity.RARE);
    public static final RegistryObject<Item> YSMAULT_BLOODSTONE = gem("ysmault_bloodstone", Rarity.RARE);
    public static final RegistryObject<Item> ODYM_FROST = gem("odym_frost", Rarity.RARE);
    public static final RegistryObject<Item> ZAMARON_HEARTSTONE = gem("zamaron_heartstone", Rarity.RARE);
    public static final RegistryObject<Item> NOK_INDIGO = gem("nok_indigo", Rarity.RARE);
    public static final RegistryObject<Item> OKAARA_GOLD = gem("okaara_gold", Rarity.RARE);
    public static final RegistryObject<Item> RYUT_OBSIDIAN = gem("ryut_obsidian", Rarity.RARE);
    public static final RegistryObject<Item> ALIEN_ALLOY = gem("alien_alloy", Rarity.UNCOMMON);

    public static final RegistryObject<Item> ALIEN_SPAWN_EGG = ITEMS.register("alien_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.ALIEN, 0x5CFF7A, 0x1E3A2A, new Item.Properties()));

    private static RegistryObject<Item> gem(String name, Rarity rarity) {
        return ITEMS.register(name, () -> new com.lanterna.item.PlanetMaterialItem(new Item.Properties().rarity(rarity)));
    }

    private ModItems() {}
}
