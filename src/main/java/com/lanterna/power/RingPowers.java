package com.lanterna.power;

import com.lanterna.LanternaConfig;
import com.lanterna.block.PowerBatteryBlock;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.CorpsDefinition;
import com.lanterna.corps.CorpsManager;
import com.lanterna.item.PowerBatteryItem;
import com.lanterna.item.PowerRingItem;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.SyncRingDataPacket;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotResult;

import java.util.Optional;

/**
 * Lógica de servidor do anel.
 *
 * O anel vive no slot "ring" do Curios, e a carga fica gravada no próprio item.
 * A cada tick o RingData do jogador é atualizado a partir do slot, então o
 * resto do mod (HUD, voo, bateria, seleção) só olha o RingData.
 */
public final class RingPowers {

    // ===================== Leitura do slot =====================

    public static Optional<ItemStack> wornRing(ServerPlayer player) {
        return CuriosHelper.findRing(player).map(SlotResult::stack);
    }

    /** Roda a cada tick no servidor. */
    public static void tick(ServerPlayer player, RingData data) {
        Optional<ItemStack> ringOpt = wornRing(player);

        if (ringOpt.isPresent()) {
            ItemStack ring = ringOpt.get();
            CorpsDefinition def = CorpsManager.get(PowerRingItem.getCorps(ring));

            int charge = PowerRingItem.getStoredCharge(ring);
            if (charge < 0 || charge > def.maxCharge()) {
                charge = def.maxCharge(); // -1 = anel novo, vem cheio
                PowerRingItem.setStoredCharge(ring, charge);
            }
            data.refresh(def.id(), def.color(), charge, def.maxCharge());
            data.setEverChosen(true);
        } else if (data.hasRing()) {
            data.unequip();
            applyFlightAbility(player, data);
        }

        if (data.hasRing()) {
            tickFlight(player, data);
            data.setPocketStatus(pocketStatus(player, data));
        } else {
            data.setPocketStatus(-1);
        }

        if (data.isDirty()) sync(player, data);
    }

    // ===================== Carga =====================

    /** Muda a carga gravando no anel do slot. */
    public static void setCharge(ServerPlayer player, RingData data, int value) {
        wornRing(player).ifPresent(ring -> {
            int clamped = Math.max(0, Math.min(value, data.maxCharge()));
            PowerRingItem.setStoredCharge(ring, clamped);
            data.setCharge(clamped);
        });
    }

    /** Recarga total. Devolve false se não tem anel ou se a Tropa não bate. */
    public static boolean recharge(ServerPlayer player, RingData data, CorpsDefinition batteryCorps) {
        if (!data.hasRing() || !data.corps().equals(batteryCorps.id())) return false;
        setCharge(player, data, data.maxCharge());
        sync(player, data);
        return true;
    }

    // ===================== Bateria de bolso =====================

    private static int cooldownTicks() {
        return LanternaConfig.POCKET_RECHARGE_COOLDOWN_SECONDS.get() * 20;
    }

    /** -1 sem bateria compatível no slot, 0 pronta, >0 segundos de espera. */
    private static int pocketStatus(ServerPlayer player, RingData data) {
        Optional<SlotResult> battery = CuriosHelper.findBattery(player);
        if (battery.isEmpty() || !PowerBatteryItem.getCorps(battery.get().stack()).equals(data.corps())) return -1;
        long elapsed = player.level().getGameTime() - data.lastPocketRecharge();
        long remaining = cooldownTicks() - elapsed;
        return remaining <= 0 ? 0 : (int) ((remaining + 19) / 20);
    }

    /** Tecla R: recarrega usando a bateria do slot. */
    public static void pocketRecharge(ServerPlayer player) {
        RingData data = ModCapabilities.get(player).orElse(null);
        if (data == null || !data.hasRing()) return;

        int status = pocketStatus(player, data);
        if (status < 0) {
            player.displayClientMessage(Component.translatable("message.lanterna.no_pocket_battery"), true);
            return;
        }
        if (status > 0) {
            player.displayClientMessage(Component.translatable("message.lanterna.pocket_cooldown", status), true);
            return;
        }

        CorpsDefinition def = CorpsManager.get(data.corps());
        if (!recharge(player, data, def)) return;
        data.setLastPocketRecharge(player.level().getGameTime());

        playRechargeEffects(player, def);
    }

    public static void playRechargeEffects(ServerPlayer player, CorpsDefinition def) {
        player.sendSystemMessage(Component.translatable("oath." + def.id().getNamespace() + "." + def.id().getPath())
                .withStyle(s -> s.withColor(def.color()).withItalic(true)));
        player.displayClientMessage(Component.translatable("message.lanterna.recharged")
                .withStyle(s -> s.withColor(def.color())), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.0f);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.4f);
        player.serverLevel().sendParticles(new DustParticleOptions(PowerBatteryBlock.rgb(def.color()), 1.3f),
                player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.8, 0.5, 0.02);
    }

    // ===================== Tirar o anel (comandos) =====================

    public static ItemStack removeRing(ServerPlayer player) {
        RingData data = ModCapabilities.get(player).orElse(null);
        Optional<SlotResult> slot = CuriosHelper.findRing(player);
        if (data == null || slot.isEmpty()) return ItemStack.EMPTY;

        ItemStack ring = CuriosHelper.take(player, slot.get());
        data.unequip();
        applyFlightAbility(player, data);
        sync(player, data);
        return ring;
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

    private static void tickFlight(ServerPlayer player, RingData data) {
        if (!data.flightEnabled()) return;

        // Outro mod ou troca de modo de jogo pode ter tirado o voo
        if (!player.getAbilities().mayfly) applyFlightAbility(player, data);

        if (player.getAbilities().flying && !player.isCreative() && !player.isSpectator()
                && data.nextFlightTick() % 20 == 0) {
            CorpsDefinition def = CorpsManager.get(data.corps());
            setCharge(player, data, data.charge() - def.flightDrain());
            if (data.charge() <= 0) {
                data.setFlightEnabled(false);
                applyFlightAbility(player, data);
                player.displayClientMessage(Component.translatable("message.lanterna.charge_depleted"), true);
                player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.PLAYERS, 1.0f, 0.7f);
            }
        }
    }

    // ===================== Sincronização =====================

    public static void sync(ServerPlayer player, RingData data) {
        ModNetwork.sendTo(player, SyncRingDataPacket.from(data));
        data.clearDirty();
    }

    private RingPowers() {}
}
