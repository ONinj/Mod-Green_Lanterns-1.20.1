package com.lanterna.tropa;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * O anel protege como uma armadura de netherite completa: quem tem um anel ganha 20 de armadura, 12 de dureza e 40%
 * de resistência a empurrão, mesmo sem armadura nenhuma. Soma com qualquer armadura (o Minecraft limita a armadura
 * total em 30 e a dureza em 20).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArmaduraAnel {
    private static final UUID ARMADURA = UUID.fromString("3b6e1f0a-7c2d-4e9b-8a51-0d4c2e7f9a11");
    private static final UUID DUREZA = UUID.fromString("8f2a5c14-1e7b-4d3a-9c62-5b0e8d1f3a22");
    private static final UUID EMPURRAO = UUID.fromString("c41d7e93-5a2f-4b8c-a1d0-6e9f2b3c4d33");
    public static final double ARMOR = 20, TOUGHNESS = 12, KNOCKBACK = 0.4;

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 3) return;
        boolean anel = ModCapabilities.get(p).map(d -> d.hasRing()).orElse(false);
        aplicar(p, Attributes.ARMOR, ARMADURA, "lanterna_anel_armadura", anel ? ARMOR : 0);
        aplicar(p, Attributes.ARMOR_TOUGHNESS, DUREZA, "lanterna_anel_dureza", anel ? TOUGHNESS : 0);
        aplicar(p, Attributes.KNOCKBACK_RESISTANCE, EMPURRAO, "lanterna_anel_empurrao", anel ? KNOCKBACK : 0);
    }

    private static void aplicar(ServerPlayer p, Attribute attr, UUID id, String nome, double valor) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier atual = inst.getModifier(id);
        if (atual != null && atual.getAmount() == valor) return;
        if (atual != null) inst.removeModifier(id);
        if (valor > 0) inst.addTransientModifier(new AttributeModifier(id, nome, valor, AttributeModifier.Operation.ADDITION));
    }

    private ArmaduraAnel() {}
}
