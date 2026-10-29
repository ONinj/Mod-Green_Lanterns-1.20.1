package com.lanterna.mission;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.construct.Affinity;
import com.lanterna.item.PowerRingItem;
import com.lanterna.lore.LoreItems;
import com.lanterna.lore.PlanetLore;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.lore.MissionToastPacket;
import com.lanterna.network.lore.SyncLorePacket;
import com.lanterna.power.EquipmentHelper;
import com.lanterna.space.SpaceBodies;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Lógica das missões no servidor: começar, avançar objetivos, concluir e dar recompensas. */
public final class MissionManager {

    public static LoreData data(ServerPlayer player) {
        return LoreCapability.get(player).orElse(null);
    }

    // ===================== Começar =====================

    public static void start(ServerPlayer player, String id) {
        LoreData data = data(player);
        Mission m = Missions.get(id);
        if (data == null || m == null || data.isActive(id)) return;
        if (data.isDone(id) && !m.line().equals("patrulha")) return;
        data.start(m);
        toast(player, MissionToastPacket.Kind.STARTED, m);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.8f);
        evaluate(player, data);
        sync(player, data);
    }

    /** Linha de início da Tropa (chamado quando o anel escolhe o jogador). */
    public static void startCorpsLine(ServerPlayer player, ResourceLocation corps) {
        LoreData data = data(player);
        if (data == null) return;
        String first = Missions.startOf(corps);
        if (first == null) return;
        // Quem já começou a lore não recomeça (ex.: trocou de anel)
        boolean begun = data.active().keySet().stream().anyMatch(k -> k.startsWith("inicio_") || k.startsWith("main_"))
                || data.completed().stream().anyMatch(k -> k.startsWith("inicio_"));
        if (!begun) start(player, first);
    }

    // ===================== Eventos =====================

    /** A cada segundo: objetivos que dependem do estado (planeta, inventário, respeito, anéis). */
    public static void tick(ServerPlayer player) {
        LoreData data = data(player);
        if (data == null) return;
        if (player.tickCount % 20 == 0) {
            RingData ring = ModCapabilities.get(player).orElse(null);
            int aff = ring != null && ring.hasRing() ? Affinity.points(EquipmentHelper.ring(player)) : 0;
            data.setAffinity(aff);
            evaluate(player, data);
        }
        if (data.isDirty() && player.tickCount % 5 == 0) sync(player, data);
    }

    public static void onTalk(ServerPlayer player, String role, String planet) {
        bump(player, Objective.Type.TALK, role + ":" + planet, 1, true);
        // Os Guardiões também valem para "falar com o líder de Oa"
        if (role.equals("guardiao")) bump(player, Objective.Type.TALK, "lider:" + planet, 1, true);
    }

    public static void onKill(ServerPlayer player, Entity victim) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(victim.getType());
        if (id != null) bump(player, Objective.Type.KILL, id.toString(), 1, false);
    }

    public static void onBoss(ServerPlayer player, String boss) {
        LoreData data = data(player);
        if (data != null) data.addBossKill(boss);
        bump(player, Objective.Type.BOSS, boss, 1, false);
    }

    public static void onConstruct(ServerPlayer player, String construct) {
        bump(player, Objective.Type.CONSTRUCT, construct, 1, false);
        bump(player, Objective.Type.CONSTRUCT, "any", 1, false);
    }

    public static void onDummy(ServerPlayer player) {
        bump(player, Objective.Type.DUMMY, "alvo", 1, false);
    }

    /** Soma progresso em todos os objetivos daquele tipo/alvo. */
    private static void bump(ServerPlayer player, Objective.Type type, String target, int amount, boolean set) {
        LoreData data = data(player);
        if (data == null) return;
        boolean changed = false;
        for (Map.Entry<String, int[]> e : new ArrayList<>(data.active().entrySet())) {
            Mission m = Missions.get(e.getKey());
            if (m == null) continue;
            for (int i = 0; i < m.objectives().size(); i++) {
                Objective o = m.objectives().get(i);
                if (o.type() != type || !o.target().equals(target)) continue;
                int now = e.getValue()[i];
                if (now >= o.count()) continue;
                int value = set ? o.count() : Math.min(o.count(), now + amount);
                if (data.setProgress(m.id(), i, value)) {
                    changed = true;
                    if (value >= o.count()) objectiveDone(player, m, o);
                }
            }
        }
        if (changed) evaluate(player, data);
    }

    /** Recalcula objetivos de estado e conclui missões prontas. */
    public static void evaluate(ServerPlayer player, LoreData data) {
        for (String id : new ArrayList<>(data.active().keySet())) {
            Mission m = Missions.get(id);
            if (m == null) {
                data.abandon(id);
                continue;
            }
            boolean all = true;
            for (int i = 0; i < m.objectives().size(); i++) {
                Objective o = m.objectives().get(i);
                int before = data.progress(id, i);
                int value = stateProgress(player, data, o, before);
                if (value != before && data.setProgress(id, i, value) && value >= o.count() && before < o.count()) {
                    objectiveDone(player, m, o);
                }
                if (value < o.count()) all = false;
            }
            if (all) complete(player, data, m);
        }
    }

    /** Objetivos que são verdade/mentira pelo estado do jogador. Os outros mantêm o valor contado. */
    private static int stateProgress(ServerPlayer player, LoreData data, Objective o, int current) {
        return switch (o.type()) {
            case GOTO -> {
                if (current >= 1) yield 1;
                SpaceBodies.Body here = SpaceBodies.bodyAt(player.level().dimension(), player.getX(), player.getZ());
                yield here != null && here.id().equals(o.target()) ? 1 : 0;
            }
            case COLLECT -> Math.min(o.count(), countItem(player, item(o.target())));
            case REP -> Math.min(o.count(), Math.max(0, data.rep(o.target())));
            case RING -> hasRing(player, o.target()) ? 1 : 0;
            case AFFINITY -> Affinity.level(data.affinity()) >= o.count() ? o.count() : Affinity.level(data.affinity());
            case FUSION -> {
                int n = fusionCount(player);
                if (n >= 14) data.setGeneseRevealed(true);
                yield n;
            }
            default -> current;
        };
    }

    /** Quantos dos 14 itens da fusão o jogador tem (7 anéis + 7 drops). */
    public static int fusionCount(ServerPlayer player) {
        int n = 0;
        for (PlanetLore p : PlanetLore.values()) {
            if (!p.fusion()) continue;
            if (hasRing(player, p.corps.id().getPath())) n++;
            if (countItem(player, p.dropItem()) > 0) n++;
        }
        return n;
    }

    public static boolean hasRing(ServerPlayer player, String corpsPath) {
        if (ringMatches(EquipmentHelper.ring(player), corpsPath)) return true;
        for (ItemStack s : player.getInventory().items) if (ringMatches(s, corpsPath)) return true;
        return ringMatches(player.getOffhandItem(), corpsPath);
    }

    private static boolean ringMatches(ItemStack s, String corpsPath) {
        return s.getItem() instanceof PowerRingItem && PowerRingItem.getCorps(s).getPath().equals(corpsPath);
    }

    public static int countItem(ServerPlayer player, Item item) {
        if (item == null || item == net.minecraft.world.item.Items.AIR) return 0;
        int n = 0;
        for (ItemStack s : player.getInventory().items) if (s.is(item)) n += s.getCount();
        if (player.getOffhandItem().is(item)) n += player.getOffhandItem().getCount();
        return n;
    }

    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
    }

    private static void objectiveDone(ServerPlayer player, Mission m, Objective o) {
        player.displayClientMessage(Component.literal("✔ ").append(o.label(o.count())).withStyle(s -> s.withColor(m.color())), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.3f);
    }

    // ===================== Concluir =====================

    private static void complete(ServerPlayer player, LoreData data, Mission m) {
        // Entrega os itens de "coletar"
        for (Objective o : m.objectives()) {
            if (o.type() == Objective.Type.COLLECT) removeItems(player, item(o.target()), o.count());
        }
        data.finish(m.id());
        for (ItemStack reward : m.rewards().get()) {
            if (!player.getInventory().add(reward.copy())) player.drop(reward.copy(), false);
        }
        if (m.affinity() > 0) Affinity.add(player, m.affinity());
        // Missões cumpridas: os Veltari (juízes das Tropas) passam a respeitar mais
        data.addRep("veltari", 1);
        toast(player, MissionToastPacket.Kind.COMPLETED, m);
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6f, 1.0f);
        player.sendSystemMessage(Component.translatable("mission.lanterna.completed_chat", m.title())
                .withStyle(s -> s.withColor(m.color())));
        if (m.next() != null) start(player, m.next());
        sync(player, data);
    }

    public static void removeItems(ServerPlayer player, Item item, int count) {
        if (item == null) return;
        List<ItemStack> inv = new ArrayList<>(player.getInventory().items);
        inv.add(player.getOffhandItem());
        for (ItemStack s : inv) {
            if (count <= 0) break;
            if (!s.is(item)) continue;
            int take = Math.min(count, s.getCount());
            s.shrink(take);
            count -= take;
        }
    }

    public static void abandon(ServerPlayer player, String id) {
        LoreData data = data(player);
        Mission m = Missions.get(id);
        if (data == null || m == null || m.main() || m.line().equals("inicio")) return;
        data.abandon(id);
        // Os Veltari punem quem quebra a palavra dada
        data.addRep("veltari", -2);
        player.displayClientMessage(Component.translatable("mission.lanterna.abandoned", m.title()), true);
        sync(player, data);
    }

    // ===================== Rede =====================

    public static void sync(ServerPlayer player, LoreData data) {
        ModNetwork.sendTo(player, new SyncLorePacket(data.serializeNBT()));
        data.clearDirty();
    }

    private static void toast(ServerPlayer player, MissionToastPacket.Kind kind, Mission m) {
        ModNetwork.sendTo(player, new MissionToastPacket(kind, m.id()));
    }

    private MissionManager() {}
}
