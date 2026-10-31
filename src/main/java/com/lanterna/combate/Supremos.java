package com.lanterna.combate;

import com.lanterna.LanternaConfig;
import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.mission.LoreCapability;
import com.lanterna.mission.LoreData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Os dois anéis supremos: o Branco (Vida) e o Preto (Morte). São os mais fortes do jogo em tudo:
 * mais vida, mais armadura, mais dano, voo mais rápido e mais resistência contra jogadores.
 * O Lanterna Negro também rouba vida com os golpes — mas tem fome: se passar dias sem ceifar um portador de anel,
 * o anel enfraquece até ele levar a morte a alguém.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Supremos {
    private static final UUID VIDA = UUID.fromString("7c1e5b2a-4d3f-4a8e-b9c6-0e1f2a3b4c51");
    private static final UUID ARMADURA = UUID.fromString("7c1e5b2a-4d3f-4a8e-b9c6-0e1f2a3b4c52");
    private static final UUID DUREZA = UUID.fromString("7c1e5b2a-4d3f-4a8e-b9c6-0e1f2a3b4c53");

    public static boolean branco(@Nullable ResourceLocation corps) {
        return BuiltinCorps.WHITE.id().equals(corps);
    }

    public static boolean preto(@Nullable ResourceLocation corps) {
        return BuiltinCorps.BLACK.id().equals(corps);
    }

    public static boolean supremo(@Nullable ResourceLocation corps) {
        return branco(corps) || preto(corps);
    }

    /** Multiplicador de dano dos poderes (o Preto faminto perde força). */
    public static float dano(ResourceLocation corps, @Nullable Player p) {
        if (branco(corps)) return 1.7f;
        if (preto(corps)) return p != null && faminto(p) ? 0.9f : 1.6f;
        return 1f;
    }

    /** Multiplicador da velocidade de voo (o cliente usa a Tropa do anel). */
    public static double voo(@Nullable ResourceLocation corps) {
        if (branco(corps)) return 1.4;
        if (preto(corps)) return 1.32;
        return 1.0;
    }

    /** Resistência extra contra jogadores (fração). */
    public static float resistenciaPvp(@Nullable ResourceLocation corps) {
        return branco(corps) ? 0.2f : preto(corps) ? 0.15f : 0f;
    }

    /** O Lanterna Negro passou tempo demais sem ceifar? */
    public static boolean faminto(Player p) {
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null || d.morte() < 3) return false;
        long dias = LanternaConfig.MORTE_DIAS_FOME.get();
        return dias > 0 && System.currentTimeMillis() - d.ultimaCeifa() > dias * 86_400_000L;
    }

    @Nullable
    private static ResourceLocation anel(Player p) {
        RingData r = ModCapabilities.get(p).orElse(null);
        return r != null && r.hasRing() ? r.corps() : null;
    }

    // ===================== Atributos (vida, armadura, dureza) =====================

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 20 != 13) return;
        ResourceLocation c = anel(p);
        boolean b = branco(c), pr = preto(c);
        mod(p, Attributes.MAX_HEALTH, VIDA, b ? 20 : pr ? 16 : 0);
        mod(p, Attributes.ARMOR, ARMADURA, b ? 6 : pr ? 5 : 0);
        mod(p, Attributes.ARMOR_TOUGHNESS, DUREZA, b ? 4 : pr ? 3 : 0);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        // O Branco cura devagar o tempo todo
        if (b && p.tickCount % 60 == 13 && p.getHealth() < p.getMaxHealth()) p.heal(1f);
        // Fome do Preto: aviso de hora em hora
        if (pr && p.tickCount % 72000 == 13 && faminto(p))
            p.sendSystemMessage(Component.translatable("morte.lanterna.fome").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
    }

    private static void mod(ServerPlayer p, Attribute attr, UUID id, double valor) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier atual = inst.getModifier(id);
        if (atual != null && atual.getAmount() == valor) return;
        inst.removeModifier(id);
        if (valor > 0) inst.addTransientModifier(new AttributeModifier(id, "lanterna_supremo", valor, AttributeModifier.Operation.ADDITION));
    }

    // ===================== Roubo de vida do Preto =====================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || event.getAmount() <= 0) return;
        Player atk = Equilibrio.atacante(event.getSource());
        if (atk == null || atk == event.getEntity()) return;
        RingData r = ModCapabilities.get(atk).orElse(null);
        if (r == null || !r.hasRing() || !r.powerActive() || !preto(r.corps())) return;
        atk.heal(Math.min(4f, event.getAmount() * 0.15f));
    }

    private Supremos() {}
}
