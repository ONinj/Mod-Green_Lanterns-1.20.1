package com.lanterna.lore;

import com.lanterna.LanternaMod;
import com.lanterna.lore.entity.LoreCreatures;
import com.lanterna.lore.entity.Race;
import com.lanterna.lore.entity.RaceEntity;
import com.lanterna.lore.entity.boss.Bosses;
import com.lanterna.lore.entity.boss.LoreBoss;
import com.lanterna.registry.ModEntities;
import com.lanterna.registry.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Entidades da lore: as raças, os Guardiões, Ecos, Brotos, alvos de treino e os nove bosses. */
public final class LoreEntities {
    private static final Map<Race, RegistryObject<EntityType<RaceEntity>>> RACES = new EnumMap<>(Race.class);
    private static final Map<String, RegistryObject<? extends EntityType<? extends LoreBoss>>> BOSSES = new LinkedHashMap<>();
    public static final List<RegistryObject<Item>> EGGS = new ArrayList<>();

    static {
        int[][] colors = {
                {0x2A2224, 0xFF4A20}, {0xB07A40, 0xFFC040}, {0x15151A, 0xFFE01A}, {0x6E7E74, 0x40FF70}, {0x8EC8FF, 0x2A7BFF},
                {0x2A2050, 0x7B6BFF}, {0xF0B0FF, 0xC23BFF}, {0xD8D0C0, 0x303038}, {0x18181C, 0x6A50C0}, {0xE8F0E8, 0x2BFF4A}};
        for (Race r : Race.values()) {
            RegistryObject<EntityType<RaceEntity>> type = ModEntities.ENTITIES.register(r.id,
                    () -> EntityType.Builder.<RaceEntity>of(RaceEntity::new, MobCategory.CREATURE)
                            .sized(r.width, r.height).clientTrackingRange(10)
                            .build(LanternaMod.id(r.id).toString()));
            RACES.put(r, type);
            int[] c = colors[r.ordinal()];
            EGGS.add(ModItems.ITEMS.register(r.id + "_spawn_egg", () -> new ForgeSpawnEggItem(type, c[0], c[1], new Item.Properties())));
        }
    }

    public static final RegistryObject<EntityType<LoreCreatures.Eco>> ECO_CINZENTO = ModEntities.ENTITIES.register("eco_cinzento",
            () -> EntityType.Builder.<LoreCreatures.Eco>of(LoreCreatures.Eco::new, MobCategory.MONSTER)
                    .sized(0.7f, 1.6f).clientTrackingRange(10).build(LanternaMod.id("eco_cinzento").toString()));
    public static final RegistryObject<EntityType<LoreCreatures.Broto>> BROTO = ModEntities.ENTITIES.register("broto",
            () -> EntityType.Builder.<LoreCreatures.Broto>of(LoreCreatures.Broto::new, MobCategory.CREATURE)
                    .sized(0.4f, 0.6f).clientTrackingRange(8).build(LanternaMod.id("broto").toString()));
    public static final RegistryObject<EntityType<LoreCreatures.Dummy>> ALVO_TREINO = ModEntities.ENTITIES.register("alvo_treino",
            () -> EntityType.Builder.<LoreCreatures.Dummy>of(LoreCreatures.Dummy::new, MobCategory.MISC)
                    .sized(0.8f, 1.9f).clientTrackingRange(8).build(LanternaMod.id("alvo_treino").toString()));

    public static final RegistryObject<EntityType<Bosses.Vorgath>> VORGATH = boss("vorgath", Bosses.Vorgath::new, 4.0f, 5.0f, true);
    public static final RegistryObject<EntityType<Bosses.Mammok>> MAMMOK = boss("mammok", Bosses.Mammok::new, 3.5f, 6.6f, false);
    public static final RegistryObject<EntityType<Bosses.Thessara>> THESSARA = boss("thessara", Bosses.Thessara::new, 2.6f, 4.5f, false);
    public static final RegistryObject<EntityType<Bosses.Vigia>> VIGIA_PARTIDO = boss("vigia_partido", Bosses.Vigia::new, 1.8f, 5.0f, false);
    public static final RegistryObject<EntityType<Bosses.Morrow>> MORROW = boss("morrow", Bosses.Morrow::new, 4.5f, 2.6f, false);
    public static final RegistryObject<EntityType<Bosses.Penitente>> PENITENTE = boss("penitente", Bosses.Penitente::new, 2.2f, 3.9f, false);
    public static final RegistryObject<EntityType<Bosses.Nyrielle>> NYRIELLE = boss("nyrielle", Bosses.Nyrielle::new, 2.2f, 3.6f, false);
    public static final RegistryObject<EntityType<Bosses.Devorador>> DEVORADOR_DE_ECOS = boss("devorador_de_ecos", Bosses.Devorador::new, 2.4f, 8.0f, true);
    public static final RegistryObject<EntityType<Bosses.Nulo>> NULO = boss("nulo", Bosses.Nulo::new, 0.8f, 2.1f, true);

    static {
        EGGS.add(ModItems.ITEMS.register("eco_cinzento_spawn_egg", () -> new ForgeSpawnEggItem(ECO_CINZENTO, 0x7A7A80, 0x404044, new Item.Properties())));
        EGGS.add(ModItems.ITEMS.register("broto_spawn_egg", () -> new ForgeSpawnEggItem(BROTO, 0xFFFFFF, 0xD0F0D0, new Item.Properties())));
        EGGS.add(ModItems.ITEMS.register("alvo_treino_spawn_egg", () -> new ForgeSpawnEggItem(ALVO_TREINO, 0x9A7040, 0x2BFF4A, new Item.Properties())));
    }

    private static <T extends LoreBoss> RegistryObject<EntityType<T>> boss(String id, EntityType.EntityFactory<T> factory,
                                                                            float w, float h, boolean fireImmune) {
        RegistryObject<EntityType<T>> type = ModEntities.ENTITIES.register(id, () -> {
            EntityType.Builder<T> b = EntityType.Builder.of(factory, MobCategory.MONSTER).sized(w, h).clientTrackingRange(12);
            if (fireImmune) b.fireImmune();
            return b.build(LanternaMod.id(id).toString());
        });
        BOSSES.put(id, type);
        EGGS.add(ModItems.ITEMS.register(id + "_spawn_egg", () -> new ForgeSpawnEggItem(type, 0x101010, 0xFFFFFF, new Item.Properties())));
        return type;
    }

    @Nullable
    public static Race raceOf(EntityType<?> type) {
        for (Map.Entry<Race, RegistryObject<EntityType<RaceEntity>>> e : RACES.entrySet()) {
            if (e.getValue().isPresent() && e.getValue().get() == type) return e.getKey();
        }
        return null;
    }

    public static EntityType<RaceEntity> typeOf(Race race) {
        return RACES.get(race).get();
    }

    @Nullable
    public static EntityType<?> bossType(String id) {
        var t = BOSSES.get(id);
        return t == null ? null : t.get();
    }

    public static Map<String, RegistryObject<? extends EntityType<? extends LoreBoss>>> bosses() {
        return BOSSES;
    }

    public static Map<Race, RegistryObject<EntityType<RaceEntity>>> races() {
        return RACES;
    }

    public static void init() {}

    @Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void attributes(EntityAttributeCreationEvent event) {
            for (Race r : Race.values()) event.put(typeOf(r), RaceEntity.attributes(r).build());
            event.put(ECO_CINZENTO.get(), LoreCreatures.Eco.attributes().build());
            event.put(BROTO.get(), LoreCreatures.Broto.attributes().build());
            event.put(ALVO_TREINO.get(), LoreCreatures.Dummy.attributes().build());
            event.put(VORGATH.get(), LoreBoss.attributes(420, 14, 0.30).build());
            event.put(MAMMOK.get(), LoreBoss.attributes(450, 16, 0.0).build());
            event.put(THESSARA.get(), LoreBoss.attributes(350, 10, 0.32).build());
            event.put(VIGIA_PARTIDO.get(), LoreBoss.attributes(300, 10, 0.28).build());
            event.put(MORROW.get(), LoreBoss.attributes(400, 12, 0.30).build());
            event.put(PENITENTE.get(), LoreBoss.attributes(380, 12, 0.24).build());
            event.put(NYRIELLE.get(), LoreBoss.attributes(360, 10, 0.25).build());
            event.put(DEVORADOR_DE_ECOS.get(), LoreBoss.attributes(600, 16, 0.26).build());
            event.put(NULO.get(), LoreBoss.attributes(800, 14, 0.30).build());
        }

        @SubscribeEvent
        public static void spawnRules(SpawnPlacementRegisterEvent event) {
            for (Race r : Race.values()) {
                event.register(typeOf(r), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        // Os povos só moram nas vilas e nas bases: nada de nascer solto pelo mapa
                        (type, level, reason, pos, random) -> reason != net.minecraft.world.entity.MobSpawnType.NATURAL
                                && reason != net.minecraft.world.entity.MobSpawnType.CHUNK_GENERATION
                                && level.getBlockState(pos.below()).isValidSpawn(level, pos.below(), type),
                        SpawnPlacementRegisterEvent.Operation.REPLACE);
            }
            event.register(ECO_CINZENTO.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (type, level, reason, pos, random) -> Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random)
                            && (level.getLevel() == null || LoreCreatures.Eco.allowSpawn(level.getLevel(), random))
                            && level.getEntitiesOfClass(LoreCreatures.Eco.class, new net.minecraft.world.phys.AABB(pos).inflate(48)).size() < 3,
                    SpawnPlacementRegisterEvent.Operation.REPLACE);
            event.register(BROTO.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mob::checkMobSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        }
    }

    private LoreEntities() {}
}
