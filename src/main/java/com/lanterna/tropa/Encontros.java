package com.lanterna.tropa;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.lore.Factions;
import com.lanterna.naves.NaveEntity;
import com.lanterna.naves.NaveTipo;
import com.lanterna.space.SpaceBodies;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Encontros no espaço: enquanto o Lanterna viaja, de vez em quando aparece alguém.
 *  nave pirata (ataca) · capitânia pirata (nível 25+, solta saqueadoras) · patrulha da sua Tropa (acompanha e
 *  ajuda) · patrulha de uma Tropa inimiga (ataca) · cargueiro mercador (para e abre comércio) · destroços (saque).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Encontros {
    private static final String CD = "lanterna_encontro_cd";
    private static final int MIN_INTERVALO = 20 * 60;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 20 != 5) return;
        if (!p.level().dimension().equals(SpaceBodies.SPACE) || p.isSpectator() || p.isCreative()) return;
        long now = p.level().getGameTime();
        long cd = p.getPersistentData().getLong(CD);
        if (now < cd) return;
        if (!p.level().getEntitiesOfClass(NaveEntity.class, p.getBoundingBox().inflate(220), n -> !n.ia().isEmpty()).isEmpty()) return;
        if (p.getRandom().nextInt(25) != 0) return;
        p.getPersistentData().putLong(CD, now + MIN_INTERVALO + p.getRandom().nextInt(20 * 45));
        sortear(p);
    }

    private static void sortear(ServerPlayer p) {
        BuiltinCorps mine = Factions.playerCorps(p);
        int lvl = Nivel.nivel(p);
        int r = p.getRandom().nextInt(100);
        if (lvl >= 20 && r < 9) capitania(p);
        else if (r < 52) pirata(p, null);
        else if (r < 66 && mine != null && mine != BuiltinCorps.WHITE) patrulha(p, mine, "patrulha");
        else if (r < 80 && mine != null && inimigo(mine, p) != null) patrulha(p, inimigo(mine, p), "inimiga");
        else if (r < 91) cargueiro(p);
        else destrocos(p);
    }

    @Nullable
    private static BuiltinCorps inimigo(BuiltinCorps mine, Player p) {
        List<BuiltinCorps> foes = new ArrayList<>();
        for (BuiltinCorps c : BuiltinCorps.values()) if (c != BuiltinCorps.WHITE && Factions.enemies(mine, c)) foes.add(c);
        return foes.isEmpty() ? null : foes.get(p.getRandom().nextInt(foes.size()));
    }

    private static Vec3 ponto(ServerPlayer p, double dist) {
        float yaw = p.getYRot() + (p.getRandom().nextFloat() - 0.5f) * 120f;
        return p.position().add(Vec3.directionFromRotation(0, yaw).scale(dist)).add(0, (p.getRandom().nextFloat() - 0.5f) * 30, 0);
    }

    private static NaveEntity nave(ServerPlayer p, NaveTipo tipo, String pintura, String ia, @Nullable LanternaEntity piloto, int life) {
        ServerLevel level = p.serverLevel();
        Vec3 at = ponto(p, 56);
        NaveEntity n = NaveEntity.create(level, tipo, pintura);
        Vec3 d = p.position().subtract(at);
        n.moveTo(at.x, at.y, at.z, (float) Math.toDegrees(Math.atan2(-d.x, d.z)), 0);
        n.setIa(ia);
        n.setAiTarget(p.getUUID());
        n.setAiLife(life);
        level.addFreshEntity(n);
        if (piloto != null) {
            piloto.moveTo(at.x, at.y, at.z);
            piloto.getPersistentData().putBoolean("lanterna_encontro", true);
            level.addFreshEntity(piloto);
            piloto.startRiding(n, true);
        }
        return n;
    }

    private static void aviso(ServerPlayer p, String key, int color) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(key).withStyle(s -> s.withColor(color))));
    }

    public static void pirata(ServerPlayer p, @Nullable String contrato) {
        NaveEntity n = nave(p, NaveTipo.NAVE_PIRATA, String.valueOf(1 + p.getRandom().nextInt(2)), "pirata",
                LanternaEntity.pirata(p.serverLevel(), false), 20 * 60 * 4);
        if (contrato != null) n.getPersistentData().putString("contrato", contrato);
        aviso(p, "encontro.lanterna.pirata", 0xFF5030);
        p.level().playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.8f, 1.3f);
    }

    private static void capitania(ServerPlayer p) {
        NaveEntity n = nave(p, NaveTipo.NAVE_PIRATA_CAPITANIA, String.valueOf(1 + p.getRandom().nextInt(2)), "pirata",
                LanternaEntity.pirata(p.serverLevel(), true), 20 * 60 * 6);
        n.setHangar(true);
        for (int i = 0; i < 2; i++) {
            NaveEntity s = nave(p, NaveTipo.NAVE_PIRATA, String.valueOf(1 + p.getRandom().nextInt(2)), "pirata",
                    LanternaEntity.pirata(p.serverLevel(), false), 20 * 60 * 5);
            s.moveTo(n.getX() + 20 * (i == 0 ? 1 : -1), n.getY(), n.getZ(), n.getYRot(), 0);
        }
        aviso(p, "encontro.lanterna.capitania", 0xFF2020);
        p.level().playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.2f, 0.7f);
    }

    private static void patrulha(ServerPlayer p, BuiltinCorps corps, String ia) {
        boolean big = p.getRandom().nextInt(3) == 0;
        nave(p, big ? NaveTipo.NAVE_PATRULHA : NaveTipo.NAVE_LANCEIRA, corps.id().getPath(), ia,
                LanternaEntity.lanterna(p.serverLevel(), corps, big), 20 * 60 * 3);
        aviso(p, ia.equals("patrulha") ? "encontro.lanterna.patrulha" : "encontro.lanterna.inimiga", corps.color());
    }

    private static void cargueiro(ServerPlayer p) {
        nave(p, NaveTipo.NAVE_CARGUEIRO, String.valueOf(1 + p.getRandom().nextInt(2)), "cargueiro",
                LanternaEntity.mercador(p.serverLevel()), 20 * 60 * 3);
        aviso(p, "encontro.lanterna.cargueiro", 0xFFB040);
    }

    private static void destrocos(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 at = ponto(p, 40);
        NaveTipo t = NaveTipo.values()[NaveTipo.DESTROCOS_1.ordinal() + p.getRandom().nextInt(3)];
        NaveEntity n = NaveEntity.create(level, t, "");
        n.moveTo(at.x, at.y, at.z, p.getRandom().nextFloat() * 360, 0);
        n.setAiLife(20 * 60 * 8);
        level.addFreshEntity(n);
        aviso(p, "encontro.lanterna.destrocos", 0xB0B8C0);
    }

    /** Comando de teste: força um encontro. */
    public static void forcar(ServerPlayer p, String tipo) {
        BuiltinCorps mine = Factions.playerCorps(p);
        BuiltinCorps c = mine == null || mine == BuiltinCorps.WHITE ? BuiltinCorps.GREEN : mine;
        switch (tipo) {
            case "capitania" -> capitania(p);
            case "patrulha" -> patrulha(p, c, "patrulha");
            case "inimiga" -> patrulha(p, inimigo(c, p) == null ? BuiltinCorps.YELLOW : inimigo(c, p), "inimiga");
            case "cargueiro" -> cargueiro(p);
            case "destrocos" -> destrocos(p);
            default -> pirata(p, null);
        }
    }

    /** Contrato "derrube uma nave pirata": garante uma nave no espaço. */
    public static void forcarPirata(ServerPlayer p, String contrato) {
        String key = "lanterna_ct_nave";
        long now = p.level().getGameTime();
        if (now < p.getPersistentData().getLong(key)) return;
        for (NaveEntity n : p.level().getEntitiesOfClass(NaveEntity.class, p.getBoundingBox().inflate(300), n -> n.ia().equals("pirata"))) {
            if (contrato.equals(n.getPersistentData().getString("contrato"))) return;
        }
        p.getPersistentData().putLong(key, now + 20 * 45);
        pirata(p, contrato);
    }

    /** O cargueiro parou perto: o mercador sai para negociar (uma vez). */
    public static void atracou(NaveEntity n) {
        if (n.getPersistentData().getBoolean("atracou")) return;
        n.getPersistentData().putBoolean("atracou", true);
        ServerLevel level = (ServerLevel) n.level();
        LanternaEntity m = LanternaEntity.mercador(level);
        Vec3 side = Vec3.directionFromRotation(0, n.getYRot() + 90).scale(n.tipo().width + 2);
        m.moveTo(n.getX() + side.x, n.getY() + 2, n.getZ() + side.z, n.getYRot(), 0);
        m.getPersistentData().putBoolean("lanterna_encontro", true);
        m.setNoGravity(true);
        level.addFreshEntity(m);
        Entity t = n.aiTarget() != null ? level.getEntity(n.aiTarget()) : null;
        if (t instanceof ServerPlayer sp) sp.sendSystemMessage(Component.translatable("encontro.lanterna.mercador_fala").withStyle(s -> s.withColor(0xFFB040)));
    }

    /** A patrulha da sua Tropa cumprimenta (uma vez). */
    public static void saudar(NaveEntity n, Player p) {
        if (n.getPersistentData().getBoolean("saudou") || !(p instanceof ServerPlayer sp)) return;
        n.getPersistentData().putBoolean("saudou", true);
        Entity pilot = n.getControllingPassenger();
        Component who = pilot != null ? pilot.getDisplayName() : Component.translatable("entity.lanterna." + n.getTipoNave());
        sp.sendSystemMessage(Component.literal("<").append(who).append("> ")
                .append(Component.translatable("encontro.lanterna.saudacao." + sp.getRandom().nextInt(3)))
                .withStyle(s -> s.withColor(pilot instanceof LanternaEntity l ? l.corEnergia() : 0x46E66E)));
    }

    private Encontros() {}
}
