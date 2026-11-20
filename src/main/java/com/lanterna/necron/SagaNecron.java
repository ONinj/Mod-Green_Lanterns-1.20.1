package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.construct.Affinity;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.corps.CorpsDefinition;
import com.lanterna.corps.CorpsManager;
import com.lanterna.item.PowerBatteryItem;
import com.lanterna.item.PowerRingItem;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import com.lanterna.network.ModNetwork;
import com.lanterna.power.EquipmentHelper;
import com.lanterna.power.RingPowers;
import com.lanterna.vida.Emocoes;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 0.54: o começo da saga do Necron.
 *
 * Quando o jogador termina a Árvore das Emoções (os 7 aspectos no nível 6, flag "dominada") com o Anel da Vida no dedo,
 * começa a cutscene: o Embrião da Vida brilha no espaço e escurece; o Anel da Vida se desfaz em sete anéis que giram em
 * volta do jogador e partem para o espaço; só o anel da Tropa escolhida no começo volta para o dedo; Ryut se parte ao
 * meio e de dentro dela sai o Necron, que voa pelo espaço e cai na Terra.
 *
 * A cena é toda do lado do cliente (uma tela de cinema); o servidor só conta o tempo, faz a troca do anel no momento
 * certo e deixa o jogador protegido enquanto assiste. Funciona igual em servidor dedicado (cada um vê a sua).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SagaNecron {
    /** Duração da cutscene (em ticks) e quando o anel da Tropa volta para o dedo. */
    public static final int DURACAO = 1520, TROCA = 618;
    /** Espera depois do título "Vida dominada" até a cena começar. */
    private static final int ESPERA = 120;

    private static final String TAG = "lanterna_saga_necron";
    /** Quem está assistindo agora → ticks desde o começo (negativo = ainda esperando). */
    private static final Map<UUID, Integer> CENA = new HashMap<>();
    /** Quem está só revendo (sem troca do anel). */
    private static final Map<UUID, Boolean> REVENDO = new HashMap<>();

    // ===================== dados =====================

    public static CompoundTag tag(Player p) {
        CompoundTag pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!p.getPersistentData().contains(Player.PERSISTED_NBT_TAG)) p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
        if (!pers.contains(TAG)) pers.put(TAG, new CompoundTag());
        return pers.getCompound(TAG);
    }

    /** O Anel da Vida já se dividiu (a saga começou). */
    public static boolean dividido(Player p) { return tag(p).getBoolean("dividido"); }

    public static boolean assistindo(Player p) { return CENA.containsKey(p.getUUID()); }

    /** Guarda a Tropa do primeiro anel (chamado na entrega de qualquer anel de Tropa emocional). */
    public static void lembrarTropa(Player p, String corps) {
        if (Emocoes.aspecto(corps) && !tag(p).contains("origem")) tag(p).putString("origem", corps);
    }

    /**
     * A Tropa escolhida no começo: a guardada na entrega do anel; nos mundos antigos, a da primeira linha de missões
     * (o juramento ou o início daquela Tropa); se nada disso existir, o aspecto treinado ou o verde.
     */
    public static String tropaDeOrigem(ServerPlayer p) {
        String o = tag(p).getString("origem");
        if (Emocoes.aspecto(o)) return o;
        LoreData d = MissionManager.data(p);
        if (d != null) {
            for (String a : Emocoes.ASPECTOS) {
                String pre1 = "juramento_" + a, pre2 = "inicio_" + a + "_";
                for (String k : d.completed()) if (k.equals(pre1) || k.startsWith(pre2)) return a;
                for (String k : d.active().keySet()) if (k.equals(pre1) || k.startsWith(pre2)) return a;
            }
        }
        String t = Emocoes.treino(p);
        return t != null ? t : "green";
    }

    // ===================== começo =====================

    /** A cada tick do jogador (RingPowers): começa a saga quando a Vida foi dominada; conta o tempo da cena. */
    public static void tick(ServerPlayer p) {
        Integer t = CENA.get(p.getUUID());
        if (t == null) {
            if (p.tickCount % 20 == 7 && !dividido(p) && Emocoes.dominada(p) && Emocoes.comAnelDaVida(p) && p.isAlive()) {
                CENA.put(p.getUUID(), -ESPERA);
            }
            return;
        }
        t++;
        CENA.put(p.getUUID(), t);
        boolean revendo = REVENDO.getOrDefault(p.getUUID(), false);
        if (t == 0) {
            if (!revendo && (!Emocoes.comAnelDaVida(p) || dividido(p))) { parar(p); return; }
            String tropa = revendo ? tag(p).getString("tropa") : tropaDeOrigem(p);
            if (!Emocoes.aspecto(tropa)) tropa = "green";
            tag(p).putString("tropa", tropa);
            ModNetwork.sendTo(p, new Cinema(tropa, revendo));
            LanternaMod.LOGGER.info("Lanterna: começa a cena do Necron para {} (fica a Tropa {})", p.getGameProfile().getName(), tropa);
        }
        if (t == TROCA && !revendo) dividir(p);
        if (t >= DURACAO + 10) {
            boolean primeira = !revendo;
            parar(p);
            if (primeira) fim(p);
        }
    }

    /** Começa a cena agora (comando / testes). revendo = só assistir de novo, sem mexer no anel. */
    public static boolean comecar(ServerPlayer p, boolean revendo) {
        if (CENA.containsKey(p.getUUID())) return false;
        if (!revendo && (!Emocoes.comAnelDaVida(p) || dividido(p))) return false;
        if (revendo && !dividido(p)) return false;
        CENA.put(p.getUUID(), -1);
        REVENDO.put(p.getUUID(), revendo);
        return true;
    }

    private static void parar(ServerPlayer p) {
        CENA.remove(p.getUUID());
        REVENDO.remove(p.getUUID());
    }

    /** Zera a saga do jogador (comando de teste). */
    public static void zerar(ServerPlayer p) {
        parar(p);
        CompoundTag t = tag(p);
        String origem = t.getString("origem");
        for (String k : t.getAllKeys().toArray(new String[0])) t.remove(k);
        if (!origem.isEmpty()) t.putString("origem", origem);
    }

    // ===================== a divisão =====================

    /** O Anel da Vida se desfaz; o anel da Tropa de origem volta para o dedo (com a mesma afinidade). */
    public static void dividir(ServerPlayer p) {
        if (dividido(p)) return;
        RingData data = ModCapabilities.get(p).orElse(null);
        if (data == null) return;
        String tropa = tag(p).getString("tropa");
        if (!Emocoes.aspecto(tropa)) tropa = tropaDeOrigem(p);
        CorpsDefinition def = CorpsManager.get(LanternaMod.id(tropa));
        if (def == null) return;

        ItemStack branco = EquipmentHelper.ring(p);
        int afinidade = 0;
        if (!branco.isEmpty() && PowerRingItem.getCorps(branco).equals(BuiltinCorps.WHITE.id())) {
            afinidade = Affinity.points(branco);
            EquipmentHelper.take(p, RingData.RING_SLOT);   // o Anel da Vida some (não volta para o inventário)
        }
        ItemStack anel = PowerRingItem.create(def.id(), def.color(), -1);
        PowerRingItem.setOwner(anel, p);
        if (afinidade > 0) anel.getOrCreateTag().putInt(Affinity.TAG, afinidade);
        EquipmentHelper.equipOrGive(p, RingData.RING_SLOT, anel);
        // a bateria da Tropa (o Anel da Vida não tinha)
        if (EquipmentHelper.battery(p).isEmpty()) {
            ItemStack bat = PowerBatteryItem.create(def.id(), def.color());
            if (!bat.isEmpty()) EquipmentHelper.equipOrGive(p, RingData.BATTERY_SLOT, bat);
        }
        // um segundo anel da mesma Tropa não vale mais: volta para o inventário
        ItemStack seg = data.segundoSlot().getStackInSlot(0);
        if (!seg.isEmpty() && PowerRingItem.getCorps(seg).getPath().equals(tropa)) {
            data.segundoSlot().setStackInSlot(0, ItemStack.EMPTY);
            if (!p.getInventory().add(seg)) p.drop(seg, false);
        }
        tag(p).putBoolean("dividido", true);
        tag(p).putLong("quando", p.level().getGameTime());
        data.setPowerActive(true);
        RingPowers.sync(p, data);
        LanternaMod.LOGGER.info("Lanterna: o Anel da Vida de {} se dividiu; ficou o anel {}", p.getGameProfile().getName(), tropa);
    }

    /** Depois da cena: as falas no chat e o aviso do que vem. */
    private static void fim(ServerPlayer p) {
        tag(p).putBoolean("visto", true);
        String tropa = tag(p).getString("tropa");
        int cor = Emocoes.cor(tropa);
        p.sendSystemMessage(Component.translatable("necron.lanterna.fim_1").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        p.sendSystemMessage(Component.translatable("necron.lanterna.fim_2", Component.translatable("corps.lanterna." + tropa))
                .withStyle(s -> s.withColor(cor)));
        p.sendSystemMessage(Component.translatable("necron.lanterna.fim_3").withStyle(ChatFormatting.DARK_PURPLE));
        p.sendSystemMessage(Component.translatable("necron.lanterna.rever").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ===================== proteção e saída =====================

    /** Ninguém se machuca enquanto assiste. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void protegido(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && CENA.getOrDefault(p.getUUID(), -1) >= 0) e.setCanceled(true);
    }

    /** Saiu no meio: a divisão acontece mesmo assim (dá para rever a cena depois). */
    @SubscribeEvent
    public static void saiu(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Integer t = CENA.get(p.getUUID());
        boolean revendo = REVENDO.getOrDefault(p.getUUID(), false);
        parar(p);
        if (t != null && t >= 0 && !revendo && !dividido(p)) {
            dividir(p);
            tag(p).putBoolean("visto", true);
        }
    }

    // ===================== rede =====================

    /** Servidor → cliente: abre a cutscene. */
    public record Cinema(String tropa, boolean revendo) {
        public static void encode(Cinema m, FriendlyByteBuf b) { b.writeUtf(m.tropa, 32); b.writeBoolean(m.revendo); }
        public static Cinema decode(FriendlyByteBuf b) { return new Cinema(b.readUtf(32), b.readBoolean()); }
        public static void handle(Cinema m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.lanterna.client.necron.CinemaNecron.abrir(m.tropa, m.revendo)));
            ctx.get().setPacketHandled(true);
        }
    }

    @Nullable
    public static Integer tempo(Player p) { return CENA.get(p.getUUID()); }

    private SagaNecron() {}
}
