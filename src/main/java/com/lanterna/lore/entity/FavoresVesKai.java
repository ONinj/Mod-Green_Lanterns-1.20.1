package com.lanterna.lore.entity;

import com.lanterna.lore.LoreItems;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.Mission;
import com.lanterna.mission.Objective;
import com.lanterna.mission.Objective.Type;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Favores dos Ves'Kai (Nok). Cada Ves'Kai pede um favor por dia; quem ajuda ganha Moedas de Gratidão, que os
 * Ves'Kai trocam por Lágrimas Índigo (e outras coisas). Curar um Ves'Kai ferido também dá moedas.
 *
 *   vk_<favor>_d<dia>
 */
public final class FavoresVesKai {
    /** Favores ao mesmo tempo. */
    public static final int MAX = 3;

    /** id do favor, objetivo (tipo, alvo, quantidade), moedas de recompensa. */
    private record Favor(String nome, Type tipo, String alvo, int qtd, int moedas) {}

    private static final List<Favor> FAVORES = List.of(
            new Favor("frutos", Type.COLLECT, "minecraft:glow_berries", 12, 2),
            new Favor("bagas", Type.COLLECT, "minecraft:sweet_berries", 16, 2),
            new Favor("velas", Type.COLLECT, "minecraft:candle", 6, 2),
            new Favor("ataduras", Type.COLLECT, "minecraft:white_wool", 8, 2),
            new Favor("cristais", Type.COLLECT, "minecraft:amethyst_shard", 8, 3),
            new Favor("mel", Type.COLLECT, "minecraft:honey_bottle", 3, 3),
            new Favor("pao", Type.COLLECT, "minecraft:bread", 8, 2),
            new Favor("ecos", Type.KILL, "lanterna:eco_cinzento", 6, 3),
            new Favor("sombras", Type.KILL, "minecraft:zombie", 8, 3));

    private static final Map<String, Mission> CACHE = new HashMap<>();

    public static boolean eFavor(String id) {
        return id.startsWith("vk_");
    }

    public static long dia(ServerPlayer p) {
        return p.server.overworld().getDayTime() / 24000L;
    }

    /** O favor que este Ves'Kai pede hoje. */
    public static String favorDe(RaceEntity npc, long dia) {
        int k = (int) Math.floorMod(npc.getUUID().getLeastSignificantBits() + dia * 7, (long) FAVORES.size());
        return "vk_" + FAVORES.get(k).nome() + "_d" + dia;
    }

    @Nullable
    private static Favor favor(String id) {
        if (!eFavor(id)) return null;
        String[] s = id.split("_");
        if (s.length != 3) return null;
        for (Favor f : FAVORES) if (f.nome().equals(s[1])) return f;
        return null;
    }

    @Nullable
    public static Mission mission(String id) {
        Mission c = CACHE.get(id);
        if (c != null) return c;
        Favor f = favor(id);
        if (f == null) return null;
        List<Objective> o = new ArrayList<>();
        o.add(Objective.of(f.tipo(), f.alvo(), f.qtd(), null, null));
        int moedas = f.moedas();
        Mission m = new Mission(id, "veskai", 0x8A70FF, o, () -> {
            List<ItemStack> out = new ArrayList<>();
            out.add(new ItemStack(LoreItems.GRATIDAO.get(), moedas));
            return out;
        }, 60, null, null);
        CACHE.put(id, m);
        return m;
    }

    public static Component titulo(String id) {
        Favor f = favor(id);
        return Component.translatable("mission.lanterna.vk." + (f == null ? "?" : f.nome()) + ".title");
    }

    public static Component descricao(String id) {
        Favor f = favor(id);
        if (f == null) return Component.empty();
        return Component.translatable("mission.lanterna.vk." + f.nome() + ".desc", f.qtd(), f.moedas());
    }

    /** Este Ves'Kai pode oferecer o favor de hoje a este jogador? */
    @Nullable
    public static String oferta(ServerPlayer p, RaceEntity npc, @Nullable LoreData d) {
        if (d == null || npc.hasFlag(RaceEntity.WOUNDED) || !npc.role().isEmpty()) return null;
        String id = favorDe(npc, dia(p));
        if (d.isActive(id) || d.isDone(id)) return null;
        long ativos = d.active().keySet().stream().filter(FavoresVesKai::eFavor).count();
        return ativos >= MAX ? null : id;
    }

    private FavoresVesKai() {}
}
