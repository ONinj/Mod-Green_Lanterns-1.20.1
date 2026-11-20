package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.lore.LoreWorldData;
import com.lanterna.lore.PlanetLore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.55.1: uma Tropa cai. A Bateria Central fica meio destruída (o vidro quebrado, entulho de pedra negra em volta), mas
 * o poder não se perde: a forja do anel continua funcionando. A região em volta vira terra morta (chão seco, cinza,
 * mato morto), com névoa e servos do Necron rondando. Fica assim até a próxima saga.
 *
 * As quedas valem para o mundo todo (as Tropas do Amor, do Índigo e da Esperança, menos a de quem fez a saga).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Queda {
    private static final String CAIDA = "saga:caida:", PRONTA = "saga:caida_pronta:";
    private static final int RAIO = 64, MAX_SERVOS = 8;
    /** Planetas sendo contaminados agora → pedaços que faltam. */
    private static final java.util.Map<PlanetLore, List<long[]>> FILA = new java.util.EnumMap<>(PlanetLore.class);

    public static boolean caida(MinecraftServer s, PlanetLore p) {
        return LoreWorldData.get(s).siteBuilt(CAIDA + p.planet);
    }

    /** As Tropas caídas (para o cliente). */
    public static List<String> caidas(MinecraftServer s) {
        List<String> l = new ArrayList<>();
        for (PlanetLore p : PlanetLore.values()) if (caida(s, p)) l.add(p.planet);
        return l;
    }

    /** A Tropa cai: marca, fecha a fenda (não há mais o que defender) e começa a contaminar. */
    public static void cair(MinecraftServer s, PlanetLore p) {
        if (caida(s, p)) return;
        LoreWorldData w = LoreWorldData.get(s);
        w.markSite(CAIDA + p.planet);
        w.resetSite("saga:fenda:" + p.planet);
        LanternaMod.LOGGER.info("Lanterna: a Tropa de {} caiu diante do Necron", p.planet);
        for (ServerPlayer q : s.getPlayerList().getPlayers()) SagaNecron.mandarMundo(q);
    }

    @Nullable
    private static ServerLevel nivel(MinecraftServer s, PlanetLore p) {
        var corpo = com.lanterna.space.SpaceBodies.byId(p.planet);
        return corpo == null || corpo.dimension() == null ? null : s.getLevel(corpo.dimension());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
        int tc = s.getTickCount();
        for (PlanetLore p : PlanetLore.values()) {
            if (!caida(s, p)) continue;
            ServerLevel l = nivel(s, p);
            if (l == null) continue;
            if (!LoreWorldData.get(s).siteBuilt(PRONTA + p.planet)) {
                if (tc % 2 == 0) contaminarPasso(s, l, p);
                continue;
            }
            if (tc % 60 == 29) servos(l);
        }
    }

    // ===================== a contaminação =====================

    private static void contaminarPasso(MinecraftServer s, ServerLevel l, PlanetLore p) {
        BlockPos h = PlanetLore.HUB;
        List<long[]> fila = FILA.computeIfAbsent(p, k -> {
            List<long[]> f = new ArrayList<>();
            int r = (RAIO >> 4) + 1;
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) f.add(new long[]{(h.getX() >> 4) + dx, (h.getZ() >> 4) + dz});
            return f;
        });
        if (fila.isEmpty()) {
            quebrarBateria(l, h);
            LoreWorldData.get(s).markSite(PRONTA + p.planet);
            FILA.remove(p);
            return;
        }
        long[] c = fila.remove(0);
        l.getChunk((int) c[0], (int) c[1]);
        contaminarChunk(l, h, (int) c[0], (int) c[1]);
    }

    private static double ruido(int x, int z, long sal) {
        long k = x * 3129871L ^ z * 116129781L ^ sal;
        k = k * k * 42317861L + k * 11L;
        return ((k >> 16) & 0xFFFF) / 65535.0;
    }

    private static void por(ServerLevel l, BlockPos p, BlockState s) { l.setBlock(p, s, 2 | 16); }

    /** O chão natural vira terra morta; plantas e folhas secam. As construções da cidade ficam. */
    private static void contaminarChunk(ServerLevel l, BlockPos h, int cx, int cz) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int ix = 0; ix < 16; ix++)
            for (int iz = 0; iz < 16; iz++) {
                int x = (cx << 4) + ix, z = (cz << 4) + iz;
                double dx = x - h.getX(), dz = z - h.getZ(), d = Math.sqrt(dx * dx + dz * dz);
                double borda = RAIO - 12 + 16 * ruido(x >> 3, z >> 3, 5);
                if (d > borda) continue;
                int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                BlockState topo = l.getBlockState(m.set(x, y, z));
                double r = ruido(x, z, 13);
                if (topo.is(BlockTags.LEAVES)) { por(l, m, r < 0.5 ? Blocks.AIR.defaultBlockState() : Blocks.COBWEB.defaultBlockState()); continue; }
                BlockState acima = l.getBlockState(m.set(x, y + 1, z));
                if (acima.is(BlockTags.FLOWERS) || acima.is(Blocks.GRASS) || acima.is(Blocks.TALL_GRASS) || acima.is(Blocks.FERN))
                    por(l, m, Blocks.DEAD_BUSH.defaultBlockState());
                m.set(x, y, z);
                if (topo.is(Blocks.GRASS_BLOCK) || topo.is(BlockTags.DIRT) || topo.is(BlockTags.SAND) || topo.is(Blocks.MOSS_BLOCK)
                        || topo.is(Blocks.SNOW_BLOCK) || topo.is(Blocks.CLAY) || topo.is(Blocks.GRAVEL)) {
                    BlockState novo = r < 0.35 ? Blocks.SOUL_SOIL.defaultBlockState() : r < 0.62 ? Blocks.COARSE_DIRT.defaultBlockState()
                            : r < 0.75 ? Blocks.SOUL_SAND.defaultBlockState() : r < 0.88 ? Blocks.PODZOL.defaultBlockState() : Blocks.TUFF.defaultBlockState();
                    por(l, m, novo);
                    BlockPos cima = m.above();
                    if (l.getBlockState(cima).isAir()) {
                        double q = ruido(x, z, 17);
                        if (q > 0.96) por(l, cima, Blocks.DEAD_BUSH.defaultBlockState());
                        else if (q > 0.955 && novo.is(Blocks.SOUL_SOIL)) por(l, cima, Blocks.SOUL_FIRE.defaultBlockState());
                        else if (q > 0.95) por(l, cima, Blocks.COBWEB.defaultBlockState());
                    }
                }
            }
    }

    /** A Bateria Central meio destruída: o vidro quebra (buracos e vidro preto) e cai entulho em volta. */
    private static void quebrarBateria(ServerLevel l, BlockPos h) {
        int y0 = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, h.getX() + 12, h.getZ() + 12) - 2;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -10; dx <= 10; dx++)
            for (int dz = -10; dz <= 10; dz++)
                for (int y = y0; y < y0 + 60; y++) {
                    BlockState s = l.getBlockState(m.set(h.getX() + dx, y, h.getZ() + dz));
                    boolean vidro = s.getBlock() instanceof AbstractGlassBlock || s.getBlock() instanceof StainedGlassPaneBlock || s.is(Blocks.GLASS_PANE);
                    if (!vidro) continue;
                    double r = ruido(h.getX() + dx + y * 7, h.getZ() + dz, 23);
                    if (r < 0.4) por(l, m, Blocks.AIR.defaultBlockState());
                    else if (r < 0.65) por(l, m, Blocks.BLACK_STAINED_GLASS.defaultBlockState());
                }
        // entulho de pedra negra e obsidiana chorando no chão em volta
        for (int i = 0; i < 26; i++) {
            double a = i * 2.39996, d = 7 + (i % 7) * 1.4;
            int x = h.getX() + (int) Math.round(Math.cos(a) * d), z = h.getZ() + (int) Math.round(Math.sin(a) * d);
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            por(l, new BlockPos(x, y, z), i % 5 == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : i % 2 == 0 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.BLACKSTONE_SLAB.defaultBlockState());
            if (i % 6 == 1) por(l, new BlockPos(x, y + 1, z), Blocks.SOUL_FIRE.defaultBlockState());
        }
    }

    // ===================== servos rondando =====================

    private static void servos(ServerLevel l) {
        BlockPos h = PlanetLore.HUB;
        List<ServerPlayer> perto = new ArrayList<>();
        for (ServerPlayer p : l.players()) if (!p.isSpectator() && p.blockPosition().distSqr(h) < 100 * 100) perto.add(p);
        if (perto.isEmpty() || l.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return;
        AABB area = new AABB(h).inflate(110, 80, 110);
        if (l.getEntitiesOfClass(Mob.class, area, Cratera::servo).size() >= MAX_SERVOS) return;
        ServerPlayer alvo = perto.get(l.random.nextInt(perto.size()));
        double a = l.random.nextDouble() * Math.PI * 2, r = 16 + l.random.nextDouble() * 16;
        int x = (int) (alvo.getX() + Math.cos(a) * r), z = (int) (alvo.getZ() + Math.sin(a) * r);
        if (!l.isLoaded(new BlockPos(x, 64, z))) return;
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        Mob m = Cratera.novoServo(l);
        if (m == null) return;
        m.moveTo(x + 0.5, y, z + 0.5, l.random.nextFloat() * 360, 0);
        m.addTag(Cratera.SERVO);
        m.setPersistenceRequired();
        l.addFreshEntity(m);
        l.sendParticles(ParticleTypes.SOUL, m.getX(), m.getY() + 0.5, m.getZ(), 20, 0.4, 0.8, 0.4, 0.03);
    }

    /** Teste: a Tropa volta (só a marca; o chão não volta). */
    public static void zerar(MinecraftServer s, PlanetLore p) {
        LoreWorldData.get(s).resetSite(CAIDA + p.planet);
        LoreWorldData.get(s).resetSite(PRONTA + p.planet);
        FILA.remove(p);
    }

    private Queda() {}
}
