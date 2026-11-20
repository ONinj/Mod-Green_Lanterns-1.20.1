package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.fonte.FonteDados;
import com.lanterna.lore.LoreWorldData;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import com.lanterna.vida.Emocoes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.*;

/**
 * 0.56.9: Capítulo 9, A Purificação (o fim da linha do herói).
 *
 *  necron_7  O Despertar: no Santuário, o Embrião da Vida acorda (o coração bate cada vez mais forte), sai da Terra
 *            voando até o céu sobre a cratera e solta uma onda de luz branca: a Bateria Central da Morte vira a
 *            Bateria Branca e a terra morta volta a ser grama, flores e água, pedaço por pedaço.
 *  necron_8  Os Irmãos Corrompidos: nos planetas das Tropas que caíram, Lanternas corrompidos rondam a bateria
 *            quebrada. Derrotados, não morrem: a luz do Embrião os purifica. Purificados os seis, vem o líder
 *            corrompido de cada planeta (Aelira em Zamaron, Tovrek no Índigo, Irmã Calyx em Odym). Purificado o
 *            líder, o núcleo guardado volta para casa, a bateria se acende de novo e a terra do planeta se cura.
 *  necron_9  O Ritual da Terra: na Bateria Branca, um Lanterna de cada Tropa desce, e as sete luzes vão até o
 *            Embrião. Ele sobe para o espaço, livre. Epílogo: em Korugar, algo amarelo acorda (gancho para Parallax).
 *
 * Tudo é do mundo: o Embrião, a Terra curada, os planetas restaurados e o ritual valem para todos.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Purificacao {
    public static final String PARTIU = "saga:embriao_partiu", T_DESPERTAR = "saga:embriao_t:", PURIFICADA = "saga:terra_purificada",
            T_RITUAL = "saga:ritual_t:", FIM = "saga:necron_fim", RESTAURADA = "saga:restaurada:";
    public static final String CORR = "lanterna_corrompido", LIDER = "lanterna_lider_corrompido";
    public static final int VOO = 100, CHEGA = 400, ONDA = 240, ALTO = 70, CORROMPIDOS = 6;
    private static final String[] CONDENADAS = {"violet", "indigo", "blue"};

    // ===================== marcas do mundo =====================

    private static boolean feito(MinecraftServer s, String k) { return LoreWorldData.get(s).siteBuilt(k); }
    private static void marcar(MinecraftServer s, String k) { LoreWorldData.get(s).markSite(k); }

    private static long numero(MinecraftServer s, String pre) {
        for (String id : LoreWorldData.get(s).sites())
            if (id.startsWith(pre)) try { return Long.parseLong(id.substring(pre.length())); } catch (NumberFormatException ignored) {}
        return -1;
    }

    public static boolean partiu(MinecraftServer s) { return feito(s, PARTIU); }
    public static boolean purificada(MinecraftServer s) { return feito(s, PURIFICADA); }
    public static boolean fim(MinecraftServer s) { return feito(s, FIM); }

    /** O que o cliente precisa para desenhar o Embrião acordando, voando e partindo, e para tirar a névoa. */
    public static String paraCliente(MinecraftServer s) {
        StringBuilder sb = new StringBuilder();
        long t = numero(s, T_DESPERTAR);
        BlockPos c = Escurece.camara(s);
        if (t >= 0 && c != null) {
            sb.append("embriaovoo:").append(c.getX()).append(":").append(c.getY()).append(":").append(c.getZ());
            sb.append(";embriaot:").append(t).append(":0:0");
        }
        long r = numero(s, T_RITUAL);
        if (r >= 0) sb.append(sb.length() == 0 ? "" : ";").append("embriaofim:").append(r).append(":0:0");
        if (purificada(s)) sb.append(sb.length() == 0 ? "" : ";").append("purificada:0:0:0");
        return sb.toString();
    }

    private static void avisarClientes(MinecraftServer s) {
        for (ServerPlayer q : s.getPlayerList().getPlayers()) SagaNecron.mandarMundo(q);
    }

    // ===================== tick do jogador =====================

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 9) return;
        LoreData d = MissionManager.data(p);
        if (d == null) return;
        MinecraftServer s = p.server;
        if (d.isActive("necron_7")) {
            if (partiu(s) && FonteDados.chamado(p, "necron:despertar") < 1) FonteDados.chamado(p, "necron:despertar", 1);
            if (purificada(s) && FonteDados.chamado(p, "necron:purificou_terra") < 1) FonteDados.chamado(p, "necron:purificou_terra", 1);
            BlockPos c = Escurece.camara(s);
            if (!partiu(s) && c != null && p.level().dimension() == Level.OVERWORLD && p.distanceToSqr(Vec3.atCenterOf(c)) < 10 * 10) despertar(p.serverLevel(), c);
        }
        if (d.isActive("necron_8")) {
            for (String t : CONDENADAS) {
                PlanetLore pl = planeta(t);
                if (pl != null && (!Queda.caida(s, pl) || feito(s, RESTAURADA + pl.planet)) && FonteDados.chamado(p, "necron:purificado_" + t) < 1)
                    FonteDados.chamado(p, "necron:purificado_" + t, 1);
            }
            PlanetLore aqui = PlanetLore.byDimension(p.level().dimension());
            if (aqui != null && Arrays.asList(CONDENADAS).contains(aqui.corps.id().getPath()) && Queda.caida(s, aqui) && Queda.pronta(s, aqui))
                corrompidos(p.serverLevel(), p, aqui);
        }
        if (d.isActive("necron_9")) {
            if (fim(s) && FonteDados.chamado(p, "necron:ritual") < 1) { FonteDados.chamado(p, "necron:ritual", 1); ProximaSaga.heroi(p); }
            BlockPos c = Cratera.centro(s);
            if (c != null && purificada(s) && ritualT < 0 && !fim(s) && p.level().dimension() == Level.OVERWORLD) {
                double dx = p.getX() - c.getX(), dz = p.getZ() - c.getZ();
                if (dx * dx + dz * dz < 16 * 16) comecarRitual(p.serverLevel(), c);
            }
        }
    }

    private static PlanetLore planeta(String tropa) {
        for (PlanetLore p : PlanetLore.values()) if (p.corps.id().getPath().equals(tropa)) return p;
        return null;
    }

    // ===================== o Despertar =====================

    private static int despT = -1;
    private static BlockPos despC;

    private static void despertar(ServerLevel l, BlockPos c) {
        MinecraftServer s = l.getServer();
        marcar(s, PARTIU);
        marcar(s, T_DESPERTAR + l.getGameTime());
        despT = 0;
        despC = c;
        avisarClientes(s);
        for (ServerPlayer q : l.players()) if (q.distanceToSqr(Vec3.atCenterOf(c)) < 120 * 120)
            q.sendSystemMessage(Component.translatable("necron.lanterna.desperta_1").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));
        LanternaMod.LOGGER.info("Lanterna: o Embrião da Vida despertou");
    }

    private static void passoDespertar(ServerLevel l) {
        int t = ++despT;
        BlockPos c = despC;
        if (t < VOO) {
            int ritmo = t < 50 ? 20 : 10;
            if (t % ritmo == 0) l.playSound(null, c, SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 2f, 1.3f);
            l.sendParticles(ParticleTypes.END_ROD, c.getX() + 0.5, c.getY() - 1.5, c.getZ() + 0.5, 3, 3, 2, 3, 0.02);
            l.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.getX() + 0.5, c.getY() - 3, c.getZ() + 0.5, 2, 5, 0.5, 5, 0);
        }
        if (t == 60) falar(l, c, "necron.lanterna.desperta_2", ChatFormatting.GREEN);
        if (t == VOO) {
            l.playSound(null, c, SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT, 3f, 0.6f);
            l.playSound(null, c, SoundEvents.TOTEM_USE, SoundSource.AMBIENT, 2f, 0.8f);
        }
        BlockPos cr = Cratera.centro(l.getServer());
        if (t == CHEGA && cr != null) {
            falarTodos(l, "necron.lanterna.desperta_3", ChatFormatting.WHITE);
            for (ServerPlayer q : l.players()) {
                q.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.translatable("necron.lanterna.purifica_titulo").withStyle(ChatFormatting.WHITE)));
                q.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.translatable("necron.lanterna.purifica_sub").withStyle(ChatFormatting.GRAY)));
            }
            l.playSound(null, cr.above(ALTO), SoundEvents.END_PORTAL_SPAWN, SoundSource.AMBIENT, 4f, 1.4f);
            bateriaBranca(l, cr);
            montarTerra(l, cr);
            despT = -1;
        }
    }

    private static void falar(ServerLevel l, BlockPos c, String chave, ChatFormatting cor) {
        for (ServerPlayer q : l.players()) if (q.distanceToSqr(Vec3.atCenterOf(c)) < 400 * 400) q.sendSystemMessage(Component.translatable(chave).withStyle(cor, ChatFormatting.ITALIC));
    }

    private static void falarTodos(ServerLevel l, String chave, ChatFormatting cor) {
        for (ServerPlayer q : l.getServer().getPlayerList().getPlayers()) q.sendSystemMessage(Component.translatable(chave).withStyle(cor, ChatFormatting.ITALIC));
    }

    // ===================== a cura da terra =====================

    /** Uma fila de pedaços (chunks) a curar, por mundo. */
    private record Cura(ResourceKey<Level> mundo, BlockPos centro, int raio, List<long[]> fila, String fim) {}
    private static final List<Cura> CURAS = new ArrayList<>();

    private static void montarTerra(ServerLevel l, BlockPos c) {
        int raio = Cratera.raioAtual + 24;
        CURAS.add(new Cura(l.dimension(), c, raio, fila(c, raio), PURIFICADA));
    }

    private static List<long[]> fila(BlockPos c, int raio) {
        List<long[]> f = new ArrayList<>();
        int r = (raio >> 4) + 1, cx0 = c.getX() >> 4, cz0 = c.getZ() >> 4;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                double mx = ((cx0 + dx) << 4) + 8 - c.getX(), mz = ((cz0 + dz) << 4) + 8 - c.getZ();
                if (mx * mx + mz * mz > (raio + 12) * (raio + 12)) continue;
                f.add(new long[]{cx0 + dx, cz0 + dz, (long) (mx * mx + mz * mz)});
            }
        f.sort((a, b) -> Long.compare(a[2], b[2]));   // do centro para fora, como uma onda
        return f;
    }

    private static double ruido(int x, int z, long sal) {
        long h = x * 3129871L ^ z * 116129781L ^ sal;
        h = h * h * 42317861L + h * 11L;
        return ((h >> 16) & 0xFFFF) / 65535.0;
    }

    private static void por(ServerLevel l, BlockPos p, BlockState s) { l.setBlock(p, s, 2 | 16); }

    private static boolean morto(BlockState s) {
        return s.is(Blocks.SOUL_SOIL) || s.is(Blocks.SOUL_SAND) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.ROOTED_DIRT)
                || s.is(Blocks.CRYING_OBSIDIAN) || s.is(Blocks.OBSIDIAN) || s.is(Blocks.BLACKSTONE) || s.is(Blocks.BASALT) || s.is(Blocks.SMOOTH_BASALT)
                || s.is(Blocks.TUFF) || s.is(Blocks.GRAVEL);
    }

    /** Cura um pedaço: o chão morto vira grama (com flores e mudas), a lama volta a ser água, o mato seco e as teias somem. */
    private static void curarChunk(ServerLevel l, BlockPos c, int raio, int cx, int cz) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState[] flores = {Blocks.DANDELION.defaultBlockState(), Blocks.POPPY.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState(),
                Blocks.CORNFLOWER.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState(), Blocks.LILY_OF_THE_VALLEY.defaultBlockState()};
        for (int ix = 0; ix < 16; ix++)
            for (int iz = 0; iz < 16; iz++) {
                int x = (cx << 4) + ix, z = (cz << 4) + iz;
                double dx = x - c.getX(), dz = z - c.getZ(), d = Math.sqrt(dx * dx + dz * dz);
                if (d > raio) continue;
                // a Bateria Branca e a plataforma dela ficam como estão
                if (d < 15) continue;
                int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                // o que está em cima do chão: teias e fogo somem, mato seco vira flor, as árvores secas brotam de novo
                // e os espinhos de basalto da Morte se desfazem
                for (int k = 0; k < 12; k++) {
                    BlockState s = l.getBlockState(m.set(x, y - k, z));
                    if (s.is(Blocks.COBWEB) || s.is(Blocks.SOUL_FIRE)) por(l, m, Blocks.AIR.defaultBlockState());
                    else if (s.is(Blocks.DEAD_BUSH)) por(l, m, ruido(x, z, 5) < 0.5 ? Blocks.GRASS.defaultBlockState() : flores[(int) (ruido(x, z, 9) * flores.length) % flores.length]);
                    else if (s.is(Blocks.WITHER_ROSE)) por(l, m, Blocks.POPPY.defaultBlockState());
                    else if (s.is(Blocks.STRIPPED_DARK_OAK_LOG)) {
                        por(l, m, Blocks.OAK_LOG.defaultBlockState());
                        if (l.getBlockState(m.above()).isAir() || l.getBlockState(m.above()).is(Blocks.DARK_OAK_FENCE)) copa(l, m.immutable());
                    } else if (s.is(Blocks.DARK_OAK_FENCE)) por(l, m, folha());
                    else if ((s.is(Blocks.BASALT) || s.is(Blocks.POLISHED_BASALT)) && d >= Cratera.BORDA) por(l, m, Blocks.AIR.defaultBlockState());
                }
                y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockState top = l.getBlockState(m.set(x, y, z));
                if (top.is(Blocks.MUD)) { por(l, m, Blocks.WATER.defaultBlockState()); continue; }
                if (!morto(top)) continue;
                // pedras que já eram do lugar (fora da cratera, tufo e cascalho) ficam
                if (d >= Cratera.BORDA + 4 && (top.is(Blocks.TUFF) || top.is(Blocks.GRAVEL)) && ruido(x, z, 3) < 0.6) continue;
                double r = ruido(x, z, 13);
                por(l, m, d < Cratera.CRATERA && r < 0.25 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
                BlockState baixo = l.getBlockState(m.set(x, y - 1, z));
                if (morto(baixo)) por(l, m, Blocks.DIRT.defaultBlockState());
                BlockPos cima = new BlockPos(x, y + 1, z);
                if (!l.getBlockState(cima).isAir()) continue;
                double q = ruido(x, z, 17);
                if (q > 0.985) por(l, cima, Blocks.OAK_SAPLING.defaultBlockState());
                else if (q > 0.9) por(l, cima, flores[(int) (ruido(x, z, 19) * flores.length) % flores.length]);
                else if (q > 0.6) por(l, cima, Blocks.GRASS.defaultBlockState());
            }
    }

    private static BlockState folha() {
        return Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
    }

    /** A copa nova de uma árvore seca que voltou à vida. */
    private static void copa(ServerLevel l, BlockPos topo) {
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = 0; dy <= 2; dy++) {
                    if (Math.abs(dx) + Math.abs(dz) + dy > 3 || dx == 0 && dz == 0 && dy == 0) continue;
                    BlockPos q = topo.offset(dx, dy, dz);
                    if (l.getBlockState(q).isAir()) por(l, q, folha());
                }
    }

    /** A Bateria Central da Morte vira a Bateria Branca: pedra negra em quartzo e calcita, vidro escuro em vidro claro, o núcleo em luz. */
    private static void bateriaBranca(ServerLevel l, BlockPos c) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -15; dx <= 15; dx++)
            for (int dz = -15; dz <= 15; dz++)
                for (int y = c.getY() - 1; y <= c.getY() + 56; y++) {
                    BlockState s = l.getBlockState(m.set(c.getX() + dx, y, c.getZ() + dz));
                    if (s.isAir()) continue;
                    Block b = s.getBlock();
                    BlockState n = null;
                    if (b == Blocks.POLISHED_BLACKSTONE_BRICKS) n = Blocks.QUARTZ_BRICKS.defaultBlockState();
                    else if (b == Blocks.POLISHED_BLACKSTONE) n = Blocks.SMOOTH_QUARTZ.defaultBlockState();
                    else if (b == Blocks.CHISELED_POLISHED_BLACKSTONE) n = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
                    else if (b == Blocks.BLACKSTONE) n = Blocks.CALCITE.defaultBlockState();
                    else if (b == Blocks.OBSIDIAN) n = Blocks.QUARTZ_BLOCK.defaultBlockState();
                    else if (b == Blocks.BLACK_STAINED_GLASS) n = Blocks.WHITE_STAINED_GLASS.defaultBlockState();
                    else if (b == Blocks.TINTED_GLASS) n = Blocks.GLASS.defaultBlockState();
                    else if (b == Blocks.CRYING_OBSIDIAN) n = Blocks.SEA_LANTERN.defaultBlockState();
                    else if (b == Blocks.SOUL_FIRE) n = Blocks.AIR.defaultBlockState();
                    else if (b == Blocks.SOUL_SOIL) n = Blocks.MOSS_BLOCK.defaultBlockState();
                    else if (b == Blocks.POLISHED_BLACKSTONE_WALL) n = Blocks.QUARTZ_PILLAR.defaultBlockState();
                    else if (b == Blocks.SOUL_LANTERN) n = Blocks.LANTERN.defaultBlockState();
                    if (n != null) por(l, m, n);
                }
        // os postes da borda da cratera também clareiam
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10;
            int x = c.getX() + (int) Math.round(Math.cos(a) * (Cratera.BORDA - 1)), z = c.getZ() + (int) Math.round(Math.sin(a) * (Cratera.BORDA - 1));
            for (int y = c.getY() - 5; y < c.getY() + 40; y++) {
                BlockState s = l.getBlockState(m.set(x, y, z));
                if (s.is(Blocks.POLISHED_BLACKSTONE_WALL)) por(l, m, Blocks.QUARTZ_PILLAR.defaultBlockState());
                else if (s.is(Blocks.SOUL_LANTERN)) por(l, m, Blocks.LANTERN.defaultBlockState());
            }
        }
        l.sendParticles(ParticleTypes.END_ROD, c.getX() + 0.5, c.getY() + 20, c.getZ() + 0.5, 300, 8, 15, 8, 0.15);
    }

    @SubscribeEvent
    public static void tickServidor(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
        if (despT >= 0) passoDespertar(s.overworld());
        // retoma depois de reiniciar o servidor no meio
        if (despT < 0 && CURAS.isEmpty() && partiu(s) && !purificada(s) && s.getTickCount() % 100 == 0) {
            long t0 = numero(s, T_DESPERTAR);
            BlockPos cr = Cratera.centro(s);
            if (cr != null && t0 >= 0 && s.overworld().getGameTime() - t0 > CHEGA) { bateriaBranca(s.overworld(), cr); montarTerra(s.overworld(), cr); }
        }
        if (!CURAS.isEmpty()) {
            Cura c = CURAS.get(0);
            ServerLevel l = s.getLevel(c.mundo());
            if (l == null) { CURAS.remove(0); return; }
            for (int k = 0; k < 2 && !c.fila().isEmpty(); k++) {
                long[] ch = c.fila().remove(0);
                l.getChunk((int) ch[0], (int) ch[1]);
                curarChunk(l, c.centro(), c.raio(), (int) ch[0], (int) ch[1]);
            }
            if (c.fila().isEmpty()) {
                CURAS.remove(0);
                marcar(s, c.fim());
                if (c.fim().equals(PURIFICADA)) {
                    falarTodos(l, "necron.lanterna.terra_curada", ChatFormatting.GREEN);
                    for (ServerPlayer q : s.getPlayerList().getPlayers()) {
                        LoreData d = MissionManager.data(q);
                        if (d != null && d.isActive("necron_7")) FonteDados.chamado(q, "necron:purificou_terra", 1);
                    }
                }
                avisarClientes(s);
                LanternaMod.LOGGER.info("Lanterna: a terra se curou ({})", c.fim());
            }
        }
        if (ritualT >= 0) passoRitual(s.overworld());
        if (s.getTickCount() % 10 == 0) lideres(s);
        sobem(s);
    }

    // ===================== os irmãos corrompidos =====================

    private static String chave(PlanetLore p, String o) { return "saga:corr:" + p.planet + ":" + o; }

    private static int purificados(MinecraftServer s, PlanetLore p) {
        int n = 0;
        while (n < CORROMPIDOS && feito(s, chave(p, "p" + (n + 1)))) n++;
        return n;
    }

    private static BuiltinCorps corps(String t) {
        for (BuiltinCorps b : BuiltinCorps.values()) if (b.id().getPath().equals(t)) return b;
        return BuiltinCorps.GREEN;
    }

    private static void corrompidos(ServerLevel l, ServerPlayer p, PlanetLore pl) {
        MinecraftServer s = p.server;
        if (feito(s, RESTAURADA + pl.planet)) return;
        BlockPos h = PlanetLore.HUB;
        double dx = p.getX() - h.getX(), dz = p.getZ() - h.getZ();
        if (dx * dx + dz * dz > 90 * 90 || !l.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(h.getX() >> 4, h.getZ() >> 4))) return;
        String t = pl.corps.id().getPath();
        if (!feito(s, chave(pl, "aviso"))) {
            marcar(s, chave(pl, "aviso"));
            p.sendSystemMessage(Component.translatable("necron.lanterna.corrompidos_" + t).withStyle(st -> st.withColor(pl.corps.color())));
        }
        int feitos = purificados(s, pl);
        if (feitos < CORROMPIDOS) {
            if (p.tickCount % 40 != 9) return;
            String tag = CORR + ":" + pl.planet;
            int vivos = l.getEntitiesOfClass(LanternaEntity.class, new AABB(h).inflate(140, 200, 140), x -> x.getTags().contains(tag)).size();
            for (int i = vivos + feitos; i < CORROMPIDOS; i++) {
                LanternaEntity c = LanternaEntity.lanterna(l, BuiltinCorps.BLACK, true);
                if (c == null) continue;
                double a = Math.PI * 2 * i / CORROMPIDOS + 0.3;
                int x = h.getX() + (int) (Math.cos(a) * 26), z = h.getZ() + (int) (Math.sin(a) * 26);
                c.moveTo(x + 0.5, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5, 0, 0);
                c.addTag(Cratera.SERVO);
                c.addTag(CORR);
                c.addTag(tag);
                c.setPersistenceRequired();
                c.restrictTo(h, 50);
                c.setCustomName(Component.translatable("necron.lanterna.corrompido", Component.translatable("corps.lanterna." + t)).withStyle(ChatFormatting.DARK_GRAY));
                var hp = c.getAttribute(Attributes.MAX_HEALTH);
                if (hp != null) { hp.setBaseValue(80); c.setHealth(80); }
                l.addFreshEntity(c);
                l.sendParticles(ParticleTypes.SOUL, c.getX(), c.getY() + 1, c.getZ(), 20, 0.4, 0.8, 0.4, 0.03);
            }
            return;
        }
        // os seis purificados: o líder
        if (feito(s, chave(pl, "lider_fim")) || p.tickCount % 40 != 9) return;
        if (!l.getEntitiesOfClass(LanternaEntity.class, new AABB(h).inflate(160, 250, 160), x -> x.getTags().contains(LIDER)).isEmpty()) return;
        LanternaEntity c = LanternaEntity.lanterna(l, BuiltinCorps.BLACK, true);
        if (c == null) return;
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, h.getX() + 16, h.getZ());
        c.moveTo(h.getX() + 16.5, y, h.getZ() + 0.5, 90, 0);
        c.addTag(Cratera.SERVO);
        c.addTag(LIDER);
        c.addTag(LIDER + ":" + pl.planet);
        c.setPersistenceRequired();
        c.restrictTo(h, 40);
        c.setCustomName(nomeLider(t));
        c.setCustomNameVisible(true);
        var hp = c.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(400);
        var ar = c.getAttribute(Attributes.ARMOR);
        if (ar != null) ar.setBaseValue(8);
        var at = c.getAttribute(Attributes.ATTACK_DAMAGE);
        if (at != null) at.setBaseValue(9);
        c.setHealth(c.getMaxHealth());
        c.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        l.addFreshEntity(c);
        l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.getX(), c.getY() + 1, c.getZ(), 100, 0.6, 2.5, 0.6, 0.05);
        l.playSound(null, c.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.7f, 0.9f);
        if (!feito(s, chave(pl, "lider"))) {
            marcar(s, chave(pl, "lider"));
            for (ServerPlayer q : l.players()) q.sendSystemMessage(Component.translatable("necron.lanterna.lider_" + t + "_1").withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    private static Component nomeLider(String t) {
        return Component.translatable("necron.lanterna.lider", Component.translatable("necron.lanterna.lider." + t)).withStyle(ChatFormatting.DARK_PURPLE);
    }

    private static final Map<UUID, ServerBossEvent> BARRAS = new HashMap<>();

    /** A barra do líder e os golpes dele (a onda de almas e os corrompidos que ele chama de volta). */
    private static void lideres(MinecraftServer s) {
        for (String t : CONDENADAS) {
            PlanetLore pl = planeta(t);
            if (pl == null || !Queda.caida(s, pl) || !feito(s, chave(pl, "lider")) || feito(s, chave(pl, "lider_fim"))) continue;
            var corpo = com.lanterna.space.SpaceBodies.byId(pl.planet);
            ServerLevel l = corpo == null || corpo.dimension() == null ? null : s.getLevel(corpo.dimension());
            if (l == null || l.players().isEmpty()) continue;
            for (LanternaEntity c : l.getEntitiesOfClass(LanternaEntity.class, new AABB(PlanetLore.HUB).inflate(160, 250, 160), x -> x.getTags().contains(LIDER))) {
                ServerBossEvent b = BARRAS.computeIfAbsent(c.getUUID(), u -> new ServerBossEvent(nomeLider(t), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10));
                b.setProgress(c.getHealth() / c.getMaxHealth());
                for (ServerPlayer q : l.players()) {
                    boolean perto = q.distanceToSqr(c) < 64 * 64;
                    if (perto && !b.getPlayers().contains(q)) b.addPlayer(q);
                    else if (!perto && b.getPlayers().contains(q)) b.removePlayer(q);
                }
                int tc = s.getTickCount();
                if (tc % 90 == 70) {
                    for (int k = 0; k < 24; k++) {
                        double a = Math.PI * 2 * k / 24;
                        l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.getX() + Math.cos(a) * 2, c.getY() + 0.3, c.getZ() + Math.sin(a) * 2, 1, 0, 0.2, 0, 0.01);
                    }
                }
                if (tc % 90 == 0) {
                    l.playSound(null, c.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.8f, 1.1f);
                    for (int k = 0; k < 48; k++) {
                        double a = Math.PI * 2 * k / 48;
                        l.sendParticles(ParticleTypes.SOUL, c.getX() + Math.cos(a) * 5, c.getY() + 0.4, c.getZ() + Math.sin(a) * 5, 1, 0, 0.1, 0, 0.02);
                    }
                    for (ServerPlayer q : l.getEntitiesOfClass(ServerPlayer.class, c.getBoundingBox().inflate(7), q -> !q.isCreative() && !q.isSpectator())) {
                        q.hurt(l.damageSources().indirectMagic(c, c), 7);
                        Vec3 v = q.position().subtract(c.position()).multiply(1, 0, 1).normalize().scale(1.3);
                        q.setDeltaMovement(v.x, 0.5, v.z);
                        q.hurtMarked = true;
                    }
                }
            }
        }
    }

    /** Derrotado, um corrompido não morre: a luz do Embrião o purifica e ele volta a ser um Lanterna da Tropa dele. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void caiu(LivingDeathEvent e) {
        LivingEntity c = e.getEntity();
        boolean lider = c.getTags().contains(LIDER);
        if (!lider && !c.getTags().contains(CORR) || !(c.level() instanceof ServerLevel l)) return;
        PlanetLore pl = PlanetLore.byDimension(l.dimension());
        if (pl == null) return;
        e.setCanceled(true);
        MinecraftServer s = l.getServer();
        String t = pl.corps.id().getPath();
        LanternaEntity novo = LanternaEntity.lanterna(l, pl.corps, lider);
        if (novo != null) {
            novo.moveTo(c.getX(), c.getY(), c.getZ(), c.getYRot(), 0);
            novo.setNoAi(true);
            novo.setNoGravity(true);
            novo.setInvulnerable(true);
            if (lider) novo.setCustomName(Component.translatable("necron.lanterna.lider." + t).withStyle(st -> st.withColor(pl.corps.color())));
            l.addFreshEntity(novo);
            SOBE.add(new Object[]{l.dimension(), novo.getUUID(), lider ? -60 : -20});
        }
        int cor = pl.corps.color();
        DustParticleOptions d = new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 2f);
        l.sendParticles(d, c.getX(), c.getY() + 1, c.getZ(), 60, 0.5, 1, 0.5, 0);
        l.sendParticles(ParticleTypes.END_ROD, c.getX(), c.getY() + 1, c.getZ(), 40, 0.4, 1, 0.4, 0.1);
        l.playSound(null, c.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 1.2f);
        c.discard();
        if (!lider) {
            int n = purificados(s, pl) + 1;
            marcar(s, chave(pl, "p" + n));
            for (ServerPlayer q : l.players())
                if (q.distanceToSqr(c) < 120 * 120) q.displayClientMessage(Component.translatable("necron.lanterna.purificados", n, CORROMPIDOS).withStyle(st -> st.withColor(cor)), true);
            if (n == CORROMPIDOS) for (ServerPlayer q : l.players()) q.sendSystemMessage(Component.translatable("necron.lanterna.corrompidos_fim").withStyle(ChatFormatting.DARK_PURPLE));
            return;
        }
        ServerBossEvent b = BARRAS.remove(c.getUUID());
        if (b != null) b.removeAllPlayers();
        marcar(s, chave(pl, "lider_fim"));
        restaurar(l, pl);
    }

    private static final List<Object[]> SOBE = new ArrayList<>();

    private static void sobem(MinecraftServer s) {
        for (Iterator<Object[]> it = SOBE.iterator(); it.hasNext(); ) {
            Object[] o = it.next();
            @SuppressWarnings("unchecked") ServerLevel l = s.getLevel((ResourceKey<Level>) o[0]);
            Entity a = l == null ? null : l.getEntity((UUID) o[1]);
            int t = (int) o[2] + 1;
            o[2] = t;
            if (a == null) { it.remove(); continue; }
            if (t <= 0) continue;
            Vec3 alvo = Vec3.atCenterOf(PlanetLore.HUB).with(net.minecraft.core.Direction.Axis.Y, a.getY());
            Vec3 dir = alvo.subtract(a.position());
            Vec3 passo = dir.lengthSqr() > 1 ? dir.normalize().scale(0.4) : Vec3.ZERO;
            a.setPos(a.getX() + passo.x, a.getY() + 0.12, a.getZ() + passo.z);
            if (t % 3 == 0) l.sendParticles(ParticleTypes.END_ROD, a.getX(), a.getY() + 1, a.getZ(), 1, 0.2, 0.3, 0.2, 0.01);
            if (t > 100) { a.discard(); it.remove(); }
        }
    }

    private static Block vidro(String t) {
        return switch (t) {
            case "red" -> Blocks.RED_STAINED_GLASS;
            case "orange" -> Blocks.ORANGE_STAINED_GLASS;
            case "yellow" -> Blocks.YELLOW_STAINED_GLASS;
            case "green" -> Blocks.LIME_STAINED_GLASS;
            case "blue" -> Blocks.LIGHT_BLUE_STAINED_GLASS;
            case "indigo" -> Blocks.BLUE_STAINED_GLASS;
            case "violet" -> Blocks.MAGENTA_STAINED_GLASS;
            default -> Blocks.WHITE_STAINED_GLASS;
        };
    }

    /** O planeta volta: o vidro preto da bateria se acende na cor da Tropa, a terra em volta se cura e a queda acaba. */
    private static void restaurar(ServerLevel l, PlanetLore pl) {
        MinecraftServer s = l.getServer();
        String t = pl.corps.id().getPath();
        BlockPos h = PlanetLore.HUB;
        int y0 = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, h.getX() + 12, h.getZ() + 12) - 2;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState v = vidro(t).defaultBlockState();
        for (int dx = -12; dx <= 12; dx++)
            for (int dz = -12; dz <= 12; dz++)
                for (int y = y0 - 4; y < y0 + 64; y++) {
                    BlockState b = l.getBlockState(m.set(h.getX() + dx, y, h.getZ() + dz));
                    if (b.is(Blocks.BLACK_STAINED_GLASS)) por(l, m, v);
                    else if (b.is(Blocks.SOUL_FIRE)) por(l, m, Blocks.AIR.defaultBlockState());
                }
        marcar(s, RESTAURADA + pl.planet);
        LoreWorldData.get(s).resetSite("saga:caida:" + pl.planet);
        CURAS.add(new Cura(l.dimension(), h, 72, fila(h, 72), RESTAURADA + pl.planet + ":curada"));
        for (Mob x : l.getEntitiesOfClass(Mob.class, new AABB(h).inflate(140, 200, 140), Cratera::servo)) {
            l.sendParticles(ParticleTypes.LARGE_SMOKE, x.getX(), x.getY() + 1, x.getZ(), 10, 0.3, 0.6, 0.3, 0.02);
            x.discard();
        }
        int cor = pl.corps.color();
        DustParticleOptions d = new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 4f);
        for (int k = 0; k < 60; k++) l.sendParticles(d, h.getX() + 0.5, y0 + k * 2, h.getZ() + 0.5, 6, 2, 0.6, 2, 0);
        l.playSound(null, h.above(y0 - h.getY() + 10), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 4f, 0.7f);
        for (ServerPlayer q : l.players()) {
            q.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                    Component.translatable("necron.lanterna.restaurada_titulo", Component.translatable("necron.lanterna.tropa." + t)).withStyle(st -> st.withColor(cor))));
            q.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.translatable("necron.lanterna.restaurada_sub").withStyle(ChatFormatting.GRAY)));
            q.sendSystemMessage(Component.translatable("necron.lanterna.lider_" + t + "_fim").withStyle(st -> st.withColor(cor)));
            if (Ruinas.nucleoGuardado(s, t)) q.sendSystemMessage(Component.translatable("necron.lanterna.nucleo_volta", Component.translatable("necron.lanterna.tropa." + t)).withStyle(ChatFormatting.GOLD));
            FonteDados.chamado(q, "necron:purificado_" + t, 1);
        }
        avisarClientes(s);
        LanternaMod.LOGGER.info("Lanterna: a Tropa de {} foi restaurada", pl.planet);
    }

    // ===================== o Ritual da Terra =====================

    private static int ritualT = -1;
    private static BlockPos ritualC;
    private static final List<UUID> RITUAL = new ArrayList<>();

    private static void comecarRitual(ServerLevel l, BlockPos c) {
        ritualT = 0;
        ritualC = c;
        RITUAL.clear();
        int y = c.getY() + 1;
        for (int i = 0; i < Emocoes.ASPECTOS.length; i++) {
            BuiltinCorps b = corps(Emocoes.ASPECTOS[i]);
            LanternaEntity a = LanternaEntity.lanterna(l, b, true);
            if (a == null) continue;
            double ang = Math.PI * 2 * i / Emocoes.ASPECTOS.length;
            a.moveTo(c.getX() + 0.5 + Math.cos(ang) * 12, y + 40, c.getZ() + 0.5 + Math.sin(ang) * 12, (float) Math.toDegrees(ang) + 90, 0);
            a.setNoAi(true);
            a.setNoGravity(true);
            a.setInvulnerable(true);
            a.setCustomName(Component.translatable("necron.lanterna.ritual_lanterna", Component.translatable("corps.lanterna." + b.id().getPath())).withStyle(st -> st.withColor(b.color())));
            l.addFreshEntity(a);
            RITUAL.add(a.getUUID());
        }
        falar(l, c, "necron.lanterna.ritual_1", ChatFormatting.WHITE);
        LanternaMod.LOGGER.info("Lanterna: começou o Ritual da Terra");
    }

    private static void passoRitual(ServerLevel l) {
        int t = ++ritualT;
        BlockPos c = ritualC;
        MinecraftServer s = l.getServer();
        Vec3 embriao = new Vec3(c.getX() + 0.5, c.getY() + ALTO, c.getZ() + 0.5);
        int y = c.getY() + 1;
        for (int i = 0; i < RITUAL.size(); i++) {
            Entity a = l.getEntity(RITUAL.get(i));
            if (a == null) continue;
            if (t <= 60) a.setPos(a.getX(), y + 40 * (1 - t / 60.0), a.getZ());
            if (t > 70 && t < 330 && t % 3 == i % 3) {
                int cor = corps(Emocoes.ASPECTOS[i]).color();
                DustParticleOptions d = new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 1.6f);
                Vec3 de = a.position().add(0, 1.5, 0), dir = embriao.subtract(de);
                int n = (int) (dir.length() / 1.2);
                for (int k = 0; k < n; k++) {
                    Vec3 q = de.add(dir.scale((double) k / n));
                    l.sendParticles(d, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0);
                }
            }
        }
        if (t % 20 == 0 && t < 330) l.playSound(null, c, SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 2f, 1.2f + t / 400f);
        if (t == 120) falar(l, c, "necron.lanterna.ritual_2", ChatFormatting.WHITE);
        if (t == 220) falar(l, c, "necron.lanterna.ritual_3", ChatFormatting.WHITE);
        if (t == 330) {
            marcar(s, T_RITUAL + l.getGameTime());
            avisarClientes(s);
            l.playSound(null, c.above(ALTO), SoundEvents.END_PORTAL_SPAWN, SoundSource.AMBIENT, 4f, 1.8f);
            l.playSound(null, c, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 2f, 1f);
            for (ServerPlayer q : l.players()) {
                q.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.translatable("necron.lanterna.fim_titulo").withStyle(ChatFormatting.WHITE)));
                q.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.translatable("necron.lanterna.fim_sub").withStyle(ChatFormatting.GRAY)));
            }
        }
        if (t == 430) falar(l, c, "necron.lanterna.epilogo_1", ChatFormatting.GRAY);
        if (t == 520) falar(l, c, "necron.lanterna.epilogo_2", ChatFormatting.GRAY);
        if (t == 520) for (UUID u : RITUAL) { Entity a = l.getEntity(u); if (a != null) SOBE.add(new Object[]{l.dimension(), u, -5}); }
        if (t == 640) {
            for (ServerPlayer q : s.getPlayerList().getPlayers()) {
                q.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.translatable("necron.lanterna.parallax_titulo").withStyle(ChatFormatting.YELLOW)));
                q.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.translatable("necron.lanterna.parallax_sub").withStyle(ChatFormatting.GOLD)));
                q.sendSystemMessage(Component.translatable("necron.lanterna.parallax_1").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
                q.playNotifySound(SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.AMBIENT, 0.6f, 0.6f);
            }
            marcar(s, FIM);
            for (ServerPlayer q : s.getPlayerList().getPlayers()) {
                LoreData d = MissionManager.data(q);
                if (d != null && d.isActive("necron_9")) { FonteDados.chamado(q, "necron:ritual", 1); ProximaSaga.heroi(q); }
            }
            ritualT = -1;
            LanternaMod.LOGGER.info("Lanterna: fim da saga do Necron (linha do herói)");
        }
    }

    /** Teste: volta o capítulo (as marcas do mundo; o que já foi curado fica curado). */
    public static void zerar(MinecraftServer s) {
        LoreWorldData w = LoreWorldData.get(s);
        for (String k : new ArrayList<>(w.sites()))
            if (k.startsWith(T_DESPERTAR) || k.startsWith(T_RITUAL) || k.startsWith("saga:corr:") || k.startsWith(RESTAURADA)
                    || k.equals(PARTIU) || k.equals(PURIFICADA) || k.equals(FIM)) w.resetSite(k);
        despT = -1;
        ritualT = -1;
        CURAS.clear();
    }

    public static void zerar(ServerPlayer p) {
        for (String k : new String[]{"necron:despertar", "necron:purificou_terra", "necron:ritual", "necron:purificado_violet", "necron:purificado_indigo", "necron:purificado_blue"})
            FonteDados.chamado(p, k, 0);
    }

    private Purificacao() {}
}
