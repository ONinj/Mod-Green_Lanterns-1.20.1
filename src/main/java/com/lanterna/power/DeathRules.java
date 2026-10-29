package com.lanterna.power;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.item.PowerRingItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * O que acontece com o anel e a bateria quando o portador morre:
 *  - a bateria sempre fica onde ele morreu (cai no chão, não some com o tempo);
 *  - se o anel tem 50% de carga ou mais, ele volta sozinho para a mão do portador,
 *    gastando 25% da carga (sobra o bastante para voltar e buscar a bateria);
 *  - com menos de 50%, o anel não tem força para voltar: cai junto com a bateria.
 * O anel Branco não descarrega e sempre volta. Com a regra keepInventory ligada, nada cai.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DeathRules {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;
        RingData data = ModCapabilities.get(player).orElse(null);
        if (data == null) return;

        ItemStack battery = EquipmentHelper.take(player, RingData.BATTERY_SLOT);
        if (!battery.isEmpty()) {
            drop(event, player, battery);
            player.sendSystemMessage(Component.translatable("message.lanterna.death_battery",
                    player.getBlockX(), player.getBlockY(), player.getBlockZ(),
                    place(player).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
        }

        ItemStack ring = EquipmentHelper.ring(player);
        if (ring.isEmpty()) return;
        int color = PowerRingItem.getColor(ring);
        boolean white = PowerRingItem.getCorps(ring).equals(BuiltinCorps.WHITE.id());
        int max = Math.max(1, data.maxCharge());
        int charge = PowerRingItem.getStoredCharge(ring);
        if (charge < 0) charge = max;
        if (white || charge * 2 >= max) {
            // Volta sozinho: gasta 25% da carga
            int left = white ? max : Math.max(1, charge - max / 4);
            PowerRingItem.setStoredCharge(ring, left);
            data.setCharge(left);
            int pct = Math.round(100f * left / max);
            player.sendSystemMessage(Component.translatable("message.lanterna.death_ring_returns", pct)
                    .withStyle(s -> s.withColor(color).withItalic(true)));
        } else {
            drop(event, player, EquipmentHelper.take(player, RingData.RING_SLOT));
            player.sendSystemMessage(Component.translatable("message.lanterna.death_ring_lost")
                    .withStyle(s -> s.withColor(color).withItalic(true)));
        }
    }

    private static net.minecraft.network.chat.MutableComponent place(ServerPlayer player) {
        String path = player.level().dimension().location().getPath();
        if (path.equals("overworld")) path = "earth";
        return Component.translatableWithFallback("planet.lanterna." + path, path);
    }

    /** Larga o item no lugar da morte, sem sumir com o tempo e sem queimar. */
    private static void drop(LivingDropsEvent event, ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
        item.setUnlimitedLifetime();
        item.setInvulnerable(true);
        item.setDefaultPickUpDelay();
        event.getDrops().add(item);
    }

    private DeathRules() {}
}
