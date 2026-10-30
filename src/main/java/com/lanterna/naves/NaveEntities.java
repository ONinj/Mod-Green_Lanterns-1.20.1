package com.lanterna.naves;

import com.lanterna.LanternaMod;
import com.lanterna.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.RegistryObject;

public final class NaveEntities {
    public static final RegistryObject<EntityType<NaveEntity>> NAVE = ModEntities.ENTITIES.register("nave",
            () -> EntityType.Builder.<NaveEntity>of(NaveEntity::new, MobCategory.MISC)
                    .sized(3f, 2.4f).fireImmune().clientTrackingRange(16).updateInterval(1)
                    .setShouldReceiveVelocityUpdates(true).build(LanternaMod.id("nave").toString()));

    public static void init() {
        Object o = NaveItem.ITEMS; // carrega os itens das naves
    }

    private NaveEntities() {}
}
