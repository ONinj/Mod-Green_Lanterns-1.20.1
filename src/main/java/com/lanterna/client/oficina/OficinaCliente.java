package com.lanterna.client.oficina;

import com.lanterna.LanternaMod;
import com.lanterna.client.mission.ClientLoreData;
import com.lanterna.client.ClientRingData;
import com.lanterna.construct.oficina.Comportamento;
import com.lanterna.construct.oficina.ConstrutoProprio;
import com.lanterna.construct.oficina.MeusConstrutos;
import com.lanterna.construct.oficina.Projeto;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.lore.ConstructCastPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** O lado do cliente da Oficina: os construtos desenhados, o preset "meu:N", o golpe da arma e a pose do braço. */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class OficinaCliente {
    public static final Projeto[] MEUS = new Projeto[Projeto.SLOTS];
    private static final long[] RECARGA = new long[Projeto.SLOTS];
    /** Arma desenhada ativa de cada jogador (para a pose do braço). */
    private static final Map<UUID, ConstrutoProprio> ARMAS = new HashMap<>();

    public static void receber(Projeto[] p) {
        System.arraycopy(p, 0, MEUS, 0, Projeto.SLOTS);
        if (Minecraft.getInstance().screen instanceof OficinaScreen s) s.atualizarEspacos();
    }

    @Nullable
    public static Projeto preset(String id) {
        if (!MeusConstrutos.eMeu(id)) return null;
        int s = MeusConstrutos.slot(id);
        return s >= 0 && s < Projeto.SLOTS ? MEUS[s] : null;
    }

    /** Nome para mostrar de um preset "meu:N". */
    public static Component nomePreset(String id) {
        Projeto p = preset(id);
        return Component.literal(p == null ? "-" : p.nome.isEmpty() ? "#" + (MeusConstrutos.slot(id) + 1) : p.nome);
    }

    /** Alt+N com um construto desenhado no preset. */
    public static void usar(String id) {
        Minecraft mc = Minecraft.getInstance();
        Projeto p = preset(id);
        if (p == null || mc.player == null || !ClientRingData.hasRing) return;
        if (!ClientRingData.active) {
            mc.player.displayClientMessage(Component.translatable("message.lanterna.power_needed"), true);
            return;
        }
        int s = MeusConstrutos.slot(id);
        long agora = System.currentTimeMillis();
        if (RECARGA[s] > agora) return;
        RECARGA[s] = agora + 400;
        ModNetwork.sendToServer(new ConstructCastPacket(id, true));
    }

    @Nullable
    public static ConstrutoProprio armaDe(Entity e) {
        ConstrutoProprio c = ARMAS.get(e.getUUID());
        return c != null && c.isAlive() && !c.desmontando() ? c : null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ARMAS.clear();
        if (mc.level == null) return;
        for (Entity en : mc.level.entitiesForRendering())
            if (en instanceof ConstrutoProprio c && c.comp() == Comportamento.ARMA && c.donoId() != null) ARMAS.put(c.donoId(), c);
    }

    /** Clique esquerdo com a arma desenhada: golpe dela (no lugar do soco). */
    @SubscribeEvent
    public static void onClique(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (!e.isAttack() || mc.player == null || armaDe(mc.player) == null) return;
        e.setCanceled(true);
        e.setSwingHand(false);
        ModNetwork.sendToServer(new MeusConstrutos.Golpe());
    }

    /** Braço segurando a arma desenhada (e acompanhando o golpe). */
    public static void bracos(HumanoidModel<?> m, LivingEntity e, float pt) {
        ConstrutoProprio c = armaDe(e);
        if (c == null) return;
        float g = c.idade() + pt - c.golpe();
        float alvoX = -0.75f, alvoY = -0.1f;
        if (g >= 0 && g < 1.5f) { alvoX = -1.9f; alvoY = -0.6f; }
        else if (g >= 1.5f && g < 4.5f) { float t = (g - 1.5f) / 3f; alvoX = Mth.lerp(t, -1.9f, -1.1f); alvoY = Mth.lerp(t, -0.6f, 0.9f); }
        else if (g >= 4.5f && g < 11f) { float t = (g - 4.5f) / 6.5f; alvoX = Mth.lerp(t, -1.1f, -0.75f); alvoY = Mth.lerp(t, 0.9f, -0.1f); }
        m.rightArm.xRot = alvoX + m.head.xRot * 0.5f;
        m.rightArm.yRot = alvoY;
        m.rightArm.zRot = 0;
        if (m instanceof PlayerModel<?> pm) pm.rightSleeve.copyFrom(m.rightArm);
    }

    private OficinaCliente() {}
}
