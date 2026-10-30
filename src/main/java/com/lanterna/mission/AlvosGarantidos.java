package com.lanterna.mission;

import com.lanterna.LanternaMod;
import com.lanterna.cidade.CidadeMissoes;
import com.lanterna.especies.Especie;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.lore.LoreEntities;
import com.lanterna.lore.PlanetLore;
import com.lanterna.naves.NaveInterior;
import com.lanterna.space.SpaceBodies;
import com.lanterna.tropa.Contratos;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Garante que toda missão de "derrotar N" seja possível, mesmo as que dependem de bichos que nascem sozinhos
 * (a Patrulha dos Veltari pede 8 Ecos, mas o planeta só deixa 3 por perto; a Prova da Emoção pede 3 capitães piratas).
 *
 * Para cada objetivo de derrotar que ainda falta, sem lugar marcado e sem quem cuide dele (contratos, cidade e palácio
 * têm os próprios reforços): se não houver alvos vivos o bastante perto do jogador, chegam alguns a 16-26 blocos.
 * Também repõe os alvos de treino da base quando a missão pede para destruí-los e faltam.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AlvosGarantidos {
    private static final String CD = "lanterna_alvos_cd";
    private static final int ESPERA = 20 * 20;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 40 != 31) return;
        if (p.isSpectator() || p.isDeadOrDying()) return;
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null || d.active().isEmpty()) return;
        var dim = p.level().dimension();
        if (dim.equals(SpaceBodies.SPACE) || dim.equals(NaveInterior.DIM)) return;
        long now = p.level().getGameTime();
        CompoundTag cds = p.getPersistentData().getCompound(CD);
        // Precisa estar há ~5 s no mesmo mundo (os bichos que já existem carregam antes de contar)
        String mundo = dim.location().toString();
        if (!mundo.equals(cds.getString("mundo"))) {
            cds.putString("mundo", mundo);
            cds.putLong("chegou", now);
            p.getPersistentData().put(CD, cds);
            return;
        }
        if (now - cds.getLong("chegou") < 100) return;
        boolean mudou = false;
        for (String id : new ArrayList<>(d.active().keySet())) {
            if (id.startsWith("ct_") || CidadeMissoes.eDaCidade(id)) continue;
            Mission m = Missions.get(id);
            if (m == null) continue;
            for (int i = 0; i < m.objectives().size(); i++) {
                Objective o = m.objectives().get(i);
                int falta = o.count() - d.progress(id, i);
                if (falta <= 0) continue;
                if (o.type() == Objective.Type.DUMMY) {
                    if (alvosDeTreino(p, o, falta)) mudou = true;
                    continue;
                }
                if (o.type() != Objective.Type.KILL || o.pos() != null) continue;
                if (o.planet() != null) {
                    SpaceBodies.Body aqui = SpaceBodies.bodyAt(dim, p.getX(), p.getZ());
                    if (aqui == null || !aqui.id().equals(o.planet())) continue;
                }
                String chave = id + "#" + i;
                if (now < cds.getLong(chave)) continue;
                if (garantir(p, id, o.target(), falta)) {
                    cds.putLong(chave, now + ESPERA);
                    mudou = true;
                }
            }
        }
        if (mudou) p.getPersistentData().put(CD, cds);
    }

    /** O bicho conta para este alvo? */
    public static boolean conta(Entity e, String alvo) {
        if (!(e instanceof LivingEntity le) || !le.isAlive()) return false;
        return switch (alvo) {
            case "papel:pirata" -> e instanceof LanternaEntity l && l.papel() == Especie.Papel.PIRATA;
            case "papel:capitao" -> e instanceof LanternaEntity l && l.papel() == Especie.Papel.CAPITAO;
            case "papel:inimigo" -> e instanceof LanternaEntity l && l.papel() == Especie.Papel.LANTERNA && !l.getPersistentData().getBoolean("lanterna_aliado");
            default -> {
                if (alvo.startsWith("alvo:")) yield alvo.substring(5).equals(e.getPersistentData().getString("lanterna_alvo"));
                ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
                yield id != null && id.toString().equals(alvo);
            }
        };
    }

    /** Põe perto do jogador os alvos que faltam (até 4 por vez). */
    private static boolean garantir(ServerPlayer p, String id, String alvo, int falta) {
        ServerLevel level = p.serverLevel();
        int vivos = level.getEntities((Entity) null, p.getBoundingBox().inflate(48, 32, 48), e -> conta(e, alvo)).size();
        int quer = Math.min(falta, 4);
        if (vivos >= quer) return false;
        int feitos = 0;
        for (int k = vivos; k < quer; k++) {
            LivingEntity e = criar(level, alvo);
            if (e == null) break;
            BlockPos q = lugar(level, p);
            if (q == null) break;
            e.moveTo(q.getX() + 0.5, q.getY(), q.getZ() + 0.5, level.random.nextFloat() * 360, 0);
            e.getPersistentData().putString(Contratos.TAG, id + ":" + p.getStringUUID());
            if (e instanceof Mob mob) mob.setPersistenceRequired();
            level.addFreshEntity(e);
            feitos++;
        }
        if (feitos > 0) p.displayClientMessage(Component.translatable("missao.lanterna.alvos_perto"), true);
        return feitos > 0;
    }

    @Nullable
    private static LivingEntity criar(ServerLevel level, String alvo) {
        switch (alvo) {
            case "papel:pirata" -> { return LanternaEntity.pirata(level, false); }
            case "papel:capitao" -> { return LanternaEntity.pirata(level, true); }
            case "papel:inimigo" -> { return LanternaEntity.lanterna(level, com.lanterna.corps.BuiltinCorps.BLACK, false); }
            default -> {}
        }
        if (alvo.startsWith("papel:") || alvo.startsWith("alvo:")) return null;
        EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(alvo));
        if (t == null) return null;
        Entity e = t.create(level);
        return e instanceof LivingEntity le ? le : null;
    }

    /** Um ponto no chão a 16-26 blocos do jogador (tenta algumas vezes achar chão firme e seco). */
    @Nullable
    private static BlockPos lugar(ServerLevel level, ServerPlayer p) {
        for (int t = 0; t < 8; t++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            int d = 16 + level.random.nextInt(11);
            int x = (int) (p.getX() + Math.cos(a) * d), z = (int) (p.getZ() + Math.sin(a) * d);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos q = new BlockPos(x, y, z);
            if (y <= level.getMinBuildHeight() + 1 || !level.getFluidState(q.below()).isEmpty()) continue;
            if (Math.abs(y - p.getY()) > 20) continue;
            return q;
        }
        return null;
    }

    /** Missão de destruir alvos de treino: se faltam alvos na base, eles voltam. */
    private static boolean alvosDeTreino(ServerPlayer p, Objective o, int falta) {
        if (o.pos() == null || o.planet() == null) return false;
        SpaceBodies.Body aqui = SpaceBodies.bodyAt(p.level().dimension(), p.getX(), p.getZ());
        if (aqui == null || !aqui.id().equals(o.planet())) return false;
        BlockPos hub = o.pos();
        if (p.blockPosition().distSqr(hub.atY((int) p.getY())) > 48 * 48) return false;
        ServerLevel level = p.serverLevel();
        int n = level.getEntitiesOfClass(Entity.class, new AABB(hub.atY((int) p.getY())).inflate(24, 30, 24),
                e -> e.getType() == LoreEntities.ALVO_TREINO.get() && e.isAlive()).size();
        int quer = Math.min(3, falta);
        if (n >= quer) return false;
        PlanetLore pl = PlanetLore.byPlanet(o.planet());
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hub.getX() - 3, hub.getZ() + 6);
        for (int i = n; i < quer; i++) {
            Entity d = LoreEntities.ALVO_TREINO.get().create(level);
            if (d == null) break;
            int x = hub.getX() - 3 + i * 3, z = hub.getZ() + 6;
            int yy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            d.moveTo(x + 0.5, yy, z + 0.5, 180, 0);
            level.addFreshEntity(d);
        }
        return pl != null || y > 0;
    }

    private AlvosGarantidos() {}
}
