package com.lanterna.baterias;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;

import java.util.function.Function;

/** Leva o jogador para a plataforma de chegada da dimensão fonte_<tropa> (criada na primeira visita). */
public class BateriaTeleporte implements ITeleporter {
    private final Tropa tropa;
    public BateriaTeleporte(Tropa t) { this.tropa = t; }

    @Override
    public Entity placeEntity(Entity entity, ServerLevel atual, ServerLevel destino, float yaw, Function<Boolean, Entity> reposicionar) {
        BlockPos centro = new BlockPos(0, 100, 0);
        if (destino.getBlockState(centro).isAir()) {
            destino.getStructureManager().get(new ResourceLocation(ModBaterias.MODID, "chegada_" + tropa.nome)).ifPresent(t -> {
                BlockPos canto = centro.offset(-6, 0, -6);
                t.placeInWorld(destino, canto, canto, new StructurePlaceSettings(), destino.random, 2);
            });
        }
        Entity e = reposicionar.apply(false);
        e.teleportTo(0.5, 101, 3.5);          // ao lado do círculo de retorno (o centro leva de volta)
        return e;
    }

    /** chega direto em cima da plataforma (y 101), ao lado do círculo de retorno */
    @Override
    public PortalInfo getPortalInfo(Entity e, ServerLevel destino, Function<ServerLevel, PortalInfo> padrao) {
        return new PortalInfo(new Vec3(0.5, 101, 3.5), Vec3.ZERO, e.getYRot(), e.getXRot());
    }

    @Override public boolean playTeleportSound(ServerPlayer p, ServerLevel de, ServerLevel para) { return true; }

    /** volta para onde o jogador estava antes de entrar, um pouco para trás (fora do campo) */
    public static void voltar(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(d.getString("bateria_volta_dim").isEmpty() ? "minecraft:overworld" : d.getString("bateria_volta_dim")));
        ServerLevel alvo = p.server.getLevel(dim);
        if (alvo == null) alvo = p.server.overworld();
        float yaw = d.getFloat("bateria_volta_yaw");
        Vec3 tras = Vec3.directionFromRotation(0, yaw).scale(-3.5);
        double x = d.contains("bateria_volta_x") ? d.getDouble("bateria_volta_x") + tras.x : alvo.getSharedSpawnPos().getX();
        double y = d.contains("bateria_volta_y") ? d.getDouble("bateria_volta_y") : alvo.getSharedSpawnPos().getY();
        double z = d.contains("bateria_volta_z") ? d.getDouble("bateria_volta_z") + tras.z : alvo.getSharedSpawnPos().getZ();
        p.teleportTo(alvo, x, y, z, yaw + 180, 0);
    }
}
