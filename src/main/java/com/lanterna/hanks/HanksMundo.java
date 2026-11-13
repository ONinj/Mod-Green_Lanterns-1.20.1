package com.lanterna.hanks;

import com.lanterna.LanternaMod;
import com.lanterna.cidade.Obra;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.lendarios.ArenaLendaria;
import com.lanterna.lendarios.Lendario;
import com.lanterna.lore.Factions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Hanks, o planeta-máquina que a Tropa Amarela construiu: um mundo todo de placas de metal escuro, com veios de luz
 * amarela. Na primeira visita é montado o distrito do pouso:
 *  pouso      a plataforma de chegada (centro do mundo);
 *  grade      veios de luz amarela no chão em volta (como as linhas de um circuito), torres e respiros;
 *  núcleos    três núcleos de energia (missão "Os Três Núcleos");
 *  cidadela   a arena do Sinestro, ao norte, com o trono e uma agulha de luz.
 * Ninguém respira em Hanks: só com o anel ligado. Quem é de Tropa inimiga dos Amarelos é atacado pelas sentinelas.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HanksMundo {
    public static final ResourceKey<Level> DIM = ResourceKey.create(Registries.DIMENSION, LanternaMod.id("hanks"));
    public static final int RAIO_DISTRITO = 150;
    public static final BlockPos[] NUCLEOS_XZ = {new BlockPos(95, 0, 25), new BlockPos(-95, 0, 25), new BlockPos(0, 0, 115)};
    public static final int CIDADELA_Z = -100;

    public static final class Dados extends SavedData {
        public boolean comecou, pronto;
        public int y;

        static Dados ler(CompoundTag t) {
            Dados d = new Dados();
            d.comecou = t.getBoolean("comecou");
            d.pronto = t.getBoolean("pronto");
            d.y = t.getInt("y");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            t.putBoolean("comecou", comecou);
            t.putBoolean("pronto", pronto);
            t.putInt("y", y);
            return t;
        }
    }

    public static Dados dados(ServerLevel l) {
        return l.getDataStorage().computeIfAbsent(Dados::ler, Dados::new, "lanterna_hanks");
    }

    public static BlockPos nucleo(Dados d, int i) {
        return new BlockPos(NUCLEOS_XZ[i].getX(), d.y, NUCLEOS_XZ[i].getZ());
    }

    @SubscribeEvent
    public static void entrou(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!e.getTo().equals(DIM) || !(e.getEntity() instanceof ServerPlayer p)) return;
        garantir(p.serverLevel());
        HanksMissoes.pousou(p);
    }

    public static void garantir(ServerLevel l) {
        if (!l.dimension().equals(DIM)) return;
        Dados d = dados(l);
        if (d.comecou) return;
        d.comecou = true;
        d.setDirty();
        List<ChunkPos> pedacos = new ArrayList<>();
        for (int x = -RAIO_DISTRITO; x <= RAIO_DISTRITO; x += 16) for (int z = -RAIO_DISTRITO; z <= RAIO_DISTRITO; z += 16)
            pedacos.add(new ChunkPos(x >> 4, z >> 4));
        Obra.carregar(l, pedacos);
        Obra.etapa(l, lv -> {
            d.y = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
            d.setDirty();
        });
        // a grade em faixas (para não travar o servidor)
        for (int x0 = -RAIO_DISTRITO; x0 <= RAIO_DISTRITO; x0 += 20) {
            int a = x0;
            Obra.etapa(l, lv -> grade(lv, d, a, Math.min(a + 19, RAIO_DISTRITO)));
        }
        Obra.etapa(l, lv -> torres(lv, d));
        Obra.etapa(l, lv -> pouso(lv, d));
        Obra.etapa(l, lv -> { for (int i = 0; i < 3; i++) nucleoBase(lv, nucleo(d, i)); });
        Obra.etapa(l, lv -> {
            BlockPos c = cidadela(lv, d);
            var s = ArenaLendaria.estado(lv.getServer(), Lendario.SINESTRO);
            s.centro = c;
            s.construida = true;
            ArenaLendaria.marcar(lv.getServer());
            d.pronto = true;
            d.setDirty();
            LanternaMod.LOGGER.info("Lanterna: Hanks pronto (cidadela em {})", c);
        });
    }

    private static void set(ServerLevel l, int x, int y, int z, BlockState s) {
        l.setBlock(new BlockPos(x, y, z), s, 2);
    }

    /** Veios de luz amarela no chão (linhas de circuito a cada 12 blocos, com nós) e placas mais escuras. */
    private static void grade(ServerLevel l, Dados d, int x0, int x1) {
        int y = d.y - 1;
        BlockState luz = Blocks.OCHRE_FROGLIGHT.defaultBlockState(), placa = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
                borda = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        for (int x = x0; x <= x1; x++) for (int z = -RAIO_DISTRITO; z <= RAIO_DISTRITO; z++) {
            if (x * x + z * z > RAIO_DISTRITO * RAIO_DISTRITO) continue;
            boolean lx = Math.floorMod(x, 12) == 0, lz = Math.floorMod(z, 12) == 0;
            long h = (x * 73856093L) ^ (z * 19349663L);
            if (lx || lz) {
                // falhas na linha, como um circuito
                boolean liga = ((h >>> 5) & 7) != 0;
                set(l, x, y, z, liga ? luz : borda);
            } else if (Math.floorMod(x, 12) == 6 && Math.floorMod(z, 12) == 6) {
                set(l, x, y, z, borda);
            } else if (((h >>> 9) & 15) == 0) {
                set(l, x, y, z, placa);
            }
        }
    }

    /** Torres e respiros espalhados (fora do caminho do pouso, dos núcleos e da cidadela). */
    private static void torres(ServerLevel l, Dados d) {
        RandomSource r = RandomSource.create(0x4A4E4B53L);
        BlockState tijolo = Blocks.DEEPSLATE_BRICKS.defaultBlockState(), vidro = Blocks.YELLOW_STAINED_GLASS.defaultBlockState(),
                luz = Blocks.OCHRE_FROGLIGHT.defaultBlockState(), ferro = Blocks.IRON_BARS.defaultBlockState(),
                cobre = Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState();
        int feitas = 0;
        for (int tent = 0; tent < 200 && feitas < 16; tent++) {
            int x = r.nextInt(2 * RAIO_DISTRITO - 30) - RAIO_DISTRITO + 15, z = r.nextInt(2 * RAIO_DISTRITO - 30) - RAIO_DISTRITO + 15;
            if (x * x + z * z > (RAIO_DISTRITO - 10) * (RAIO_DISTRITO - 10) || x * x + z * z < 30 * 30) continue;
            if (Math.abs(x) < 34 && z < -60) continue;                       // cidadela
            boolean perto = false;
            for (BlockPos n : NUCLEOS_XZ) if (Math.abs(n.getX() - x) < 14 && Math.abs(n.getZ() - z) < 14) perto = true;
            if (perto) continue;
            int y = d.y;
            if (r.nextInt(3) == 0) {
                // respiro: buraco com luz no fundo e grade por cima
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    for (int k = 1; k <= 4; k++) set(l, x + dx, y - k, z + dz, Blocks.AIR.defaultBlockState());
                    set(l, x + dx, y - 5, z + dz, luz);
                    set(l, x + dx, y - 1, z + dz, ferro);
                }
            } else {
                int h = 10 + r.nextInt(22);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int k = 0; k < h; k++) {
                    boolean canto = Math.abs(dx) == 1 && Math.abs(dz) == 1;
                    BlockState b = canto ? cobre : (k % 5 == 3 ? vidro : tijolo);
                    if (!canto && dx != 0 && dz != 0) b = tijolo;
                    if (dx == 0 && dz == 0) b = k % 5 == 3 ? luz : tijolo;
                    set(l, x + dx, y + k, z + dz, b);
                }
                set(l, x, y + h, z, luz);
                set(l, x, y + h + 1, z, Blocks.LIGHTNING_ROD.defaultBlockState());
            }
            feitas++;
        }
    }

    private static void pouso(ServerLevel l, Dados d) {
        int y = d.y;
        BlockState piso = Blocks.POLISHED_BLACKSTONE.defaultBlockState(), luz = Blocks.OCHRE_FROGLIGHT.defaultBlockState(),
                ouro = Blocks.GOLD_BLOCK.defaultBlockState();
        for (int x = -11; x <= 11; x++) for (int z = -11; z <= 11; z++) {
            double r = Math.sqrt(x * x + z * z);
            if (r > 11.5) continue;
            for (int k = 0; k < 12; k++) set(l, x, y + k, z, Blocks.AIR.defaultBlockState());
            set(l, x, y - 1, z, r > 10 ? luz : (Math.abs(r - 6) < 0.6 ? ouro : piso));
        }
    }

    /** Base de um núcleo: pedestal com o núcleo (luz amarela) no meio, apagado até alguém ligar. */
    private static void nucleoBase(ServerLevel l, BlockPos c) {
        BlockState piso = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), vidro = Blocks.YELLOW_STAINED_GLASS.defaultBlockState(),
                ouro = Blocks.GILDED_BLACKSTONE.defaultBlockState();
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            for (int k = 0; k < 8; k++) set(l, c.getX() + x, c.getY() + k, c.getZ() + z, Blocks.AIR.defaultBlockState());
            set(l, c.getX() + x, c.getY() - 1, c.getZ() + z, Math.abs(x) == 4 || Math.abs(z) == 4 ? ouro : piso);
        }
        for (int[] q : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) for (int k = 0; k < 4; k++)
            set(l, c.getX() + q[0], c.getY() + k, c.getZ() + q[1], k == 3 ? Blocks.OCHRE_FROGLIGHT.defaultBlockState() : ouro);
        for (int k = 0; k < 3; k++) set(l, c.getX(), c.getY() + k, c.getZ(), vidro);
        acender(l, c, false);
    }

    /** Liga (ou apaga) o miolo do núcleo. */
    public static void acender(ServerLevel l, BlockPos c, boolean aceso) {
        set(l, c.getX(), c.getY() + 1, c.getZ(), aceso ? Blocks.OCHRE_FROGLIGHT.defaultBlockState() : Blocks.BLACK_STAINED_GLASS.defaultBlockState());
        set(l, c.getX(), c.getY() + 3, c.getZ(), aceso ? Blocks.LIGHTNING_ROD.defaultBlockState() : Blocks.AIR.defaultBlockState());
    }

    /** A cidadela do Sinestro: arena com pilares dourados, trono e uma agulha alta de luz. Devolve o centro. */
    private static BlockPos cidadela(ServerLevel l, Dados d) {
        int cz = CIDADELA_Z, y = d.y;
        BlockPos c = ArenaLendaria.montar(l, Lendario.SINESTRO, 0, cz);
        // muralha baixa com vãos
        BlockState muro = Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), luz = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
        for (int i = 0; i < 72; i++) {
            double a = Math.PI * 2 * i / 72;
            if (i % 12 < 2) continue;
            int x = (int) Math.round(Math.cos(a) * (ArenaLendaria.RAIO + 1)), z = cz + (int) Math.round(Math.sin(a) * (ArenaLendaria.RAIO + 1));
            set(l, x, c.getY(), z, muro);
            if (i % 6 == 0) set(l, x, c.getY() + 1, z, luz);
        }
        // caminho do pouso até a cidadela
        for (int z = cz + ArenaLendaria.RAIO; z <= -12; z++) for (int x = -2; x <= 2; x++)
            set(l, x, y - 1, z, Math.abs(x) == 2 ? luz : Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        return c;
    }

    // ===================== Sentinelas: Hanks se defende =====================

    @SubscribeEvent
    public static void sentinelas(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % (20 * 40) != 11) return;
        if (!p.level().dimension().equals(DIM) || p.isCreative() || p.isSpectator()) return;
        BuiltinCorps c = Factions.playerCorps(p);
        if (c == null || !Factions.enemies(BuiltinCorps.YELLOW, c)) return;
        ServerLevel l = p.serverLevel();
        int perto = l.getEntitiesOfClass(LanternaEntity.class, p.getBoundingBox().inflate(48),
                x -> x.tropa() == BuiltinCorps.YELLOW && x.getPersistentData().getBoolean("lanterna_sentinela")).size();
        if (perto >= 3) return;
        for (int i = 0; i < 2; i++) {
            LanternaEntity s = LanternaEntity.lanterna(l, BuiltinCorps.YELLOW, i == 0);
            double a = p.getRandom().nextDouble() * Math.PI * 2;
            s.moveTo(p.getX() + Math.cos(a) * 18, p.getY() + 6, p.getZ() + Math.sin(a) * 18, 0, 0);
            s.getPersistentData().putBoolean("lanterna_encontro", true);
            s.getPersistentData().putBoolean("lanterna_sentinela", true);
            l.addFreshEntity(s);
            s.setTarget(p);
        }
        p.displayClientMessage(Component.translatable("hanks.lanterna.sentinelas").withStyle(st -> st.withColor(0xFFC830)), true);
    }

    private HanksMundo() {}
}
