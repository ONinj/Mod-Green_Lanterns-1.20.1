package com.lanterna.selection;

import com.lanterna.LanternaConfig;
import com.lanterna.block.PowerBatteryBlock;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.CorpsDefinition;
import com.lanterna.corps.CorpsManager;
import com.lanterna.item.PowerBatteryItem;
import com.lanterna.item.PowerRingItem;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.SelectionCutscenePacket;
import com.lanterna.power.EquipmentHelper;
import com.lanterna.power.RingPowers;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A cerimônia de entrega do anel. Cada Tropa tem a sua:
 *
 *  VERDE    (vontade)    o anel risca o céu como um cometa e gira até o dedo
 *  VERMELHO (fúria)      colunas de sangue sobem do chão e se fecham em volta
 *  AMARELO  (medo)       a escuridão engole tudo; raios amarelos rasgam o escuro até você
 *  AZUL     (esperança)  uma aurora azul sobe do chão como um amanhecer, com sinos
 *  VIOLETA  (amor)       pétalas e corações caem em espiral em volta de você
 *  ÍNDIGO   (compaixão)  um feixe de luz desce do céu e ondas índigo se espalham no chão
 *  LARANJA  (avareza)    uma chuva de ouro gira em volta de você, cada vez mais rápida
 *  NEGRO    (morte)      almas sobem da terra e giram até virar um anel negro
 *  BRANCO   (vida)       uma coluna de luz branca explode, e tudo em volta floresce de luz
 *
 * Fluxo:  startSelection() -> tick() a cada tick -> deliver() no fim.
 */
public final class SelectionManager {

    /** Como o anel chegou, quando não foi pelo ritual da Tropa ("call" = conquistas, "dormant" = anel apagado). */
    private static final java.util.Map<java.util.UUID, String> REASON = new java.util.HashMap<>();

    public static boolean startSelection(ServerPlayer player, ResourceLocation corpsId, String reason) {
        boolean ok = startSelection(player, corpsId);
        if (ok) REASON.put(player.getUUID(), reason);
        return ok;
    }

    /** Começa a cerimônia. Devolve false se o jogador já tem anel ou já está sendo escolhido. */
    public static boolean startSelection(ServerPlayer player, ResourceLocation corpsId) {
        RingData data = ModCapabilities.get(player).orElse(null);
        if (data == null || data.hasRing() || data.isSelectionPending()) return false;

        CorpsDefinition def = CorpsManager.get(corpsId);
        // a cena tem roteiro (chegada do anel, ritual, encaixe no dedo): pelo menos 10 s
        int duration = Math.max(200, LanternaConfig.SELECTION_ANIMATION_TICKS.get());
        data.startPending(def.id(), duration);

        // Efeito de tela no cliente
        SelectionCutscenePacket cena = new SelectionCutscenePacket(def.id().toString(), def.color(), duration, player.getId());
        ModNetwork.sendTo(player, cena);
        ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> player), cena);

        // Título grande no meio da tela
        String base = "selection." + def.id().getNamespace() + "." + def.id().getPath();
        player.connection.send(new ClientboundSetTitlesAnimationPacket(20, Math.max(10, duration - 30), 20));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.translatable(base + ".subtitle").withStyle(s -> s.withColor(def.color()).withItalic(true))));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable(base + ".title").withStyle(s -> s.withColor(def.color()).withBold(true))));

        startEffects(player, def.id().getPath(), duration);
        return true;
    }

    private static void startEffects(ServerPlayer player, String corps, int duration) {
        ServerLevel level = player.serverLevel();
        switch (corps) {
            case "red" -> {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, duration + 40, 0, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 2, false, false));
                sound(player, SoundEvents.WARDEN_HEARTBEAT, 2.0f, 0.6f);
                sound(player, SoundEvents.WITHER_SPAWN, 0.4f, 1.6f);
            }
            case "yellow" -> {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 3, false, false));
                sound(player, SoundEvents.WARDEN_EMERGE, 1.0f, 1.3f);
                sound(player, SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.0f, 0.7f);
            }
            case "blue" -> {
                player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, duration / 2, 0, false, false));
                sound(player, SoundEvents.BELL_BLOCK, 1.5f, 1.2f);
                sound(player, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.2f);
            }
            case "violet" -> {
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, duration + 20, 0, false, false));
                sound(player, SoundEvents.ALLAY_AMBIENT_WITH_ITEM, 1.5f, 1.0f);
                sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 0.8f);
            }
            case "indigo" -> {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 1, false, false));
                sound(player, SoundEvents.CONDUIT_ACTIVATE, 1.5f, 0.9f);
            }
            case "orange" -> {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 2, false, false));
                sound(player, SoundEvents.PIGLIN_ADMIRING_ITEM, 1.5f, 0.8f);
                sound(player, SoundEvents.VILLAGER_TRADE, 1.0f, 0.7f);
            }
            case "black" -> {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 3, false, false));
                sound(player, SoundEvents.SOUL_ESCAPE, 2.0f, 0.5f);
                sound(player, SoundEvents.WITHER_AMBIENT, 0.8f, 0.5f);
            }
            case "white" -> {
                player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, duration / 3, 0, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, duration + 40, 0, false, false));
                sound(player, SoundEvents.BEACON_ACTIVATE, 2.0f, 0.6f);
                sound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0f, 0.6f);
            }
            default -> { // verde e Tropas de datapack
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, duration + 20, 0, false, false));
                sound(player, SoundEvents.BEACON_ACTIVATE, 1.5f, 0.8f);
            }
        }
    }

    /** Roda a cada tick enquanto a cerimônia está acontecendo. */
    public static void tick(ServerPlayer player, RingData data) {
        if (!data.isSelectionPending()) return;

        ResourceLocation corpsId = data.pendingCorps();
        if (corpsId == null) {
            data.clearPending();
            return;
        }
        CorpsDefinition def = CorpsManager.get(corpsId);

        int left = data.tickPending();
        int total = Math.max(1, data.pendingTotal());
        float progress = 1.0f - (float) left / total;   // 0 -> 1

        // a cena em si é desenhada no cliente (client/escolha); aqui só os sons do roteiro
        sons(player, def.id().getPath(), progress, left, total);

        if (left <= 0) {
            deliver(player, data, def);
        }
    }

    // ============================== Sons do roteiro ==============================

    private static void sons(ServerPlayer p, String corps, float t, int left, int total) {
        int passado = total - left;
        // o anel chega perto e encaixa: igual para todas
        if (passado == (int) (total * 0.86f)) sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6f, 1.4f);
        if (passado == (int) (total * 0.93f)) sound(p, SoundEvents.BEACON_POWER_SELECT, 1.5f, 1.6f);
        switch (corps) {
            case "green" -> {
                if (passado == 2) sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 2f, 0.5f);
                if (t < 0.5f && passado % 12 == 0) sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 2f, 0.8f + t * 1.4f);
                if (passado == (int) (total * 0.5f)) sound(p, SoundEvents.TRIDENT_RIPTIDE_3, 1.4f, 1.2f);
                if (passado == (int) (total * 0.72f)) sound(p, SoundEvents.BEACON_ACTIVATE, 2f, 0.9f);
            }
            case "red" -> {
                int batida = Math.max(10, (int) (26 - t * 16));
                if (passado % batida == 0) sound(p, SoundEvents.WARDEN_HEARTBEAT, 2.5f, 0.55f);
                if (passado == 6) sound(p, SoundEvents.GENERIC_EXPLODE, 0.7f, 0.5f);
                if (passado == (int) (total * 0.25f)) sound(p, SoundEvents.LAVA_AMBIENT, 2f, 0.6f);
                if (passado == (int) (total * 0.55f)) sound(p, SoundEvents.BLAZE_SHOOT, 1.5f, 0.5f);
            }
            case "yellow" -> {
                if (passado % 22 == 7) sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.7f, 1.4f + t * 0.4f);
                if (passado % 35 == 0) sound(p, SoundEvents.WARDEN_TENDRIL_CLICKS, 1.5f, 0.8f);
                if (passado == (int) (total * 0.62f)) sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.4f, 1.6f);
            }
            case "blue" -> {
                if (passado % 30 == 0) sound(p, SoundEvents.BELL_RESONATE, 1.2f, 1.2f + t * 0.6f);
                if (passado == (int) (total * 0.4f)) sound(p, SoundEvents.BEACON_AMBIENT, 2f, 1.5f);
            }
            case "violet" -> {
                if (passado % 14 == 0) sound(p, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.4f, 0.8f + t);
                if (passado == (int) (total * 0.6f)) sound(p, SoundEvents.AMETHYST_BLOCK_BREAK, 2f, 0.7f);
            }
            case "indigo" -> {
                if (passado == 4) sound(p, SoundEvents.CONDUIT_ACTIVATE, 2f, 0.8f);
                if (passado % 20 == 0) sound(p, SoundEvents.CONDUIT_AMBIENT_SHORT, 1.5f, 1.2f);
            }
            case "orange" -> {
                if (passado % 4 == 0) sound(p, SoundEvents.CHAIN_STEP, 0.5f, 1.2f + t);
                if (passado % 9 == 0) sound(p, SoundEvents.ITEM_PICKUP, 0.6f, 0.5f + t);
                if (passado == (int) (total * 0.62f)) sound(p, SoundEvents.ANVIL_LAND, 1f, 0.6f);
            }
            case "black" -> {
                if (passado % 20 == 0) sound(p, SoundEvents.SOUL_ESCAPE, 2f, 0.4f);
                if (passado % 40 == 10) sound(p, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6f, 0.5f);
            }
            case "white" -> {
                if (passado % 16 == 0) sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, 1.0f + t);
                if (passado == (int) (total * 0.65f)) sound(p, SoundEvents.TOTEM_USE, 1f, 1.2f);
            }
            default -> {
                if (passado % 20 == 0) sound(p, SoundEvents.BEACON_AMBIENT, 1.5f, 1.2f);
            }
        }
    }

    // ============================== Entrega ==============================

    private static void deliver(ServerPlayer player, RingData data, CorpsDefinition def) {
        String reason = REASON.remove(player.getUUID());
        data.clearPending();
        data.resetRage();
        data.resetRituals();
        data.setEverChosen(true);
        data.setPowerActive(true);

        // O anel vai direto para o slot de anel, já ligado ao portador
        // O Anel Apagado desperta fraco: só 10% de carga
        int startCharge = "dormant".equals(reason) ? Math.max(1, def.maxCharge() / 10) : def.maxCharge();
        ItemStack ring = PowerRingItem.create(def.id(), def.color(), startCharge);
        PowerRingItem.setOwner(ring, player);
        EquipmentHelper.equipOrGive(player, RingData.RING_SLOT, ring);

        // A bateria da Tropa vem junto, no slot de bateria
        // (a Tropa da Vida não tem bateria: o anel branco não descarrega)
        ItemStack battery = PowerBatteryItem.create(def.id(), def.color());
        if (!battery.isEmpty()) EquipmentHelper.equipOrGive(player, RingData.BATTERY_SLOT, battery);

        ServerLevel level = player.serverLevel();
        Vector3f rgb = PowerBatteryBlock.rgb(def.color());
        sound(player, SoundEvents.BEACON_POWER_SELECT, 2.0f, 1.0f);
        sound(player, SoundEvents.TOTEM_USE, 0.6f, 1.4f);

        switch (def.id().getPath()) {
            case "red" -> {
                sound(player, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0f, 0.6f);
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 60, 1));
            }
            case "yellow" -> {
                player.removeEffect(MobEffects.DARKNESS);
                sound(player, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.5f);
            }
            case "blue" -> player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 30, 1));
            case "violet" -> player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 60, 1));
            case "indigo" -> player.addEffect(new MobEffectInstance(MobEffects.HEAL, 1, 1));
            case "orange" -> player.addEffect(new MobEffectInstance(MobEffects.LUCK, 20 * 300, 1));
            case "black" -> player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60, 1));
            case "white" -> {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 30, 2));
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 10, 0));
            }
            default -> {}
        }

        String base = "selection." + def.id().getNamespace() + "." + def.id().getPath();
        if (reason != null) {
            player.sendSystemMessage(Component.translatable("selection.lanterna.reason." + reason,
                    Component.translatable(CorpsDefinition.nameKey(def.id()))).withStyle(s -> s.withColor(def.color())));
        } else {
            player.sendSystemMessage(Component.translatable(base + ".message").withStyle(s -> s.withColor(def.color())));
        }
        player.sendSystemMessage(Component.translatable("message.lanterna.tutorial"));
        RingPowers.sync(player, data);

        // A lore começa: missão inicial da Tropa, e o anel leva o novo Lanterna direto para o planeta dela
        com.lanterna.mission.MissionManager.startCorpsLine(player, def.id());
        com.lanterna.lore.PlanetLore home = com.lanterna.lore.PlanetLore.byCorps(def.id());
        if (home != null && home != com.lanterna.lore.PlanetLore.GENESE
                && com.lanterna.lore.PlanetLore.byDimension(player.level().dimension()) != home) {
            boolean green = def.id().equals(com.lanterna.corps.BuiltinCorps.GREEN.id());
            player.sendSystemMessage((green ? Component.translatable("message.lanterna.green_to_oa")
                    : Component.translatable("message.lanterna.ring_to_home", Component.translatable("planet.lanterna." + home.planet)))
                    .withStyle(s -> s.withColor(def.color()).withItalic(true)));
            ModNetwork.sendTo(player, new com.lanterna.network.lore.StartAutopilotPacket(home.planet, 60));
            RingPowers.setHoming(player, true);
        }
    }

    private static void sound(ServerPlayer player, SoundEvent event, float volume, float pitch) {
        player.level().playSound(null, player.blockPosition(), event, SoundSource.PLAYERS, volume, pitch);
    }

    private SelectionManager() {}
}
