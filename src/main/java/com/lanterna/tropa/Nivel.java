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
 * Nível do Lanterna (1 a 250; até a 0.56.0 era 150): a experiência da pessoa, separada da afinidade (que é o laço com aquele anel).
 *
 *  - Cada nível custa mais XP que o anterior (do 1 ao 50 são ~54 mil XP).
 *  - A cada 5 níveis: +1 coração. Construtos e voo gastam 1% menos carga por nível (até 40%).
 *    Força dos construtos e do soco: +1% por nível.
 *  - Postos: Recruta (1), Patrulheiro (10), Oficial (20), Veterano (30), Guarda de Honra (40).
 *    Cada posto dá a insígnia e um presente (Lanceira, Patrulha...).
 *  - Nível 50: começa a Prova da Emoção.
 *  - Os anéis só podem ser forjados a partir de um nível (veja {@link #nivelParaForjar}).
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.lanterna.LanternaMod.MODID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
public final class Nivel {
    public static final int MAX = 250;
    /** O antigo nível máximo (a fase dos lendários vai até ele; depois é a saga do Necron). */
    public static final int MAX_ANTIGO = 150;
    /** Nível da Prova da Emoção (e do antigo nível máximo). */
    public static final int PROVA = 50;
    private static final UUID VIDA = UUID.fromString("0a3f7e42-5b8c-4d1e-9f2a-6c7b8d9e0f11");
    private static final UUID ARMADURA = UUID.fromString("5d2b1c0e-8a4f-4b6d-9e3a-1f7c2d8b4a61");
    private static final UUID DUREZA = UUID.fromString("9c4e7a12-3b5d-4f8e-a6c1-2d9b0e7f3a52");

    /** XP para ir do nível n para o n+1. */
    public static int custo(int n) {
        // até o 50 como sempre; depois cresce mais devagar (do 50 ao 150 é a fase dos lendários)
        if (n <= PROVA) return 100 + 40 * n;
        if (n <= MAX_ANTIGO) return 100 + 40 * PROVA + 25 * (n - PROVA);
        // 0.56.1: do 150 ao 250 (a saga do Necron) cada nível custa bem mais: é preciso farmar muito
        return 100 + 40 * PROVA + 25 * (MAX_ANTIGO - PROVA) + 60 * (n - MAX_ANTIGO);
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

    /**
     * 0.56.1: quando o Anel da Vida se divide, o portador perde parte da força: o nível cai para no máximo "max"
     * (a experiência vai para o começo daquele nível).
     */
    public static void limitar(ServerPlayer p, int max) {
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null) return;
        int antes = nivel(d.xp());
        if (antes <= max) return;
        d.setXp(total(max));
        aplicar(p);
        p.sendSystemMessage(Component.translatable("nivel.lanterna.cortado", antes, max).withStyle(ChatFormatting.DARK_PURPLE));
        MissionManager.sync(p, d);
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
            case ODYM -> 6;
            case OA -> 12;
            case OKAARA -> 17;
            case YSMAULT -> 22;
            case ZAMARON -> 27;
            case KORUGAR -> 32;
            case NOK -> 37;
            case RYUT -> 40;
            case GENESE -> 46;
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
            player.sendSystemMessage(Component.translatable("nivel.lanterna.defesa", vidaExtra(after) / 2, Math.round(defesa(after) * 100),
                    Math.round(com.lanterna.LanternaConfig.PVP_DEFESA_ANEL.get() * 100)).withStyle(ChatFormatting.GRAY));
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 0.8f);
            aplicar(player);
            promover(player, data, after);
            if (after >= PROVA && before < PROVA) Prova.comecar(player);
        }
        MissionManager.sync(player, data);
    }

    /** Vida extra pelo nível: em corações inteiros. */
    public static int vidaExtra(int nivel) {
        double v = (nivel - 1) * com.lanterna.LanternaConfig.VIDA_POR_NIVEL.get();
        return (int) Math.floor(v / 2) * 2;
    }

    public static double armaduraExtra(int nivel) {
        return Math.round((nivel - 1) * com.lanterna.LanternaConfig.ARMADURA_POR_NIVEL.get() * 2) / 2.0;
    }

    public static double durezaExtra(int nivel) {
        return Math.round((nivel - 1) * com.lanterna.LanternaConfig.DUREZA_POR_NIVEL.get() * 2) / 2.0;
    }

    /**
     * Fração do dano que a defesa do nível tira, depois da armadura (0 a 0,35). Equivale aos pontos de armadura e
     * dureza que o nível dava antes (no nível 50 com a config padrão: cerca de 27%).
     */
    public static double defesa(int nivel) {
        // até 35% no nível 50; depois do 50 continua subindo até 60% no 150
        double base = Math.min(0.35, (armaduraExtra(Math.min(nivel, PROVA)) * 2.6 + durezaExtra(Math.min(nivel, PROVA)) * 1.2) / 100.0);
        return nivel <= PROVA ? base : Math.min(0.6, base + (nivel - PROVA) * 0.0025);
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOW)
    public static void defender(net.minecraftforge.event.entity.living.LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        var s = e.getSource();
        if (s.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR) || s.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        // contra jogadores a defesa já entra no equilíbrio do PvP (não conta duas vezes)
        if (com.lanterna.combate.Equilibrio.pvp(s, p)) return;
        double d = defesa(nivel(p));
        // 0.56.1: a Morte (o Necron e os servos dele) atravessa metade da defesa do nível
        var quem = s.getEntity();
        if (quem instanceof com.lanterna.necron.NecronEntity || (quem != null && com.lanterna.necron.Cratera.servo(quem))) d *= 0.5;
        if (d > 0) e.setAmount((float) (e.getAmount() * (1 - d)));
    }

    /** Vida, armadura e dureza pelo nível (chamado ao entrar, renascer e subir de nível). */
    public static void aplicar(ServerPlayer player) {
        int n = nivel(player);
        modificar(player, Attributes.MAX_HEALTH, VIDA, "lanterna_nivel", vidaExtra(n));
        // A defesa do nível não entra mais como pontos de armadura: o Minecraft limita a armadura em 30 e a dureza em 20,
        // então com armaduras fortes de outros mods (netherite encantada, MekaSuit...) o bônus do anel sumia. Agora ela
        // reduz o dano depois da armadura (ver defesa()), somando com qualquer armadura.
        modificar(player, Attributes.ARMOR, ARMADURA, "lanterna_nivel_armadura", 0);
        modificar(player, Attributes.ARMOR_TOUGHNESS, DUREZA, "lanterna_nivel_dureza", 0);
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private static void modificar(ServerPlayer player, net.minecraft.world.entity.ai.attributes.Attribute attr, UUID id, String nome, double valor) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (valor > 0) inst.addPermanentModifier(new AttributeModifier(id, nome, valor, AttributeModifier.Operation.ADDITION));
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

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }

    private static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    /** Atalho: carga com desconto do nível. */
    public static int custoComDesconto(Player p, int cost) {
        return Math.max(1, Math.round(cost * (1f - desconto(p)) * com.lanterna.construct.fonte.ArvoreDaLuz.custo(p)));
    }

    @Nullable
    public static RingData ring(Player p) {
        return ModCapabilities.get(p).orElse(null);
    }

    private Nivel() {}
}
