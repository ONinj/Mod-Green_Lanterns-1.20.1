package com.lanterna.client.ia;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lanterna.LanternaMod;
import com.lanterna.client.mission.ClientLoreData;
import com.lanterna.ia.Texto;
import com.lanterna.mission.Mission;
import com.lanterna.mission.Missions;
import com.lanterna.mission.Objective;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * O anel tira dúvidas sobre o mod: como fabricar um item (lendo as receitas de verdade do jogo, inclusive as de outros
 * mods), como fazer uma missão (os passos e onde fica), e os assuntos do mod (assets/lanterna/anel/duvidas.json, em
 * português e inglês).
 */
public final class Duvidas {

    private record Topico(String id, List<String> chaves, String pt, String en) {}

    private static List<Topico> topicos;

    private static final Set<String> VAZIAS = new HashSet<>(Arrays.asList(
            "como", "eu", "faco", "faz", "fazer", "crafto", "craftar", "crafta", "fabrico", "fabricar", "fabrica", "receita", "receitas",
            "recipe", "recipes", "craft", "crafting", "how", "do", "does", "i", "to", "make", "the", "a", "an", "o", "os", "as", "um", "uma",
            "de", "do", "da", "dos", "das", "para", "pra", "pro", "consigo", "conseguir", "pego", "pegar", "obter", "obtenho", "onde",
            "acho", "achar", "encontro", "encontrar", "where", "find", "get", "of", "for", "is", "can", "qual", "quais", "what", "e", "sao",
            "que", "me", "ensina", "ensine", "explica", "explique", "se", "item", "itens", "posso", "você", "voce", "vc",
            "my", "meu", "minha", "this", "isso", "esse", "essa", "no", "na", "em", "in", "on", "at", "duvida", "tutorial", "por", "favor",
            "please", "fica", "ficam", "tem", "ter", "dar", "da", "will", "would", "you", "your", "build", "construir", "construo",
            "missao", "missoes", "mission", "missions", "quest", "passo", "passar", "completar", "completo", "terminar", "termino",
            "complete", "finish", "pass", "beat", "objetivo", "objective", "agora", "now", "aquela", "aquele", "that"));

    /** A resposta, ou null se não achou nada que valha (aí o anel tenta do jeito antigo). */
    @Nullable
    public static Component responder(String limpo, LocalPlayer p) {
        boolean pt = VozAnel.idioma().startsWith("pt");
        boolean receita = Texto.tem(limpo, "receita", "receitas", "craft", "craftar", "crafto", "crafta", "fabricar", "fabrico", "recipe",
                "recipes", "crafting");
        boolean missao = Texto.tem(limpo, "missao", "missoes", "mission", "missions", "quest", "quests", "objetivo", "objective");
        String assunto = assunto(limpo);

        // 1) "receita de X": vai direto nas receitas
        if (receita && !assunto.isEmpty()) {
            ItemStack it = item(assunto);
            if (it != null) return comoFazer(it);
        }
        // 2) missões: pelo nome, ou a que está acompanhando
        if (missao) {
            Mission m = missaoPeloNome(assunto);
            if (m == null) m = ClientLoreData.tracked();
            if (m != null) return guia(m, p);
            return Component.translatable("anel.ia.duvida.sem_missao");
        }
        // 3) os assuntos do mod
        Topico t = topico(limpo);
        if (t != null) return Component.literal(pt ? t.pt() : t.en());
        // 4) um item pelo nome ("como faço uma bateria verde")
        if (!assunto.isEmpty()) {
            ItemStack it = item(assunto);
            if (it != null) return comoFazer(it);
            Mission m = missaoPeloNome(assunto);
            if (m != null) return guia(m, p);
        }
        return null;
    }

    /** Tira as palavras de pergunta e deixa só o assunto ("como eu faço a bateria verde" -> "bateria verde"). */
    static String assunto(String limpo) {
        StringBuilder b = new StringBuilder();
        List<String> ws = new ArrayList<>(Texto.palavras(limpo));
        if (!ws.isEmpty() && (ws.get(0).equals("anel") || ws.get(0).equals("ring"))) ws.remove(0);
        for (String w : ws) {
            if (VAZIAS.contains(w)) continue;
            if (b.length() > 0) b.append(' ');
            b.append(w);
        }
        return b.toString();
    }

    // ===================== assuntos do mod =====================

    private static List<Topico> topicos() {
        if (topicos != null) return topicos;
        List<Topico> l = new ArrayList<>();
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(LanternaMod.MODID, "anel/duvidas.json"));
            if (res.isPresent()) try (var r = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                JsonArray a = JsonParser.parseReader(r).getAsJsonArray();
                for (JsonElement e : a) {
                    JsonObject o = e.getAsJsonObject();
                    List<String> ch = new ArrayList<>();
                    for (JsonElement c : o.getAsJsonArray("chaves")) ch.add(Texto.limpo(c.getAsString()));
                    l.add(new Topico(o.get("id").getAsString(), ch, o.get("pt").getAsString(), o.get("en").getAsString()));
                }
            }
        } catch (Exception ex) {
            LanternaMod.LOGGER.warn("Lanterna: não deu para ler as dúvidas do anel ({})", ex.toString());
        }
        topicos = l;
        return l;
    }

    /** O assunto que mais bate com a pergunta (frases inteiras valem mais; palavra quase igual vale um pouco). */
    @Nullable
    static Topico topico(String limpo) {
        Topico melhor = null;
        double nota = 0;
        List<String> ws = Texto.palavras(limpo);
        for (Topico t : topicos()) {
            double n = 0;
            for (String c : t.chaves()) {
                int tam = Texto.palavras(c).size();
                if ((" " + limpo + " ").contains(" " + c + " ")) n += 1 + tam * 0.6;
                else if (tam == 1 && c.length() >= 5) {
                    for (String w : ws) if (w.length() >= 5 && (Texto.distancia(w, c) <= 1 || mesmaRaiz(w, c))) { n += c.length() >= 6 ? 1.0 : 0.7; break; }
                } else if (tam > 1 && Texto.semelhanca(limpo, c) >= 0.86) n += 0.8 + tam * 0.4;
            }
            if (n > nota) { nota = n; melhor = t; }
        }
        return nota >= 1 ? melhor : null;
    }

    /** "recarrego" e "recarregar", "fragmentos" e "fragmento": a mesma palavra com outra terminação. */
    private static boolean mesmaRaiz(String a, String b) {
        int n = Math.min(a.length(), b.length());
        if (n < 6) return false;
        int k = 0;
        while (k < n && a.charAt(k) == b.charAt(k)) k++;
        return k >= Math.max(6, n - 2);
    }

    // ===================== receitas =====================

    private static final Map<String, String[]> CORES = Map.of(
            "green", new String[]{"verde", "green"}, "yellow", new String[]{"amarela", "amarelo", "yellow"},
            "red", new String[]{"vermelha", "vermelho", "red"}, "orange", new String[]{"laranja", "orange"},
            "blue", new String[]{"azul", "blue"}, "indigo", new String[]{"indigo"}, "violet", new String[]{"violeta", "violet"},
            "black", new String[]{"negra", "preta", "negro", "preto", "black"}, "white", new String[]{"branca", "branco", "white"});

    /** Nomes pelos quais o item pode ser chamado (o nome do jogo e, nas baterias das Tropas, "bateria verde"). */
    private static List<String> nomes(ItemStack st) {
        List<String> l = new ArrayList<>();
        l.add(assunto(Texto.limpo(st.getHoverName().getString()))); // "lingote de ferro" -> "lingote ferro", igual à pergunta
        var tag = st.getTag();
        if (tag != null && tag.contains("BlockEntityTag") && tag.getCompound("BlockEntityTag").contains("Corps")) {
            String c = new ResourceLocation(tag.getCompound("BlockEntityTag").getString("Corps")).getPath();
            for (String cor : CORES.getOrDefault(c, new String[]{c})) {
                l.add("bateria " + cor);
                l.add("bateria de poder " + cor);
                l.add(cor + " battery");
                l.add(cor + " power battery");
            }
        }
        return l;
    }

    /** O item cujo nome (no idioma do jogo) mais parece com o assunto. Itens do mod e com receita ganham na dúvida. */
    @Nullable
    static ItemStack item(String assunto) {
        ItemStack melhor = null;
        double nota = 0;
        int pal = Texto.palavras(assunto).size();
        List<ItemStack> candidatos = new ArrayList<>();
        Set<Item> comReceita = new HashSet<>();
        var mc = Minecraft.getInstance();
        if (mc.level != null) for (Recipe<?> r : mc.level.getRecipeManager().getRecipes()) {
            ItemStack out = r.getResultItem(mc.level.registryAccess());
            if (out.isEmpty()) continue;
            if (out.hasTag()) candidatos.add(out);
            comReceita.add(out.getItem());
        }
        for (Item it : ForgeRegistries.ITEMS) candidatos.add(new ItemStack(it));
        for (ItemStack st : candidatos) {
            for (String nome : nomes(st)) {
                if (nome.isEmpty()) continue;
                int pn = Texto.palavras(nome).size();
                double s = Math.max(Texto.semelhanca(nome, assunto), Texto.semelhanca(assunto, nome) * (pn >= pal ? 0.97 : 0.9));
                if (nome.equals(assunto)) s = 1.2;
                s -= Math.abs(pn - pal) * 0.04;
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
                if (id != null && id.getNamespace().equals(LanternaMod.MODID)) s += 0.03;
                if (comReceita.contains(st.getItem())) s += 0.04;
                if (st.hasTag()) s += 0.01;
                if (s > nota) { nota = s; melhor = st; }
            }
        }
        return nota >= 0.82 ? melhor : null;
    }

    static Component comoFazer(ItemStack alvo) {
        var mc = Minecraft.getInstance();
        Component nome = alvo.getHoverName();
        if (mc.level == null) return Component.translatable("anel.ia.duvida.sem_receita", nome);
        List<Recipe<?>> rs = new ArrayList<>();
        for (Recipe<?> r : mc.level.getRecipeManager().getRecipes())
            if (mesmo(r.getResultItem(mc.level.registryAccess()), alvo)) rs.add(r);
        if (rs.isEmpty()) {
            // sem receita: talvez um assunto do mod explique (rituais, trocas, chefes...)
            Topico t = topico(Texto.limpo(nome.getString()));
            if (t != null) return Component.literal(VozAnel.idioma().startsWith("pt") ? t.pt() : t.en());
            return Component.translatable("anel.ia.duvida.sem_receita", nome);
        }
        // a receita mais simples primeiro (bancada antes das especiais)
        rs.sort(Comparator.comparingInt(r -> r instanceof CraftingRecipe ? 0 : r instanceof AbstractCookingRecipe ? 1 : 2));
        Recipe<?> r = rs.get(0);
        int qtd = r.getResultItem(mc.level.registryAccess()).getCount();
        Component quanto = qtd > 1 ? Component.translatable("anel.ia.duvida.faz", qtd) : Component.empty();
        Component outras = rs.size() > 1 ? Component.translatable("anel.ia.duvida.outras", rs.size() - 1) : Component.empty();
        if (r instanceof ShapedRecipe sh) {
            List<String> linhas = new ArrayList<>();
            for (int y = 0; y < sh.getHeight(); y++) {
                List<String> l = new ArrayList<>();
                for (int x = 0; x < sh.getWidth(); x++) l.add(nomeDe(sh.getIngredients().get(y * sh.getWidth() + x)));
                linhas.add("[" + String.join(", ", l) + "]");
            }
            return Component.translatable("anel.ia.duvida.bancada", nome, quanto, total(sh.getIngredients()), String.join(" ", linhas), outras);
        }
        if (r instanceof CraftingRecipe c)
            return Component.translatable("anel.ia.duvida.bancada_livre", nome, quanto, total(c.getIngredients()), outras);
        if (r instanceof AbstractCookingRecipe c) {
            String onde = c instanceof BlastingRecipe ? "alto_forno" : c instanceof SmokingRecipe ? "defumador"
                    : c instanceof CampfireCookingRecipe ? "fogueira" : "fornalha";
            return Component.translatable("anel.ia.duvida." + onde, nome, nomeDe(c.getIngredients().get(0)), outras);
        }
        if (r instanceof StonecutterRecipe c)
            return Component.translatable("anel.ia.duvida.cortador", nome, nomeDe(c.getIngredients().get(0)), outras);
        if (r instanceof SmithingRecipe)
            return Component.translatable("anel.ia.duvida.ferraria", nome, outras);
        ResourceLocation tipo = ForgeRegistries.RECIPE_TYPES.getKey(r.getType());
        String ings = r.getIngredients().isEmpty() ? "?" : total(r.getIngredients());
        return Component.translatable("anel.ia.duvida.especial", nome, tipo == null ? "?" : tipo.toString(), ings, outras);
    }

    /** Mesmo item; se o pedido tem NBT (bateria de uma Tropa), o NBT também tem de bater. */
    private static boolean mesmo(ItemStack out, ItemStack alvo) {
        if (!out.is(alvo.getItem())) return false;
        return !alvo.hasTag() || ItemStack.isSameItemSameTags(out, alvo);
    }

    /** "4 Diamante, 4 Mérito e 1 Lingote de Netherite" */
    private static String total(List<Ingredient> ings) {
        Map<String, Integer> conta = new LinkedHashMap<>();
        for (Ingredient i : ings) {
            if (i.isEmpty()) continue;
            conta.merge(nomeDe(i), 1, Integer::sum);
        }
        List<String> partes = new ArrayList<>();
        conta.forEach((k, v) -> partes.add(v + " " + k));
        if (partes.size() <= 1) return String.join("", partes);
        String e = " " + I18n.get("anel.ia.duvida.e") + " ";
        return String.join(", ", partes.subList(0, partes.size() - 1)) + e + partes.get(partes.size() - 1);
    }

    private static String nomeDe(Ingredient i) {
        if (i.isEmpty()) return I18n.get("anel.ia.duvida.vazio");
        ItemStack[] its = i.getItems();
        if (its.length == 0) return "?";
        String n = its[0].getHoverName().getString();
        boolean variado = false;
        for (ItemStack s : its) if (!s.getHoverName().getString().equals(n)) { variado = true; break; }
        return variado ? I18n.get("anel.ia.duvida.qualquer", n) : n;
    }

    // ===================== missões =====================

    @Nullable
    static Mission missaoPeloNome(String assunto) {
        if (assunto.isBlank()) return null;
        Mission melhor = null;
        double nota = 0;
        List<Mission> todas = new ArrayList<>();
        for (String id : ClientLoreData.DATA.active().keySet()) { Mission m = Missions.get(id); if (m != null) todas.add(m); }
        todas.addAll(Missions.all());
        for (Mission m : todas) {
            String t = Texto.limpo(m.title().getString());
            if (t.isEmpty() || t.startsWith("mission lanterna")) continue;
            double s = Math.max(Texto.semelhanca(assunto, t), Texto.semelhanca(t, assunto) * 0.95);
            if (ClientLoreData.DATA.active().containsKey(m.id())) s += 0.05;
            if (m.id().contains(com.lanterna.client.ClientRingData.corps.getPath())) s += 0.03;
            if (s > nota) { nota = s; melhor = m; }
        }
        return nota >= 0.8 ? melhor : null;
    }

    /** Como fazer a missão: o que é, os passos, onde está o próximo e uma dica do tipo de objetivo. */
    static Component guia(Mission m, LocalPlayer p) {
        int[] prog = ClientLoreData.DATA.active().get(m.id());
        boolean ativa = prog != null;
        Objective atual = null;
        List<String> passos = new ArrayList<>();
        for (int i = 0; i < m.objectives().size(); i++) {
            Objective o = m.objectives().get(i);
            int feito = prog != null && i < prog.length ? prog[i] : 0;
            boolean ok = ativa && feito >= o.count();
            if (!ok && atual == null) atual = o;
            passos.add((ok ? "✔ " : (i + 1) + ") ") + o.label(feito).getString());
        }
        Component r = Component.translatable(ativa ? "anel.ia.duvida.missao" : "anel.ia.duvida.missao_nova", m.title(), m.description(), String.join("; ", passos));
        if (atual != null) {
            String dica = "anel.ia.dica." + atual.type().name().toLowerCase(Locale.ROOT);
            if (I18n.exists(dica)) r = r.copy().append(" ").append(Component.translatable(dica));
            Component onde = onde(atual, p);
            if (onde != null) r = r.copy().append(" ").append(onde);
        }
        if (!ativa) r = r.copy().append(" ").append(Component.translatable("anel.ia.duvida.missao_pegar"));
        return r;
    }

    @Nullable
    private static Component onde(Objective o, LocalPlayer p) {
        String pl = o.planet();
        String aqui = p.level().dimension().location().getPath();
        if (pl != null && !pl.isBlank() && !pl.equals("space") && !aqui.equals(pl)) {
            var d = AnelIA.destino(pl, p);
            return Component.translatable("anel.ia.duvida.fica_em", d != null ? d.name() : Component.literal(pl));
        }
        BlockPos pos = o.pos();
        if (pos == null) return null;
        int dist = (int) Math.sqrt(p.blockPosition().distSqr(pos));
        return Component.translatable("anel.ia.duvida.fica_a", dist, pos.getX(), pos.getY(), pos.getZ());
    }

    private Duvidas() {}
}
