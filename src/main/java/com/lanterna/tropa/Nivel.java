package com.lanterna.tropa;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.lore.Factions;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.LoreCapability;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import com.lanterna.naves.NaveItem;
import com.lanterna.naves.NaveTipo;
import com.lanterna.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Nível do Lanterna (1 a 50): a experiência da pessoa, separada da afinidade (que é o laço com aquele anel).
 *
 *  - Cada nível custa mais XP que o anterior (do 1 ao 50 são ~54 mil XP).
 *  - A cada 5 níveis: +1 coração. Construtos e voo gastam 1% menos carga por nível (até 40%).
 *    Força dos construtos e do soco: +1% por nível.
 *  - Postos: Recruta (1), Patrulheiro (10), Oficial (20), Veterano (30), Guarda de Honra (40).
 *    Cada posto dá a insígnia e um presente (Lanceira, Patrulha...).
 *  - Nível 50: começa a Prova da Emoção.
 *  - Os anéis só podem ser forjados a partir de um nível (veja {@link #nivelParaForjar}).
 */
public final class Nivel {
    public static final int MAX = 50;
    private static final UUID VIDA = UUID.fromString("0a3f7e42-5b8c-4d1e-9f2a-6c7b8d9e0f11");

    /** XP para ir do nível n para o n+1. */
    public static int custo(int n) {
        return 100 + 40 * n;
    }

    /** XP total para chegar ao nível n. */
    public static int total(int n) {
        int t = 0;
        for (int i = 1; i < n; i++) t += custo(i);
        return t;
    }

    public static int nivel(int xp) {
        int n = 1;
        while (n < MAX && xp >= total(n + 1)) n++;
        return n;
    }

    /** Progresso (0..1) dentro do nível atual. */
    public static float progresso(int xp) {
        int n = nivel(xp);
        if (n >= MAX) return 1f;
        int a = total(n), b = total(n + 1);
        return (xp - a) / (float) (b - a);
    }

    public static int posto(int nivel) {
        return Math.min(4, nivel / 10);
    }

    public static int nivel(Player p) {
        return LoreCapability.get(p).map(d -> nivel(d.xp())).orElse(1);
    }

    /** Desconto de carga dos construtos e do voo (0 a 0,4). */
    public static float desconto(Player p) {
        return Math.min(0.4f, 0.01f * nivel(p));
    }

    /** Multiplicador de força (construtos e soco). */
    public static float forca(Player p) {
        return 1f + 0.01f * nivel(p);
    }

    /** Nível pedido para forjar o anel de cada planeta (o da própria Tropa não precisa ser forjado). */
    public static int nivelParaForjar(PlanetLore p) {
        return switch (p) {
            case ODYM -> 5;
            case OA -> 8;
            case OKAARA -> 12;
            case YSMAULT -> 16;
            case ZAMARON -> 20;
            case KORUGAR -> 25;
            case NOK -> 30;
            case RYUT -> 32;
            case GENESE -> 35;
        };
    }

    /** Nível pedido para o planeta na linha principal (e para chamar o boss dele). */
    public static int nivelDoPlaneta(PlanetLore p, @Nullable BuiltinCorps own) {
        if (own != null && p.corps == own) return 1;
        return nivelParaForjar(p);
    }

    public static void addXp(ServerPlayer player, int amount, @Nullable Component motivo) {
        LoreData data = LoreCapability.get(player).orElse(null);
        if (data == null || amount <= 0) return;
        int before = nivel(data.xp());
        data.setXp(data.xp() + amount);
        int after = nivel(data.xp());
        ModNetwork.sendTo(player, new TropaSyncPacket(amount, motivo == null ? "" : motivo.getString()));
        if (after > before) {
            player.sendSystemMessage(Component.translatable("nivel.lanterna.subiu", after).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 0.8f);
            aplicar(player);
            promover(player, data, after);
            if (after >= MAX && before < MAX) Prova.comecar(player);
        }
        MissionManager.sync(player, data);
    }

    /** Vida extra pelo nível (chamado ao entrar, renascer e subir de nível). */
    public static void aplicar(ServerPlayer player) {
        AttributeInstance hp = player.getAttribute(Attributes.MAX_HEALTH);
        if (hp == null) return;
        hp.removeModifier(VIDA);
        int extra = (nivel(player) / 5) * 2;
        if (extra > 0) hp.addPermanentModifier(new AttributeModifier(VIDA, "lanterna_nivel", extra, AttributeModifier.Operation.ADDITION));
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    /** Entrega as recompensas dos postos ainda não entregues. */
    private static void promover(ServerPlayer player, LoreData data, int nivel) {
        int posto = posto(nivel);
        while (data.postoEntregue() < posto) {
            int p = data.postoEntregue() + 1;
            data.setPostoEntregue(p);
            BuiltinCorps c = Factions.playerCorps(player);
            String cor = c != null && c != BuiltinCorps.WHITE ? c.id().getPath() : "green";
            player.sendSystemMessage(Component.translatable("posto.lanterna.promovido", Component.translatable("posto.lanterna." + p))
                    .withStyle(s -> s.withColor(c != null ? c.color() : 0xFFD040).withBold(true)));
            player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
            ItemStack ins = new ItemStack(TropaItems.insignia(p));
            if (c != null) ins.getOrCreateTag().putString("Corps", c.id().toString());
            give(player, ins);
            switch (p) {
                case 1 -> give(player, NaveItem.of(NaveTipo.NAVE_LANCEIRA, cor));
                case 2 -> give(player, NaveItem.of(NaveTipo.NAVE_PATRULHA, cor));
                case 3 -> {
                    give(player, new ItemStack(TropaItems.NUCLEO_PROPULSOR.get(), 3));
                    give(player, new ItemStack(Items.DIAMOND, 8));
                }
                case 4 -> give(player, new ItemStack(com.lanterna.lore.LoreItems.MERITO.get(), 16));
                default -> {}
            }
        }
    }

    private static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    /** Atalho: carga com desconto do nível. */
    public static int custoComDesconto(Player p, int cost) {
        return Math.max(1, Math.round(cost * (1f - desconto(p))));
    }

    @Nullable
    public static RingData ring(Player p) {
        return ModCapabilities.get(p).orElse(null);
    }

    private Nivel() {}
}
