package com.lanterna.visual;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.construct.Affinity;
import com.lanterna.corps.CorpsDefinition;
import com.lanterna.mission.LoreCapability;
import com.lanterna.mission.LoreData;
import com.lanterna.network.ModNetwork;
import com.lanterna.power.EquipmentHelper;
import com.lanterna.tropa.Nivel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Visual do Lanterna escolhido na tela de Personalização: qual uniforme o anel veste (padrão, traje A, traje B ou
 * nenhum) e a altura da máscara no rosto (em quartos de pixel, para encaixar nos olhos da skin). Fica salvo no
 * jogador (sobrevive à morte) e é mandado para todos que o enxergam. Também monta a ficha de estatísticas.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Visual {
    public static final List<String> TRAJES = List.of("padrao", "a", "b", "nenhum");
    /** Máscara: de -2 a +2 pixels, em passos de 1/4 de pixel. */
    public static final int MASCARA_MAX = 8;
    private static final String TAG = "lanterna_visual";

    private static CompoundTag tag(Player p) {
        CompoundTag persist = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return persist.getCompound(TAG);
    }

    public static String traje(Player p) {
        String t = tag(p).getString("traje");
        return TRAJES.contains(t) ? t : "padrao";
    }

    public static int mascara(Player p) {
        return Math.max(-MASCARA_MAX, Math.min(MASCARA_MAX, tag(p).getInt("mascara")));
    }

    public static void definir(ServerPlayer p, String traje, int mascara) {
        if (!TRAJES.contains(traje)) traje = "padrao";
        mascara = Math.max(-MASCARA_MAX, Math.min(MASCARA_MAX, mascara));
        CompoundTag persist = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag t = new CompoundTag();
        t.putString("traje", traje);
        t.putInt("mascara", mascara);
        persist.put(TAG, t);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persist);
        enviar(p);
    }

    private static VisualPackets.Sync sync(ServerPlayer p) {
        return new VisualPackets.Sync(p.getId(), traje(p), mascara(p));
    }

    public static void enviar(ServerPlayer p) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), sync(p));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) enviar(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) enviar(p);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) enviar(p);
    }

    @SubscribeEvent
    public static void onTrack(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer alvo && e.getEntity() instanceof ServerPlayer quem) ModNetwork.sendTo(quem, sync(alvo));
    }

    // ===================== Ficha de estatísticas =====================

    private static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
    }

    private static Component sim(boolean v) {
        return Component.translatable(v ? "perfil.lanterna.sim" : "perfil.lanterna.nao");
    }

    public static VisualPackets.Ficha ficha(ServerPlayer p) {
        List<VisualPackets.Linha> l = new ArrayList<>();
        LoreData d = LoreCapability.get(p).orElse(null);
        RingData r = ModCapabilities.get(p).orElse(null);
        boolean anel = r != null && r.hasRing();
        int cor = anel ? r.color() : 0x9AA4AE;
        int xp = d == null ? 0 : d.xp();
        int nivel = Nivel.nivel(xp);

        // ---- Lanterna ----
        String s = "lanterna";
        l.add(new VisualPackets.Linha(s, k("tropa"), anel ? Component.translatable(CorpsDefinition.nameKey(r.corps())) : k("sem_anel"), -1, cor));
        l.add(new VisualPackets.Linha(s, k("posto"), Component.translatable("posto.lanterna." + Nivel.posto(nivel)), -1, cor));
        Component nv = nivel >= Nivel.MAX ? Component.literal(nivel + " (máx.)")
                : Component.literal(nivel + "  ·  " + (xp - Nivel.total(nivel)) + " / " + (Nivel.total(nivel + 1) - Nivel.total(nivel)) + " XP");
        l.add(new VisualPackets.Linha(s, k("nivel"), nv, Nivel.progresso(xp), cor));
        var gs = com.lanterna.guilda.Guildas.get();
        var g = gs == null ? null : gs.daJogador(p.getUUID());
        l.add(new VisualPackets.Linha(s, k("guilda"), g == null ? k("nenhuma") : Component.literal(g.nome), -1, g == null ? 0x9AA4AE : g.cor()));

        // ---- Corpo ----
        s = "corpo";
        double vida = p.getMaxHealth();
        l.add(new VisualPackets.Linha(s, k("vida"), Component.literal(num(p.getHealth()) + " / " + num(vida) + "  (+" + Nivel.vidaExtra(nivel) + " pelo nível)"),
                (float) (p.getHealth() / vida), 0xE5484D));
        l.add(new VisualPackets.Linha(s, k("armadura"), Component.literal(num(p.getAttributeValue(Attributes.ARMOR))), -1, 0xC8D0D8));
        l.add(new VisualPackets.Linha(s, k("dureza"), Component.literal(num(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS))), -1, 0xC8D0D8));
        float pvp = 1f - com.lanterna.combate.Equilibrio.multiplicador(nivel, nivel, true) / Math.max(0.01f, com.lanterna.LanternaConfig.PVP_DANO.get().floatValue());
        float sup = anel ? com.lanterna.combate.Supremos.resistenciaPvp(r.corps()) : 0f;
        l.add(new VisualPackets.Linha(s, k("resist_pvp"), Component.literal(Math.round((pvp + sup) * 100) + "%"), -1, 0xC8D0D8));

        // ---- Anel ----
        s = "anel";
        if (anel) {
            l.add(new VisualPackets.Linha(s, k("carga"), Component.literal(r.charge() + " / " + r.maxCharge()), r.charge() / (float) Math.max(1, r.maxCharge()), cor));
            int pts = Affinity.points(EquipmentHelper.ring(p));
            int an = Affinity.level(pts);
            l.add(new VisualPackets.Linha(s, k("afinidade"), Affinity.levelName(an).copy().append(Component.literal("  ·  " + pts + " pts")),
                    an >= Affinity.MAX_LEVEL ? 1f : Affinity.progress(pts), cor));
            l.add(new VisualPackets.Linha(s, k("forca"), Component.literal("×" + num(Math.round(com.lanterna.construct.Constructs.power(p, r) * 100) / 100.0)), -1, cor));
            l.add(new VisualPackets.Linha(s, k("desconto"), Component.literal(Math.round(Nivel.desconto(p) * 100) + "%"), -1, cor));
            double voo = com.lanterna.combate.Supremos.voo(r.corps());
            l.add(new VisualPackets.Linha(s, k("voo"), Component.translatable("perfil.lanterna.voo_val", Math.round(23 * voo), Math.round(48 * voo)), -1, cor));
            if (d != null) {
                l.add(new VisualPackets.Linha(s, k("autorecarga"), sim(d.autoRecarga()), -1, cor));
                l.add(new VisualPackets.Linha(s, k("portal"), sim(d.portal()), -1, cor));
                l.add(new VisualPackets.Linha(s, k("tecnicas"), Component.literal(d.tecnicas().size() + " / " + com.lanterna.cidade.CidadeMissoes.TECNICAS.size()
                        + "  ·  " + com.lanterna.cidade.Tecnicas.slots(d) + " espaços"), -1, cor));
            }
        } else {
            l.add(new VisualPackets.Linha(s, k("carga"), k("sem_anel"), -1, 0x9AA4AE));
        }

        // ---- Jornada ----
        s = "jornada";
        if (d != null) {
            l.add(new VisualPackets.Linha(s, k("missoes"), Component.literal(String.valueOf(d.completed().size())), -1, 0xE8C860));
            l.add(new VisualPackets.Linha(s, k("planetas"), Component.literal(String.valueOf(d.visitados().size())), -1, 0xE8C860));
            l.add(new VisualPackets.Linha(s, k("chefes"), Component.literal(String.valueOf(d.bossKills().size())), -1, 0xE8C860));
            l.add(new VisualPackets.Linha(s, k("honra"), sim(d.honra()), -1, 0xE8C860));
            if (!d.tropasHostis().isEmpty()) {
                net.minecraft.network.chat.MutableComponent t = Component.empty();
                boolean first = true;
                for (String h : d.tropasHostis()) {
                    if (!first) t.append(", ");
                    t.append(Component.translatable("corps.lanterna." + h));
                    first = false;
                }
                l.add(new VisualPackets.Linha(s, k("hostis"), t, -1, 0xE5484D));
            }
            if (d.morte() > 0) l.add(new VisualPackets.Linha(s, k("morte"), Component.translatable("perfil.lanterna.morte." + d.morte()), -1, 0xB070FF));
        }
        return new VisualPackets.Ficha(l);
    }

    private static Component k(String key) {
        return Component.translatable("perfil.lanterna." + key);
    }

    private Visual() {}
}
