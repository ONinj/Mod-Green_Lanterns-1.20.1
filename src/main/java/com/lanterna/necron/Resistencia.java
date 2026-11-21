package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.fonte.FonteDados;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import com.lanterna.vida.Emocoes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.*;

/**
 * 0.58.0: A Última Resistência (necron_c3b), o clímax da linha corrompida: o espelho da batalha final. Apagado o
 * Embrião, os últimos Lanternas das Tropas descem sobre a cratera para tentar parar o Arauto antes que ele entregue a
 * Vida ao Necron. Três ondas: oito Lanternas; dez (com dois oficiais); e os três líderes, cada um com a sua barra de
 * chefe e a onda da sua cor (o primeiro é sempre o líder da Tropa que o Arauto traiu). Os servos do Necron lutam do
 * lado do Arauto. Se ele cair ou sair da cratera, a resistência se reagrupa e a batalha recomeça.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Resistencia {
    public static final String TAG = "lanterna_resistencia", LIDER = "lanterna_resistencia_lider";
    private static final String[] NOMES = {"red:Atros", "orange:Vosk", "yellow:Kael", "green:Guardião Sallek", "blue:Irmão Lumo", "indigo:Munk", "violet:Seren"};

    private static UUID quem;
    private static int onda, pausa, nascer, longe;
    private static final Map<UUID, ServerBossEvent> BARRAS = new HashMap<>();

    public static boolean ativa() { return quem != null; }

    private static BuiltinCorps corps(String t) {
        for (BuiltinCorps b : BuiltinCorps.values()) if (b.id().getPath().equals(t)) return b;
        return BuiltinCorps.GREEN;
    }

    private static String nome(String t) {
        for (String n : NOMES) if (n.startsWith(t + ":")) return n.substring(t.length() + 1);
        return "Sallek";
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 8 || !Tentacao.corrompido(p)) return;
        LoreData d = MissionManager.data(p);
        if (d == null || !d.isActive("necron_c3b") || ativa() || p.level().dimension() != Level.OVERWORLD) return;
        BlockPos c = Cratera.centro(p.server);
        if (c == null) return;
        double dx = p.getX() - c.getX(), dz = p.getZ() - c.getZ();
        if (dx * dx + dz * dz > 50 * 50) return;
        quem = p.getUUID();
        onda = 0;
        pausa = 80;
        nascer = 0;
        longe = 0;
        p.sendSystemMessage(Component.translatable("necron.lanterna.resist_comeca").withStyle(ChatFormatting.GOLD));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.translatable("necron.lanterna.resist_titulo").withStyle(ChatFormatting.GOLD)));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.translatable("necron.lanterna.resist_sub").withStyle(ChatFormatting.GRAY)));
        LanternaMod.LOGGER.info("Lanterna: começou A Última Resistência contra {}", p.getGameProfile().getName());
    }

    @SubscribeEvent
    public static void tickServidor(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || quem == null) return;
        MinecraftServer s = e.getServer();
        ServerLevel l = s.overworld();
        ServerPlayer p = s.getPlayerList().getPlayer(quem);
        BlockPos c = Cratera.centro(s);
        List<LanternaEntity> vivos = c == null ? List.of() : l.getEntitiesOfClass(LanternaEntity.class, new AABB(c).inflate(140, 120, 140), x -> x.getTags().contains(TAG));
        // o Arauto caiu ou foi embora: a resistência se reagrupa
        boolean fora = p == null || !p.isAlive() || p.level() != l || c == null || p.distanceToSqr(Vec3.atCenterOf(c)) > 90 * 90;
        if (fora) {
            if (++longe > 400 || p == null || !p.isAlive()) { reagrupar(l, p, vivos); return; }
        } else longe = 0;
        // as barras dos líderes
        for (LanternaEntity x : vivos) {
            ServerBossEvent b = BARRAS.get(x.getUUID());
            if (b == null) continue;
            b.setProgress(x.getHealth() / x.getMaxHealth());
            if (p != null && !b.getPlayers().contains(p)) b.addPlayer(p);
        }
        int tc = s.getTickCount();
        if (pausa > 0) {
            if (--pausa == 0 && onda < 3) {
                onda++;
                nascer = onda == 1 ? 8 : onda == 2 ? 10 : 7;
                if (p != null) {
                    p.sendSystemMessage(Component.translatable("necron.lanterna.resist_onda_" + onda).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
                    p.displayClientMessage(Component.translatable("necron.lanterna.cerco_onda", onda, 3).withStyle(ChatFormatting.GOLD), true);
                }
                l.playSound(null, p != null ? p.blockPosition() : c, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2f, 1.2f);
            }
            return;
        }
        if (nascer > 0 && tc % 12 == 0 && p != null) { nascer(l, p, c); nascer--; }
        // os líderes: a onda da cor deles
        if (tc % 90 == 0) for (LanternaEntity x : vivos) if (x.getTags().contains(LIDER)) onda(l, x);
        if (nascer == 0 && vivos.isEmpty()) {
            if (onda < 3) { pausa = 120; return; }
            vencer(l, p);
        }
    }

    private static void nascer(ServerLevel l, ServerPlayer p, BlockPos c) {
        List<BuiltinCorps> tr = new ArrayList<>();
        for (String t : Emocoes.ASPECTOS) tr.add(corps(t));
        String propria = SagaNecron.tag(p).getString("tropa");
        boolean lider = onda == 3 && nascer <= 3;
        BuiltinCorps bc;
        if (lider) {
            // o primeiro líder é o da Tropa que ele traiu; os outros dois, de outras Tropas
            int k = 3 - nascer;
            bc = k == 0 && Emocoes.aspecto(propria) ? corps(propria) : tr.get((k * 3 + propria.length()) % tr.size());
            if (k > 0 && bc.id().getPath().equals(propria)) bc = tr.get((k * 3 + propria.length() + 1) % tr.size());
        } else bc = tr.get(l.random.nextInt(tr.size()));
        final int corTropa = bc.color();
        LanternaEntity a = LanternaEntity.lanterna(l, bc, lider || onda == 2 && nascer <= 2);
        if (a == null) return;
        double ang = l.random.nextDouble() * Math.PI * 2;
        a.moveTo(p.getX() + Math.cos(ang) * 18, p.getY() + 22, p.getZ() + Math.sin(ang) * 18, 0, 0);
        a.addTag(TAG);
        a.setPersistenceRequired();
        a.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, false));
        var hp = a.getAttribute(Attributes.MAX_HEALTH);
        if (lider) {
            String nome = nome(bc.id().getPath());
            a.addTag(LIDER);
            a.setCustomName(Component.translatable("necron.lanterna.resist_lider", nome, Component.translatable("necron.lanterna.tropa." + bc.id().getPath()))
                    .withStyle(st -> st.withColor(corTropa)));
            a.setCustomNameVisible(true);
            if (hp != null) hp.setBaseValue(320);
            var ar = a.getAttribute(Attributes.ARMOR);
            if (ar != null) ar.setBaseValue(8);
            var at = a.getAttribute(Attributes.ATTACK_DAMAGE);
            if (at != null) at.setBaseValue(10);
            a.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
            BARRAS.put(a.getUUID(), new ServerBossEvent(a.getCustomName(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10));
            p.sendSystemMessage(Component.translatable("necron.lanterna.resist_lider_fala." + (3 - nascer), nome).withStyle(st -> st.withColor(corTropa)));
        } else if (hp != null) hp.setBaseValue(hp.getBaseValue() * 2);
        a.setHealth(a.getMaxHealth());
        l.addFreshEntity(a);
        a.setTarget(p);
        int cor = bc.color();
        DustParticleOptions d = new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 2f);
        for (int k = 0; k < 10; k++) l.sendParticles(d, a.getX(), a.getY() + k * 1.5, a.getZ(), 3, 0.2, 0.3, 0.2, 0);
    }

    private static void onda(ServerLevel l, LanternaEntity x) {
        int cor = x.tropa() == null ? 0xFFFFFF : x.tropa().color();
        DustParticleOptions d = new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 1.8f);
        for (int k = 0; k < 48; k++) {
            double ang = Math.PI * 2 * k / 48;
            for (int r = 2; r <= 6; r += 2) l.sendParticles(d, x.getX() + Math.cos(ang) * r, x.getY() + 0.4, x.getZ() + Math.sin(ang) * r, 1, 0, 0.1, 0, 0);
        }
        l.playSound(null, x.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.5f, 0.7f);
        for (ServerPlayer q : l.getEntitiesOfClass(ServerPlayer.class, x.getBoundingBox().inflate(6.5), q -> Tentacao.corrompido(q) && !q.isCreative())) {
            q.hurt(l.damageSources().indirectMagic(x, x), 8);
            Vec3 v = q.position().subtract(x.position()).multiply(1, 0, 1).normalize().scale(1.3);
            q.setDeltaMovement(v.x, 0.5, v.z);
            q.hurtMarked = true;
        }
    }

    @SubscribeEvent
    public static void morreu(LivingDeathEvent e) {
        ServerBossEvent b = BARRAS.remove(e.getEntity().getUUID());
        if (b != null) b.removeAllPlayers();
    }

    private static void reagrupar(ServerLevel l, ServerPlayer p, List<LanternaEntity> vivos) {
        for (LanternaEntity x : vivos) {
            l.sendParticles(ParticleTypes.END_ROD, x.getX(), x.getY() + 1, x.getZ(), 10, 0.3, 0.6, 0.3, 0.05);
            x.discard();
        }
        for (ServerBossEvent b : BARRAS.values()) b.removeAllPlayers();
        BARRAS.clear();
        if (p != null) p.sendSystemMessage(Component.translatable("necron.lanterna.resist_reagrupa").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        quem = null;
    }

    private static void vencer(ServerLevel l, ServerPlayer p) {
        quem = null;
        for (ServerBossEvent b : BARRAS.values()) b.removeAllPlayers();
        BARRAS.clear();
        if (p == null) return;
        FonteDados.chamado(p, "necron:resistencia", 1);
        p.sendSystemMessage(Component.translatable("necron.lanterna.resist_fim").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.translatable("necron.lanterna.resist_fim_titulo").withStyle(ChatFormatting.DARK_PURPLE)));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.translatable("necron.lanterna.resist_fim_sub").withStyle(ChatFormatting.GRAY)));
        l.playSound(null, p.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 1.5f, 0.5f);
        LanternaMod.LOGGER.info("Lanterna: a Última Resistência caiu");
    }

    /** Teste: para a batalha. */
    public static void zerar(ServerPlayer p) {
        FonteDados.chamado(p, "necron:resistencia", 0);
        for (ServerBossEvent b : BARRAS.values()) b.removeAllPlayers();
        BARRAS.clear();
        quem = null;
    }

    private Resistencia() {}
}
