package com.lanterna.lore.ritual;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.item.PowerRingItem;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.MissionManager;
import com.lanterna.power.EquipmentHelper;
import com.lanterna.power.RingPowers;
import com.lanterna.selection.SelectionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Altar da Vida (Gênese). Os sete anéis da fusão (Vermelho, Laranja, Amarelo, Verde, Azul, Índigo e Violeta)
 * mais os sete drops dos bosses se fundem e somem; nasce o Anel Branco. O Anel Preto nunca entra:
 * quem o carrega nem consegue tocar o altar.
 */
public final class WhiteAltar {

    public static void use(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (MissionManager.hasRing(player, "black")) {
            Vec3 push = player.position().subtract(Vec3.atCenterOf(pos)).normalize().scale(1.6);
            player.push(push.x, 0.6, push.z);
            player.hurtMarked = true;
            player.hurt(player.damageSources().magic(), 4);
            player.displayClientMessage(Component.translatable("message.lanterna.altar_rejects_black"), true);
            level.playSound(null, pos, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 1f, 1.6f);
            return;
        }
        if (!player.isCreative() && com.lanterna.tropa.Nivel.nivel(player) < com.lanterna.tropa.Nivel.nivelParaForjar(com.lanterna.lore.PlanetLore.GENESE)) {
            player.displayClientMessage(Component.translatable("nivel.lanterna.precisa_forja",
                    com.lanterna.tropa.Nivel.nivelParaForjar(com.lanterna.lore.PlanetLore.GENESE)), true);
            return;
        }
        if (MissionManager.hasRing(player, "white")) {
            player.displayClientMessage(Component.translatable("message.lanterna.altar_already_white"), true);
            return;
        }
        List<Component> missing = new ArrayList<>();
        for (PlanetLore p : PlanetLore.values()) {
            if (!p.fusion()) continue;
            if (!MissionManager.hasRing(player, p.corps.id().getPath()))
                missing.add(Component.translatable("corps.lanterna." + p.corps.id().getPath()).withStyle(s -> s.withColor(p.color())));
            if (MissionManager.countItem(player, p.dropItem()) < 1) missing.add(p.dropItem().getDescription().copy().withStyle(s -> s.withColor(p.color())));
        }
        if (!missing.isEmpty() && !player.isCreative()) {
            player.sendSystemMessage(Component.translatable("message.lanterna.altar_missing"));
            for (Component c : missing) player.sendSystemMessage(Component.literal(" • ").append(c));
            return;
        }

        // Fusão: os anéis e os drops somem (inclusive o anel que está no dedo)
        RingData data = ModCapabilities.get(player).orElse(null);
        if (data == null) return;
        for (PlanetLore p : PlanetLore.values()) {
            if (!p.fusion()) continue;
            removeRing(player, p.corps.id().getPath());
            MissionManager.removeItems(player, p.dropItem(), 1);
        }
        ItemStack worn = EquipmentHelper.ring(player);
        if (!worn.isEmpty() && !PowerRingItem.getCorps(worn).equals(BuiltinCorps.WHITE.id())) RingPowers.removeRing(player);

        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 400, 0.5, 12, 0.5, 0.05);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 2, pos.getZ() + 0.5, 200, 3, 3, 3, 0.4);
        ForgeRituals.burst(level, pos, 0xFFFFFF);
        // O Anel Branco chega com a cerimônia da Vida
        SelectionManager.startSelection(player, BuiltinCorps.WHITE.id());
    }

    private static void removeRing(ServerPlayer player, String corps) {
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack s = player.getInventory().items.get(i);
            if (s.getItem() instanceof PowerRingItem && PowerRingItem.getCorps(s).getPath().equals(corps)) {
                player.getInventory().items.set(i, ItemStack.EMPTY);
                return;
            }
        }
        if (player.getOffhandItem().getItem() instanceof PowerRingItem && PowerRingItem.getCorps(player.getOffhandItem()).getPath().equals(corps)) {
            player.getInventory().offhand.set(0, ItemStack.EMPTY);
        }
        // O do dedo sai depois (RingPowers.removeRing)
    }

    private WhiteAltar() {}
}
