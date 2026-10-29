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
import com.lanterna.network.SyncVisibleRingPacket;
import com.lanterna.network.RechargeAnimPacket;
import net.minecraftforge.network.PacketDistributor;

import java.util.Optional;

/**
 * Lógica de servidor do anel.
 *
 * O anel vive no slot de anel da aba "Lanterna", e a carga fica gravada no próprio item.
 * A cada tick o RingData do jogador é atualizado a partir do slot, então o
 * resto do mod (HUD, voo, bateria, seleção) só olha o RingData.
 */
public final class RingPowers {

    // ===================== Leitura do slot =====================

    public static Optional<ItemStack> wornRing(ServerPlayer player) {
        ItemStack ring = EquipmentHelper.ring(player);
        return ring.isEmpty() ? Optional.empty() : Optional.of(ring);
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

        tickRecharge(player, data);
        if (data.isDirty()) sync(player, data);
        syncVisibleRing(player, data, false);
    }

    // ===================== Anel visível para os outros =====================

    /** Avisa quem está vendo este jogador (e ele mesmo) qual anel desenhar na mão. */
    public static void syncVisibleRing(ServerPlayer player, RingData data, boolean force) {
        String key = data.hasRing()
                ? data.corps() + "|" + data.color() + "|" + data.powerActive() + "|" + data.flightEnabled()
                + "|" + data.hyper() + "|" + data.autopilot() : "";
        if (!data.visibleRingChanged(key) && !force) return;
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                SyncVisibleRingPacket.of(player.getId(), data));
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
        ItemStack battery = EquipmentHelper.battery(player);
        if (battery.isEmpty() || !PowerBatteryItem.getCorps(battery).equals(data.corps())) return -1;
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

        if (data.rechargeAnim() >= 0) return; // já está recarregando

        // Começa a animação: o portal abre na cintura e a bateria sobe dele.
        // A carga entra quando a mão encosta na bateria (RechargeTimeline.CHARGE_AT).
        data.setRechargeAnim(0);
        data.setLastPocketRecharge(player.level().getGameTime());
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new RechargeAnimPacket(player.getId(), data.corps().toString(), data.color()));
    }

    /** Avança a animação de recarga (chamado todo tick). */
    private static void tickRecharge(ServerPlayer player, RingData data) {
        int t = data.rechargeAnim();
        if (t < 0) return;
        if (!data.hasRing()) {
            data.setRechargeAnim(-1);
            return;
        }
        t++;
        data.setRechargeAnim(t);
        if (t == RechargeTimeline.CHARGE_AT) {
            CorpsDefinition def = CorpsManager.get(data.corps());
            if (recharge(player, data, def)) playRechargeEffects(player, def);
        }
        if (t >= RechargeTimeline.TOTAL) data.setRechargeAnim(-1);
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
        if (data == null || EquipmentHelper.ring(player).isEmpty()) return ItemStack.EMPTY;

        ItemStack ring = EquipmentHelper.take(player, RingData.RING_SLOT);
        data.unequip();
        applyFlightAbility(player, data);
        sync(player, data);
        return ring;
    }

    // ===================== Ligar / desligar o anel =====================

    /** Tecla G: liga ou desliga os poderes (a aura acende e apaga). */
    public static void togglePower(ServerPlayer player) {
        RingData data = ModCapabilities.get(player).orElse(null);
        if (data == null || !data.hasRing()) return;

        boolean enable = !data.powerActive();
        if (enable && data.charge() <= 0) {
            player.displayClientMessage(Component.translatable("message.lanterna.no_charge"), true);
            return;
        }
        data.setPowerActive(enable);
        applyFlightAbility(player, data);

        CorpsDefinition def = CorpsManager.get(data.corps());
        player.displayClientMessage(Component.translatable(enable ? "message.lanterna.power_on" : "message.lanterna.power_off")
                .withStyle(s -> s.withColor(def.color())), true);
        player.level().playSound(null, player.blockPosition(),
                enable ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 1.4f);
        if (enable) {
            player.serverLevel().sendParticles(new DustParticleOptions(PowerBatteryBlock.rgb(def.color()), 1.2f),
                    player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.9, 0.4, 0.02);
        }
        sync(player, data);
        syncVisibleRing(player, data, false);
    }

    // ===================== Voo =====================

    public static void toggleFlight(ServerPlayer player) {
        RingData data = ModCapabilities.get(player).orElse(null);
        if (data == null || !data.hasRing()) return;

        if (!data.powerActive()) {
            player.displayClientMessage(Component.translatable("message.lanterna.power_needed"), true);
            return;
        }

        if (!data.flightEnabled() && data.charge() <= 0) {
            player.displayClientMessage(Component.translatable("message.lanterna.no_charge"), true);
            return;
        }

        boolean enable = !data.flightEnabled();
        data.setFlightEnabled(enable);
        applyFlightAbility(player, data);

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
        boolean shouldFly = canFlyNaturally || (data.hasRing() && data.powerActive() && data.flightEnabled());

        if (abilities.mayfly != shouldFly) {
            abilities.mayfly = shouldFly;
            if (!shouldFly || !canFlyNaturally) abilities.flying = false;
            player.onUpdateAbilities();
        }
    }

    /** O cliente avisa que entrou/saiu da hiperpropulsão ou do piloto automático. */
    public static void setFlightMode(ServerPlayer player, boolean hyper, boolean autopilot) {
        RingData data = ModCapabilities.get(player).orElse(null);
        if (data == null) return;
        boolean canFly = data.hasRing() && data.powerActive() && data.flightEnabled();
        boolean inSpace = player.level().dimension().equals(com.lanterna.space.SpaceBodies.SPACE);
        data.setFlightMode(canFly && hyper && inSpace, canFly && autopilot);
        syncVisibleRing(player, data, false);
    }

    private static void tickFlight(ServerPlayer player, RingData data) {
        if (!data.flightEnabled()) return;
        // Hiperpropulsão só existe no espaço
        if (data.hyper() && !player.level().dimension().equals(com.lanterna.space.SpaceBodies.SPACE)) {
            data.setFlightMode(false, data.autopilot());
        }

        // Outro mod ou troca de modo de jogo pode ter tirado a permissão de voo
        if (!player.getAbilities().mayfly) applyFlightAbility(player, data);

        // O voo é controlado pelo anel (cliente); aqui só zera a queda e cobra a carga
        player.fallDistance = 0;
        if (!player.onGround() && !player.isCreative() && !player.isSpectator()
                && data.nextFlightTick() % 20 == 0) {
            CorpsDefinition def = CorpsManager.get(data.corps());
            // Hiperpropulsão gasta o triplo
            setCharge(player, data, data.charge() - def.flightDrain() * (data.hyper() ? 3 : 1));
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
