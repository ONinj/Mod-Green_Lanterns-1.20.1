package com.lanterna.lore.entity;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.Mission;
import com.lanterna.mission.MissionManager;
import com.lanterna.mission.Missions;
import com.lanterna.mission.Objective;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.lore.OpenDialogPacket;
import com.lanterna.space.SpaceBodies;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Conversas com os NPCs. O servidor decide o que o NPC diz (chaves de texto) e quais botões aparecem;
 * o cliente só mostra ({@link com.lanterna.client.mission.DialogScreen}).
 */
public final class NpcDialogs {

    public static void open(ServerPlayer player, RaceEntity npc) {
        Race race = npc.race();
        String role = npc.role();
        SpaceBodies.Body body = SpaceBodies.bodyAt(npc.level().dimension(), npc.getX(), npc.getZ());
        String planet = body != null ? body.id() : race.planet;
        LoreData data = MissionManager.data(player);
        List<String> lines = new ArrayList<>();
        List<String> options = new ArrayList<>();

        String who = role.isEmpty() ? "morador" : role;
        if (race == Race.GUARDIAO && !planet.equals("oa")) lines.add("dialog.lanterna.guardiao.emissario");
        else lines.add("dialog.lanterna." + race.id + "." + who);

        // Falas da missão (quando ela pede para falar com este NPC)
        if (data != null && !role.isEmpty()) {
            String key = role + ":" + planet;
            for (Map.Entry<String, int[]> e : data.active().entrySet()) {
                Mission m = Missions.get(e.getKey());
                if (m == null) continue;
                for (int i = 0; i < m.objectives().size(); i++) {
                    Objective o = m.objectives().get(i);
                    boolean matches = o.type() == Objective.Type.TALK && (o.target().equals(key)
                            || (role.equals("guardiao") && o.target().equals("lider:" + planet)));
                    if (matches && e.getValue()[i] < o.count()) lines.add("mission.lanterna." + m.id() + ".talk");
                }
            }
            MissionManager.onTalk(player, role, planet);
        }

        // Jeito de cada raça
        switch (race) {
            case BRAKKARI -> {
                if (RaceEntity.rep(player, "brakkari") < 1) lines.add("dialog.lanterna.brakkari.no_respect");
            }
            case VELTARI -> {
                if (!npc.canTradeWith(player)) lines.add("dialog.lanterna.veltari.ignore");
            }
            case ZAELITH -> {
                if (npc.hasFlag(RaceEntity.MOURNING)) lines.add("dialog.lanterna.zaelith.mourning");
                else lines.add("dialog.lanterna.zaelith.favorite|item:" + ForgeRegistries.ITEMS.getKey(npc.favorite()));
                if (npc.affection(player) < 2) lines.add("dialog.lanterna.zaelith.no_affection");
            }
            case VES_KAI -> {
                if (npc.hasFlag(RaceEntity.WOUNDED)) lines.add("dialog.lanterna.ves_kai.wounded");
            }
            case SKRELL_VATH -> {
                if (RaceEntity.rep(player, "skrell_vath") < 1) lines.add("dialog.lanterna.skrell_vath.no_respect");
            }
            case GHALLOS -> options.add("ask_way");
            default -> {}
        }

        if (npc.enemyCorps(player)) lines.add("dialog.lanterna.enemy_corps");
        if (race.trades() && npc.canTradeWith(player)) options.add(0, "trade");

        // Missões que este NPC oferece
        if (data != null) {
            boolean introDone = data.completed().stream().anyMatch(k -> k.startsWith("inicio_") && k.endsWith("_4"));
            if (race == Race.GUARDIAO && introDone && !data.isActive("ryut_1") && !data.isDone("ryut_1")
                    && com.lanterna.tropa.Nivel.nivel(data.xp()) >= 32) options.add("accept:ryut_1");
            // Ordem livre: o Guardião ou o líder do planeta oferecem a linha deste planeta, se o nível deixar
            if (introDone && (race == Race.GUARDIAO || role.equals("lider"))) {
                com.lanterna.lore.PlanetLore pl = com.lanterna.lore.PlanetLore.byPlanet(planet);
                RingData rd = ModCapabilities.get(player).orElse(null);
                var own = rd != null && rd.hasRing() ? com.lanterna.lore.Factions.of(rd.corps()) : null;
                if (pl != null && java.util.Arrays.asList(Missions.MAIN_ORDER).contains(pl)
                        && !data.isDone("main_" + pl.planet + "_5") && !(own == pl.corps && data.isDone("main_" + pl.planet + "_4"))
                        && MissionManager.activeMainPlanet(data) == null
                        && com.lanterna.tropa.Nivel.nivel(data.xp()) >= com.lanterna.tropa.Nivel.nivelDoPlaneta(pl, own)) {
                    options.add("accept:main_" + pl.planet + "_1");
                }
            }
            // Prova da Emoção (nível 50) com o Guardião
            if (race == Race.GUARDIAO && com.lanterna.tropa.Nivel.nivel(data.xp()) >= com.lanterna.tropa.Nivel.MAX && !data.autoRecarga()) {
                RingData rd = ModCapabilities.get(player).orElse(null);
                if (rd != null && rd.hasRing() && !rd.corps().getPath().equals("white")) {
                    String prova = "prova_" + rd.corps().getPath();
                    if (!data.isActive(prova)) options.add("accept:" + prova);
                }
            }
            if (race == Race.VELTARI && role.equals("instrutor") && introDone && !data.isActive("patrulha")) options.add("accept:patrulha");
            if (race == Race.GUARDIAO && data.active().isEmpty() && !introDone) {
                // Quem chegou a Oa sem passar pelo início da própria Tropa começa por aqui
                RingData ring = ModCapabilities.get(player).orElse(null);
                if (ring != null && ring.hasRing()) {
                    String first = Missions.startOf(ring.corps());
                    if (first != null) options.add("accept:" + first.replace("_1", "_4"));
                }
            }
        }
        options.add("bye");

        // Os Ves'Kai falam a própria língua: só quem tem o anel índigo entende
        boolean symbols = race == Race.VES_KAI && !hasCorps(player, BuiltinCorps.INDIGO);
        ModNetwork.sendTo(player, new OpenDialogPacket(npc.getId(), race.id, role, planet, lines, options, symbols));
    }

    private static boolean hasCorps(ServerPlayer p, BuiltinCorps corps) {
        RingData ring = ModCapabilities.get(p).orElse(null);
        return ring != null && ring.hasRing() && ring.corps().equals(corps.id());
    }

    /** O jogador apertou um botão da conversa. */
    public static void action(ServerPlayer player, int entityId, String action) {
        Entity e = player.level().getEntity(entityId);
        if (!(e instanceof RaceEntity npc) || npc.distanceToSqr(player) > 100) return;
        if (action.equals("trade")) {
            if (npc.canTradeWith(player)) npc.openTrade(player);
            return;
        }
        if (action.equals("ask_way")) {
            if (MissionManager.countItem(player, Items.EMERALD) < 1) {
                player.displayClientMessage(Component.translatable("message.lanterna.ghallos_way_pay"), true);
                return;
            }
            MissionManager.removeItems(player, Items.EMERALD, 1);
            PlanetLore here = PlanetLore.byDimension(player.level().dimension());
            var arena = PlanetLore.arena(here != null ? here : PlanetLore.OKAARA);
            player.sendSystemMessage(Component.translatable("message.lanterna.ghallos_way", arena.getX(), arena.getZ(),
                    PlanetLore.HUB.getX(), PlanetLore.HUB.getZ()));
            return;
        }
        if (action.startsWith("accept:")) {
            String id = action.substring(7);
            if (id.startsWith("main_")) {
                LoreData d = MissionManager.data(player);
                if (d != null) for (String a : new java.util.ArrayList<>(d.active().keySet())) if (a.startsWith("treino_")) d.abandon(a);
            }
            if (Missions.get(id) != null) MissionManager.start(player, id);
        }
    }

    private NpcDialogs() {}
}
