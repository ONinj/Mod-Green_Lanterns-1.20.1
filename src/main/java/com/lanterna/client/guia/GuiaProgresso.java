package com.lanterna.client.guia;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.lanterna.LanternaMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * O que o jogador já leu no Guia (fica no computador dele, em config/lanterna_guia.json): as páginas lidas, onde
 * parou e se ele já abriu o guia alguma vez (para o aviso de "aperte F9" sumir).
 */
public final class GuiaProgresso {
    private static final Path ARQ = FMLPaths.CONFIGDIR.get().resolve("lanterna_guia.json");
    private static final Set<String> LIDAS = new HashSet<>();
    private static boolean carregado, aberto;
    private static String ultimoCap = "";
    private static int ultimaPag;

    private static void carregar() {
        if (carregado) return;
        carregado = true;
        try {
            if (!Files.exists(ARQ)) return;
            JsonObject o = new Gson().fromJson(Files.readString(ARQ, StandardCharsets.UTF_8), JsonObject.class);
            if (o.has("lidas")) o.getAsJsonArray("lidas").forEach(e -> LIDAS.add(e.getAsString()));
            aberto = o.has("aberto") && o.get("aberto").getAsBoolean();
            if (o.has("cap")) ultimoCap = o.get("cap").getAsString();
            if (o.has("pag")) ultimaPag = o.get("pag").getAsInt();
        } catch (Exception e) {
            LanternaMod.LOGGER.warn("Lanterna: não li o progresso do guia: {}", e.toString());
        }
    }

    private static void salvar() {
        try {
            JsonObject o = new JsonObject();
            var a = new com.google.gson.JsonArray();
            LIDAS.stream().sorted().forEach(a::add);
            o.add("lidas", a);
            o.addProperty("aberto", aberto);
            o.addProperty("cap", ultimoCap);
            o.addProperty("pag", ultimaPag);
            Files.writeString(ARQ, new Gson().toJson(o), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LanternaMod.LOGGER.warn("Lanterna: não salvei o progresso do guia: {}", e.toString());
        }
    }

    public static boolean lida(String cap, int pag) {
        carregar();
        return LIDAS.contains(cap + ":" + pag);
    }

    public static void ler(String cap, int pag) {
        carregar();
        boolean nova = LIDAS.add(cap + ":" + pag);
        if (nova || !cap.equals(ultimoCap) || pag != ultimaPag || !aberto) {
            ultimoCap = cap;
            ultimaPag = pag;
            aberto = true;
            salvar();
        }
    }

    public static boolean jaAbriu() {
        carregar();
        return aberto;
    }

    public static String ultimoCapitulo() {
        carregar();
        return ultimoCap;
    }

    public static int ultimaPagina() {
        carregar();
        return ultimaPag;
    }

    /** Teste: começa do zero. */
    public static void zerar() {
        carregar();
        LIDAS.clear();
        aberto = false;
        ultimoCap = "";
        ultimaPag = 0;
        salvar();
    }

    private GuiaProgresso() {}
}
