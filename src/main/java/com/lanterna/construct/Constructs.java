package com.lanterna.construct;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.mission.MissionManager;
import com.lanterna.power.EquipmentHelper;
import com.lanterna.power.RingPowers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Os construtos no servidor: confere carga, afinidade e recarga, faz o construto acontecer,
 * gasta a carga e dá afinidade. Laser e picareta ficam ativos enquanto a tecla está apertada.
 *
 * Força = afinidade (1,0 a 2,0) × Tropa (Branco 1,5; Preto 1,35; as outras 1,0).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Constructs {
    private static final Map<UUID, EnumMap<ConstructType, Long>> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Active> ACTIVE = new HashMap<>();
    private static final Map<UUID, ConstructEntity> SHIELDS = new HashMap<>();
    private static final Map<UUID, ConstructType> LAST_USED = new HashMap<>();

    private static final class Active {
        final ConstructType type;
        @Nullable ConstructEntity visual;
        int ticks;
        @Nullable BlockPos mining;
        float progress;

        Active(ConstructType type) {
            this.type = type;
        }
    }

    // ===================== Força =====================

    public static float corpsPower(RingData ring) {
        return com.lanterna.combate.Supremos.dano(ring.corps(), null);
    }

    public static float power(ServerPlayer player, RingData ring) {
        return Affinity.power(Affinity.points(EquipmentHelper.ring(player))) * com.lanterna.combate.Supremos.dano(ring.corps(), player)
                * com.lanterna.tropa.Nivel.forca(player);
    }

    public static int level(ServerPlayer player) {
        return Affinity.level(Affinity.points(EquipmentHelper.ring(player)));
    }

    /** Último construto usado (o Vigia Partido copia). */
    @Nullable
    public static ConstructType lastUsed(UUID player) {
        return LAST_USED.get(player);
    }

    // ===================== Usar =====================

    public static void cast(ServerPlayer player, String id) {
        ConstructType type = ConstructType.byId(id);
        RingData ring = ModCapabilities.get(player).orElse(null);
        if (type == null || ring == null || !ring.hasRing()) return;
        if (!ring.powerActive()) {
            player.displayClientMessage(Component.translatable("message.lanterna.power_needed"), true);
            return;
        }
        int lvl = level(player);
        if (lvl < type.unlockLevel) {
            player.displayClientMessage(Component.translatable("message.lanterna.construct_locked", type.displayName(),
                    Affinity.levelName(type.unlockLevel)), true);
            return;
        }
        long now = player.level().getGameTime();
        EnumMap<ConstructType, Long> cds = COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new EnumMap<>(ConstructType.class));
        if (cds.getOrDefault(type, 0L) > now) return;
        int cost = cost(type, lvl);
        if (ring.charge() < cost && !player.isCreative()) {
            player.displayClientMessage(Component.translatable("message.lanterna.no_charge"), true);
            return;
        }
        // Anel desconfiado às vezes hesita
        if (lvl == 0 && player.getRandom().nextInt(10) == 0) {
            player.displayClientMessage(Component.translatable("message.lanterna.construct_hesitates").withStyle(s -> s.withColor(ring.color())), true);
            cds.put(type, now + 20);
            return;
        }
        if (type.hold) {
            Active cur = ACTIVE.get(player.getUUID());
            if (cur != null && cur.type == type && cur.visual != null && cur.visual.isAlive()) return; // já está ligado
            release(player, null);
            Active a = new Active(type);
            ACTIVE.put(player.getUUID(), a);
            if (type == ConstructType.LASER) {
                a.visual = ConstructEntity.spawnBeam(player, ring.color(), 3.0f * power(player, ring), 20 * 60 * 10);
                player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.8f);
            }
        } else {
            boolean ok = perform(player, ring, type, lvl);
            if (!ok) return;
            if (!player.isCreative()) RingPowers.setCharge(player, ring, ring.charge() - com.lanterna.tropa.Nivel.custoComDesconto(player, cost));
            cds.put(type, now + type.cooldown);
        }
        LAST_USED.put(player.getUUID(), type);
        Affinity.add(player, type.hold ? 1 : 3);
        MissionManager.onConstruct(player, type.id);
        spark(player, ring.color());
    }

    public static void release(ServerPlayer player, @Nullable String id) {
        Active a = ACTIVE.get(player.getUUID());
        if (a == null || (id != null && !a.type.id.equals(id))) return;
        ACTIVE.remove(player.getUUID());
        if (a.visual != null) a.visual.discard();
        if (a.mining != null) player.level().destroyBlockProgress(player.getId(), a.mining, -1);
    }

    private static int cost(ConstructType type, int lvl) {
        return Math.max(1, Math.round(type.cost * (1f - 0.05f * lvl)));
    }

    private static boolean perform(ServerPlayer player, RingData ring, ConstructType type, int lvl) {
        ServerLevel level = player.serverLevel();
        float pow = power(player, ring);
        int color = ring.color();
        Vec3 look = player.getViewVector(1f);
        switch (type) {
            case PUNHO -> {
                ConstructEntity.spawnProjectile(player, ConstructEntity.Kind.FIST, look, 1.5, color, 10f * pow, 1f + 0.15f * lvl);
                level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1f, 0.6f);
            }
            case RAJADA -> {
                for (int i = 0; i < 3; i++) {
                    Vec3 dir = look.add((player.getRandom().nextDouble() - 0.5) * 0.08, (player.getRandom().nextDouble() - 0.5) * 0.08,
                            (player.getRandom().nextDouble() - 0.5) * 0.08);
                    ConstructEntity.spawnProjectile(player, ConstructEntity.Kind.BOLT, dir, 2.2 + i * 0.15, color, 4f * pow, 1f);
                }
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.8f);
            }
            case MARTELO -> {
                Vec3 spot = groundAhead(player, 14);
                ConstructEntity.spawnHammer(player, spot, color, 18f * pow, 1f + 0.12f * lvl);
            }
            case LAMINA -> {
                ConstructEntity blades = ConstructEntity.create(player, ConstructEntity.Kind.BLADE, color, 70, 5f * pow, 1f + 0.08f * lvl);
                level.addFreshEntity(blades);
            }
            case ESCUDO -> {
                ConstructEntity old = SHIELDS.remove(player.getUUID());
                if (old != null) old.discard();
                ConstructEntity shield = ConstructEntity.create(player, ConstructEntity.Kind.SHIELD, color, 200 + 30 * lvl, 0, 1f + 0.1f * lvl);
                level.addFreshEntity(shield);
                SHIELDS.put(player.getUUID(), shield);
                level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 1.5f);
            }
            case PAREDE -> {
                return buildWall(player, color, lvl);
            }
            case PONTE -> {
                return buildBridge(player, color, lvl);
            }
            case JAULA -> {
                LivingEntity target = lookedEntity(player, 24);
                if (target == null) {
                    player.displayClientMessage(Component.translatable("message.lanterna.construct_no_target"), true);
                    return false;
                }
                cage(level, target, color, 120 + 30 * lvl);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120 + 30 * lvl, 3));
            }
            case MAO -> {
                Entity target = lookedEntity(player, 24);
                if (target == null) {
                    // Sem alvo: puxa os itens que estão na mira
                    Vec3 end = player.getEyePosition().add(look.scale(20));
                    for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(player.getEyePosition(), end).inflate(2))) {
                        item.setPos(player.getX(), player.getY() + 0.5, player.getZ());
                    }
                    level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1f, 0.8f);
                    return true;
                }
                ConstructEntity hand = ConstructEntity.create(player, ConstructEntity.Kind.HAND, color, 80, 4f * pow, 1f + 0.1f * lvl);
                hand.setEnd(player.getEyePosition().add(look));
                hand.setTarget(target);
                level.addFreshEntity(hand);
            }
            case GANCHO -> {
                Vec3 far = player.getEyePosition().add(look.scale(32 + 4 * lvl));
                BlockHitResult hit = level.clip(new ClipContext(player.getEyePosition(), far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                if (hit.getType() == HitResult.Type.MISS) {
                    player.displayClientMessage(Component.translatable("message.lanterna.construct_no_target"), true);
                    return false;
                }
                ConstructEntity chain = ConstructEntity.create(player, ConstructEntity.Kind.GRAPPLE, color, 45, 0, 1f);
                chain.setEnd(hit.getLocation());
                level.addFreshEntity(chain);
                level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1f, 1.2f);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ===================== Construtos de blocos =====================

    private static boolean buildWall(ServerPlayer player, int color, int lvl) {
        ServerLevel level = player.serverLevel();
        Direction facing = player.getDirection();
        BlockPos center = player.blockPosition().relative(facing, 3);
        int half = 2 + lvl / 2;
        int height = 4 + lvl / 2;
        Direction side = facing.getClockWise();
        int placed = 0;
        int life = 20 * (20 + 4 * lvl);
        for (int w = -half; w <= half; w++) {
            BlockPos column = center.relative(side, w);
            // Acompanha o chão
            int y = column.getY();
            while (y > column.getY() - 3 && level.getBlockState(new BlockPos(column.getX(), y - 1, column.getZ())).isAir()) y--;
            for (int h = 0; h < height; h++) {
                if (ConstructBlock.place(level, new BlockPos(column.getX(), y + h, column.getZ()), color, life)) placed++;
            }
        }
        if (placed > 0) level.playSound(null, center, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 1.2f);
        return placed > 0;
    }

    private static boolean buildBridge(ServerPlayer player, int color, int lvl) {
        ServerLevel level = player.serverLevel();
        Vec3 dir = player.getViewVector(1f);
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() < 1e-3) return false;
        flat = flat.normalize();
        Vec3 side = new Vec3(-flat.z, 0, flat.x);
        // Sobe ou desce um pouco conforme você olha
        double slope = Math.max(-0.5, Math.min(0.5, dir.y));
        int len = 20 + 4 * lvl;
        int life = 20 * (30 + 6 * lvl);
        int placed = 0;
        Vec3 start = player.position().add(0, -1, 0);
        for (int i = 1; i <= len; i++) {
            Vec3 p = start.add(flat.scale(i)).add(0, slope * i, 0);
            for (int w = -1; w <= 1; w++) {
                if (ConstructBlock.place(level, BlockPos.containing(p.add(side.scale(w))), color, life)) placed++;
            }
        }
        if (placed > 0) level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 1.0f);
        return placed > 0;
    }

    private static void cage(ServerLevel level, LivingEntity target, int color, int life) {
        AABB box = target.getBoundingBox();
        int x0 = (int) Math.floor(box.minX) - 1, x1 = (int) Math.floor(box.maxX) + 1;
        int y0 = (int) Math.floor(box.minY) - 1, y1 = (int) Math.floor(box.maxY) + 1;
        int z0 = (int) Math.floor(box.minZ) - 1, z1 = (int) Math.floor(box.maxZ) + 1;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    boolean shell = x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1;
                    boolean bars = (x + z) % 2 == 0 || y == y0 || y == y1;
                    if (shell && bars) ConstructBlock.place(level, new BlockPos(x, y, z), color, life);
                }
            }
        }
        level.playSound(null, target.blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 1.2f, 0.7f);
    }

    // ===================== Tick (laser e picareta) =====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Active a = ACTIVE.get(player.getUUID());
        if (a == null) return;
        RingData ring = ModCapabilities.get(player).orElse(null);
        if (ring == null || !ring.hasRing() || !ring.powerActive() || (ring.charge() <= 0 && !player.isCreative())) {
            release(player, null);
            return;
        }
        a.ticks++;
        if (!player.isCreative()) RingPowers.setCharge(player, ring, ring.charge() - com.lanterna.tropa.Nivel.custoComDesconto(player, cost(a.type, level(player))));
        if (a.ticks % 40 == 0) Affinity.add(player, 1);
        if (a.type == ConstructType.PICARETA) mine(player, ring, a);
        if (a.ticks > 20 * 60 * 5) release(player, null);
    }

    private static void mine(ServerPlayer player, RingData ring, Active a) {
        ServerLevel level = player.serverLevel();
        Vec3 far = player.getEyePosition().add(player.getViewVector(1f).scale(6));
        BlockHitResult hit = level.clip(new ClipContext(player.getEyePosition(), far, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (a.visual == null || !a.visual.isAlive()) {
            a.visual = ConstructEntity.create(player, ConstructEntity.Kind.PICKAXE, ring.color(), 20 * 60 * 10, 0, 1f);
            level.addFreshEntity(a.visual);
        }
        if (hit.getType() != HitResult.Type.BLOCK) {
            a.visual.setEnd(far);
            return;
        }
        BlockPos pos = hit.getBlockPos();
        a.visual.setEnd(Vec3.atCenterOf(pos));
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        if (state.isAir() || hardness < 0 || hardness > 60) return;
        if (!pos.equals(a.mining)) {
            if (a.mining != null) level.destroyBlockProgress(player.getId(), a.mining, -1);
            a.mining = pos;
            a.progress = 0;
        }
        int lvl = level(player);
        a.progress += (0.35f + 0.12f * lvl) / Math.max(0.6f, hardness * 0.35f);
        level.destroyBlockProgress(player.getId(), pos, (int) Math.min(9, a.progress * 10));
        if (a.ticks % 4 == 0) level.playSound(null, pos, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.6f, 1.2f);
        if (a.progress >= 1f) {
            a.progress = 0;
            level.destroyBlockProgress(player.getId(), pos, -1);
            breakBlock(player, pos);
            // Nível 3+: abre um buraco 3x3
            if (lvl >= 3) {
                Direction face = hit.getDirection();
                for (int u = -1; u <= 1; u++) {
                    for (int v = -1; v <= 1; v++) {
                        if (u == 0 && v == 0) continue;
                        BlockPos p = switch (face.getAxis()) {
                            case X -> pos.offset(0, u, v);
                            case Y -> pos.offset(u, 0, v);
                            case Z -> pos.offset(u, v, 0);
                        };
                        BlockState s = level.getBlockState(p);
                        float h = s.getDestroySpeed(level, p);
                        if (!s.isAir() && h >= 0 && h <= 60) breakBlock(player, p);
                    }
                }
            }
            if (!player.isCreative()) RingPowers.setCharge(player, ring, ring.charge() - 2);
            a.mining = null;
        }
    }

    /** Quebra como se fosse uma picareta de netherite (os minérios caem certinho). */
    private static void breakBlock(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (state.is(ConstructRegistry.CONSTRUCT_BLOCK.get())) return;
        int exp = ForgeHooks.onBlockBreakEvent(level, player.gameMode.getGameModeForPlayer(), player, pos);
        if (exp < 0) return;
        BlockEntity be = level.getBlockEntity(pos);
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        if (!player.isCreative()) Block.dropResources(state, level, pos, be, player, tool);
        level.destroyBlock(pos, false, player);
        if (exp > 0) state.getBlock().popExperience(level, pos, exp);
    }

    // ===================== Escudo =====================

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ConstructEntity shield = SHIELDS.get(player.getUUID());
        if (shield == null) return;
        if (!shield.isAlive()) {
            SHIELDS.remove(player.getUUID());
            return;
        }
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                || event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL)) return;
        RingData ring = ModCapabilities.get(player).orElse(null);
        if (ring == null) return;
        event.setCanceled(true);
        int drain = Math.max(1, Math.round(event.getAmount() * 3));
        RingPowers.setCharge(player, ring, ring.charge() - drain);
        ServerLevel level = player.serverLevel();
        int c = ring.color();
        level.sendParticles(new DustParticleOptions(new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f), 1.5f),
                player.getX(), player.getY() + 1, player.getZ(), 20, 0.8, 0.9, 0.8, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.4f);
        if (ring.charge() <= 0) {
            shield.discard();
            SHIELDS.remove(player.getUUID());
        }
    }

    // ===================== Ajudantes =====================

    private static Vec3 groundAhead(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 far = eye.add(player.getViewVector(1f).scale(range));
        LivingEntity target = lookedEntity(player, range);
        if (target != null) return target.position();
        BlockHitResult hit = player.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 spot = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
        // Desce até o chão
        BlockPos p = BlockPos.containing(spot);
        for (int i = 0; i < 12 && player.level().getBlockState(p.below()).isAir(); i++) p = p.below();
        return new Vec3(spot.x, p.getY(), spot.z);
    }

    @Nullable
    public static LivingEntity lookedEntity(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1f);
        Vec3 far = eye.add(look.scale(range));
        BlockHitResult block = player.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) far = block.getLocation();
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, far).inflate(1.5),
                e -> e != player && e.isAlive() && !e.isSpectator())) {
            var clip = e.getBoundingBox().inflate(0.5).clip(eye, far);
            if (clip.isPresent() && clip.get().distanceToSqr(eye) < bestD) {
                bestD = clip.get().distanceToSqr(eye);
                best = e;
            }
        }
        return best;
    }

    private static void spark(ServerPlayer player, int c) {
        player.serverLevel().sendParticles(new DustParticleOptions(new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f), 1.2f),
                player.getX(), player.getY() + 1.1, player.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        Active a = ACTIVE.remove(id);
        if (a != null && a.visual != null) a.visual.discard();
        ConstructEntity s = SHIELDS.remove(id);
        if (s != null) s.discard();
        COOLDOWNS.remove(id);
    }

    private Constructs() {}
}
