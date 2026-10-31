package com.lanterna.mission;

import com.lanterna.lore.LoreItems;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.Objective.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Todas as missões do mod.
 *
 * Linha de início (uma por Tropa): ir ao planeta da Tropa, falar com o líder (em Oa, o Instrutor Veltari),
 * treinar construtos nos alvos e se apresentar aos Guardiões em Oa.
 *
 * Linha principal ("O Caminho do Branco"): em cada um dos sete planetas da fusão (ordem da lore:
 * Odym, Oa, Okaara, Ysmault, Zamaron, Korugar, Nok) — viajar, ganhar o respeito da raça, minerar o minério,
 * derrotar o boss e forjar o anel. Depois: juntar os sete anéis e os sete drops, revelar Gênese,
 * fundir o Anel Branco no Altar da Vida e derrotar o Nulo.
 *
 * Linha de Ryut (opcional, oferecida pelos Guardiões): o Coletor, o Devorador de Ecos e o Anel Preto.
 */
public final class Missions {
    private static final Map<String, Mission> ALL = new LinkedHashMap<>();

    /** Ordem dos planetas na linha principal. */
    public static final PlanetLore[] MAIN_ORDER = {PlanetLore.ODYM, PlanetLore.OA, PlanetLore.OKAARA, PlanetLore.YSMAULT,
            PlanetLore.ZAMARON, PlanetLore.KORUGAR, PlanetLore.NOK};

    /** Respeito necessário com cada raça na linha principal. */
    public static int repNeeded(String race) {
        return race.equals("brakkari") ? 1 : 3;
    }

    static {
        // ---------------- Início (uma linha por Tropa) ----------------
        for (PlanetLore home : PlanetLore.values()) {
            if (home == PlanetLore.GENESE) continue;
            String c = home.corps.id().getPath();
            String p = home.planet;
            boolean green = home == PlanetLore.OA;
            String talk = green ? "instrutor:oa" : "lider:" + p;
            add("inicio_" + c + "_1", "inicio", home.color(), List.of(goTo(p)), items(), 100, "inicio_" + c + "_2");
            add("inicio_" + c + "_2", "inicio", home.color(), List.of(talk(talk, p)), items(), 100, "inicio_" + c + "_3");
            add("inicio_" + c + "_3", "inicio", home.color(), List.of(
                    Objective.of(Type.CONSTRUCT, "parede", 1, p, PlanetLore.HUB),
                    Objective.of(Type.CONSTRUCT, "laser", 1, p, PlanetLore.HUB),
                    Objective.of(Type.DUMMY, "alvo", 3, p, PlanetLore.HUB)),
                    items(() -> new ItemStack(LoreItems.MERITO.get(), 3)), 300, "inicio_" + c + "_4");
            // Cada planeta das Tropas tem o seu Guardião: ninguém precisa ir a Oa para se apresentar
            List<Objective> meet = List.of(talk("guardiao:" + p, p));
            add("inicio_" + c + "_4", "inicio", home.color(), meet,
                    items(() -> new ItemStack(LoreItems.MERITO.get(), 2), () -> new ItemStack(LoreItems.ANEL_VAZIO.get())),
                    200, "main_" + MAIN_ORDER[0].planet + "_1");
        }

        // ---------------- Principal: os sete planetas ----------------
        for (int i = 0; i < MAIN_ORDER.length; i++) {
            PlanetLore pl = MAIN_ORDER[i];
            String p = pl.planet;
            String next = i + 1 < MAIN_ORDER.length ? "main_" + MAIN_ORDER[i + 1].planet + "_1" : "main_genese_1";
            add("main_" + p + "_1", "principal", pl.color(), List.of(goTo(p)), items(), 100, "main_" + p + "_2");
            add("main_" + p + "_2", "principal", pl.color(), List.of(
                    Objective.of(Type.REP, pl.race, repNeeded(pl.race), p, PlanetLore.HUB),
                    talk((pl == PlanetLore.OA ? "guardiao:" : "lider:") + p, p)),
                    items(() -> new ItemStack(LoreItems.MERITO.get(), 2)), 200, "main_" + p + "_3");
            add("main_" + p + "_3", "principal", pl.color(), List.of(
                    Objective.of(Type.COLLECT, "lanterna:" + pl.ore, 8, p, null)),
                    items(), 150, "main_" + p + "_4");
            add("main_" + p + "_4", "principal", pl.color(), List.of(
                    Objective.of(Type.BOSS, pl.boss, 1, p, PlanetLore.arena(pl))),
                    items(() -> new ItemStack(LoreItems.MERITO.get(), 3), () -> new ItemStack(LoreItems.ANEL_VAZIO.get())),
                    500, "main_" + p + "_5");
            add("main_" + p + "_5", "principal", pl.color(), List.of(
                    Objective.of(Type.RING, pl.corps.id().getPath(), 1, p, PlanetLore.HUB)),
                    items(), 300, next);
            // Rastro: a pista leva a outro planeta (piratas para derrubar e minério de lá para trazer)
            PlanetLore outro = MAIN_ORDER[(i + 3) % MAIN_ORDER.length];
            List<Objective> rastro = new ArrayList<>();
            rastro.add(goTo(outro.planet));
            rastro.add(Objective.of(Type.KILL, "papel:pirata", 10, outro.planet, null));
            if (outro.ore != null) rastro.add(Objective.of(Type.COLLECT, "lanterna:" + outro.ore, 4, outro.planet, null));
            add("main_" + p + "_6", "principal", pl.color(), rastro,
                    items(() -> new ItemStack(LoreItems.MERITO.get(), 4)), 400, "main_" + p + "_3");
            // Provação: o povo só confia em quem já é forte (nível) e limpa o planeta de capitães e Ecos
            add("main_" + p + "_7", "principal", pl.color(), List.of(
                    Objective.of(Type.LEVEL, "nivel", provacao(pl), null, null),
                    Objective.of(Type.KILL, "papel:capitao", 3, p, null),
                    Objective.of(Type.KILL, "lanterna:eco_cinzento", 15, p, null)),
                    items(() -> new ItemStack(LoreItems.MERITO.get(), 6)), 500, "main_" + p + "_4");
        }

        // ---------------- Gênese ----------------
        int white = PlanetLore.GENESE.color();
        // O Juramento da Vida: antes da fusão, provar que a Vida é mais forte que a Morte
        add("main_genese_0", "principal", white, List.of(
                Objective.of(Type.LEVEL, "nivel", 46, null, null),
                Objective.of(Type.KILL, "papel:inimigo", 25, null, null),
                Objective.of(Type.KILL, "papel:capitao", 10, null, null),
                Objective.of(Type.COLLECT, "lanterna:merito", 32, null, null)),
                items(), 1500, "main_genese_1");
        add("main_genese_1", "principal", white, List.of(Objective.of(Type.FUSION, "fusao", 14, null, null)),
                items(), 300, "main_genese_2");
        add("main_genese_2", "principal", white, List.of(goTo("genese")), items(), 200, "main_genese_3");
        add("main_genese_3", "principal", white, List.of(Objective.of(Type.RING, "white", 1, "genese", PlanetLore.HUB)),
                items(), 1000, "main_genese_4");
        add("main_genese_4", "principal", white, List.of(Objective.of(Type.BOSS, "nulo", 1, "genese", PlanetLore.arena(PlanetLore.GENESE))),
                items(() -> new ItemStack(LoreItems.FRAGMENTO_PRIMORDIAL.get())), 3000, null);

        // ---------------- Ryut (opcional) ----------------
        PlanetLore ryut = PlanetLore.RYUT;
        int black = ryut.color();
        add("ryut_1", "ryut", black, List.of(goTo("ryut"), talk("lider:ryut", "ryut")), items(), 100, "ryut_2");
        add("ryut_2", "ryut", black, List.of(Objective.of(Type.REP, "silente", 3, "ryut", PlanetLore.HUB),
                Objective.of(Type.COLLECT, "lanterna:obsidiana_do_fim", 8, "ryut", null)), items(), 300, "ryut_3");
        add("ryut_3", "ryut", black, List.of(Objective.of(Type.BOSS, ryut.boss, 1, "ryut", PlanetLore.arena(ryut))),
                items(() -> new ItemStack(LoreItems.ANEL_VAZIO.get())), 600, "ryut_4");
        add("ryut_4", "ryut", black, List.of(talk("lider:ryut", "ryut")),
                items(() -> new ItemStack(LoreItems.RELIQUIA.get()), () -> new ItemStack(LoreItems.MERITO.get(), 6)), 400, null);

        // ---------------- A Morte (só para quem teve o anel corrompido ao morrer) ----------------
        add("morte_1", "morte", black, List.of(goTo("ryut"), talk("lider:ryut", "ryut")), items(), 200, "morte_2");
        add("morte_2", "morte", black, List.of(
                Objective.of(Type.KILL, "qualquer", 60, null, null),
                Objective.of(Type.COLLECT, "lanterna:alma", 24, null, null)), items(), 400, "morte_3");
        add("morte_3", "morte", black, List.of(
                Objective.of(Type.LEVEL, "nivel", 25, null, null),
                Objective.of(Type.REP, "silente", 6, "ryut", PlanetLore.HUB)), items(), 400, "morte_4");
        add("morte_4", "morte", black, List.of(
                Objective.of(Type.KILL, "papel:inimigo", 15, null, null),
                Objective.of(Type.COLLECT, "lanterna:obsidiana_do_fim", 12, null, null)), items(), 600, "morte_5");
        add("morte_5", "morte", black, List.of(
                Objective.of(Type.COLLECT, "lanterna:reliquia", 3, null, null),
                Objective.of(Type.COLLECT, "lanterna:alma", 32, null, null)), items(), 700, "morte_6");
        add("morte_6", "morte", black, List.of(
                Objective.of(Type.LEVEL, "nivel", 35, null, null),
                Objective.of(Type.KILL, "papel:capitao", 6, null, null),
                Objective.of(Type.BOSS, ryut.boss, 1, "ryut", PlanetLore.arena(ryut))),
                items(() -> new ItemStack(LoreItems.ANEL_VAZIO.get())), 900, "morte_7");
        add("morte_7", "morte", black, List.of(goTo("odym"), goTo("okaara"), goTo("zamaron"), goTo("nok"),
                Objective.of(Type.KILL, "qualquer", 150, null, null),
                Objective.of(Type.COLLECT, "lanterna:alma", 48, null, null)), items(), 1200, "morte_8");
        add("morte_8", "morte", black, List.of(
                Objective.of(Type.LEVEL, "nivel", 42, null, null),
                Objective.of(Type.RING, "black", 1, "ryut", PlanetLore.HUB)), items(), 2500, "morte_ceifa");
        // O dever do Lanterna Negro: levar a morte a outros portadores de anel (repete para sempre)
        add("morte_ceifa", "patrulha", black, List.of(Objective.of(Type.KILL, "jogador_anel", 1, null, null)),
                items(() -> new ItemStack(LoreItems.ALMA.get(), 12)), 800, "morte_ceifa");

        // ---------------- A Porta Estelar (Guardiões, nível 32 + afinidade Confiável) ----------------
        int verdeClaro = 0x7CFFB0;
        add("portal_1", "portal", verdeClaro, List.of(talk("guardiao:oa", "oa")), items(), 300, "portal_2");
        add("portal_2", "portal", verdeClaro, List.of(goTo("odym"), goTo("korugar"), goTo("zamaron"),
                Objective.of(Type.AFFINITY, "afinidade", com.lanterna.portal.PortalEstelar.AFINIDADE, null, null),
                Objective.of(Type.LEVEL, "nivel", com.lanterna.portal.PortalEstelar.NIVEL, null, null)),
                items(() -> new ItemStack(LoreItems.MERITO.get(), 8)), 600, "portal_3");
        add("portal_3", "portal", verdeClaro, List.of(talk("guardiao:oa", "oa")), items(), 800, null);

        // ---------------- Patrulha (repetível, dos Veltari) ----------------
        add("patrulha", "patrulha", PlanetLore.OA.color(), List.of(Objective.of(Type.KILL, "lanterna:eco_cinzento", 8, null, null)),
                items(() -> new ItemStack(LoreItems.MERITO.get(), 3)), 200, null);
    }

    /** Nível pedido na Provação de cada planeta (no planeta da própria Tropa é menor: o resto do caminho cobra). */
    public static int provacao(PlanetLore pl) {
        return com.lanterna.tropa.Nivel.nivelParaForjar(pl) + 4;
    }

    private static Objective goTo(String planet) {
        return Objective.of(Type.GOTO, planet, 1, planet, null);
    }

    private static Objective talk(String who, String planet) {
        return Objective.of(Type.TALK, who, 1, planet, PlanetLore.HUB);
    }

    static {
        com.lanterna.tropa.Prova.registrar(ALL::put);
        // Treino: chegar a um nível (a linha principal espera o Lanterna ficar forte o bastante)
        for (int lvl = 2; lvl <= 50; lvl++) {
            ALL.put("treino_" + lvl, new Mission("treino_" + lvl, "principal", 0xFFD040,
                    List.of(Objective.of(Type.LEVEL, "nivel", lvl, null, null)), items(), 0, null, null));
        }
    }

    @SafeVarargs
    private static Supplier<List<ItemStack>> items(Supplier<ItemStack>... stacks) {
        return () -> {
            List<ItemStack> list = new ArrayList<>();
            for (Supplier<ItemStack> s : stacks) list.add(s.get());
            return list;
        };
    }

    private static void add(String id, String line, int color, List<Objective> objectives, Supplier<List<ItemStack>> rewards,
                            int affinity, @Nullable String next) {
        ALL.put(id, new Mission(id, line, color, objectives, rewards, affinity, next, null));
    }

    @Nullable
    public static Mission get(@Nullable String id) {
        if (id == null) return null;
        Mission m = ALL.get(id);
        if (m == null && id.startsWith("ct_")) return com.lanterna.tropa.Contratos.mission(id);
        if (m == null && com.lanterna.cidade.CidadeMissoes.eDaCidade(id)) return com.lanterna.cidade.CidadeMissoes.mission(id);
        return m;
    }

    public static Collection<Mission> all() {
        return ALL.values();
    }

    /** Primeira missão da linha de início da Tropa. */
    @Nullable
    public static String startOf(net.minecraft.resources.ResourceLocation corps) {
        String id = "inicio_" + corps.getPath() + "_1";
        return ALL.containsKey(id) ? id : null;
    }

    public static BlockPos hub() {
        return PlanetLore.HUB;
    }

    private Missions() {}
}
