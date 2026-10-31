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

    private static int ok(CommandContext<CommandSourceStack> c, String texto) {
        c.getSource().sendSuccess(() -> Component.literal("✔ " + texto).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int erro(CommandContext<CommandSourceStack> c, String texto) {
        c.getSource().sendFailure(Component.literal(texto));
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
                    if (r == null || !r.hasRing()) return erro(c, "Sem anel.");
                    if (!r.powerActive()) RingPowers.togglePower(p);
                    RingPowers.setCharge(p, r, r.maxCharge());
                    Constructs.zerarRecargas(p.getUUID());
                    dados(p).setPortalCd(0);
                    return ok(c, "Carga cheia, poder ligado, recargas e espera da Porta Estelar zeradas.");
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
                    return ok(c, "Nível " + n + ".");
                })))
                .then(op("afinidade_nivel").then(Commands.argument("n", IntegerArgumentType.integer(0, Affinity.MAX_LEVEL)).executes(c -> {
                    ServerPlayer p = eu(c);
                    ItemStack ring = EquipmentHelper.ring(p);
                    if (ring.isEmpty()) return erro(c, "Equipe um anel primeiro.");
                    int n = IntegerArgumentType.getInteger(c, "n");
                    Affinity.add(p, Affinity.THRESHOLDS[n] - Affinity.points(ring));
                    return ok(c, "Afinidade do anel: " + Affinity.levelName(n).getString() + ".");
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
                                if (!CidadeMissoes.TECNICAS.contains(t)) return erro(c, "Técnica desconhecida: " + t);
                                Tecnicas.desbloquear(p, dd, t);
                                dd.completed().add("prov_" + t);
                            }
                            sync(p);
                            return ok(c, "Técnica(s) liberada(s): " + String.join(", ", l) + ". Abra com O.");
                        })))
                .then(op("poder").then(Commands.argument("qual", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"portal", "autorecarga", "honra", "genese", "tudo"}, b))
                        .executes(c -> poder(c, StringArgumentType.getString(c, "qual")))))
                .then(op("morte").then(Commands.argument("acao", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"corromper", "dominar", "faminto", "alimentar", "limpar"}, b))
                        .executes(c -> morte(c, StringArgumentType.getString(c, "acao")))))

                // ---------- missões ----------
                .then(op("missao_iniciar").then(argMissao().executes(c -> {
                    MissionManager.start(eu(c), StringArgumentType.getString(c, "id"));
                    return ok(c, "Missão iniciada.");
                })))
                .then(op("missao_concluir").then(argMissao().executes(c -> MissionManager.forcarConclusao(eu(c), StringArgumentType.getString(c, "id"))
                        ? ok(c, "Missão concluída (com recompensas e a próxima da linha).") : erro(c, "Missão desconhecida."))))
                .then(op("missao_abandonar").then(argMissao().executes(c -> {
                    ServerPlayer p = eu(c);
                    dados(p).abandon(StringArgumentType.getString(c, "id"));
                    sync(p);
                    return ok(c, "Missão abandonada.");
                })))
                .then(op("missao_limpar").executes(c -> {
                    ServerPlayer p = eu(c);
                    LoreData dd = dados(p);
                    for (String id : new ArrayList<>(dd.active().keySet())) dd.abandon(id);
                    sync(p);
                    return ok(c, "Todas as missões ativas foram abandonadas.");
                }))
                .then(op("missao_concluidas_limpar").executes(c -> {
                    ServerPlayer p = eu(c);
                    dados(p).completed().clear();
                    sync(p);
                    return ok(c, "Histórico de missões concluídas apagado (dá para refazer tudo).");
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
                            return ok(c, "Marcado como visitado: " + id + " (aparece na Porta Estelar).");
                        })))
                .then(op("tropa_hostil").then(Commands.argument("tropa", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(lista(Arrays.asList(TROPAS), "limpar"), b))
                        .executes(c -> {
                            ServerPlayer p = eu(c);
                            String t = StringArgumentType.getString(c, "tropa");
                            LoreData dd = dados(p);
                            if (t.equals("limpar")) dd.tropasHostis().clear();
                            else dd.addTropaHostil(t);
                            return ok(c, t.equals("limpar") ? "Nenhuma Tropa é sua inimiga agora." : "A Tropa " + t + " agora é sua inimiga.");
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
        if (t == null) return erro(c, "Tropa desconhecida: " + id);
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
        return ok(c, "Anel " + id + " equipado" + (velho.isEmpty() ? "." : " (o anel antigo foi para o inventário)."));
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
                return erro(c, "Use: portal, autorecarga, honra, genese ou tudo.");
            }
        }
        sync(p);
        return ok(c, switch (qual) {
            case "portal" -> "Porta Estelar liberada (tecla N).";
            case "autorecarga" -> "O anel agora se recarrega sozinho (recompensa da Prova da Emoção).";
            case "honra" -> "Guarda de Honra: posto e 3º espaço de técnica.";
            case "genese" -> "Gênese revelada no espaço.";
            default -> "Tudo liberado: nível 50, afinidade máxima, todas as técnicas, Guarda de Honra, Porta Estelar, autorrecarga, Gênese e todos os planetas visitados.";
        });
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
                return erro(c, "Use: corromper, dominar, faminto, alimentar ou limpar.");
            }
        }
        sync(p);
        return ok(c, switch (acao) {
            case "corromper" -> "Anel corrompido: a linha \"A Morte\" começou.";
            case "dominar" -> "Você domina a Morte (pode forjar/usar o Anel Preto; use /lanterna anel black para equipar).";
            case "faminto" -> "O Lanterna Negro agora está faminto (dano reduzido).";
            case "alimentar" -> "A fome do Lanterna Negro foi saciada.";
            default -> "Corrupção removida.";
        });
    }

    private static int invocar(CommandContext<CommandSourceStack> c, String oQue, int qtd) throws CommandSyntaxException {
        ServerPlayer p = eu(c);
        ServerLevel level = p.serverLevel();
        Vec3 frente = p.position().add(Vec3.directionFromRotation(0, p.getYRot()).scale(4));
        int feitos = 0;
        for (int i = 0; i < qtd; i++) {
            Entity e = criar(level, oQue);
            if (e == null) return erro(c, "Não sei invocar: " + oQue);
            double a = i * 2.4;
            e.moveTo(frente.x + Math.cos(a) * (i == 0 ? 0 : 1.5), frente.y, frente.z + Math.sin(a) * (i == 0 ? 0 : 1.5), p.getYRot() + 180, 0);
            if (e instanceof Mob m && !(e instanceof LanternaEntity))
                m.finalizeSpawn(level, level.getCurrentDifficultyAt(e.blockPosition()), MobSpawnType.COMMAND, null, null);
            level.addFreshEntity(e);
            feitos++;
        }
        return ok(c, "Invocado: " + oQue + " x" + feitos + ".");
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
                return ok(c, "Nave " + tipo + " (" + pintura + ") no inventário. Clique no chão para colocar.");
            }
        }
        return erro(c, "Nave desconhecida: " + tipo);
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
        if (!tudo && !List.of("aneis", "materiais", "reliquias", "guilda", "naves").contains(qual)) return erro(c, "Kits: aneis, materiais, reliquias, guilda, naves, tudo.");
        return ok(c, "Kit " + qual + " no inventário.");
    }

    private static Item item(String id) {
        Item i = ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.tryParse(id));
        return i == null ? Items.AIR : i;
    }

    // ===================== Ajuda =====================

    private static int ajuda(CommandContext<CommandSourceStack> c) {
        String[][] linhas = {
                {"ANEL E NÍVEL", ""},
                {"/lanterna anel <tropa>", "equipa um anel (green, yellow, red, blue, violet, indigo, orange, black, white)"},
                {"/lanterna cheio", "carga cheia, poder ligado, zera recargas e a espera da Porta"},
                {"/lanterna nivel <1-50>", "muda o nível da Tropa (dá as recompensas de posto ao subir)"},
                {"/lanterna afinidade_nivel <0-5>", "afinidade do anel equipado (libera construtos)"},
                {"/lanterna escolher <tropa>", "o anel escolhe você, com a animação"},
                {"PODERES", ""},
                {"/lanterna tecnica <id|todas>", "técnicas dos Capitães (olhar, bastiao, onda, salto, armadura, comando)"},
                {"/lanterna poder <portal|autorecarga|honra|genese|tudo>", "Porta Estelar, autorrecarga da Prova, Guarda de Honra, Gênese, ou tudo"},
                {"/lanterna morte <corromper|dominar|faminto|alimentar|limpar>", "linha da Morte e o Anel Preto"},
                {"MISSÕES", ""},
                {"/lanterna missao_iniciar <id>", "começa uma missão"},
                {"/lanterna missao_concluir <id>", "conclui na hora (recompensas e próxima da linha)"},
                {"/lanterna missao_abandonar <id> | missao_limpar | missao_concluidas_limpar", ""},
                {"MUNDO", ""},
                {"/lanterna visitar <planeta|todos>", "marca planetas como visitados (Porta Estelar)"},
                {"/lanterna tropa_hostil <tropa|limpar>", "inimizade com uma Tropa"},
                {"/lanterna genese | reconstruir | encontro <tipo> | contratos | interior", ""},
                {"INVOCAR E ITENS", ""},
                {"/lanterna invocar <o_que> [qtd]", "pirata, capitao, mercador, eco, alvo_treino, lanterna_<tropa>, oficial_<tropa>, boss_<nome>"},
                {"/lanterna nave <tipo> [pintura]", "item de nave"},
                {"/lanterna kit <aneis|materiais|reliquias|guilda|naves|tudo>", ""},
                {"/guilda admin tropas <n> | defesa <n> | sem_escudo", "guildas"},
        };
        for (String[] l : linhas) {
            if (l[1].isEmpty() && !l[0].startsWith("/")) {
                c.getSource().sendSuccess(() -> Component.literal("— " + l[0] + " —").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
            } else {
                c.getSource().sendSuccess(() -> Component.literal(l[0]).withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(l[1].isEmpty() ? "" : "  " + l[1]).withStyle(ChatFormatting.GRAY)), false);
            }
        }
        return 1;
    }

    private ComandosTeste() {}
}
