package com.lanterna.selection;

import com.lanterna.LanternaConfig;
import com.lanterna.block.PowerBatteryBlock;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.corps.CorpsDefinition;
import com.lanterna.corps.CorpsManager;
import com.lanterna.item.PowerBatteryItem;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.SelectionCutscenePacket;
import com.lanterna.item.PowerRingItem;
import com.lanterna.power.CuriosHelper;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A cerimônia de entrega do anel. Cada Tropa tem o seu "estilo":
 *
 *  VERDE    - o anel risca o céu como um cometa, desce até o jogador
 *             e gira em volta dele até encaixar no dedo.
 *  VERMELHO - colunas de energia cor de sangue sobem do chão e se fecham
 *             em volta do jogador, com a visão distorcida pela fúria.
 *  OUTRAS   - espiral padrão (cada uma ganha o seu estilo nas próximas fases).
 *
 * O fluxo é sempre:  startSelection() -> tick() a cada tick -> deliver() no fim.
 */
public final class SelectionManager {

    /** Começa a cerimônia. Devolve false se o jogador já tem anel ou já está sendo escolhido. */
    public static boolean startSelection(ServerPlayer player, ResourceLocation corpsId) {
        RingData data = ModCapabilities.get(player).orElse(null);
        if (data == null || data.hasRing() || data.isSelectionPending()) return false;

        CorpsDefinition def = CorpsManager.get(corpsId);
        int duration = LanternaConfig.SELECTION_ANIMATION_TICKS.get();
        data.startPending(def.id(), duration);

        // Efeito de tela no cliente
        ModNetwork.sendTo(player, new SelectionCutscenePacket(def.id().toString(), def.color(), duration));

        // Título grande no meio da tela
        String base = "selection." + def.id().getNamespace() + "." + def.id().getPath();
        player.connection.send(new ClientboundSetTitlesAnimationPacket(20, Math.max(10, duration - 30), 20));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.translatable(base + ".subtitle").withStyle(s -> s.withColor(def.color()).withItalic(true))));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable(base + ".title").withStyle(s -> s.withColor(def.color()).withBold(true))));

        ServerLevel level = player.serverLevel();
        if (isCorps(def, BuiltinCorps.RED)) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, duration + 40, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 2, false, false));
            level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0f, 0.6f);
            level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.4f, 1.6f);
        } else {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, duration + 20, 0, false, false));
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 0.8f);
        }
        return true;
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

        if (isCorps(def, BuiltinCorps.GREEN)) {
            animateGreen(player, def, progress, left);
        } else if (isCorps(def, BuiltinCorps.RED)) {
            animateRed(player, def, progress, left);
        } else {
            animateDefault(player, def, progress, left);
        }

        if (left <= 0) {
            deliver(player, data, def);
        }
    }

    // ============================== Animações ==============================

    /** Verde: cometa vindo do céu, depois espiral fechando até a mão. */
    private static void animateGreen(ServerPlayer player, CorpsDefinition def, float progress, int left) {
        ServerLevel level = player.serverLevel();
        Vector3f rgb = PowerBatteryBlock.rgb(def.color());
        Vec3 hand = player.position().add(0, 1.2, 0);

        if (progress < 0.6f) {
            float q = progress / 0.6f;
            float eased = q * q; // acelera ao chegar perto
            double angle = (player.getUUID().getLeastSignificantBits() & 0xFFFF) / 65535.0 * Math.PI * 2;
            Vec3 start = hand.add(Math.cos(angle) * 18, 24, Math.sin(angle) * 18);
            Vec3 pos = start.lerp(hand.add(0, 1.0, 0), eased);
            Vec3 prev = start.lerp(hand.add(0, 1.0, 0), Math.max(0, (q - 0.03f)) * Math.max(0, (q - 0.03f)));

            // Rastro do cometa
            for (int i = 0; i < 6; i++) {
                Vec3 p = prev.lerp(pos, i / 5.0);
                level.sendParticles(new DustParticleOptions(rgb, 2.0f), p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
            }
            level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1, 0.02, 0.02, 0.02, 0.01);

            if (left % 20 == 0) {
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0f, 1.5f);
            }
        } else {
            float q = (progress - 0.6f) / 0.4f;
            double radius = Mth.lerp(q, 1.8, 0.15);
            double height = Mth.lerp(q, 2.2, 1.2);
            double angle = left * 0.55;
            for (int arm = 0; arm < 3; arm++) {
                double a = angle + arm * (Math.PI * 2 / 3);
                double x = player.getX() + Math.cos(a) * radius;
                double z = player.getZ() + Math.sin(a) * radius;
                level.sendParticles(new DustParticleOptions(rgb, 1.4f), x, player.getY() + height, z, 1, 0, 0, 0, 0);
            }
            if (left % 6 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + height, player.getZ(),
                        2, radius * 0.5, 0.1, radius * 0.5, 0.01);
            }
        }
    }

    /** Vermelho: colunas de sangue subindo do chão e se fechando. */
    private static void animateRed(ServerPlayer player, CorpsDefinition def, float progress, int left) {
        ServerLevel level = player.serverLevel();
        Vector3f rgb = PowerBatteryBlock.rgb(def.color());
        Vector3f dark = new Vector3f(0.35f, 0.0f, 0.0f);

        double radius = Mth.lerp(progress, 4.0, 0.4);
        double rise = (left * 0.12) % 2.4;
        int columns = 8;
        for (int i = 0; i < columns; i++) {
            double a = i * (Math.PI * 2 / columns) + progress * Math.PI;
            double x = player.getX() + Math.cos(a) * radius;
            double z = player.getZ() + Math.sin(a) * radius;
            level.sendParticles(new DustParticleOptions(rgb, 1.6f), x, player.getY() + rise, z, 1, 0, 0, 0, 0);
            level.sendParticles(new DustParticleOptions(dark, 1.2f), x, player.getY() + 0.1, z, 1, 0.1, 0, 0.1, 0);
        }

        // "Gotas" caindo em volta
        level.sendParticles(ParticleTypes.FALLING_LAVA, player.getX(), player.getY() + 3.0, player.getZ(),
                2, 2.5, 0.5, 2.5, 0);

        if (left % 25 == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0f, 0.5f);
        }
    }

    /** Padrão para as demais Tropas: espiral subindo. */
    private static void animateDefault(ServerPlayer player, CorpsDefinition def, float progress, int left) {
        ServerLevel level = player.serverLevel();
        Vector3f rgb = PowerBatteryBlock.rgb(def.color());
        double radius = Mth.lerp(progress, 2.5, 0.2);
        double angle = left * 0.4;
        for (int arm = 0; arm < 2; arm++) {
            double a = angle + arm * Math.PI;
            level.sendParticles(new DustParticleOptions(rgb, 1.3f),
                    player.getX() + Math.cos(a) * radius,
                    player.getY() + 0.2 + progress * 1.2,
                    player.getZ() + Math.sin(a) * radius,
                    1, 0, 0, 0, 0);
        }
    }

    // ============================== Entrega ==============================

    private static void deliver(ServerPlayer player, RingData data, CorpsDefinition def) {
        data.clearPending();
        data.resetRage();

        data.setEverChosen(true);

        // O anel vai direto para o slot de anel, já ligado ao portador
        ItemStack ring = PowerRingItem.create(def.id(), def.color(), def.maxCharge());
        PowerRingItem.setOwner(ring, player);
        giveOrEquip(player, CuriosHelper.RING_SLOT, ring);

        // A bateria da Tropa vem junto, no slot de bateria
        giveOrEquip(player, CuriosHelper.BATTERY_SLOT, PowerBatteryItem.create(def.id(), def.color()));

        ServerLevel level = player.serverLevel();
        Vector3f rgb = PowerBatteryBlock.rgb(def.color());
        level.sendParticles(new DustParticleOptions(rgb, 2.5f), player.getX(), player.getY() + 1.0, player.getZ(),
                120, 0.8, 1.0, 0.8, 0.15);
        level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.2, player.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0f, 1.0f);
        level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.4f);

        if (isCorps(def, BuiltinCorps.RED)) {
            level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 0.6f);
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 60, 1));
        }

        String base = "selection." + def.id().getNamespace() + "." + def.id().getPath();
        player.sendSystemMessage(Component.translatable(base + ".message").withStyle(s -> s.withColor(def.color())));
        player.sendSystemMessage(Component.translatable("message.lanterna.tutorial"));
    }

    private static void giveOrEquip(ServerPlayer player, String slot, ItemStack stack) {
        if (CuriosHelper.equip(player, slot, stack)) return;
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static boolean isCorps(CorpsDefinition def, BuiltinCorps corps) {
        return def.id().equals(corps.id());
    }

    private SelectionManager() {}
}
