package com.lanterna.lendarios;

import com.lanterna.LanternaMod;
import com.lanterna.cidade.Obra;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.hanks.HanksMundo;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * As arenas dos Lanternas lendários. Na primeira visita ao planeta, a arena é montada no ponto de pouso (um disco de
 * pedra com pilares de luz na cor da Tropa e o trono); o Lanterna lendário aparece quando alguém chega perto e, depois
 * de derrotado, volta em 30 minutos. A arena do Sinestro é a cidadela de Hanks ({@link HanksMundo}).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArenaLendaria {
    public static final int RAIO = 24;
    public static final long VOLTA = 20L * 60 * 30;

    public static final class Estado {
        public boolean construida, construindo, vivo;
        public long volta;
        public BlockPos centro = BlockPos.ZERO;
        int sumido;
        /** 2 = com a arquitetura da 0.47.0 (colunas, arcos, estandartes). */
        int versao = 1;
    }

    public static final class Dados extends SavedData {
        final Map<String, Estado> estados = new HashMap<>();

        static Dados ler(CompoundTag t) {
            Dados d = new Dados();
            for (String k : t.getAllKeys()) {
                CompoundTag e = t.getCompound(k);
                Estado s = new Estado();
                s.construida = e.getBoolean("construida");
                s.vivo = e.getBoolean("vivo");
                s.volta = e.getLong("volta");
                s.centro = BlockPos.of(e.getLong("centro"));
                s.versao = Math.max(1, e.getInt("versao"));
                d.estados.put(k, s);
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            estados.forEach((k, s) -> {
                CompoundTag e = new CompoundTag();
                e.putBoolean("construida", s.construida);
                e.putBoolean("vivo", s.vivo);
                e.putLong("volta", s.volta);
                e.putLong("centro", s.centro.asLong());
                e.putInt("versao", s.versao);
                t.put(k, e);
            });
            return t;
        }
    }

    public static Dados dados(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(Dados::ler, Dados::new, "lanterna_lendarios");
    }

    public static Estado estado(MinecraftServer s, Lendario l) {
        Dados d = dados(s);
        return d.estados.computeIfAbsent(l.id, k -> new Estado());
    }

    public static void marcar(MinecraftServer s) {
        dados(s).setDirty();
    }

    /** Em que planeta lendário o jogador está (ou nenhum). */
    @Nullable
    public static Lendario aqui(ServerPlayer p) {
        if (p.level().dimension().equals(HanksMundo.DIM)) return Lendario.SINESTRO;
        return PlanetasLendarios.lendarioEm(p.level().dimension(), p.getX(), p.getZ());
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 7 || p.isSpectator()) return;
        Lendario l = aqui(p);
        if (l == null) return;
        ServerLevel level = p.serverLevel();
        Estado s = estado(p.server, l);
        if (!s.construida) {
            if (!s.construindo && l != Lendario.SINESTRO) construir(level, l, s);
            return;
        }
        if (s.versao < 2 && l != Lendario.SINESTRO && !s.construindo && p.blockPosition().distSqr(s.centro) < 120 * 120) {
            s.versao = 2;
            marcar(p.server);
            BlockPos c = s.centro;
            Obra.etapa(level, lv -> enfeitar(lv, l, c, true));
        }
        if (p.blockPosition().distSqr(s.centro) > 90 * 90) return;
        LanternaEntity achado = achar(level, l, s.centro);
        if (achado != null) {
            s.sumido = 0;
            if (!s.vivo) { s.vivo = true; marcar(p.server); }
            return;
        }
        // morto: espera a volta; vivo mas não achado (pedaço ainda carregando): espera uns segundos antes de recriar
        if (s.vivo && ++s.sumido < 8) return;
        if (level.getGameTime() < s.volta) {
            if (p.tickCount % 400 == 7) {
                long min = Math.max(1, (s.volta - level.getGameTime()) / 1200);
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable("lendario.lanterna.volta", l.nome(), min)
                        .withStyle(st -> st.withColor(l.corps.color())), true);
            }
            return;
        }
        if (p.blockPosition().distSqr(s.centro) > 64 * 64) return;
        Lendarios.surgir(level, l, s.centro);
        s.vivo = true;
        s.sumido = 0;
        marcar(p.server);
    }

    @Nullable
    public static LanternaEntity achar(ServerLevel level, Lendario l, BlockPos c) {
        List<LanternaEntity> lista = level.getEntitiesOfClass(LanternaEntity.class, new AABB(c).inflate(110),
                x -> l.id.equals(x.getPersistentData().getString(Lendarios.TAG)) && x.isAlive());
        return lista.isEmpty() ? null : lista.get(0);
    }

    // ------------------------------------------------------------------ construção

    private record Paleta(Block chao, Block anel, Block pilar, Block vidro, Block luz) {}

    private static Paleta paleta(Lendario l) {
        return switch (l.corps) {
            case GREEN -> new Paleta(Blocks.POLISHED_DEEPSLATE, Blocks.EMERALD_BLOCK, Blocks.DEEPSLATE_BRICKS, Blocks.LIME_STAINED_GLASS, Blocks.VERDANT_FROGLIGHT);
            case ORANGE -> new Paleta(Blocks.SMOOTH_SANDSTONE, Blocks.GOLD_BLOCK, Blocks.CUT_SANDSTONE, Blocks.ORANGE_STAINED_GLASS, Blocks.SHROOMLIGHT);
            case RED -> new Paleta(Blocks.POLISHED_BLACKSTONE, Blocks.RED_NETHER_BRICKS, Blocks.NETHER_BRICKS, Blocks.RED_STAINED_GLASS, Blocks.MAGMA_BLOCK);
            case BLUE -> new Paleta(Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE, Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.SEA_LANTERN);
            case INDIGO -> new Paleta(Blocks.CALCITE, Blocks.AMETHYST_BLOCK, Blocks.SMOOTH_BASALT, Blocks.PURPLE_STAINED_GLASS, Blocks.PEARLESCENT_FROGLIGHT);
            case VIOLET -> new Paleta(Blocks.PURPUR_BLOCK, Blocks.PINK_CONCRETE, Blocks.PURPUR_PILLAR, Blocks.MAGENTA_STAINED_GLASS, Blocks.PEARLESCENT_FROGLIGHT);
            case BLACK -> new Paleta(Blocks.DEEPSLATE_TILES, Blocks.BLACK_CONCRETE, Blocks.POLISHED_BASALT, Blocks.BLACK_STAINED_GLASS, Blocks.CRYING_OBSIDIAN);
            default -> new Paleta(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.GOLD_BLOCK, Blocks.GILDED_BLACKSTONE, Blocks.YELLOW_STAINED_GLASS, Blocks.OCHRE_FROGLIGHT);
        };
    }

    private static void construir(ServerLevel level, Lendario l, Estado s) {
        s.construindo = true;
        int cx = PlanetasLendarios.regiaoX(l), cz = PlanetasLendarios.REGIAO_BASE;
        List<ChunkPos> pedacos = new ArrayList<>();
        for (int x = -RAIO - 16; x <= RAIO + 16; x += 16) for (int z = -RAIO - 16; z <= RAIO + 16; z += 16)
            pedacos.add(new ChunkPos((cx + x) >> 4, (cz + z) >> 4));
        Obra.carregar(level, pedacos);
        Obra.etapa(level, lv -> {
            BlockPos c = montar(lv, l, cx, cz);
            s.centro = c;
            s.construida = true;
            s.versao = 2;
            s.construindo = false;
            marcar(lv.getServer());
            LanternaMod.LOGGER.info("Lanterna: arena de {} pronta em {}", l.id, c);
        });
    }

    private static void set(ServerLevel l, int x, int y, int z, BlockState b) {
        l.setBlock(new BlockPos(x, y, z), b, 2);
    }

    /** Monta a arena e devolve o centro (no chão). */
    public static BlockPos montar(ServerLevel lv, Lendario l, int cx, int cz) {
        Paleta p = paleta(l);
        // altura: o chão do meio (na água, a arena flutua um pouco acima do mar)
        int y = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        int soma = 0, n = 0;
        for (int dx = -RAIO; dx <= RAIO; dx += 8) for (int dz = -RAIO; dz <= RAIO; dz += 8) {
            soma += lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx + dx, cz + dz);
            n++;
        }
        y = Math.max(y, soma / Math.max(1, n)) + 1;
        y = Math.min(y, lv.getMaxBuildHeight() - 40);
        BlockState chao = p.chao.defaultBlockState(), anel = p.anel.defaultBlockState(), pilar = p.pilar.defaultBlockState();
        BlockState vidro = p.vidro.defaultBlockState(), luz = p.luz.defaultBlockState(), ar = Blocks.AIR.defaultBlockState();
        for (int x = -RAIO - 2; x <= RAIO + 2; x++) for (int z = -RAIO - 2; z <= RAIO + 2; z++) {
            double r = Math.sqrt(x * x + z * z);
            if (r > RAIO + 2.5) continue;
            int wx = cx + x, wz = cz + z;
            int topo = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
            // limpa até 26 blocos acima (montanhas, árvores) e enche embaixo até o chão (no máximo 14 blocos)
            for (int yy = y; yy <= Math.max(topo, y) + 26; yy++) set(lv, wx, yy, wz, ar);
            if (r > RAIO + 0.5) continue;
            for (int yy = y - 2; yy >= Math.max(topo - 1, y - 14); yy--) set(lv, wx, yy, wz, pilar);
            boolean faixa = Math.abs(r - 12) < 0.6 || Math.abs(r - RAIO + 1) < 0.7 || (r < 5 && Math.abs(r - 4) < 0.6);
            set(lv, wx, y - 1, wz, faixa ? anel : chao);
            if (r < 1.5) set(lv, wx, y - 1, wz, luz);
        }
        // trono ao norte: degraus, assento e um farol alto atrás
        int tz = cz - (RAIO - 6);
        for (int dx = -3; dx <= 3; dx++) for (int dz = -2; dz <= 2; dz++) {
            int alt = dz <= -1 ? 2 : dz == 0 ? 1 : 0;
            for (int k = 0; k < alt; k++) set(lv, cx + dx, y + k, tz + dz, Math.abs(dx) == 3 ? anel : chao);
        }
        for (int k = 0; k < 16; k++) set(lv, cx, y + 2 + k, tz - 3, k % 4 == 3 ? luz : vidro);
        set(lv, cx, y + 18, tz - 3, luz);
        BlockPos c = new BlockPos(cx, y, cz);
        enfeitar(lv, l, c, false);
        return c;
    }

    /**
     * Arquitetura da arena (0.47.0): 10 colunas com capitel ligadas por arcos, estandartes da Tropa, braseiros, dois
     * obeliscos ao lado do trono e um piso desenhado no meio. Em mundos antigos, troca os pilares de luz por isto.
     */
    static void enfeitar(ServerLevel lv, Lendario l, BlockPos c, boolean antigo) {
        int cx = c.getX(), cz = c.getZ(), y = c.getY();
        BlockState ar = Blocks.AIR.defaultBlockState();
        if (antigo) for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10;
            int px = cx + (int) Math.round(Math.cos(a) * (RAIO - 3)), pz = cz + (int) Math.round(Math.sin(a) * (RAIO - 3));
            for (int k = 0; k < 12; k++) set(lv, px, y + k, pz, ar);
        }
        com.lanterna.lore.world.Arquitetura.Estilo e = com.lanterna.lore.world.Arquitetura.estilo(l.corps);
        com.lanterna.lore.world.Arquitetura.piso(lv, c, y, 10, e);
        BlockPos[] topos = new BlockPos[10];
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10 + Math.PI / 10;
            int px = cx + (int) Math.round(Math.cos(a) * (RAIO - 4)), pz = cz + (int) Math.round(Math.sin(a) * (RAIO - 4));
            int topo = com.lanterna.lore.world.Arquitetura.coluna(lv, new BlockPos(px, y, pz), 10 + (i % 2) * 2, e);
            topos[i] = new BlockPos(px, topo, pz);
            net.minecraft.core.Direction dentro = com.lanterna.lore.world.Arquitetura.rumo(cx - px, cz - pz);
            com.lanterna.lore.world.Arquitetura.estandarte(lv, new BlockPos(px, y + 7, pz).relative(dentro), dentro, e.cor());
        }
        for (int i = 0; i < 10; i++) com.lanterna.lore.world.Arquitetura.arco(lv, topos[i], topos[(i + 1) % 10], 4, e);
        for (int[] d : new int[][]{{13, 0}, {-13, 0}, {0, 13}, {9, 9}, {-9, 9}})
            com.lanterna.lore.world.Arquitetura.braseiro(lv, new BlockPos(cx + d[0], y, cz + d[1]), e);
        int tz = cz - (RAIO - 6);
        com.lanterna.lore.world.Arquitetura.obelisco(lv, new BlockPos(cx - 6, y, tz), 7, e);
        com.lanterna.lore.world.Arquitetura.obelisco(lv, new BlockPos(cx + 6, y, tz), 7, e);
    }

    private ArenaLendaria() {}
}
