package com.lanterna.mogo;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.construct.ConstructEntity;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.network.ModNetwork;
import com.lanterna.space.SpaceBodies;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * O Mogo se defende, no espaço:
 *  - a Tropa da Morte (anel preto) ele SEMPRE ataca quando chega perto: um feixe de luz enorme sai da superfície;
 *  - as outras Tropas inimigas não pousam, mas ele só atira de volta em quem atirar nele (laser, rajada, punho...
 *    qualquer construto que acerte o planeta). Fica "bravo" por 20 s depois da última provocação.
 * O feixe é grande, mas o dano é o mesmo do laser de um Lanterna oficial (NPC), e nunca mata: deixa pelo menos meio
 * coração (quem expulsa é o próprio Mogo).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MogoDefesa {
    public static final float DANO = 2.4f;
    private static final int ALCANCE = 260;        // além da superfície
    private static final int RITMO = 50;           // ticks entre um feixe e outro
    private static final int BRAVO = 20 * 20;

    private static final Map<UUID, Long> PROVOCOU = new HashMap<>();
    private static final Map<UUID, Long> ULTIMO = new HashMap<>();
    private static final Map<UUID, Long> AVISO = new HashMap<>();

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 5 != 0) return;
        if (!p.level().dimension().equals(SpaceBodies.SPACE) || p.isCreative() || p.isSpectator() || !p.isAlive()) return;
        SpaceBodies.Body mogo = SpaceBodies.byId("mogo");
        if (mogo == null) return;
        Vec3 c = mogo.center();
        double dist = p.position().distanceTo(c);
        if (dist > mogo.radius() + ALCANCE) return;
        ServerLevel l = p.serverLevel();
        long agora = l.getGameTime();
        var r = ModCapabilities.get(p).orElse(null);
        String corps = r != null && r.hasRing() && r.corps() != null ? r.corps().getPath() : "";
        if ("green".equals(corps)) return; // os seus ele não ataca

        // alguém da Tropa atirou no planeta?
        if (atirou(p, l, c, mogo.radius())) {
            if (!PROVOCOU.containsKey(p.getUUID()) || agora - PROVOCOU.get(p.getUUID()) > BRAVO) MogoMissoes.falar(p, "revida", true);
            PROVOCOU.put(p.getUUID(), agora);
        }
        boolean morte = BuiltinCorps.BLACK.id().getPath().equals(corps);
        boolean bravo = PROVOCOU.containsKey(p.getUUID()) && agora - PROVOCOU.get(p.getUUID()) <= BRAVO;
        if (!morte && !bravo) return;
        if (agora - ULTIMO.getOrDefault(p.getUUID(), 0L) < RITMO) return;
        ULTIMO.put(p.getUUID(), agora);
        if (morte && agora - AVISO.getOrDefault(p.getUUID(), -99999L) > 20 * 60) {
            AVISO.put(p.getUUID(), agora);
            MogoMissoes.falar(p, "morte", true);
        }
        disparar(p, l, c, mogo.radius());
    }

    /** Algum construto do jogador (laser, rajada, punho...) encostou no Mogo? */
    private static boolean atirou(ServerPlayer p, ServerLevel l, Vec3 c, double raio) {
        AABB area = new AABB(c, c).inflate(raio + 30);
        for (ConstructEntity ce : l.getEntitiesOfClass(ConstructEntity.class, area, x -> x.owner() == p)) {
            if (ce.position().distanceTo(c) <= raio + 4) return true;
            Vec3 fim = ce.end();
            if (fim != null && fim.distanceTo(c) <= raio + 4) return true;
        }
        return false;
    }

    private static void disparar(ServerPlayer p, ServerLevel l, Vec3 c, double raio) {
        Entity alvo = p.getVehicle() != null ? p.getVehicle() : p;
        Vec3 dir = alvo.position().subtract(c).normalize();
        Vec3 de = c.add(dir.scale(raio * 0.98));
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new Raio(de.x, de.y, de.z, p.getId()));
        l.playSound(null, alvo.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 3f, 0.5f);
        l.playSound(null, alvo.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.4f, 1.3f);
        // dano do laser de um Lanterna oficial; nunca mata
        float dano = Math.min(DANO, p.getHealth() - 1f);
        if (dano > 0) p.hurt(l.damageSources().magic(), dano);
        if (p.getVehicle() != null) p.getVehicle().hurt(l.damageSources().magic(), DANO * 3);
        // e empurra para longe
        alvo.push(dir.x * 0.8, dir.y * 0.8, dir.z * 0.8);
        alvo.hurtMarked = true;
    }

    /** Feixe visível: da superfície do Mogo até o alvo (o cliente desenha). */
    public record Raio(double x, double y, double z, int alvo) {
        public static void encode(Raio m, FriendlyByteBuf b) { b.writeDouble(m.x); b.writeDouble(m.y); b.writeDouble(m.z); b.writeVarInt(m.alvo); }
        public static Raio decode(FriendlyByteBuf b) { return new Raio(b.readDouble(), b.readDouble(), b.readDouble(), b.readVarInt()); }
        public static void handle(Raio m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.lanterna.client.mogo.ClienteRaioMogo.novo(m)));
            ctx.get().setPacketHandled(true);
        }
    }

    private MogoDefesa() {}
}
