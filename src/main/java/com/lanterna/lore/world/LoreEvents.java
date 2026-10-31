package com.lanterna.lore.world;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.lore.LoreItems;
import com.lanterna.lore.PlanetLore;
import com.lanterna.lore.entity.LoreCreatures;
import com.lanterna.lore.entity.Race;
import com.lanterna.lore.entity.RaceEntity;
import com.lanterna.lore.entity.boss.LoreBoss;
import com.lanterna.mission.LoreCapability;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Eventos da lore: mortes (missões, almas, respeito), ataques a inocentes e o tick das missões. */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LoreEvents {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        MissionManager.tick(player);
        // Afinidade: 1 ponto por segundo com o anel ligado
        if (player.tickCount % 20 == 0) {
            RingData ring = ModCapabilities.get(player).orElse(null);
            if (ring != null && ring.hasRing() && ring.powerActive()) com.lanterna.construct.Affinity.add(player, 1);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) LoreCapability.get(p).ifPresent(d -> MissionManager.sync(p, d));
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) LoreCapability.get(p).ifPresent(d -> MissionManager.sync(p, d));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        MissionManager.onKill(player, victim);
        LoreData data = MissionManager.data(player);

        // Animais pacíficos: o ritual índigo pede 3 dias sem matar nenhum
        if (victim instanceof Animal && !(victim instanceof Enemy) && data != null) {
            data.setLastPassiveKill(player.level().getGameTime());
        }

        // Ecos Cinzentos: Aelínos e Veltari respeitam quem protege o planeta
        if (victim instanceof LoreCreatures.Eco && data != null) {
            PlanetLore here = PlanetLore.byDimension(player.level().dimension());
            if (here == PlanetLore.ODYM) data.addRep("aelinos", 1);
            if (here == PlanetLore.OA) data.addRep("veltari", 1);
        }

        // Almas: quem mata com o Anel Preto ligado recolhe a alma (moeda do Coletor)
        RingData ring = ModCapabilities.get(player).orElse(null);
        if ((ring != null && ring.hasRing() && ring.powerActive() && ring.corps().equals(BuiltinCorps.BLACK.id())
                || com.lanterna.morte.Corrupcao.colheAlmas(player))
                && !(victim instanceof RaceEntity r && r.race() == Race.GUARDIAO)) {
            boolean big = victim instanceof LoreBoss || victim.getMaxHealth() >= 40;
            if (big || player.getRandom().nextFloat() < 0.6f) {
                victim.spawnAtLocation(new ItemStack(LoreItems.ALMA.get(), big ? 4 : 1));
                ((ServerLevel) victim.level()).sendParticles(ParticleTypes.SOUL, victim.getX(), victim.getY() + 1, victim.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
            }
        }
    }

    /** Ferir um inocente (animal, aldeão, morador pacífico) deixa os Ves'Kai por perto hostis. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        boolean innocent = victim instanceof Animal && !(victim instanceof Enemy)
                || victim instanceof AbstractVillager && !(victim instanceof RaceEntity r && r.hostileTo(player));
        if (innocent) RaceEntity.innocentHarmed(player, victim);
    }

    private LoreEvents() {}
}
