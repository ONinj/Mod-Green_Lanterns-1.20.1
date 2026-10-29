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
        dispatcher.register(Commands.literal("lanterna")

                // Qualquer jogador: lista dos rituais
                .then(Commands.literal("rituais")
                        .executes(LanternaCommand::rituals))

                // Daqui para baixo: só operador (cheats)
                .then(Commands.literal("escolher")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("tropa", ResourceLocationArgument.id())
                                .suggests(CORPS)
                                .executes(ctx -> choose(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("jogador", EntityArgument.player())
                                        .executes(ctx -> choose(ctx, EntityArgument.getPlayer(ctx, "jogador"))))))

                .then(Commands.literal("carga")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("valor", IntegerArgumentType.integer(0))
                                .executes(LanternaCommand::setCharge)))

                .then(Commands.literal("remover")
                        .requires(source -> source.hasPermission(2))
                        .executes(LanternaCommand::remove))

                .then(Commands.literal("resetar")
                        .requires(source -> source.hasPermission(2))
                        .executes(LanternaCommand::reset))

                // ---- Lore (para testar) ----
                .then(Commands.literal("missao").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        com.lanterna.mission.Missions.all().stream().map(com.lanterna.mission.Mission::id), b))
                                .executes(c -> {
                                    com.lanterna.mission.MissionManager.start(c.getSource().getPlayerOrException(),
                                            com.mojang.brigadier.arguments.StringArgumentType.getString(c, "id"));
                                    return 1;
                                })))
                .then(Commands.literal("afinidade").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("pontos", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                .executes(c -> {
                                    com.lanterna.construct.Affinity.add(c.getSource().getPlayerOrException(),
                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "pontos"));
                                    return 1;
                                })))
                .then(Commands.literal("respeito").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("raca", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(Commands.argument("valor", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                        .executes(c -> {
                                            ServerPlayer p = c.getSource().getPlayerOrException();
                                            com.lanterna.mission.LoreCapability.get(p).ifPresent(d -> d.addRep(
                                                    com.mojang.brigadier.arguments.StringArgumentType.getString(c, "raca"),
                                                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "valor")));
                                            return 1;
                                        }))))
                .then(Commands.literal("genese").requires(source -> source.hasPermission(2))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            com.lanterna.mission.LoreCapability.get(p).ifPresent(d -> d.setGeneseRevealed(true));
                            return 1;
                        }))
                .then(Commands.literal("reconstruir").requires(source -> source.hasPermission(2))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            var planet = com.lanterna.lore.PlanetLore.byDimension(p.level().dimension());
                            if (planet != null) com.lanterna.lore.world.PlanetSites.rebuild(p.serverLevel(), planet);
                            return 1;
                        })));
    }

    private static int choose(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        ResourceLocation corpsId = ResourceLocationArgument.getId(ctx, "tropa");
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
        RingPowers.setCharge(player, data, IntegerArgumentType.getInteger(ctx, "valor"));
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
