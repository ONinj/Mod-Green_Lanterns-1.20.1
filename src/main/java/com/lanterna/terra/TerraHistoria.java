package com.lanterna.terra;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.fonte.FonteDados;
import com.lanterna.lore.LoreItems;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.Mission;
import com.lanterna.mission.MissionManager;
import com.lanterna.mission.Objective;
import com.lanterna.mission.Objective.Type;
import com.lanterna.lore.entity.RaceEntity;
import com.lanterna.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A história na Terra (linha "terra"), antes do anel:
 *
 *  terra_1  Luzes sobre Aurora    falar com o Sgt. Rui na porta do observatório da Vila Aurora
 *  terra_2  A Base Vigília        descer pelo alçapão do observatório e falar com a Dra. Lívia
 *  terra_3  Estrelas Cadentes     de noite, juntar 3 Fragmentos Estelares das estrelas que caem (ela estuda e devolve)
 *  terra_4  O Sinal               uma das "estrelas" era um anel: achar o Anel Apagado (ela dá o mapa da cratera)
 *  terra_5  O Ritual da Bateria   fazer a Bateria Cinzelada e enchê-la com o ritual (altar de ensaio no terraço)
 *  terra_6  O Anel Escolhe        encostar o Anel Apagado na bateria carregada
 *
 * Depois disso o anel escolhe a Tropa e a linha da Tropa começa (o juramento na Bateria Central).
 * Quem já tem anel, ou já começou a lore, não recebe a linha sozinho (pode aceitar com os moradores da vila).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TerraHistoria {
    public static final String LINHA = "terra";
    public static final int COR = 0x7FD6FF;
    private static final String OFERECIDA = "lanterna_terra_oferecida";

    private static final Map<String, Mission> CACHE = new HashMap<>();
    @Nullable private static BlockPos cacheDe;

    public static boolean eDaTerra(String id) {
        return id.startsWith("terra_");
    }

    @Nullable
    public static Mission mission(String id) {
        BlockPos c = TerraSite.centro;
        if (c != cacheDe) {
            CACHE.clear();
            cacheDe = c;
        }
        Mission m = CACHE.get(id);
        if (m != null) return m;
        m = criar(id, c);
        if (m != null) CACHE.put(id, m);
        return m;
    }

    @Nullable
    private static BlockPos abs(@Nullable BlockPos c, BlockPos rel, boolean altura) {
        if (c == null) return null;
        BlockPos p = c.offset(rel);
        return altura ? p : new BlockPos(p.getX(), 0, p.getZ());
    }

    @Nullable
    private static Mission criar(String id, @Nullable BlockPos c) {
        return switch (id) {
            case "terra_1" -> m(id, List.of(Objective.of(Type.TALK, "seguranca:earth", 1, "earth", abs(c, TerraSite.OBS.offset(-8, 0, 2), false))),
                    List.of(() -> new ItemStack(Items.BREAD, 6), () -> new ItemStack(Items.TORCH, 16)), "terra_2");
            case "terra_2" -> m(id, List.of(Objective.of(Type.TALK, "cientista:earth", 1, "earth", abs(c, TerraSite.LAB.offset(2, 1, -2), true))),
                    List.of(() -> new ItemStack(Items.IRON_PICKAXE), () -> new ItemStack(Items.SHIELD)), "terra_3");
            case "terra_3" -> m(id, List.of(Objective.of(Type.COLLECT, "lanterna:fragmento_estelar", 3, null, null)),
                    List.of(() -> new ItemStack(ModItems.FRAGMENTO_ESTELAR.get(), 4), () -> new ItemStack(Items.AMETHYST_BLOCK)), "terra_4");
            case "terra_4" -> m(id, List.of(Objective.of(Type.FONTE, "terra:anel", 1, null, null)),
                    List.of(() -> new ItemStack(Items.GOLD_INGOT, 2), () -> new ItemStack(Items.WHITE_CANDLE, 4)), "terra_5");
            case "terra_5" -> m(id, List.of(Objective.of(Type.FONTE, "terra:ritual", 1, "earth", abs(c, TerraSite.ALTAR, false))),
                    List.of(() -> new ItemStack(Items.GOLDEN_APPLE, 2)), "terra_6");
            case "terra_6" -> m(id, List.of(Objective.of(Type.FONTE, "terra:despertou", 1, null, null)),
                    List.of(() -> new ItemStack(LoreItems.MERITO.get(), 3), () -> new ItemStack(Items.ENDER_PEARL, 4)), null);
            default -> null;
        };
    }

    private static Mission m(String id, List<Objective> o, List<java.util.function.Supplier<ItemStack>> itens, @Nullable String next) {
        return new Mission(id, LINHA, COR, o, () -> {
            List<ItemStack> out = new ArrayList<>();
            for (var s : itens) out.add(s.get());
            return out;
        }, 50, next, null);
    }

    // ===================== Começo =====================

    private static boolean temAnel(ServerPlayer p) {
        RingData r = ModCapabilities.get(p).orElse(null);
        return r != null && (r.hasRing() || r.isSelectionPending());
    }

    /** Ao entrar: quem é novo (sem anel e sem lore) recebe a primeira missão, uma vez só. */
    public static void aoEntrar(ServerPlayer p) {
        if (p.getPersistentData().getBoolean(OFERECIDA)) return;
        LoreData d = MissionManager.data(p);
        if (d == null) return;
        if (temAnel(p) || !d.active().isEmpty() || !d.completed().isEmpty()) {
            p.getPersistentData().putBoolean(OFERECIDA, true);
            return;
        }
        p.getPersistentData().putBoolean(OFERECIDA, true);
        MissionManager.start(p, "terra_1");
        p.sendSystemMessage(Component.translatable("message.lanterna.terra.boas_vindas").withStyle(s -> s.withColor(COR)));
    }

    /** Conversa com os humanos da Vila Aurora (chamado pelo NpcDialogs). */
    public static void dialogo(ServerPlayer p, RaceEntity npc, @Nullable LoreData d, List<String> lines, List<String> options) {
        if (d == null) return;
        String papel = npc.role();
        boolean comecou = d.active().keySet().stream().anyMatch(TerraHistoria::eDaTerra) || d.completed().stream().anyMatch(TerraHistoria::eDaTerra);
        if (!comecou && !temAnel(p)) options.add("accept:terra_1");
        // falas que acompanham a história
        for (String id : d.active().keySet()) {
            if (!eDaTerra(id)) continue;
            String k = "dialog.lanterna.terrano." + papel + "." + id;
            lines.add(k);
        }
        if (papel.equals("cientista") && d.isActive("terra_4") && FonteDados.chamado(p, "terra:anel") < 1) darMapa(p, lines);
        if (temAnel(p) && (papel.equals("cientista") || papel.equals("seguranca"))) lines.add("dialog.lanterna.terrano." + papel + ".lanterna");
    }

    private static void darMapa(ServerPlayer p, List<String> lines) {
        for (ItemStack s : p.getInventory().items)
            if (s.is(Items.FILLED_MAP) && s.hasCustomHoverName() && s.getHoverName().getString().equals(Component.translatable("filled_map.lanterna.anel_caido").getString())) {
                lines.add("dialog.lanterna.terrano.cientista.ja_tem_mapa");
                return;
            }
        ServerLevel l = p.serverLevel();
        BlockPos pos = l.findNearestMapStructure(com.lanterna.worldgen.FallenRingTrades.MAP_TAG, p.blockPosition(), 100, false);
        if (pos == null) {
            lines.add("dialog.lanterna.terrano.cientista.sem_mapa");
            return;
        }
        ItemStack map = MapItem.create(l, pos.getX(), pos.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(l, map);
        MapItemSavedData.addTargetDecoration(map, pos, "+", MapDecoration.Type.RED_X);
        map.setHoverName(Component.translatable("filled_map.lanterna.anel_caido"));
        if (!p.getInventory().add(map)) p.drop(map, false);
        lines.add("dialog.lanterna.terrano.cientista.mapa");
    }

    /** A cada segundo: quem pegou o Anel Apagado cumpre "O Sinal". */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 7) return;
        if (FonteDados.chamado(p, "terra:anel") >= 1) return;
        if (MissionManager.countItem(p, ModItems.DORMANT_RING.get()) > 0) FonteDados.chamado(p, "terra:anel", 1);
    }

    private TerraHistoria() {}
}
