package com.lanterna.item;

import com.lanterna.corps.BuiltinCorps;
import com.lanterna.corps.CorpsDefinition;
import com.lanterna.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.UUID;

/**
 * Um único item para todos os anéis. Vai no slot "ring" do Curios.
 * Dados no NBT:
 *   Corps  = "lanterna:green"
 *   Color  = 0x2BFF4A  (pinta a pedra do anel)
 *   Charge = carga atual (-1 = cheia, anel novo)
 *   Owner  = UUID do portador (o anel só aceita quem escolheu)
 */
public class PowerRingItem extends Item implements ICurioItem {

    public PowerRingItem(Properties properties) {
        super(properties);
    }

    // ===================== NBT =====================

    public static ItemStack create(ResourceLocation corps, int color, int charge) {
        ItemStack stack = new ItemStack(ModItems.POWER_RING.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("Corps", corps.toString());
        tag.putInt("Color", color);
        tag.putInt("Charge", charge);
        return stack;
    }

    public static ResourceLocation getCorps(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Corps")) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("Corps"));
            if (id != null) return id;
        }
        return BuiltinCorps.GREEN.id();
    }

    public static int getColor(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Color")) return tag.getInt("Color");
        return BuiltinCorps.colorOf(getCorps(stack));
    }

    public static int getStoredCharge(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("Charge") ? tag.getInt("Charge") : -1;
    }

    public static void setStoredCharge(ItemStack stack, int charge) {
        stack.getOrCreateTag().putInt("Charge", charge);
    }

    public static void setOwner(ItemStack stack, Player player) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID("Owner", player.getUUID());
        tag.putString("OwnerName", player.getGameProfile().getName());
    }

    @Nullable
    public static UUID getOwner(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    // ===================== Curios =====================

    /** Só entra no slot de quem o anel escolheu (ou de quem está no criativo). */
    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return false;
        UUID owner = getOwner(stack);
        return owner == null || owner.equals(player.getUUID()) || player.isCreative();
    }

    /** Clique direito com o anel na mão coloca direto no slot. */
    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player && !player.level().isClientSide) {
            if (getOwner(stack) == null) setOwner(stack, player);
            if (!ItemStack.isSameItemSameTags(prevStack, stack)) {
                player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                        SoundSource.PLAYERS, 0.8f, 1.3f);
            }
        }
    }

    /** O anel nunca cai quando o portador morre. */
    @Override
    public ICurio.DropRule getDropRule(SlotContext slotContext, DamageSource source, int lootingLevel,
                                       boolean recentlyHit, ItemStack stack) {
        return ICurio.DropRule.ALWAYS_KEEP;
    }

    // ===================== Aparência =====================

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

        int charge = getStoredCharge(stack);
        if (charge >= 0) {
            tooltip.add(Component.translatable("tooltip.lanterna.charge", charge).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.lanterna.charge_full").withStyle(ChatFormatting.GRAY));
        }

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("OwnerName")) {
            tooltip.add(Component.translatable("tooltip.lanterna.owner", tag.getString("OwnerName"))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.lanterna.ring_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
