package com.lanterna.fonte;

import com.lanterna.LanternaMod;
import com.lanterna.cidade.Obra;
import com.lanterna.lore.LoreEntities;
import com.lanterna.lore.LoreWorldData;
import com.lanterna.lore.entity.boss.ChefeFonte;
import com.lanterna.lore.entity.boss.LoreBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * O que existe dentro de cada Fonte, em volta da plataforma de chegada (0, 100, 0):
 *
 *   Arquivo da Tropa   (oeste)  um púlpito com o livro de memórias dos Lanternas antigos
 *   Forja de Construtos (leste)  alvos de treino para testar os construtos da Árvore da Luz
 *   Salão do Conselho  (norte)  bancos em roda e o quadro de contratos da Tropa
 *   Ilha do Coração    (sul, no fim da ponte)  o altar onde o Coração da Emoção (o chefe) desperta
 *
 * É construído na primeira vez que alguém entra naquela Fonte.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FonteMundo {
    public static final BlockPos ILHA = new BlockPos(0, 100, 140);
    /** Nós da Árvore da Luz para o Coração despertar. */
    public static final int NOS_PARA_CHEFE = 8;

    private record Cores(Block vidro, Block luz, Block piso, Block borda) {}

    private static final Map<String, Cores> CORES = Map.of(
            "verde", new Cores(Blocks.LIME_STAINED_GLASS, Blocks.VERDANT_FROGLIGHT, Blocks.LIME_CONCRETE, Blocks.GREEN_CONCRETE),
            "amarelo", new Cores(Blocks.YELLOW_STAINED_GLASS, Blocks.OCHRE_FROGLIGHT, Blocks.YELLOW_CONCRETE, Blocks.BLACK_CONCRETE),
            "vermelho", new Cores(Blocks.RED_STAINED_GLASS, Blocks.SHROOMLIGHT, Blocks.RED_CONCRETE, Blocks.BLACK_CONCRETE),
            "laranja", new Cores(Blocks.ORANGE_STAINED_GLASS, Blocks.OCHRE_FROGLIGHT, Blocks.ORANGE_CONCRETE, Blocks.GOLD_BLOCK),
            "azul", new Cores(Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.SEA_LANTERN, Blocks.LIGHT_BLUE_CONCRETE, Blocks.WHITE_CONCRETE),
            "indigo", new Cores(Blocks.PURPLE_STAINED_GLASS, Blocks.PEARLESCENT_FROGLIGHT, Blocks.PURPLE_CONCRETE, Blocks.BLUE_CONCRETE),
            "violeta", new Cores(Blocks.MAGENTA_STAINED_GLASS, Blocks.PEARLESCENT_FROGLIGHT, Blocks.MAGENTA_CONCRETE, Blocks.PINK_CONCRETE));

    public static final Map<String, String> CHEFE = Map.of("verde", "colosso_de_jade", "amarelo", "mae_das_sombras", "vermelho", "serpente_escarlate",
            "laranja", "rei_faminto", "azul", "arcanjo_da_aurora", "indigo", "cervo_sereno", "violeta", "rainha_de_cristal");

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 40 != 7) return;
        String tropa = Fonte.fonteDe(p.level());
        if (tropa == null || Obra.ocupada()) return;
        var data = LoreWorldData.get(p.server);
        String site = "fonte_" + tropa + ":salas";
        if (data.siteBuilt(site)) return;
        data.markSite(site);
        construir(p.serverLevel(), tropa);
    }

    private static void s(ServerLevel l, BlockPos p, BlockState st) { l.setBlock(p, st, 2); }
    private static BlockState b(Block x) { return x.defaultBlockState(); }

    private static void disco(ServerLevel l, BlockPos c, double r, Block piso, Block borda) {
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) for (int dz = -ri; dz <= ri; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > r) continue;
            s(l, c.offset(dx, 0, dz), b(d > r - 1.2 ? borda : ((dx + dz) & 1) == 0 ? piso : borda));
            s(l, c.offset(dx, -1, dz), b(Blocks.BLACK_CONCRETE));
        }
    }

    private static void ponte(ServerLevel l, BlockPos a, BlockPos z, Cores c) {
        int n = (int) Math.max(Math.abs(z.getX() - a.getX()), Math.abs(z.getZ() - a.getZ()));
        Direction lado = Math.abs(z.getX() - a.getX()) > Math.abs(z.getZ() - a.getZ()) ? Direction.SOUTH : Direction.EAST;
        for (int i = 0; i <= n; i++) {
            BlockPos p = new BlockPos(a.getX() + (z.getX() - a.getX()) * i / Math.max(1, n), a.getY(), a.getZ() + (z.getZ() - a.getZ()) * i / Math.max(1, n));
            for (int w = -1; w <= 1; w++) s(l, p.relative(lado, w), b(w == 0 ? c.piso() : c.vidro()));
            if (i % 8 == 4) { s(l, p.relative(lado, 2).above(), b(c.luz())); s(l, p.relative(lado, -2).above(), b(c.luz())); s(l, p.relative(lado, 2), b(c.borda())); s(l, p.relative(lado, -2), b(c.borda())); }
        }
    }

    private static void construir(ServerLevel level, String tropa) {
        Cores c = CORES.getOrDefault(tropa, CORES.get("verde"));
        java.util.List<net.minecraft.world.level.ChunkPos> pedacos = new java.util.ArrayList<>();
        for (int cx = -3; cx <= 2; cx++) for (int cz = -3; cz <= 10; cz++) pedacos.add(new net.minecraft.world.level.ChunkPos(cx, cz));
        Obra.carregar(level, pedacos);
        // Arquivo (oeste)
        Obra.etapa(level, l -> {
            BlockPos a = new BlockPos(-24, 100, 0);
            disco(l, a, 7, c.piso(), c.borda());
            for (int i = 0; i < 6; i++) {
                double ang = i * Math.PI / 3;
                BlockPos p = a.offset((int) Math.round(Math.cos(ang) * 6), 1, (int) Math.round(Math.sin(ang) * 6));
                for (int y = 0; y < 5; y++) s(l, p.above(y), b(y == 4 ? c.luz() : c.vidro()));
            }
            BlockPos lec = a.above();
            s(l, lec, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.EAST));
            if (l.getBlockEntity(lec) instanceof LecternBlockEntity be) {
                LecternBlock.tryPlaceBook(null, l, lec, l.getBlockState(lec), livro(tropa));
            }
            ponte(l, new BlockPos(-7, 100, 0), new BlockPos(-17, 100, 0), c);
        });
        // Forja de Construtos (leste)
        Obra.etapa(level, l -> {
            BlockPos f = new BlockPos(24, 100, 0);
            disco(l, f, 8, c.piso(), c.borda());
            for (int i = 0; i < 4; i++) {
                double ang = i * Math.PI / 2 + Math.PI / 4;
                var alvo = LoreEntities.ALVO_TREINO.get().create(l);
                if (alvo == null) continue;
                alvo.moveTo(f.getX() + Math.cos(ang) * 4.5 + 0.5, 101, f.getZ() + Math.sin(ang) * 4.5 + 0.5, 0, 0);
                alvo.setPersistenceRequired();
                l.addFreshEntity(alvo);
            }
            for (int i = 0; i < 16; i++) {
                double ang = i * Math.PI / 8;
                s(l, f.offset((int) Math.round(Math.cos(ang) * 8), 1, (int) Math.round(Math.sin(ang) * 8)), b(i % 4 == 0 ? c.luz() : c.vidro()));
            }
            ponte(l, new BlockPos(7, 100, 0), new BlockPos(16, 100, 0), c);
        });
        // Salão do Conselho (norte)
        Obra.etapa(level, l -> {
            BlockPos h = new BlockPos(0, 100, -28);
            disco(l, h, 10, c.piso(), c.borda());
            for (int i = 0; i < 20; i++) {
                double ang = i * Math.PI / 10;
                BlockPos p = h.offset((int) Math.round(Math.cos(ang) * 6.5), 1, (int) Math.round(Math.sin(ang) * 6.5));
                if (Math.abs(p.getX() - h.getX()) <= 1 && p.getZ() > h.getZ()) continue;   // entrada pela ponte
                Direction olha = Math.abs(Math.cos(ang)) > Math.abs(Math.sin(ang)) ? (Math.cos(ang) > 0 ? Direction.WEST : Direction.EAST) : (Math.sin(ang) > 0 ? Direction.NORTH : Direction.SOUTH);
                s(l, p, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, olha.getOpposite()));
            }
            for (int y = 1; y <= 4; y++) s(l, h.above(y), b(y == 4 ? c.luz() : c.vidro()));
            var kit = new com.lanterna.cidade.Kit(l, com.lanterna.cidade.Paleta.of(com.lanterna.lore.PlanetLore.OA));
            for (int dx = -1; dx <= 1; dx++) for (int y = 1; y <= 3; y++) s(l, h.offset(dx, y, -9), b(c.borda()));
            kit.quadro(h.offset(1, 1, -8), Direction.SOUTH);
            ponte(l, new BlockPos(0, 100, -7), new BlockPos(0, 100, -18), c);
        });
        // Ponte longa e a Ilha do Coração (sul)
        Obra.etapa(level, l -> ponte(l, new BlockPos(0, 100, 7), new BlockPos(0, 100, ILHA.getZ() - 25), c));
        Obra.etapa(level, l -> {
            disco(l, ILHA, 26, c.piso(), c.borda());
            for (int i = 0; i < 8; i++) {
                double ang = i * Math.PI / 4 + Math.PI / 8;
                BlockPos p = ILHA.offset((int) Math.round(Math.cos(ang) * 22), 1, (int) Math.round(Math.sin(ang) * 22));
                for (int y = 0; y < 9; y++) s(l, p.above(y), b(y == 8 ? c.luz() : y % 3 == 2 ? c.borda() : c.vidro()));
            }
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) s(l, ILHA.offset(dx, 0, dz), b(Blocks.IRON_BLOCK));
            s(l, ILHA.above(), b(Blocks.BEACON));
            s(l, ILHA.above(2), b(c.vidro()));
        });
        Obra.etapa(level, l -> LanternaMod.LOGGER.info("Lanterna: salas da Fonte {} prontas", tropa));
    }

    /** O livro do Arquivo: memórias dos Lanternas antigos daquela Tropa (texto traduzido no cliente). */
    private static ItemStack livro(String tropa) {
        ItemStack s = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag t = s.getOrCreateTag();
        // o título e o autor de um livro escrito não se traduzem: o nome que aparece vem de display.Name (traduzido)
        t.putString("title", "Arquivo");
        t.putString("author", "Oa");
        CompoundTag display = new CompoundTag();
        display.putString("Name", "{\"translate\":\"item.lanterna.arquivo_fonte\",\"italic\":false}");
        t.put("display", display);
        ListTag pages = new ListTag();
        for (int i = 1; i <= 5; i++) pages.add(StringTag.valueOf("{\"translate\":\"arquivo.lanterna." + tropa + "." + i + "\"}"));
        t.put("pages", pages);
        t.putBoolean("resolved", true);
        return s;
    }

    /** O altar da Ilha: desperta o Coração da Emoção. */
    /** Hora (game time) em que o Coração de cada Fonte caiu: ele leva 20 minutos para despertar de novo. */
    public static final java.util.Map<String, Long> DERROTA = new java.util.concurrent.ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onAltar(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        String tropa = Fonte.fonteDe(p.level());
        if (tropa == null || !e.getPos().equals(ILHA.above())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        ServerLevel l = p.serverLevel();
        String chefe = CHEFE.get(tropa);
        if (!l.getEntitiesOfClass(ChefeFonte.class, new net.minecraft.world.phys.AABB(ILHA).inflate(80)).isEmpty()) {
            p.displayClientMessage(Component.translatable("chefe.lanterna.ja_acordado"), true);
            return;
        }
        Long morte = DERROTA.get(tropa);
        if (morte != null && l.getGameTime() - morte < 24000 && !p.isCreative()) {
            p.displayClientMessage(Component.translatable("chefe.lanterna.descansa", (int) Math.ceil((24000 - (l.getGameTime() - morte)) / 1200.0)), true);
            return;
        }
        long nos = FonteDados.arvore(p).stream().filter(x -> {
            for (var t : com.lanterna.baterias.Tropa.values()) if (t.nome.equals(tropa)) return x.startsWith(t.corps().id().getPath() + ":");
            return false;
        }).count();
        if (nos < NOS_PARA_CHEFE && !p.isCreative()) {
            p.displayClientMessage(Component.translatable("chefe.lanterna.dorme", NOS_PARA_CHEFE, nos), true);
            return;
        }
        var tipo = LoreEntities.bossType(chefe);
        if (tipo == null) return;
        var ent = tipo.create(l);
        if (!(ent instanceof ChefeFonte boss)) return;
        boss.moveTo(ILHA.getX() + 0.5, ILHA.getY() + 1, ILHA.getZ() + 10.5, 180, 0);
        boss.setHome(ILHA);
        boss.finalizeSpawn(l, l.getCurrentDifficultyAt(ILHA), MobSpawnType.EVENT, null, null);
        l.addFreshEntity(boss);
        l.playSound(null, ILHA, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2f, 0.7f);
        for (ServerPlayer o : l.players()) o.sendSystemMessage(Component.translatable("chefe.lanterna.desperta", boss.getDisplayName()).withStyle(st -> st.withColor(boss.cfg().cor()).withBold(true)));
    }

    private FonteMundo() {}
}
