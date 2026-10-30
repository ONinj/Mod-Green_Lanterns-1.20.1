package com.lanterna.cidade;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.lore.Factions;
import com.lanterna.lore.LoreEntities;
import com.lanterna.lore.LoreItems;
import com.lanterna.lore.LoreWorldData;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.LoreCapability;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.Mission;
import com.lanterna.mission.MissionManager;
import com.lanterna.mission.Objective;
import com.lanterna.mission.Objective.Type;
import com.lanterna.network.ModNetwork;
import com.lanterna.space.SpaceBodies;
import com.lanterna.tropa.Contratos;
import com.lanterna.tropa.Nivel;
import com.lanterna.tropa.QuadroPacket;
import com.lanterna.tropa.TropaItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Missões dos NPCs da cidade e do Palácio dos Guardiões.
 *
 *  cid_<planeta>_<npc>_<1..3>   cadeia de 3 missões de cada NPC (história da cidade)
 *  cid_<planeta>_<npc>_d<dia>   missão diária do NPC (cidadãos só têm diárias: pedidos)
 *  pal_chamado / pal_conselho / pal_honra   linha do Palácio (a partir do nível 30)
 *  prov_<técnica>               provação de um Capitão (ensina uma técnica)
 *
 * Tudo é montado a partir do id (igual no cliente e no servidor). Os alvos (piratas, Ecos, fugitivos...) aparecem
 * quando o jogador chega perto do lugar da missão; se faltarem vivos, chegam reforços.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CidadeMissoes {
    public static final int MAX_ATIVAS = 4;
    private static final List<String> CADEIAS = List.of("comandante", "engenheiro", "medico", "arquivista", "instrutor", "carcereiro");
    public static final List<String> TECNICAS = List.of("olhar", "bastiao", "onda", "salto", "armadura", "comando");
    private static final Map<String, Mission> CACHE = new HashMap<>();

    public static boolean eDaCidade(String id) {
        return id.startsWith("cid_") || id.startsWith("pal_") || id.startsWith("prov_");
    }

    public static boolean daMissoes(String funcao) {
        return CADEIAS.contains(funcao) || funcao.startsWith("cidadao") || funcao.startsWith("capitao_");
    }

    // ===================== Lugares (iguais no cliente e no servidor) =====================

    private static Random rnd(String planet, long salt) {
        return new Random(planet.hashCode() * 1_000_003L + salt * 7919L);
    }

    /** Um lugar no planeta a "dist" blocos da cidade (acampamentos, ninhos, esconderijos). */
    static BlockPos lugar(String planet, long salt, int dist) {
        Random r = rnd(planet, salt);
        double a = r.nextDouble() * Math.PI * 2;
        int d = dist + r.nextInt(80);
        return Cidade.CENTRO.offset((int) (Math.cos(a) * d), 0, (int) (Math.sin(a) * d));
    }

    static BlockPos hangar() { return Cidade.CENTRO.offset(0, 0, 40); }
    static BlockPos arena() { return Cidade.CENTRO.offset(-38, 0, 14); }

    // ===================== Montagem das missões =====================

    @Nullable
    public static Mission mission(String id) {
        Mission c = CACHE.get(id);
        if (c != null) return c;
        Mission m = montar(id);
        if (m != null) CACHE.put(id, m);
        return m;
    }

    private record Partes(String planet, String npc, int passo, long dia) {}

    @Nullable
    private static Partes partes(String id) {
        if (!id.startsWith("cid_")) return null;
        String[] s = id.split("_");
        if (s.length < 4) return null;
        String planet = s[1];
        String npc = s.length == 5 ? s[2] + "_" + s[3] : s[2];
        String last = s[s.length - 1];
        try {
            if (last.startsWith("d")) return new Partes(planet, npc, 0, Long.parseLong(last.substring(1)));
            return new Partes(planet, npc, Integer.parseInt(last), -1);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Mission nova(String id, String linha, int cor, List<Objective> obj, int xp, ItemStack... premios) {
        List<ItemStack> l = new ArrayList<>();
        for (ItemStack s : premios) if (!s.isEmpty()) l.add(s);
        return new Mission(id, linha, cor, obj, () -> {
            List<ItemStack> out = new ArrayList<>();
            for (ItemStack s : l) out.add(s.copy());
            return out;
        }, xp, null, null);
    }

    private static ItemStack merito(int n) {
        return new ItemStack(LoreItems.MERITO.get(), n);
    }

    @Nullable
    private static Mission montar(String id) {
        if (id.startsWith("pal_") || id.startsWith("prov_")) return palacio(id);
        Partes p = partes(id);
        if (p == null) return null;
        PlanetLore pl = PlanetLore.byPlanet(p.planet);
        if (pl == null) return null;
        String P = p.planet;
        int cor = pl.color();
        List<Objective> o = new ArrayList<>();
        if (p.dia >= 0) {
            // ---- diárias ----
            switch (p.npc) {
                case "engenheiro" -> o.add(Objective.of(Type.COLLECT, "lanterna:fragmento_casco", 4, null, null));
                case "medico" -> o.add(Objective.of(Type.COLLECT, "minecraft:golden_carrot", 4, null, null));
                case "arquivista" -> o.add(Objective.of(Type.COLLECT, "minecraft:book", 3, null, null));
                case "instrutor" -> o.add(Objective.of(Type.CONSTRUCT, "any", 15, null, null));
                case "carcereiro" -> o.add(Objective.of(Type.KILL, "alvo:fugitivo", 1, P, lugar(P, 900 + p.dia, 140)));
                case "comandante" -> o.add(Objective.of(Type.KILL, "papel:pirata", 5, P, lugar(P, 500 + p.dia, 180)));
                default -> {
                    // cidadãos: um pedido simples por dia
                    String[][] pedidos = {{"minecraft:bread", "6"}, {"minecraft:cooked_beef", "6"}, {"minecraft:iron_ingot", "6"},
                            {"minecraft:white_wool", "8"}, {"minecraft:oak_log", "16"}, {"minecraft:glass", "8"}, {"minecraft:torch", "16"},
                            {"minecraft:apple", "6"}, {"minecraft:coal", "12"}, {"minecraft:sugar_cane", "12"}};
                    int k = (int) Math.floorMod(p.dia * 3 + p.npc.hashCode(), pedidos.length);
                    o.add(Objective.of(Type.COLLECT, pedidos[k][0], Integer.parseInt(pedidos[k][1]), null, null));
                }
            }
            boolean cidadao = p.npc.startsWith("cidadao");
            return nova(id, "cidade", cor, o, cidadao ? 80 : 140, merito(cidadao ? 1 : 2), new ItemStack(Items.EMERALD, 3));
        }
        // ---- cadeias ----
        int n = p.passo;
        switch (p.npc) {
            case "comandante" -> {
                if (n == 1) { o.add(Objective.of(Type.TALK, "engenheiro:" + P, 1, P, hangar())); o.add(Objective.of(Type.KILL, "alvo:sabotador", 1, P, hangar())); }
                if (n == 2) o.add(Objective.of(Type.KILL, "papel:pirata", 6, P, lugar(P, 1, 200)));
                if (n == 3) { o.add(Objective.of(Type.KILL, "papel:capitao", 1, P, lugar(P, 2, 260))); o.add(Objective.of(Type.KILL, "papel:pirata", 3, P, lugar(P, 2, 260))); }
            }
            case "engenheiro" -> {
                if (n == 1) o.add(Objective.of(Type.COLLECT, "lanterna:fragmento_casco", 6, null, null));
                if (n == 2) o.add(Objective.of(Type.COLLECT, "lanterna:nucleo_propulsor", 1, null, null));
                if (n == 3) for (int i = 0; i < 3; i++) o.add(Objective.of(Type.PONTO, "anel:" + i, 1, P, aneisCidade().get(i)));
            }
            case "medico" -> {
                if (n == 1) o.add(Objective.of(Type.KILL, "lanterna:eco_cinzento", 8, P, lugar(P, 3, 150)));
                if (n == 2) o.add(Objective.of(Type.COLLECT, "minecraft:glistering_melon_slice", 3, null, null));
                if (n == 3) o.add(Objective.of(Type.TALK, "resgate:" + P, 1, P, lugar(P, 4, 220)));
            }
            case "arquivista" -> {
                if (n == 1) for (String outro : outros(P)) o.add(Objective.of(Type.GOTO, outro, 1, outro, null));
                if (n == 2) o.add(Objective.of(Type.DESCOBRIR, "novo", 1, null, null));
                if (n == 3) for (BlockPos s : setores(P)) o.add(Objective.of(Type.SETOR, "setor:" + s.getX(), 1, "space", s));
            }
            case "instrutor" -> {
                if (n == 1) o.add(Objective.of(Type.CONSTRUCT, "any", 30, null, null));
                if (n == 2) o.add(Objective.of(Type.KILL, "alvo:duelista", 1, P, arena()));
                if (n == 3) o.add(Objective.of(Type.KILL, "papel:pirata", 10, P, arena()));
            }
            case "carcereiro" -> {
                if (n == 1) o.add(Objective.of(Type.KILL, "alvo:fugitivo", 1, P, lugar(P, 5, 180)));
                if (n == 2) o.add(Objective.of(Type.KILL, "papel:pirata", 6, P, lugar(P, 6, 240)));
                if (n == 3) o.add(Objective.of(Type.KILL, "papel:capitao", 1, P, lugar(P, 7, 300)));
            }
            default -> { return null; }
        }
        if (o.isEmpty()) return null;
        int xp = n == 1 ? 260 : n == 2 ? 420 : 750;
        return nova(id, "cidade", cor, o, xp, merito(n == 1 ? 3 : n == 2 ? 5 : 8), new ItemStack(Items.EMERALD, 4 + 4 * n),
                n == 3 ? new ItemStack(TropaItems.NUCLEO_PROPULSOR.get()) : ItemStack.EMPTY);
    }

    /** Anéis do teste de voo do Engenheiro: altura relativa ao chão (y = metros acima do chão). */
    static List<BlockPos> aneisCidade() {
        return List.of(Cidade.CENTRO.offset(40, 30, -60), Cidade.CENTRO.offset(-70, 40, -20), Cidade.CENTRO.offset(0, 50, 90));
    }

    private static List<String> outros(String planet) {
        List<String> all = new ArrayList<>();
        for (PlanetLore p : PlanetLore.values()) if (p != PlanetLore.GENESE && !p.planet.equals(planet)) all.add(p.planet);
        Random r = rnd(planet, 77);
        Collections.shuffle(all, r);
        return all.subList(0, 2);
    }

    private static List<BlockPos> setores(String planet) {
        SpaceBodies.Body home = SpaceBodies.byId(planet);
        net.minecraft.world.phys.Vec3 c = home != null ? home.center() : net.minecraft.world.phys.Vec3.ZERO;
        Random r = rnd(planet, 99);
        List<BlockPos> l = new ArrayList<>();
        double a = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < 2; i++) {
            a += 1 + r.nextDouble();
            double d = 1800 + r.nextInt(1500);
            l.add(new BlockPos((int) (c.x + Math.cos(a) * d), (int) c.y, (int) (c.z + Math.sin(a) * d)));
        }
        return l;
    }

    // ---- Palácio ----

    @Nullable
    private static Mission palacio(String id) {
        int cor = 0x46E66E;
        List<Objective> o = new ArrayList<>();
        switch (id) {
            case "pal_chamado" -> {
                o.add(Objective.of(Type.TALK, "conselho_varo:oa", 1, "oa", Palacio.SALAO));
                Mission m = new Mission(id, "palacio", cor, o, () -> List.of(merito(5)), 500, "pal_conselho", null);
                return m;
            }
            case "pal_conselho" -> {
                o.add(Objective.of(Type.TALK, "conselho_selun:oa", 1, "oa", Palacio.BIBLIOTECA));
                o.add(Objective.of(Type.TALK, "capitao_comando:oa", 1, "oa", Palacio.ala("comando")));
                return nova(id, "palacio", cor, o, 600, merito(8));
            }
            case "pal_honra" -> {
                o.add(Objective.of(Type.TALK, "conselho_miraeth:oa", 1, "oa", Palacio.SALAO));
                return nova(id, "palacio", cor, o, 2000, merito(32), new ItemStack(TropaItems.insignia(4)));
            }
            default -> {}
        }
        String tec = id.substring("prov_".length());
        BlockPos campo = Palacio.CAMPO;
        switch (tec) {
            case "olhar" -> o.add(Objective.of(Type.COLLECT, "lanterna:reliquia", 3, "oa", Palacio.SALAO));
            case "bastiao" -> o.add(Objective.of(Type.KILL, "lanterna:eco_cinzento", 12, "oa", campo));
            case "onda" -> o.add(Objective.of(Type.KILL, "papel:pirata", 12, "oa", campo));
            case "salto" -> { for (int i = 0; i < Palacio.ANEIS.size(); i++) o.add(Objective.of(Type.PONTO, "anel:" + i, 1, "oa", Palacio.ANEIS.get(i))); }
            case "armadura" -> o.add(Objective.of(Type.KILL, "alvo:brakhul", 1, "oa", campo));
            case "comando" -> o.add(Objective.of(Type.KILL, "papel:inimigo", 6, "oa", campo));
            default -> { return null; }
        }
        return nova(id, "provacao", cor, o, 1500, merito(10));
    }

    // ===================== Textos =====================

    public static Component titulo(String id) {
        if (id.startsWith("pal_") || id.startsWith("prov_")) return Component.translatable("cidade.lanterna.missao." + id + ".title");
        Partes p = partes(id);
        if (p == null) return Component.literal(id);
        Component planeta = Component.translatable("planet.lanterna." + p.planet);
        if (p.dia >= 0) {
            String npc = p.npc.startsWith("cidadao") ? "cidadao" : p.npc;
            return Component.translatable("cidade.lanterna.missao." + npc + ".d.title", planeta);
        }
        return Component.translatable("cidade.lanterna.missao." + p.npc + "." + p.passo + ".title", planeta);
    }

    public static Component descricao(String id) {
        if (id.startsWith("pal_") || id.startsWith("prov_")) return Component.translatable("cidade.lanterna.missao." + id + ".desc");
        Partes p = partes(id);
        if (p == null) return Component.empty();
        Component planeta = Component.translatable("planet.lanterna." + p.planet);
        Mission m = mission(id);
        Component o0 = m != null && !m.objectives().isEmpty() ? m.objectives().get(0).label(0) : Component.empty();
        if (p.dia >= 0) {
            String npc = p.npc.startsWith("cidadao") ? "cidadao" : p.npc;
            return Component.translatable("cidade.lanterna.missao." + npc + ".d.desc", planeta, o0);
        }
        return Component.translatable("cidade.lanterna.missao." + p.npc + "." + p.passo + ".desc", planeta, o0);
    }

    // ===================== Painel do NPC =====================

    private static long dia(ServerPlayer p) {
        return p.server.overworld().getDayTime() / 24000L;
    }

    /** Requisito que falta para uma missão (ou null se pode). */
    @Nullable
    public static Component falta(ServerPlayer p, LoreData d, String id) {
        int nivel = Nivel.nivel(d.xp());
        Partes pt = partes(id);
        if (pt != null && pt.dia < 0) {
            if (pt.passo > 1 && !d.isDone("cid_" + pt.planet + "_" + pt.npc + "_" + (pt.passo - 1)))
                return Component.translatable("cidade.lanterna.falta.anterior");
            int req = pt.passo == 1 ? 1 : pt.passo == 2 ? 8 : 15;
            if (nivel < req) return Component.translatable("cidade.lanterna.falta.nivel", req);
            return null;
        }
        if (id.equals("pal_honra")) {
            if (!d.isDone("pal_conselho")) return Component.translatable("cidade.lanterna.falta.conselho");
            if (nivel < 40) return Component.translatable("cidade.lanterna.falta.nivel", 40);
            long provas = TECNICAS.stream().filter(t -> d.isDone("prov_" + t)).count();
            if (provas < 2) return Component.translatable("cidade.lanterna.falta.provas", 2);
            return null;
        }
        if (id.startsWith("prov_")) {
            if (!d.isDone("pal_conselho")) return Component.translatable("cidade.lanterna.falta.conselho");
            String t = id.substring(5);
            int req = switch (t) { case "olhar" -> 30; case "bastiao" -> 32; case "onda" -> 35; case "salto" -> 38; case "armadura" -> 42; default -> 45; };
            if (nivel < req) return Component.translatable("cidade.lanterna.falta.nivel", req);
            int bosses = d.bossKills().size();
            switch (t) {
                case "olhar" -> { if (bosses < 2) return Component.translatable("cidade.lanterna.falta.bosses", 2); }
                case "bastiao" -> { if (bosses < 4) return Component.translatable("cidade.lanterna.falta.bosses", 4); }
                case "onda" -> { if (bosses < 5) return Component.translatable("cidade.lanterna.falta.bosses", 5); }
                case "salto" -> { if (!d.geneseRevealed()) return Component.translatable("cidade.lanterna.falta.genese"); }
                case "armadura" -> { if (bosses < 7) return Component.translatable("cidade.lanterna.falta.bosses", 7); }
                case "comando" -> {
                    boolean prova = d.autoRecarga() || d.active().keySet().stream().anyMatch(k -> k.startsWith("prova_"));
                    if (!prova) return Component.translatable("cidade.lanterna.falta.prova");
                }
                default -> {}
            }
        }
        return null;
    }

    /** Missões que o NPC oferece agora para este jogador. */
    static List<String> ofertas(ServerPlayer p, LoreData d, String funcao, String planet) {
        List<String> l = new ArrayList<>();
        if (funcao.startsWith("capitao_")) {
            l.add("prov_" + funcao.substring("capitao_".length()));
            return l;
        }
        if (funcao.startsWith("cidadao")) {
            l.add("cid_" + planet + "_" + funcao + "_d" + dia(p));
            return l;
        }
        if (!CADEIAS.contains(funcao)) return l;
        for (int n = 1; n <= 3; n++) {
            String id = "cid_" + planet + "_" + funcao + "_" + n;
            if (!d.isDone(id) || n == 3) { l.add(id); break; }
        }
        l.add("cid_" + planet + "_" + funcao + "_d" + dia(p));
        return l;
    }

    public static void abrirPainel(ServerPlayer p, LanternaEntity npc, String funcao, String planet) {
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null) return;
        List<String> ids = ofertas(p, d, funcao, planet);
        lembrarConversa(p, funcao, planet);
        enviarPainel(p, d, npc.getDisplayName(), ids);
        CidadeNpcs.fala(p, npc, Component.translatable("cidade.lanterna.fala." + (funcao.startsWith("cidadao") ? "cidadao" : funcao.startsWith("capitao_") ? "capitao" : funcao)));
    }

    static void enviarPainel(ServerPlayer p, LoreData d, Component titulo, List<String> ids) {
        List<Integer> est = new ArrayList<>();
        List<String> notas = new ArrayList<>();
        for (String id : ids) {
            Component falta = falta(p, d, id);
            int st = d.isDone(id) ? 2 : d.isActive(id) ? 1 : falta != null ? 3 : 0;
            est.add(st);
            notas.add(st == 3 ? Component.Serializer.toJson(falta) : st == 0 ? Component.Serializer.toJson(premioTexto(id)) : "");
        }
        int ativas = (int) d.active().keySet().stream().filter(CidadeMissoes::eDaCidade).count();
        ModNetwork.sendTo(p, new QuadroPacket("oa", ids, est, ativas, Component.Serializer.toJson(titulo), notas));
    }

    private static Component premioTexto(String id) {
        Mission m = mission(id);
        return Component.translatable("cidade.lanterna.premio", m == null ? 0 : m.affinity());
    }

    public static void aceitar(ServerPlayer p, String id) {
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null || d.isActive(id) || (d.isDone(id) && !id.contains("_d"))) return;
        if (mission(id) == null) return;
        Component falta = falta(p, d, id);
        if (falta != null) {
            p.displayClientMessage(falta, true);
            return;
        }
        long ativas = d.active().keySet().stream().filter(CidadeMissoes::eDaCidade).count();
        if (ativas >= MAX_ATIVAS && !id.startsWith("pal_")) {
            p.displayClientMessage(Component.translatable("cidade.lanterna.limite", MAX_ATIVAS), true);
            return;
        }
        MissionManager.start(p, id);
        // Quem deu a missão já está conversando com você: "fale com ..." dele mesmo conta na hora
        CompoundTag u = p.getPersistentData().getCompound(ULTIMA_CONVERSA);
        if (!u.isEmpty() && p.level().getGameTime() - u.getLong("t") < 20 * 60)
            MissionManager.onTalk(p, u.getString("papel"), u.getString("planeta"));
        p.closeContainer();
    }

    private static final String ULTIMA_CONVERSA = "lanterna_ultima_conversa";

    /** Guarda com quem o jogador acabou de falar (para aceitar a missão contar a conversa). */
    public static void lembrarConversa(ServerPlayer p, String papel, String planeta) {
        CompoundTag u = new CompoundTag();
        u.putString("papel", papel);
        u.putString("planeta", planeta);
        u.putLong("t", p.level().getGameTime());
        p.getPersistentData().put(ULTIMA_CONVERSA, u);
    }

    public static void desistir(ServerPlayer p, String id) {
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null || !d.isActive(id) || id.startsWith("pal_")) return;
        d.abandon(id);
        MissionManager.sync(p, d);
        sumirAlvos(p, id);
    }

    // ===================== Ao concluir =====================

    public static void aoConcluir(ServerPlayer p, LoreData d, Mission m) {
        String id = m.id();
        if (!eDaCidade(id)) return;
        Partes pt = partes(id);
        if (pt != null) {
            int rep = pt.dia >= 0 ? (pt.npc.startsWith("cidadao") ? 6 : 10) : pt.passo == 1 ? 20 : pt.passo == 2 ? 30 : 50;
            int antes = CidadeNpcs.nivelRep(d.rep("cidade_" + pt.planet));
            d.addRep("cidade_" + pt.planet, rep);
            int depois = CidadeNpcs.nivelRep(d.rep("cidade_" + pt.planet));
            p.sendSystemMessage(Component.translatable("cidade.lanterna.rep_ganha", rep, Component.translatable("planet.lanterna." + pt.planet))
                    .withStyle(s -> s.withColor(m.color())));
            if (depois > antes) {
                p.sendSystemMessage(Component.translatable("cidade.lanterna.rep_subiu", Component.translatable("cidade.lanterna.rep." + depois))
                        .withStyle(s -> s.withColor(0xFFD040).withBold(true)));
            }
            sumirAlvos(p, id);
            return;
        }
        if (id.startsWith("prov_")) {
            Tecnicas.desbloquear(p, d, id.substring(5));
            sumirAlvos(p, id);
        }
        if (id.equals("pal_honra")) Tecnicas.honra(p, d);
    }

    // ===================== Linha do Palácio começa sozinha no nível 30 =====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 40 != 23) return;
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null) return;
        if (p.tickCount % 200 == 23 && Nivel.nivel(d.xp()) >= 30 && !d.isActive("pal_chamado") && !d.isDone("pal_chamado")
                && Factions.playerCorps(p) != null) {
            MissionManager.start(p, "pal_chamado");
            p.sendSystemMessage(Component.translatable("cidade.lanterna.chamado").withStyle(s -> s.withColor(0x46E66E).withBold(true)));
        }
        alvos(p, d);
    }

    // ===================== Alvos das missões (aparecem perto do lugar; reforços se faltar) =====================

    public static String tag(String id, ServerPlayer p) {
        return id + ":" + p.getStringUUID();
    }

    private static void alvos(ServerPlayer p, LoreData d) {
        SpaceBodies.Body here = SpaceBodies.bodyAt(p.level().dimension(), p.getX(), p.getZ());
        if (here == null) return;
        long now = p.level().getGameTime();
        CompoundTag cds = p.getPersistentData().getCompound("lanterna_cid_cd");
        for (String id : new ArrayList<>(d.active().keySet())) {
            if (!eDaCidade(id)) continue;
            Mission m = mission(id);
            if (m == null) continue;
            for (int i = 0; i < m.objectives().size(); i++) {
                Objective o = m.objectives().get(i);
                if (o.pos() == null || o.planet() == null || !o.planet().equals(here.id())) continue;
                int falta = o.count() - d.progress(id, i);
                if (falta <= 0) continue;
                double dx = p.getX() - o.pos().getX(), dz = p.getZ() - o.pos().getZ();
                String chave = id + "#" + i;
                if (dx * dx + dz * dz > 110 * 110) { cds.remove("perto#" + chave); continue; }
                // os alvos que já existem precisam carregar antes de contar quantos faltam (senão aparecem repetidos)
                if (!cds.contains("perto#" + chave)) { cds.putLong("perto#" + chave, now); p.getPersistentData().put("lanterna_cid_cd", cds); continue; }
                if (now - cds.getLong("perto#" + chave) < 100) continue;
                if (now < cds.getLong(chave)) continue;
                if (spawnar(p, id, o, falta)) {
                    cds.putLong(chave, now + 20 * 25);
                    p.getPersistentData().put("lanterna_cid_cd", cds);
                }
            }
        }
    }

    private static boolean eAlvo(Entity e, String alvo) {
        return switch (alvo) {
            case "papel:pirata" -> e instanceof LanternaEntity l && l.papel() == com.lanterna.especies.Especie.Papel.PIRATA;
            case "papel:capitao" -> e instanceof LanternaEntity l && l.papel() == com.lanterna.especies.Especie.Papel.CAPITAO;
            case "papel:inimigo" -> e instanceof LanternaEntity l && l.papel() == com.lanterna.especies.Especie.Papel.LANTERNA && !l.getPersistentData().getBoolean("lanterna_aliado");
            case "lanterna:eco_cinzento" -> e.getType() == LoreEntities.ECO_CINZENTO.get();
            default -> alvo.startsWith("alvo:") ? alvo.substring(5).equals(e.getPersistentData().getString("lanterna_alvo"))
                    : alvo.startsWith("resgate:") && e.getPersistentData().getBoolean("lanterna_resgate");
        };
    }

    /** Põe os alvos que faltam perto do lugar. Retorna true se fez algo. */
    private static boolean spawnar(ServerPlayer p, String id, Objective o, int falta) {
        String alvo = o.target();
        boolean conhecido = alvo.startsWith("papel:") || alvo.startsWith("alvo:") || alvo.equals("lanterna:eco_cinzento")
                || (o.type() == Type.TALK && alvo.startsWith("resgate:"));
        if (!conhecido || (o.type() != Type.KILL && o.type() != Type.TALK)) return false;
        ServerLevel level = p.serverLevel();
        String tag = tag(id, p);
        BlockPos c = superficie(level, o.pos());
        int vivos = level.getEntities((Entity) null, new AABB(c).inflate(120, 90, 120),
                e -> e.isAlive() && tag.equals(e.getPersistentData().getString(Contratos.TAG)) && eAlvo(e, alvo)).size();
        int quer = Math.min(alvo.startsWith("resgate:") ? 1 : falta, 10);
        if (vivos >= quer) return false;
        for (int k = vivos; k < quer; k++) {
            LivingEntity e = criar(level, p, alvo);
            if (e == null) return false;
            BlockPos q = superficie(level, c.offset(level.random.nextInt(11) - 5, 0, level.random.nextInt(11) - 5));
            e.moveTo(q.getX() + 0.5, q.getY(), q.getZ() + 0.5, level.random.nextFloat() * 360, 0);
            e.getPersistentData().putString(Contratos.TAG, tag);
            if (e instanceof net.minecraft.world.entity.Mob mob) mob.setPersistenceRequired();
            level.addFreshEntity(e);
            if (e instanceof LanternaEntity l && l.getPersistentData().getBoolean("lanterna_irritar")) l.irritar(p);
        }
        // Comando: três aliados da sua Tropa lutam do seu lado na simulação
        if (alvo.equals("papel:inimigo") && id.startsWith("prov_")) {
            BuiltinCorps mine = Factions.playerCorps(p);
            if (mine != null && mine != BuiltinCorps.WHITE) for (int i = 0; i < 3; i++) {
                LanternaEntity a = LanternaEntity.lanterna(level, mine, i == 0);
                a.moveTo(c.getX() + i * 2 - 2 + 0.5, c.getY(), c.getZ() + 6.5, 180, 0);
                a.getPersistentData().putString(Contratos.TAG, tag);
                a.getPersistentData().putBoolean("lanterna_aliado", true);
                a.setPersistenceRequired();
                level.addFreshEntity(a);
            }
        }
        if (vivos == 0 && alvo.startsWith("papel:")) level.setBlock(c, net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState(), 3);
        return true;
    }

    @Nullable
    private static LivingEntity criar(ServerLevel level, ServerPlayer p, String alvo) {
        switch (alvo) {
            case "papel:pirata" -> { return LanternaEntity.pirata(level, false); }
            case "papel:capitao" -> { return LanternaEntity.pirata(level, true); }
            case "papel:inimigo" -> {
                BuiltinCorps mine = Factions.playerCorps(p);
                BuiltinCorps foe = BuiltinCorps.BLACK;
                for (BuiltinCorps x : BuiltinCorps.values()) if (x != BuiltinCorps.WHITE && mine != null && Factions.enemies(mine, x) && level.random.nextBoolean()) foe = x;
                return LanternaEntity.lanterna(level, foe, false);
            }
            case "lanterna:eco_cinzento" -> { return LoreEntities.ECO_CINZENTO.get().create(level); }
            default -> {}
        }
        if (alvo.startsWith("resgate:")) {
            BuiltinCorps mine = Factions.playerCorps(p);
            LanternaEntity w = LanternaEntity.lanterna(level, mine == null || mine == BuiltinCorps.WHITE ? BuiltinCorps.GREEN : mine, false);
            w.getPersistentData().putBoolean("lanterna_resgate", true);
            w.setHealth(w.getMaxHealth() * 0.3f);
            return w;
        }
        String nome = alvo.substring(5);
        LanternaEntity e;
        switch (nome) {
            case "duelista" -> {
                BuiltinCorps mine = Factions.playerCorps(p);
                e = LanternaEntity.lanterna(level, mine == null || mine == BuiltinCorps.WHITE ? BuiltinCorps.GREEN : mine, true);
                e.getPersistentData().putBoolean("lanterna_irritar", true);
            }
            case "brakhul" -> {
                BuiltinCorps mine = Factions.playerCorps(p);
                e = LanternaEntity.lanterna(level, mine == null || mine == BuiltinCorps.WHITE ? BuiltinCorps.GREEN : mine, true);
                e.setup(com.lanterna.especies.Especie.byId("brutor"), 2, com.lanterna.especies.Especie.Papel.LANTERNA,
                        mine == null || mine == BuiltinCorps.WHITE ? BuiltinCorps.GREEN : mine, "roupa_tropa_" + (mine == null || mine == BuiltinCorps.WHITE ? "green" : mine.id().getPath()), true, false);
                var hp = e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (hp != null) hp.setBaseValue(160);
                e.setHealth(160);
                e.getPersistentData().putBoolean("lanterna_irritar", true);
            }
            default -> e = LanternaEntity.pirata(level, false);
        }
        e.getPersistentData().putString("lanterna_alvo", nome);
        e.setCustomName(Component.translatable("cidade.lanterna.alvo." + nome));
        e.setCustomNameVisible(true);
        return e;
    }

    private static BlockPos superficie(ServerLevel level, BlockPos p) {
        if (p.getY() >= Palacio.Y - 5) return p; // palácio: altura fixa
        level.getChunk(p.getX() >> 4, p.getZ() >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
        return new BlockPos(p.getX(), y, p.getZ());
    }

    /** Some com os alvos marcados desta missão (ao concluir ou desistir). */
    private static void sumirAlvos(ServerPlayer p, String id) {
        String tag = tag(id, p);
        List<Entity> sumir = new ArrayList<>();
        for (ServerLevel l : p.server.getAllLevels()) {
            for (Entity e : l.getAllEntities()) if (e != null && tag.equals(e.getPersistentData().getString(Contratos.TAG))) sumir.add(e);
        }
        for (Entity e : sumir) if (!e.isRemoved()) e.discard();
    }

    private CidadeMissoes() {}
}
