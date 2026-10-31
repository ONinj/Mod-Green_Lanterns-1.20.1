package com.lanterna.client.construct;

import com.lanterna.client.ClientRingData;
import com.lanterna.client.KeyBindings;
import com.lanterna.client.mission.ClientLoreData;
import com.lanterna.construct.Affinity;
import com.lanterna.construct.ConstructType;
import com.lanterna.mission.LoreData;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.lore.ConstructCastPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Teclas dos construtos no cliente:
 *   Z (segurar)  abre a roda de construtos; soltar em cima de um escolhe
 *   X            usa o construto escolhido (laser e picareta: enquanto segurar)
 *   Alt+1..4     usa na hora o construto do preset
 */
public final class ClientConstructs {
    private static final Map<ConstructType, Long> COOLDOWN_UNTIL = new EnumMap<>(ConstructType.class);
    @Nullable private static ConstructType holding;
    @Nullable private static KeyMapping holdingKey;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen == null && KeyBindings.CONSTRUCT_WHEEL.isDown() && ClientRingData.hasRing) {
            mc.setScreen(new ConstructWheelScreen());
        }
        // Soltou a tecla de um construto contínuo
        if (holding != null && (holdingKey == null || !holdingKey.isDown() || mc.screen != null)) {
            ModNetwork.sendToServer(new ConstructCastPacket(holding.id, false));
            holding = null;
            holdingKey = null;
        }
        // Pilotando uma nave: X atira com os canhões
        if (mc.player.getVehicle() instanceof com.lanterna.naves.NaveEntity) {
            while (KeyBindings.CONSTRUCT_USE.consumeClick()) com.lanterna.client.naves.ClientNaves.fire();
            return;
        }
        while (KeyBindings.CONSTRUCT_USE.consumeClick()) use(selected(), KeyBindings.CONSTRUCT_USE);
        for (int i = 0; i < KeyBindings.PRESETS.length; i++) {
            while (KeyBindings.PRESETS[i].consumeClick()) use(ConstructType.byId(ClientLoreData.DATA.preset(i)), KeyBindings.PRESETS[i]);
        }
    }

    public static ConstructType selected() {
        ConstructType t = ConstructType.byId(ClientLoreData.DATA.selected());
        return t == null ? ConstructType.LASER : t;
    }

    public static void use(@Nullable ConstructType type, KeyMapping key) {
        Minecraft mc = Minecraft.getInstance();
        if (type == null || mc.player == null) return;
        if (!ClientRingData.hasRing) return;
        if (!ClientRingData.active) {
            mc.player.displayClientMessage(Component.translatable("message.lanterna.power_needed"), true);
            return;
        }
        if (level() < type.unlockLevel) {
            mc.player.displayClientMessage(Component.translatable("message.lanterna.construct_locked", type.displayName(),
                    Affinity.levelName(type.unlockLevel)), true);
            return;
        }
        if (cooldown(type) > 0) return;
        if (!type.disponivel(ClientRingData.corps)) return;
        // Segurando a tecla, o teclado "repete" o clique: o construto contínuo já está ligado, não reinicia
        if (type.hold && holding == type) return;
        ModNetwork.sendToServer(new ConstructCastPacket(type.id, true));
        if (type.hold) {
            holding = type;
            holdingKey = key;
        } else {
            COOLDOWN_UNTIL.put(type, System.currentTimeMillis() + type.cooldown * 50L);
        }
        // A animação do braço vem do servidor (CastAnim); o balanço normal só nos de toque
        if (!type.hold && CastAnim.estilo(type) == CastAnim.Estilo.ESTOCADA) mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    public static int level() {
        return Affinity.level(ClientLoreData.DATA.affinity());
    }

    /** 0..1 do tempo de recarga que falta. */
    public static float cooldown(ConstructType type) {
        Long until = COOLDOWN_UNTIL.get(type);
        if (until == null || type.cooldown <= 0) return 0;
        long left = until - System.currentTimeMillis();
        return left <= 0 ? 0 : Math.min(1f, left / (type.cooldown * 50f));
    }

    public static boolean isHolding(ConstructType type) {
        return holding == type;
    }

    public static LoreData data() {
        return ClientLoreData.DATA;
    }

    public static void clear() {
        COOLDOWN_UNTIL.clear();
        holding = null;
        holdingKey = null;
    }

    private ClientConstructs() {}
}
