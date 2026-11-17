package com.lanterna.vida;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.construct.ConstructType;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.item.PowerRingItem;
import com.lanterna.lendarios.Lendario;
import com.lanterna.lore.LoreItems;
import com.lanterna.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

/**
 * 0.53: a Árvore das Emoções do Anel da Vida.
 *
 * O Anel da Vida tem os construtos de todas as Tropas, mas no começo só 3 de cada aspecto emocional (Vontade, Medo,
 * Raiva, Avareza, Esperança, Compaixão e Amor). Cada aspecto se desperta com a Insígnia do Lanterna lendário daquela
 * Tropa e depois sobe até o nível 6 pagando Fragmentos Lendários (dos lendários e dos Bosses das Tropas). Cada nível abre
 * mais construtos daquela Tropa e deixa o anel mais forte.
 *
 * Treinar um aspecto: o anel continua branco, mas o traje e a luz dos construtos passam a ser os daquela Tropa.
 *
 * Com os 7 aspectos despertos dá para preparar o corpo para Dois Anéis: um segundo anel (de outra Tropa) num slot a
 * mais, que empresta os construtos de suporte dela e pinta metade do traje. Isso fica para sempre, mesmo sem o Anel da
 * Vida.
 *
 * Os dados ficam no NBT persistente do jogador (sobrevive à morte).
 */
public final class Emocoes {
    /** Os 7 aspectos, na ordem do espectro. */
    public static final String[] ASPECTOS = {"red", "orange", "yellow", "green", "blue", "indigo", "violet"};
    public static final int MAX = 6;
    /** Fragmentos Lendários para chegar a cada nível (o 1 é a Insígnia). */
    public static final int[] CUSTO = {0, 0, 3, 5, 8, 12, 16};
    public static final int CUSTO_DOIS = 20;
    /** Construtos de cada aspecto liberados no começo. */
    public static final int INICIAIS = 3;

    private static final String TAG = "lanterna_emocoes";

    // ===================== dados =====================

    private static CompoundTag tag(Player p) {
        CompoundTag pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!p.getPersistentData().contains(Player.PERSISTED_NBT_TAG)) p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
        if (!pers.contains(TAG)) pers.put(TAG, new CompoundTag());
        return pers.getCompound(TAG);
    }

    public static int nivel(Player p, String a) { return tag(p).getCompound("nivel").getInt(a); }
    @Nullable public static String treino(Player p) { String t = tag(p).getString("treino"); return t.isEmpty() ? null : t; }
    public static boolean doisAneis(Player p) { return tag(p).getBoolean("dois"); }
    public static boolean dominada(Player p) { return tag(p).getBoolean("dominada"); }

    private static void nivel(Player p, String a, int n) {
        CompoundTag t = tag(p);
        CompoundTag nv = t.getCompound("nivel");
        nv.putInt(a, n);
        t.put("nivel", nv);
    }

    public static boolean aspecto(String a) {
        for (String x : ASPECTOS) if (x.equals(a)) return true;
        return false;
    }

    public static int total(java.util.function.ToIntFunction<String> nivel) {
        int s = 0;
        for (String a : ASPECTOS) s += nivel.applyAsInt(a);
        return s;
    }

    // ===================== construtos =====================

    private static final Map<String, List<ConstructType>> POR_TROPA = new HashMap<>();

    /** Os construtos de uma Tropa, do mais simples ao mais forte. */
    public static List<ConstructType> daTropa(String corps) {
        return POR_TROPA.computeIfAbsent(corps, c -> {
            List<ConstructType> l = new ArrayList<>();
            for (ConstructType t : ConstructType.values()) if (c.equals(t.corps) && t != ConstructType.ESCUDO) l.add(t);
            l.sort(Comparator.comparingInt((ConstructType t) -> t.unlockLevel).thenComparingInt(Enum::ordinal));
            return l;
        });
    }

    /** Quantos construtos de uma Tropa o Anel da Vida tem com o aspecto neste nível. */
    public static int limite(String corps, int nivel) {
        int total = daTropa(corps).size();
        if (nivel <= 1) return Math.min(INICIAIS, total);
        return Math.min(total, INICIAIS + (int) Math.ceil((total - INICIAIS) * (nivel - 1) / (double) (MAX - 1)));
    }

    public static boolean dentroDoLimite(ConstructType t, int nivel) {
        int i = daTropa(t.corps).indexOf(t);
        return i >= 0 && i < limite(t.corps, nivel);
    }

    /** O contexto do jogador no servidor (níveis e o segundo anel). */
    public static ConstructType.Contexto contexto(ServerPlayer p) {
        RingData r = ModCapabilities.get(p).orElse(null);
        String seg = r == null || r.segundo().isEmpty() ? null : r.segundo();
        return new ConstructType.Contexto() {
            public int nivel(String a) { return Emocoes.nivel(p, a); }
            public String segundo() { return seg; }
        };
    }

    /** Anel da Vida: +1,5% de força por nível de aspecto (até +63% com tudo no 6). */
    public static float bonusForca(ServerPlayer p, RingData ring) {
        if (!ring.hasRing() || !BuiltinCorps.WHITE.id().equals(ring.corps())) return 1f;
        return 1f + 0.015f * total(a -> nivel(p, a));
    }

    public static boolean comAnelDaVida(Player p) {
        RingData r = ModCapabilities.get(p).orElse(null);
        return r != null && r.hasRing() && BuiltinCorps.WHITE.id().equals(r.corps());
    }

    /** A Tropa cujo traje o jogador veste agora pelo treino (só com o Anel da Vida). */
    @Nullable
    public static String trajeDoTreino(Player p) {
        String t = treino(p);
        return t != null && comAnelDaVida(p) && nivel(p, t) >= 1 ? t : null;
    }

    /** O segundo anel vale? (corpo preparado, anel de outra Tropa emocional, do próprio jogador) */
    public static boolean segundoValido(Player p, RingData data, ItemStack s) {
        if (s.isEmpty() || !(s.getItem() instanceof PowerRingItem) || !doisAneis(p) || !data.hasRing()) return false;
        String c = PowerRingItem.getCorps(s).getPath();
        return aspecto(c) && !c.equals(data.corps().getPath()) && PowerRingItem.canBeWornBy(s, p) && PowerRingItem.getCorrupcao(s) == 0;
    }

    /** A Insígnia que desperta um aspecto (a do lendário daquela Tropa). */
    @Nullable
    public static Item insignia(String a) {
        for (Lendario l : Lendario.values()) if (l.corps.id().getPath().equals(a)) return LoreItems.INSIGNIAS.get(l).get();
        return null;
    }

    @Nullable
    public static Lendario lendario(String a) {
        for (Lendario l : Lendario.values()) if (l.corps.id().getPath().equals(a)) return l;
        return null;
    }

    // ===================== ações =====================

    private static int contar(Player p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static void gastar(Player p, Item item, int qtd) {
        for (ItemStack s : p.getInventory().items) {
            if (qtd <= 0) break;
            if (!s.is(item)) continue;
            int t = Math.min(qtd, s.getCount());
            s.shrink(t);
            qtd -= t;
        }
    }

    private static void msg(ServerPlayer p, String chave, Object... args) {
        p.displayClientMessage(Component.translatable(chave, args), true);
    }

    public static void subir(ServerPlayer p, String a) {
        if (!aspecto(a)) return;
        if (!comAnelDaVida(p)) { msg(p, "emocoes.lanterna.so_vida"); return; }
        int n = nivel(p, a);
        if (n >= MAX) return;
        if (n == 0) {
            Item ins = insignia(a);
            if (ins == null || contar(p, ins) < 1) {
                Lendario l = lendario(a);
                msg(p, "emocoes.lanterna.falta_insignia", l == null ? "?" : Component.translatable("lendario.lanterna." + l.id));
                return;
            }
            gastar(p, ins, 1);
        } else {
            int custo = CUSTO[n + 1];
            if (contar(p, LoreItems.FRAGMENTO_LENDARIO.get()) < custo) { msg(p, "emocoes.lanterna.faltam_fragmentos", custo); return; }
            gastar(p, LoreItems.FRAGMENTO_LENDARIO.get(), custo);
        }
        nivel(p, a, n + 1);
        if (n == 0 && treino(p) == null) tag(p).putString("treino", a);
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.7f + 0.12f * n);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.3f);
        Component nome = Component.translatable("emocoes.lanterna.aspecto." + a);
        p.sendSystemMessage(Component.translatable(n == 0 ? "emocoes.lanterna.despertou" : "emocoes.lanterna.subiu", nome, n + 1)
                .withStyle(s -> s.withColor(cor(a))));
        verificarDominio(p);
        sync(p);
    }

    public static void treinar(ServerPlayer p, String a) {
        if (!comAnelDaVida(p)) { msg(p, "emocoes.lanterna.so_vida"); return; }
        if (a.isEmpty() || a.equals(treino(p))) {
            tag(p).putString("treino", "");
            msg(p, "emocoes.lanterna.treino_fim");
        } else {
            if (!aspecto(a) || nivel(p, a) < 1) { msg(p, "emocoes.lanterna.treino_dormente"); return; }
            tag(p).putString("treino", a);
            msg(p, "emocoes.lanterna.treino", Component.translatable("emocoes.lanterna.aspecto." + a));
            p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 1.4f);
        }
        sync(p);
    }

    public static void prepararDois(ServerPlayer p) {
        if (doisAneis(p)) return;
        if (!comAnelDaVida(p)) { msg(p, "emocoes.lanterna.so_vida"); return; }
        for (String a : ASPECTOS) if (nivel(p, a) < 1) { msg(p, "emocoes.lanterna.dois_falta"); return; }
        if (contar(p, LoreItems.FRAGMENTO_LENDARIO.get()) < CUSTO_DOIS) { msg(p, "emocoes.lanterna.faltam_fragmentos", CUSTO_DOIS); return; }
        gastar(p, LoreItems.FRAGMENTO_LENDARIO.get(), CUSTO_DOIS);
        tag(p).putBoolean("dois", true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7f, 1.2f);
        p.sendSystemMessage(Component.translatable("emocoes.lanterna.dois_pronto").withStyle(ChatFormatting.WHITE));
        sync(p);
    }

    /** Os 7 aspectos no máximo: a Vida está dominada (o começo da Noite Mais Densa vem depois). */
    private static void verificarDominio(ServerPlayer p) {
        if (dominada(p)) return;
        for (String a : ASPECTOS) if (nivel(p, a) < MAX) return;
        tag(p).putBoolean("dominada", true);
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                Component.translatable("emocoes.lanterna.dominada_titulo").withStyle(ChatFormatting.WHITE)));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                Component.translatable("emocoes.lanterna.dominada_sub").withStyle(ChatFormatting.GRAY)));
        p.sendSystemMessage(Component.translatable("emocoes.lanterna.dominada_msg").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 0.6f);
        LanternaMod.LOGGER.info("Lanterna: {} dominou os sete aspectos do Anel da Vida", p.getGameProfile().getName());
    }

    public static int cor(String a) {
        var def = com.lanterna.corps.CorpsManager.get(LanternaMod.id(a));
        return def == null ? 0xFFFFFF : def.color();
    }

    // ===================== rede =====================

    private static final Map<UUID, Integer> ENVIADO = new HashMap<>();

    /** Manda o estado (e de vez em quando confere se mudou). */
    public static void sync(ServerPlayer p) {
        CompoundTag t = tag(p).copy();
        ENVIADO.put(p.getUUID(), t.hashCode());
        ModNetwork.sendTo(p, new Estado(t));
    }

    public static void tick(ServerPlayer p) {
        if (p.tickCount % 40 != 0) return;
        Integer h = ENVIADO.get(p.getUUID());
        if (h == null || h != tag(p).hashCode()) sync(p);
    }

    public record Estado(CompoundTag tag) {
        public static void encode(Estado m, FriendlyByteBuf b) { b.writeNbt(m.tag); }
        public static Estado decode(FriendlyByteBuf b) { CompoundTag t = b.readNbt(); return new Estado(t == null ? new CompoundTag() : t); }
        public static void handle(Estado m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.lanterna.client.vida.ClientEmocoes.receber(m.tag)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Cliente → servidor: "subir" (despertar/fortalecer), "treinar" ou "dois". */
    public record Acao(String acao, String aspecto) {
        public static void encode(Acao m, FriendlyByteBuf b) { b.writeUtf(m.acao, 16); b.writeUtf(m.aspecto, 16); }
        public static Acao decode(FriendlyByteBuf b) { return new Acao(b.readUtf(16), b.readUtf(16)); }
        public static void handle(Acao m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p == null) return;
                switch (m.acao) {
                    case "subir" -> subir(p, m.aspecto);
                    case "treinar" -> treinar(p, m.aspecto);
                    case "dois" -> prepararDois(p);
                    default -> { }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    private Emocoes() {}
}
