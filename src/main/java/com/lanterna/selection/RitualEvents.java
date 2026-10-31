package com.lanterna.selection;

import com.lanterna.LanternaConfig;
import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.power.EquipmentHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;

/**
 * Os rituais: o que cada anel exige para escolher alguém.
 *
 *  VERDE    dormir a noite inteira (chance)                         -> CommonEvents
 *  VERMELHO matar muitos mobs hostis em pouco tempo                  -> CommonEvents
 *  AMARELO  ficar na escuridão do Warden (efeito Escuridão) com ele por perto, sem fugir
 *  AZUL     curar um aldeão zumbi (fraqueza + maçã dourada)
 *  VIOLETA  criar filhotes com o seu bicho de estimação domado por perto
 *  ÍNDIGO   consertar um Golem de Ferro quase morto (menos de 25% de vida) até a vida cheia
 *  LARANJA  carregar um tesouro enorme em pé sobre um bloco de ouro (só 1 por mundo)
 *  NEGRO    morrer sob o efeito Wither (o anel chega quando você renasce)
 *  BRANCO   só pela fusão dos sete anéis no Altar da Vida (Gênese)
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RitualEvents {

    private static final String HOPE_TAG = "lanterna_hope";
    private static final String COMPASSION_TAG = "lanterna_compassion";

    /** Valor de cada tesouro para o ritual laranja. */
    private static final Map<Item, Integer> TREASURE = Map.of(
            Items.GOLD_INGOT, 1, Items.GOLD_BLOCK, 9,
            Items.EMERALD, 3, Items.EMERALD_BLOCK, 27,
            Items.DIAMOND, 5, Items.DIAMOND_BLOCK, 45,
            Items.NETHERITE_INGOT, 40, Items.NETHERITE_BLOCK, 360);

    /** Pode ser escolhido agora? (sem anel no slot e sem cerimônia em andamento) */
    public static boolean canBeChosen(ServerPlayer player, RingData data) {
        return !data.hasRing() && !data.isSelectionPending() && EquipmentHelper.ring(player).isEmpty()
                && !player.isSpectator();
    }

    private static RingData dataOf(ServerPlayer player) {
        return ModCapabilities.get(player).orElse(null);
    }

    // ===================== AMARELO (medo) e LARANJA (avareza): checagem periódica =====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % 10 != 0) return;
        RingData data = dataOf(player);
        if (data == null || !canBeChosen(player, data)) return;

        // AMARELO: na Escuridão, com um Warden a até 24 blocos
        boolean inFear = player.hasEffect(MobEffects.DARKNESS)
                && !player.level().getEntitiesOfClass(Warden.class, player.getBoundingBox().inflate(24)).isEmpty();
        if (inFear) {
            int needed = LanternaConfig.YELLOW_FEAR_SECONDS.get() * 2; // checagem a cada meio segundo
            int fear = data.addFearTick();
            if (fear >= needed) {
                SelectionManager.startSelection(player, BuiltinCorps.YELLOW.id());
                return;
            }
            if (fear >= needed / 2 && fear % 6 == 0) {
                hint(player, "ritual.lanterna.yellow.hint", BuiltinCorps.YELLOW.color());
            }
        } else {
            data.decayFear();
        }

        // LARANJA: em pé sobre ouro, carregando um tesouro
        if (player.tickCount % 40 == 0
                && player.level().getBlockState(player.blockPosition().below()).is(Blocks.GOLD_BLOCK)) {
            int value = treasureValue(player.getInventory());
            if (value >= LanternaConfig.ORANGE_TREASURE_VALUE.get()) {
                OrangeClaim claim = OrangeClaim.get(player.server);
                if (claim.isTaken() && !player.getUUID().equals(claim.owner())) {
                    hint(player, "ritual.lanterna.orange.taken", BuiltinCorps.ORANGE.color());
                } else {
                    claim.claim(player.getUUID());
                    SelectionManager.startSelection(player, BuiltinCorps.ORANGE.id());
                }
            } else if (value >= LanternaConfig.ORANGE_TREASURE_VALUE.get() / 2) {
                hint(player, "ritual.lanterna.orange.hint", BuiltinCorps.ORANGE.color());
            }
        }
    }

    private static int treasureValue(Inventory inventory) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            total += TREASURE.getOrDefault(stack.getItem(), 0) * stack.getCount();
        }
        return total;
    }

    // ===================== AZUL (esperança) e ÍNDIGO (compaixão): interações =====================

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack held = event.getItemStack();

        // AZUL: começar a curar um aldeão zumbi (precisa estar com Fraqueza)
        if (event.getTarget() instanceof ZombieVillager zombie && held.is(Items.GOLDEN_APPLE)
                && zombie.hasEffect(MobEffects.WEAKNESS)) {
            zombie.getPersistentData().putUUID(HOPE_TAG, player.getUUID());
            return;
        }

        // ÍNDIGO: consertar um golem quase morto até a vida cheia
        if (event.getTarget() instanceof IronGolem golem && held.is(Items.IRON_INGOT)) {
            RingData data = dataOf(player);
            if (data == null || !canBeChosen(player, data)) return;
            CompoundTag tag = golem.getPersistentData();

            if (golem.getHealth() <= golem.getMaxHealth() * 0.25f) {
                tag.putUUID(COMPASSION_TAG, player.getUUID());
                hint(player, "ritual.lanterna.indigo.hint", BuiltinCorps.INDIGO.color());
            }
            boolean mine = tag.hasUUID(COMPASSION_TAG) && tag.getUUID(COMPASSION_TAG).equals(player.getUUID());
            // Cada barra de ferro cura 25 de vida: se esta completa a vida, o ritual acontece
            if (mine && golem.getHealth() + 25f >= golem.getMaxHealth()) {
                tag.remove(COMPASSION_TAG);
                SelectionManager.startSelection(player, BuiltinCorps.INDIGO.id());
            }
        }
    }

    @SubscribeEvent
    public static void onConversion(LivingConversionEvent.Post event) {
        if (!(event.getEntity() instanceof ZombieVillager zombie) || !(event.getOutcome() instanceof Villager villager)) return;
        CompoundTag tag = zombie.getPersistentData();
        if (!tag.hasUUID(HOPE_TAG) || zombie.getServer() == null) return;

        UUID id = tag.getUUID(HOPE_TAG);
        ServerPlayer player = zombie.getServer().getPlayerList().getPlayer(id);
        if (player == null || player.distanceTo(villager) > 64) return;
        RingData data = dataOf(player);
        if (data != null && canBeChosen(player, data)) {
            SelectionManager.startSelection(player, BuiltinCorps.BLUE.id());
        }
    }

    // ===================== VIOLETA (amor) =====================

    @SubscribeEvent
    public static void onBreed(BabyEntitySpawnEvent event) {
        if (!(event.getCausedByPlayer() instanceof ServerPlayer player)) return;
        RingData data = dataOf(player);
        if (data == null || !canBeChosen(player, data)) return;

        boolean companionNearby = !player.level().getEntitiesOfClass(TamableAnimal.class,
                player.getBoundingBox().inflate(12),
                pet -> pet.isTame() && player.getUUID().equals(pet.getOwnerUUID())).isEmpty();
        if (!companionNearby) return;

        int breeds = data.addLoveBreed();
        int needed = LanternaConfig.VIOLET_BREEDS.get();
        if (breeds >= needed) {
            SelectionManager.startSelection(player, BuiltinCorps.VIOLET.id());
        } else {
            player.displayClientMessage(Component.translatable("ritual.lanterna.violet.progress", breeds, needed)
                    .withStyle(s -> s.withColor(BuiltinCorps.VIOLET.color())), true);
        }
    }

    // ===================== NEGRO (morte) =====================

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        RingData data = dataOf(player);
        if (data == null || !canBeChosen(player, data)) return;

        boolean withering = player.hasEffect(MobEffects.WITHER)
                || event.getSource().is(DamageTypes.WITHER)
                || event.getSource().is(DamageTypes.WITHER_SKULL)
                || event.getSource().getEntity() instanceof WitherBoss;
        if (withering && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            data.setDiedWithering(true);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) return;
        RingData data = dataOf(player);
        if (data != null && data.diedWithering()) {
            data.setDiedWithering(false);
            // O Anel Preto não escolhe mais por ritual: ele só nasce da corrupção (morte/Corrupcao)
        }
    }

    // BRANCO (vida): não é mais escolhido por ritual. Na lore, o Anel Branco só nasce da fusão
    // dos sete anéis no Altar da Vida, em Gênese (veja lore/ritual/WhiteAltar).

    private static void hint(ServerPlayer player, String key, int color) {
        player.displayClientMessage(Component.translatable(key).withStyle(s -> s.withColor(color).withItalic(true)), true);
    }

    private RitualEvents() {}
}
