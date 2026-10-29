package com.lanterna.registry;

import com.lanterna.LanternaMod;
import com.lanterna.alien.AlienEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, LanternaMod.MODID);

    /** Habitantes dos planetas (uma espécie por planeta). */
    public static final RegistryObject<EntityType<AlienEntity>> ALIEN = ENTITIES.register("alien",
            () -> EntityType.Builder.<AlienEntity>of(AlienEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.9f)
                    .clientTrackingRange(10)
                    .build(LanternaMod.id("alien").toString()));

    private ModEntities() {}
}
