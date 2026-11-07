package com.lanterna.energia;

import com.lanterna.LanternaMod;
import com.lanterna.baterias.Tropa;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** O que acontece no mundo alimenta os Geradores Emocionais por perto. */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EnergiaEventos {
    @SubscribeEvent
    public static void quebrou(BlockEvent.BreakEvent e) {
        if (e.getPlayer() != null && e.getLevel() instanceof Level l) GeradorEmocionalBE.aconteceu(l, e.getPos(), Tropa.VERDE, 6, 10);
    }

    @SubscribeEvent
    public static void colocou(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof Player && e.getLevel() instanceof Level l) GeradorEmocionalBE.aconteceu(l, e.getPos(), Tropa.VERDE, 4, 10);
    }

    @SubscribeEvent
    public static void morreu(LivingDeathEvent e) {
        Level l = e.getEntity().level();
        float v = e.getEntity() instanceof Player ? 200 : 15 + e.getEntity().getMaxHealth() * 2;
        GeradorEmocionalBE.aconteceu(l, e.getEntity().blockPosition(), Tropa.VERMELHO, v, 14);
    }

    @SubscribeEvent
    public static void nasceu(BabyEntitySpawnEvent e) {
        if (e.getChild() != null) GeradorEmocionalBE.aconteceu(e.getChild().level(), e.getChild().blockPosition(), Tropa.VIOLETA, 60, 14);
    }

    private EnergiaEventos() {}
}
