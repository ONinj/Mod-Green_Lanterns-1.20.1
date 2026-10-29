package com.lanterna.construct;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.power.EquipmentHelper;
import com.lanterna.power.RingPowers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * O anel cuida do portador, e cuida melhor quanto maior a afinidade:
 *  - mata a fome aos poucos (+1 de comida a cada 20 s no começo, a cada 5 s no nível máximo);
 *  - cura meio coração a cada 12 s no começo, a cada 2 s no nível máximo (gasta 1 de carga);
 *  - o soco de mão vazia ganha de +1,5 até +6,5 de dano (vezes 1,35 no Preto e 1,5 no Branco).
 * Tudo só com o anel ligado e com carga.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RingPassives {

    private static boolean powered(RingData ring) {
        return ring != null && ring.hasRing() && ring.powerActive() && ring.charge() > 0;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 3 || player.isCreative() || player.isSpectator() || !player.isAlive()) return;
        RingData ring = ModCapabilities.get(player).orElse(null);
        if (!powered(ring)) return;
        int lvl = Affinity.level(Affinity.points(EquipmentHelper.ring(player)));
        int sec = player.tickCount / 20;
        var food = player.getFoodData();
        if (sec % Affinity.foodSeconds(lvl) == 0 && food.needsFood()) {
            food.eat(1, 0.25f);
        }
        if (sec % Affinity.regenSeconds(lvl) == 0 && player.getHealth() < player.getMaxHealth()) {
            player.heal(1.0f);
            RingPowers.setCharge(player, ring, ring.charge() - 1);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (event.getSource().getDirectEntity() != player || !player.getMainHandItem().isEmpty()) return;
        RingData ring = ModCapabilities.get(player).orElse(null);
        if (!powered(ring)) return;
        int lvl = Affinity.level(Affinity.points(EquipmentHelper.ring(player)));
        event.setAmount(event.getAmount() + Affinity.punchBonus(lvl) * Constructs.corpsPower(ring));
    }

    private RingPassives() {}
}
