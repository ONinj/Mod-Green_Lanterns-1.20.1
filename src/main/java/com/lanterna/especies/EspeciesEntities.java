package com.lanterna.especies;

import com.lanterna.LanternaMod;
import com.lanterna.registry.ModEntities;
import com.lanterna.registry.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/** Registro da entidade humanoide (todas as espécies, Lanternas, piratas e mercadores). */
public final class EspeciesEntities {

    public static final RegistryObject<EntityType<LanternaEntity>> LANTERNA_NPC = ModEntities.ENTITIES.register("lanterna_npc",
            () -> EntityType.Builder.<LanternaEntity>of(LanternaEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 2.0f).clientTrackingRange(10).build(LanternaMod.id("lanterna_npc").toString()));

    public static final RegistryObject<Item> OVO = ModItems.ITEMS.register("lanterna_npc_spawn_egg",
            () -> new ForgeSpawnEggItem(LANTERNA_NPC, 0x2A3A30, 0x2BFF4A, new Item.Properties()));

    public static void init() {}

    @Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void attributes(EntityAttributeCreationEvent event) {
            event.put(LANTERNA_NPC.get(), LanternaEntity.attributes().build());
        }
    }

    private EspeciesEntities() {}
}
