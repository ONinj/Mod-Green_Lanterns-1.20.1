package com.lanterna.event;

import com.lanterna.LanternaConfig;
import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.capability.RingDataProvider;
import com.lanterna.command.LanternaCommand;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.corps.CorpsManager;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.SyncVisibleRingPacket;
import com.lanterna.power.RingPowers;
import com.lanterna.selection.RitualEvents;
import com.lanterna.selection.SelectionManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommonEvents {

    // ===================== Dados do jogador =====================

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(LanternaMod.id("ring_data"), new RingDataProvider());
        }
    }

    /** Morte ou volta do End: copia os dados para o "novo" jogador. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        ModCapabilities.get(original).ifPresent(oldData ->
                ModCapabilities.get(event.getEntity()).ifPresent(newData -> newData.copyFrom(oldData)));
        original.invalidateCaps();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        refresh(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        refresh(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        refresh(event.getEntity());
    }

    private static void refresh(Player player) {
        if (player instanceof ServerPlayer sp) {
            ModCapabilities.get(sp).ifPresent(data -> {
                RingPowers.applyFlightAbility(sp, data);
                RingPowers.sync(sp, data);
                RingPowers.syncVisibleRing(sp, data, true);
            });
        }
    }

    /** Quando alguém passa a enxergar um jogador, conta qual anel ele está usando. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            ModCapabilities.get(target).ifPresent(data ->
                    ModNetwork.sendTo(viewer, SyncVisibleRingPacket.of(target.getId(), data,
                            com.lanterna.item.BatteryItems.isStaff(com.lanterna.power.EquipmentHelper.battery(target)))));
        }
    }

    // ===================== Tick =====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        ModCapabilities.get(player).ifPresent(data -> {
            SelectionManager.tick(player, data);
            RingPowers.tick(player, data);
        });
    }

    // ===================== Seleção VERDE: acordar =====================

    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        // wakeImmediately = false quando a noite passou de verdade (não saiu da cama no meio)
        if (event.wakeImmediately()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        RingData data = ModCapabilities.get(player).orElse(null);
        if (data == null || !RitualEvents.canBeChosen(player, data) || data.everChosen()) return;

        if (player.getRandom().nextDouble() < LanternaConfig.GREEN_SLEEP_CHANCE.get()) {
            SelectionManager.startSelection(player, BuiltinCorps.GREEN.id());
        }
    }

    // ===================== Seleção VERMELHA: fúria =====================

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Enemy)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        RingData data = ModCapabilities.get(player).orElse(null);
        if (data == null || !RitualEvents.canBeChosen(player, data)) return;

        int required = LanternaConfig.RED_KILLS_REQUIRED.get();
        int window = LanternaConfig.RED_WINDOW_SECONDS.get() * 20;
        int kills = data.registerRageKill(player.level().getGameTime(), window);

        if (kills >= required) {
            SelectionManager.startSelection(player, BuiltinCorps.RED.id());
        } else if (kills >= required / 2) {
            // A partir da metade, o jogador começa a sentir a fúria
            player.displayClientMessage(Component.translatable("message.lanterna.rage_building")
                    .withStyle(s -> s.withColor(BuiltinCorps.RED.color())), true);
        }
    }

    // ===================== Registro =====================

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CorpsManager());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LanternaCommand.register(event.getDispatcher());
    }

    private CommonEvents() {}
}
