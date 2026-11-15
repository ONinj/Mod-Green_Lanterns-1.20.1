package com.lanterna.item;

import com.lanterna.capability.RingData;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.corps.CorpsDefinition;
import com.lanterna.power.EquipmentHelper;
import com.lanterna.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
 * Um único item para todos os anéis. Fica no slot de anel da aba "Lanterna".
 * Dados no NBT:
 *   Corps  = "lanterna:green"
 *   Color  = 0x2BFF4A  (pinta a pedra do anel)
 *   Charge = carga atual (-1 = cheia, anel novo)
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

    /**
     * Corrupção (0.49.1): 0 = anel limpo; 1 = corrompido pela morte (rachaduras brancas, manchas cinzas); 2 = o
     * portador foi convertido em Lanterna Negro (anel preto com rachaduras, símbolo da Tropa de origem).
     */
    public static int getCorrupcao(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.max(0, Math.min(2, tag.getInt("Corrupcao")));
    }

    /** A Tropa de antes da conversão (só no anel convertido), ou null. */
    @Nullable
    public static ResourceLocation getOrigem(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("Origem") ? ResourceLocation.tryParse(tag.getString("Origem")) : null;
    }

    public static void setCorrupcao(ItemStack stack, int nivel, @Nullable ResourceLocation origem) {
        CompoundTag tag = stack.getOrCreateTag();
        if (nivel <= 0) tag.remove("Corrupcao"); else tag.putInt("Corrupcao", nivel);
        if (nivel != 1) tag.remove("Estagio");
        if (origem == null) tag.remove("Origem"); else tag.putString("Origem", origem.toString());
    }

    /**
     * Estágio da corrupção que aparece (0.51): 0 limpo; 1 corrompido (rachaduras brancas, manchas cinzas); 2 corroído
     * (anel escuro, manchas pretas e da Tropa, mais rachaduras; traje mais cinza que colorido); 3 Portador Negro.
     */
    public static int getEstagio(ItemStack stack) {
        int c = getCorrupcao(stack);
        if (c == 2) return 3;
        if (c == 1) return stack.getTag() != null && stack.getTag().getInt("Estagio") == 2 ? 2 : 1;
        return 0;
    }

    /** Põe o estágio 0, 1 ou 2 (o 3 é a conversão em Portador Negro, ver Corrupcao.converter). */
    public static void setEstagio(ItemStack stack, int estagio, @Nullable ResourceLocation origem) {
        if (estagio >= 3) { setCorrupcao(stack, 2, origem); return; }
        setCorrupcao(stack, estagio <= 0 ? 0 : 1, origem);
        if (estagio == 2) stack.getOrCreateTag().putInt("Estagio", 2);
    }

    /** A Tropa cujo símbolo e traje aparecem: a de origem no anel convertido, senão a do anel. */
    public static ResourceLocation getCorpsVisual(ItemStack stack) {
        ResourceLocation o = getCorrupcao(stack) == 2 ? getOrigem(stack) : null;
        return o != null ? o : getCorps(stack);
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

    /** O anel só aceita quem ele escolheu (ou alguém no criativo). Anel sem dono aceita qualquer um. */
    public static boolean canBeWornBy(ItemStack stack, Player player) {
        UUID owner = getOwner(stack);
        return owner == null || owner.equals(player.getUUID()) || player.isCreative();
    }

    // ===================== Uso =====================

    /** Clique direito com o anel na mão: vai direto para o slot de anel. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);

        if (!canBeWornBy(stack, player)) {
            player.displayClientMessage(Component.translatable("message.lanterna.not_owner"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!EquipmentHelper.ring(player).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.lanterna.already_wearing"), true);
            return InteractionResultHolder.fail(stack);
        }

        ItemStack ring = stack.split(1);
        if (getOwner(ring) == null) setOwner(ring, player);
        EquipmentHelper.equip(player, RingData.RING_SLOT, ring);

        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 1.3f);
        int color = getColor(ring);
        player.displayClientMessage(Component.translatable("message.lanterna.equipped",
                Component.translatable(CorpsDefinition.nameKey(getCorps(ring)))).withStyle(s -> s.withColor(color)), true);
        return InteractionResultHolder.consume(stack);
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
