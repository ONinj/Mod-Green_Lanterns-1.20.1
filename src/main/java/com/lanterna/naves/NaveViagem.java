package com.lanterna.naves;

import com.lanterna.LanternaMod;
import com.lanterna.mission.LoreCapability;
import com.lanterna.mission.LoreData;
import com.lanterna.space.SpaceBodies;
import com.lanterna.space.SpaceTravel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * A nave leva a tripulação junto entre a Terra, o espaço e os planetas (as mesmas regras do voo com o anel):
 * subir acima de Y 330 leva ao espaço; entrar num planeta no espaço pousa nele; a Terra devolve para onde se saiu.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NaveViagem {

    private record Remount(UUID rider, UUID ship, ResourceKey<Level> dim, int[] delay) {}
    private static final List<Remount> PENDING = new ArrayList<>();

    static void check(NaveEntity n) {
        if (!(n.getControllingPassenger() instanceof ServerPlayer pilot)) return;
        ResourceKey<Level> dim = n.level().dimension();
        var server = n.getServer();
        if (server == null) return;
        if ((dim.equals(Level.OVERWORLD) || SpaceBodies.isPlanet(dim)) && n.getY() > SpaceTravel.EXIT_HEIGHT) {
            ServerLevel space = server.getLevel(SpaceBodies.SPACE);
            if (space == null) return;
            Vec3 out = SpaceTravel.exitPoint(pilot, dim, n.getX(), n.getZ());
            mover(n, space, out, n.getYRot(), "space.lanterna.enter");
            return;
        }
        if (!dim.equals(SpaceBodies.SPACE)) return;
        SpaceBodies.Body body = SpaceBodies.inside(n.position());
        if (body == null) return;
        if (SpaceBodies.isHidden(body.id()) && !pilot.isCreative()
                && !LoreCapability.get(pilot).map(LoreData::geneseRevealed).orElse(false)) return;
        ServerLevel target = server.getLevel(body.dimension());
        if (target == null) return;
        Vec3 pos = body == SpaceBodies.EARTH ? SpaceTravel.earthReturn(pilot, target) : SpaceTravel.landingPoint(body, n.position());
        mover(n, target, pos, n.getYRot(), "space.lanterna.landing");
    }

    /** Muda a nave de mundo com todos os passageiros (que voltam aos mesmos assentos). */
    public static void mover(NaveEntity n, ServerLevel dest, Vec3 pos, float yaw, String messageKey) {
        List<Entity> riders = new ArrayList<>(n.getPassengers());
        n.ejectPassengers();
        Entity moved = n.changeDimension(dest, teleporter(pos, yaw));
        if (moved == null) return;
        for (Entity r : riders) {
            Entity at;
            if (r instanceof ServerPlayer p) {
                p.teleportTo(dest, pos.x, pos.y + 1, pos.z, yaw, p.getXRot());
                p.fallDistance = 0;
                p.displayClientMessage(Component.translatable(messageKey,
                        SpaceBodies.bodyAt(dest.dimension(), pos.x, pos.z) != null
                                ? SpaceBodies.bodyAt(dest.dimension(), pos.x, pos.z).displayName()
                                : Component.translatable("planet.lanterna.space")), true);
                at = p;
            } else {
                at = r.changeDimension(dest, teleporter(pos, yaw));
            }
            if (at != null) PENDING.add(new Remount(at.getUUID(), moved.getUUID(), dest.dimension(), new int[]{8}));
        }
        dest.playSound(null, moved.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.NEUTRAL, 0.3f, 1.6f);
    }

    private static ITeleporter teleporter(Vec3 pos, float yaw) {
        return new ITeleporter() {
            @Override
            public Entity placeEntity(Entity entity, ServerLevel current, ServerLevel dest, float y, Function<Boolean, Entity> reposition) {
                Entity e = reposition.apply(false);
                e.moveTo(pos.x, pos.y, pos.z, yaw, e.getXRot());
                return e;
            }

            @Override
            public PortalInfo getPortalInfo(Entity entity, ServerLevel dest, Function<ServerLevel, PortalInfo> defaultPortalInfo) {
                return new PortalInfo(pos, Vec3.ZERO, yaw, entity.getXRot());
            }

            @Override
            public boolean playTeleportSound(ServerPlayer player, ServerLevel sourceWorld, ServerLevel destWorld) {
                return false;
            }
        };
    }

    /** Remonta a tripulação alguns ticks depois (o cliente precisa conhecer a nave antes). */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        Iterator<Remount> it = PENDING.iterator();
        while (it.hasNext()) {
            Remount r = it.next();
            if (--r.delay()[0] > 0) continue;
            it.remove();
            ServerLevel level = event.getServer().getLevel(r.dim());
            if (level == null) continue;
            Entity rider = level.getEntity(r.rider());
            Entity ship = level.getEntity(r.ship());
            if (rider != null && ship != null && !rider.isPassenger()) rider.startRiding(ship, true);
        }
    }

    private NaveViagem() {}
}
