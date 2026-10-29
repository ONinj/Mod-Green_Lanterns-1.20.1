package com.lanterna.lore.world;

import com.lanterna.LanternaMod;
import com.lanterna.block.BatteryShape;
import com.lanterna.block.PowerBatteryBlock;
import com.lanterna.block.PowerBatteryBlockEntity;
import com.lanterna.lore.LoreBlocks;
import com.lanterna.lore.LoreEntities;
import com.lanterna.lore.LoreWorldData;
import com.lanterna.lore.PlanetLore;
import com.lanterna.lore.block.LoreBlockTypes;
import com.lanterna.lore.entity.Race;
import com.lanterna.lore.entity.RaceEntity;
import com.lanterna.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Constrói (uma vez por mundo) a base e a arena de cada planeta das Tropas quando alguém chega perto:
 *
 *   Base   (0, 0)    plataforma da pedra do planeta, Forja do Espectro, bateria da Tropa, líder do povo,
 *                    moradores e três alvos de treino. Em Oa: a cidadela com a Bateria Central gigante,
 *                    o Instrutor Veltari e os três Guardiões do Universo.
 *   Arena  (120, 0)  plataforma grande com o Altar do Desafio (em Odym, um lago com três faróis de pérola;
 *                    em Ryut, duas caveiras-bateria que iluminam contra o dreno do Devorador).
 *   Gênese           o Altar da Vida no centro e a rachadura do Nulo ao lado.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlanetSites {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 7) return;
        ServerLevel level = player.serverLevel();
        PlanetLore planet = PlanetLore.byDimension(level.dimension());
        if (planet == null) return;
        LoreWorldData data = LoreWorldData.get(level.getServer());
        // Uma construção por vez (a cada segundo), para não travar o servidor
        if (!tryBuild(level, data, planet, "hub", PlanetLore.HUB, player, 220))
            tryBuild(level, data, planet, "arena", PlanetLore.arena(planet), player, 140);
    }

    private static boolean tryBuild(ServerLevel level, LoreWorldData data, PlanetLore planet, String kind, BlockPos at, ServerPlayer near, int radius) {
        String id = planet.planet + ":" + kind;
        if (data.siteBuilt(id)) return false;
        double dx = near.getX() - at.getX(), dz = near.getZ() - at.getZ();
        if (dx * dx + dz * dz > (double) radius * radius) return false;
        data.markSite(id);
        try {
            if (planet == PlanetLore.GENESE) {
                if (kind.equals("hub")) buildGeneseAltar(level, at);
                else buildRift(level, at);
            } else if (kind.equals("hub")) {
                if (planet == PlanetLore.OA) buildCitadel(level);
                else buildHub(level, planet);
            } else {
                buildArena(level, planet);
            }
            LanternaMod.LOGGER.info("Lanterna: construída a {} de {}", kind, planet.planet);
        } catch (Exception e) {
            LanternaMod.LOGGER.error("Lanterna: falha ao construir {} em {}", kind, planet.planet, e);
        }
        return true;
    }

    /** Reconstrói (comando /lanterna lore rebuild). */
    public static void rebuild(ServerLevel level, PlanetLore planet) {
        LoreWorldData data = LoreWorldData.get(level.getServer());
        data.resetSite(planet.planet + ":hub");
        data.resetSite(planet.planet + ":arena");
    }

    // ===================== Ajudantes =====================

    /** Altura do chão (ou da água) média em volta do ponto. */
    private static int surface(ServerLevel level, BlockPos c, int r) {
        int sum = 0, n = 0;
        for (int dx = -r; dx <= r; dx += Math.max(1, r / 2)) {
            for (int dz = -r; dz <= r; dz += Math.max(1, r / 2)) {
                level.getChunk((c.getX() + dx) >> 4, (c.getZ() + dz) >> 4);
                sum += level.getHeight(Heightmap.Types.WORLD_SURFACE, c.getX() + dx, c.getZ() + dz);
                n++;
            }
        }
        int y = sum / Math.max(1, n);
        return Math.max(level.getMinBuildHeight() + 10, Math.min(y, level.getMaxBuildHeight() - 40));
    }

    private static void set(ServerLevel level, BlockPos p, BlockState s) {
        level.setBlock(p, s, 2);
    }

    /** Plataforma redonda: chão de pedra, borda de destaque, terra firme embaixo e céu limpo em cima. */
    private static void platform(ServerLevel level, BlockPos center, int y, int r, BlockState floor, BlockState border, BlockState fill) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 0.5) continue;
                int x = center.getX() + dx, z = center.getZ() + dz;
                set(level, new BlockPos(x, y - 1, z), d > r - 1 ? border : floor);
                for (int k = 2; k < 14; k++) {
                    BlockPos below = new BlockPos(x, y - k, z);
                    BlockState s = level.getBlockState(below);
                    if (!s.isAir() && !s.liquid() && !s.canBeReplaced()) break;
                    set(level, below, fill);
                }
                for (int k = 0; k < 16; k++) {
                    BlockPos above = new BlockPos(x, y + k, z);
                    if (!level.getBlockState(above).isAir()) set(level, above, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void pillar(ServerLevel level, BlockPos base, int height, BlockState body, BlockState top) {
        for (int k = 0; k < height; k++) set(level, base.above(k), body);
        set(level, base.above(height), top);
    }

    private static void battery(ServerLevel level, BlockPos p, PlanetLore planet) {
        BlockState s = ModBlocks.POWER_BATTERY.get().defaultBlockState().setValue(PowerBatteryBlock.CORPS, BatteryShape.of(planet.corps.id()));
        set(level, p, s);
        if (level.getBlockEntity(p) instanceof PowerBatteryBlockEntity be) be.setCorps(planet.corps.id(), planet.color());
    }

    private static BlockState accent(PlanetLore p) {
        return switch (p) {
            case YSMAULT -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case OKAARA -> Blocks.GOLD_BLOCK.defaultBlockState();
            case KORUGAR -> Blocks.YELLOW_GLAZED_TERRACOTTA.defaultBlockState();
            case ODYM -> Blocks.PRISMARINE_BRICKS.defaultBlockState();
            case NOK -> Blocks.PURPLE_GLAZED_TERRACOTTA.defaultBlockState();
            case ZAMARON -> Blocks.AMETHYST_BLOCK.defaultBlockState();
            case RYUT -> Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
            case OA -> Blocks.EMERALD_BLOCK.defaultBlockState();
            default -> Blocks.QUARTZ_BLOCK.defaultBlockState();
        };
    }

    private static BlockState lamp(PlanetLore p) {
        return switch (p) {
            case YSMAULT -> Blocks.SHROOMLIGHT.defaultBlockState();
            case RYUT -> Blocks.SOUL_LANTERN.defaultBlockState();
            case NOK, ZAMARON -> LoreBlocks.CRISTAL_DEVOTO_AGLOMERADO.get().defaultBlockState().setValue(LoreBlockTypes.CristalDevoto.ACESO, true);
            case ODYM -> Blocks.SEA_LANTERN.defaultBlockState();
            case KORUGAR -> Blocks.OCHRE_FROGLIGHT.defaultBlockState();
            default -> Blocks.GLOWSTONE.defaultBlockState();
        };
    }

    @Nullable
    private static RaceEntity npc(ServerLevel level, Race race, BlockPos at, String role, @Nullable String nameKey) {
        RaceEntity e = LoreEntities.typeOf(race).create(level);
        if (e == null) return null;
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        e.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.STRUCTURE, null, null);
        e.setRole(role, role.isEmpty() ? null : at);
        e.setPersistenceRequired();
        if (nameKey != null) {
            e.setCustomName(Component.translatable(nameKey));
            e.setCustomNameVisible(true);
        }
        if (role.equals("lider") && race == Race.SKRELL_VATH) e.setFlag(RaceEntity.LEADER_MASK, true);
        if (race == Race.VES_KAI) e.setFlag(RaceEntity.WOUNDED, false);
        level.addFreshEntity(e);
        return e;
    }

    private static void dummies(ServerLevel level, BlockPos origin, int y, int z) {
        for (int i = -1; i <= 1; i++) {
            Entity d = LoreEntities.ALVO_TREINO.get().create(level);
            if (d == null) continue;
            d.moveTo(origin.getX() + i * 3 + 0.5, y, origin.getZ() + z + 0.5, 180, 0);
            level.addFreshEntity(d);
        }
    }

    // ===================== Base comum =====================

    private static void buildHub(ServerLevel level, PlanetLore planet) {
        BlockPos c = PlanetLore.HUB;
        int y = surface(level, c, 8);
        BlockState stone = planet.stoneBlock().defaultBlockState();
        platform(level, c, y, 10, stone, accent(planet), stone);
        set(level, new BlockPos(c.getX(), y, c.getZ() - 5), LoreBlocks.FORJA.get().defaultBlockState());
        if (planet != PlanetLore.NOK) battery(level, new BlockPos(c.getX() + 4, y, c.getZ() - 5), planet);
        for (int[] corner : new int[][]{{-7, -7}, {7, -7}, {-7, 7}, {7, 7}}) {
            pillar(level, new BlockPos(c.getX() + corner[0], y, c.getZ() + corner[1]), 3, stone, lamp(planet));
        }
        Race race = Race.ofPlanet(planet.planet);
        if (planet == PlanetLore.RYUT) race = Race.COLETOR;
        if (race != null) {
            RaceEntity leader = npc(level, race, new BlockPos(c.getX() - 3, y, c.getZ() - 3), "lider", "npc.lanterna.name." + planet.planet);
            Race folk = Race.ofPlanet(planet.planet);
            if (folk != null && planet != PlanetLore.ZAMARON) {
                npc(level, folk, new BlockPos(c.getX() + 5, y, c.getZ() + 2), "", null);
                npc(level, folk, new BlockPos(c.getX() - 5, y, c.getZ() + 3), "", null);
            }
            if (leader != null && planet == PlanetLore.ZAMARON) {
                // A líder de Zamaron vive com o seu par, ligado por um fio de luz
                RaceEntity mate = npc(level, Race.ZAELITH, new BlockPos(c.getX() - 1, y, c.getZ() - 3), "lider", "npc.lanterna.name.zamaron_par");
                if (mate != null) leader.setPartner(mate);
            }
        }
        dummies(level, c, y, 6);
    }

    // ===================== Cidadela de Oa =====================

    private static void buildCitadel(ServerLevel level) {
        BlockPos c = PlanetLore.HUB;
        int y = surface(level, c, 10);
        BlockState rock = LoreBlocks.ROCHA_POLIDA_VERIDIA.get().defaultBlockState();
        BlockState jade = LoreBlocks.JADE_ESTELAR_MINERIO.get().defaultBlockState();
        BlockState glass = Blocks.LIME_STAINED_GLASS.defaultBlockState();
        platform(level, c, y, 15, rock, Blocks.EMERALD_BLOCK.defaultBlockState(), rock);
        // Linhas de luz verde no chão (simétricas, como tudo em Oa)
        for (int i = -14; i <= 14; i++) {
            set(level, new BlockPos(c.getX() + i, y - 1, c.getZ()), jade);
            set(level, new BlockPos(c.getX(), y - 1, c.getZ() + i), jade);
        }
        // Bateria Central gigante: torre de vidro verde com núcleo de luz
        for (int k = 0; k < 18; k++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                    BlockPos p = new BlockPos(c.getX() + dx, y + k, c.getZ() + dz);
                    if (corner) set(level, p, k % 6 == 5 ? Blocks.EMERALD_BLOCK.defaultBlockState() : rock);
                    else if (edge) set(level, p, (k < 2 || k > 15) ? rock : glass);
                    else set(level, p, k < 2 ? rock : Blocks.SEA_LANTERN.defaultBlockState());
                }
            }
        }
        for (int k = 18; k < 22; k++) set(level, new BlockPos(c.getX(), y + k, c.getZ()), Blocks.END_ROD.defaultBlockState());
        // Forja e bateria
        set(level, new BlockPos(c.getX() + 7, y, c.getZ() - 7), LoreBlocks.FORJA.get().defaultBlockState());
        battery(level, new BlockPos(c.getX() - 7, y, c.getZ() - 7), PlanetLore.OA);
        // Estrado dos Guardiões (ao sul)
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = 8; dz <= 11; dz++) set(level, new BlockPos(c.getX() + dx, y, c.getZ() + dz), rock);
        }
        for (int dx = -5; dx <= 5; dx += 10) pillar(level, new BlockPos(c.getX() + dx, y + 1, c.getZ() + 11), 4, rock, Blocks.GLOWSTONE.defaultBlockState());
        String[] names = {"npc.lanterna.name.guardiao_1", "npc.lanterna.name.guardiao_2", "npc.lanterna.name.guardiao_3"};
        for (int i = 0; i < 3; i++) {
            npc(level, Race.GUARDIAO, new BlockPos(c.getX() - 3 + i * 3, y + 1, c.getZ() + 10), "guardiao", names[i]);
        }
        // Instrutor Veltari, moradores e os alvos de treino (a leste)
        npc(level, Race.VELTARI, new BlockPos(c.getX(), y, c.getZ() - 8), "instrutor", "npc.lanterna.name.oa_instrutor");
        npc(level, Race.VELTARI, new BlockPos(c.getX() - 9, y, c.getZ() + 2), "", null);
        npc(level, Race.VELTARI, new BlockPos(c.getX() - 8, y, c.getZ() - 3), "", null);
        for (int i = -1; i <= 1; i++) {
            Entity d = LoreEntities.ALVO_TREINO.get().create(level);
            if (d == null) continue;
            d.moveTo(c.getX() + 11.5, y, c.getZ() + i * 3 + 0.5, 90, 0);
            level.addFreshEntity(d);
        }
        // Postes de luz
        for (int[] p : new int[][]{{-12, -12}, {12, -12}, {-12, 12}, {12, 12}}) {
            pillar(level, new BlockPos(c.getX() + p[0], y, c.getZ() + p[1]), 5, rock, Blocks.GLOWSTONE.defaultBlockState());
        }
    }

    // ===================== Arena =====================

    private static void buildArena(ServerLevel level, PlanetLore planet) {
        BlockPos c = PlanetLore.arena(planet);
        int y = surface(level, c, 12);
        BlockState stone = planet.stoneBlock().defaultBlockState();
        platform(level, c, y, 19, stone, accent(planet), stone);
        BlockPos altar = new BlockPos(c.getX(), y, c.getZ());
        if (planet == PlanetLore.ODYM) {
            // Lago no meio e o altar na margem sul
            for (int dx = -13; dx <= 13; dx++) {
                for (int dz = -13; dz <= 13; dz++) {
                    if (dx * dx + dz * dz > 13 * 13) continue;
                    for (int k = 1; k <= 7; k++) set(level, new BlockPos(c.getX() + dx, y - k, c.getZ() + dz), Blocks.WATER.defaultBlockState());
                    set(level, new BlockPos(c.getX() + dx, y - 8, c.getZ() + dz), LoreBlocks.AREIA_ABISSAL_LUMERIS.get().defaultBlockState());
                }
            }
            altar = new BlockPos(c.getX(), y, c.getZ() + 15);
            for (int i = 0; i < 3; i++) {
                double a = i * Math.PI * 2 / 3 + Math.PI / 2;
                BlockPos base = new BlockPos(c.getX() + (int) Math.round(Math.cos(a) * 16), y, c.getZ() + (int) Math.round(Math.sin(a) * 16));
                pillar(level, base, 2, Blocks.PRISMARINE_BRICKS.defaultBlockState(), LoreBlocks.FAROL_PEROLA.get().defaultBlockState());
            }
        } else {
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                BlockPos base = new BlockPos(c.getX() + (int) Math.round(Math.cos(a) * 17), y, c.getZ() + (int) Math.round(Math.sin(a) * 17));
                pillar(level, base, 4 + (i % 2) * 2, stone, lamp(planet));
            }
        }
        if (planet == PlanetLore.RYUT) {
            battery(level, new BlockPos(c.getX() + 12, y, c.getZ()), planet);
            battery(level, new BlockPos(c.getX() - 12, y, c.getZ()), planet);
        }
        set(level, altar, LoreBlocks.ALTAR_DESAFIO.get().defaultBlockState());
    }

    // ===================== Gênese =====================

    private static void buildGeneseAltar(ServerLevel level, BlockPos c) {
        int y = surface(level, c, 8);
        BlockState white = Blocks.QUARTZ_BLOCK.defaultBlockState();
        platform(level, c, y, 10, Blocks.SMOOTH_QUARTZ.defaultBlockState(), Blocks.QUARTZ_PILLAR.defaultBlockState(),
                LoreBlocks.TERRA_BRANCA.get().defaultBlockState());
        set(level, new BlockPos(c.getX(), y, c.getZ()), LoreBlocks.ALTAR_VIDA.get().defaultBlockState());
        for (int i = 0; i < 7; i++) {
            double a = i * Math.PI * 2 / 7;
            BlockPos base = new BlockPos(c.getX() + (int) Math.round(Math.cos(a) * 6), y, c.getZ() + (int) Math.round(Math.sin(a) * 6));
            PlanetLore p = PlanetLore.values()[i];
            pillar(level, base, 2, white, accent(p));
        }
    }

    private static void buildRift(ServerLevel level, BlockPos c) {
        int y = surface(level, c, 10);
        platform(level, c, y, 14, Blocks.SMOOTH_STONE.defaultBlockState(), Blocks.GRAY_CONCRETE.defaultBlockState(),
                Blocks.STONE.defaultBlockState());
        for (int k = 0; k < 6; k++) {
            set(level, new BlockPos(c.getX(), y + k, c.getZ()), LoreBlocks.RACHADURA.get().defaultBlockState());
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 0 && level.random.nextInt(3) == 0)
                    set(level, new BlockPos(c.getX() + dx, y - 1, c.getZ() + dz), Blocks.TUFF.defaultBlockState());
            }
        }
    }

    private PlanetSites() {}
}
