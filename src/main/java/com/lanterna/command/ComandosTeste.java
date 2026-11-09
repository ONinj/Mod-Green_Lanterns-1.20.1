package com.lanterna.command;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.cidade.CidadeMissoes;
import com.lanterna.cidade.Tecnicas;
import com.lanterna.construct.Affinity;
import com.lanterna.construct.Constructs;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.item.PowerRingItem;
import com.lanterna.lore.LoreEntities;
import com.lanterna.lore.LoreItems;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.LoreCapability;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.Mission;
import com.lanterna.mission.MissionManager;
import com.lanterna.mission.Missions;
import com.lanterna.naves.NaveItem;
import com.lanterna.naves.NaveTipo;
import com.lanterna.power.EquipmentHelper;
import com.lanterna.power.RingPowers;
import com.lanterna.space.SpaceBodies;
import com.lanterna.tropa.Nivel;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Comandos de teste (só operador / cheats ligados): tudo o que o jogo entrega pela história, conquistas e missões,
 * para poder testar sem jogar a história. Digite /lanterna ajuda para ver a lista.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ComandosTeste {

    private static final String[] TROPAS = Arrays.stream(BuiltinCorps.values()).map(c -> c.id().getPath()).toArray(String[]::new);

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> op(String nome) {
        return Commands.literal(nome).requires(s -> s.hasPermission(2));
    }

    private static ServerPlayer eu(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return c.getSource().getPlayerOrException();
    }

    private static LoreData dados(ServerPlayer p) {
        return LoreCapability.get(p).orElseThrow(() -> new IllegalStateException("sem dados"));
    }

    /** Resposta de sucesso: a chave é traduzida no cliente (cmd.lanterna.<chave>). */
    private static int ok(CommandContext<CommandSourceStack> c, String chave, Object... args) {
        c.getSource().sendSuccess(() -> Component.literal("✔ ").append(Component.translatable("cmd.lanterna." + chave, args)).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int erro(CommandContext<CommandSourceStack> c, String chave, Object... args) {
        c.getSource().sendFailure(Component.translatable("cmd.lanterna." + chave, args));
        return 0;
    }

    private static BuiltinCorps tropa(String id) {
        for (BuiltinCorps b : BuiltinCorps.values()) if (b.id().getPath().equals(id)) return b;
        return null;
    }

    private static void dar(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    private static void sync(ServerPlayer p) {
        MissionManager.sync(p, dados(p));
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("lanterna")
                .then(Commands.literal("ajuda").executes(ComandosTeste::ajuda))

                // ---------- anel ----------
                .then(op("anel").then(Commands.argument("tropa", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(TROPAS, b))
                        .executes(c -> anel(c, StringArgumentType.getString(c, "tropa")))))
                .then(op("cheio").executes(c -> {
                    ServerPlayer p = eu(c);
                    RingData r = ModCapabilities.get(p).orElse(null);
                    if (r == null || !r.hasRing()) return erro(c, "sem_anel");
                    if (!r.powerActive()) RingPowers.togglePower(p);
                    RingPowers.setCharge(p, r, r.maxCharge());
                    Constructs.zerarRecargas(p.getUUID());
                    dados(p).setPortalCd(0);
                    return ok(c, "cheio");
                }))
                // tudo no máximo para testes: nível, afinidade, técnicas, poderes, Fonte (selos, árvores, Essência, Corações) e carga 250%
                .then(op("maximo").executes(c -> {
                    ServerPlayer p = eu(c);
                    RingData r0 = ModCapabilities.get(p).orElse(null);
                    if (r0 == null || !r0.hasRing()) return erro(c, "equipe_anel");
                    poder(c, "tudo");
                    for (var t : com.lanterna.baterias.Tropa.values()) {
                        com.lanterna.fonte.FonteDados.darSelo(p, t.nome);
                        com.lanterna.fonte.FonteDados.tag(p).putBoolean("coracao:" + t.nome, true);
                        for (int i = 1; i <= 3; i++) com.lanterna.fonte.FonteDados.chamado(p, t.nome + (i == 1 ? ":chamado" : i == 2 ? ":prova" : ":juramento"), 1);
                    }
                    for (var e : com.lanterna.construct.fonte.ArvoreDados.ARVORES.entrySet())
                        for (var no : e.getValue()) com.lanterna.fonte.FonteDados.darNo(p, e.getKey() + ":" + no.id());
                    com.lanterna.fonte.FonteDados.essencia(p, 9999);
                    com.lanterna.fonte.FonteDados.pressao(p, 0);
                    com.lanterna.fonte.FonteDados.bloqueado(p, false);
                    RingData r = ModCapabilities.get(p).orElse(null);
                    if (r != null) {
                        if (!r.powerActive()) RingPowers.togglePower(p);
                        RingPowers.setCharge(p, r, r.maxCharge());
                        com.lanterna.fonte.FonteDados.sobrecarga(p, Math.round(r.maxCharge() * com.lanterna.fonte.Fonte.SOBRECARGA));
                    }
                    Constructs.zerarRecargas(p.getUUID());
                    sync(p);
                    return ok(c, "maximo", Nivel.MAX);
                }))
                .then(op("nivel").then(Commands.argument("n", IntegerArgumentType.integer(1, Nivel.MAX)).executes(c -> {
                    ServerPlayer p = eu(c);
                    int n = IntegerArgumentType.getInteger(c, "n");
                    LoreData dd = dados(p);
                    int alvo = Nivel.total(n);
                    if (alvo > dd.xp()) Nivel.addXp(p, alvo - dd.xp(), Component.literal("comando"));
                    else {
                        dd.setXp(alvo);
                        Nivel.aplicar(p);
                        sync(p);
                    }
                    return ok(c, "nivel", n);
                })))
                .then(op("afinidade_nivel").then(Commands.argument("n", IntegerArgumentType.integer(0, Affinity.MAX_LEVEL)).executes(c -> {
                    ServerPlayer p = eu(c);
                    ItemStack ring = EquipmentHelper.ring(p);
                    if (ring.isEmpty()) return erro(c, "equipe_anel");
                    int n = IntegerArgumentType.getInteger(c, "n");
                    Affinity.add(p, Affinity.THRESHOLDS[n] - Affinity.points(ring));
                    return ok(c, "afinidade", Affinity.levelName(n));
                })))

                // ---------- poderes ----------
                .then(op("tecnica").then(Commands.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(lista(CidadeMissoes.TECNICAS, "todas"), b))
                        .executes(c -> {
                            ServerPlayer p = eu(c);
                            String id = StringArgumentType.getString(c, "id");
                            LoreData dd = dados(p);
                            List<String> l = id.equals("todas") ? CidadeMissoes.TECNICAS : List.of(id);
                            for (String t : l) {
                                if (!CidadeMissoes.TECNICAS.contains(t)) return erro(c, "tecnica_desconhecida", t);
                                Tecnicas.desbloquear(p, dd, t);
                                dd.completed().add("prov_" + t);
                            }
                            sync(p);
                            return ok(c, "tecnicas", String.join(", ", l));
                        })))
                // ---------- a Fonte (Baterias Centrais) ----------
                .then(op("fonte").then(Commands.argument("acao", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"selo", "pressao", "banho", "essencia", "prova", "juramento", "arvore", "zerar", "evento", "chefe", "ir"}, b))
                        .executes(c -> fonte(c, StringArgumentType.getString(c, "acao"), "", 0))
                        .then(Commands.argument("valor", StringArgumentType.word())
                                .executes(c -> fonte(c, StringArgumentType.getString(c, "acao"), StringArgumentType.getString(c, "valor"), 0)))))
                .then(op("poder").then(Commands.argument("qual", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"portal", "autorecarga", "honra", "genese", "tudo"}, b))
                        .executes(c -> poder(c, StringArgumentType.getString(c, "qual")))))
                .then(op("morte").then(Commands.argument("acao", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"corromper", "dominar", "faminto", "alimentar", "limpar"}, b))
                        .executes(c -> morte(c, StringArgumentType.getString(c, "acao")))))

                // ---------- missões ----------
                .then(op("missao_iniciar").then(argMissao().executes(c -> {
                    MissionManager.start(eu(c), StringArgumentType.getString(c, "id"));
                    return ok(c, "missao_iniciada");
                })))
                .then(op("missao_concluir").then(argMissao().executes(c -> MissionManager.forcarConclusao(eu(c), StringArgumentType.getString(c, "id"))
                        ? ok(c, "missao_concluida") : erro(c, "missao_desconhecida"))))
                .then(op("missao_abandonar").then(argMissao().executes(c -> {
                    ServerPlayer p = eu(c);
                    dados(p).abandon(StringArgumentType.getString(c, "id"));
                    sync(p);
                    return ok(c, "missao_abandonada");
                })))
                .then(op("missao_limpar").executes(c -> {
                    ServerPlayer p = eu(c);
                    LoreData dd = dados(p);
                    for (String id : new ArrayList<>(dd.active().keySet())) dd.abandon(id);
                    sync(p);
                    return ok(c, "missoes_limpas");
                }))
                .then(op("missao_concluidas_limpar").executes(c -> {
                    ServerPlayer p = eu(c);
                    dados(p).completed().clear();
                    sync(p);
                    return ok(c, "concluidas_limpas");
                }))

                // ---------- mundo ----------
                .then(op("visitar").then(Commands.argument("planeta", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(lista(planetas(), "todos"), b))
                        .executes(c -> {
                            ServerPlayer p = eu(c);
                            String id = StringArgumentType.getString(c, "planeta");
                            LoreData dd = dados(p);
                            for (String pl : id.equals("todos") ? planetas() : List.of(id)) dd.visitar(pl);
                            sync(p);
                            return ok(c, "visitado", id);
                        })))
                .then(op("tropa_hostil").then(Commands.argument("tropa", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(lista(Arrays.asList(TROPAS), "limpar"), b))
                        .executes(c -> {
                            ServerPlayer p = eu(c);
                            String t = StringArgumentType.getString(c, "tropa");
                            LoreData dd = dados(p);
                            if (t.equals("limpar")) dd.tropasHostis().clear();
                            else dd.addTropaHostil(t);
                            return (t.equals("limpar") ? ok(c, "hostil_limpo") : ok(c, "hostil", t));
                        })))

                // ---------- invocar ----------
                .then(op("invocar").then(Commands.argument("o_que", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(invocaveis(), b))
                        .executes(c -> invocar(c, StringArgumentType.getString(c, "o_que"), 1))
                        .then(Commands.argument("qtd", IntegerArgumentType.integer(1, 50))
                                .executes(c -> invocar(c, StringArgumentType.getString(c, "o_que"), IntegerArgumentType.getInteger(c, "qtd"))))))
                .then(op("nave").then(Commands.argument("tipo", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(NaveTipo.values()).map(t -> t.id), b))
                        .executes(c -> nave(c, StringArgumentType.getString(c, "tipo"), "green"))
                        .then(Commands.argument("pintura", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(lista(Arrays.asList(TROPAS), "1", "2"), b))
                                .executes(c -> nave(c, StringArgumentType.getString(c, "tipo"), StringArgumentType.getString(c, "pintura"))))))

                // ---------- itens ----------
                .then(op("kit").then(Commands.argument("qual", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"aneis", "materiais", "reliquias", "guilda", "naves", "tudo"}, b))
                        .executes(c -> kit(c, StringArgumentType.getString(c, "qual"))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> argMissao() {
        return Commands.argument("id", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Missions.all().stream().map(Mission::id), b));
    }

    private static List<String> lista(List<String> base, String... extra) {
        List<String> l = new ArrayList<>(base);
        l.addAll(Arrays.asList(extra));
        return l;
    }

    private static List<String> planetas() {
        List<String> l = new ArrayList<>();
        for (SpaceBodies.Body b : SpaceBodies.BODIES) if (b.landable()) l.add(b.id());
        return l;
    }

    private static List<String> invocaveis() {
        List<String> l = new ArrayList<>(List.of("pirata", "capitao", "mercador", "eco", "alvo_treino", "broto"));
        for (String t : TROPAS) {
            l.add("lanterna_" + t);
            l.add("oficial_" + t);
        }
        for (String b : LoreEntities.bosses().keySet()) l.add("boss_" + b);
        return l;
    }

    // ===================== Ações =====================

    private static int anel(CommandContext<CommandSourceStack> c, String id) throws CommandSyntaxException {
        ServerPlayer p = eu(c);
        BuiltinCorps t = tropa(id);
        if (t == null) return erro(c, "tropa_desconhecida", id);
        ItemStack velho = EquipmentHelper.take(p, RingData.RING_SLOT);
        if (!velho.isEmpty()) dar(p, velho);
        ItemStack novo = PowerRingItem.create(t.id(), t.color(), -1);
        PowerRingItem.setOwner(novo, p);
        EquipmentHelper.equip(p, RingData.RING_SLOT, novo);
        LoreData dd = dados(p);
        // o Preto e o Branco contam como dominados (sem precisar da história)
        if (t == BuiltinCorps.BLACK && dd.morte() < 3) {
            dd.setMorte(3);
            dd.setUltimaCeifa(System.currentTimeMillis());
        }
        return ok(c, velho.isEmpty() ? "anel" : "anel_troca", id);
    }

    private static int poder(CommandContext<CommandSourceStack> c, String qual) throws CommandSyntaxException {
        ServerPlayer p = eu(c);
        LoreData dd = dados(p);
        switch (qual) {
            case "portal" -> {
                dd.setPortal(true);
                dd.setPortalCd(0);
                dd.completed().addAll(List.of("portal_1", "portal_2", "portal_3"));
            }
            case "autorecarga" -> dd.setAutoRecarga(true);
            case "honra" -> {
                if (!dd.honra()) Tecnicas.honra(p, dd);
            }
            case "genese" -> dd.setGeneseRevealed(true);
            case "tudo" -> {
                int alvo = Nivel.total(Nivel.MAX);
                if (alvo > dd.xp()) Nivel.addXp(p, alvo - dd.xp(), Component.literal("comando"));
                ItemStack ring = EquipmentHelper.ring(p);
                if (!ring.isEmpty()) Affinity.add(p, Affinity.THRESHOLDS[Affinity.MAX_LEVEL] - Affinity.points(ring));
                for (String t : CidadeMissoes.TECNICAS) {
                    Tecnicas.desbloquear(p, dd, t);
                    dd.completed().add("prov_" + t);
                }
                if (!dd.honra()) Tecnicas.honra(p, dd);
                dd.setPortal(true);
                dd.setPortalCd(0);
                dd.setAutoRecarga(true);
                dd.setGeneseRevealed(true);
                for (String pl : planetas()) dd.visitar(pl);
                RingData r = ModCapabilities.get(p).orElse(null);
                if (r != null && r.hasRing()) {
                    if (!r.powerActive()) RingPowers.togglePower(p);
                    RingPowers.setCharge(p, r, r.maxCharge());
                }
                Constructs.zerarRecargas(p.getUUID());
            }
            default -> {
                return erro(c, "uso_poder");
            }
        }
        sync(p);
        return ok(c, "poder." + (List.of("portal", "autorecarga", "honra", "genese").contains(qual) ? qual : "tudo"));
    }

    private static int morte(CommandContext<CommandSourceStack> c, String acao) throws CommandSyntaxException {
        ServerPlayer p = eu(c);
        LoreData dd = dados(p);
        switch (acao) {
            case "corromper" -> {
                dd.setMorte(0);
                dd.completed().removeIf(x -> x.startsWith("morte_"));
                com.lanterna.morte.Corrupcao.corromper(p, dd);
            }
            case "dominar" -> {
                dd.setMorte(3);
                dd.setUltimaCeifa(System.currentTimeMillis());
                MissionManager.start(p, "morte_ceifa");
            }
            case "faminto" -> dd.setUltimaCeifa(0);
            case "alimentar" -> dd.setUltimaCeifa(System.currentTimeMillis());
            case "limpar" -> {
                dd.setMorte(0);
                for (String id : new ArrayList<>(dd.active().keySet())) if (id.startsWith("morte_")) dd.abandon(id);
            }
            default -> {
                return erro(c, "uso_morte");
            }
        }
        sync(p);
        return ok(c, "morte." + (List.of("corromper", "dominar", "faminto", "alimentar").contains(acao) ? acao : "limpar"));
    }

    private static int invocar(CommandContext<CommandSourceStack> c, String oQue, int qtd) throws CommandSyntaxException {
        ServerPlayer p = eu(c);
        ServerLevel level = p.serverLevel();
        Vec3 frente = p.position().add(Vec3.directionFromRotation(0, p.getYRot()).scale(4));
        int feitos = 0;
        for (int i = 0; i < qtd; i++) {
            Entity e = criar(level, oQue);
            if (e == null) return erro(c, "invocar_desconhecido", oQue);
            double a = i * 2.4;
            e.moveTo(frente.x + Math.cos(a) * (i == 0 ? 0 : 1.5), frente.y, frente.z + Math.sin(a) * (i == 0 ? 0 : 1.5), p.getYRot() + 180, 0);
            if (e instanceof Mob m && !(e instanceof LanternaEntity))
                m.finalizeSpawn(level, level.getCurrentDifficultyAt(e.blockPosition()), MobSpawnType.COMMAND, null, null);
            level.addFreshEntity(e);
            feitos++;
        }
        return ok(c, "invocado", oQue, feitos);
    }

    private static Entity criar(ServerLevel level, String oQue) {
        switch (oQue) {
            case "pirata": return LanternaEntity.pirata(level, false);
            case "capitao": return LanternaEntity.pirata(level, true);
            case "mercador": return LanternaEntity.mercador(level);
            case "eco": return LoreEntities.ECO_CINZENTO.get().create(level);
            case "alvo_treino": return LoreEntities.ALVO_TREINO.get().create(level);
            case "broto": return LoreEntities.BROTO.get().create(level);
            default: break;
        }
        if (oQue.startsWith("lanterna_") || oQue.startsWith("oficial_")) {
            BuiltinCorps t = tropa(oQue.substring(oQue.indexOf('_') + 1));
            return t == null ? null : LanternaEntity.lanterna(level, t, oQue.startsWith("oficial_"));
        }
        if (oQue.startsWith("boss_")) {
            EntityType<?> t = LoreEntities.bossType(oQue.substring(5));
            return t == null ? null : t.create(level);
        }
        return null;
    }

    private static int nave(CommandContext<CommandSourceStack> c, String tipo, String pintura) throws CommandSyntaxException {
        for (NaveTipo t : NaveTipo.values()) {
            if (t.id.equals(tipo)) {
                dar(eu(c), NaveItem.of(t, pintura));
                return ok(c, "nave", tipo, pintura);
            }
        }
        return erro(c, "nave_desconhecida", tipo);
    }

    private static int kit(CommandContext<CommandSourceStack> c, String qual) throws CommandSyntaxException {
        ServerPlayer p = eu(c);
        boolean tudo = qual.equals("tudo");
        if (tudo || qual.equals("aneis")) {
            for (BuiltinCorps t : BuiltinCorps.values()) dar(p, PowerRingItem.create(t.id(), t.color(), -1));
            dar(p, new ItemStack(LoreItems.ANEL_VAZIO.get(), 8));
            dar(p, com.lanterna.item.DormantRingItem.create());
        }
        if (tudo || qual.equals("materiais")) {
            for (PlanetLore pl : PlanetLore.values()) {
                if (pl.ore != null) dar(p, new ItemStack(item("lanterna:" + pl.ore), 16));
            }
            dar(p, new ItemStack(LoreItems.MERITO.get(), 64));
            dar(p, new ItemStack(LoreItems.ALMA.get(), 64));
            dar(p, new ItemStack(LoreItems.GRATIDAO.get(), 32));
            dar(p, new ItemStack(Items.DIAMOND, 64));
            dar(p, new ItemStack(Items.NETHERITE_INGOT, 8));
            dar(p, new ItemStack(Items.GOLD_INGOT, 64));
        }
        if (tudo || qual.equals("reliquias")) {
            for (PlanetLore pl : PlanetLore.values()) dar(p, new ItemStack(pl.dropItem(), 2));
            dar(p, new ItemStack(LoreItems.RELIQUIA.get(), 8));
        }
        if (tudo || qual.equals("guilda")) {
            dar(p, new ItemStack(com.lanterna.guilda.GuildaItems.BANDEIRA_ITEM.get(), 2));
            dar(p, new ItemStack(com.lanterna.guilda.GuildaItems.ESSENCIA.get(), 64));
            dar(p, new ItemStack(com.lanterna.guilda.GuildaItems.ESSENCIA.get(), 64));
            dar(p, new ItemStack(com.lanterna.guilda.GuildaItems.ANEL_RECRUTA.get(), 16));
        }
        if (tudo || qual.equals("naves")) {
            for (NaveTipo t : NaveTipo.values()) dar(p, NaveItem.of(t, t.name().contains("PIRATA") || t.name().contains("CARGUEIRO") ? "1" : "green"));
        }
        if (!tudo && !List.of("aneis", "materiais", "reliquias", "guilda", "naves").contains(qual)) return erro(c, "uso_kit");
        return ok(c, "kit", qual);
    }

    private static Item item(String id) {
        Item i = ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.tryParse(id));
        return i == null ? Items.AIR : i;
    }

    // ===================== Ajuda =====================

    private static int ajuda(CommandContext<CommandSourceStack> c) {
        // {comando, chave da explicação}; sem comando = título da seção
        String[][] linhas = {
                {"", "ajuda.t0"},
                {"/lanterna anel <tropa>", "ajuda.1"},
                {"/lanterna cheio", "ajuda.2"},
                {"/lanterna nivel <1-50>", "ajuda.3"},
                {"/lanterna afinidade_nivel <0-5>", "ajuda.4"},
                {"/lanterna escolher <tropa>", "ajuda.5"},
                {"", "ajuda.t6"},
                {"/lanterna tecnica <id|todas>", "ajuda.7"},
                {"/lanterna maximo", "ajuda.8"},
                {"/lanterna poder <portal|autorecarga|honra|genese|tudo>", "ajuda.9"},
                {"/lanterna morte <corromper|dominar|faminto|alimentar|limpar>", "ajuda.10"},
                {"", "ajuda.t11"},
                {"/lanterna missao_iniciar <id>", "ajuda.12"},
                {"/lanterna missao_concluir <id>", "ajuda.13"},
                {"/lanterna missao_abandonar <id> | missao_limpar | missao_concluidas_limpar", ""},
                {"", "ajuda.t15"},
                {"/lanterna visitar <planeta|todos>", "ajuda.16"},
                {"/lanterna tropa_hostil <tropa|limpar>", "ajuda.17"},
                {"/lanterna genese | reconstruir | encontro <tipo> | contratos | interior", ""},
                {"", "ajuda.t19"},
                {"/lanterna invocar <o_que> [qtd]", "ajuda.20"},
                {"/lanterna nave <tipo> [pintura]", "ajuda.21"},
                {"/lanterna kit <aneis|materiais|reliquias|guilda|naves|tudo>", ""},
                {"/guilda admin tropas <n> | defesa <n> | sem_escudo", "ajuda.23"},
        };
        for (String[] l : linhas) {
            if (l[0].isEmpty()) {
                c.getSource().sendSuccess(() -> Component.literal("— ").append(Component.translatable("cmd.lanterna." + l[1])).append(" —").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
            } else {
                c.getSource().sendSuccess(() -> Component.literal(l[0]).withStyle(ChatFormatting.AQUA)
                        .append(l[1].isEmpty() ? Component.empty() : Component.literal("  ").append(Component.translatable("cmd.lanterna." + l[1])).withStyle(ChatFormatting.GRAY)), false);
            }
        }
        return 1;
    }

    private ComandosTeste() {}

    /** /lanterna fonte selo <tropa|todas> | pressao <min> | banho <min> | essencia <n> | prova | juramento | zerar */
    private static int fonte(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c, String acao, String valor, int n0)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = eu(c);
        int n = 0;
        try { n = Integer.parseInt(valor); } catch (NumberFormatException ignored) {}
        var t = com.lanterna.fonte.FonteChamado.tropaDoAnel(p);
        switch (acao) {
            case "selo" -> {
                for (var x : com.lanterna.baterias.Tropa.values())
                    if (valor.equals("todas") || valor.equals(x.nome) || (valor.isEmpty() && x == t)) com.lanterna.fonte.FonteDados.darSelo(p, x.nome);
                return ok(c, "selo", valor.isEmpty() ? (t == null ? "-" : t.nome) : valor);
            }
            case "pressao" -> { com.lanterna.fonte.FonteDados.pressao(p, n * 20 * 60); return ok(c, "pressao", n); }
            case "banho" -> { com.lanterna.fonte.FonteDados.banho(p, n * 20 * 60); com.lanterna.fonte.FonteDados.banhoFeito(p, false); return ok(c, "banho", n); }
            case "essencia" -> { com.lanterna.fonte.FonteDados.essencia(p, n); return ok(c, "essencia", n); }
            case "arvore" -> {
                String corps = com.lanterna.construct.fonte.ArvoreDaLuz.corps(p);
                var arv = corps == null ? null : com.lanterna.construct.fonte.ArvoreDados.ARVORES.get(corps);
                if (arv == null) return erro(c, "sem_arvore");
                if (valor.equals("zerar")) {
                    var set = com.lanterna.fonte.FonteDados.arvore(p);
                    set.removeIf(x -> x.startsWith(corps + ":"));
                    com.lanterna.fonte.FonteDados.zerarArvore(p);
                    for (String x : set) com.lanterna.fonte.FonteDados.darNo(p, x);
                    return ok(c, "arvore_zerada", corps);
                }
                for (var no : arv) com.lanterna.fonte.FonteDados.darNo(p, corps + ":" + no.id());
                return ok(c, "arvore_completa", corps, arv.size());
            }
            case "evento" -> {
                return com.lanterna.fonte.BateriaEnfraquece.comecar(p.server, p) ? ok(c, "bateria_ataque")
                        : erro(c, "bateria_sem");
            }
            case "chefe" -> {
                com.lanterna.fonte.FonteMundo.DERROTA.clear();
                return ok(c, "coracao");
            }
            case "ir" -> {
                var alvo = t == null ? null : p.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, LanternaMod.id("fonte_" + t.nome)));
                if (alvo == null) return erro(c, "anel_bateria");
                int z = valor.equals("ilha") ? com.lanterna.fonte.FonteMundo.ILHA.getZ() - 6 : 5;
                p.teleportTo(alvo, 0.5, 101, z + 0.5, 0, 0);
                return ok(c, "na_fonte", t.nome);
            }
            case "zerar" -> {
                var tag = com.lanterna.fonte.FonteDados.tag(p);
                for (String k : new java.util.ArrayList<>(tag.getAllKeys())) tag.remove(k);
                return ok(c, "fonte_zerada");
            }
            case "prova", "juramento" -> {
                if (t == null) return erro(c, "anel_bateria");
                LoreData d = dados(p);
                for (int i = 1; i <= 3; i++) d.abandon("fonte_" + t.nome + "_" + i);
                com.lanterna.mission.MissionManager.start(p, "fonte_" + t.nome + (acao.equals("prova") ? "_2" : "_3"));
                sync(p);
                return ok(c, acao.equals("prova") ? "fonte_prova" : "fonte_juramento", t.nome);
            }
            default -> { return erro(c, "acao_desconhecida"); }
        }
    }
}
