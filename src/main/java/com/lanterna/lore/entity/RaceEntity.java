package com.lanterna.lore.entity;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.lore.LoreEntities;
import com.lanterna.lore.LoreItems;
import com.lanterna.mission.LoreCapability;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Morador de um planeta (uma das raças da lore) ou Guardião do Universo.
 * Cada raça trata o jogador de acordo com a sua emoção (veja a lore):
 *
 *  Brakkari    atacam estranhos; quem vence um em duelo (deixa com pouca vida) ganha o respeito deles
 *  Ghallos     não atacam: roubam um item e fogem; matar um ladrão devolve o item, mas a vila fica hostil
 *  Skrell'Vath perseguem de longe e só atacam pelas costas; encarar um por 3 s faz ele recuar (respeito)
 *  Veltari     neutros; com o anel verde, fazem reverência e treinam o jogador
 *  Aelínos     curam quem está com pouca vida; fogem de qualquer luta
 *  Ves'Kai     pacíficos; ficam hostis se você ferir um inocente perto deles; curar um ferido dá Gratidão
 *  Zaëlith     vivem em pares; presentes favoritos aumentam a afeição; se um morre, o outro odeia para sempre
 *  Silentes    ignoram quem está forte; atacam em bando quem está com pouca vida ou fome
 *  O Coletor   troca almas
 *  Guardiões   anciões de Oa: flutuam, não lutam, contam a história e dão as missões
 */
public class RaceEntity extends AbstractVillager implements GeoEntity {
    private static final EntityDataAccessor<String> ROLE = SynchedEntityData.defineId(RaceEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> FLAGS = SynchedEntityData.defineId(RaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(RaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PAIR = SynchedEntityData.defineId(RaceEntity.class, EntityDataSerializers.INT);

    public static final int KNEEL = 1, WOUNDED = 2, LEADER_MASK = 4, MOURNING = 8, BOW = 16, FLEE = 32;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("misc.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("move.walk");
    private static final RawAnimation STRIKE = RawAnimation.begin().thenPlay("attack.strike");

    /** Itens favoritos possíveis de um Zaëlith (cada um tem o seu). */
    private static final List<Item> FAVORITES = List.of(Items.POPPY, Items.DIAMOND, Items.AMETHYST_SHARD, Items.PINK_PETALS,
            Items.GOLDEN_APPLE, Items.EMERALD, Items.LILY_OF_THE_VALLEY, Items.MUSIC_DISC_CAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Race race;

    // ---- Estado só do servidor ----
    private final Map<UUID, Long> angryUntil = new HashMap<>();
    private final Set<UUID> beatenBy = new HashSet<>();
    private final Set<UUID> sparedBy = new HashSet<>();
    private final Map<UUID, Integer> affection = new HashMap<>();
    private final Map<UUID, Integer> stare = new HashMap<>();
    private final Map<UUID, Long> bowed = new HashMap<>();
    private ItemStack stolen = ItemStack.EMPTY;
    @Nullable private UUID stolenFrom;
    @Nullable private UUID partner;
    private long timerUntil;
    private long nextSteal;
    private int attackCooldown;
    @Nullable private BlockPos home;
    private final List<MerchantOffer> discounted = new ArrayList<>();
    private int lifetime = -1;

    /** Some depois de um tempo (cópias de cristal da Nyrielle). */
    public void setLifetime(int ticks) {
        lifetime = ticks;
    }

    public RaceEntity(EntityType<? extends AbstractVillager> type, Level level) {
        super(type, level);
        if (race() == Race.GUARDIAO) setNoGravity(true);
    }

    public Race race() {
        if (race == null) race = LoreEntities.raceOf(getType());
        return race == null ? Race.VELTARI : race;
    }

    public static AttributeSupplier.Builder attributes(Race race) {
        return AbstractVillager.createMobAttributes()
                .add(Attributes.MAX_HEALTH, race.health)
                .add(Attributes.MOVEMENT_SPEED, race.speed)
                .add(Attributes.ATTACK_DAMAGE, Math.max(1, race.damage))
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, race == Race.BRAKKARI ? 0.6 : race == Race.GUARDIAO ? 1.0 : 0.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ROLE, "");
        entityData.define(FLAGS, 0);
        entityData.define(VARIANT, 0);
        entityData.define(PAIR, -1);
    }

    // ===================== IA =====================

    @Override
    protected void registerGoals() {
        Race r = race();
        goalSelector.addGoal(0, new FloatGoal(this));
        if (r == Race.GUARDIAO) {
            goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 12f, 1f));
            return;
        }
        goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
        switch (r) {
            case AELINOS -> {
                goalSelector.addGoal(1, new PanicGoal(this, 1.6));
                goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Monster.class, 10f, 1.2, 1.5));
            }
            case GHALLOS -> goalSelector.addGoal(2, new StealGoal());
            case SKRELL_VATH -> goalSelector.addGoal(2, new StalkGoal());
            case ZAELITH -> goalSelector.addGoal(5, new FollowPartnerGoal());
            default -> {}
        }
        if (r != Race.AELINOS && r != Race.SKRELL_VATH && r.damage > 0) {
            goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.25, true) {
                @Override
                public boolean canUse() {
                    return !hasFlag(KNEEL) && super.canUse();
                }
            });
        }
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, r == Race.VES_KAI ? 0.9 : 0.7) {
            @Override
            public boolean canUse() {
                return !hasFlag(KNEEL) && super.canUse();
            }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        if (r != Race.AELINOS) {
            targetSelector.addGoal(1, new HurtByTargetGoal(this, RaceEntity.class) {
                @Override
                public boolean canUse() {
                    return !hasFlag(KNEEL) && role().isEmpty() && super.canUse();
                }
            }.setAlertOthers(RaceEntity.class));
            targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                    e -> e instanceof Player p && hostileTo(p)));
        }
    }

    /** Este morador quer brigar com o jogador? */
    public boolean hostileTo(Player p) {
        if (p.isCreative() || p.isSpectator() || level().isClientSide) return false;
        if (!role().isEmpty() || race() == Race.GUARDIAO) return false;
        Long until = angryUntil.get(p.getUUID());
        if (until != null && until > level().getGameTime()) return true;
        // Lanterna de uma Tropa inimiga: o povo ataca até ganhar o respeito dele.
        // Lanterna da própria Tropa do planeta: é um dos nossos, ninguém ataca.
        if (enemyCorps(p)) return true;
        var mine = com.lanterna.lore.Factions.playerCorps(p);
        if (mine != null && mine == com.lanterna.lore.Factions.raceCorps(race())) return false;
        return switch (race()) {
            case BRAKKARI -> !hasFlag(KNEEL) && rep(p, "brakkari") < 1;
            case SKRELL_VATH -> !hasFlag(FLEE) && !sparedBy.contains(p.getUUID()) && rep(p, "skrell_vath") < 3;
            case SILENTE -> p.getHealth() < p.getMaxHealth() * 0.4f || p.getFoodData().getFoodLevel() < 6;
            case ZAELITH -> hasFlag(MOURNING);
            default -> false;
        };
    }

    public static int rep(Player p, String key) {
        return LoreCapability.get(p).map(d -> d.rep(key)).orElse(0);
    }

    public void angerAt(Player p, int ticks) {
        angryUntil.put(p.getUUID(), level().getGameTime() + ticks);
        if (getTarget() == null && role().isEmpty()) setTarget(p);
    }

    // ===================== Tick =====================

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        long now = level().getGameTime();
        if (timerUntil > 0 && now > timerUntil) {
            timerUntil = 0;
            setFlag(KNEEL, false);
            setFlag(FLEE, false);
            setFlag(BOW, false);
        }
        if (hasFlag(KNEEL)) {
            getNavigation().stop();
            setTarget(null);
        }
        switch (race()) {
            case GUARDIAO -> hover();
            case AELINOS -> {
                if (tickCount % 100 == 0) healNearby();
            }
            case VELTARI -> {
                if (tickCount % 10 == 0) bowToLanterns();
            }
            case SKRELL_VATH -> {
                if (tickCount % 100 == 0 && getTarget() != null) snuffTorch();
            }
            case GHALLOS -> {
                if (tickCount % 2400 == 0 && offers != null) {
                    for (MerchantOffer o : offers) if (o.getSpecialPriceDiff() > 0) o.addToSpecialPriceDiff(-1);
                }
            }
            case ZAELITH -> {
                if (tickCount % 20 == 0) syncPartner();
            }
            default -> {}
        }
        if (attackCooldown > 0) attackCooldown--;
        if (lifetime > 0 && --lifetime == 0) {
            ((ServerLevel) level()).sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 20, 0.3, 0.6, 0.3, 0.02);
            discard();
        }
    }

    private void hover() {
        setDeltaMovement(Vec3.ZERO);
        if (home == null) home = blockPosition();
        double y = home.getY() + 0.4 + Math.sin(tickCount * 0.05) * 0.15;
        setPos(getX(), y, getZ());
    }

    private void healNearby() {
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(6))) {
            if (p.getHealth() < p.getMaxHealth() * 0.5f) {
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.8, p.getZ(), 4, 0.4, 0.3, 0.4, 0);
                level().playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1f, 1.8f);
                if (p instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("message.lanterna.aelinos_heal"), true);
            }
        }
    }

    private void bowToLanterns() {
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(5))) {
            RingData ring = ModCapabilities.get(p).orElse(null);
            if (ring == null || !ring.hasRing() || !ring.powerActive() || !ring.corps().equals(BuiltinCorps.GREEN.id())) continue;
            long last = bowed.getOrDefault(p.getUUID(), -10000L);
            if (level().getGameTime() - last < 600) continue;
            bowed.put(p.getUUID(), level().getGameTime());
            getLookControl().setLookAt(p);
            setFlag(BOW, true);
            timerUntil = level().getGameTime() + 40;
            return;
        }
    }

    private void snuffTorch() {
        BlockPos c = blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-5, -2, -5), c.offset(5, 3, 5))) {
            var s = level().getBlockState(p);
            if (s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH)
                    || s.is(Blocks.LANTERN) || s.is(Blocks.CAMPFIRE)) {
                level().destroyBlock(p, true);
                level().playSound(null, p, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.8f, 0.6f);
                return;
            }
        }
    }

    // ===================== Brakkari: duelo =====================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (race() == Race.GUARDIAO && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (!role().isEmpty() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // Líderes e instrutores não morrem (são da história)
            if (source.getEntity() instanceof Player p && !level().isClientSide) {
                p.displayClientMessage(Component.translatable("message.lanterna.leader_unharmed"), true);
            }
            return false;
        }
        if (!level().isClientSide && source.getEntity() instanceof ServerPlayer sp) {
            if (race() == Race.BRAKKARI && !hasFlag(KNEEL) && getHealth() - amount <= getMaxHealth() * 0.25f) {
                setHealth(getMaxHealth() * 0.25f);
                surrender(sp);
                return false;
            }
            if (race() == Race.ZAELITH) imprison(sp);
            if (race() == Race.GHALLOS && stolen.isEmpty()) angerAt(sp, 1200);
        }
        if (hasFlag(KNEEL)) return super.hurt(source, amount * 0.5f);
        return super.hurt(source, amount);
    }

    private void surrender(ServerPlayer winner) {
        setFlag(KNEEL, true);
        timerUntil = level().getGameTime() + 600;
        setTarget(null);
        getNavigation().stop();
        for (RaceEntity other : level().getEntitiesOfClass(RaceEntity.class, getBoundingBox().inflate(32))) {
            if (other.getTarget() == winner) other.setTarget(null);
        }
        level().playSound(null, blockPosition(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 1f, 0.8f);
        if (beatenBy.add(winner.getUUID())) {
            LoreCapability.get(winner).ifPresent(d -> d.addRep("brakkari", 1));
            winner.sendSystemMessage(Component.translatable("message.lanterna.brakkari_duel_won")
                    .withStyle(s -> s.withColor(BuiltinCorps.RED.color()).withBold(true)));
            // Ganhar uma luta faz crescer um chifre (mais chifres = mais alto na hierarquia)
        }
        setVariant(Math.max(0, getVariant() - 1));
    }

    // ===================== Zaëlith: prisão de cristal e pares =====================

    private void imprison(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 6, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 60, 2, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.JUMP, 60, 128, false, false));
        ((ServerLevel) level()).sendParticles(new DustParticleOptions(new Vector3f(0.95f, 0.6f, 1f), 1.6f),
                p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.9, 0.4, 0);
        level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.HOSTILE, 1.2f, 0.7f);
        p.displayClientMessage(Component.translatable("message.lanterna.zaelith_prison"), true);
        angerAt(p, 2400);
        RaceEntity mate = partnerEntity();
        if (mate != null) mate.angerAt(p, 2400);
    }

    @Nullable
    public RaceEntity partnerEntity() {
        if (partner == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getEntity(partner) instanceof RaceEntity r && r.isAlive() ? r : null;
    }

    private void syncPartner() {
        RaceEntity mate = partnerEntity();
        entityData.set(PAIR, mate != null ? mate.getId() : -1);
    }

    /** Id do par (no cliente, para desenhar o fio de luz). */
    public int pairId() {
        return entityData.get(PAIR);
    }

    public void setPartner(RaceEntity mate) {
        partner = mate.getUUID();
        mate.partner = getUUID();
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            if (race() == Race.ZAELITH) {
                RaceEntity mate = partnerEntity();
                if (mate != null) {
                    mate.setFlag(MOURNING, true);
                    level().playSound(null, mate.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 2f, 0.5f);
                }
            }
            if (race() == Race.GHALLOS && source.getEntity() instanceof ServerPlayer sp) {
                LoreCapability.get(sp).ifPresent(d -> d.addRep("ghallos", -3));
                for (RaceEntity g : level().getEntitiesOfClass(RaceEntity.class, getBoundingBox().inflate(32))) {
                    if (g.race() == Race.GHALLOS && g != this) g.angerAt(sp, 6000);
                }
                sp.displayClientMessage(Component.translatable("message.lanterna.ghallos_angry"), true);
            }
            if (!stolen.isEmpty()) {
                spawnAtLocation(stolen.copy());
                stolen = ItemStack.EMPTY;
            }
        }
        super.die(source);
    }

    // ===================== Interação =====================

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isAlive() || isTrading() || hand != InteractionHand.MAIN_HAND) return super.mobInteract(player, hand);
        ItemStack held = player.getItemInHand(hand);
        if (level().isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer sp = (ServerPlayer) player;

        if (race() == Race.ZAELITH && !held.isEmpty() && held.is(favorite())) {
            giveGift(sp, held);
            return InteractionResult.CONSUME;
        }
        if (race() == Race.VES_KAI && hasFlag(WOUNDED) && isHealingItem(held)) {
            healWounded(sp, held);
            return InteractionResult.CONSUME;
        }
        if (getTarget() == player && role().isEmpty()) return InteractionResult.PASS;
        if (player.isShiftKeyDown() && canTradeWith(sp)) {
            openTrade(sp);
            return InteractionResult.CONSUME;
        }
        NpcDialogs.open(sp, this);
        return InteractionResult.CONSUME;
    }

    public Item favorite() {
        return FAVORITES.get(Math.floorMod(getUUID().hashCode(), FAVORITES.size()));
    }

    public int affection(Player p) {
        return affection.getOrDefault(p.getUUID(), 0);
    }

    private void giveGift(ServerPlayer p, ItemStack held) {
        if (!p.getAbilities().instabuild) held.shrink(1);
        affection.merge(p.getUUID(), 1, Integer::sum);
        RaceEntity mate = partnerEntity();
        if (mate != null) mate.affection.merge(p.getUUID(), 1, Integer::sum);
        LoreCapability.get(p).ifPresent(d -> d.addRep("zaelith", 1));
        ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, getX(), getY() + 2.2, getZ(), 6, 0.4, 0.3, 0.4, 0);
        level().playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.5f, 1.4f);
        p.displayClientMessage(Component.translatable("message.lanterna.zaelith_gift", affection(p)), true);
    }

    public static boolean isHealingItem(ItemStack s) {
        if (s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE) || s.is(Items.GLISTERING_MELON_SLICE)
                || s.is(LoreItems.ERVA_TALEUN.get()) || s.is(LoreItems.BENCAO_AELINOS.get())
                || s.getItem() instanceof com.lanterna.item.CompassionStaffItem) return true;
        if (s.is(Items.POTION) || s.is(Items.SPLASH_POTION)) {
            var potion = PotionUtils.getPotion(s);
            return potion == Potions.HEALING || potion == Potions.STRONG_HEALING || potion == Potions.REGENERATION
                    || potion == Potions.LONG_REGENERATION || potion == Potions.STRONG_REGENERATION;
        }
        return false;
    }

    private void healWounded(ServerPlayer p, ItemStack held) {
        if (!(held.getItem() instanceof com.lanterna.item.CompassionStaffItem) && !p.getAbilities().instabuild) held.shrink(1);
        setHealth(getMaxHealth());
        setFlag(WOUNDED, false);
        ((ServerLevel) level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.5, getZ(), 12, 0.4, 0.6, 0.4, 0);
        level().playSound(null, blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 1f, 0.9f);
        ItemStack gift = new ItemStack(LoreItems.GRATIDAO.get(), 1 + random.nextInt(2));
        if (!p.getInventory().add(gift)) p.drop(gift, false);
        LoreCapability.get(p).ifPresent(d -> d.addRep("ves_kai", 1));
        p.displayClientMessage(Component.translatable("message.lanterna.veskai_healed"), true);
    }

    /** O jogador usa o anel de uma Tropa inimiga deste povo e ainda não tem o respeito dele? */
    public boolean enemyCorps(Player p) {
        Race r = race();
        if (r == Race.GUARDIAO) return false;
        if (!com.lanterna.lore.Factions.raceHatesPlayer(r, p)) return false;
        return rep(p, r.repKey()) < com.lanterna.mission.Missions.repNeeded(r.repKey());
    }

    public boolean canTradeWith(ServerPlayer p) {
        Race r = race();
        if (!r.trades() || !role().equals("") && r == Race.GUARDIAO) return false;
        if (enemyCorps(p)) return false;
        return switch (r) {
            case BRAKKARI -> rep(p, "brakkari") >= 1;
            case SKRELL_VATH -> rep(p, "skrell_vath") >= 1;
            case VELTARI -> hasGreenRing(p) || rep(p, "veltari") >= 5;
            case ZAELITH -> affection(p) >= 2 && !hasFlag(MOURNING);
            default -> true;
        };
    }

    public static boolean hasGreenRing(Player p) {
        RingData ring = ModCapabilities.get(p).orElse(null);
        return ring != null && ring.hasRing() && ring.corps().equals(BuiltinCorps.GREEN.id());
    }

    public void openTrade(ServerPlayer p) {
        if (getOffers().isEmpty()) return;
        // Aelínos: com o anel verde, tudo fica mais barato ("esperança sem vontade não age")
        if (race() == Race.AELINOS && hasGreenRing(p)) {
            for (MerchantOffer o : getOffers()) {
                o.addToSpecialPriceDiff(-Math.max(1, o.getBaseCostA().getCount() / 3));
                discounted.add(o);
            }
        }
        setTradingPlayer(p);
        openTradingScreen(p, getDisplayName(), 1);
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        if (player == null && !discounted.isEmpty()) {
            for (MerchantOffer o : discounted) o.resetSpecialPriceDiff();
            discounted.clear();
        }
        super.setTradingPlayer(player);
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        super.notifyTrade(offer);
        if (!(getTradingPlayer() instanceof ServerPlayer sp)) return;
        switch (race()) {
            // A Ganância respeita quem paga — e cobra mais da próxima vez
            case GHALLOS -> {
                offer.addToSpecialPriceDiff(Math.max(1, offer.getBaseCostA().getCount() / 4));
                LoreCapability.get(sp).ifPresent(d -> d.addRep("ghallos", 1));
            }
            case COLETOR -> LoreCapability.get(sp).ifPresent(d -> d.addRep("silente", 1));
            default -> {}
        }
    }

    @Override
    protected void updateTrades() {
        getOffers().addAll(RaceTrades.of(race()));
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
        if (offer.shouldRewardExp()) {
            level().addFreshEntity(new net.minecraft.world.entity.ExperienceOrb(level(), getX(), getY() + 0.5, getZ(), 3 + random.nextInt(4)));
        }
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    // ===================== Ataque =====================

    @Override
    public boolean doHurtTarget(Entity target) {
        triggerAnim("attack", "strike");
        boolean hit = super.doHurtTarget(target);
        if (hit && race() == Race.SILENTE && target instanceof LivingEntity le) {
            le.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 1));
        }
        return hit;
    }

    // ===================== Nascimento =====================

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data, tag);
        switch (race()) {
            case BRAKKARI -> setVariant(1 + random.nextInt(6));                       // chifres
            case SKRELL_VATH -> setFlag(LEADER_MASK, random.nextInt(5) == 0);         // líder de colmeia
            case VES_KAI -> {
                if (random.nextInt(10) < 3) {                                         // ferido
                    setFlag(WOUNDED, true);
                    setHealth(getMaxHealth() * 0.35f);
                }
            }
            case ZAELITH -> {
                if (partner == null && reason != MobSpawnType.COMMAND && level instanceof ServerLevel sl) {
                    RaceEntity mate = LoreEntities.typeOf(Race.ZAELITH).create(sl);
                    if (mate != null) {
                        mate.partner = getUUID(); // evita gerar outro par
                        mate.moveTo(getX() + 1.5, getY(), getZ() + 0.5, getYRot() + 180, 0);
                        mate.finalizeSpawn(level, difficulty, reason, null, null);
                        setPartner(mate);
                        sl.addFreshEntity(mate);
                    }
                }
            }
            default -> {}
        }
        return result;
    }

    // ===================== Papel (líder, instrutor, guardião) =====================

    public String role() {
        return entityData.get(ROLE);
    }

    public void setRole(String role, @Nullable BlockPos home) {
        entityData.set(ROLE, role);
        this.home = home;
        if (home != null && race() != Race.GUARDIAO) restrictTo(home, 6);
        setPersistenceRequired();
    }

    public boolean hasFlag(int flag) {
        return (entityData.get(FLAGS) & flag) != 0;
    }

    public void setFlag(int flag, boolean on) {
        int f = entityData.get(FLAGS);
        entityData.set(FLAGS, on ? f | flag : f & ~flag);
    }

    public int getVariant() {
        return entityData.get(VARIANT);
    }

    public void setVariant(int v) {
        entityData.set(VARIANT, v);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return role().isEmpty() && !hasCustomName() && !isPersistenceRequired() && stolen.isEmpty();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        Race r = race();
        return EntityDimensions.scalable(r.width, r.height);
    }

    @Override
    public boolean isPushable() {
        return role().isEmpty() && race() != Race.GUARDIAO && super.isPushable();
    }

    @Override
    protected void pushEntities() {
        if (race() != Race.GUARDIAO) super.pushEntities();
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return race() != Race.GUARDIAO && super.causeFallDamage(distance, multiplier, source);
    }

    // ===================== Sons =====================

    @Override
    protected SoundEvent getAmbientSound() {
        if (isTrading()) return SoundEvents.WANDERING_TRADER_TRADE;
        return switch (race()) {
            case BRAKKARI -> SoundEvents.RAVAGER_AMBIENT;
            case GHALLOS -> SoundEvents.CHAIN_STEP;
            case SKRELL_VATH -> SoundEvents.SPIDER_AMBIENT;
            case VELTARI, GUARDIAO -> null;
            case AELINOS -> SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
            case VES_KAI -> SoundEvents.SOUL_ESCAPE;
            case ZAELITH -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case SILENTE, COLETOR -> null;
        };
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return switch (race()) {
            case BRAKKARI -> SoundEvents.RAVAGER_HURT;
            case SKRELL_VATH -> SoundEvents.SPIDER_HURT;
            case ZAELITH, AELINOS -> SoundEvents.AMETHYST_BLOCK_HIT;
            case SILENTE, COLETOR -> SoundEvents.SKELETON_HURT;
            default -> SoundEvents.WANDERING_TRADER_HURT;
        };
    }

    @Override
    protected SoundEvent getDeathSound() {
        return switch (race()) {
            case BRAKKARI -> SoundEvents.RAVAGER_DEATH;
            case SKRELL_VATH -> SoundEvents.SPIDER_DEATH;
            case ZAELITH, AELINOS -> SoundEvents.AMETHYST_BLOCK_BREAK;
            case SILENTE, COLETOR -> SoundEvents.SKELETON_DEATH;
            default -> SoundEvents.WANDERING_TRADER_DEATH;
        };
    }

    @Override
    public float getVoicePitch() {
        return switch (race()) {
            case BRAKKARI -> 0.6f;
            case GHALLOS -> 1.6f;
            case AELINOS -> 1.5f;
            default -> 1.0f;
        };
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(boolean yes) {
        return yes ? SoundEvents.WANDERING_TRADER_YES : SoundEvents.WANDERING_TRADER_NO;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.WANDERING_TRADER_YES;
    }

    // ===================== NBT =====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Role", role());
        tag.putInt("Flags", entityData.get(FLAGS) & (WOUNDED | LEADER_MASK | MOURNING));
        tag.putInt("Variant", getVariant());
        if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
        if (partner != null) tag.putUUID("Partner", partner);
        if (!stolen.isEmpty()) tag.put("Stolen", stolen.save(new CompoundTag()));
        tag.put("Beaten", uuids(beatenBy));
        tag.put("Spared", uuids(sparedBy));
        CompoundTag aff = new CompoundTag();
        affection.forEach((k, v) -> aff.putInt(k.toString(), v));
        tag.put("Affection", aff);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        BlockPos h = tag.contains("Home") ? NbtUtils.readBlockPos(tag.getCompound("Home")) : null;
        setRole(tag.getString("Role"), h);
        if (tag.getString("Role").isEmpty() && h == null) clearRestriction();
        entityData.set(FLAGS, tag.getInt("Flags"));
        setVariant(tag.getInt("Variant"));
        if (tag.hasUUID("Partner")) partner = tag.getUUID("Partner");
        if (tag.contains("Stolen")) stolen = ItemStack.of(tag.getCompound("Stolen"));
        readUuids(tag.getList("Beaten", Tag.TAG_STRING), beatenBy);
        readUuids(tag.getList("Spared", Tag.TAG_STRING), sparedBy);
        CompoundTag aff = tag.getCompound("Affection");
        for (String k : aff.getAllKeys()) {
            try {
                affection.put(UUID.fromString(k), aff.getInt(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static ListTag uuids(Set<UUID> set) {
        ListTag list = new ListTag();
        for (UUID u : set) list.add(net.minecraft.nbt.StringTag.valueOf(u.toString()));
        return list;
    }

    private static void readUuids(ListTag list, Set<UUID> into) {
        for (int i = 0; i < list.size(); i++) {
            try {
                into.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    // ===================== Animações =====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 4,
                state -> state.setAndContinue(state.isMoving() && !hasFlag(KNEEL) ? WALK : IDLE)));
        controllers.add(new AnimationController<>(this, "attack", 0, state -> PlayState.STOP)
                .triggerableAnim("strike", STRIKE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ===================== Objetivos especiais =====================

    /** Ghallos: chega perto, puxa um item e foge. */
    private class StealGoal extends Goal {
        @Nullable private Player victim;
        private int fleeTicks;

        StealGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!stolen.isEmpty() || level().getGameTime() < nextSteal || isTrading() || !role().isEmpty()) return false;
            if (random.nextInt(40) != 0) return false;
            Player p = level().getNearestPlayer(RaceEntity.this, 10);
            if (p == null || p.isCreative() || p.isSpectator() || rep(p, "ghallos") >= 5) return false;
            victim = p;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return victim != null && victim.isAlive() && (fleeTicks > 0 || stolen.isEmpty()) && distanceTo(victim) < 24;
        }

        @Override
        public void start() {
            fleeTicks = 0;
        }

        @Override
        public void tick() {
            if (victim == null) return;
            if (fleeTicks > 0) {
                fleeTicks--;
                if (getNavigation().isDone()) {
                    Vec3 away = DefaultRandomPos.getPosAway(RaceEntity.this, 16, 7, victim.position());
                    if (away != null) getNavigation().moveTo(away.x, away.y, away.z, 1.6);
                }
                return;
            }
            getNavigation().moveTo(victim, 1.3);
            if (distanceToSqr(victim) < 3.0) {
                List<Integer> slots = new ArrayList<>();
                var inv = victim.getInventory().items;
                for (int i = 0; i < inv.size(); i++) if (!inv.get(i).isEmpty() && i != victim.getInventory().selected) slots.add(i);
                if (slots.isEmpty()) {
                    nextSteal = level().getGameTime() + 1200;
                    victim = null;
                    return;
                }
                int slot = slots.get(random.nextInt(slots.size()));
                stolen = inv.get(slot).copy();
                inv.set(slot, ItemStack.EMPTY);
                stolenFrom = victim.getUUID();
                nextSteal = level().getGameTime() + 2400;
                level().playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 1f, 1.6f);
                ((ServerLevel) level()).sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stolen),
                        getX(), getY() + 1, getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                victim.displayClientMessage(Component.translatable("message.lanterna.ghallos_stole", stolen.getHoverName()), true);
                setFlag(FLEE, true);
                timerUntil = level().getGameTime() + 200;
                fleeTicks = 200;
            }
        }

        @Override
        public void stop() {
            victim = null;
            getNavigation().stop();
        }
    }

    /** Skrell'Vath: congela quando é olhado, avança e ataca pelas costas; encarar por 3 s faz recuar. */
    private class StalkGoal extends Goal {
        StalkGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return getTarget() instanceof Player p && p.isAlive() && !hasFlag(FLEE);
        }

        @Override
        public void tick() {
            if (!(getTarget() instanceof Player p)) return;
            if (watchedBy(p)) {
                getNavigation().stop();
                int t = stare.merge(p.getUUID(), 1, Integer::sum);
                if (t >= 60) recoil(p);
                return;
            }
            stare.put(p.getUUID(), Math.max(0, stare.getOrDefault(p.getUUID(), 0) - 1));
            getLookControl().setLookAt(p, 30, 30);
            getNavigation().moveTo(p, 1.35);
            double reach = getBbWidth() * 2.0 + p.getBbWidth() + 1.0;
            if (distanceToSqr(p) <= reach * reach && attackCooldown <= 0) {
                attackCooldown = 25;
                doHurtTarget(p);
            }
        }

        private boolean watchedBy(Player p) {
            Vec3 view = p.getViewVector(1f).normalize();
            Vec3 to = new Vec3(getX() - p.getX(), getEyeY() - p.getEyeY(), getZ() - p.getZ());
            double dist = to.length();
            if (dist > 32) return false;
            return view.dot(to.normalize()) > 1.0 - 0.06 / Math.max(1, dist / 8) && p.hasLineOfSight(RaceEntity.this);
        }

        private void recoil(Player p) {
            stare.remove(p.getUUID());
            setTarget(null);
            setFlag(FLEE, true);
            timerUntil = level().getGameTime() + 300;
            Vec3 away = DefaultRandomPos.getPosAway(RaceEntity.this, 20, 7, p.position());
            if (away != null) getNavigation().moveTo(away.x, away.y, away.z, 1.5);
            level().playSound(null, blockPosition(), SoundEvents.SPIDER_HURT, SoundSource.HOSTILE, 1f, 0.5f);
            if (sparedBy.add(p.getUUID()) && p instanceof ServerPlayer sp) {
                LoreCapability.get(sp).ifPresent(d -> d.addRep("skrell_vath", 1));
                sp.displayClientMessage(Component.translatable("message.lanterna.skrell_recoil")
                        .withStyle(s -> s.withColor(BuiltinCorps.YELLOW.color())), true);
            }
        }
    }

    /** Zaëlith: nunca se afasta do par. */
    private class FollowPartnerGoal extends Goal {
        FollowPartnerGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            RaceEntity mate = partnerEntity();
            return mate != null && distanceToSqr(mate) > 36 && getTarget() == null;
        }

        @Override
        public void tick() {
            RaceEntity mate = partnerEntity();
            if (mate != null) getNavigation().moveTo(mate, 1.0);
        }
    }

    /** Ves'Kai: se o jogador ferir um inocente perto, ficam hostis. Chamado por {@link LoreEntityEvents}. */
    public static void innocentHarmed(ServerPlayer attacker, LivingEntity victim) {
        for (RaceEntity v : attacker.level().getEntitiesOfClass(RaceEntity.class, victim.getBoundingBox().inflate(16))) {
            if (v.race() == Race.VES_KAI && v.role().isEmpty() && v != victim) {
                if (v.angryUntil.getOrDefault(attacker.getUUID(), 0L) < attacker.level().getGameTime()) {
                    attacker.displayClientMessage(Component.translatable("message.lanterna.veskai_angry"), true);
                    LoreData d = MissionManager.data(attacker);
                    if (d != null) d.addRep("ves_kai", -1);
                }
                v.angerAt(attacker, 2400);
            }
        }
    }

    public float bowAmount(float partial) {
        return hasFlag(BOW) ? 1f : 0f;
    }

    public static float clamp01(float v) {
        return Mth.clamp(v, 0f, 1f);
    }
}
