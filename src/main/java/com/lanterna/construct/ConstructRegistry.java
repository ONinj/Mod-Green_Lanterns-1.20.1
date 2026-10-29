package com.lanterna.construct;

import com.lanterna.LanternaMod;
import com.lanterna.registry.ModBlockEntities;
import com.lanterna.registry.ModBlocks;
import com.lanterna.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.RegistryObject;

/** Registro do bloco de energia e da entidade dos construtos. */
public final class ConstructRegistry {

    public static final RegistryObject<Block> CONSTRUCT_BLOCK = ModBlocks.BLOCKS.register("construct_block",
            () -> new ConstructBlock(BlockBehaviour.Properties.of().mapColor(MapColor.EMERALD).strength(-1f, 3600000f)
                    .noLootTable().noOcclusion().lightLevel(s -> 9).sound(SoundType.AMETHYST)
                    .pushReaction(PushReaction.BLOCK).isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<ConstructBlock.Entity>> CONSTRUCT_BLOCK_ENTITY =
            ModBlockEntities.BLOCK_ENTITIES.register("construct_block",
                    () -> BlockEntityType.Builder.of(ConstructBlock.Entity::new, CONSTRUCT_BLOCK.get()).build(null));

    public static final RegistryObject<EntityType<ConstructEntity>> CONSTRUCT = ModEntities.ENTITIES.register("construct",
            () -> EntityType.Builder.<ConstructEntity>of(ConstructEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(10).updateInterval(1).noSummon().fireImmune()
                    .setShouldReceiveVelocityUpdates(true)
                    .build(LanternaMod.id("construct").toString()));

    public static void init() {}

    private ConstructRegistry() {}
}
