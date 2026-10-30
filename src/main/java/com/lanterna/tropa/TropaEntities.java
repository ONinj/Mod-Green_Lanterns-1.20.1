package com.lanterna.tropa;

import com.lanterna.LanternaMod;
import com.lanterna.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.RegistryObject;

public final class TropaEntities {
    public static final RegistryObject<EntityType<TiroPlasma>> TIRO_PLASMA = ModEntities.ENTITIES.register("tiro_plasma",
            () -> EntityType.Builder.<TiroPlasma>of(TiroPlasma::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f).clientTrackingRange(8).updateInterval(2).build(LanternaMod.id("tiro_plasma").toString()));

    public static void init() {}

    private TropaEntities() {}
}
