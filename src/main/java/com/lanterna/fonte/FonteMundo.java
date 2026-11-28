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
        if (!data.siteBuilt(site)) {
            data.markSite(site);
            construir(p.serverLevel(), tropa);
            return;
        }
        // 0.60.3: o Salão das Memórias (o Arquivo refeito, com o Altar dos Desafios) e os cristais em volta
        String salao = "fonte_" + tropa + ":memorias";
        if (!data.siteBuilt(salao)) {
            data.markSite(salao);
            construirMemorias(p.serverLevel(), tropa);
            return;
        }
        // 0.60.4: a prisão da entidade (ao norte, depois do Salão do Conselho)
        String prisao = "fonte_" + tropa + ":prisao2";
        if (!data.siteBuilt(prisao)) {
            data.markSite(prisao);
            construirPrisao(p.serverLevel(), tropa);
        }
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

    // ===================== 0.60.3: o Salão das Memórias =====================

    private static void circulo(ServerLevel l, BlockPos c, double r, int y, BlockState st) {
        int n = (int) Math.ceil(r * 8);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            s(l, new BlockPos(c.getX() + (int) Math.round(Math.cos(a) * r), y, c.getZ() + (int) Math.round(Math.sin(a) * r)), st);
        }
    }

    /** Uma estátua de um Lanterna lendário (de frente para o centro do salão). */
    private static void estatua(ServerLevel l, BlockPos base, Cores c, int nivel) {
        int alto = nivel == 3 ? 2 : 1;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = 1; y <= alto; y++)
            s(l, base.offset(dx, y, dz), b(Math.abs(dx) + Math.abs(dz) == 2 ? c.borda() : Blocks.POLISHED_DEEPSLATE));
        BlockPos p = base.above(alto + 1);
        s(l, p, b(c.vidro())); s(l, p.above(), b(c.vidro()));                      // pernas
        s(l, p.above(2), b(c.borda())); s(l, p.above(3), b(c.borda()));            // tronco
        for (Direction d : new Direction[]{Direction.EAST, Direction.WEST}) s(l, p.above(3).relative(d), b(c.vidro()));   // braços
        s(l, p.above(4), b(c.luz()));                                              // cabeça de luz
        if (nivel == 3) for (Direction d : Direction.Plane.HORIZONTAL) s(l, p.above(5).relative(d), b(c.vidro()));   // auréola do lendário
        // o anel brilhando na mão
        s(l, p.above(3).relative(Direction.EAST).above(), b(Blocks.END_ROD));
    }

    private static void construirMemorias(ServerLevel level, String tropa) {
        Cores c = CORES.getOrDefault(tropa, CORES.get("verde"));
        BlockPos m = Desafios.SALAO;
        java.util.List<net.minecraft.world.level.ChunkPos> pedacos = new java.util.ArrayList<>();
        for (int cx = -4; cx <= 0; cx++) for (int cz = -2; cz <= 1; cz++) pedacos.add(new net.minecraft.world.level.ChunkPos(cx, cz));
        Obra.carregar(level, pedacos);
        // limpa o Arquivo antigo e a área do salão
        Obra.etapa(level, l -> {
            for (int dx = -17; dx <= 17; dx++) for (int dz = -17; dz <= 17; dz++) for (int y = 90; y <= 118; y++)
                if (dx * dx + dz * dz <= 17 * 17) s(l, m.offset(dx, y - m.getY(), dz), b(Blocks.AIR));
            for (int x = -17; x <= -6; x++) for (int dz = -2; dz <= 2; dz++) for (int y = 100; y <= 106; y++) s(l, new BlockPos(x, y, dz), b(Blocks.AIR));
        });
        // o piso flutuante: anéis, o anel de luz embutido, o centro brilhando e a base em cone por baixo
        Obra.etapa(level, l -> {
            for (int dx = -14; dx <= 14; dx++) for (int dz = -14; dz <= 14; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 14) continue;
                Block piso = d > 13 ? c.borda() : (d > 9 && d < 10.2) ? c.luz() : d < 2.2 ? c.vidro() : ((int) d) % 3 == 0 ? c.borda() : c.piso();
                s(l, m.offset(dx, 0, dz), b(piso));
                if (d < 2.2) s(l, m.offset(dx, -1, dz), b(c.luz()));
                for (int k = 1; k <= 7; k++) if (d <= 14 - k * 1.9) s(l, m.offset(dx, -k, dz), b(k == 7 ? c.luz() : Blocks.BLACK_CONCRETE));
            }
        });
        // colunas, arcos e a borda do teto
        Obra.etapa(level, l -> {
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6 + Math.PI / 12;
                BlockPos p = m.offset((int) Math.round(Math.cos(a) * 13), 1, (int) Math.round(Math.sin(a) * 13));
                if (p.getX() > m.getX() + 9 && Math.abs(p.getZ()) <= 3) continue;   // a entrada, pela ponte
                for (int y = 0; y < 8; y++) s(l, p.above(y), b(y % 3 == 2 ? c.borda() : c.vidro()));
                s(l, p.above(8), b(c.luz()));
            }
            circulo(l, m, 13, m.getY() + 9, b(c.borda()));
            circulo(l, m, 12, m.getY() + 10, b(c.vidro()));
            circulo(l, m, 10.5, m.getY() + 11, b(c.vidro()));
        });
        // as quatro estátuas das memórias e o Altar dos Desafios com o livro
        Obra.etapa(level, l -> {
            double[] ang = {Math.PI * 0.75, Math.PI * 1.25, Math.PI * 1.65, Math.PI * 0.35};
            for (int i = 0; i < 4; i++) estatua(l, m.offset((int) Math.round(Math.cos(ang[i]) * 9.5), 0, (int) Math.round(Math.sin(ang[i]) * 9.5)), c, i);
            BlockPos al = Desafios.ALTAR;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) s(l, al.offset(dx, -1, dz), b(Blocks.CRYING_OBSIDIAN));
            s(l, al, b(Blocks.LODESTONE));
            s(l, al.above(), b(c.luz()));
            s(l, al.offset(-2, 0, 0), b(c.borda())); s(l, al.offset(2, 0, 0), b(c.borda()));
            s(l, al.offset(-2, 1, 0), b(Blocks.END_ROD)); s(l, al.offset(2, 1, 0), b(Blocks.END_ROD));
            BlockPos lec = al.offset(0, 0, 2).east(3);
            s(l, lec, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.NORTH));
            if (l.getBlockEntity(lec) instanceof LecternBlockEntity) LecternBlock.tryPlaceBook(null, l, lec, l.getBlockState(lec), livro(tropa));
            ponte(l, new BlockPos(-7, 100, 0), new BlockPos(m.getX() + 13, 100, 0), c);
        });
        // cristais flutuando em volta da Fonte
        Obra.etapa(level, l -> {
            var r = new java.util.Random(tropa.hashCode());
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI * 2 / 16 + r.nextDouble() * 0.3;
                double dist = 48 + r.nextDouble() * 24;
                BlockPos p = new BlockPos((int) (Math.cos(a) * dist), 104 + r.nextInt(26), (int) (Math.sin(a) * dist) + 40);
                int h = 3 + r.nextInt(5);
                for (int y = -h; y <= h; y++) {
                    int w = Math.max(0, (h - Math.abs(y)) / 2);
                    for (int dx = -w; dx <= w; dx++) for (int dz = -w; dz <= w; dz++)
                        if (Math.abs(dx) + Math.abs(dz) <= w) s(l, p.offset(dx, y, dz), b(Math.abs(y) == h ? c.luz() : c.vidro()));
                }
            }
        });
        Obra.etapa(level, l -> LanternaMod.LOGGER.info("Lanterna: Salão das Memórias da Fonte {} pronto", tropa));
    }

    // ===================== 0.60.4: a prisão da entidade (como a cela da Kurama) =====================

    private static void construirPrisao(ServerLevel level, String tropa) {
        Cores c = CORES.getOrDefault(tropa, CORES.get("verde"));
        int gz = Prisao.GRADES_Z, L = Prisao.LARGURA;
        java.util.List<net.minecraft.world.level.ChunkPos> pedacos = new java.util.ArrayList<>();
        for (int cx = -3; cx <= 2; cx++) for (int cz = -9; cz <= -2; cz++) pedacos.add(new net.minecraft.world.level.ChunkPos(cx, cz));
        Obra.carregar(level, pedacos);
        // o chão: água rasa e escura por toda a sala (antessala e cela), com as bordas de pedra
        Obra.etapa(level, l -> {
            for (int x = -L - 2; x <= L + 2; x++) for (int z = -124; z <= -42; z++) {
                boolean borda = Math.abs(x) > L || z < -122;
                s(l, new BlockPos(x, 98, z), b(Blocks.BLACK_CONCRETE));
                s(l, new BlockPos(x, 99, z), b(borda ? c.borda() : Blocks.BLACK_CONCRETE));
                s(l, new BlockPos(x, 100, z), borda ? b(c.borda()) : z > gz + 1 && Math.abs(x) <= 3 ? b(c.piso()) : b(Blocks.WATER));
            }
        });
        // as paredes da cela e da antessala, altas e escuras, com fendas de luz da cor da Tropa
        Obra.etapa(level, l -> {
            for (int y = 101; y <= 144; y++) {
                for (int z = -124; z <= -42; z++) for (int sx : new int[]{-L - 2, L + 2}) s(l, new BlockPos(sx, y, z), b(y % 9 == 0 && z % 6 == 0 ? c.luz() : Blocks.BLACKSTONE));
                for (int x = -L - 2; x <= L + 2; x++) s(l, new BlockPos(x, y, -124), b(y % 9 == 0 && x % 6 == 0 ? c.luz() : Blocks.BLACKSTONE));
            }
        });
        // as grades gigantes (do chão até 42 blocos) e as traves
        Obra.etapa(level, l -> {
            for (int x = -L; x <= L; x++) {
                boolean barra = Math.floorMod(x, 4) == 0;
                for (int y = 100; y <= 142; y++) {
                    if (barra) { s(l, new BlockPos(x, y, gz), b(c.borda())); if (Math.abs(x) <= 4) s(l, new BlockPos(x, y, gz - 1), b(c.borda())); }
                    else if (y == 100 || y == 121 || y == 142) s(l, new BlockPos(x, y, gz), b(Blocks.POLISHED_BLACKSTONE));
                }
            }
        });
        // o selo: um papel grande atravessado no portão do meio, com o símbolo da Tropa
        Obra.etapa(level, l -> {
            int z = gz + 1;
            // 0.60.4: mais baixo, para não tapar o rosto da entidade quando ela encosta nas grades
            int y0 = 101;
            for (int x = -3; x <= 3; x++) for (int y = 101; y <= 130; y++) s(l, new BlockPos(x, y, z), b(Blocks.AIR));   // o selo antigo (mais alto)
            for (int x = -2; x <= 2; x++) for (int y = y0; y <= y0 + 10; y++) s(l, new BlockPos(x, y, z), b(Blocks.WHITE_WOOL));
            // os traços do selo (as letras do lacre)
            int[][] tracos = {{0, 9}, {0, 8}, {-1, 7}, {0, 7}, {1, 7}, {0, 5}, {-1, 4}, {1, 4}, {-2, 3}, {2, 3}, {0, 2}, {0, 1}};
            for (int[] t : tracos) s(l, new BlockPos(t[0], y0 + t[1], z), b(Blocks.BLACK_WOOL));
            s(l, new BlockPos(0, y0 + 5, z), b(c.luz()));
        });
        // a antessala: a ponte que vem do Salão do Conselho e tochas da cor da Tropa
        Obra.etapa(level, l -> {
            ponte(l, new BlockPos(0, 100, -38), new BlockPos(0, 100, -43), c);
            for (int z = -56; z <= -44; z += 6) for (int sx : new int[]{-4, 4}) {
                s(l, new BlockPos(sx, 100, z), b(c.borda()));
                s(l, new BlockPos(sx, 101, z), b(Blocks.SOUL_LANTERN));
            }
        });
        Obra.etapa(level, l -> LanternaMod.LOGGER.info("Lanterna: a prisão da Fonte {} pronta", tropa));
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
