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
        }

        // ---------------- Gênese ----------------
        int white = PlanetLore.GENESE.color();
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
        add("ryut_4", "ryut", black, List.of(Objective.of(Type.RING, "black", 1, "ryut", PlanetLore.HUB)), items(), 400, null);

        // ---------------- Patrulha (repetível, dos Veltari) ----------------
        add("patrulha", "patrulha", PlanetLore.OA.color(), List.of(Objective.of(Type.KILL, "lanterna:eco_cinzento", 8, null, null)),
                items(() -> new ItemStack(LoreItems.MERITO.get(), 3)), 200, null);
    }

    private static Objective goTo(String planet) {
        return Objective.of(Type.GOTO, planet, 1, planet, null);
    }

    private static Objective talk(String who, String planet) {
        return Objective.of(Type.TALK, who, 1, planet, PlanetLore.HUB);
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
        return id == null ? null : ALL.get(id);
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
