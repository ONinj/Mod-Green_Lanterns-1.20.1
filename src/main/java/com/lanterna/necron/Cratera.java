package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.item.PowerRingItem;
import com.lanterna.lore.LoreWorldData;
import com.lanterna.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.54.2: onde o Necron caiu na Terra. Uma região grande de terra morta (cemitério, árvores secas, cinza), a cratera
 * do impacto e, no fundo dela, a Bateria Central gigante da Tropa da Morte. O Necron fica na frente da bateria;
 * em volta, servos: Lanternas Negros corrompidos e mortos-vivos que carregam anéis da Morte.
 *
 * Com um anel só ninguém fere os servos do Necron (nem ele): a luz de uma emoção é absorvida. Só quem usa dois
 * anéis ao mesmo tempo (Dois Anéis, com o segundo anel no punho) consegue machucar os servos.
 *
 * O lugar é escolhido quando Ryut se parte (uma vez por mundo) e o terreno é gerado aos poucos (um pedaço de
 * mundo por vez), para não travar o servidor. Cada pedaço pronto fica marcado no mundo.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Cratera {
    /** Raio da terra morta, raio da cratera e da borda. */
    public static final int RAIO = 96, CRATERA = 30, BORDA = 38;
    private static final String SITE = "saga:impacto:", PRONTO = "saga:cratera_pronta", CHUNK = "saga:cc:";
    public static final String SERVO = "lanterna_servo_necron";
    private static final int MAX_SERVOS = 14;

    @Nullable private static BlockPos cache;
    private static final List<long[]> FILA = new ArrayList<>();
    private static boolean filaMontada;

    // ===================== onde =====================

    /** O centro do impacto (x, y do chão da cratera, z), ou null se o Necron ainda não caiu. */
    @Nullable
    public static BlockPos centro(MinecraftServer s) {
        if (cache != null) return cache;
        for (String id : LoreWorldData.get(s).sites()) {
            if (!id.startsWith(SITE)) continue;
            String[] p = id.substring(SITE.length()).split(":");
            cache = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            MissoesNecron.centro = cache;
            return cache;
        }
        return null;
    }

    /** O Necron caiu: escolhe o lugar (longe do spawn, em terra firme) e começa a gerar. */
    public static void escolher(MinecraftServer s) {
        if (centro(s) != null) return;
        ServerLevel terra = s.overworld();
        BlockPos spawn = terra.getSharedSpawnPos();
        RandomSource r = RandomSource.create(terra.getSeed() ^ 0x6E6563726FL);
        BlockPos escolhido = null;
        for (int i = 0; i < 24 && escolhido == null; i++) {
            double ang = r.nextDouble() * Math.PI * 2, dist = 900 + r.nextInt(600);
            int x = spawn.getX() + (int) (Math.cos(ang) * dist), z = spawn.getZ() + (int) (Math.sin(ang) * dist);
            var bioma = terra.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(x >> 2, 16, z >> 2, terra.getChunkSource().randomState().sampler());
            if (bioma.is(BiomeTags.IS_OCEAN) || bioma.is(BiomeTags.IS_DEEP_OCEAN) || bioma.is(BiomeTags.IS_RIVER) || bioma.is(BiomeTags.IS_BEACH)) continue;
            escolhido = new BlockPos(x, 0, z);
        }
        if (escolhido == null) escolhido = spawn.offset(1100, 0, 0);
        // a altura do fundo da cratera é decidida quando o pedaço do centro for gerado
        terra.getChunk(escolhido.getX() >> 4, escolhido.getZ() >> 4);
        int h = chao(terra, escolhido.getX(), escolhido.getZ());
        // o fundo nunca passa do fundo do mundo (mundos planos ou muito baixos: a cratera fica rasa)
        int fundo = Math.max(h - profundidade(0), terra.getMinBuildHeight() + 4);
        LoreWorldData.get(s).markSite(SITE + escolhido.getX() + ":" + fundo + ":" + escolhido.getZ());
        cache = null;
        filaMontada = false;
        LanternaMod.LOGGER.info("Lanterna: o Necron caiu na Terra em {} {} {}", escolhido.getX(), fundo, escolhido.getZ());
        for (ServerPlayer q : s.getPlayerList().getPlayers()) SagaNecron.mandarMundo(q);
    }

    /** Zera (testes): esquece o lugar e as marcas dos pedaços gerados. */
    public static void esquecer(MinecraftServer s) {
        LoreWorldData w = LoreWorldData.get(s);
        for (String id : w.sites()) if (id.startsWith(SITE) || id.startsWith(CHUNK) || id.equals(PRONTO)) w.resetSite(id);
        cache = null;
        filaMontada = false;
        FILA.clear();
    }

    // ===================== 0.56.0: a terra morta cresce (A Terra Escurece) =====================

    private static final String ESTAGIO = "saga:terra:";
    /** Quanto a terra morta cresce a cada etapa (blocos de raio). */
    public static final int CRESCE = 70;
    private static int estagioCache = -1;

    /** A etapa da Terra Escurece (0 = só a cratera; até 3). */
    public static int estagio(MinecraftServer s) {
        if (estagioCache >= 0) return estagioCache;
        int e = 0;
        for (String id : LoreWorldData.get(s).sites()) if (id.startsWith(ESTAGIO)) e = Math.max(e, Integer.parseInt(id.substring(ESTAGIO.length())));
        estagioCache = e;
        raioAtual = RAIO + CRESCE * e;
        return e;
    }

    /** O raio da terra morta agora (cresce com as etapas). */
    public static int raioAtual = RAIO;

    /** Avança uma etapa: a terra morta cresce (gerada aos poucos, como antes). */
    public static void crescer(MinecraftServer s, int novo) {
        LoreWorldData.get(s).markSite(ESTAGIO + novo);
        estagioCache = novo;
        raioAtual = RAIO + CRESCE * novo;
        filaMontada = false;
        FILA.clear();
    }

    public static void zerarEstagio(MinecraftServer s) {
        LoreWorldData w = LoreWorldData.get(s);
        for (String id : w.sites()) if (id.startsWith(ESTAGIO) || id.startsWith(PRONTO + ":") || id.startsWith(CHUNK + "e")) w.resetSite(id);
        estagioCache = 0;
        raioAtual = RAIO;
    }

    private static String prontoKey(MinecraftServer s) {
        int e = estagio(s);
        return e == 0 ? PRONTO : PRONTO + ":" + e;
    }

    public static boolean pronta(MinecraftServer s) {
        return LoreWorldData.get(s).siteBuilt(prontoKey(s));
    }

    /** Dentro da terra morta? */
    public static boolean dentro(Level l, BlockPos c, double x, double z, double margem) {
        if (c == null || l.dimension() != Level.OVERWORLD) return false;
        double dx = x - c.getX(), dz = z - c.getZ();
        return dx * dx + dz * dz < (raioAtual + margem) * (raioAtual + margem);
    }

    // ===================== geração aos poucos =====================

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
        BlockPos c = centro(s);
        if (c == null) return;
        if (!pronta(s)) {
            if (s.getTickCount() % 2 == 0) gerarPasso(s, c);
            return;
        }
        if (s.getTickCount() % 40 == 13) manter(s.overworld(), c);
    }

    private static void montarFila(MinecraftServer s, BlockPos c) {
        FILA.clear();
        LoreWorldData w = LoreWorldData.get(s);
        int e = estagio(s);
        int raio = RAIO + CRESCE * e, antes = e == 0 ? 0 : RAIO + CRESCE * (e - 1) - 24;
        int r = (raio + 8) >> 4;
        int cx0 = c.getX() >> 4, cz0 = c.getZ() >> 4;
        List<long[]> l = new ArrayList<>();
        for (int dx = -r - 1; dx <= r + 1; dx++)
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                int cx = cx0 + dx, cz = cz0 + dz;
                double mx = (cx << 4) + 8 - c.getX(), mz = (cz << 4) + 8 - c.getZ();
                double dc = Math.sqrt(mx * mx + mz * mz);
                if (dc > raio + 24 || dc < antes) continue;
                if (w.siteBuilt(chunkKey(e, cx, cz))) continue;
                l.add(new long[]{cx, cz, (long) (mx * mx + mz * mz)});
            }
        l.sort((a, b) -> Long.compare(a[2], b[2]));   // do centro para fora
        FILA.addAll(l);
        filaMontada = true;
    }

    private static void gerarPasso(MinecraftServer s, BlockPos c) {
        if (!filaMontada) montarFila(s, c);
        ServerLevel l = s.overworld();
        if (FILA.isEmpty()) {
            if (estagio(s) == 0) bateria(l, c);
            LoreWorldData.get(s).markSite(prontoKey(s));
            LanternaMod.LOGGER.info("Lanterna: a cratera do Necron está pronta");
            return;
        }
        long[] k = FILA.remove(0);
        int cx = (int) k[0], cz = (int) k[1];
        l.getChunk(cx, cz);   // carrega (ou gera) o pedaço
        gerarChunk(l, c, cx, cz);
        LoreWorldData.get(s).markSite(chunkKey(estagio(s), cx, cz));
    }

    private static String chunkKey(int e, int cx, int cz) {
        return e == 0 ? CHUNK + cx + ":" + cz : CHUNK + "e" + e + ":" + cx + ":" + cz;
    }

    /** A cratera: fundo em tigela; a borda levantada; o resto é terra morta. */
    private static int profundidade(double d) {
        if (d >= CRATERA) return 0;
        double k = d / CRATERA;
        return (int) Math.round(11 * (1 - k * k));
    }

    private static boolean arvoreOuAr(BlockState s) {
        return s.isAir() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.canBeReplaced() || s.is(Blocks.SNOW)
                || s.is(BlockTags.FLOWERS) || s.is(Blocks.BAMBOO) || s.is(Blocks.CACTUS) || s.is(Blocks.SUGAR_CANE)
                || s.is(Blocks.VINE) || s.is(Blocks.MUSHROOM_STEM) || s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.RED_MUSHROOM_BLOCK)
                || s.is(Blocks.BEE_NEST) || s.is(Blocks.COCOA) || s.is(Blocks.MANGROVE_ROOTS);
    }

    /** O chão de verdade (sem árvores nem plantas). */
    private static int chao(ServerLevel l, int x, int z) {
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, y, z);
        while (y > l.getMinBuildHeight() + 5 && arvoreOuAr(l.getBlockState(m.setY(y)))) y--;
        return y;
    }

    private static double ruido(int x, int z, long sal) {
        long h = x * 3129871L ^ z * 116129781L ^ sal;
        h = h * h * 42317861L + h * 11L;
        return ((h >> 16) & 0xFFFF) / 65535.0;
    }

    private static void por(ServerLevel l, BlockPos p, BlockState s) {
        l.setBlock(p, s, 2 | 16);
    }

    private static void gerarChunk(ServerLevel l, BlockPos c, int cx, int cz) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int ix = 0; ix < 16; ix++)
            for (int iz = 0; iz < 16; iz++) {
                int x = (cx << 4) + ix, z = (cz << 4) + iz;
                double dx = x - c.getX(), dz = z - c.getZ(), d = Math.sqrt(dx * dx + dz * dz);
                // borda irregular da terra morta
                double borda = raioAtual - 10 + 14 * ruido(x >> 3, z >> 3, 7);
                if (d > borda + 6) continue;
                boolean morta = d <= borda || ruido(x, z, 3) > (d - borda) / 6.0;
                if (!morta) continue;
                int topo = l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                int h = chao(l, x, z);
                // tira árvores e plantas
                for (int y = h + 1; y <= Math.max(topo, h + 1) + 2; y++) {
                    BlockState s = l.getBlockState(m.set(x, y, z));
                    if (!s.isAir() && arvoreOuAr(s)) por(l, m, Blocks.AIR.defaultBlockState());
                }
                BlockState topoBloco = l.getBlockState(m.set(x, h, z));
                if (topoBloco.getFluidState().isSource() && !topoBloco.isAir()) {
                    // água vira lama escura (o charco da terra morta)
                    por(l, m, Blocks.MUD.defaultBlockState());
                    continue;
                }
                double r = ruido(x, z, 11);
                if (d < CRATERA) {
                    int fundo = c.getY() + (int) Math.round(11 * ((d / CRATERA) * (d / CRATERA)));
                    fundo = Math.max(fundo, l.getMinBuildHeight() + 4);
                    fundo = Math.min(fundo, h);
                    for (int y = fundo + 1; y <= h + 1; y++) por(l, m.set(x, y, z), Blocks.AIR.defaultBlockState());
                    BlockState chao = r < 0.06 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : r < 0.22 ? Blocks.OBSIDIAN.defaultBlockState()
                            : r < 0.55 ? Blocks.BLACKSTONE.defaultBlockState() : r < 0.8 ? Blocks.BASALT.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
                    por(l, m.set(x, fundo, z), chao);
                    por(l, m.set(x, fundo - 1, z), Blocks.BLACKSTONE.defaultBlockState());
                    por(l, m.set(x, fundo - 2, z), Blocks.BLACKSTONE.defaultBlockState());
                    if (r > 0.985 && d > 14) por(l, m.set(x, fundo + 1, z), Blocks.SOUL_FIRE.defaultBlockState());
                    continue;
                }
                if (d < BORDA + 4) {
                    // a borda levantada pelo impacto
                    double k = 1 - Math.abs(d - (CRATERA + 4)) / 8.0;
                    int sobe = (int) Math.round(Math.max(0, k) * 4 + r * 1.5);
                    for (int y = h + 1; y <= h + sobe; y++) por(l, m.set(x, y, z), r < 0.5 ? Blocks.TUFF.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
                    por(l, m.set(x, h + sobe, z), r < 0.3 ? Blocks.SOUL_SOIL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
                    continue;
                }
                // terra morta
                BlockState top = r < 0.32 ? Blocks.SOUL_SOIL.defaultBlockState() : r < 0.55 ? Blocks.COARSE_DIRT.defaultBlockState()
                        : r < 0.65 ? Blocks.GRAVEL.defaultBlockState() : r < 0.75 ? Blocks.SOUL_SAND.defaultBlockState()
                        : r < 0.85 ? Blocks.PODZOL.defaultBlockState() : r < 0.93 ? Blocks.TUFF.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState();
                BlockState antes = l.getBlockState(m.set(x, h, z));
                if (antes.is(BlockTags.DIRT) || antes.is(BlockTags.SAND) || antes.is(Blocks.GRASS_BLOCK) || antes.is(Blocks.SNOW_BLOCK)
                        || antes.is(Blocks.GRAVEL) || antes.is(Blocks.CLAY) || antes.is(Blocks.MOSS_BLOCK) || antes.is(Blocks.STONE) || antes.is(Blocks.ANDESITE)
                        || antes.is(Blocks.DIORITE) || antes.is(Blocks.GRANITE) || antes.is(Blocks.CALCITE) || antes.is(Blocks.ICE) || antes.is(Blocks.PACKED_ICE)
                        || antes.is(BlockTags.TERRACOTTA)) {
                    por(l, m, top);
                    BlockState baixo = l.getBlockState(m.set(x, h - 1, z));
                    if (baixo.is(BlockTags.DIRT) || baixo.is(Blocks.GRASS_BLOCK) || baixo.is(BlockTags.SAND))
                        por(l, m, r < 0.5 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.SOUL_SOIL.defaultBlockState());
                }
                enfeitar(l, x, h, z, d, top);
            }
    }

    /** Túmulos, árvores secas, espinhos de basalto, mato seco, teias e fogo das almas. */
    private static void enfeitar(ServerLevel l, int x, int h, int z, double d, BlockState top) {
        BlockPos p = new BlockPos(x, h + 1, z);
        if (!l.getBlockState(p).isAir()) return;
        double r = ruido(x, z, 23);
        if (d > 44 && r < 0.011) { tumulo(l, p, ruido(x, z, 29)); return; }
        if (d > 46 && r > 0.996) { arvoreSeca(l, p, ruido(x, z, 31)); return; }
        if (d > BORDA && d < 66 && r > 0.991 && r <= 0.996) { espinho(l, p, ruido(x, z, 37)); return; }
        if (r > 0.94 && r <= 0.991) { por(l, p, Blocks.DEAD_BUSH.defaultBlockState()); return; }
        if (r > 0.925 && r <= 0.94) { por(l, p, Blocks.COBWEB.defaultBlockState()); return; }
        if (r > 0.92 && r <= 0.925) { por(l, p, Blocks.WITHER_ROSE.defaultBlockState()); return; }
        if (r > 0.915 && r <= 0.92 && (top.is(Blocks.SOUL_SOIL) || top.is(Blocks.SOUL_SAND))) por(l, p, Blocks.SOUL_FIRE.defaultBlockState());
    }

    private static void tumulo(ServerLevel l, BlockPos p, double r) {
        // lápide de pedra, cova de terra na frente; às vezes um crânio
        BlockState pedra = r < 0.33 ? Blocks.STONE_BRICK_WALL.defaultBlockState() : r < 0.66 ? Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState()
                : Blocks.COBBLESTONE_WALL.defaultBlockState();
        por(l, p, pedra);
        por(l, p.above(), r < 0.5 ? Blocks.STONE_BRICK_SLAB.defaultBlockState() : Blocks.ANDESITE_SLAB.defaultBlockState());
        por(l, p.below().north(), Blocks.COARSE_DIRT.defaultBlockState());
        por(l, p.below().north(2), Blocks.COARSE_DIRT.defaultBlockState());
        if (r > 0.82) por(l, p.north(), Blocks.SKELETON_SKULL.defaultBlockState());
        if (r < 0.1) por(l, p.north(), Blocks.SOUL_LANTERN.defaultBlockState());
    }

    private static void arvoreSeca(ServerLevel l, BlockPos p, double r) {
        int alt = 4 + (int) (r * 4);
        BlockState tronco = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
        for (int i = 0; i < alt; i++) por(l, p.above(i), tronco);
        BlockState galho = Blocks.DARK_OAK_FENCE.defaultBlockState();
        int[][] g = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int i = 0; i < 4; i++) {
            if (ruido(p.getX() + i, p.getZ(), 41) < 0.45) continue;
            BlockPos b = p.above(alt - 1 - (i % 2)).offset(g[i][0], 0, g[i][1]);
            por(l, b, galho);
            if (ruido(p.getX(), p.getZ() + i, 43) > 0.5) por(l, b.above().offset(g[i][0], 0, g[i][1]), galho);
        }
    }

    private static void espinho(ServerLevel l, BlockPos p, double r) {
        int alt = 3 + (int) (r * 6);
        for (int i = 0; i < alt; i++) por(l, p.above(i), i < alt - 2 ? Blocks.BASALT.defaultBlockState() : Blocks.POLISHED_BASALT.defaultBlockState());
    }

    // ===================== a Bateria Central da Morte =====================

    /**
     * A Bateria Central gigante da Tropa da Morte, no fundo da cratera: o formato de lanterna das baterias (boca larga
     * embaixo e em cima, corpo redondo de vidro escuro com nervuras e o núcleo aceso, tampa e alça), em pedra negra,
     * obsidiana e vidro preto, com o núcleo roxo da obsidiana chorona.
     */
    private static void bateria(ServerLevel l, BlockPos c) {
        BlockState pedra = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), lisa = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState entalhe = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), obs = Blocks.OBSIDIAN.defaultBlockState();
        BlockState vidro = Blocks.BLACK_STAINED_GLASS.defaultBlockState(), fume = Blocks.TINTED_GLASS.defaultBlockState();
        BlockState nucleo = Blocks.CRYING_OBSIDIAN.defaultBlockState(), ar = Blocks.AIR.defaultBlockState();
        int y0 = c.getY() + 1;
        // limpa o espaço e faz a plataforma de degraus
        for (int dx = -14; dx <= 14; dx++)
            for (int dz = -14; dz <= 14; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 14.3) continue;
                for (int y = y0; y < y0 + 46; y++) por(l, new BlockPos(c.getX() + dx, y, c.getZ() + dz), ar);
                por(l, new BlockPos(c.getX() + dx, y0 - 1, c.getZ() + dz), d > 12.5 ? Blocks.BLACKSTONE.defaultBlockState() : d > 11 ? entalhe : pedra);
                if (d <= 11) por(l, new BlockPos(c.getX() + dx, y0, c.getZ() + dz), d > 10 ? lisa : pedra);
            }
        // perfil: raio de cada camada (de baixo para cima)
        double[] raio = {9, 9, 7.5, 6.5, 5.5, 5, 7.5, 7.5, 7.5, 7.5, 7.5, 7.5, 7.5, 7.5, 7.5, 7.5, 7.5, 7.5, 7.5, 7.5, 7.5, 7.5, 7.5, 7.5,
                7.5, 7.5, 7.5, 7.5, 6, 7, 8.5, 8.5, 6.5, 4.5, 2.5, 1.2};
        int base = y0 + 1;
        for (int k = 0; k < raio.length; k++) {
            double r = raio[k];
            int ri = (int) Math.ceil(r);
            boolean corpo = k >= 6 && k <= 27;
            boolean faixa = k == 6 || k == 16 || k == 27;
            for (int dx = -ri; dx <= ri; dx++)
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > r + 0.5) continue;
                    BlockPos p = new BlockPos(c.getX() + dx, base + k, c.getZ() + dz);
                    boolean casca = d > r - 1.0;
                    BlockState s;
                    if (!corpo) s = casca ? (k == 0 || k == 30 ? entalhe : pedra) : (k >= 28 ? lisa : obs);
                    else if (faixa) s = casca ? obs : (d < 2.6 ? nucleo : lisa);
                    else if (casca) {
                        // oito nervuras de pedra; entre elas o vidro escuro
                        double ang = Math.atan2(dz, dx);
                        double t = (ang / (Math.PI * 2) * 8 + 8) % 1.0;
                        s = t < 0.16 || t > 0.9 ? pedra : (k % 4 == 0 ? fume : vidro);
                    } else s = d < 2.6 ? nucleo : ar;
                    por(l, p, s);
                }
        }
        // a alça: um arco grosso por cima, de um lado ao outro
        int topo = base + raio.length;
        for (int i = 0; i <= 40; i++) {
            double a = Math.PI * i / 40;
            int ax = (int) Math.round(Math.cos(a) * 8.5), ay = (int) Math.round(Math.sin(a) * 9);
            for (int dz = -1; dz <= 0; dz++) {
                por(l, new BlockPos(c.getX() + ax, topo - 4 + ay, c.getZ() + dz), pedra);
                por(l, new BlockPos(c.getX() + ax, topo - 3 + ay, c.getZ() + dz), pedra);
            }
        }
        // o topo da alça: a pérola negra (obsidiana chorona com vidro preto)
        BlockPos per = new BlockPos(c.getX(), topo + 6, c.getZ());
        por(l, per, nucleo);
        for (var dir : net.minecraft.core.Direction.values()) por(l, per.relative(dir), vidro);
        // quatro braseiros de fogo das almas na plataforma
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 4 + i * Math.PI / 2;
            BlockPos b = new BlockPos(c.getX() + (int) Math.round(Math.cos(a) * 11.5), y0, c.getZ() + (int) Math.round(Math.sin(a) * 11.5));
            por(l, b, entalhe);
            por(l, b.above(), Blocks.SOUL_SOIL.defaultBlockState());
            por(l, b.above(2), Blocks.SOUL_FIRE.defaultBlockState());
        }
        // postes com lanternas das almas na borda da cratera
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10;
            int x = c.getX() + (int) Math.round(Math.cos(a) * (BORDA - 1)), z = c.getZ() + (int) Math.round(Math.sin(a) * (BORDA - 1));
            int h = chao(l, x, z);
            for (int k = 1; k <= 3; k++) por(l, new BlockPos(x, h + k, z), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
            por(l, new BlockPos(x, h + 4, z), Blocks.SOUL_LANTERN.defaultBlockState());
        }
    }

    /** Onde o Necron fica: na frente da bateria (lado norte), flutuando. */
    public static BlockPos lugarDoNecron(BlockPos c) {
        return new BlockPos(c.getX(), c.getY() + 3, c.getZ() - 16);
    }

    // ===================== o Necron e os servos =====================

    /** Com alguém por perto: o Necron no lugar dele e servos em volta (até o limite). */
    private static void manter(ServerLevel l, BlockPos c) {
        List<ServerPlayer> perto = new ArrayList<>();
        for (ServerPlayer p : l.players()) if (!p.isSpectator() && dentro(l, c, p.getX(), p.getZ(), 60)) perto.add(p);
        if (perto.isEmpty()) return;
        if (!l.isLoaded(lugarDoNecron(c))) return;
        AABB area = new AABB(c).inflate(raioAtual + 20, 80, raioAtual + 20);
        if (BatalhaFinal.derrotado(l.getServer())) return;   // 0.56.0: o Necron caiu; a cratera fica quieta
        if (l.getEntitiesOfClass(NecronEntity.class, area).isEmpty()) {
            NecronEntity n = com.lanterna.lore.LoreEntities.NECRON.get().create(l);
            if (n != null) {
                BlockPos lp = lugarDoNecron(c);
                n.moveTo(lp.getX() + 0.5, lp.getY(), lp.getZ() + 0.5, 180, 0);
                n.casa(lp);
                n.setPersistenceRequired();
                l.addFreshEntity(n);
            }
        }
        int servos = l.getEntitiesOfClass(Mob.class, area, m -> m.getTags().contains(SERVO)).size();
        if (servos >= MAX_SERVOS || l.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return;
        ServerPlayer alvo = perto.get(l.random.nextInt(perto.size()));
        // nasce longe o bastante do jogador, dentro da terra morta
        for (int tent = 0; tent < 6; tent++) {
            double a = l.random.nextDouble() * Math.PI * 2, r = 18 + l.random.nextDouble() * 20;
            int x = (int) (alvo.getX() + Math.cos(a) * r), z = (int) (alvo.getZ() + Math.sin(a) * r);
            if (!dentro(l, c, x, z, 0) || !l.isLoaded(new BlockPos(x, 64, z))) continue;
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            Mob m = servo(l);
            if (m == null) return;
            m.moveTo(x + 0.5, y, z + 0.5, l.random.nextFloat() * 360, 0);
            m.addTag(SERVO);
            m.setPersistenceRequired();
            l.addFreshEntity(m);
            l.sendParticles(ParticleTypes.SOUL, m.getX(), m.getY() + 0.5, m.getZ(), 25, 0.4, 0.8, 0.4, 0.03);
            l.sendParticles(ParticleTypes.LARGE_SMOKE, m.getX(), m.getY() + 0.2, m.getZ(), 12, 0.5, 0.2, 0.5, 0.01);
            l.playSound(null, m.blockPosition(), SoundEvents.WITHER_SKELETON_AMBIENT, SoundSource.HOSTILE, 1f, 0.6f);
            if (m instanceof LanternaEntity le) le.setTarget(alvo);
            return;
        }
    }

    /** Um servo: Lanterna Negro (mais comum) ou morto-vivo carregando um anel da Morte. */
    @Nullable
    private static Mob servo(ServerLevel l) {
        int r = l.random.nextInt(10);
        if (r < 5) return LanternaEntity.lanterna(l, BuiltinCorps.BLACK, r == 0);
        EntityType<? extends Mob> tipo = switch (r) {
            case 5, 6 -> EntityType.ZOMBIE;
            case 7 -> EntityType.SKELETON;
            case 8 -> EntityType.HUSK;
            default -> EntityType.WITHER_SKELETON;
        };
        Mob m = tipo.create(l);
        if (m == null) return null;
        m.finalizeSpawn(l, l.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null);
        ItemStack anel = PowerRingItem.create(BuiltinCorps.BLACK.id(), BuiltinCorps.BLACK.color(), 100);
        m.setItemInHand(InteractionHand.OFF_HAND, anel);
        m.setDropChance(net.minecraft.world.entity.EquipmentSlot.OFFHAND, 0f);
        m.setCustomName(Component.translatable("necron.lanterna.servo"));
        m.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        return m;
    }

    /** Um servo novo (para as ondas da defesa das Baterias). */
    @Nullable
    public static Mob novoServo(ServerLevel l) {
        return servo(l);
    }

    public static boolean servo(net.minecraft.world.entity.Entity e) {
        return e.getTags().contains(SERVO);
    }

    // ===================== a luz de um anel só não fere =====================

    /** Usa dois anéis ao mesmo tempo (o segundo no punho, valendo). */
    public static boolean doisAneis(Player p) {
        var r = com.lanterna.capability.ModCapabilities.get(p).orElse(null);
        return r != null && r.hasRing() && !r.segundo().isEmpty();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void ataque(LivingAttackEvent e) {
        var alvo = e.getEntity();
        boolean necron = alvo instanceof NecronEntity;
        if (!necron && !servo(alvo)) return;
        if (e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (!(e.getSource().getEntity() instanceof Player p)) return;
        if (doisAneis(p) && (!necron || BatalhaFinal.ativa())) return;
        e.setCanceled(true);
        if (alvo.level() instanceof ServerLevel l) {
            l.sendParticles(ParticleTypes.SCULK_SOUL, alvo.getX(), alvo.getY() + alvo.getBbHeight() * 0.6, alvo.getZ(), 4, 0.3, 0.3, 0.3, 0.02);
            if (p instanceof ServerPlayer sp && sp.tickCount - sp.getPersistentData().getInt("lanterna_aviso_servo") > 60) {
                sp.getPersistentData().putInt("lanterna_aviso_servo", sp.tickCount);
                sp.displayClientMessage(Component.translatable(necron ? "necron.lanterna.imune" : "necron.lanterna.servo_imune")
                        .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), true);
                l.playSound(null, alvo.blockPosition(), SoundEvents.SCULK_SHRIEKER_HIT, SoundSource.HOSTILE, 0.6f, 0.5f);
            }
        }
    }

    private Cratera() {}
}
