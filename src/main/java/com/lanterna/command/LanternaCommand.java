package com.lanterna.command;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.CorpsDefinition;
import com.lanterna.corps.CorpsManager;
import com.lanterna.power.RingPowers;
import com.lanterna.selection.OrangeClaim;
import com.lanterna.selection.SelectionManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Comandos de teste (precisa de permissão de operador / cheats ligados):
 *
 *   /lanterna escolher <tropa> [jogador]   roda a cerimônia de entrega
 *   /lanterna carga <valor>                define a carga do anel
 *   /lanterna remover                      tira o anel e devolve o item
 *   /lanterna resetar                      zera tudo para testar a seleção de novo
 */
public final class LanternaCommand {

    private static final SuggestionProvider<CommandSourceStack> CORPS =
            (ctx, builder) -> SharedSuggestionProvider.suggestResource(CorpsManager.ids(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lantern")

                // Qualquer jogador: lista dos rituais
                .then(Commands.literal("rituals")
                        .executes(LanternaCommand::rituals))

                // Daqui para baixo: só operador (cheats)
                .then(Commands.literal("mogo")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("reset").executes(c -> {
                            net.minecraft.server.level.ServerPlayer p = c.getSource().getPlayerOrException();
                            com.lanterna.mogo.ChamadoMogo.resetar(p);
                            c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable("mogo.lanterna.cmd.resetado"), false);
                            return 1;
                        }))
                        .then(Commands.literal("jade").executes(c -> {
                            net.minecraft.server.level.ServerPlayer p = c.getSource().getPlayerOrException();
                            p.getInventory().add(new net.minecraft.world.item.ItemStack(com.lanterna.mogo.JadeViva.ITEM.get(), 3));
                            return 1;
                        }))
                        .then(Commands.literal("seal").executes(c -> {
                            net.minecraft.server.level.ServerPlayer p = c.getSource().getPlayerOrException();
                            net.minecraft.world.item.ItemStack s = p.getMainHandItem();
                            if (!com.lanterna.mogo.JadeViva.aceita(s)) {
                                c.getSource().sendFailure(net.minecraft.network.chat.Component.translatable("mogo.lanterna.cmd.sem_bateria"));
                                return 0;
                            }
                            com.lanterna.mogo.JadeViva.definir(s, com.lanterna.mogo.JadeViva.MAX);
                            c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable("mogo.lanterna.jade.subiu.3"), false);
                            return 1;
                        }))
                        .then(Commands.literal("second").executes(c -> {
                            com.lanterna.mogo.SegundaChance.resetar(c.getSource().getPlayerOrException());
                            c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable("commands.lanterna.mogo.segunda"), false);
                            return 1;
                        }))
                        .then(Commands.literal("go").executes(c -> {
                            com.lanterna.mogo.SegundaChance.chegar(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("mission").then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 6)).executes(c -> {
                            com.lanterna.mogo.MogoMissoes.pular(c.getSource().getPlayerOrException(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "n"));
                            return 1;
                        }))))

                .then(Commands.literal("hanks").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("go").executes(c -> {
                            var p = c.getSource().getPlayerOrException();
                            var l = p.server.getLevel(com.lanterna.hanks.HanksMundo.DIM);
                            if (l != null) p.teleportTo(l, 0.5, 120, 6.5, 180, 20);
                            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 20 * 20, 0, false, false));
                            return 1;
                        }))
                        .then(Commands.literal("mission").then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 3)).executes(c -> {
                            com.lanterna.hanks.HanksMissoes.pular(c.getSource().getPlayerOrException(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "n"));
                            return 1;
                        }))))
                .then(Commands.literal("legendary").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        java.util.Arrays.stream(com.lanterna.lendarios.Lendario.values()).map(x -> x.id), b))
                                .then(Commands.literal("go").executes(c -> {
                                    var p = c.getSource().getPlayerOrException();
                                    var l = com.lanterna.lendarios.Lendario.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "id"));
                                    if (l == null) return 0;
                                    if (l.tipo == null) {
                                        var lv = p.server.getLevel(com.lanterna.hanks.HanksMundo.DIM);
                                        if (lv != null) p.teleportTo(lv, 0.5, 120, com.lanterna.hanks.HanksMundo.CIDADELA_Z + 30.5, 180, 20);
                                    } else {
                                        var lv = p.server.getLevel(l.tipo.dimension);
                                        int x = com.lanterna.lendarios.PlanetasLendarios.regiaoX(l), z = com.lanterna.lendarios.PlanetasLendarios.REGIAO_BASE;
                                        if (lv != null) p.teleportTo(lv, x + 0.5, 300, z + 40.5, 180, 30);
                                    }
                                    p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 20 * 25, 0, false, false));
                                    return 1;
                                }))
                                .then(Commands.literal("revive").executes(c -> {
                                    var l = com.lanterna.lendarios.Lendario.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "id"));
                                    if (l == null) return 0;
                                    var st = com.lanterna.lendarios.ArenaLendaria.estado(c.getSource().getServer(), l);
                                    st.volta = 0;
                                    st.vivo = false;
                                    com.lanterna.lendarios.ArenaLendaria.marcar(c.getSource().getServer());
                                    c.getSource().getPlayerOrException().getPersistentData().remove("lanterna_duelo_" + l.id);
                                    return 1;
                                }))))

                .then(Commands.literal("frenzy").requires(source -> source.hasPermission(2))
                        .executes(c -> { com.lanterna.morte.Frenesi.comecar(c.getSource().getPlayerOrException()); return 1; })
                        .then(Commands.literal("stop").executes(c -> { com.lanterna.morte.Frenesi.acabar(c.getSource().getPlayerOrException(), true); return 1; })))
                .then(Commands.literal("orange").requires(source -> source.hasPermission(2))
                        .executes(c -> laranja(c, null))
                        .then(Commands.literal("single").executes(c -> laranja(c, true)))
                        .then(Commands.literal("many").executes(c -> laranja(c, false)))
                        .then(Commands.literal("unlock").executes(c -> {
                            OrangeClaim claim = OrangeClaim.get(c.getSource().getServer());
                            claim.release();
                            c.getSource().sendSuccess(() -> Component.translatable("command.lanterna.laranja.liberado"), true);
                            return 1;
                        })))

                .then(Commands.literal("choose")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("corps", ResourceLocationArgument.id())
                                .suggests(CORPS)
                                .executes(ctx -> choose(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> choose(ctx, EntityArgument.getPlayer(ctx, "player"))))))

                .then(Commands.literal("charge")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                .executes(LanternaCommand::setCharge)))

                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(2))
                        .executes(LanternaCommand::remove))

                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .executes(LanternaCommand::reset))

                // ---- Espécies, naves, nível (para testar) ----
                .then(Commands.literal("spawn").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("species", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(com.lanterna.especies.Especie.values()).map(e -> e.id), b))
                                .executes(c -> spawnNpc(c, ""))
                                .then(Commands.argument("options", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(c -> spawnNpc(c, com.mojang.brigadier.arguments.StringArgumentType.getString(c, "options"))))))
                .then(Commands.literal("xp").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("points", IntegerArgumentType.integer(0))
                                .executes(c -> {
                                    com.lanterna.tropa.Nivel.addXp(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "points"),
                                            Component.literal("comando"));
                                    return 1;
                                })))
                .then(Commands.literal("encounter").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("type", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Ingles.en(new String[]{"pirata", "capitania", "patrulha", "inimiga", "cargueiro", "destrocos"}), b))
                                .executes(c -> {
                                    com.lanterna.tropa.Encontros.forcar(c.getSource().getPlayerOrException(),
                                            Ingles.pt(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "type")));
                                    return 1;
                                })))
                .then(Commands.literal("interior").requires(src -> src.hasPermission(2))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            var naves = p.level().getEntitiesOfClass(com.lanterna.naves.NaveEntity.class, p.getBoundingBox().inflate(48),
                                    n -> n.tipo().interior() != null);
                            if (naves.isEmpty()) return 0;
                            com.lanterna.naves.NaveInterior.entrar(p, naves.get(0));
                            return 1;
                        }))
                .then(Commands.literal("contracts").requires(src -> src.hasPermission(2))
                        .executes(c -> {
                            com.lanterna.tropa.Contratos.abrirQuadro(c.getSource().getPlayerOrException(), true);
                            return 1;
                        }))

                // ---- Lore (para testar) ----
                .then(Commands.literal("mission").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        com.lanterna.mission.Missions.all().stream().map(com.lanterna.mission.Mission::id), b))
                                .executes(c -> {
                                    com.lanterna.mission.MissionManager.start(c.getSource().getPlayerOrException(),
                                            com.mojang.brigadier.arguments.StringArgumentType.getString(c, "id"));
                                    return 1;
                                })))
                .then(Commands.literal("affinity").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("points", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                .executes(c -> {
                                    com.lanterna.construct.Affinity.add(c.getSource().getPlayerOrException(),
                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "points"));
                                    return 1;
                                })))
                .then(Commands.literal("respect").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("race", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                        .executes(c -> {
                                            ServerPlayer p = c.getSource().getPlayerOrException();
                                            com.lanterna.mission.LoreCapability.get(p).ifPresent(d -> d.addRep(
                                                    com.mojang.brigadier.arguments.StringArgumentType.getString(c, "race"),
                                                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "value")));
                                            return 1;
                                        }))))
                .then(Commands.literal("genesis").requires(source -> source.hasPermission(2))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            com.lanterna.mission.LoreCapability.get(p).ifPresent(d -> d.setGeneseRevealed(true));
                            return 1;
                        }))
                .then(Commands.literal("rebuild").requires(source -> source.hasPermission(2))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            var planet = com.lanterna.lore.PlanetLore.byDimension(p.level().dimension());
                            if (planet != null) com.lanterna.lore.world.PlanetSites.rebuild(p.serverLevel(), planet);
                            return 1;
                        })));
    }

    private static int choose(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        ResourceLocation corpsId = ResourceLocationArgument.getId(ctx, "corps");
        if (!CorpsManager.exists(corpsId)) {
            ctx.getSource().sendFailure(Component.translatable("command.lanterna.unknown_corps", corpsId.toString()));
            return 0;
        }
        if (!SelectionManager.startSelection(target, corpsId)) {
            ctx.getSource().sendFailure(Component.translatable("command.lanterna.cannot_choose"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.lanterna.chosen",
                target.getDisplayName(), Component.translatable(CorpsDefinition.nameKey(corpsId))), true);
        return 1;
    }

    private static int setCharge(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        RingData data = ModCapabilities.get(player).orElse(null);
        if (data == null || !data.hasRing()) {
            ctx.getSource().sendFailure(Component.translatable("message.lanterna.battery_no_ring"));
            return 0;
        }
        RingPowers.setCharge(player, data, IntegerArgumentType.getInteger(ctx, "value"));
        RingPowers.sync(player, data);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.lanterna.charge_set", data.charge(), data.maxCharge()), false);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack ring = RingPowers.removeRing(player);
        if (ring.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("message.lanterna.battery_no_ring"));
            return 0;
        }
        if (!player.getInventory().add(ring)) player.drop(ring, false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.lanterna.removed"), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        RingData data = ModCapabilities.get(player).orElse(null);
        if (data == null) return 0;
        RingPowers.removeRing(player); // o anel e a bateria somem (é só para testes)
        com.lanterna.power.EquipmentHelper.take(player, RingData.BATTERY_SLOT);
        data.unequip();
        data.clearPending();
        data.resetRage();
        data.setEverChosen(false);
        data.resetRituals();
        OrangeClaim claim = OrangeClaim.get(player.server);
        if (player.getUUID().equals(claim.owner())) claim.release();
        com.lanterna.selection.CallingEvents.reset(player);
        RingPowers.applyFlightAbility(player, data);
        RingPowers.sync(player, data);
        // Lore também volta ao começo (missões, respeito, presets)
        com.lanterna.mission.LoreCapability.get(player).ifPresent(lore -> {
            lore.deserializeNBT(new net.minecraft.nbt.CompoundTag());
            com.lanterna.mission.MissionManager.sync(player, lore);
        });
        ctx.getSource().sendSuccess(() -> Component.translatable("command.lanterna.reset"), false);
        return 1;
    }

    /** /lanterna laranja [unico|varios]: um só Lanterna Laranja por mundo, ou quantos quiserem. */
    private static int laranja(CommandContext<CommandSourceStack> ctx, @org.jetbrains.annotations.Nullable Boolean unico) {
        OrangeClaim claim = OrangeClaim.get(ctx.getSource().getServer());
        if (unico != null) claim.setUnico(unico);
        String dono = "-";
        if (claim.owner() != null) {
            var p = ctx.getSource().getServer().getPlayerList().getPlayer(claim.owner());
            dono = p != null ? p.getGameProfile().getName()
                    : ctx.getSource().getServer().getProfileCache().get(claim.owner()).map(com.mojang.authlib.GameProfile::getName).orElse(claim.owner().toString().substring(0, 8));
        }
        String d = dono;
        ctx.getSource().sendSuccess(() -> Component.translatable(claim.unico() ? "command.lanterna.laranja.unico" : "command.lanterna.laranja.varios", d), unico != null);
        return 1;
    }

    /** /lanterna spawn <espécie> [papel=lanterna|pirata|capitao|mercador] [tropa=green..] [roupa=...|nenhuma] [pele=1..3] [oficial] [corrompido] */
    private static int spawnNpc(CommandContext<CommandSourceStack> ctx, String opts) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        var esp = com.lanterna.especies.Especie.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "species"));
        var papel = esp.papel != null ? esp.papel : com.lanterna.especies.Especie.Papel.LANTERNA;
        com.lanterna.corps.BuiltinCorps corps = esp.tropa;
        String roupa = null;
        int pele = 1;
        boolean oficial = false, corrompido = false, roupaSet = false;
        for (String o : opts.split(" ")) {
            if (o.isBlank()) continue;
            String[] kv = o.split("=", 2);
            kv[0] = Ingles.pt(kv[0]);
            if (kv.length > 1) kv[1] = Ingles.pt(kv[1]);
            switch (kv[0]) {
                case "papel" -> papel = com.lanterna.especies.Especie.Papel.byId(kv[1]);
                case "tropa" -> { for (var c : com.lanterna.corps.BuiltinCorps.values()) if (c.id().getPath().equals(kv[1])) corps = c; }
                case "roupa" -> { roupa = kv[1].equals("nenhuma") ? null : kv[1]; roupaSet = true; }
                case "pele" -> pele = Integer.parseInt(kv[1]);
                case "oficial" -> oficial = true;
                case "corrompido" -> corrompido = true;
                default -> {}
            }
        }
        if (papel != com.lanterna.especies.Especie.Papel.LANTERNA) corps = null;
        else if (corps == null) corps = com.lanterna.corps.BuiltinCorps.GREEN;
        if (!roupaSet) roupa = switch (papel) {
            case LANTERNA -> "roupa_tropa_" + corps.id().getPath();
            case PIRATA -> "roupa_pirata";
            case CAPITAO -> "roupa_capitao";
            case MERCADOR -> "roupa_mercador";
        };
        var e = new com.lanterna.especies.LanternaEntity(com.lanterna.especies.EspeciesEntities.LANTERNA_NPC.get(), p.level());
        e.setup(esp, pele, papel, corps, roupa, oficial, corrompido);
        e.moveTo(p.getX() + p.getLookAngle().x * 3, p.getY(), p.getZ() + p.getLookAngle().z * 3, p.getYRot() + 180, 0);
        p.level().addFreshEntity(e);
        return 1;
    }

    private static final String[] RITUAL_ORDER = {"green", "red", "yellow", "blue", "violet", "indigo", "orange", "black", "white"};

    private static int rituals(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable("ritual.lanterna.header").withStyle(s -> s.withBold(true)), false);
        for (String corps : RITUAL_ORDER) {
            ResourceLocation id = new ResourceLocation("lanterna", corps);
            int color = com.lanterna.corps.BuiltinCorps.colorOf(id);
            source.sendSuccess(() -> Component.literal("\u25C6 ").withStyle(s -> s.withColor(color))
                    .append(Component.translatable(CorpsDefinition.fullNameKey(id)).withStyle(s -> s.withColor(color).withBold(true)))
                    .append(Component.literal(": "))
                    .append(Component.translatable("ritual.lanterna." + corps + ".how")), false);
        }
        // Outros caminhos na Terra
        int signs = source.getEntity() instanceof net.minecraft.world.entity.player.Player p
                ? com.lanterna.selection.CallingEvents.count(p) : 0;
        int needed = com.lanterna.LanternaConfig.CALL_REQUIRED.get();
        source.sendSuccess(() -> Component.literal("\u2726 ").withStyle(net.minecraft.ChatFormatting.WHITE)
                .append(Component.translatable("ritual.lanterna.call.title").withStyle(s -> s.withBold(true)))
                .append(Component.literal(": "))
                .append(Component.translatable("ritual.lanterna.call.how", signs, needed)), false);
        source.sendSuccess(() -> Component.literal("\u2726 ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
                .append(Component.translatable("ritual.lanterna.fallen.title").withStyle(s -> s.withBold(true)))
                .append(Component.literal(": "))
                .append(Component.translatable("ritual.lanterna.fallen.how")), false);
        return 1;
    }

    private LanternaCommand() {}
}
