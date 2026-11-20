package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 0.56.0: a Terra Escurece. Depois das quedas, cada vez que o jogador volta para a Terra (com a missão "A Terra
 * Escurece"), a escuridão avançou. 0.56.7: agora cada etapa é uma missão (ver {@link Escurece}): a terra morta em volta da cratera cresce, o céu da Terra fica mais cinza-roxo e
 * mais servos rondam. Na terceira volta, os Lanternas que restam chamam para a batalha final ("A Última Luz").
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TerraEscurece {
    public static final int ETAPAS = 3;
    /** Tempo mínimo fora da Terra entre uma etapa e outra (ticks). */
    private static final long INTERVALO = 20 * 60 * 2;

    /** Uma etapa a mais (também pelo comando de teste). */
    public static void avancar(ServerPlayer p, int etapa) {
        var s = p.server;
        if (Cratera.estagio(s) < etapa) Cratera.crescer(s, etapa);
        for (ServerPlayer q : s.getPlayerList().getPlayers()) SagaNecron.mandarMundo(q);
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                Component.translatable("necron.lanterna.terra_titulo").withStyle(ChatFormatting.DARK_PURPLE)));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                Component.translatable("necron.lanterna.terra_sub", etapa, ETAPAS).withStyle(ChatFormatting.GRAY)));
        p.sendSystemMessage(Component.translatable("necron.lanterna.terra_" + etapa).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), SoundSource.AMBIENT, 1.5f, 0.6f);
        LanternaMod.LOGGER.info("Lanterna: a Terra escurece (etapa {} de {})", etapa, ETAPAS);
        if (etapa >= ETAPAS) {
            com.lanterna.fonte.FonteDados.chamado(p, "necron:terra", 1);
            MissionManager.start(p, "necron_6a");
            p.sendSystemMessage(Component.translatable("necron.lanterna.ultima_luz_chamado").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    private TerraEscurece() {}
}
