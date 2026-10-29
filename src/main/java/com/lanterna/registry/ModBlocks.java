package com.lanterna.registry;

import com.lanterna.LanternaMod;
import com.lanterna.block.PowerBatteryBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, LanternaMod.MODID);

    public static final RegistryObject<Block> POWER_BATTERY = BLOCKS.register("power_battery",
            () -> new PowerBatteryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 1200.0f)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 12)
                    .noOcclusion()));

    /** Bateria Cinzelada: acorda o Anel Apagado e se parte. */
    public static final RegistryObject<Block> CHISELED_BATTERY = BLOCKS.register("bateria_cinzelada",
            () -> new com.lanterna.block.ChiseledBatteryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.STONE)
                    .lightLevel(state -> 3)
                    .noOcclusion()));

    private ModBlocks() {}
}
