package com.lanterna.power;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.CorpsDefinition;
import com.lanterna.corps.CorpsManager;
import com.lanterna.item.PowerRingItem;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.SyncRingDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;

/**
 * Toda a lógica de servidor do anel. As fases seguintes (rajada, escudo,
 * constructos) entram aqui, sempre gastando carga pelo mesmo caminho.
 */
public final class RingPowers {

    /** Coloca o anel no dedo do jogador. */
    public static void equip(ServerPlayer player, CorpsDefinition def, int charge) {
        ModCapabilities.get(player).ifPresent(data -> {
            data.equip(def.id(), def.color(), charge < 0 ? def.maxCharge() : charge, def.maxCharge());
            data.setEverChosen(true);
            applyFlightAbility(player, data);
            sync(player, data);
        });
    }

    /** Tira o anel e devolve o item, com a carga e o dono preservados. */
    public static ItemStack removeRing(ServerPlayer player) {
        RingData data = ModCapabilities.get(player).orElse(null);
        if (data == null || !data.hasRing()) return ItemStack.EMPTY;

        ItemStack stack = PowerRingItem.create(data.corps(), data.color(), data.charge());
        PowerRingItem.setOwner(stack, player);
        data.unequip();
        applyFlightAbility(player, data);
        sync(player, data);
        return stack;
    }

    /** Recarga total na bateria. Devolve false se a Tropa não bate. */
    public static boolean recharge(ServerPlayer player, RingData data, CorpsDefinition batteryCorps) {
        if (!data.hasRing() || !data.corps().equals(batteryCorps.id())) return false;
        data.setMaxCharge(batteryCorps.maxCharge());
        data.setCharge(data.maxCharge());
        sync(player, data);
        return true;
    }

    // ===================== Voo =====================

    public static void toggleFlight(ServerPlayer player) {
        RingData data = ModCapabilities.get(player).orElse(null);
        if (data == null || !data.hasRing()) return;

        if (!data.flightEnabled() && data.charge() <= 0) {
            player.displayClientMessage(Component.translatable("message.lanterna.no_charge"), true);
            return;
        }

        boolean enable = !data.flightEnabled();
        data.setFlightEnabled(enable);
        applyFlightAbility(player, data);

        if (enable) {
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }

        player.displayClientMessage(Component.translatable(
                enable ? "message.lanterna.flight_on" : "message.lanterna.flight_off"), true);
        player.level().playSound(null, player.blockPosition(),
                enable ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, 0.6f, 1.6f);
        sync(player, data);
    }

    /** Liga ou desliga a permissão de voar sem estragar o modo criativo. */
    public static void applyFlightAbility(ServerPlayer player, RingData data) {
        Abilities abilities = player.getAbilities();
        boolean canFlyNaturally = player.isCreative() || player.isSpectator();
        boolean shouldFly = canFlyNaturally || (data.hasRing() && data.flightEnabled());

        if (abilities.mayfly != shouldFly) {
            abilities.mayfly = shouldFly;
            if (!shouldFly) abilities.flying = false;
            player.onUpdateAbilities();
        }
    }

    /** Roda a cada tick no servidor. */
    public static void tick(ServerPlayer player, RingData data) {
        if (!data.hasRing()) return;

        if (data.flightEnabled()) {
            // Outro mod ou troca de modo de jogo pode ter tirado o voo
            if (!player.getAbilities().mayfly) applyFlightAbility(player, data);

            if (player.getAbilities().flying && !player.isCreative() && !player.isSpectator()) {
                if (data.nextFlightTick() % 20 == 0) {
                    CorpsDefinition def = CorpsManager.get(data.corps());
                    data.addCharge(-def.flightDrain());
                    if (data.charge() <= 0) {
                        data.setFlightEnabled(false);
                        applyFlightAbility(player, data);
                        player.displayClientMessage(Component.translatable("message.lanterna.charge_depleted"), true);
                        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE,
                                SoundSource.PLAYERS, 1.0f, 0.7f);
                    }
                }
            }
        }

        if (data.isDirty()) sync(player, data);
    }

    // ===================== Sincronização =====================

    public static void sync(ServerPlayer player) {
        ModCapabilities.get(player).ifPresent(data -> sync(player, data));
    }

    public static void sync(ServerPlayer player, RingData data) {
        ModNetwork.sendTo(player, SyncRingDataPacket.from(data));
        data.clearDirty();
    }

    private RingPowers() {}
}
