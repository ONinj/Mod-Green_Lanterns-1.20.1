package com.lanterna.construct.fonte;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.construct.ConstructType;
import com.lanterna.fonte.FonteDados;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A Árvore da Luz: cada Tropa tem a sua (3 ramos de 6-7 degraus: Ataque, Proteção e Domínio), com os 10 construtos
 * próprios e 10 passivas. Os nós custam Essência da Fonte; um degrau só abre depois dos de baixo no mesmo ramo, e
 * a árvore só cresce depois que a Bateria Central da Tropa reconhece o Lanterna (Selo da Fonte).
 *
 * Os nós ficam guardados como "tropa:nó" (o jogador pode ter árvores de várias Tropas; vale a do anel em uso).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArvoreDaLuz {
    private static final UUID COURACA = UUID.fromString("6a1f0c2e-9a3b-4f5e-8d6c-1b2a3c4d5e6f");

    @Nullable
    public static String corps(Player p) {
        RingData r = ModCapabilities.get(p).orElse(null);
        return r == null || !r.hasRing() ? null : r.corps().getPath();
    }

    public static boolean tem(Player p, String no) {
        String c = corps(p);
        return c != null && FonteDados.temNo(p, c + ":" + no);
    }

    /** O construto da árvore está liberado para o anel que o jogador usa? */
    public static boolean liberado(Player p, ConstructType t) {
        return !t.arvore || tem(p, t.id);
    }

    // ---- passivas ----

    public static float bonusDano(Player p) { return (tem(p, "dano1") ? 0.15f : 0) + (tem(p, "dano2") ? 0.15f : 0) + (com.lanterna.lore.entity.boss.ChefeFonte.temCoracao(p) ? 0.1f : 0); }
    public static float custo(Player p) { return tem(p, "custo1") ? 0.9f : 1f; }
    public static float recarga(Player p) { return tem(p, "recarga1") ? 0.85f : 1f; }
    public static float carga(Player p) { return 1f + (tem(p, "carga1") ? 0.2f : 0) + (tem(p, "carga2") ? 0.2f : 0) + (com.lanterna.lore.entity.boss.ChefeFonte.temCoracao(p) ? 0.2f : 0); }
    public static float duracao(Player p) { return tem(p, "duracao1") ? 1.3f : 1f; }
    public static float regen(Player p) { return tem(p, "regen1") ? 1.5f : 1f; }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 3) return;
        var armor = p.getAttribute(Attributes.ARMOR);
        if (armor == null) return;
        RingData r = ModCapabilities.get(p).orElse(null);
        boolean quer = r != null && r.hasRing() && r.powerActive() && tem(p, "couraca1");
        boolean tem = armor.getModifier(COURACA) != null;
        if (quer && !tem) armor.addTransientModifier(new AttributeModifier(COURACA, "lanterna_couraca", 4, AttributeModifier.Operation.ADDITION));
        if (!quer && tem) armor.removeModifier(COURACA);
    }

    // ---- comprar ----

    public static void comprar(ServerPlayer p, String no) {
        String c = corps(p);
        if (c == null) return;
        List<ArvoreDados.No> arvore = ArvoreDados.ARVORES.get(c);
        if (arvore == null) { p.displayClientMessage(Component.translatable("arvore.lanterna.sem_arvore"), true); return; }
        ArvoreDados.No n = arvore.stream().filter(x -> x.id().equals(no)).findFirst().orElse(null);
        if (n == null || FonteDados.temNo(p, c + ":" + no)) return;
        com.lanterna.baterias.Tropa t = com.lanterna.fonte.FonteChamado.tropaDoAnel(p);
        if (t == null || !FonteDados.temSelo(p, t.nome)) { p.displayClientMessage(Component.translatable("arvore.lanterna.sem_selo"), true); return; }
        for (ArvoreDados.No x : arvore)
            if (x.ramo() == n.ramo() && x.tier() < n.tier() && !FonteDados.temNo(p, c + ":" + x.id())) {
                p.displayClientMessage(Component.translatable("arvore.lanterna.antes"), true);
                return;
            }
        int e = FonteDados.essencia(p);
        if (e < n.custo()) { p.displayClientMessage(Component.translatable("arvore.lanterna.sem_essencia", n.custo()), true); return; }
        FonteDados.essencia(p, e - n.custo());
        FonteDados.darNo(p, c + ":" + no);
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.8f + 0.1f * n.tier());
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    /** Nós da árvore do anel em uso (para o cliente). */
    public static String nosDoAnel(Player p) {
        String c = corps(p);
        if (c == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String s : FonteDados.arvore(p)) if (s.startsWith(c + ":")) { if (sb.length() > 0) sb.append(','); sb.append(s.substring(c.length() + 1)); }
        return sb.toString();
    }

    /** Cliente → servidor: comprar um nó. */
    public record Comprar(String no) {
        public static void encode(Comprar m, FriendlyByteBuf b) { b.writeUtf(m.no, 64); }
        public static Comprar decode(FriendlyByteBuf b) { return new Comprar(b.readUtf(64)); }
        public static void handle(Comprar m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> { if (ctx.get().getSender() != null) comprar(ctx.get().getSender(), m.no); });
            ctx.get().setPacketHandled(true);
        }
    }

    private ArvoreDaLuz() {}
}
