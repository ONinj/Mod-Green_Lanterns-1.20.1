package com.lanterna.mogo;

import com.lanterna.LanternaMod;
import com.lanterna.cidade.Kit;
import com.lanterna.cidade.Obra;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * O mundo do Mogo. Na primeira visita são montados os lugares das missões (todos perto do pouso):
 *  pouso       clareira com o Coração de Jade, de onde o Mogo fala;
 *  faixa       a faixa de jade atravessa o planeta (z de -12 a 12) e vai sendo pintada conforme os pedaços carregam;
 *  ponte       o cânion com a ponte que desaba (O Chão que se Move);
 *  arena       o platô da Prova do Mogo;
 *  torres      três torres de jade na faixa (O Cerco);
 *  fenda       o poço até o núcleo e a câmara da Ferida (O Mergulho).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MogoMundo {
    public static final ResourceKey<Level> DIM = ResourceKey.create(Registries.DIMENSION, LanternaMod.id("mogo"));
    public static final int FAIXA = 12, FAIXA_X = 420;

    public static class Dados extends SavedData {
        public boolean comecou, pronto;
        public int y0;
        public BlockPos pouso = BlockPos.ZERO, coracao = BlockPos.ZERO, ponteIni = BlockPos.ZERO, ponteFim = BlockPos.ZERO, arena = BlockPos.ZERO,
                fenda = BlockPos.ZERO, camara = BlockPos.ZERO;
        public int ponteY, ponteZ0, ponteZ1;
        public final BlockPos[] torres = new BlockPos[3];
        final Set<Long> pintados = new HashSet<>();

        static Dados load(CompoundTag t) {
            Dados d = new Dados();
            d.comecou = t.getBoolean("comecou");
            d.pronto = t.getBoolean("pronto");
            d.y0 = t.getInt("y0");
            d.pouso = NbtUtils.readBlockPos(t.getCompound("pouso"));
            d.coracao = NbtUtils.readBlockPos(t.getCompound("coracao"));
            d.ponteIni = NbtUtils.readBlockPos(t.getCompound("ponteIni"));
            d.ponteFim = NbtUtils.readBlockPos(t.getCompound("ponteFim"));
            d.arena = NbtUtils.readBlockPos(t.getCompound("arena"));
            d.fenda = NbtUtils.readBlockPos(t.getCompound("fenda"));
            d.camara = NbtUtils.readBlockPos(t.getCompound("camara"));
            d.ponteY = t.getInt("ponteY");
            d.ponteZ0 = t.getInt("ponteZ0");
            d.ponteZ1 = t.getInt("ponteZ1");
            for (int i = 0; i < 3; i++) d.torres[i] = t.contains("torre" + i) ? NbtUtils.readBlockPos(t.getCompound("torre" + i)) : BlockPos.ZERO;
            for (var e : t.getList("pintados", 4)) d.pintados.add(((LongTag) e).getAsLong());
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            t.putBoolean("comecou", comecou);
            t.putBoolean("pronto", pronto);
            t.putInt("y0", y0);
            t.put("pouso", NbtUtils.writeBlockPos(pouso));
            t.put("coracao", NbtUtils.writeBlockPos(coracao));
            t.put("ponteIni", NbtUtils.writeBlockPos(ponteIni));
            t.put("ponteFim", NbtUtils.writeBlockPos(ponteFim));
            t.put("arena", NbtUtils.writeBlockPos(arena));
            t.put("fenda", NbtUtils.writeBlockPos(fenda));
            t.put("camara", NbtUtils.writeBlockPos(camara));
            t.putInt("ponteY", ponteY);
            t.putInt("ponteZ0", ponteZ0);
            t.putInt("ponteZ1", ponteZ1);
            for (int i = 0; i < 3; i++) if (torres[i] != null) t.put("torre" + i, NbtUtils.writeBlockPos(torres[i]));
            ListTag l = new ListTag();
            for (long v : pintados) l.add(LongTag.valueOf(v));
            t.put("pintados", l);
            return t;
        }
    }

    @Nullable
    public static ServerLevel nivel(MinecraftServer s) {
        return s.getLevel(DIM);
    }

    public static Dados dados(ServerLevel mogo) {
        return mogo.getDataStorage().computeIfAbsent(Dados::load, Dados::new, "lanterna_mogo");
    }

    @Nullable
    public static Dados dados(MinecraftServer s) {
        ServerLevel l = nivel(s);
        return l == null ? null : dados(l);
    }

    public static boolean pronto(MinecraftServer s) {
        Dados d = dados(s);
        return d != null && d.pronto;
    }

    // ===================== Primeira visita =====================

    @SubscribeEvent
    public static void entrou(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!e.getTo().equals(DIM) || !(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)) return;
        garantir(p.serverLevel());
        MogoMissoes.pousou(p);
    }

    /** Monta os lugares do Mogo (uma vez por mundo). */
    public static void garantir(ServerLevel l) {
        if (!l.dimension().equals(DIM)) return;
        Dados d = dados(l);
        if (d.comecou) return;
        d.comecou = true;
        d.setDirty();
        List<ChunkPos> pedacos = new ArrayList<>();
        for (int x = -32; x <= 64; x += 16) for (int z = -128; z <= 160; z += 16) pedacos.add(new ChunkPos(x >> 4, z >> 4));
        for (int x = -224; x <= -48; x += 16) pedacos.add(new ChunkPos(x >> 4, 0));
        Obra.carregar(l, pedacos);
        Obra.etapa(l, lv -> pouso(lv, d));
        Obra.etapa(l, lv -> ponte(lv, d));
        Obra.etapa(l, lv -> arena(lv, d));
        Obra.etapa(l, lv -> torres(lv, d));
        Obra.etapa(l, lv -> fenda(lv, d));
        Obra.etapa(l, lv -> {
            d.pronto = true;
            d.setDirty();
            LanternaMod.LOGGER.info("Lanterna: Mogo pronto (pouso em {})", d.pouso);
        });
    }

    private static int sup(ServerLevel l, int x, int z) {
        return Kit.altura(l, x, z);
    }

    private static void set(ServerLevel l, int x, int y, int z, BlockState s) {
        l.setBlock(new BlockPos(x, y, z), s, 2);
    }

    /** Aplaina um disco: chão na altura y, ar em cima, terra embaixo. */
    private static void aplainar(ServerLevel l, int cx, int cz, int r, int y, BlockState chao, int ar) {
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            if (x * x + z * z > r * r) continue;
            int top = sup(l, cx + x, cz + z);
            for (int yy = Math.min(top, y) - 4; yy < y; yy++) if (yy < y - 1) set(l, cx + x, yy, cz + z, Blocks.DIRT.defaultBlockState());
            set(l, cx + x, y - 1, cz + z, chao);
            for (int yy = y; yy <= Math.max(top, y) + ar; yy++) set(l, cx + x, yy, cz + z, Blocks.AIR.defaultBlockState());
        }
    }

    private static void pouso(ServerLevel l, Dados d) {
        int y = sup(l, 0, 0);
        d.y0 = y;
        aplainar(l, 0, 0, 16, y, Blocks.MOSS_BLOCK.defaultBlockState(), 14);
        BlockState jade = MogoBlocos.JADE.get().defaultBlockState();
        for (int x = -14; x <= 14; x++) for (int z = -14; z <= 14; z++) {
            double r = Math.sqrt(x * x + z * z);
            if (r >= 11.5 && r < 13) set(l, x, y - 1, z, jade);
        }
        // o Coração de Jade: uma agulha de jade de onde a voz do Mogo vem
        for (int k = 0; k < 7; k++) {
            int w = k < 2 ? 1 : 0;
            for (int dx = -w; dx <= w; dx++) for (int dz = -w; dz <= w; dz++) set(l, dx, y + k, -9 + dz, jade);
        }
        d.pouso = new BlockPos(0, y, 0);
        d.coracao = new BlockPos(0, y + 7, -9);
        d.setDirty();
    }

    private static void ponte(ServerLevel l, Dados d) {
        int z0 = 52, z1 = 128, xc = -12;
        int yb = Math.max(sup(l, xc, z0 - 4), sup(l, xc, z1 + 4)) + 2;
        for (int z = z0; z <= z1; z += 8) yb = Math.max(yb, sup(l, xc, z) + 2);
        d.ponteY = yb;
        d.ponteZ0 = z0;
        d.ponteZ1 = z1;
        BlockState pedra = Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), jade = MogoBlocos.JADE.get().defaultBlockState();
        // plataformas das pontas
        for (int x = -18; x <= -6; x++) for (int z = z0 - 9; z < z0; z++) plataforma(l, x, yb, z, pedra);
        for (int x = -18; x <= -6; x++) for (int z = z1 + 1; z <= z1 + 9; z++) plataforma(l, x, yb, z, pedra);
        // o cânion: um corte fundo embaixo da ponte
        for (int x = -20; x <= -4; x++) for (int z = z0; z <= z1; z++) {
            for (int y = yb + 12; y >= yb - 26; y--) set(l, x, y, z, Blocks.AIR.defaultBlockState());
            set(l, x, yb - 27, z, Blocks.DEEPSLATE.defaultBlockState());
            if (x == -20 || x == -4) for (int y = yb - 26; y <= yb + 1; y++) set(l, x, y, z, Blocks.STONE.defaultBlockState());
        }
        // a ponte
        for (int z = z0; z <= z1; z++) for (int x = xc - 1; x <= xc + 1; x++) set(l, x, yb - 1, z, (z % 9 == 0) ? jade : pedra);
        d.ponteIni = new BlockPos(xc, yb, z0 - 4);
        d.ponteFim = new BlockPos(xc, yb, z1 + 5);
        for (int k = 0; k < 4; k++) set(l, xc, yb + k, z1 + 8, jade);
        d.setDirty();
    }

    private static void plataforma(ServerLevel l, int x, int y, int z, BlockState s) {
        int top = sup(l, x, z);
        for (int yy = Math.min(top, y) - 3; yy < y - 1; yy++) set(l, x, yy, z, Blocks.STONE.defaultBlockState());
        set(l, x, y - 1, z, s);
        for (int yy = y; yy <= Math.max(top, y) + 6; yy++) set(l, x, yy, z, Blocks.AIR.defaultBlockState());
    }

    private static void arena(ServerLevel l, Dados d) {
        int cx = 0, cz = -100;
        int y = sup(l, cx, cz);
        aplainar(l, cx, cz, 24, y, Blocks.POLISHED_ANDESITE.defaultBlockState(), 24);
        for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) {
            double r = Math.sqrt(x * x + z * z);
            if (r > 24) continue;
            if ((int) r % 6 == 0) set(l, cx + x, y - 1, cz + z, Blocks.MOSS_BLOCK.defaultBlockState());
            if (r >= 23) for (int k = 0; k < 2; k++) set(l, cx + x, y + k, cz + z, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        }
        d.arena = new BlockPos(cx, y, cz);
        d.setDirty();
    }

    private static void torres(ServerLevel l, Dados d) {
        BlockState pedra = Blocks.STONE_BRICKS.defaultBlockState(), jade = MogoBlocos.JADE.get().defaultBlockState();
        for (int i = 0; i < 3; i++) {
            int x = -70 - i * 60;
            int y = sup(l, x, 0);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                int top = sup(l, x + dx, dz);
                for (int yy = Math.min(top, y) - 3; yy < y; yy++) set(l, x + dx, yy, dz, pedra);
                boolean canto = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                for (int k = 0; k < 8; k++) set(l, x + dx, y + k, dz, canto || k % 4 == 3 ? jade : (Math.abs(dx) == 2 || Math.abs(dz) == 2 ? pedra : Blocks.AIR.defaultBlockState()));
                set(l, x + dx, y + 8, dz, pedra);
            }
            set(l, x, y + 9, 0, MogoBlocos.NUCLEO_TORRE.get().defaultBlockState());
            d.torres[i] = new BlockPos(x, y + 9, 0);
        }
        d.setDirty();
    }

    private static void fenda(ServerLevel l, Dados d) {
        int cx = 40, cz = 100;
        int top = sup(l, cx, cz);
        int fundo = l.getMinBuildHeight() + 20;
        BlockState jade = MogoBlocos.JADE.get().defaultBlockState();
        RandomSource r = l.random;
        for (int y = top + 2; y > fundo + 14; y--) {
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                double rr = Math.sqrt(x * x + z * z);
                if (rr < 3.6) set(l, cx + x, y, cz + z, Blocks.AIR.defaultBlockState());
                else if (rr < 4.6 && y < top - 2) set(l, cx + x, y, cz + z, (y % 7 == 0 || r.nextInt(9) == 0) ? jade : Blocks.DEEPSLATE.defaultBlockState());
            }
        }
        // borda de jade em volta da boca
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            double rr = Math.sqrt(x * x + z * z);
            if (rr >= 4.6 && rr < 6) set(l, cx + x, sup(l, cx + x, cz + z) - 1, cz + z, jade);
        }
        // a câmara do núcleo
        int cy = fundo + 7;
        for (int x = -15; x <= 15; x++) for (int z = -15; z <= 15; z++) for (int y = -8; y <= 9; y++) {
            double e = (x * x + z * z) / 225.0 + (y * y) / 90.0;
            BlockPos p = new BlockPos(cx + x, cy + y, cz + z);
            if (e < 1) l.setBlock(p, y == -8 ? Blocks.DEEPSLATE_TILES.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            else if (e < 1.25) l.setBlock(p, r.nextInt(5) == 0 ? jade : Blocks.DEEPSLATE.defaultBlockState(), 2);
        }
        for (int x = -15; x <= 15; x++) for (int z = -15; z <= 15; z++)
            if (x * x + z * z < 200) l.setBlock(new BlockPos(cx + x, cy - 8, cz + z), (x * x + z * z) % 23 == 0 ? jade : Blocks.DEEPSLATE_TILES.defaultBlockState(), 2);
        d.fenda = new BlockPos(cx, top, cz);
        d.camara = new BlockPos(cx, cy - 7, cz);
        d.setDirty();
    }

    // ===================== A faixa de jade =====================

    private static final Deque<Long> FILA = new java.util.concurrent.ConcurrentLinkedDeque<>();

    @SubscribeEvent
    public static void carregou(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel l) || !l.dimension().equals(DIM)) return;
        ChunkPos c = e.getChunk().getPos();
        if (c.getMaxBlockZ() < -FAIXA || c.getMinBlockZ() > FAIXA || c.getMaxBlockX() < -FAIXA_X || c.getMinBlockX() > FAIXA_X) return;
        FILA.add(c.toLong());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || FILA.isEmpty()) return;
        ServerLevel l = nivel(e.getServer());
        if (l == null) {
            FILA.clear();
            return;
        }
        Dados d = dados(l);
        for (int n = 0; n < 2 && !FILA.isEmpty(); n++) {
            long k = FILA.poll();
            if (d.pintados.contains(k)) continue;
            ChunkPos c = new ChunkPos(k);
            if (l.getChunkSource().getChunkNow(c.x, c.z) == null) continue;
            pintar(l, c);
            d.pintados.add(k);
            d.setDirty();
        }
    }

    /** Pinta a faixa num pedaço: chão de jade e musgo, com agulhas de jade aqui e ali. */
    private static void pintar(ServerLevel l, ChunkPos c) {
        BlockState jade = MogoBlocos.JADE.get().defaultBlockState(), musgo = Blocks.MOSS_BLOCK.defaultBlockState();
        RandomSource r = RandomSource.create(c.toLong() * 31 + 7);
        for (int x = c.getMinBlockX(); x <= c.getMaxBlockX(); x++) for (int z = Math.max(-FAIXA, c.getMinBlockZ()); z <= Math.min(FAIXA, c.getMaxBlockZ()); z++) {
            if (Math.abs(x) > FAIXA_X) continue;
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos p = new BlockPos(x, y, z);
            BlockState em = l.getBlockState(p);
            if (em.isAir() || !em.getFluidState().isEmpty() || em.is(MogoBlocos.NUCLEO_TORRE.get())) continue;
            int borda = FAIXA - Math.abs(z);
            boolean veio = (Math.abs(z) <= 2) || (borda > 2 && r.nextInt(4) == 0) || (borda <= 2 && r.nextInt(9) == 0);
            l.setBlock(p, veio ? jade : musgo, 2);
            if (borda > 3 && r.nextInt(70) == 0) {
                int h = 3 + r.nextInt(6);
                for (int k = 1; k <= h; k++) l.setBlock(p.above(k), jade, 2);
            }
        }
    }

    private MogoMundo() {}
}
