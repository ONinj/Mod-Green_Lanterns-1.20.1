package com.lanterna.client.guia;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.item.PowerBatteryItem;
import com.lanterna.item.PowerRingItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * O conteúdo do Guia do Portador, lido de assets/lanterna/guia/guia.json (português e inglês no mesmo arquivo, para
 * ficar fácil de escrever e revisar). Cada capítulo tem páginas; cada página tem texto e, se quiser, teclas, itens,
 * uma receita de verdade (lida do jogo) e uma dica.
 *
 * No texto: **assim** fica na cor da Tropa, {tecla:key.lanterna.toggle_flight} vira a tecla que o jogador usa de
 * verdade, e ||assim|| fica tarjado (só no capítulo do que vem por aí).
 */
public final class GuiaDados {

    public record Pagina(String titulo, String texto, List<String> teclas, List<String> itens, List<String> receitas, String dica) {}

    public record Capitulo(String id, String icone, String titulo, String resumo, int cor, boolean spoiler, List<Pagina> paginas) {}

    private static List<Capitulo> cache;
    private static String cacheLingua;

    public static List<Capitulo> capitulos() {
        String lingua = lingua();
        if (cache == null || !lingua.equals(cacheLingua)) {
            cache = carregar(lingua);
            cacheLingua = lingua;
        }
        return cache;
    }

    /** Descarta o que foi lido (F3+T recarrega). */
    public static void esquecer() {
        cache = null;
    }

    static String lingua() {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        return code != null && code.toLowerCase(Locale.ROOT).startsWith("pt") ? "pt" : "en";
    }

    private static List<Capitulo> carregar(String lingua) {
        List<Capitulo> out = new ArrayList<>();
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(LanternaMod.id("guia/guia.json"));
            if (res.isEmpty()) return out;
            try (var r = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                JsonObject raiz = JsonParser.parseReader(r).getAsJsonObject();
                for (JsonElement ce : raiz.getAsJsonArray("capitulos")) {
                    JsonObject c = ce.getAsJsonObject();
                    List<Pagina> pags = new ArrayList<>();
                    for (JsonElement pe : c.getAsJsonArray("paginas")) {
                        JsonObject p = pe.getAsJsonObject();
                        pags.add(new Pagina(txt(p, "titulo", lingua), txt(p, "texto", lingua), lista(p, "teclas"), lista(p, "itens"),
                                lista(p, "receitas"), txt(p, "dica", lingua)));
                    }
                    int cor = c.has("cor") ? Integer.parseInt(c.get("cor").getAsString().replace("#", ""), 16) : -1;
                    out.add(new Capitulo(c.get("id").getAsString(), c.has("icone") ? c.get("icone").getAsString() : "minecraft:book",
                            txt(c, "titulo", lingua), txt(c, "resumo", lingua), cor, c.has("spoiler") && c.get("spoiler").getAsBoolean(), pags));
                }
            }
        } catch (Exception e) {
            LanternaMod.LOGGER.error("Lanterna: não consegui ler o Guia do Portador", e);
        }
        return out;
    }

    private static String txt(JsonObject o, String chave, String lingua) {
        if (!o.has(chave)) return "";
        JsonElement e = o.get(chave);
        if (e.isJsonPrimitive()) return e.getAsString();
        JsonObject t = e.getAsJsonObject();
        if (t.has(lingua)) return t.get(lingua).getAsString();
        return t.has("en") ? t.get("en").getAsString() : t.has("pt") ? t.get("pt").getAsString() : "";
    }

    private static List<String> lista(JsonObject o, String chave) {
        List<String> l = new ArrayList<>();
        if (!o.has(chave)) return l;
        JsonArray a = o.getAsJsonArray(chave);
        for (JsonElement e : a) l.add(e.getAsString());
        return l;
    }

    /**
     * O ícone de um item do guia: "anel:green", "bateria:red", ou o id de um item ("lanterna:merito").
     */
    public static ItemStack item(String spec) {
        try {
            if (spec.startsWith("anel:")) {
                BuiltinCorps c = corps(spec.substring(5));
                return PowerRingItem.create(c.id(), c.color(), -1);
            }
            if (spec.startsWith("bateria:")) {
                BuiltinCorps c = corps(spec.substring(8));
                return PowerBatteryItem.create(c.id(), c.color());
            }
            var item = BuiltInRegistries.ITEM.get(new ResourceLocation(spec));
            return item == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(item);
        } catch (Exception e) {
            return new ItemStack(Items.BARRIER);
        }
    }

    private static BuiltinCorps corps(String nome) {
        for (BuiltinCorps c : BuiltinCorps.values()) if (c.id().getPath().equals(nome)) return c;
        return BuiltinCorps.GREEN;
    }

    private GuiaDados() {}
}
