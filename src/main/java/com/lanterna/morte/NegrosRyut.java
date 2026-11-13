package com.lanterna.morte;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.combate.Supremos;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.fonte.LugarDeSurgir;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.LoreCapability;
import com.lanterna.tropa.Nivel;
import com.lanterna.LanternaConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ryut, o planeta da Morte: quem chega lá com um anel (nível mínimo da corrupção, anel ainda limpo) é caçado de vez em
 * quando por Lanternas Negros que se levantam do chão. Morrer para eles tem a chance do ceifador (8%), então quem
 * joga sozinho também consegue o caminho do Anel Preto, indo atrás dele de propósito.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NegrosRyut {
    private static final int INTERVALO = 20 * 45;

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % INTERVALO != 11) return;
        if (p.isSpectator() || p.isCreative()) return;
        ServerLevel l = p.serverLevel();
        if (PlanetLore.byDimension(l.dimension()) != PlanetLore.RYUT || l.getDifficulty() == Difficulty.PEACEFUL) return;
        var ring = ModCapabilities.get(p).orElse(null);
        var d = LoreCapability.get(p).orElse(null);
        if (ring == null || d == null || !ring.hasRing() || d.morte() != 0 || Supremos.supremo(ring.corps())) return;
        if (Nivel.nivel(p) < LanternaConfig.MORTE_NIVEL_MIN.get()) return;
        if (l.getEntitiesOfClass(LanternaEntity.class, p.getBoundingBox().inflate(48), x -> x.tropa() == BuiltinCorps.BLACK).size() >= 2) return;
        BlockPos onde = LugarDeSurgir.achar(l, p.blockPosition(), 14, 22, null);
        LanternaEntity n = LanternaEntity.lanterna(l, BuiltinCorps.BLACK, l.random.nextInt(4) == 0);
        if (n == null) return;
        n.moveTo(onde.getX() + 0.5, onde.getY(), onde.getZ() + 0.5, l.random.nextFloat() * 360, 0);
        l.addFreshEntity(n);
        n.setTarget(p);
        l.sendParticles(ParticleTypes.SOUL, n.getX(), n.getY() + 0.5, n.getZ(), 40, 0.5, 1, 0.5, 0.05);
        l.sendParticles(ParticleTypes.LARGE_SMOKE, n.getX(), n.getY() + 0.2, n.getZ(), 20, 0.6, 0.3, 0.6, 0.02);
        l.playSound(null, n.blockPosition(), SoundEvents.WITHER_SKELETON_AMBIENT, SoundSource.HOSTILE, 1.4f, 0.6f);
        if (!p.getPersistentData().getBoolean("lanterna_aviso_negros")) {
            p.getPersistentData().putBoolean("lanterna_aviso_negros", true);
            p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("morte.lanterna.negros_ryut")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC));
        }
    }

    private NegrosRyut() {}
}
