package com.lanterna.mogo;

import com.lanterna.LanternaMod;
import com.lanterna.fonte.FonteDados;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A Segunda Chance: quem tem o Vínculo com o Mogo (fim da linha do Mogo) e é Lanterna Verde, ao morrer, é salvo uma vez
 * por dia (tempo real). O Mogo aparece no céu, segura o jogador num feixe e puxa até a superfície dele.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SegundaChance {
    public static final String VINCULO = "mogo:vinculo";
    public static final String TAG = "lanterna_mogo_segunda";
    public static final long RECARGA_MS = 24L * 60L * 60L * 1000L;

    public static boolean vinculado(ServerPlayer p) {
        return FonteDados.chamado(p, VINCULO) >= 1;
    }

    public static long restante(ServerPlayer p) {
        long ultimo = p.getPersistentData().getLong(TAG);
        return Math.max(0, ultimo + RECARGA_MS - System.currentTimeMillis());
    }

    public static void resetar(ServerPlayer p) {
        p.getPersistentData().remove(TAG);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void morreu(LivingDeathEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer p)) return;
        if (!vinculado(p) || !MogoMissoes.verde(p) || restante(p) > 0) return;
        if (!MogoMissoes.podePousar(p)) return;
        // morrer pelo próprio raio do Mogo não conta
        if (e.getSource().is(ChamadoMogo.DANO)) return;
        e.setCanceled(true);
        p.getPersistentData().putLong(TAG, System.currentTimeMillis());
        p.setHealth(Math.max(4, p.getMaxHealth() * 0.3f));
        p.removeAllEffects();
        p.clearFire();
        p.fallDistance = 0;
        p.invulnerableTime = 60;
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(5, 60, 20));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.translatable("mogo.lanterna.segunda.titulo").withStyle(ChatFormatting.GREEN)));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.translatable("mogo.lanterna.voz.segunda").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC)));
        // caiu no vazio, ou já está no Mogo: o Mogo puxa na hora
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || p.getY() < p.level().getMinBuildHeight() || MogoMissoes.noMogo(p)) {
            chegar(p);
            return;
        }
        ChamadoMogo.resgate(p);
    }

    /** Fim do resgate: o jogador chega no pouso do Mogo. */
    public static void chegar(ServerPlayer p) {
        ServerLevel mogo = p.server.getLevel(MogoMundo.DIM);
        if (mogo == null) return;
        MogoMundo.garantir(mogo);
        MogoMundo.Dados d = MogoMundo.dados(mogo);
        double x = d.pouso.getX() + 0.5, z = d.pouso.getZ() + 4.5;
        int y = d.pronto ? d.pouso.getY() + 1 : mogo.getSeaLevel() + 40;
        if (p.getVehicle() != null) p.stopRiding();
        p.teleportTo(mogo, x, y + 6, z, 180, 10);
        p.fallDistance = 0;
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 2, false, false));
        mogo.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2f, 0.6f);
        MogoMissoes.falar(p, "segunda_chegou", false);
    }

    private SegundaChance() {}
}
