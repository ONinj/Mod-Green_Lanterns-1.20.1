package com.lanterna.item;

import com.lanterna.corps.BuiltinCorps;
import com.lanterna.corps.CorpsDefinition;
import com.lanterna.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Item da bateria. A Tropa vai em BlockEntityTag, então ao colocar o bloco
 * o Minecraft já passa a Tropa para a bateria sozinho.
 */
public class PowerBatteryItem extends BlockItem {

    public PowerBatteryItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack create(ResourceLocation corps, int color) {
        ItemStack stack = new ItemStack(ModItems.POWER_BATTERY.get());
        CompoundTag beTag = new CompoundTag();
        beTag.putString("Corps", corps.toString());
        beTag.putInt("Color", color);
        stack.getOrCreateTag().put("BlockEntityTag", beTag);
        return stack;
    }

    public static ResourceLocation getCorps(ItemStack stack) {
        CompoundTag be = stack.getTagElement("BlockEntityTag");
        if (be != null && be.contains("Corps")) {
            ResourceLocation id = ResourceLocation.tryParse(be.getString("Corps"));
            if (id != null) return id;
        }
        return BuiltinCorps.GREEN.id();
    }

    public static int getColor(ItemStack stack) {
        CompoundTag be = stack.getTagElement("BlockEntityTag");
        if (be != null && be.contains("Color")) return be.getInt("Color");
        return BuiltinCorps.colorOf(getCorps(stack));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack),
                Component.translatable(CorpsDefinition.nameKey(getCorps(stack))));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int color = getColor(stack);
        tooltip.add(Component.translatable(CorpsDefinition.fullNameKey(getCorps(stack)))
                .withStyle(s -> s.withColor(color)));
        tooltip.add(Component.translatable("tooltip.lanterna.battery_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
