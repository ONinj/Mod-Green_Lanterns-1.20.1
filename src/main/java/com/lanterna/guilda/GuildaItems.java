package com.lanterna.guilda;

import com.lanterna.registry.ModBlocks;
import com.lanterna.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Bandeira da Guilda, Essência de Poder e Anel de Recruta. */
public final class GuildaItems {
    public static final RegistryObject<Block> BANDEIRA = ModBlocks.BLOCKS.register("bandeira_guilda",
            () -> new BandeiraBlock(BlockBehaviour.Properties.of().mapColor(MapColor.EMERALD).strength(-1f, 3_600_000f)
                    .sound(SoundType.METAL).lightLevel(s -> 12).noOcclusion().noLootTable()));
    public static final RegistryObject<Item> BANDEIRA_ITEM = ModItems.ITEMS.register("bandeira_guilda",
            () -> new BlockItem(BANDEIRA.get(), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)) {
                @Override
                public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> tip, TooltipFlag f) {
                    tip.add(Component.translatable("item.lanterna.bandeira_guilda.desc").withStyle(ChatFormatting.GRAY));
                }
            });
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<BandeiraBlock.Entidade>> BANDEIRA_BE =
            com.lanterna.registry.ModBlockEntities.BLOCK_ENTITIES.register("bandeira_guilda",
                    () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(BandeiraBlock.Entidade::new, BANDEIRA.get()).build(null));
    public static final RegistryObject<Item> ESSENCIA = ModItems.ITEMS.register("essencia_poder", () -> new Desc(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> ANEL_RECRUTA = ModItems.ITEMS.register("anel_recruta", () -> new Desc(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));

    public static class Desc extends Item {
        public Desc(Properties p) {
            super(p);
        }

        @Override
        public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> tip, TooltipFlag f) {
            tip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        }
    }

    public static void init() {}

    private GuildaItems() {}
}
