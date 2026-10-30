package com.lanterna.tropa;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.lore.Factions;
import com.lanterna.lore.LoreEntities;
import com.lanterna.lore.LoreWorldData;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.LoreCapability;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.Mission;
import com.lanterna.mission.MissionManager;
import com.lanterna.mission.Objective;
import com.lanterna.mission.Objective.Type;
import com.lanterna.naves.NaveEntity;
import com.lanterna.naves.NaveTipo;
import com.lanterna.network.ModNetwork;
import com.lanterna.space.SpaceBodies;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Contratos: missões secundárias do quadro de cada base. São 4 por planeta por dia (iguais para todos),
 * e cada Lanterna pode ter até 3 ao mesmo tempo. O id guarda tudo: "ct_<planeta>_<dia>_<vaga>"; o resto
 * (tipo, quantidade, lugar, recompensa) sai sempre igual de uma semente feita desse id.
 *
 *  piratas   acampamento pirata: derrote N piratas
 *  capitao   esconderijo do capitão (com uma nave pirata parada que dá para roubar)
 *  ecos      ninho de Ecos Cinzentos
 *  invasores Lanternas de uma Tropa inimiga invadiram o planeta
 *  resgate   um Lanterna ferido pediu socorro (fale com ele)
 *  entrega   leve minério deste planeta para o líder de outro planeta
 *  nave      derrube uma nave pirata no espaço (aparece logo que você voar pelo espaço)
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Contratos {
    public static final int MAX_ATIVOS = 3;
    public static final int POR_DIA = 4;
    private static final String[] TIPOS = {"piratas", "ecos", "resgate", "entrega", "invasores", "capitao", "nave"};
    private static final Map<String, Mission> CACHE = new HashMap<>();

    public static long dia(ServerPlayer p) {
        return p.server.overworld().getDayTime() / 24000L;
    }

    public static List<String> ofertas(String planet, long day) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < POR_DIA; i++) l.add("ct_" + planet + "_" + day + "_" + i);
        return l;
    }

    private record Dados(String tipo, String planet, int count, BlockPos pos, String dest, int xp, int tier) {}

    @Nullable
    private static Dados dados(String id) {
        String[] p = id.split("_");
        if (p.length != 4) return null;
        String planet = p[1];
        PlanetLore pl = PlanetLore.byPlanet(planet);
        if (pl == null || pl == PlanetLore.GENESE) return null;
        long day;
        int slot;
        try {
            day = Long.parseLong(p[2]);
            slot = Integer.parseInt(p[3]);
        } catch (NumberFormatException e) {
            return null;
        }
        Random r = new Random(planet.hashCode() * 31L + day * 1_000_003L + slot * 7919L);
        String tipo = switch (slot) {
            case 0 -> r.nextBoolean() ? "piratas" : "ecos";
            case 1 -> r.nextBoolean() ? "resgate" : "entrega";
            case 2 -> r.nextBoolean() ? "invasores" : "piratas";
            default -> r.nextInt(3) == 0 ? "nave" : "capitao";
        };
        double a = r.nextDouble() * Math.PI * 2;
        double dist = 160 + r.nextInt(240);
        BlockPos pos = new BlockPos((int) (Math.cos(a) * dist), 0, (int) (Math.sin(a) * dist));
        String dest = "";
        if (tipo.equals("entrega")) {
            PlanetLore[] others = PlanetLore.values();
            PlanetLore d;
            do d = others[r.nextInt(others.length)]; while (d == pl || d == PlanetLore.GENESE);
            dest = d.planet;
        }
        int count = switch (tipo) {
            case "piratas" -> 4 + r.nextInt(4) + slot;
            case "ecos" -> 5 + r.nextInt(5);
            case "invasores" -> 3;
            case "entrega" -> 4 + r.nextInt(5);
            default -> 1;
        };
        int xp = switch (tipo) {
            case "piratas" -> 140 + 30 * count;
            case "ecos" -> 120 + 15 * count;
            case "resgate" -> 220;
            case "entrega" -> 260;
            case "invasores" -> 420;
            case "capitao" -> 700;
            case "nave" -> 480;
            default -> 150;
        };
        return new Dados(tipo, planet, count, pos, dest, xp, slot);
    }

    /** A missão do contrato (montada do id). */
    @Nullable
    public static Mission mission(String id) {
        Mission cached = CACHE.get(id);
        if (cached != null) return cached;
        Dados d = dados(id);
        if (d == null) return null;
        PlanetLore pl = PlanetLore.byPlanet(d.planet);
        String p = d.planet;
        List<Objective> obj = switch (d.tipo) {
            case "piratas" -> List.of(Objective.of(Type.KILL, "papel:pirata", d.count, p, d.pos));
            case "ecos" -> List.of(Objective.of(Type.KILL, "lanterna:eco_cinzento", d.count, p, d.pos));
            case "resgate" -> List.of(Objective.of(Type.TALK, "resgate:" + p, 1, p, d.pos));
            case "entrega" -> List.of(Objective.of(Type.COLLECT, "lanterna:" + pl.ore, d.count, p, null),
                    Objective.of(Type.TALK, "lider:" + d.dest, 1, d.dest, PlanetLore.HUB));
            case "invasores" -> List.of(Objective.of(Type.KILL, "papel:inimigo", d.count, p, d.pos));
            case "capitao" -> List.of(Objective.of(Type.KILL, "papel:capitao", 1, p, d.pos),
                    Objective.of(Type.KILL, "papel:pirata", 3, p, d.pos));
            case "nave" -> List.of(Objective.of(Type.KILL, "papel:nave_pirata", 1, null, null));
            default -> List.of();
        };
        int tier = d.tier;
        Mission m = new Mission(id, "contrato", pl.color(), obj, () -> {
            List<ItemStack> r = new ArrayList<>();
            r.add(new ItemStack(com.lanterna.lore.LoreItems.MERITO.get(), 1 + tier));
            r.add(new ItemStack(Items.EMERALD, 4 + 3 * tier));
            if (tier >= 2) r.add(new ItemStack(TropaItems.FRAGMENTO_CASCO.get(), 2));
            if (tier >= 3) r.add(new ItemStack(TropaItems.NUCLEO_PROPULSOR.get()));
            return r;
        }, d.xp, null, null);
        CACHE.put(id, m);
        return m;
    }

    public static Component titulo(String id) {
        Dados d = dados(id);
        if (d == null) return Component.literal(id);
        return Component.translatable("contrato.lanterna." + d.tipo + ".title",
                Component.translatable("planet.lanterna." + (d.tipo.equals("entrega") ? d.dest : d.planet)));
    }

    public static Component descricao(String id) {
        Dados d = dados(id);
        if (d == null) return Component.empty();
        return Component.translatable("contrato.lanterna." + d.tipo + ".desc", Component.translatable("planet.lanterna." + d.planet),
                d.count, Component.translatable("planet.lanterna." + (d.dest.isEmpty() ? d.planet : d.dest)), d.xp);
    }

    /** Nível recomendado (só aviso). */
    public static int recomendado(String id) {
        Dados d = dados(id);
        return d == null ? 1 : switch (d.tipo) {
            case "capitao" -> 20;
            case "invasores", "nave" -> 12;
            case "piratas" -> 4 + 3 * d.tier;
            default -> 1;
        };
    }

    // ===================== Quadro =====================

    public static void abrirQuadro(ServerPlayer p) {
        abrirQuadro(p, false);
    }

    /**
     * Abre os contratos da Tropa do jogador. Só funciona no planeta da própria Tropa ou no painel (console)
     * de uma nave sua. {@code livre} = sem essa checagem (comando de teste e atualização depois de aceitar).
     */
    public static void abrirQuadro(ServerPlayer p, boolean livre) {
        PlanetLore pl = livre ? casa(p) : origem(p);
        if (pl == null) {
            if (livre) p.displayClientMessage(Component.translatable("contrato.lanterna.sem_anel"), true);
            return;
        }
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null) return;
        List<String> ids = ofertas(pl.planet, dia(p));
        List<Integer> estado = new ArrayList<>();
        for (String id : ids) estado.add(d.isDone(id) ? 2 : d.isActive(id) ? 1 : 0);
        int ativos = (int) d.active().keySet().stream().filter(k -> k.startsWith("ct_")).count();
        ModNetwork.sendTo(p, new QuadroPacket(pl.planet, ids, estado, ativos));
    }

    /** Planeta da Tropa do jogador (o Branco usa os contratos de Oa, dos Guardiões). */
    @Nullable
    public static PlanetLore casa(ServerPlayer p) {
        BuiltinCorps c = Factions.playerCorps(p);
        if (c == null) return null;
        if (c == BuiltinCorps.WHITE) return PlanetLore.OA;
        for (PlanetLore x : PlanetLore.values()) if (x.corps == c && x != PlanetLore.GENESE) return x;
        return null;
    }

    /** De onde o jogador está abrindo o quadro; null (com aviso) se não pode. */
    @Nullable
    private static PlanetLore origem(ServerPlayer p) {
        PlanetLore casa = casa(p);
        if (casa == null) {
            p.displayClientMessage(Component.translatable("contrato.lanterna.sem_anel"), true);
            return null;
        }
        if (p.level().dimension().equals(com.lanterna.naves.NaveInterior.DIM)) {
            if (com.lanterna.naves.NaveInterior.salaPropria(p)) return casa;
            p.displayClientMessage(Component.translatable("contrato.lanterna.nave_alheia"), true);
            return null;
        }
        SpaceBodies.Body b = SpaceBodies.bodyAt(p.level().dimension(), p.getX(), p.getZ());
        PlanetLore aqui = b != null ? PlanetLore.byPlanet(b.id()) : null;
        if (aqui == casa) return casa;
        SpaceBodies.Body home = SpaceBodies.byId(casa.planet);
        p.displayClientMessage(Component.translatable("contrato.lanterna.so_tropa",
                home != null ? home.displayName() : Component.literal(casa.planet)), true);
        return null;
    }

    public static void aceitar(ServerPlayer p, String id) {
        LoreData d = LoreCapability.get(p).orElse(null);
        Dados dd = dados(id);
        if (d == null || dd == null) return;
        if (!ofertas(dd.planet, dia(p)).contains(id)) {
            p.displayClientMessage(Component.translatable("contrato.lanterna.expirou"), true);
            return;
        }
        long ativos = d.active().keySet().stream().filter(k -> k.startsWith("ct_")).count();
        if (ativos >= MAX_ATIVOS) {
            p.displayClientMessage(Component.translatable("contrato.lanterna.limite", MAX_ATIVOS), true);
            return;
        }
        if (d.isDone(id) || d.isActive(id)) return;
        MissionManager.start(p, id);
        abrirQuadro(p, true);
    }

    public static void desistir(ServerPlayer p, String id) {
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null || !id.startsWith("ct_") || !d.isActive(id)) return;
        d.abandon(id);
        MissionManager.sync(p, d);
        abrirQuadro(p, true);
    }

    // ===================== Os alvos aparecem quando o Lanterna chega perto =====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 40 != 17) return;
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null) return;
        SpaceBodies.Body here = SpaceBodies.bodyAt(p.level().dimension(), p.getX(), p.getZ());
        for (String id : new ArrayList<>(d.active().keySet())) {
            if (!id.startsWith("ct_")) continue;
            Dados dd = dados(id);
            if (dd == null) continue;
            if (dd.tipo.equals("nave")) {
                if (p.level().dimension().equals(SpaceBodies.SPACE)) Encontros.forcarPirata(p, id);
                continue;
            }
            if (here == null || !here.id().equals(dd.planet)) continue;
            double dx = p.getX() - dd.pos.getX(), dz = p.getZ() - dd.pos.getZ();
            if (dx * dx + dz * dz > 110 * 110) continue;
            LoreWorldData wd = LoreWorldData.get(p.server);
            String site = "ct:" + id + ":" + p.getStringUUID();
            if (wd.siteBuilt(site)) continue;
            wd.markSite(site);
            spawnAlvo(p.serverLevel(), dd, PlanetLore.byPlanet(dd.planet));
        }
    }

    private static void spawnAlvo(ServerLevel level, Dados d, PlanetLore pl) {
        BlockPos c = surface(level, d.pos);
        switch (d.tipo) {
            case "piratas" -> {
                acampamento(level, c);
                for (int i = 0; i < d.count; i++) add(level, LanternaEntity.pirata(level, false), c, 6);
            }
            case "capitao" -> {
                acampamento(level, c);
                add(level, LanternaEntity.pirata(level, true), c, 3);
                for (int i = 0; i < 4; i++) add(level, LanternaEntity.pirata(level, false), c, 7);
                // A nave do capitão fica parada: quem vencer pode levá-la
                NaveEntity ship = NaveEntity.create(level, NaveTipo.NAVE_PIRATA, String.valueOf(1 + level.random.nextInt(2)));
                BlockPos s = surface(level, c.offset(14, 0, 0));
                ship.moveTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, level.random.nextFloat() * 360, 0);
                level.addFreshEntity(ship);
            }
            case "ecos" -> {
                for (int i = 0; i < d.count; i++) {
                    var e = LoreEntities.ECO_CINZENTO.get().create(level);
                    if (e == null) continue;
                    BlockPos q = surface(level, c.offset(level.random.nextInt(13) - 6, 0, level.random.nextInt(13) - 6));
                    e.moveTo(q.getX() + 0.5, q.getY(), q.getZ() + 0.5, level.random.nextFloat() * 360, 0);
                    e.setPersistenceRequired();
                    level.addFreshEntity(e);
                }
            }
            case "invasores" -> {
                BuiltinCorps home = pl.corps;
                List<BuiltinCorps> enemies = new ArrayList<>();
                for (BuiltinCorps x : BuiltinCorps.values()) if (x != BuiltinCorps.WHITE && Factions.enemies(home, x)) enemies.add(x);
                BuiltinCorps foe = enemies.isEmpty() ? BuiltinCorps.BLACK : enemies.get(level.random.nextInt(enemies.size()));
                for (int i = 0; i < d.count; i++) add(level, LanternaEntity.lanterna(level, foe, i == 0), c, 5);
            }
            case "resgate" -> {
                LanternaEntity w = LanternaEntity.lanterna(level, pl.corps == BuiltinCorps.WHITE ? BuiltinCorps.GREEN : pl.corps, false);
                w.getPersistentData().putBoolean("lanterna_resgate", true);
                add(level, w, c, 0);
                w.setHealth(w.getMaxHealth() * 0.3f);
                for (int i = 0; i < 3; i++) {
                    var e = LoreEntities.ECO_CINZENTO.get().create(level);
                    if (e == null) continue;
                    BlockPos q = surface(level, c.offset(level.random.nextInt(11) - 5, 0, level.random.nextInt(11) - 5));
                    e.moveTo(q.getX() + 0.5, q.getY(), q.getZ() + 0.5, 0, 0);
                    e.setPersistenceRequired();
                    level.addFreshEntity(e);
                }
            }
            default -> {}
        }
    }

    private static void add(ServerLevel level, LanternaEntity e, BlockPos c, int spread) {
        BlockPos q = spread > 0 ? surface(level, c.offset(level.random.nextInt(spread * 2 + 1) - spread, 0,
                level.random.nextInt(spread * 2 + 1) - spread)) : c;
        e.moveTo(q.getX() + 0.5, q.getY(), q.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        e.restrictTo(q, 16);
        level.addFreshEntity(e);
    }

    private static void acampamento(ServerLevel level, BlockPos c) {
        level.setBlock(c, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, false), 3);
        NaveTipo d = NaveTipo.values()[NaveTipo.DESTROCOS_1.ordinal() + level.random.nextInt(3)];
        NaveEntity w = NaveEntity.create(level, d, "");
        BlockPos s = surface(level, c.offset(-9, 0, 4));
        w.moveTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        level.addFreshEntity(w);
        for (int i = 0; i < 3; i++) {
            BlockPos b = surface(level, c.offset(level.random.nextInt(9) - 4, 0, level.random.nextInt(9) - 4));
            level.setBlock(b, level.random.nextBoolean() ? Blocks.BARREL.defaultBlockState() : Blocks.CHEST.defaultBlockState(), 3);
            if (level.getBlockEntity(b) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity be) {
                be.setLootTable(LanternaMod.id("chests/acampamento_pirata"), level.random.nextLong());
            }
        }
    }

    private static BlockPos surface(ServerLevel level, BlockPos p) {
        level.getChunk(p.getX() >> 4, p.getZ() >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
        return new BlockPos(p.getX(), y, p.getZ());
    }

    private Contratos() {}
}
