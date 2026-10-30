package com.lanterna.combate;

import com.lanterna.LanternaConfig;
import com.lanterna.LanternaMod;
import com.lanterna.lore.Factions;
import com.lanterna.tropa.Nivel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Equilíbrio de jogador contra jogador.
 *
 * Os poderes do anel são feitos para derrubar monstros de 50 a 500 de vida; um jogador tem 20 a 60. Sem isto, um
 * martelo de nível 50 tira 50 de vida de uma vez (hit kill). Aqui, quando quem bate é um jogador (soco, construto,
 * técnica ou tiro da nave que ele pilota) e quem apanha também é:
 *  1. se o golpe é do anel (construto, técnica, tiro, soco com o anel), passa só uma parte do dano (pvp_dano);
 *     armas comuns (espada, machado, arco) não sofrem esse corte;
 *  2. quem apanha resiste mais quanto maior o nível dele (pvp_resistencia_por_nivel);
 *  3. a diferença de nível pesa um pouco (pvp_diferenca_nivel, entre -40% e +25%);
 *  4. depois da armadura, um golpe nunca tira mais que pvp_teto_golpe da vida máxima.
 * Contra monstros nada muda: lá valem só a vida, a armadura e a dureza que o nível dá (Nivel.aplicar).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Equilibrio {

    /** Ligado enquanto um poder do anel está ferindo alguém (construtos, técnicas, tiros de plasma). */
    private static final ThreadLocal<Boolean> PODER = ThreadLocal.withInitial(() -> false);

    /** Fere com um poder do anel (para o equilíbrio saber que não é uma arma comum). */
    public static boolean ferirComPoder(Entity alvo, DamageSource src, float dano) {
        boolean antes = PODER.get();
        PODER.set(true);
        try {
            return alvo.hurt(src, dano);
        } finally {
            PODER.set(antes);
        }
    }

    private static boolean doAnel(DamageSource src, Player atk) {
        if (PODER.get()) return true;
        Entity direto = src.getDirectEntity();
        if (direto == atk) return atk.getMainHandItem().isEmpty(); // soco com o anel
        return direto != null && !(direto instanceof net.minecraft.world.entity.projectile.AbstractArrow)
                && !(direto instanceof net.minecraft.world.entity.projectile.ThrowableItemProjectile);
    }

    /** O jogador por trás do golpe (direto, construto, projétil ou nave pilotada), se houver. */
    @Nullable
    public static Player atacante(DamageSource src) {
        Entity e = src.getEntity();
        if (e instanceof Player p) return p;
        if (e != null && e.getControllingPassenger() instanceof Player p) return p;
        return null;
    }

    /** Multiplicador do dano de um jogador num jogador (antes da armadura). */
    public static float multiplicador(int nivelAtacante, int nivelAlvo, boolean anel) {
        float m = anel ? LanternaConfig.PVP_DANO.get().floatValue() : 1f;
        m *= 1f - (float) Math.min(0.5, (nivelAlvo - 1) * LanternaConfig.PVP_RESISTENCIA_POR_NIVEL.get());
        float dif = (float) ((nivelAtacante - nivelAlvo) * LanternaConfig.PVP_DIFERENCA_NIVEL.get());
        m *= 1f + Math.max(-0.4f, Math.min(0.25f, dif));
        return m;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer alvo)) return;
        Player atk = atacante(event.getSource());
        if (atk == null || atk == alvo) return;
        if (!LanternaConfig.PVP_MESMA_TROPA.get()) {
            var a = Factions.playerCorps(atk);
            if (a != null && a == Factions.playerCorps(alvo)) {
                event.setCanceled(true);
                return;
            }
        }
        event.setAmount(event.getAmount() * multiplicador(Nivel.nivel(atk), Nivel.nivel(alvo), doAnel(event.getSource(), atk)));
    }

    /** Depois da armadura: nada de hit kill. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer alvo)) return;
        Player atk = atacante(event.getSource());
        if (atk == null || atk == alvo) return;
        float teto = alvo.getMaxHealth() * LanternaConfig.PVP_TETO_GOLPE.get().floatValue();
        if (event.getAmount() > teto) event.setAmount(teto);
    }

    private Equilibrio() {}
}
