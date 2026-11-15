package com.lanterna.registry;

import com.lanterna.LanternaMod;
import com.lanterna.block.PowerBatteryBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, LanternaMod.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<PowerBatteryBlockEntity>> POWER_BATTERY =
            BLOCK_ENTITIES.register("power_battery",
                    () -> BlockEntityType.Builder.of(PowerBatteryBlockEntity::new, ModBlocks.POWER_BATTERY.get())
                            .build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.lanterna.block.CapsulaEstelarBlockEntity>> CAPSULA_ESTELAR =
            BLOCK_ENTITIES.register("capsula_estelar",
                    () -> BlockEntityType.Builder.of(com.lanterna.block.CapsulaEstelarBlockEntity::new, ModBlocks.CAPSULA_ESTELAR.get()).build(null));

    private ModBlockEntities() {}
}
