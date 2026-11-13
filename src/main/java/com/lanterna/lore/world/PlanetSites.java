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
        if (!tryBuild(level, data, planet, "hub", PlanetLore.HUB, player, 220)
                && !tryBuild(level, data, planet, "arena", PlanetLore.arena(planet), player, 140)
                && (planet == PlanetLore.GENESE || !tryBuild(level, data, planet, "arena2", PlanetLore.arena(planet), player, 140))
                && !tryBuild(level, data, planet, "hub2", PlanetLore.HUB, player, 160)
                && (planet == PlanetLore.OA || planet == PlanetLore.GENESE || !tryBuild(level, data, planet, "guardiao", PlanetLore.HUB, player, 120))
                && planet != PlanetLore.GENESE
                && !tryBuild(level, data, planet, "tropa", PlanetLore.HUB, player, 120))
        {
            boolean ocupada = com.lanterna.cidade.Obra.ocupada();
            if (!tryBuild(level, data, planet, "cidade2", com.lanterna.cidade.Cidade.CENTRO, player, 200) && planet == PlanetLore.OA)
                if (!tryBuild(level, data, planet, "palacio", com.lanterna.cidade.Palacio.CENTRO.atY(0), player, 260)
                        && data.siteBuilt("oa:palacio") && !ocupada)
                    tryBuild(level, data, planet, "palacio_degraus", com.lanterna.cidade.Palacio.CENTRO.atY(0), player, 120);
            ocupada = com.lanterna.cidade.Obra.ocupada();
            // consertos de construções antigas (uma vez por mundo) e a cidade dos nativos
            if (!ocupada && planet == PlanetLore.OA && data.siteBuilt("oa:palacio"))
                tryBuild(level, data, planet, "palacio_fontes", com.lanterna.cidade.Palacio.CENTRO.atY(0), player, 140);
            if (!com.lanterna.cidade.Obra.ocupada() && data.siteBuilt(planet.planet + ":cidade2"))
                tryBuild(level, data, planet, "cidade2_agua", com.lanterna.cidade.Cidade.CENTRO, player, 160);
            if (!com.lanterna.cidade.Obra.ocupada() && planet != PlanetLore.GENESE)
                tryBuild(level, data, planet, "nativa", com.lanterna.cidade.CidadeNativa.CENTRO, player, 1000);
            // mundos com a cidade pequena da 0.25: vira a megacidade do povo
            if (!com.lanterna.cidade.Obra.ocupada() && planet != PlanetLore.GENESE && data.siteBuilt(planet.planet + ":nativa"))
                tryBuild(level, data, planet, "megacidade", com.lanterna.cidade.CidadeNativa.CENTRO, player, 1000);
            // cidades da 0.26/0.27: ganham o bairro novo e mais moradores
            if (!com.lanterna.cidade.Obra.ocupada() && planet != PlanetLore.GENESE && data.siteBuilt(planet.planet + ":megacidade"))
                tryBuild(level, data, planet, "megacidade_densa", com.lanterna.cidade.CidadeNativa.CENTRO, player, 300);
        }
    }

    private static boolean tryBuild(ServerLevel level, LoreWorldData data, PlanetLore planet, String kind, BlockPos at, ServerPlayer near, int radius) {
        String id = planet.planet + ":" + kind;
        if (data.siteBuilt(id)) return false;
        double dx = near.getX() - at.getX(), dz = near.getZ() - at.getZ();
        if (dx * dx + dz * dz > (double) radius * radius) return false;
        data.markSite(id);
        try {
            if (kind.equals("guardiao")) {
                placeGuardian(level, planet);
            } else if (kind.equals("tropa")) {
                placeTropa(level, planet);
            } else if (kind.equals("cidade")) {
                CidadeTropa.construir(level, planet);
            } else if (kind.equals("palacio_fontes")) {
                com.lanterna.cidade.Palacio.consertarFontes(level);
            } else if (kind.equals("cidade2_agua")) {
                com.lanterna.cidade.Cidade.consertarAgua(level);
            } else if (kind.equals("nativa")) {
                com.lanterna.cidade.CidadeNativa.construir(level, planet);
                data.markSite(planet.planet + ":megacidade"); // já nasce grande, com a arquitetura do povo
                data.markSite(planet.planet + ":megacidade_densa"); // e com o bairro novo
            } else if (kind.equals("megacidade")) {
                com.lanterna.cidade.CidadeNativa.refazer(level, planet);
                data.markSite(planet.planet + ":megacidade_densa");
            } else if (kind.equals("megacidade_densa")) {
                com.lanterna.cidade.CidadesPovos.adensar(level, planet, com.lanterna.cidade.CidadeNativa.CENTRO.atY(com.lanterna.cidade.CidadeNativa.altura(level)));
                com.lanterna.cidade.Obra.etapa(level, l -> com.lanterna.cidade.CidadeNativa.nativos(l, com.lanterna.cidade.CidadeNativa.CENTRO.atY(com.lanterna.cidade.CidadeNativa.altura(l)), planet, com.lanterna.cidade.CidadesPovos.NATIVOS));
            } else if (kind.equals("palacio_degraus")) {
                com.lanterna.cidade.Palacio.consertarTronos(level);
            } else if (kind.equals("cidade2")) {
                com.lanterna.cidade.Cidade.construir(level, planet);
                data.markSite(planet.planet + ":cidade2_agua"); // a cidade nova já vem selada
            } else if (kind.equals("palacio")) {
                com.lanterna.cidade.Palacio.construir(level);
                data.markSite(planet.planet + ":palacio_degraus"); // o Palácio novo já vem com os degraus
                data.markSite(planet.planet + ":palacio_fontes");
            } else if (kind.equals("hub2")) {
                // 0.47.0: a base ganha arquitetura (mundos antigos também); sem mexer nos NPCs
                if (planet == PlanetLore.GENESE) decorarGenese(level, at);
                else if (planet == PlanetLore.OA) decorarCitadela(level);
                else decorarHub(level, planet);
            } else if (planet == PlanetLore.GENESE) {
                if (kind.equals("hub")) buildGeneseAltar(level, at);
                else buildRift(level, at);
            } else if (kind.equals("hub")) {
                if (planet == PlanetLore.OA) buildCitadel(level);
                else buildHub(level, planet);
            } else {
                buildArena(level, planet);
                data.markSite(planet.planet + ":arena2");
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
        data.resetSite(planet.planet + ":cidade2");
        data.resetSite(planet.planet + ":palacio");
        data.resetSite(planet.planet + ":palacio_degraus");
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

    /** Um morador comum do povo do planeta (para a cidade dos nativos). */
    @Nullable
    public static RaceEntity nativo(ServerLevel level, Race race, BlockPos at) {
        return npc(level, race, at, "", null);
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
        // líderes e NPCs de missão nunca estão feridos; os moradores comuns podem estar (35%, ver RaceEntity)
        if (race == Race.VES_KAI && !role.isEmpty()) e.setFlag(RaceEntity.WOUNDED, false);
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

    /**
     * Um Guardião do Universo em cada planeta das Tropas: assim nenhum Lanterna precisa ir a Oa
     * (o Amarelo e o Vermelho não seriam bem-vindos lá). Ele fica num pequeno estrado ao lado da base.
     */
    private static void placeGuardian(ServerLevel level, PlanetLore planet) {
        BlockPos c = PlanetLore.HUB;
        if (!level.getEntitiesOfClass(RaceEntity.class, new net.minecraft.world.phys.AABB(c).inflate(40),
                e -> e.race() == Race.GUARDIAO).isEmpty()) return;
        BlockPos spot = new BlockPos(c.getX() + 3, 0, c.getZ() + 9);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot.getX(), spot.getZ());
        BlockState stone = planet.stoneBlock().defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(level, new BlockPos(spot.getX() + dx, y, spot.getZ() + dz), stone);
        set(level, new BlockPos(spot.getX(), y, spot.getZ()), Blocks.EMERALD_BLOCK.defaultBlockState());
        npc(level, Race.GUARDIAO, new BlockPos(spot.getX(), y + 1, spot.getZ()), "guardiao", "npc.lanterna.name.guardiao_emissario");
    }

    /**
     * A Tropa do planeta na base: 3 Lanternas NPCs (um oficial) que patrulham e ajudam quem é da mesma cor,
     * e o quadro de contratos numa parede ao norte.
     */
    private static void placeTropa(ServerLevel level, PlanetLore planet) {
        BlockPos c = PlanetLore.HUB;
        com.lanterna.corps.BuiltinCorps corps = planet.corps;
        for (int i = 0; i < 3; i++) {
            var l = com.lanterna.especies.LanternaEntity.lanterna(level, corps, i == 0);
            BlockPos at = new BlockPos(c.getX() - 6 + i * 6, 0, c.getZ() + 4);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
            l.moveTo(at.getX() + 0.5, y, at.getZ() + 0.5, level.random.nextFloat() * 360, 0);
            l.restrictTo(new BlockPos(at.getX(), y, at.getZ()), 20);
            level.addFreshEntity(l);
        }
        // Parede com o quadro de contratos
        BlockPos w = new BlockPos(c.getX() + 3, 0, c.getZ() - 12);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, w.getX(), w.getZ());
        BlockState stone = planet.stoneBlock().defaultBlockState();
        for (int dx = -2; dx <= 1; dx++) {
            for (int dy = 0; dy < 3; dy++) set(level, new BlockPos(w.getX() + dx, y + dy, w.getZ()), stone);
            set(level, new BlockPos(w.getX() + dx, y + 3, w.getZ()), com.lanterna.naves.bloco.NaveBlocks.NAVE_PLACA_FAIXA.get().defaultBlockState());
        }
        var q = com.lanterna.naves.bloco.NaveBlocks.QUADRO_CONTRATOS.get().defaultBlockState()
                .setValue(com.lanterna.naves.bloco.QuadroContratos.FACING, net.minecraft.core.Direction.SOUTH);
        BlockPos a = new BlockPos(w.getX(), y + 1, w.getZ() + 1);
        // facing=south: a parte _esq fica a oeste (-x) da _dir
        set(level, a, q.setValue(com.lanterna.naves.bloco.QuadroContratos.PARTE, com.lanterna.naves.bloco.QuadroContratos.Parte.BAIXO_DIR));
        set(level, a.west(), q.setValue(com.lanterna.naves.bloco.QuadroContratos.PARTE, com.lanterna.naves.bloco.QuadroContratos.Parte.BAIXO_ESQ));
        set(level, a.above(), q.setValue(com.lanterna.naves.bloco.QuadroContratos.PARTE, com.lanterna.naves.bloco.QuadroContratos.Parte.CIMA_DIR));
        set(level, a.above().west(), q.setValue(com.lanterna.naves.bloco.QuadroContratos.PARTE, com.lanterna.naves.bloco.QuadroContratos.Parte.CIMA_ESQ));
    }

    // ===================== Vigia dos NPCs da base =====================

    /**
     * Os NPCs de missão da base (líder do povo, Guardião emissário, os três Guardiões e o Instrutor de Oa) nasciam
     * só quando a base era construída. Se sumirem, voltam ao lugar deles (chamado pelo vigia de moradores).
     */
    public static int vigiarBase(ServerLevel level, PlanetLore planet) {
        BlockPos c = PlanetLore.HUB;
        if (!level.isLoaded(c)) return 0;
        // a base fica em cima do terreno: a busca vai do fundo ao teto do mundo
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(c.getX() - 48, level.getMinBuildHeight(), c.getZ() - 48,
                c.getX() + 48, level.getMaxBuildHeight(), c.getZ() + 48);
        // Limpa repetidos (a 0.23 criava um novo a cada 30 s): fica só a quantidade certa de cada um
        java.util.Map<String, Integer> limite = new java.util.HashMap<>();
        if (planet == PlanetLore.OA) { limite.put("guardiao", 3); limite.put("instrutor", 1); }
        else { limite.put("guardiao", 1); limite.put("lider", planet == PlanetLore.ZAMARON ? 2 : 1); }
        for (var en : limite.entrySet()) {
            java.util.List<RaceEntity> l = level.getEntitiesOfClass(RaceEntity.class, area, e -> e.isAlive() && e.role().equals(en.getKey()));
            l.sort(java.util.Comparator.comparingInt(net.minecraft.world.entity.Entity::getId));
            for (int i = en.getValue(); i < l.size(); i++) l.get(i).discard();
            if (l.size() > en.getValue()) LanternaMod.LOGGER.info("Lanterna: {} {} repetido(s) removido(s) em {}", l.size() - en.getValue(), en.getKey(), planet.planet);
        }
        java.util.function.Function<String, Long> quantos = role -> level.getEntitiesOfClass(RaceEntity.class, area,
                e -> e.isAlive() && e.role().equals(role)).stream().count();
        java.util.function.BiFunction<Integer, Integer, Integer> chao = (x, z) ->
                level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX() + x, c.getZ() + z);
        int n = 0;
        if (planet == PlanetLore.NOK) n += feridosNok(level, c, area, chao);
        if (planet == PlanetLore.OA) {
            String[] names = {"npc.lanterna.name.guardiao_1", "npc.lanterna.name.guardiao_2", "npc.lanterna.name.guardiao_3"};
            for (long i = quantos.apply("guardiao"); i < 3; i++, n++) {
                int x = -3 + (int) i * 3;
                npc(level, Race.GUARDIAO, new BlockPos(c.getX() + x, chao.apply(x, 10), c.getZ() + 10), "guardiao", names[(int) i]);
            }
            if (quantos.apply("instrutor") == 0) { npc(level, Race.VELTARI, new BlockPos(c.getX(), chao.apply(0, -8), c.getZ() - 8), "instrutor", "npc.lanterna.name.oa_instrutor"); n++; }
        } else if (planet != PlanetLore.GENESE) {
            Race race = planet == PlanetLore.RYUT ? Race.COLETOR : Race.ofPlanet(planet.planet);
            if (race != null && quantos.apply("lider") == 0) { npc(level, race, new BlockPos(c.getX() - 3, chao.apply(-3, -3), c.getZ() - 3), "lider", "npc.lanterna.name." + planet.planet); n++; }
            if (quantos.apply("guardiao") == 0) { npc(level, Race.GUARDIAO, new BlockPos(c.getX() + 3, chao.apply(3, 9), c.getZ() + 9), "guardiao", "npc.lanterna.name.guardiao_emissario"); n++; }
        }
        return n;
    }

    /**
     * Nok: em volta da base sempre há alguns Ves'Kai feridos (ajoelhados, apoiados no cajado) esperando cura. Quando
     * um é curado ele vira um morador comum e, de tempos em tempos, outro ferido chega (até 4 feridos, 12 Ves'Kai).
     */
    private static int feridosNok(ServerLevel level, BlockPos c, net.minecraft.world.phys.AABB area,
                                  java.util.function.BiFunction<Integer, Integer, Integer> chao) {
        var todos = level.getEntitiesOfClass(RaceEntity.class, area, e -> e.isAlive() && e.race() == Race.VES_KAI && e.role().isEmpty());
        long feridos = todos.stream().filter(e -> e.hasFlag(RaceEntity.WOUNDED)).count();
        int n = 0;
        for (int t = 0; t < 24 && feridos + n < 4 && todos.size() + n < 12; t++) {
            // em volta da base; se em volta for água, em cima da própria plataforma (raio 10)
            double a = level.random.nextDouble() * Math.PI * 2, r = (t < 12 ? 13 + level.random.nextDouble() * 9 : 6 + level.random.nextDouble() * 3);
            int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r);
            BlockPos at = new BlockPos(c.getX() + x, chao.apply(x, z), c.getZ() + z);
            // em terra firme: nada de água (eles ficam parados) nem em cima de folhas
            if (!level.getFluidState(at).isEmpty() || !level.getFluidState(at.below()).isEmpty()
                    || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)) continue;
            RaceEntity e = nativo(level, Race.VES_KAI, at);
            if (e == null) continue;
            e.setFlag(RaceEntity.WOUNDED, true);
            e.setHealth(e.getMaxHealth() * 0.35f);
            n++;
        }
        return n;
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
        decorarHub(level, planet);
        LoreWorldData.get(level.getServer()).markSite(planet.planet + ":hub2");
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
        decorarCitadela(level);
        LoreWorldData.get(level.getServer()).markSite("oa:hub2");
    }

    // ===================== Arena =====================

    /**
     * Arena do desafio (0.47.0): piso desenhado, 8 colunas com capitel ligadas por arcos, estandartes da Tropa,
     * arquibancada com 4 portões, braseiros e 4 obeliscos em volta do altar.
     */
    private static void buildArena(ServerLevel level, PlanetLore planet) {
        BlockPos c = PlanetLore.arena(planet);
        int y = surface(level, c, 14);
        BlockState stone = planet.stoneBlock().defaultBlockState();
        Arquitetura.Estilo e = Arquitetura.estilo(planet.corps);
        platform(level, c, y, 26, stone, accent(planet), stone);
        Arquitetura.piso(level, c, y, 19, e);
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
        } else {
            // estrado do altar e 4 obeliscos
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 2) set(level, new BlockPos(c.getX() + dx, y, c.getZ() + dz), Arquitetura.laje(e, false));
            for (int[] d : new int[][]{{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) Arquitetura.obelisco(level, c.offset(d[0], 0, d[1]).atY(y), 4, e);
            for (int[] d : new int[][]{{9, 0}, {-9, 0}, {0, 9}, {0, -9}}) Arquitetura.braseiro(level, c.offset(d[0], 0, d[1]).atY(y), e);
        }
        // 8 colunas (fora dos eixos, onde ficam os portões) ligadas por arcos
        BlockPos[] topos = new BlockPos[8];
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            int bx = c.getX() + (int) Math.round(Math.cos(a) * 17), bz = c.getZ() + (int) Math.round(Math.sin(a) * 17);
            int topo = Arquitetura.coluna(level, new BlockPos(bx, y, bz), 9, e);
            topos[i] = new BlockPos(bx, topo, bz);
            net.minecraft.core.Direction paraDentro = Arquitetura.rumo(c.getX() - bx, c.getZ() - bz);
            Arquitetura.estandarte(level, new BlockPos(bx, y + 6, bz).relative(paraDentro), paraDentro, e.cor());
        }
        for (int i = 0; i < 8; i++) Arquitetura.arco(level, topos[i], topos[(i + 1) % 8], 3, e);
        Arquitetura.arquibancada(level, c, y, 21, 3, e);
        if (planet == PlanetLore.RYUT) {
            battery(level, new BlockPos(c.getX() + 12, y, c.getZ()), planet);
            battery(level, new BlockPos(c.getX() - 12, y, c.getZ()), planet);
        }
        set(level, altar, LoreBlocks.ALTAR_DESAFIO.get().defaultBlockState());
    }

    // ===================== Base (hub) com arquitetura =====================

    /** Colunas com capitel nos cantos, toldo de lajes com luzes penduradas, bancos e braseiros (sem tocar nos NPCs). */
    private static void decorarHub(ServerLevel level, PlanetLore planet) {
        BlockPos c = PlanetLore.HUB;
        int y = surface(level, c, 8);
        // o chão da base já existe: acha a altura certa pelo bloco da plataforma
        for (int k = -4; k <= 4; k++) if (level.getBlockState(new BlockPos(c.getX(), y + k - 1, c.getZ() + 9)).is(accent(planet).getBlock())
                || level.getBlockState(new BlockPos(c.getX() + 10, y + k - 1, c.getZ())).is(accent(planet).getBlock())) { y = y + k; break; }
        Arquitetura.Estilo e = Arquitetura.estilo(planet.corps);
        BlockPos[] cantos = {c.offset(-7, 0, -7).atY(y), c.offset(7, 0, -7).atY(y), c.offset(7, 0, 7).atY(y), c.offset(-7, 0, 7).atY(y)};
        BlockPos[] topos = new BlockPos[4];
        for (int i = 0; i < 4; i++) {
            BlockPos b = cantos[i];
            for (int k = 0; k < 8; k++) if (!level.getBlockState(b.above(k)).isAir() && k > 0) set(level, b.above(k), Blocks.AIR.defaultBlockState());
            int topo = Arquitetura.coluna(level, b, 6, e);
            topos[i] = new BlockPos(b.getX(), topo, b.getZ());
        }
        // toldo: borda de lajes ligando as colunas, com estandartes
        for (int i = 0; i < 4; i++) {
            BlockPos a = topos[i], b = topos[(i + 1) % 4];
            int dx = Integer.signum(b.getX() - a.getX()), dz = Integer.signum(b.getZ() - a.getZ());
            for (BlockPos p = a.offset(dx, 0, dz); !p.equals(b); p = p.offset(dx, 0, dz)) {
                set(level, p, e.s(e.tijolo()));
                set(level, p.above(), Arquitetura.laje(e, false));
                if ((p.getX() - c.getX() + p.getZ() - c.getZ()) % 4 == 0) {
                    set(level, p.below(), Blocks.CHAIN.defaultBlockState());
                    set(level, p.below(2), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            net.minecraft.core.Direction d = net.minecraft.core.Direction.from2DDataValue(i);
            Arquitetura.estandarte(level, c.relative(d, 8).atY(topos[0].getY()), d, e.cor());
            // bancos virados para dentro, dos dois lados de cada lado
            for (int s = -5; s <= 5; s += 10) {
                BlockPos banco = c.relative(d, 9).relative(d.getClockWise(), s / 2).atY(y);
                if (level.getBlockState(banco).isAir()) set(level, banco, Arquitetura.escada(e, d, false));
            }
            Arquitetura.braseiro(level, c.relative(d, 9).relative(d.getClockWise(), 4).atY(y), e);
            Arquitetura.braseiro(level, c.relative(d, 9).relative(d.getCounterClockWise(), 4).atY(y), e);
        }
    }

    /** A citadela de Oa ganha colunas e arcos em volta da praça (sem mexer na torre e nos Guardiões). */
    private static void decorarCitadela(ServerLevel level) {
        BlockPos c = PlanetLore.HUB;
        int y = surface(level, c, 10);
        for (int k = -6; k <= 6; k++) if (level.getBlockState(new BlockPos(c.getX() + 15, y + k - 1, c.getZ())).is(Blocks.EMERALD_BLOCK)) { y = y + k; break; }
        Arquitetura.Estilo e = Arquitetura.estilo(com.lanterna.corps.BuiltinCorps.GREEN);
        BlockPos[] topos = new BlockPos[12];
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6 + Math.PI / 12;
            int bx = c.getX() + (int) Math.round(Math.cos(a) * 18), bz = c.getZ() + (int) Math.round(Math.sin(a) * 18);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(level, new BlockPos(bx + dx, y - 1, bz + dz), e.s(e.polido()));
            int topo = Arquitetura.coluna(level, new BlockPos(bx, y, bz), 10, e);
            topos[i] = new BlockPos(bx, topo, bz);
            net.minecraft.core.Direction paraDentro = Arquitetura.rumo(c.getX() - bx, c.getZ() - bz);
            Arquitetura.estandarte(level, new BlockPos(bx, y + 7, bz).relative(paraDentro), paraDentro, e.cor());
        }
        for (int i = 0; i < 12; i++) Arquitetura.arco(level, topos[i], topos[(i + 1) % 12], 2, e);
    }

    /** O Altar da Vida ganha 7 colunas (uma por Tropa da fusão) e arcos de quartzo. */
    private static void decorarGenese(ServerLevel level, BlockPos c) {
        int y = surface(level, c, 8);
        for (int k = -5; k <= 5; k++) if (level.getBlockState(new BlockPos(c.getX() + 10, y + k - 1, c.getZ())).is(Blocks.QUARTZ_PILLAR)) { y = y + k; break; }
        BlockPos[] topos = new BlockPos[7];
        for (int i = 0; i < 7; i++) {
            double a = i * Math.PI * 2 / 7;
            int bx = c.getX() + (int) Math.round(Math.cos(a) * 12), bz = c.getZ() + (int) Math.round(Math.sin(a) * 12);
            PlanetLore p = PlanetLore.values()[i];
            Arquitetura.Estilo base = Arquitetura.estilo(com.lanterna.corps.BuiltinCorps.WHITE);
            Arquitetura.Estilo e = new Arquitetura.Estilo(base.cheio(), base.tijolo(), base.polido(), base.escada(), base.laje(), base.muro(),
                    accent(p).getBlock(), base.luz(), base.vidro(), Arquitetura.estilo(p.corps).cor());
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) set(level, new BlockPos(bx + dx, y - 1, bz + dz), Blocks.SMOOTH_QUARTZ.defaultBlockState());
            int topo = Arquitetura.coluna(level, new BlockPos(bx, y, bz), 7, e);
            topos[i] = new BlockPos(bx, topo, bz);
            net.minecraft.core.Direction paraDentro = Arquitetura.rumo(c.getX() - bx, c.getZ() - bz);
            Arquitetura.estandarte(level, new BlockPos(bx, y + 5, bz).relative(paraDentro), paraDentro, e.cor());
        }
        Arquitetura.Estilo branco = Arquitetura.estilo(com.lanterna.corps.BuiltinCorps.WHITE);
        for (int i = 0; i < 7; i++) Arquitetura.arco(level, topos[i], topos[(i + 1) % 7], 3, branco);
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
        decorarGenese(level, c);
        LoreWorldData.get(level.getServer()).markSite("genese:hub2");
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
        // ruína em volta da rachadura: colunas partidas (alturas diferentes), pedaços caídos e rachaduras no chão
        java.util.Random r = new java.util.Random(c.asLong());
        Arquitetura.Estilo e = new Arquitetura.Estilo(Blocks.POLISHED_DEEPSLATE, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES,
                Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICK_SLAB, Blocks.DEEPSLATE_BRICK_WALL, Blocks.CRYING_OBSIDIAN,
                Blocks.SOUL_LANTERN, Blocks.TINTED_GLASS, net.minecraft.world.item.DyeColor.GRAY);
        for (int i = 0; i < 9; i++) {
            double a = i * Math.PI * 2 / 9 + r.nextDouble() * 0.2;
            int bx = c.getX() + (int) Math.round(Math.cos(a) * 11), bz = c.getZ() + (int) Math.round(Math.sin(a) * 11);
            int h = 2 + r.nextInt(7);
            if (r.nextInt(3) == 0) Arquitetura.coluna(level, new BlockPos(bx, y, bz), h + 2, e);
            else for (int k = 0; k < h; k++) set(level, new BlockPos(bx, y + k, bz), k % 3 == 1 ? e.s(e.tijolo()) : e.s(e.cheio()));
            // pedaço caído ao lado
            for (int k = 0; k < 3; k++) {
                int fx = bx + (int) Math.round(Math.cos(a + 1.3) * (2 + k)), fz = bz + (int) Math.round(Math.sin(a + 1.3) * (2 + k));
                set(level, new BlockPos(fx, y, fz), r.nextBoolean() ? e.s(e.tijolo()) : Arquitetura.laje(e, false));
            }
        }
        for (int i = 0; i < 40; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 2 + r.nextDouble() * 11;
            set(level, new BlockPos(c.getX() + (int) (Math.cos(a) * d), y - 1, c.getZ() + (int) (Math.sin(a) * d)),
                    r.nextBoolean() ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState());
        }
    }

    private PlanetSites() {}
}
