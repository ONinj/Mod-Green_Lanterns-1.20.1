package com.lanterna.construct.oficina;

import com.lanterna.LanternaMod;
import com.lanterna.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.RegistryObject;

public final class OficinaRegistro {
    public static final RegistryObject<EntityType<ConstrutoProprio>> CONSTRUTO = ModEntities.ENTITIES.register("construto_proprio",
            () -> EntityType.Builder.<ConstrutoProprio>of(ConstrutoProprio::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f).clientTrackingRange(10).updateInterval(1).noSummon().fireImmune()
                    .setShouldReceiveVelocityUpdates(true)
                    .build(LanternaMod.id("construto_proprio").toString()));

    public static void init() {}

    private OficinaRegistro() {}
}
