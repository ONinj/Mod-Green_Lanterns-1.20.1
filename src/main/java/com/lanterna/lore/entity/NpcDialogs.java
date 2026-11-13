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

    /**
     * O que ainda falta para a fusão do Anel Branco, no chat: o anel e/ou a relíquia de cada planeta, e onde conseguir
     * (o anel se forja na Bateria Central do planeta com 1 Anel Vazio, a relíquia e 4 minérios; a relíquia cai do chefe
     * invocado no altar da arena do planeta).
     */
    private static void faltamNaFusao(ServerPlayer player, List<String> faltam) {
        player.sendSystemMessage(Component.translatable("dialog.lanterna.genese.faltam").withStyle(s -> s.withColor(0xF4F4FF).withBold(true)));
        for (String id : faltam) {
            com.lanterna.lore.PlanetLore pl = com.lanterna.lore.PlanetLore.byPlanet(id);
            if (pl == null) continue;
            boolean anel = MissionManager.hasRing(player, pl.corps.id().getPath());
            boolean reliquia = MissionManager.countItem(player, pl.dropItem()) > 0;
            Component o = Component.literal("  • ").append(pl.planetName()).append(": ");
            if (!anel) o = o.copy().append(Component.translatable("dialog.lanterna.genese.anel", pl.planetName()));
            if (!anel && !reliquia) o = o.copy().append(" · ");
            if (!reliquia) o = o.copy().append(Component.translatable("dialog.lanterna.genese.reliquia", pl.dropItem().getDescription(), pl.bossName()));
            int cor = pl.color();
            player.sendSystemMessage(o.copy().withStyle(s -> s.withColor(cor)));
        }
    }

    public static void open(ServerPlayer player, RaceEntity npc) {
        if (npc.role().startsWith("conselho_")) {
            com.lanterna.cidade.Palacio.conselho(player, npc);
            return;
        }
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
            case TERRANO -> com.lanterna.terra.TerraHistoria.dialogo(player, npc, data, lines, options);
            case VES_KAI -> {
                if (npc.hasFlag(RaceEntity.WOUNDED)) lines.add("dialog.lanterna.ves_kai.wounded");
                else {
                    String favor = FavoresVesKai.oferta(player, npc, data);
                    if (favor != null) {
                        lines.add("dialog.lanterna.ves_kai.favor");
                        options.add("accept:" + favor);
                    } else lines.add("dialog.lanterna.ves_kai.moedas");
                }
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
            // Caminho do Anel Branco: quem já está juntando os 7 anéis pega com QUALQUER Guardião (ou líder) a linha de um
            // anel que ainda falta, sem depender de nível, de outra linha ativa nem do Guardião daquele planeta
            boolean genese = data.isActive("main_genese_0") || data.isActive("main_genese_1") || data.isDone("main_genese_0");
            if (genese && (race == Race.GUARDIAO || role.equals("lider"))) {
                com.lanterna.lore.PlanetLore aqui = com.lanterna.lore.PlanetLore.byPlanet(planet);
                List<String> faltam = new ArrayList<>();
                for (com.lanterna.lore.PlanetLore pl : Missions.MAIN_ORDER) {
                    if (!pl.fusion()) continue;
                    boolean anel = MissionManager.hasRing(player, pl.corps.id().getPath());
                    boolean reliquia = MissionManager.countItem(player, pl.dropItem()) > 0;
                    if (anel && reliquia) continue;
                    faltam.add(pl.planet);
                    // o Guardião de Oa oferece todas; os outros, a do próprio planeta
                    boolean oferece = race == Race.GUARDIAO && "oa".equals(planet) || pl == aqui;
                    if (!oferece) continue;
                    String linha = null;
                    for (int k = 1; k <= 5 && linha == null; k++) {
                        String id = "main_" + pl.planet + "_" + k;
                        if (data.isActive(id)) linha = "";            // já está fazendo
                        else if (!data.isDone(id)) linha = id;
                    }
                    if (linha != null && !linha.isEmpty() && !options.contains("accept:" + linha)) options.add("accept:" + linha);
                }
                if (!faltam.isEmpty()) faltamNaFusao(player, faltam);
            }
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
            if (race == Race.GUARDIAO && com.lanterna.tropa.Nivel.nivel(data.xp()) >= com.lanterna.tropa.Nivel.PROVA && !data.autoRecarga()) {
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
