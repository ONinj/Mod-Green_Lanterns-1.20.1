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
 * mais vida, mais armadura, mais dano, voo mais rápido e mais resistência contra jogadores. Desde a 0.49.2 o Preto
 * passa o Branco em tudo, e só o Branco (ou um portador de nível igual ou maior) consegue feri-lo.
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
        if (p != null && !preto(corps) && com.lanterna.morte.AnelPreto.portador(p)) return 1.15f;
        // 0.49.2: o Preto é o mais forte de todos (faminto não enfraquece: entra em frenesi, morte/Frenesi)
        if (preto(corps)) return 2.0f;
        if (branco(corps)) return 1.7f;
        return 1f;
    }

    /** Multiplicador da velocidade de voo (o cliente usa a Tropa do anel). */
    public static double voo(@Nullable ResourceLocation corps) {
        if (preto(corps)) return 1.45;
        if (branco(corps)) return 1.4;
        return 1.0;
    }

    /** Resistência extra contra jogadores (fração). */
    public static float resistenciaPvp(@Nullable ResourceLocation corps) {
        return preto(corps) ? 0.25f : branco(corps) ? 0.2f : 0f;
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
        boolean portador = !pr && !b && com.lanterna.morte.AnelPreto.portador(p);
        mod(p, Attributes.MAX_HEALTH, VIDA, pr ? VIDA_PRETO : b ? 20 : portador ? 8 : 0);
        mod(p, Attributes.ARMOR, ARMADURA, armaduraDe(c));
        mod(p, Attributes.ARMOR_TOUGHNESS, DUREZA, pr ? 5 : b ? 4 : 0);
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

    /** Vida a mais do Lanterna Negro (20 corações): a maior do jogo. */
    public static final int VIDA_PRETO = 40;

    private static double armaduraDe(@Nullable ResourceLocation c) {
        return preto(c) ? 7 : branco(c) ? 6 : 0;
    }

    /** A armadura que o anel supremo dá (para o PvP não contar como armadura vestida). */
    public static double armaduraExtra(Player p) {
        return armaduraDe(anel(p));
    }

    // ===================== Ninguém fere a Morte =====================

    /**
     * 0.49.2: o Lanterna Negro não pode ser ferido por outros portadores de anel. Só o Anel Branco o fere, ou um
     * portador de nível igual ou maior que o dele. Monstros, chefes e quem não tem anel ferem normalmente.
     */
    @Nullable
    public static Component imuneA(Player alvo, net.minecraft.world.damagesource.DamageSource src) {
        ResourceLocation c = anel(alvo);
        if (!preto(c)) return null;
        int nivelAlvo = com.lanterna.tropa.Nivel.nivel(alvo);
        Player atk = Equilibrio.atacante(src);
        if (atk != null && atk != alvo) {
            ResourceLocation ca = anel(atk);
            if (ca == null || branco(ca)) return null;
            if (com.lanterna.tropa.Nivel.nivel(atk) >= nivelAlvo) return null;
            return Component.translatable("morte.lanterna.imune", nivelAlvo);
        }
        if (src.getEntity() instanceof com.lanterna.especies.LanternaEntity le && le.tropa() != null) {
            if (le.tropa() == BuiltinCorps.WHITE) return null;
            // os lendários lutam no nível 60
            if (com.lanterna.lendarios.Lendarios.de(le) != null && com.lanterna.lendarios.Lendarios.NIVEL >= nivelAlvo) return null;
            return Component.empty();
        }
        return null;
    }

    private static final java.util.Map<UUID, Long> AVISO = new java.util.HashMap<>();

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer alvo)) return;
        Component msg = imuneA(alvo, event.getSource());
        if (msg == null) return;
        event.setCanceled(true);
        var l = alvo.serverLevel();
        if (alvo.tickCount % 3 == 0)
            l.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, alvo.getX(), alvo.getY() + 1, alvo.getZ(), 6, 0.3, 0.5, 0.3, 0.01);
        if (Equilibrio.atacante(event.getSource()) instanceof ServerPlayer atk && !msg.getString().isEmpty()) {
            long agora = l.getGameTime();
            Long antes = AVISO.get(atk.getUUID());
            if (antes == null || agora - antes > 40) {
                AVISO.put(atk.getUUID(), agora);
                atk.displayClientMessage(msg.copy().withStyle(ChatFormatting.DARK_GRAY), true);
                l.playSound(null, alvo.blockPosition(), net.minecraft.sounds.SoundEvents.SOUL_ESCAPE, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 0.6f);
            }
        }
    }

    // ===================== Roubo de vida do Preto =====================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || event.getAmount() <= 0) return;
        Player atk = Equilibrio.atacante(event.getSource());
        if (atk == null || atk == event.getEntity()) return;
        RingData r = ModCapabilities.get(atk).orElse(null);
        if (r == null || !r.hasRing() || !r.powerActive()) return;
        if (preto(r.corps())) atk.heal(Math.min(4f, event.getAmount() * 0.15f));
        else if (r.corrupcao() == 2) atk.heal(Math.min(1.5f, event.getAmount() * 0.06f)); // Portador Negro: um pouco
    }

    private Supremos() {}
}
