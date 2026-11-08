package com.lanterna.network;

import com.lanterna.LanternaMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "16";
    public static SimpleChannel CHANNEL;

    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                LanternaMod.id("main"),
                () -> VERSION,
                VERSION::equals,
                VERSION::equals);

        int id = 0;

        CHANNEL.messageBuilder(SyncRingDataPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncRingDataPacket::encode)
                .decoder(SyncRingDataPacket::decode)
                .consumerMainThread(SyncRingDataPacket::handle)
                .add();

        CHANNEL.messageBuilder(SelectionCutscenePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SelectionCutscenePacket::encode)
                .decoder(SelectionCutscenePacket::decode)
                .consumerMainThread(SelectionCutscenePacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.mogo.ChamadoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.mogo.ChamadoPacket::encode)
                .decoder(com.lanterna.mogo.ChamadoPacket::decode)
                .consumerMainThread(com.lanterna.mogo.ChamadoPacket::handle)
                .add();

        CHANNEL.messageBuilder(com.lanterna.mogo.MogoOrbita.Pacote.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.mogo.MogoOrbita.Pacote::encode)
                .decoder(com.lanterna.mogo.MogoOrbita.Pacote::decode)
                .consumerMainThread(com.lanterna.mogo.MogoOrbita.Pacote::handle)
                .add();

        CHANNEL.messageBuilder(com.lanterna.mogo.MogoDefesa.Raio.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.mogo.MogoDefesa.Raio::encode)
                .decoder(com.lanterna.mogo.MogoDefesa.Raio::decode)
                .consumerMainThread(com.lanterna.mogo.MogoDefesa.Raio::handle)
                .add();

        CHANNEL.messageBuilder(com.lanterna.mogo.MogoRastro.Pacote.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.mogo.MogoRastro.Pacote::encode)
                .decoder(com.lanterna.mogo.MogoRastro.Pacote::decode)
                .consumerMainThread(com.lanterna.mogo.MogoRastro.Pacote::handle)
                .add();

        CHANNEL.messageBuilder(com.lanterna.morte.Frenesi.Pacote.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.morte.Frenesi.Pacote::encode)
                .decoder(com.lanterna.morte.Frenesi.Pacote::decode)
                .consumerMainThread(com.lanterna.morte.Frenesi.Pacote::handle)
                .add();

        CHANNEL.messageBuilder(ToggleFlightPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleFlightPacket::encode)
                .decoder(ToggleFlightPacket::decode)
                .consumerMainThread(ToggleFlightPacket::handle)
                .add();

        CHANNEL.messageBuilder(PocketRechargePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PocketRechargePacket::encode)
                .decoder(PocketRechargePacket::decode)
                .consumerMainThread(PocketRechargePacket::handle)
                .add();

        CHANNEL.messageBuilder(TogglePowerPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TogglePowerPacket::encode)
                .decoder(TogglePowerPacket::decode)
                .consumerMainThread(TogglePowerPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenLanternMenuPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenLanternMenuPacket::encode)
                .decoder(OpenLanternMenuPacket::decode)
                .consumerMainThread(OpenLanternMenuPacket::handle)
                .add();

        CHANNEL.messageBuilder(RechargeAnimPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RechargeAnimPacket::encode)
                .decoder(RechargeAnimPacket::decode)
                .consumerMainThread(RechargeAnimPacket::handle)
                .add();

        CHANNEL.messageBuilder(SyncVisibleRingPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncVisibleRingPacket::encode)
                .decoder(SyncVisibleRingPacket::decode)
                .consumerMainThread(SyncVisibleRingPacket::handle)
                .add();

        CHANNEL.messageBuilder(FlightModePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FlightModePacket::encode)
                .decoder(FlightModePacket::decode)
                .consumerMainThread(FlightModePacket::handle)
                .add();

        CHANNEL.messageBuilder(SpaceSeedPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SpaceSeedPacket::encode)
                .decoder(SpaceSeedPacket::decode)
                .consumerMainThread(SpaceSeedPacket::handle)
                .add();

        // ---- Lore: missões, conversas, construtos ----
        CHANNEL.messageBuilder(com.lanterna.network.lore.SyncLorePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.network.lore.SyncLorePacket::encode)
                .decoder(com.lanterna.network.lore.SyncLorePacket::decode)
                .consumerNetworkThread(com.lanterna.network.lore.SyncLorePacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.network.lore.MissionToastPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.network.lore.MissionToastPacket::encode)
                .decoder(com.lanterna.network.lore.MissionToastPacket::decode)
                .consumerNetworkThread(com.lanterna.network.lore.MissionToastPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.network.lore.OpenDialogPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.network.lore.OpenDialogPacket::encode)
                .decoder(com.lanterna.network.lore.OpenDialogPacket::decode)
                .consumerNetworkThread(com.lanterna.network.lore.OpenDialogPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.network.lore.StartAutopilotPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.network.lore.StartAutopilotPacket::encode)
                .decoder(com.lanterna.network.lore.StartAutopilotPacket::decode)
                .consumerNetworkThread(com.lanterna.network.lore.StartAutopilotPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.network.lore.DialogActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.lanterna.network.lore.DialogActionPacket::encode)
                .decoder(com.lanterna.network.lore.DialogActionPacket::decode)
                .consumerNetworkThread(com.lanterna.network.lore.DialogActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.network.lore.MissionActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.lanterna.network.lore.MissionActionPacket::encode)
                .decoder(com.lanterna.network.lore.MissionActionPacket::decode)
                .consumerNetworkThread(com.lanterna.network.lore.MissionActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.network.lore.ConstructCastPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.lanterna.network.lore.ConstructCastPacket::encode)
                .decoder(com.lanterna.network.lore.ConstructCastPacket::decode)
                .consumerNetworkThread(com.lanterna.network.lore.ConstructCastPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.network.lore.ConstructSelectPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.lanterna.network.lore.ConstructSelectPacket::encode)
                .decoder(com.lanterna.network.lore.ConstructSelectPacket::decode)
                .consumerNetworkThread(com.lanterna.network.lore.ConstructSelectPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.naves.NaveControlPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.lanterna.naves.NaveControlPacket::encode)
                .decoder(com.lanterna.naves.NaveControlPacket::decode)
                .consumerNetworkThread(com.lanterna.naves.NaveControlPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.tropa.TropaSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.tropa.TropaSyncPacket::encode)
                .decoder(com.lanterna.tropa.TropaSyncPacket::decode)
                .consumerMainThread(com.lanterna.tropa.TropaSyncPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.tropa.ContratoPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.lanterna.tropa.ContratoPacket::encode)
                .decoder(com.lanterna.tropa.ContratoPacket::decode)
                .consumerNetworkThread(com.lanterna.tropa.ContratoPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.naves.NaveAutoPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.lanterna.naves.NaveAutoPacket::encode)
                .decoder(com.lanterna.naves.NaveAutoPacket::decode)
                .consumerNetworkThread(com.lanterna.naves.NaveAutoPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.cidade.TecnicaPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.lanterna.cidade.TecnicaPacket::encode)
                .decoder(com.lanterna.cidade.TecnicaPacket::decode)
                .consumerNetworkThread(com.lanterna.cidade.TecnicaPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.tropa.QuadroPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.tropa.QuadroPacket::encode)
                .decoder(com.lanterna.tropa.QuadroPacket::decode)
                .consumerMainThread(com.lanterna.tropa.QuadroPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.portal.PortalPackets.Pedido.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.lanterna.portal.PortalPackets.Pedido::encode)
                .decoder(com.lanterna.portal.PortalPackets.Pedido::decode)
                .consumerMainThread(com.lanterna.portal.PortalPackets.Pedido::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.portal.PortalPackets.Lista.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.portal.PortalPackets.Lista::encode)
                .decoder(com.lanterna.portal.PortalPackets.Lista::decode)
                .consumerMainThread(com.lanterna.portal.PortalPackets.Lista::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.portal.PortalPackets.Anim.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.portal.PortalPackets.Anim::encode)
                .decoder(com.lanterna.portal.PortalPackets.Anim::decode)
                .consumerMainThread(com.lanterna.portal.PortalPackets.Anim::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.construct.CastAnimPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.construct.CastAnimPacket::encode)
                .decoder(com.lanterna.construct.CastAnimPacket::decode)
                .consumerMainThread(com.lanterna.construct.CastAnimPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.guilda.GuildasSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.guilda.GuildasSyncPacket::encode)
                .decoder(com.lanterna.guilda.GuildasSyncPacket::decode)
                .consumerMainThread(com.lanterna.guilda.GuildasSyncPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.guilda.GuildaPainelPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.guilda.GuildaPainelPacket::encode)
                .decoder(com.lanterna.guilda.GuildaPainelPacket::decode)
                .consumerMainThread(com.lanterna.guilda.GuildaPainelPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.guilda.AbrirSimboloPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.guilda.AbrirSimboloPacket::encode)
                .decoder(com.lanterna.guilda.AbrirSimboloPacket::decode)
                .consumerMainThread(com.lanterna.guilda.AbrirSimboloPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.guilda.GuildaAcaoPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.lanterna.guilda.GuildaAcaoPacket::encode)
                .decoder(com.lanterna.guilda.GuildaAcaoPacket::decode)
                .consumerMainThread(com.lanterna.guilda.GuildaAcaoPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.lanterna.visual.VisualPackets.Pedido.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.lanterna.visual.VisualPackets.Pedido::encode).decoder(com.lanterna.visual.VisualPackets.Pedido::decode)
                .consumerMainThread(com.lanterna.visual.VisualPackets.Pedido::handle).add();
        CHANNEL.messageBuilder(com.lanterna.visual.VisualPackets.Sync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.visual.VisualPackets.Sync::encode).decoder(com.lanterna.visual.VisualPackets.Sync::decode)
                .consumerMainThread(com.lanterna.visual.VisualPackets.Sync::handle).add();
        CHANNEL.messageBuilder(com.lanterna.visual.VisualPackets.FichaPedido.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.lanterna.visual.VisualPackets.FichaPedido::encode).decoder(com.lanterna.visual.VisualPackets.FichaPedido::decode)
                .consumerMainThread(com.lanterna.visual.VisualPackets.FichaPedido::handle).add();
        CHANNEL.messageBuilder(com.lanterna.visual.VisualPackets.Ficha.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.visual.VisualPackets.Ficha::encode).decoder(com.lanterna.visual.VisualPackets.Ficha::decode)
                .consumerMainThread(com.lanterna.visual.VisualPackets.Ficha::handle).add();
        CHANNEL.messageBuilder(com.lanterna.fonte.FontePackets.Estado.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.fonte.FontePackets.Estado::encode).decoder(com.lanterna.fonte.FontePackets.Estado::decode)
                .consumerMainThread(com.lanterna.fonte.FontePackets.Estado::handle).add();
        CHANNEL.messageBuilder(com.lanterna.construct.fonte.ArvoreDaLuz.Comprar.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.lanterna.construct.fonte.ArvoreDaLuz.Comprar::encode).decoder(com.lanterna.construct.fonte.ArvoreDaLuz.Comprar::decode)
                .consumerMainThread(com.lanterna.construct.fonte.ArvoreDaLuz.Comprar::handle).add();
        CHANNEL.messageBuilder(com.lanterna.fonte.FontePackets.Aura.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.fonte.FontePackets.Aura::encode).decoder(com.lanterna.fonte.FontePackets.Aura::decode)
                .consumerMainThread(com.lanterna.fonte.FontePackets.Aura::handle).add();
        CHANNEL.messageBuilder(com.lanterna.construct.oficina.MeusConstrutos.Salvar.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.lanterna.construct.oficina.MeusConstrutos.Salvar::encode).decoder(com.lanterna.construct.oficina.MeusConstrutos.Salvar::decode)
                .consumerMainThread(com.lanterna.construct.oficina.MeusConstrutos.Salvar::handle).add();
        CHANNEL.messageBuilder(com.lanterna.construct.oficina.MeusConstrutos.Golpe.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.lanterna.construct.oficina.MeusConstrutos.Golpe::encode).decoder(com.lanterna.construct.oficina.MeusConstrutos.Golpe::decode)
                .consumerMainThread(com.lanterna.construct.oficina.MeusConstrutos.Golpe::handle).add();
        CHANNEL.messageBuilder(com.lanterna.construct.oficina.MeusConstrutos.Lista.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.construct.oficina.MeusConstrutos.Lista::encode).decoder(com.lanterna.construct.oficina.MeusConstrutos.Lista::decode)
                .consumerMainThread(com.lanterna.construct.oficina.MeusConstrutos.Lista::handle).add();
        CHANNEL.messageBuilder(com.lanterna.terra.TerraSite.Sync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.terra.TerraSite.Sync::encode).decoder(com.lanterna.terra.TerraSite.Sync::decode)
                .consumerMainThread(com.lanterna.terra.TerraSite.Sync::handle).add();
        CHANNEL.messageBuilder(com.lanterna.juramento.Juramento.Abrir.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.juramento.Juramento.Abrir::encode).decoder(com.lanterna.juramento.Juramento.Abrir::decode)
                .consumerMainThread(com.lanterna.juramento.Juramento.Abrir::handle).add();
        CHANNEL.messageBuilder(com.lanterna.juramento.Juramento.Feito.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.lanterna.juramento.Juramento.Feito::encode).decoder(com.lanterna.juramento.Juramento.Feito::decode)
                .consumerMainThread(com.lanterna.juramento.Juramento.Feito::handle).add();
        CHANNEL.messageBuilder(com.lanterna.voz.Ligacoes.Acao.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.lanterna.voz.Ligacoes.Acao::encode).decoder(com.lanterna.voz.Ligacoes.Acao::decode)
                .consumerMainThread(com.lanterna.voz.Ligacoes.Acao::handle).add();
        CHANNEL.messageBuilder(com.lanterna.voz.Ligacoes.Estado.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.lanterna.voz.Ligacoes.Estado::encode).decoder(com.lanterna.voz.Ligacoes.Estado::decode)
                .consumerMainThread(com.lanterna.voz.Ligacoes.Estado::handle).add();
    }

    public static void sendTo(ServerPlayer player, Object message) {
        if (CHANNEL != null) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
    }

    public static void sendToServer(Object message) {
        if (CHANNEL != null) {
            CHANNEL.sendToServer(message);
        }
    }

    private ModNetwork() {}
}
