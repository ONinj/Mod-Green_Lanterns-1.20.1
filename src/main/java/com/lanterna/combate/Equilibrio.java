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
 * Equilíbrio de jogador contra jogador (refeito na 0.49.2: antes, no nível 150, a armadura do anel, a defesa do nível
 * e a resistência por nível se empilhavam e sobrava menos de 2% do golpe).
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

    /** Multiplicador da diferença de nível (entre -40% e +25%). */
    public static float diferenca(int nivelAtacante, int nivelAlvo) {
        float dif = (float) ((nivelAtacante - nivelAlvo) * LanternaConfig.PVP_DIFERENCA_NIVEL.get());
        return 1f + Math.max(-0.4f, Math.min(0.25f, dif));
    }

    /**
     * Igualador de nível (0.49.2): a vida cresce muito mais com o nível (20 → ~140) do que a força dos poderes
     * (×1 → ×2,5). Sem isto, dois jogadores de nível 150 precisariam de 3x mais golpes que dois de nível 30. Aqui o
     * dano sobe na mesma proporção, para a luta durar parecido em qualquer nível.
     */
    public static float igualador(Player alvo) {
        if (!LanternaConfig.PVP_IGUALAR.get()) return 1f;
        float vida = alvo.getMaxHealth() / 42f;
        float forca = Nivel.forca(alvo) / 1.3f;
        return Math.max(1f, vida / Math.max(1f, forca));
    }

    /**
     * Defesa de quem tem anel contra jogadores (0.49.2): no lugar de empilhar a armadura de netherite do anel, a
     * defesa do nível e a resistência por nível (que somadas cortavam mais de 98% do golpe no nível 150), uma
     * defesa só: pvp_defesa_anel (40%), mais até 15% pela armadura de verdade vestida por cima.
     */
    public static float defesaPvp(Player alvo) {
        double extra = Math.max(0, alvo.getArmorValue() - com.lanterna.tropa.ArmaduraAnel.ARMOR - Supremos.armaduraExtra(alvo));
        return (float) Math.min(0.9, LanternaConfig.PVP_DEFESA_ANEL.get() + Math.min(0.15, extra * 0.015));
    }

    /** Fração do golpe de um jogador que chega na vida de outro com anel (para o Perfil e os testes). */
    public static float fracao(int nivelAtacante, Player alvo, boolean anel) {
        float m = anel ? LanternaConfig.PVP_DANO.get().floatValue() : 1f;
        m *= diferenca(nivelAtacante, Nivel.nivel(alvo)) * igualador(alvo) * (1f - defesaPvp(alvo));
        var ra = com.lanterna.capability.ModCapabilities.get(alvo).orElse(null);
        if (ra != null && ra.hasRing()) m *= 1f - Supremos.resistenciaPvp(ra.corps());
        return m;
    }

    /** O golpe é de um jogador em outro jogador (para a defesa do nível não contar duas vezes). */
    public static boolean pvp(DamageSource src, Player alvo) {
        Player atk = atacante(src);
        return atk != null && atk != alvo;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer alvo)) return;
        Player atk = atacante(event.getSource());
        if (atk == null || atk == alvo) return;
        if (!LanternaConfig.PVP_MESMA_GUILDA.get() && com.lanterna.guilda.Guildas.mesmaGuilda(atk, alvo)) {
            event.setCanceled(true);
            return;
        }
        if (!LanternaConfig.PVP_MESMA_TROPA.get()) {
            var a = Factions.playerCorps(atk);
            if (a != null && a == Factions.playerCorps(alvo)) {
                event.setCanceled(true);
                return;
            }
        }
        var ra = com.lanterna.capability.ModCapabilities.get(alvo).orElse(null);
        boolean alvoComAnel = ra != null && ra.hasRing();
        boolean anel = doAnel(event.getSource(), atk);
        if (!alvoComAnel) {
            // sem anel: só o corte dos poderes e a diferença de nível; a armadura comum faz o resto
            event.setAmount(event.getAmount() * (anel ? LanternaConfig.PVP_DANO.get().floatValue() : 1f) * diferenca(Nivel.nivel(atk), Nivel.nivel(alvo)));
            return;
        }
        float alvoFinal = event.getAmount() * fracao(Nivel.nivel(atk), alvo, anel);
        // A armadura do Minecraft ainda vai cortar depois deste evento: compensa, para valer só a defesa de cima
        if (!event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) alvoFinal = antesDaArmadura(alvoFinal, alvo);
        event.setAmount(alvoFinal);
    }

    /** O dano que, depois da armadura e da dureza do alvo, vira exatamente "depois". */
    static float antesDaArmadura(float depois, Player alvo) {
        if (depois <= 0) return 0;
        float armor = alvo.getArmorValue();
        float tough = (float) alvo.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
        float lo = depois, hi = depois * 40f;
        if (net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(hi, armor, tough) < depois) return hi;
        for (int i = 0; i < 30; i++) {
            float mid = (lo + hi) / 2f;
            if (net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(mid, armor, tough) < depois) lo = mid; else hi = mid;
        }
        return hi;
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
