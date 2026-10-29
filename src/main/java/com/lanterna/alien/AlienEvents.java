package com.lanterna.alien;

import com.lanterna.LanternaMod;
import com.lanterna.registry.ModEntities;
import com.lanterna.space.SpaceBodies;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Registro e regras dos alienígenas. */
public final class AlienEvents {

    @Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void attributes(EntityAttributeCreationEvent event) {
            event.put(ModEntities.ALIEN.get(), AlienEntity.createAttributes().build());
        }

        @SubscribeEvent
        public static void spawnRules(SpawnPlacementRegisterEvent event) {
            event.register(ModEntities.ALIEN.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (type, level, reason, pos, random) -> reason != net.minecraft.world.entity.MobSpawnType.NATURAL
                            && reason != net.minecraft.world.entity.MobSpawnType.CHUNK_GENERATION
                            && level.getBlockState(pos.below()).isValidSpawn(level, pos.below(), type),
                    SpawnPlacementRegisterEvent.Operation.REPLACE);
        }
    }

    @Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBus {
        private record Pending(ServerLevel level, Vec3 pos, float yaw) {}
        private static final List<Pending> PENDING = new ArrayList<>();

        /** Nas vilas dos planetas não há aldeões: quem mora lá são os alienígenas daquele planeta. */
        @SubscribeEvent
        public static void onJoin(EntityJoinLevelEvent event) {
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            if (!(event.getEntity() instanceof Villager villager)) return;
            if (!SpaceBodies.isPlanet(level.dimension())) return;
            event.setCanceled(true);
            synchronized (PENDING) {
                PENDING.add(new Pending(level, villager.position(), villager.getYRot()));
            }
        }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            List<Pending> todo;
            synchronized (PENDING) {
                if (PENDING.isEmpty()) return;
                todo = new ArrayList<>(PENDING);
                PENDING.clear();
            }
            for (Pending p : todo) {
                // Planetas das Tropas: quem mora na vila é a raça da lore daquele planeta
                com.lanterna.space.SpaceBodies.Body body = com.lanterna.space.SpaceBodies.bodyAt(p.level().dimension(), p.pos().x, p.pos().z);
                com.lanterna.lore.entity.Race race = body != null ? com.lanterna.lore.entity.Race.ofPlanet(body.id()) : null;
                if (race != null) {
                    var folk = com.lanterna.lore.LoreEntities.typeOf(race).create(p.level());
                    if (folk != null) {
                        folk.moveTo(p.pos().x, p.pos().y, p.pos().z, p.yaw(), 0);
                        folk.finalizeSpawn(p.level(), p.level().getCurrentDifficultyAt(folk.blockPosition()), MobSpawnType.STRUCTURE, null, null);
                        folk.setPersistenceRequired();
                        p.level().addFreshEntity(folk);
                    }
                    continue;
                }
                AlienEntity alien = ModEntities.ALIEN.get().create(p.level());
                if (alien == null) continue;
                alien.moveTo(p.pos().x, p.pos().y, p.pos().z, p.yaw(), 0);
                alien.assignSpeciesFromLocation();
                alien.finalizeSpawn(p.level(), p.level().getCurrentDifficultyAt(alien.blockPosition()), MobSpawnType.STRUCTURE, null, null);
                alien.setPersistenceRequired();
                boolean added = p.level().addFreshEntity(alien);
                LanternaMod.LOGGER.debug("Lanterna: aldeão virou alienígena ({}) em {} {}: {}", alien.speciesId(),
                        p.level().dimension().location(), alien.blockPosition(), added);
            }
        }
    }

    private AlienEvents() {}
}
