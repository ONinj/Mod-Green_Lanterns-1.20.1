package com.lanterna.ia;

import com.lanterna.LanternaMod;
import com.lanterna.lore.entity.boss.LoreBoss;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Chefe em fúria bate mais forte. */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BossIAEventos {
    @SubscribeEvent
    public static void bateu(LivingHurtEvent e) {
        if (e.getSource().getEntity() instanceof LoreBoss b && b.ia().emFuria()) e.setAmount(e.getAmount() * b.ia().danoExtra());
    }

    private BossIAEventos() {}
}
