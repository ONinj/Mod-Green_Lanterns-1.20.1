package com.lanterna.item;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.corps.CorpsDefinition;
import com.lanterna.corps.CorpsManager;
import com.lanterna.power.RingPowers;
import com.lanterna.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Um único item para todos os anéis. A Tropa e a cor ficam no NBT:
 *   Corps  = "lanterna:green"
 *   Color  = 0x2BFF4A  (pinta a pedra do anel)
 *   Charge = carga guardada (-1 = cheia)
 *   Owner  = UUID do portador (o anel só aceita quem escolheu)
 */
public class PowerRingItem extends Item {

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

    // ===================== Uso =====================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        RingData data = ModCapabilities.get(player).orElse(null);
        if (data == null) return InteractionResultHolder.fail(stack);

        if (data.hasRing()) {
            player.displayClientMessage(Component.translatable("message.lanterna.already_wearing"), true);
            return InteractionResultHolder.fail(stack);
        }

        UUID owner = getOwner(stack);
        if (owner != null && !owner.equals(player.getUUID()) && !player.isCreative()) {
            player.displayClientMessage(Component.translatable("message.lanterna.not_owner"), true);
            return InteractionResultHolder.fail(stack);
        }

        CorpsDefinition def = CorpsManager.get(getCorps(stack));
        RingPowers.equip(serverPlayer, def, getStoredCharge(stack));

        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.2f);
        player.displayClientMessage(Component.translatable("message.lanterna.equipped",
                Component.translatable(CorpsDefinition.nameKey(def.id()))).withStyle(s -> s.withColor(def.color())), true);

        stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    // ===================== Aparência =====================

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack),
                Component.translatable(CorpsDefinition.nameKey(getCorps(stack))));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
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
