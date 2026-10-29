package com.lanterna.item;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.corps.CorpsDefinition;
import com.lanterna.corps.CorpsManager;
import com.lanterna.power.RingPowers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Cajado da Compaixão: a "bateria" da Tropa Índigo. É um item (não um bloco).
 * No slot de bateria ele aparece na mão esquerda do personagem e recarrega o anel com a tecla R.
 * Segurando na mão, clique direito recarrega o anel índigo.
 */
public class CompassionStaffItem extends Item {
    public CompassionStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.success(stack);
        RingData data = ModCapabilities.get(player).orElse(null);
        if (data == null || !data.hasRing()) {
            player.displayClientMessage(Component.translatable("message.lanterna.battery_no_ring"), true);
            return InteractionResultHolder.fail(stack);
        }
        CorpsDefinition def = CorpsManager.get(BuiltinCorps.INDIGO.id());
        if (!RingPowers.recharge(serverPlayer, data, def)) {
            player.displayClientMessage(Component.translatable("message.lanterna.battery_wrong_corps"), true);
            return InteractionResultHolder.fail(stack);
        }
        RingPowers.playRechargeEffects(serverPlayer, def);
        player.getCooldowns().addCooldown(this, 20 * 10);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int color = BuiltinCorps.INDIGO.color();
        tooltip.add(Component.translatable(CorpsDefinition.fullNameKey(BuiltinCorps.INDIGO.id())).withStyle(s -> s.withColor(color)));
        tooltip.add(Component.translatable("tooltip.lanterna.staff_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
