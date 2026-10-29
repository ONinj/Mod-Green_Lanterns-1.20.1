package com.lanterna.lore.entity.boss;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.construct.ConstructBlock;
import com.lanterna.construct.ConstructEntity;
import com.lanterna.construct.ConstructType;
import com.lanterna.construct.Constructs;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.lore.LoreBlocks;
import com.lanterna.lore.LoreEntities;
import com.lanterna.lore.LoreWorldData;
import com.lanterna.lore.block.LoreBlockTypes;
import com.lanterna.lore.block.RitualBlocks;
import com.lanterna.lore.entity.LoreCreatures;
import com.lanterna.lore.entity.Race;
import com.lanterna.lore.entity.RaceEntity;
import com.lanterna.mission.LoreCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.core.animation.RawAnimation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Os nove bosses da lore, cada um com a mecânica descrita pelo autor. */
public final class Bosses {

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), size);
    }

    // =====================================================================================
    /** Vorgath, o Coração Fervente (Ysmault): avanços, pisões que abrem fendas de fogo; na fase 2 o coração dispara. */
    public static class Vorgath extends LoreBoss {
        private int charging;
        private Vec3 chargeDir = Vec3.ZERO;
        private final Map<UUID, Integer> chargeHit = new HashMap<>();

        public Vorgath(EntityType<? extends Monster> type, Level level) {
            super(type, level, BossEvent.BossBarColor.RED);
        }

        @Override
        protected String[] extraAnims() {
            return new String[]{"stomp"};
        }

        @Override
        protected boolean usesPhase2Loop() {
            return true;
        }

        @Override
        protected void bossTick(LivingEntity target) {
            if (charging > 0) {
                charging--;
                setDeltaMovement(chargeDir.x, getDeltaMovement().y, chargeDir.z);
                for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(1.5))) {
                    if (e == this || chargeHit.containsKey(e.getUUID())) continue;
                    chargeHit.put(e.getUUID(), 1);
                    e.hurt(damageSources().mobAttack(this), 14);
                    e.push(chargeDir.x * 1.5, 0.6, chargeDir.z * 1.5);
                    e.hurtMarked = true;
                }
                if (charging % 3 == 0) ((ServerLevel) level()).sendParticles(ParticleTypes.LAVA, getX(), getY() + 0.5, getZ(), 3, 1, 0.2, 1, 0);
                return;
            }
            chase(target, phase() >= 2 ? 1.35 : 1.0);
            tryMelee(target, 1.5, 30);
            if (specialCooldown == 0) {
                if (distanceTo(target) < 10) {
                    stomp();
                    specialCooldown = phase() >= 2 ? 90 : 130;
                } else {
                    Vec3 d = target.position().subtract(position());
                    chargeDir = new Vec3(d.x, 0, d.z).normalize().scale(phase() >= 2 ? 1.0 : 0.8);
                    charging = 22;
                    chargeHit.clear();
                    specialCooldown = 150;
                    level().playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2f, 0.6f);
                }
            }
            // Fase 2: cada batida do coração solta uma onda de choque
            if (phase() >= 2 && tickCount % 40 == 0) {
                areaDamage(position(), 10, 6, 1.3);
                ((ServerLevel) level()).sendParticles(ParticleTypes.FLAME, getX(), getY() + 1, getZ(), 60, 4, 0.3, 4, 0.05);
                level().playSound(null, blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 3f, 0.6f);
            }
        }

        private void stomp() {
            triggerAnim("attack", "stomp");
            areaDamage(position(), 6, 12, 1.0);
            level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5f, 0.6f);
            ServerLevel sl = (ServerLevel) level();
            // Fendas de lava: linhas de fogo saindo do pisão
            for (int line = 0; line < 4; line++) {
                double a = line * Math.PI / 2 + random.nextDouble() * 0.6;
                for (int i = 3; i < 11; i++) {
                    BlockPos p = BlockPos.containing(getX() + Math.cos(a) * i, getY(), getZ() + Math.sin(a) * i);
                    BlockPos top = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
                    if (Math.abs(top.getY() - getY()) < 4 && sl.getBlockState(top).isAir()) {
                        sl.setBlockAndUpdate(top, Blocks.FIRE.defaultBlockState());
                    }
                    sl.sendParticles(ParticleTypes.LAVA, top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0);
                }
            }
        }

        /** Na fase 2 o coração fica exposto: golpes de frente dão dano dobrado. */
        @Override
        protected float damageTaken(DamageSource source, float amount) {
            if (phase() >= 2 && source.getEntity() != null) {
                Vec3 to = source.getEntity().position().subtract(position()).normalize();
                if (getLookAngle().dot(to) > 0.4) return amount * 2;
            }
            return amount;
        }
    }

    // =====================================================================================
    /** Mammok, o Que Engole (Okaara): suga itens do chão (cada um vira armadura); na fase 2 cospe tesouro com explosões. */
    public static class Mammok extends LoreBoss {
        private final List<ItemStack> swallowed = new ArrayList<>();
        private final List<long[]> blasts = new ArrayList<>();
        private int pulling;

        public Mammok(EntityType<? extends Monster> type, Level level) {
            super(type, level, BossEvent.BossBarColor.YELLOW);
        }

        @Override
        protected String[] extraAnims() {
            return new String[]{"bite", "spit"};
        }

        @Override
        protected void registerGoals() {
            super.registerGoals();
        }

        private Vec3 mouth() {
            return position().add(getLookAngle().scale(3)).add(0, getBbHeight() * 0.8, 0);
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (level().isClientSide) return;
            setDeltaMovement(0, getDeltaMovement().y, 0);
            // Suga os itens do chão
            Vec3 mouth = mouth();
            for (ItemEntity item : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(20))) {
                Vec3 d = mouth.subtract(item.position());
                if (d.length() < 2.5) {
                    swallowed.add(item.getItem().copy());
                    item.discard();
                    setState(Math.min(20, state() + 1));
                    level().playSound(null, blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.HOSTILE, 2f, 0.5f);
                    for (Player p : playersNear(40)) {
                        p.displayClientMessage(Component.translatable("boss.lanterna.mammok.swallow", state()), true);
                    }
                } else {
                    item.setDeltaMovement(d.normalize().scale(0.45));
                }
            }
            // Explosões atrasadas do cuspe
            long now = level().getGameTime();
            blasts.removeIf(b -> {
                if (now < b[0]) return false;
                level().explode(this, b[1] + 0.5, b[2], b[3] + 0.5, 2.2f, Level.ExplosionInteraction.NONE);
                return true;
            });
        }

        @Override
        protected void bossTick(LivingEntity target) {
            Vec3 d = target.position().subtract(position());
            setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
            setYBodyRot(getYRot());
            setYHeadRot(getYRot());
            if (meleeCooldown == 0 && distanceTo(target) < 9) {
                meleeCooldown = 35;
                triggerAnim("attack", "bite");
                target.hurt(damageSources().mobAttack(this), 16);
                level().playSound(null, blockPosition(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 2f, 0.5f);
            }
            if (pulling > 0) {
                pulling--;
                for (Player p : playersNear(18)) {
                    Vec3 v = mouth().subtract(p.position()).normalize().scale(0.25);
                    p.push(v.x, 0.02, v.z);
                    p.hurtMarked = true;
                }
            }
            if (specialCooldown == 0) {
                if (phase() >= 2) {
                    spit(target);
                    specialCooldown = 80;
                } else {
                    pulling = 40;
                    specialCooldown = 140;
                    level().playSound(null, blockPosition(), SoundEvents.WARDEN_SNIFF, SoundSource.HOSTILE, 3f, 0.4f);
                }
            }
        }

        /** Cospe tesouro misturado com explosões. */
        private void spit(LivingEntity target) {
            triggerAnim("attack", "spit");
            level().playSound(null, blockPosition(), SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 3f, 0.4f);
            for (int i = 0; i < 3; i++) {
                BlockPos p = target.blockPosition().offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3);
                blasts.add(new long[]{level().getGameTime() + 20 + i * 8, p.getX(), p.getY(), p.getZ()});
                ((ServerLevel) level()).sendParticles(ParticleTypes.FALLING_LAVA, p.getX() + 0.5, p.getY() + 4, p.getZ() + 0.5, 8, 0.3, 1, 0.3, 0);
            }
            ItemStack treasure = random.nextBoolean() ? new ItemStack(Items.GOLD_NUGGET, 3 + random.nextInt(5)) : new ItemStack(Items.EMERALD);
            ItemEntity item = new ItemEntity(level(), mouth().x, mouth().y, mouth().z, treasure);
            item.setDeltaMovement(target.position().subtract(mouth()).normalize().scale(0.6).add(0, 0.4, 0));
            level().addFreshEntity(item);
        }

        @Override
        protected float damageTaken(DamageSource source, float amount) {
            return amount * (1f - 0.03f * state());
        }

        @Override
        protected List<ItemStack> extraLoot() {
            return swallowed;
        }

        @Override
        public void addAdditionalSaveData(CompoundTag tag) {
            super.addAdditionalSaveData(tag);
            ListTag list = new ListTag();
            for (ItemStack s : swallowed) list.add(s.save(new CompoundTag()));
            tag.put("Swallowed", list);
        }

        @Override
        public void readAdditionalSaveData(CompoundTag tag) {
            super.readAdditionalSaveData(tag);
            swallowed.clear();
            ListTag list = tag.getList("Swallowed", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) swallowed.add(ItemStack.of(list.getCompound(i)));
        }
    }

    // =====================================================================================
    /** Thessara, a Rainha das Mil Sombras (Korugar): ilusões dela mesma; só a verdadeira não tem sombra. Fase 2: escuridão e voo. */
    public static class Thessara extends LoreBoss {
        public static final int ILLUSION = 1;
        private static final RawAnimation FLY = RawAnimation.begin().thenLoop("move.fly");

        public Thessara(EntityType<? extends Monster> type, Level level) {
            super(type, level, BossEvent.BossBarColor.YELLOW);
        }

        public boolean isIllusion() {
            return (state() & ILLUSION) != 0;
        }

        @Override
        protected boolean usesPhase2Loop() {
            return true;
        }

        @Override
        protected RawAnimation moveAnim(boolean moving) {
            if (phase() >= 2 || isNoGravity()) return FLY;
            return super.moveAnim(moving);
        }

        @Override
        protected void bossTick(LivingEntity target) {
            if (isIllusion()) {
                chase(target, 1.0);
                if (tickCount > 400) poof();
                return;
            }
            if (phase() >= 2) {
                setNoGravity(true);
                Vec3 goal = target.position().add(0, 2.5, 0);
                Vec3 d = goal.subtract(position());
                if (d.length() > 3) setDeltaMovement(d.normalize().scale(0.35));
                else setDeltaMovement(getDeltaMovement().scale(0.6));
                if (tickCount % 60 == 0) {
                    for (Player p : playersNear(40)) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false));
                }
            } else {
                chase(target, 1.1);
            }
            if (tryMelee(target, 2.0, 25)) target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
            if (specialCooldown == 0) {
                illusions(target);
                specialCooldown = phase() >= 2 ? 160 : 220;
            }
        }

        /** Três ilusões em volta; a verdadeira troca de lugar com uma delas. */
        private void illusions(LivingEntity target) {
            ServerLevel sl = (ServerLevel) level();
            List<Vec3> spots = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + random.nextDouble();
                spots.add(target.position().add(Math.cos(a) * 8, phase() >= 2 ? 3 : 0, Math.sin(a) * 8));
            }
            java.util.Collections.shuffle(spots, new java.util.Random(random.nextLong()));
            for (int i = 0; i < 3; i++) {
                Thessara copy = (Thessara) getType().create(sl);
                if (copy == null) continue;
                copy.setState(ILLUSION);
                copy.bar.setVisible(false);
                copy.moveTo(spots.get(i).x, spots.get(i).y, spots.get(i).z, random.nextFloat() * 360, 0);
                copy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1);
                copy.setHealth(1);
                copy.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0);
                copy.setPhase(phase());
                copy.setNoGravity(phase() >= 2);
                copy.setTarget(target);
                copy.setCustomName(getDisplayName());
                sl.addFreshEntity(copy);
            }
            Vec3 me = spots.get(3);
            teleportTo(me.x, me.y, me.z);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 40, 1, 1, 1, 0.02);
            level().playSound(null, blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 2f, 0.8f);
        }

        private void poof() {
            ((ServerLevel) level()).sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 20, 0.6, 1, 0.6, 0.02);
            discard();
        }

        @Override
        protected float damageTaken(DamageSource source, float amount) {
            if (isIllusion()) {
                poof();
                return 0;
            }
            return amount;
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            if (isIllusion()) return false;
            return super.doHurtTarget(target);
        }

        @Override
        protected boolean countsAsDefeat() {
            return !isIllusion();
        }

        @Override
        protected void onDefeated() {
            for (Thessara t : level().getEntitiesOfClass(Thessara.class, getBoundingBox().inflate(64))) {
                if (t.isIllusion()) t.poof();
            }
        }
    }

    // =====================================================================================
    /** Vigia Partido (Oa): copia os construtos do jogador; na fase 2 se divide em três vigias menores. */
    public static class Vigia extends LoreBoss {
        public static final int SMALL = 1;
        private static final UUID SMALL_HP = UUID.fromString("7d8c2b10-4a5e-4f3c-8b1d-9e0a1c2b3d4e");
        private long shieldUntil;

        public Vigia(EntityType<? extends Monster> type, Level level) {
            super(type, level, BossEvent.BossBarColor.GREEN);
        }

        public boolean isSmall() {
            return (state() & SMALL) != 0;
        }

        @Override
        protected String[] extraAnims() {
            return new String[]{"split"};
        }

        @Override
        public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
            return super.getDimensions(pose).scale(isSmall() ? 0.75f : 1f);
        }

        @Override
        protected float phase2At() {
            return isSmall() ? 0f : 0.5f;
        }

        @Override
        protected void onPhaseChange(int phase) {
            super.onPhaseChange(phase);
            if (isSmall() || phase != 2) return;
            triggerAnim("attack", "split");
            ServerLevel sl = (ServerLevel) level();
            for (int i = 0; i < 3; i++) {
                Vigia part = (Vigia) getType().create(sl);
                if (part == null) continue;
                double a = i * Math.PI * 2 / 3;
                part.moveTo(getX() + Math.cos(a) * 3, getY(), getZ() + Math.sin(a) * 3, getYRot(), 0);
                part.setState(SMALL);
                part.setPhase(2);
                AttributeInstance hp = part.getAttribute(Attributes.MAX_HEALTH);
                if (hp != null) hp.addPermanentModifier(new AttributeModifier(SMALL_HP, "fragmento", -0.7, AttributeModifier.Operation.MULTIPLY_TOTAL));
                part.setHealth(part.getMaxHealth());
                part.setHome(home != null ? home : blockPosition());
                part.fighters.addAll(fighters);
                part.setTarget(getTarget());
                part.setCustomName(Component.translatable("entity.lanterna.vigia_fragmento"));
                part.refreshDimensions();
                sl.addFreshEntity(part);
            }
            sl.sendParticles(dust(0x40FF70, 2f), getX(), getY() + 2, getZ(), 120, 1.5, 2, 1.5, 0.1);
            level().playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2f, 0.5f);
            discard();
        }

        @Override
        protected void bossTick(LivingEntity target) {
            chase(target, isSmall() ? 1.3 : 1.0);
            tryMelee(target, 1.5, 28);
            if (specialCooldown == 0) {
                copyConstruct(target);
                specialCooldown = isSmall() ? 110 : 80;
            }
        }

        /** Usa contra o jogador o último construto que ele usou. */
        private void copyConstruct(LivingEntity target) {
            ConstructType last = target instanceof Player p ? Constructs.lastUsed(p.getUUID()) : null;
            int color = BuiltinCorps.GREEN.color();
            float scale = isSmall() ? 0.8f : 1.2f;
            if (target instanceof Player p) p.displayClientMessage(Component.translatable("boss.lanterna.vigia_partido.copy",
                    last != null ? last.displayName() : ConstructType.LASER.displayName()), true);
            switch (last == null ? ConstructType.LASER : last) {
                case MARTELO, LAMINA -> ConstructEntity.spawnHammer(this, target.position(), color, 14, scale);
                case PUNHO, RAJADA, MAO -> {
                    Vec3 dir = target.getEyePosition().subtract(getEyePosition());
                    ConstructEntity.spawnProjectile(this, ConstructEntity.Kind.FIST, dir, 1.3, color, 10, scale);
                }
                case PAREDE, ESCUDO -> {
                    shieldUntil = level().getGameTime() + 100;
                    ConstructEntity shield = ConstructEntity.create(this, ConstructEntity.Kind.SHIELD, color, 100, 0, getBbHeight() / 2.2f);
                    level().addFreshEntity(shield);
                }
                case JAULA -> {
                    BlockPos c = target.blockPosition();
                    for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y <= 2; y++) {
                        boolean inside = x == 0 && z == 0 && y < 2;
                        if (!inside) ConstructBlock.place((ServerLevel) level(), c.offset(x, y, z), color, 60);
                    }
                }
                default -> ConstructEntity.spawnBeam(this, color, 3f, 40);
            }
        }

        @Override
        protected float damageTaken(DamageSource source, float amount) {
            return level().getGameTime() < shieldUntil ? amount * 0.3f : amount;
        }

        @Override
        protected boolean countsAsDefeat() {
            if (!isSmall()) return phase() == 1;
            for (Vigia v : level().getEntitiesOfClass(Vigia.class, getBoundingBox().inflate(80))) {
                if (v != this && v.isAlive() && v.isSmall()) return false;
            }
            return true;
        }

        @Override
        protected void onDefeated() {
            if (!countsAsDefeat()) return;
            for (Player p : playersNear(64)) {
                LoreCapability.get(p).ifPresent(d -> d.addRep("veltari", 3));
                p.sendSystemMessage(Component.translatable("boss.lanterna.vigia_partido.recognized")
                        .withStyle(s -> s.withColor(BuiltinCorps.GREEN.color()).withItalic(true)));
            }
        }
    }

    // =====================================================================================
    /** Morrow, o Que Desistiu (Odym): Desânimo crescente; na fase 2 apaga os faróis e só toma dano com os três acesos. */
    public static class Morrow extends LoreBoss {
        private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("move.swim");
        private final Map<UUID, Integer> gloom = new HashMap<>();
        private final List<BlockPos> beacons = new ArrayList<>();
        private int warn;

        public Morrow(EntityType<? extends Monster> type, Level level) {
            super(type, level, BossEvent.BossBarColor.BLUE);
            setNoGravity(true);
        }

        @Override
        protected RawAnimation moveAnim(boolean moving) {
            return moving || getDeltaMovement().lengthSqr() > 0.002 ? SWIM : IDLE;
        }

        @Override
        protected void bossTick(LivingEntity target) {
            // Nada livremente (na água ou rente a ela), sem sair da arena
            Vec3 goal = target.position().add(0, 0.5, 0);
            if (home != null && goal.distanceTo(Vec3.atCenterOf(home)) > 22) goal = Vec3.atCenterOf(home).add(0, 1, 0);
            Vec3 d = goal.subtract(position());
            setDeltaMovement(d.length() > 4 ? d.normalize().scale(0.3) : getDeltaMovement().scale(0.7));
            setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
            setYBodyRot(getYRot());
            tryMelee(target, 2.5, 30);
            if (tickCount % 100 == 0) {
                for (Player p : playersNear(24)) {
                    int g = Math.min(3, gloom.merge(p.getUUID(), 1, Integer::sum));
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 140, g - 1));
                    p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 140, g - 1));
                    p.displayClientMessage(Component.translatable("boss.lanterna.morrow.gloom", g), true);
                }
            }
            if (tickCount % 4 == 0) {
                ((ServerLevel) level()).sendParticles(dust(0x707880, 1.6f), getX(), getY() + 0.5, getZ(), 6, 3, 0.4, 3, 0);
            }
        }

        @Override
        protected void onPhaseChange(int phase) {
            super.onPhaseChange(phase);
            BlockPos c = home != null ? home : blockPosition();
            beacons.clear();
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-26, -8, -26), c.offset(26, 8, 26))) {
                var s = level().getBlockState(p);
                if (s.is(LoreBlocks.FAROL_PEROLA.get())) {
                    beacons.add(p.immutable());
                    level().setBlockAndUpdate(p, s.setValue(RitualBlocks.FarolPerola.ACESO, false));
                }
            }
            level().playSound(null, blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 3f, 0.4f);
        }

        private boolean beaconsLit() {
            for (BlockPos p : beacons) {
                var s = level().getBlockState(p);
                if (s.is(LoreBlocks.FAROL_PEROLA.get()) && !s.getValue(RitualBlocks.FarolPerola.ACESO)) return false;
            }
            return true;
        }

        @Override
        protected float damageTaken(DamageSource source, float amount) {
            if (phase() >= 2 && !beaconsLit()) {
                if (source.getEntity() instanceof Player p && warn-- <= 0) {
                    warn = 40;
                    p.displayClientMessage(Component.translatable("boss.lanterna.morrow.beacons"), true);
                }
                return 0;
            }
            return amount;
        }

        @Override
        protected void onDefeated() {
            ServerLevel sl = (ServerLevel) level();
            sl.sendParticles(dust(0x40A0FF, 2.5f), getX(), getY() + 1, getZ(), 200, 3, 2, 3, 0.05);
            sl.sendParticles(ParticleTypes.GLOW, getX(), getY() + 1, getZ(), 80, 3, 2, 3, 0.05);
            for (Player p : playersNear(64)) {
                p.sendSystemMessage(Component.translatable("boss.lanterna.morrow.thanks").withStyle(s -> s.withColor(0x60B0FF).withItalic(true)));
            }
            for (BlockPos p : beacons) {
                var s = level().getBlockState(p);
                if (s.is(LoreBlocks.FAROL_PEROLA.get())) level().setBlockAndUpdate(p, s.setValue(RitualBlocks.FarolPerola.ACESO, true));
            }
        }

        @Override
        public void readAdditionalSaveData(CompoundTag tag) {
            super.readAdditionalSaveData(tag);
            setNoGravity(true);
        }
    }

    // =====================================================================================
    /** O Penitente Sem Rosto (Nok): golpes normais não ferem; curar os rostos (itens de cura nele) o enfraquece. */
    public static class Penitente extends LoreBoss {
        private static final RawAnimation WEEP = RawAnimation.begin().thenLoop("misc.weep");
        private int warn;

        public Penitente(EntityType<? extends Monster> type, Level level) {
            super(type, level, BossEvent.BossBarColor.PURPLE);
        }

        @Override
        protected RawAnimation moveAnim(boolean moving) {
            if (!moving && phase() == 1) return WEEP;
            return super.moveAnim(moving);
        }

        public int healedFaces() {
            return Integer.bitCount(state() & 0xFF);
        }

        @Override
        protected void bossTick(LivingEntity target) {
            chase(target, phase() >= 2 ? 1.1 : 0.7);
            if (phase() >= 2) tryMelee(target, 2.0, 25);
            else if (tryMelee(target, 1.5, 50)) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            // A culpa pesa em quem está perto
            if (tickCount % 80 == 0) {
                for (Player p : playersNear(10)) {
                    p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 1));
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                }
                ((ServerLevel) level()).sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR, getX(), getY() + 3, getZ(), 30, 1.5, 1, 1.5, 0);
            }
            // Fase 2: de tempos em tempos dois rostos voltam a chorar
            if (phase() >= 2 && specialCooldown == 0) {
                specialCooldown = 600;
                int s = state();
                for (int k = 0; k < 2; k++) {
                    int bit = random.nextInt(8);
                    s &= ~(1 << bit);
                }
                setState(s);
                for (Player p : playersNear(40)) p.displayClientMessage(Component.translatable("boss.lanterna.penitente.reopen"), true);
            }
        }

        @Override
        protected InteractionResult mobInteract(Player player, InteractionHand hand) {
            ItemStack held = player.getItemInHand(hand);
            if (!RaceEntity.isHealingItem(held)) return super.mobInteract(player, hand);
            if (level().isClientSide) return InteractionResult.SUCCESS;
            int s = state();
            List<Integer> open = new ArrayList<>();
            for (int i = 0; i < 8; i++) if ((s & (1 << i)) == 0) open.add(i);
            if (open.isEmpty()) return InteractionResult.PASS;
            int face = open.get(random.nextInt(open.size()));
            setState(s | (1 << face));
            if (!(held.getItem() instanceof com.lanterna.item.CompassionStaffItem) && !player.getAbilities().instabuild) held.shrink(1);
            fighters.add(player.getUUID());
            ((ServerLevel) level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 3, getZ(), 20, 1, 1, 1, 0);
            level().playSound(null, blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.HOSTILE, 2f, 0.6f);
            player.displayClientMessage(Component.translatable("boss.lanterna.penitente.healed", healedFaces()), true);
            return InteractionResult.CONSUME;
        }

        @Override
        protected float damageTaken(DamageSource source, float amount) {
            float mult = healedFaces() / 8f;
            if (phase() >= 2) mult = Math.max(0.25f, mult);
            if (mult <= 0 && source.getEntity() instanceof Player p && warn-- <= 0) {
                warn = 30;
                p.displayClientMessage(Component.translatable("boss.lanterna.penitente.immune"), true);
            }
            return amount * mult;
        }
    }

    // =====================================================================================
    /** Nyrielle, a Gema Possessiva (Zamaron): cristais em volta são amados presos (quebrar a enfraquece); cópias de cristal; prisão na fase 2. */
    public static class Nyrielle extends LoreBoss {
        private final List<long[]> cages = new ArrayList<>();
        private boolean placed;

        public Nyrielle(EntityType<? extends Monster> type, Level level) {
            super(type, level, BossEvent.BossBarColor.PINK);
        }

        @Override
        protected String[] extraAnims() {
            return new String[]{"imprison"};
        }

        private BlockPos crystal(int i) {
            BlockPos c = home != null ? home : blockPosition();
            double a = i * Math.PI / 3;
            BlockPos p = BlockPos.containing(c.getX() + Math.cos(a) * 12, c.getY(), c.getZ() + Math.sin(a) * 12);
            return level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (level().isClientSide) return;
            if (!placed) {
                placed = true;
                for (int i = 0; i < 6; i++) {
                    if ((state() & (1 << i)) == 0) {
                        level().setBlockAndUpdate(crystal(i), LoreBlocks.CRISTAL_DEVOTO_AGLOMERADO.get().defaultBlockState()
                                .setValue(LoreBlockTypes.CristalDevoto.ACESO, true));
                    }
                }
            }
            if (tickCount % 20 == 0) {
                for (int i = 0; i < 6; i++) {
                    if ((state() & (1 << i)) != 0) continue;
                    BlockPos p = crystal(i);
                    boolean present = level().getBlockState(p).is(LoreBlocks.CRISTAL_DEVOTO_AGLOMERADO.get())
                            || level().getBlockState(p.below()).is(LoreBlocks.CRISTAL_DEVOTO_AGLOMERADO.get());
                    if (!present) {
                        setState(state() | (1 << i));
                        hurt(damageSources().magic(), getMaxHealth() * 0.05f);
                        level().playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 2f, 0.5f);
                        for (Player pl : playersNear(48)) pl.displayClientMessage(Component.translatable("boss.lanterna.nyrielle.freed"), true);
                    }
                }
            }
            long now = level().getGameTime();
            cages.removeIf(c -> {
                if (now < c[0]) return false;
                BlockPos center = new BlockPos((int) c[1], (int) c[2], (int) c[3]);
                for (BlockPos p : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 3, 1))) {
                    if (level().getBlockState(p).is(Blocks.AMETHYST_BLOCK)) level().removeBlock(p, false);
                }
                return true;
            });
        }

        @Override
        protected void bossTick(LivingEntity target) {
            chase(target, 0.9);
            tryMelee(target, 2.0, 30);
            if (specialCooldown == 0) {
                if (phase() >= 2 && random.nextBoolean() && target instanceof ServerPlayer sp) imprison(sp);
                else copies(target);
                specialCooldown = phase() >= 2 ? 160 : 240;
            }
        }

        /** Cópias de cristal (Zaëlith possessos que somem depois de um tempo). */
        private void copies(LivingEntity target) {
            ServerLevel sl = (ServerLevel) level();
            for (int i = 0; i < 2; i++) {
                RaceEntity copy = LoreEntities.typeOf(Race.ZAELITH).create(sl);
                if (copy == null) continue;
                copy.moveTo(getX() + random.nextInt(5) - 2, getY(), getZ() + random.nextInt(5) - 2, 0, 0);
                copy.setFlag(RaceEntity.MOURNING, true);
                copy.setCustomName(Component.translatable("entity.lanterna.copia_cristal"));
                copy.setLifetime(600);
                copy.setTarget(target);
                sl.addFreshEntity(copy);
            }
            sl.sendParticles(dust(0xF080FF, 1.8f), getX(), getY() + 2, getZ(), 40, 1.5, 1, 1.5, 0);
            level().playSound(null, blockPosition(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.HOSTILE, 2f, 0.6f);
        }

        /** Prisão de cristal: o jogador quebra batendo rápido nos blocos. */
        private void imprison(ServerPlayer p) {
            triggerAnim("attack", "imprison");
            BlockPos c = p.blockPosition();
            for (BlockPos q : BlockPos.betweenClosed(c.offset(-1, 0, -1), c.offset(1, 3, 1))) {
                boolean inside = q.getX() == c.getX() && q.getZ() == c.getZ() && q.getY() < c.getY() + 2;
                if (!inside && level().getBlockState(q).isAir()) level().setBlockAndUpdate(q, Blocks.AMETHYST_BLOCK.defaultBlockState());
            }
            cages.add(new long[]{level().getGameTime() + 160, c.getX(), c.getY(), c.getZ()});
            p.displayClientMessage(Component.translatable("boss.lanterna.nyrielle.prison").withStyle(s -> s.withColor(0xF080FF).withBold(true)), true);
            level().playSound(null, c, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.HOSTILE, 2f, 0.4f);
        }

        @Override
        protected float damageTaken(DamageSource source, float amount) {
            if (source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC) && source.getEntity() == null) return amount;
            return amount * (0.4f + 0.1f * Integer.bitCount(state() & 0x3F));
        }

        @Override
        public void readAdditionalSaveData(CompoundTag tag) {
            super.readAdditionalSaveData(tag);
            placed = true;
        }
    }

    // =====================================================================================
    /** Devorador de Ecos (Ryut): revive como ecos os bosses já derrotados; na fase 2 drena a vida (menos perto da luz das baterias). */
    public static class Devorador extends LoreBoss {
        public static final String[] HEADS = {"vorgath", "mammok", "thessara", "vigia_partido", "morrow", "penitente", "nyrielle"};
        private static final RawAnimation DRAIN = RawAnimation.begin().thenLoop("misc.drain");
        private final List<UUID> echoes = new ArrayList<>();

        public Devorador(EntityType<? extends Monster> type, Level level) {
            super(type, level, BossEvent.BossBarColor.PURPLE);
        }

        @Override
        protected RawAnimation moveAnim(boolean moving) {
            if (phase() >= 2 && !moving) return DRAIN;
            return super.moveAnim(moving);
        }

        @Override
        protected void bossTick(LivingEntity target) {
            if (tickCount % 100 == 1) {
                int mask = 0;
                var defeated = LoreWorldData.get(((ServerLevel) level()).getServer()).bosses();
                for (int i = 0; i < HEADS.length; i++) if (defeated.contains(HEADS[i])) mask |= 1 << i;
                setState(mask);
            }
            chase(target, 0.9);
            tryMelee(target, 2.5, 30);
            ServerLevel sl = (ServerLevel) level();
            echoes.removeIf(id -> !(sl.getEntity(id) instanceof LivingEntity le) || !le.isAlive());
            if (specialCooldown == 0 && echoes.size() < 2) {
                specialCooldown = 300;
                List<String> pool = new ArrayList<>();
                for (int i = 0; i < HEADS.length; i++) if ((state() & (1 << i)) != 0) pool.add(HEADS[i]);
                Vec3 at = position().add(random.nextInt(11) - 5, 0, random.nextInt(11) - 5);
                if (pool.isEmpty()) {
                    for (int i = 0; i < 3; i++) {
                        var eco = LoreEntities.ECO_CINZENTO.get().create(sl);
                        if (eco == null) continue;
                        eco.moveTo(at.x + i, at.y, at.z, 0, 0);
                        eco.setTarget(target);
                        sl.addFreshEntity(eco);
                    }
                } else {
                    LoreBoss echo = LoreBoss.spawnEcho(sl, pool.get(random.nextInt(pool.size())), at, target);
                    if (echo != null) echoes.add(echo.getUUID());
                }
                level().playSound(null, blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 3f, 0.5f);
            }
            if (phase() >= 2 && tickCount % 20 == 0) {
                float healed = 0;
                for (Player p : playersNear(32)) {
                    float drain = nearLight(p) ? 0.75f : 2f;
                    p.hurt(damageSources().magic(), drain);
                    healed += drain;
                    sl.sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 1, p.getZ(), 3, 0.3, 0.4, 0.3, 0.02);
                }
                if (healed > 0) heal(healed);
            }
        }

        /** Luz das baterias: bateria perto (no chão) ou no slot com o anel ligado. */
        private boolean nearLight(Player p) {
            BlockPos c = p.blockPosition();
            for (BlockPos q : BlockPos.betweenClosed(c.offset(-6, -3, -6), c.offset(6, 3, 6))) {
                if (level().getBlockState(q).getBlock() instanceof com.lanterna.block.PowerBatteryBlock) return true;
            }
            RingData ring = ModCapabilities.get(p).orElse(null);
            return ring != null && ring.hasRing() && ring.powerActive()
                    && com.lanterna.item.BatteryItems.isBattery(com.lanterna.power.EquipmentHelper.battery(p));
        }
    }

    // =====================================================================================
    /** O Nulo (Gênese): só o Anel Branco o fere. Fase 1 apaga as cores e chama Ecos; fase 2 copia bosses; fase 3 fecha a rachadura. */
    public static class Nulo extends LoreBoss {
        private UUID copy;
        private int closeTimer = 1200;
        private int warn;
        private int nextCopy;

        public Nulo(EntityType<? extends Monster> type, Level level) {
            super(type, level, BossEvent.BossBarColor.WHITE);
        }

        @Override
        protected float phase2At() {
            return 0.66f;
        }

        @Override
        protected boolean hasPhase3() {
            return true;
        }

        @Override
        protected void bossTick(LivingEntity target) {
            ServerLevel sl = (ServerLevel) level();
            chase(target, 1.0);
            tryMelee(target, 1.5, 22);
            if (tickCount % 40 == 0) {
                for (Player p : playersNear(40)) p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false));
                sl.sendParticles(dust(0x8C8C90, 2f), getX(), getY() + 1, getZ(), 50, 6, 1, 6, 0);
            }
            switch (phase()) {
                case 1 -> {
                    if (specialCooldown == 0) {
                        specialCooldown = 160;
                        int near = level().getEntitiesOfClass(LoreCreatures.Eco.class, getBoundingBox().inflate(40)).size();
                        for (int i = 0; i < 2 && near + i < 6; i++) {
                            var eco = LoreEntities.ECO_CINZENTO.get().create(sl);
                            if (eco == null) continue;
                            eco.moveTo(getX() + random.nextInt(9) - 4, getY(), getZ() + random.nextInt(9) - 4, 0, 0);
                            eco.setTarget(target);
                            sl.addFreshEntity(eco);
                        }
                        colorless(target);
                    }
                }
                case 2 -> {
                    boolean alive = copy != null && sl.getEntity(copy) instanceof LivingEntity le && le.isAlive();
                    if (!alive && specialCooldown == 0) {
                        String boss = Devorador.HEADS[nextCopy++ % Devorador.HEADS.length];
                        LoreBoss echo = LoreBoss.spawnEcho(sl, boss, position().add(4, 0, 4), target);
                        copy = echo != null ? echo.getUUID() : null;
                        specialCooldown = 100;
                        for (Player p : playersNear(48)) p.displayClientMessage(Component.translatable("boss.lanterna.nulo.copy"), true);
                    }
                }
                default -> {
                    // A rachadura tenta se fechar com você dentro
                    Vec3 rift = home != null ? Vec3.atCenterOf(home) : position();
                    for (Player p : playersNear(40)) {
                        Vec3 v = rift.subtract(p.position());
                        p.push(v.x * 0.012, 0, v.z * 0.012);
                        p.hurtMarked = true;
                        if (v.length() < 3 && tickCount % 20 == 0) p.hurt(damageSources().magic(), 4);
                    }
                    if (--closeTimer <= 0) {
                        closeTimer = 1200;
                        for (Player p : playersNear(48)) {
                            p.hurt(damageSources().magic(), 16);
                            p.displayClientMessage(Component.translatable("boss.lanterna.nulo.close"), true);
                        }
                        heal(getMaxHealth() * 0.15f);
                        sl.sendParticles(ParticleTypes.SONIC_BOOM, rift.x, rift.y + 1, rift.z, 1, 0, 0, 0, 0);
                        level().playSound(null, BlockPos.containing(rift), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3f, 0.5f);
                    }
                    if (specialCooldown == 0) {
                        specialCooldown = 120;
                        colorless(target);
                    }
                }
            }
        }

        /** Os poderes de todas as cores, mas sem cor: fúria sem calor, medo sem som, amor sem toque. */
        private void colorless(LivingEntity target) {
            switch (random.nextInt(3)) {
                case 0 -> {
                    areaDamage(position(), 8, 10, 1.6);
                    level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5f, 0.4f);
                }
                case 1 -> {
                    for (Player p : playersNear(30)) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
                }
                default -> {
                    Vec3 v = position().subtract(target.position()).normalize().scale(1.4);
                    target.push(v.x, 0.3, v.z);
                    target.hurtMarked = true;
                }
            }
        }

        @Override
        protected float damageTaken(DamageSource source, float amount) {
            boolean white = source.getEntity() instanceof Player p && ModCapabilities.get(p)
                    .map(r -> r.hasRing() && r.powerActive() && r.corps().equals(BuiltinCorps.WHITE.id())).orElse(false);
            boolean copyAlive = copy != null && level() instanceof ServerLevel sl && sl.getEntity(copy) instanceof LivingEntity le && le.isAlive();
            if (!white || copyAlive) {
                if (source.getEntity() instanceof Player p && warn-- <= 0) {
                    warn = 30;
                    p.displayClientMessage(Component.translatable(copyAlive ? "boss.lanterna.nulo.shielded" : "boss.lanterna.nulo.immune"), true);
                }
                return 0;
            }
            return amount;
        }

        @Override
        protected void onDefeated() {
            ServerLevel sl = (ServerLevel) level();
            sl.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1, getZ(), 3, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 300, 6, 4, 6, 0.2);
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + 1, getZ(), 200, 4, 3, 4, 0.5);
            for (ServerPlayer p : sl.getServer().getPlayerList().getPlayers()) {
                p.sendSystemMessage(Component.translatable("boss.lanterna.nulo.defeated").withStyle(s -> s.withColor(0xFFFFFF).withBold(true)));
            }
            for (int i = 0; i < 5; i++) {
                var broto = LoreEntities.BROTO.get().create(sl);
                if (broto == null) continue;
                broto.moveTo(getX() + random.nextInt(7) - 3, getY(), getZ() + random.nextInt(7) - 3, 0, 0);
                sl.addFreshEntity(broto);
            }
        }

        @Override
        public void addAdditionalSaveData(CompoundTag tag) {
            super.addAdditionalSaveData(tag);
            tag.putInt("CloseTimer", closeTimer);
            tag.putInt("NextCopy", nextCopy);
        }

        @Override
        public void readAdditionalSaveData(CompoundTag tag) {
            super.readAdditionalSaveData(tag);
            closeTimer = tag.contains("CloseTimer") ? tag.getInt("CloseTimer") : 1200;
            nextCopy = tag.getInt("NextCopy");
        }
    }

    /** Usado pelo altar: cria o boss do planeta e o põe na arena. */
    public static LoreBoss summon(ServerLevel level, EntityType<?> type, Vec3 pos, BlockPos home) {
        Entity e = type.create(level);
        if (!(e instanceof LoreBoss boss)) return null;
        boss.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360, 0);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(pos)), MobSpawnType.MOB_SUMMONED, null, null);
        boss.setHome(home);
        level.addFreshEntity(boss);
        return boss;
    }

    private Bosses() {}
}
