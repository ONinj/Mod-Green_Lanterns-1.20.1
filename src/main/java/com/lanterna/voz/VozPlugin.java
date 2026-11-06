package com.lanterna.voz;

import com.lanterna.LanternaMod;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Plugin do Lanterna para o Simple Voice Chat (o Voice Chat acha esta classe sozinho pela anotação).
 *  - servidor: cria um grupo de voz escondido para cada ligação e sabe quem está falando;
 *  - cliente: o áudio do microfone que o Voice Chat capta alimenta o medidor do juramento falado.
 */
@ForgeVoicechatPlugin
public class VozPlugin implements VoicechatPlugin {
    @Nullable private static VoicechatServerApi api;

    @Override
    public String getPluginId() {
        return LanternaMod.MODID;
    }

    @Override
    public void initialize(VoicechatApi a) {
        LanternaMod.LOGGER.info("Lanterna: Simple Voice Chat encontrado (ligações com voz e juramento falado)");
    }

    @Override
    public void registerEvents(EventRegistration r) {
        r.registerEvent(VoicechatServerStartedEvent.class, e -> api = e.getVoicechat());
        r.registerEvent(MicrophonePacketEvent.class, e -> {
            VoicechatConnection c = e.getSenderConnection();
            if (c != null) Ligacoes.FALOU.put(c.getPlayer().getUuid(), System.currentTimeMillis());
        });
        r.registerEvent(ClientSoundEvent.class, e -> com.lanterna.client.juramento.Microfone.amostra(e.getRawAudio(), true));
        r.registerEvent(ClientVoicechatConnectionEvent.class, e -> com.lanterna.client.juramento.Microfone.svcConectado = e.isConnected());
    }

    static void juntar(Ligacoes.Ligacao l) {
        if (api == null) return;
        Group g = l.grupoVoz != null ? api.getGroup(l.grupoVoz) : null;
        if (g == null) {
            g = api.groupBuilder().setName("Lanterna").setHidden(true).setPersistent(false).setType(Group.Type.NORMAL).build();
            l.grupoVoz = g.getId();
        }
        for (UUID u : l.membros) {
            VoicechatConnection c = api.getConnectionOf(u);
            if (c != null) c.setGroup(g);
        }
    }

    static void sair(UUID p) {
        if (api == null) return;
        VoicechatConnection c = api.getConnectionOf(p);
        if (c != null && c.isInGroup()) c.setGroup(null);
    }

    static void fim(Ligacoes.Ligacao l) {
        if (api == null || l.grupoVoz == null) return;
        api.removeGroup(l.grupoVoz);
    }
}
