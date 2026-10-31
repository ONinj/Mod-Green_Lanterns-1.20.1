package com.lanterna.guilda;

import com.lanterna.LanternaConfig;
import com.lanterna.lore.Factions;
import com.lanterna.network.ModNetwork;
import com.lanterna.space.SpaceBodies;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;

/**
 * /guilda criar|convidar|entrar|sair|expulsar|desfazer|info|lista|simbolo|abandonar|tropas
 */
@Mod.EventBusSubscriber(modid = com.lanterna.LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GuildaComando {

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("guilda")
                .executes(c -> info(c, null))
                .then(Commands.literal("criar").then(Commands.argument("nome", StringArgumentType.greedyString()).executes(GuildaComando::criar)))
                .then(Commands.literal("convidar").then(Commands.argument("jogador", EntityArgument.player()).executes(GuildaComando::convidar)))
                .then(Commands.literal("entrar").then(Commands.argument("nome", StringArgumentType.greedyString())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Guildas.get(c.getSource().getServer()).guildas.values().stream().map(g -> g.nome), b))
                        .executes(GuildaComando::entrar)))
                .then(Commands.literal("sair").executes(GuildaComando::sair))
                .then(Commands.literal("expulsar").then(Commands.argument("nome", StringArgumentType.word())
                        .suggests((c, b) -> {
                            Guilda g = Guildas.get(c.getSource().getServer()).daJogador(c.getSource().getPlayerOrException().getUUID());
                            return SharedSuggestionProvider.suggest(g == null ? java.util.List.of() : g.membros.values(), b);
                        }).executes(GuildaComando::expulsar)))
                .then(Commands.literal("desfazer").executes(GuildaComando::desfazer))
                .then(Commands.literal("info").executes(c -> info(c, null))
                        .then(Commands.argument("nome", StringArgumentType.greedyString())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Guildas.get(c.getSource().getServer()).guildas.values().stream().map(g -> g.nome), b))
                                .executes(c -> info(c, StringArgumentType.getString(c, "nome")))))
                .then(Commands.literal("lista").executes(GuildaComando::lista))
                .then(Commands.literal("simbolo").executes(GuildaComando::simbolo))
                .then(Commands.literal("abandonar").executes(GuildaComando::abandonar))
                // administração (op): dá tropas de ataque, encerra o escudo de um planeta
                .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("tropas").then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 300))
                                .executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    Guildas gs = Guildas.get(p.server);
                                    Guilda g = gs.daJogador(p.getUUID());
                                    if (g == null) return 0;
                                    g.ataque = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "n");
                                    gs.mudou();
                                    return 1;
                                })))
                        .then(Commands.literal("defesa").then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 200))
                                .executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    Conquista q = aqui(p);
                                    if (q == null) return 0;
                                    q.defesa = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "n");
                                    Guildas.get(p.server).mudou();
                                    return 1;
                                })))
                        .then(Commands.literal("sem_escudo").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Conquista q = aqui(p);
                            if (q == null) return 0;
                            q.escudoAte = 0;
                            Guildas.get(p.server).mudou();
                            return 1;
                        }))));
    }

    private static Conquista aqui(ServerPlayer p) {
        SpaceBodies.Body b = SpaceBodies.bodyAt(p.level().dimension(), p.getX(), p.getZ());
        return b == null ? null : Guildas.get(p.server).conquistas.get(b.id());
    }

    private static void msg(CommandContext<CommandSourceStack> c, Component m) {
        c.getSource().sendSuccess(() -> m, false);
    }

    private static int erro(CommandContext<CommandSourceStack> c, String key, Object... args) {
        c.getSource().sendFailure(Component.translatable(key, args));
        return 0;
    }

    private static int criar(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Guildas gs = Guildas.get(p.server);
        String nome = StringArgumentType.getString(c, "nome").trim();
        if (gs.daJogador(p.getUUID()) != null) return erro(c, "guilda.lanterna.erro.ja_tem");
        if (nome.length() < 3 || nome.length() > 24) return erro(c, "guilda.lanterna.erro.nome");
        if (gs.porNome(nome) != null) return erro(c, "guilda.lanterna.erro.nome_usado");
        var corps = Factions.playerCorps(p);
        if (corps == null) return erro(c, "guilda.lanterna.erro.sem_anel");
        Guilda g = gs.criar(p, nome, corps == com.lanterna.corps.BuiltinCorps.WHITE ? "green" : corps.id().getPath());
        msg(c, Component.translatable("guilda.lanterna.criada", g.nome).withStyle(s -> s.withColor(g.cor()).withBold(true)));
        msg(c, Component.translatable("guilda.lanterna.criada_dica").withStyle(ChatFormatting.GRAY));
        ModNetwork.sendTo(p, new AbrirSimboloPacket(g.nome, g.cor(), g.simbolo));
        return 1;
    }

    private static int convidar(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerPlayer alvo = EntityArgument.getPlayer(c, "jogador");
        Guildas gs = Guildas.get(p.server);
        Guilda g = gs.daJogador(p.getUUID());
        if (g == null || !g.dono.equals(p.getUUID())) return erro(c, "guilda.lanterna.erro.so_dono");
        if (gs.daJogador(alvo.getUUID()) != null) return erro(c, "guilda.lanterna.erro.alvo_tem");
        if (g.membros.size() >= LanternaConfig.GUILDA_MAX_MEMBROS.get()) return erro(c, "guilda.lanterna.erro.cheia");
        gs.convites.put(alvo.getUUID(), g.id);
        msg(c, Component.translatable("guilda.lanterna.convidou", alvo.getDisplayName()));
        MutableComponent botao = Component.translatable("guilda.lanterna.convite_botao").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/guilda entrar " + g.nome)));
        alvo.sendSystemMessage(Component.translatable("guilda.lanterna.convite", p.getDisplayName(), g.nome).append(" ").append(botao));
        return 1;
    }

    private static int entrar(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Guildas gs = Guildas.get(p.server);
        Guilda g = gs.porNome(StringArgumentType.getString(c, "nome").trim());
        if (gs.daJogador(p.getUUID()) != null) return erro(c, "guilda.lanterna.erro.ja_tem");
        if (g == null || !g.id.equals(gs.convites.get(p.getUUID()))) return erro(c, "guilda.lanterna.erro.sem_convite");
        if (g.membros.size() >= LanternaConfig.GUILDA_MAX_MEMBROS.get()) return erro(c, "guilda.lanterna.erro.cheia");
        gs.convites.remove(p.getUUID());
        g.membros.put(p.getUUID(), p.getGameProfile().getName());
        gs.mudou();
        Conquistas.avisarGuilda(p.server, g.id, Component.translatable("guilda.lanterna.entrou", p.getDisplayName(), g.nome).withStyle(s -> s.withColor(g.cor())));
        return 1;
    }

    private static int sair(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Guildas gs = Guildas.get(p.server);
        Guilda g = gs.daJogador(p.getUUID());
        if (g == null) return erro(c, "guilda.lanterna.erro.sem_guilda");
        if (g.dono.equals(p.getUUID())) return erro(c, "guilda.lanterna.erro.dono_sai");
        g.membros.remove(p.getUUID());
        gs.mudou();
        msg(c, Component.translatable("guilda.lanterna.saiu", g.nome));
        Conquistas.avisarGuilda(p.server, g.id, Component.translatable("guilda.lanterna.saiu_outro", p.getDisplayName()));
        return 1;
    }

    private static int expulsar(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Guildas gs = Guildas.get(p.server);
        Guilda g = gs.daJogador(p.getUUID());
        if (g == null || !g.dono.equals(p.getUUID())) return erro(c, "guilda.lanterna.erro.so_dono");
        String nome = StringArgumentType.getString(c, "nome");
        UUID alvo = null;
        for (Map.Entry<UUID, String> e : g.membros.entrySet()) if (e.getValue().equalsIgnoreCase(nome)) alvo = e.getKey();
        if (alvo == null || alvo.equals(g.dono)) return erro(c, "guilda.lanterna.erro.nao_membro");
        g.membros.remove(alvo);
        gs.mudou();
        Conquistas.avisarGuilda(p.server, g.id, Component.translatable("guilda.lanterna.expulso", nome));
        ServerPlayer x = p.server.getPlayerList().getPlayer(alvo);
        if (x != null) x.sendSystemMessage(Component.translatable("guilda.lanterna.voce_expulso", g.nome).withStyle(ChatFormatting.RED));
        return 1;
    }

    private static int desfazer(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Guildas gs = Guildas.get(p.server);
        Guilda g = gs.daJogador(p.getUUID());
        if (g == null || !g.dono.equals(p.getUUID())) return erro(c, "guilda.lanterna.erro.so_dono");
        for (Conquista q : gs.planetasDe(g.id)) {
            if (Invasao.ativa(q.planeta) != null) return erro(c, "guilda.lanterna.erro.em_invasao");
        }
        for (Conquista q : gs.planetasDe(g.id)) Conquistas.largar(p.server, q);
        gs.desfazer(g);
        Conquistas.anunciar(p.server, Component.translatable("guilda.lanterna.desfeita", g.nome).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int abandonar(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Guildas gs = Guildas.get(p.server);
        Guilda g = gs.daJogador(p.getUUID());
        if (g == null || !g.dono.equals(p.getUUID())) return erro(c, "guilda.lanterna.erro.so_dono");
        Conquista q = aqui(p);
        if (q == null || !q.guilda.equals(g.id)) return erro(c, "guilda.lanterna.erro.nao_e_seu");
        if (Invasao.ativa(q.planeta) != null) return erro(c, "guilda.lanterna.erro.em_invasao");
        Conquistas.largar(p.server, q);
        msg(c, Component.translatable("guilda.lanterna.abandonou"));
        return 1;
    }

    private static int lista(CommandContext<CommandSourceStack> c) {
        Guildas gs = Guildas.get(c.getSource().getServer());
        if (gs.guildas.values().stream().allMatch(g -> g.npc)) return erro(c, "guilda.lanterna.nenhuma");
        for (Guilda g : gs.guildas.values())
            if (!g.npc) msg(c, Component.literal("■ ").withStyle(s -> s.withColor(g.cor())).append(Component.translatable("guilda.lanterna.linha",
                    g.nome, g.donoNome(), g.membros.size(), gs.planetasDe(g.id).size()).withStyle(ChatFormatting.WHITE)));
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> c, String nome) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Guildas gs = Guildas.get(c.getSource().getServer());
        Guilda g = nome != null ? gs.porNome(nome) : gs.daJogador(c.getSource().getPlayerOrException().getUUID());
        if (g == null) {
            if (nome == null) {
                msg(c, Component.translatable("guilda.lanterna.ajuda").withStyle(ChatFormatting.GRAY));
                return 1;
            }
            return erro(c, "guilda.lanterna.erro.nao_existe");
        }
        msg(c, Component.literal("== " + g.nome + " ==").withStyle(s -> s.withColor(g.cor()).withBold(true)));
        msg(c, Component.translatable("guilda.lanterna.info.dono", g.donoNome()));
        msg(c, Component.translatable("guilda.lanterna.info.membros", String.join(", ", g.membros.values())));
        msg(c, Component.translatable("guilda.lanterna.info.ataque", g.ataque, LanternaConfig.GUILDA_MAX_ATAQUE.get()));
        long agora = System.currentTimeMillis();
        for (Conquista q : gs.planetasDe(g.id)) {
            SpaceBodies.Body b = SpaceBodies.byId(q.planeta);
            msg(c, Component.translatable("guilda.lanterna.info.planeta", b == null ? Component.literal(q.planeta) : b.displayName(), q.defesa,
                    q.protegido(agora) ? Invasao.tempo(q.escudoAte - agora) : "-"));
        }
        return 1;
    }

    private static int simbolo(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Guilda g = Guildas.get(p.server).daJogador(p.getUUID());
        if (g == null || !g.dono.equals(p.getUUID())) return erro(c, "guilda.lanterna.erro.so_dono");
        ModNetwork.sendTo(p, new AbrirSimboloPacket(g.nome, g.cor(), g.simbolo));
        return 1;
    }

    static void salvarSimbolo(ServerPlayer p, byte[] s) {
        Guildas gs = Guildas.get(p.server);
        Guilda g = gs.daJogador(p.getUUID());
        if (g == null || !g.dono.equals(p.getUUID()) || s.length != 256) return;
        for (byte b : s) if (b < 0 || b >= Guilda.PALETA.length) return;
        g.simbolo = s.clone();
        gs.mudou();
        p.displayClientMessage(Component.translatable("guilda.lanterna.simbolo_salvo").withStyle(ChatFormatting.GREEN), true);
    }

    private GuildaComando() {}
}
