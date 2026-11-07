package com.lanterna.mogo;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.lore.entity.boss.LoreBoss;
import com.lanterna.network.ModNetwork;
import com.lanterna.power.RingPowers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Chamado do Mogo (bateria verde com Selo de Jade III, botão direito num alvo): uma jaula de construto prende o alvo,
 * o Mogo surge no céu, carrega e dispara um raio que mata o alvo na hora e abre um poço no terreno.
 * É o "ultimate" dos Lanternas Verdes: gasta metade da carga do anel e só pode ser usado uma vez por hora (tempo real).
 * O Mogo se recusa a atirar nos guardiões corrompidos (os chefes): eles foram companheiros dele.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChamadoMogo {
    public static final long RECARGA_MS = 60L * 60L * 1000L;
    public static final String TAG_ULTIMO = "lanterna_mogo_ultimo";
    public static final ResourceKey<DamageType> DANO = ResourceKey.create(Registries.DAMAGE_TYPE, LanternaMod.id("mogo"));
    public static final float ALCANCE = 96;

    /** Roteiro (ticks): jaula 0, o Mogo chega até 50, carrega até 80, raio de 80 a 110 (acerta em 84). */
    public static final int CHEGA = 50, CARGA = 80, ACERTA = 84, FIM = 112;

    private record Chamado(UUID dono, int alvo, ServerLevel level, Vec3 centro, Vec3 mogo, long inicio) {}

    private static final List<Chamado> ATIVOS = new ArrayList<>();

    /** Tenta chamar o Mogo. Devolve true se a bateria deve "consumir" o clique. */
    public static boolean usar(ServerPlayer p, ItemStack bateria) {
        if (!JadeViva.aceita(bateria) || JadeViva.nivel(bateria) < JadeViva.MAX) return false;
        RingData ring = ModCapabilities.get(p).orElse(null);
        if (ring == null || !ring.hasRing() || !BuiltinCorps.GREEN.id().equals(ring.corps())) {
            p.displayClientMessage(Component.translatable("mogo.lanterna.chamado.so_verde").withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        LivingEntity alvo = mirar(p);
        if (alvo == null) {
            p.displayClientMessage(Component.translatable("mogo.lanterna.chamado.sem_alvo").withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        if (alvo instanceof LoreBoss) {
            // o Mogo não luta contra os antigos companheiros
            p.sendSystemMessage(Component.translatable("mogo.lanterna.chamado.recusa.1").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));
            p.sendSystemMessage(Component.translatable("mogo.lanterna.chamado.recusa.2").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.translatable("mogo.lanterna.chamado.recusa.titulo").withStyle(ChatFormatting.GREEN)));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.translatable("mogo.lanterna.chamado.recusa.1").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
            p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1f, 0.6f);
            return true;
        }
        long falta = restante(p);
        if (falta > 0) {
            p.displayClientMessage(Component.translatable("mogo.lanterna.chamado.recarga", formatar(falta)).withStyle(ChatFormatting.RED), true);
            return true;
        }
        int custo = ring.maxCharge() / 2;
        if (ring.charge() < custo) {
            p.displayClientMessage(Component.translatable("mogo.lanterna.chamado.sem_carga").withStyle(ChatFormatting.RED), true);
            return true;
        }
        RingPowers.setCharge(p, ring, ring.charge() - custo);
        p.getPersistentData().putLong(TAG_ULTIMO, System.currentTimeMillis());
        comecar(p, alvo);
        return true;
    }

    /** O que o jogador está olhando (até 96 blocos), ou o alvo da trava de mira. */
    private static LivingEntity mirar(ServerPlayer p) {
        Vec3 olho = p.getEyePosition(), dir = p.getLookAngle();
        Vec3 fim = olho.add(dir.scale(ALCANCE));
        var bloco = p.level().clip(new net.minecraft.world.level.ClipContext(olho, fim, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        Vec3 ate = bloco.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? fim : bloco.getLocation();
        AABB area = p.getBoundingBox().expandTowards(dir.scale(ALCANCE)).inflate(2);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, olho, ate, area,
                e -> e instanceof LivingEntity le && le.isAlive() && e != p && !e.isSpectator() && e.isPickable(), ALCANCE * ALCANCE);
        if (hit != null && hit.getEntity() instanceof LivingEntity le) return le;
        // folga: o mais perto da linha de mira
        // folga por ângulo (uns 5 graus), para não precisar acertar o pixel de longe
        AABB larga = p.getBoundingBox().expandTowards(dir.scale(ALCANCE)).inflate(8);
        LivingEntity melhor = null;
        double md = 0.09;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, larga, e -> e != p && e.isAlive() && !e.isSpectator())) {
            Vec3 c = e.getBoundingBox().getCenter().subtract(olho);
            double t = c.dot(dir);
            if (t <= 0 || t > ALCANCE || !p.hasLineOfSight(e)) continue;
            double d = c.subtract(dir.scale(t)).length() / t;
            if (d < md) {
                md = d;
                melhor = e;
            }
        }
        return melhor;
    }

    private static void comecar(ServerPlayer p, LivingEntity alvo) {
        ServerLevel l = p.serverLevel();
        Vec3 c = alvo.position();
        // o Mogo aparece no céu, na direção para onde o jogador olha, bem alto
        Vec3 h = new Vec3(c.x - p.getX(), 0, c.z - p.getZ());
        if (h.lengthSqr() < 1e-3) h = p.getLookAngle().multiply(1, 0, 1);
        h = h.normalize();
        Vec3 mogo = c.add(h.scale(150)).add(0, 230, 0);
        Chamado ch = new Chamado(p.getUUID(), alvo.getId(), l, c, mogo, l.getGameTime());
        ATIVOS.add(ch);
        ChamadoPacket pk = new ChamadoPacket(p.getId(), alvo.getId(), c.x, c.y, c.z, mogo.x, mogo.y, mogo.z, l.getGameTime());
        for (ServerPlayer o : l.players()) if (o.distanceToSqr(c) < 400 * 400 || o == p) ModNetwork.sendTo(o, pk);
        l.playSound(null, BlockPos.containing(c), SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 2f, 0.5f);
        l.playSound(null, BlockPos.containing(c), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3f, 0.6f);
        p.sendSystemMessage(Component.translatable("mogo.lanterna.chamado.fala").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));
        if (alvo instanceof ServerPlayer sp)
            sp.sendSystemMessage(Component.translatable("mogo.lanterna.chamado.preso").withStyle(ChatFormatting.DARK_GREEN));
    }

    public static boolean preso(Entity e) {
        for (Chamado c : ATIVOS) if (c.alvo == e.getId() && c.level == e.level()) return true;
        return false;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ATIVOS.isEmpty()) return;
        Iterator<Chamado> it = ATIVOS.iterator();
        while (it.hasNext()) {
            Chamado c = it.next();
            long t = c.level.getGameTime() - c.inicio;
            Entity alvo = c.level.getEntity(c.alvo);
            // jaula: o alvo não sai do lugar
            if (alvo instanceof LivingEntity le && le.isAlive() && t < ACERTA) {
                le.setDeltaMovement(0, 0, 0);
                if (le.position().distanceToSqr(c.centro) > 0.01) {
                    if (le instanceof ServerPlayer sp) sp.connection.teleport(c.centro.x, c.centro.y, c.centro.z, sp.getYRot(), sp.getXRot());
                    else le.teleportTo(c.centro.x, c.centro.y, c.centro.z);
                }
                le.fallDistance = 0;
                if (le instanceof net.minecraft.world.entity.Mob m) m.getNavigation().stop();
            }
            if (t == CHEGA) c.level.playSound(null, BlockPos.containing(c.centro), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 4f, 0.5f);
            if (t == CARGA) c.level.playSound(null, BlockPos.containing(c.centro), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 6f, 0.4f);
            if (t == ACERTA) {
                c.level.playSound(null, BlockPos.containing(c.centro), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 6f, 0.5f);
                c.level.playSound(null, BlockPos.containing(c.centro), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 6f, 0.6f);
                if (alvo instanceof LivingEntity le && le.isAlive()) matar(c, le);
            }
            if (t >= ACERTA && t < FIM - 6) cavar(c, (int) (t - ACERTA));
            if (t >= FIM) it.remove();
        }
    }

    private static void matar(Chamado c, LivingEntity le) {
        Entity dono = c.level.getServer().getPlayerList().getPlayer(c.dono);
        DamageSource src = new DamageSource(c.level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DANO), dono);
        le.invulnerableTime = 0;
        le.hurt(src, Float.MAX_VALUE);
        if (le.isAlive() && !(le instanceof ServerPlayer sp && (sp.isCreative() || sp.isSpectator()))) {
            le.setHealth(0);
            le.die(src);
        }
    }

    /** O raio abre um poço: cada tick desce mais um pedaço (raio de 3 blocos), sem passar da bedrock. */
    private static void cavar(Chamado c, int passo) {
        BlockPos base = BlockPos.containing(c.centro);
        int topo = base.getY() + 12 - passo * 2, fundo = topo - 2;
        int min = c.level.getMinBuildHeight() + 1;
        for (int y = topo; y > fundo; y--) {
            if (y < min || y < base.getY() - 30) return;
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 10) continue;
                BlockPos bp = new BlockPos(base.getX() + dx, y, base.getZ() + dz);
                BlockState s = c.level.getBlockState(bp);
                if (s.isAir() || s.getDestroySpeed(c.level, bp) < 0) continue;
                boolean borda = dx * dx + dz * dz > 6;
                c.level.setBlock(bp, borda && c.level.random.nextInt(3) == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** Pérola do Ender, coro e afins não tiram ninguém da jaula. */
    @SubscribeEvent
    public static void teleporte(EntityTeleportEvent e) {
        if (e instanceof EntityTeleportEvent.TeleportCommand || e instanceof EntityTeleportEvent.SpreadPlayersCommand) return;
        if (preso(e.getEntity())) e.setCanceled(true);
    }

    /** Jade II ou mais: a bateria verde equipada recarrega o anel devagar (1% a cada 2 s). */
    @SubscribeEvent
    public static void recargaJade(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 40 != 17) return;
        ItemStack bat = com.lanterna.power.EquipmentHelper.battery(p);
        if (!JadeViva.aceita(bat) || JadeViva.nivel(bat) < 2) return;
        RingData ring = ModCapabilities.get(p).orElse(null);
        if (ring == null || !ring.hasRing() || ring.charge() >= ring.maxCharge() || !BuiltinCorps.GREEN.id().equals(ring.corps())) return;
        RingPowers.setCharge(p, ring, Math.min(ring.maxCharge(), ring.charge() + Math.max(1, ring.maxCharge() / 100)));
    }

    public static long restante(ServerPlayer p) {
        long ultimo = p.getPersistentData().getLong(TAG_ULTIMO);
        return Math.max(0, ultimo + RECARGA_MS - System.currentTimeMillis());
    }

    public static void resetar(ServerPlayer p) {
        p.getPersistentData().remove(TAG_ULTIMO);
    }

    public static String formatar(long ms) {
        long s = ms / 1000;
        return String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    private ChamadoMogo() {}
}
