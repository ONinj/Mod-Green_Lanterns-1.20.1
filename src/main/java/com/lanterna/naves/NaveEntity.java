package com.lanterna.naves;

import com.lanterna.especies.LanternaEntity;
import com.lanterna.space.SpaceBodies;
import com.lanterna.tropa.TiroPlasma;
import com.lanterna.tropa.TropaItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Nave pilotável (e os destroços, que usam a mesma entidade sem motor).
 *  - O primeiro passageiro é o piloto: WASD voa para onde ele olha, espaço sobe, C desce, Ctrl é o turbo, X atira, B abre a porta.
 *  - Só pilota quem é o dono ou tem a chave. Shift + clique entra no interior (uma sala de blocos numa dimensão própria).
 *  - As caixas col_N viram partes sólidas (dá para pisar e acertar).
 *  - Naves com IA (piratas, patrulhas, cargueiros dos encontros no espaço) se pilotam sozinhas.
 */
public class NaveEntity extends Entity implements GeoEntity, TemNave {

    private static final EntityDataAccessor<String> TIPO = SynchedEntityData.defineId(NaveEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> PINTURA = SynchedEntityData.defineId(NaveEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> HP = SynchedEntityData.defineId(NaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> POUSADA = SynchedEntityData.defineId(NaveEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TURBO = SynchedEntityData.defineId(NaveEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> TORRE = SynchedEntityData.defineId(NaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> DONO = SynchedEntityData.defineId(NaveEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> IA = SynchedEntityData.defineId(NaveEntity.class, EntityDataSerializers.STRING);

    /** Entrada do piloto (lida só no cliente, preenchida pelo código de cliente). */
    public record Input(float forward, float strafe, boolean up, boolean down, boolean boost) {}
    public static Function<NaveEntity, Input> CLIENT_INPUT = n -> new Input(0, 0, false, false, false);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("misc.idle");
    private static final RawAnimation FLOAT = RawAnimation.begin().thenLoop("misc.float");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("move.fly");
    private static final RawAnimation BOOST = RawAnimation.begin().thenLoop("move.boost");
    private static final RawAnimation LAND = RawAnimation.begin().then("misc.land", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation TAKEOFF = RawAnimation.begin().then("misc.takeoff", Animation.LoopType.PLAY_ONCE).thenLoop("move.fly");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final NavePart[] parts = new NavePart[8];
    private boolean portaAberta;
    private int fireCooldown;
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYRot;
    private boolean lootTaken;
    /** IA: alvo atual e tempo de vida (encontros no espaço). */
    @Nullable private UUID aiTarget;
    private int aiLife = -1;
    private int aiTimer;
    private final NaveAI ai = new NaveAI(this);

    public NaveEntity(EntityType<? extends NaveEntity> type, Level level) {
        super(type, level);
        for (int i = 0; i < parts.length; i++) parts[i] = new NavePart(this, i);
        // reserva os ids seguintes para as partes (como o Ender Dragon): nave = v+1, partes = v+2..v+9
        setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < parts.length; i++) parts[i].setId(id + i + 1);
    }

    /** Cria uma nave do tipo e pintura dados, pronta para pôr no mundo. */
    public static NaveEntity create(Level level, NaveTipo tipo, String pintura) {
        NaveEntity n = new NaveEntity(NaveEntities.NAVE.get(), level);
        n.entityData.set(TIPO, tipo.id);
        n.entityData.set(PINTURA, pintura);
        n.entityData.set(HP, (float) tipo.maxHp);
        n.refreshDimensions();
        return n;
    }

    // ===================== Dados =====================

    @Override
    protected void defineSynchedData() {
        entityData.define(TIPO, NaveTipo.NAVE_LANCEIRA.id);
        entityData.define(PINTURA, "green");
        entityData.define(HP, 120f);
        entityData.define(POUSADA, true);
        entityData.define(TURBO, false);
        entityData.define(TORRE, 0f);
        entityData.define(DONO, Optional.empty());
        entityData.define(IA, "");
    }

    public NaveTipo tipo() { return NaveTipo.byId(entityData.get(TIPO)); }
    @Override public String getTipoNave() { return tipo().id; }
    @Override public String getPintura() { return tipo().pinturaValida(entityData.get(PINTURA)); }
    public float hp() { return entityData.get(HP); }
    public boolean pousada() { return entityData.get(POUSADA); }
    public boolean turbo() { return entityData.get(TURBO); }
    public String ia() { return entityData.get(IA); }
    public void setIa(String mode) { entityData.set(IA, mode); }
    @Nullable public UUID dono() { return entityData.get(DONO).orElse(null); }
    public void setDono(@Nullable UUID id) { entityData.set(DONO, Optional.ofNullable(id)); }
    public void setTurbo(boolean v) { if (turbo() != v) entityData.set(TURBO, v); }
    public void setAnguloTorre(float deg) { entityData.set(TORRE, deg); }
    public void setAiLife(int ticks) { aiLife = ticks; }
    public void setAiTarget(@Nullable UUID id) { aiTarget = id; }
    @Nullable public UUID aiTarget() { return aiTarget; }
    public int aiTimer() { return aiTimer; }
    public net.minecraft.util.RandomSource rng() { return random; }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (TIPO.equals(key)) refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        NaveTipo t = tipo();
        return EntityDimensions.scalable(t.width, t.height);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // /summon sem NBT: vira uma Lanceira verde em vez de procurar um modelo de nome vazio
        NaveTipo t = NaveTipo.byId(tag.getString("Tipo"));
        entityData.set(TIPO, t.id);
        entityData.set(PINTURA, t.pinturaValida(tag.getString("Pintura")));
        entityData.set(HP, tag.contains("Hp") ? tag.getFloat("Hp") : tipo().maxHp);
        entityData.set(IA, tag.getString("Ia"));
        if (tag.hasUUID("Dono")) setDono(tag.getUUID("Dono"));
        lootTaken = tag.getBoolean("Saqueado");
        aiLife = tag.contains("AiLife") ? tag.getInt("AiLife") : -1;
        refreshDimensions();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Tipo", getTipoNave());
        tag.putString("Pintura", getPintura());
        tag.putFloat("Hp", hp());
        tag.putString("Ia", ia());
        if (dono() != null) tag.putUUID("Dono", dono());
        tag.putBoolean("Saqueado", lootTaken);
        tag.putInt("AiLife", aiLife);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // ===================== Partes (colisão) =====================

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    private void updateParts() {
        NaveTipo t = tipo();
        double yaw = Math.toRadians(getYRot());
        double cos = Math.cos(yaw), sin = Math.sin(yaw);
        for (int i = 0; i < parts.length; i++) {
            NavePart p = parts[i];
            if (i >= t.colisao.length) {
                p.resize(0.1f, 0.1f);
                p.setPos(getX(), getY(), getZ());
                continue;
            }
            double[] c = t.colisao[i];
            // Caixa em blocos; gira os 4 cantos pelo yaw e pega a caixa alinhada que os contém
            double x0 = c[0] / 16, z0 = c[2] / 16, x1 = (c[0] + c[3]) / 16, z1 = (c[2] + c[5]) / 16;
            double minX = 1e9, maxX = -1e9, minZ = 1e9, maxZ = -1e9;
            for (double[] corner : new double[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
                // modelo: +x = esquerda, -z = frente  ->  mundo
                double lx = corner[0], fz = -corner[1];
                double wx = -sin * fz + cos * lx;
                double wz = cos * fz + sin * lx;
                minX = Math.min(minX, wx); maxX = Math.max(maxX, wx);
                minZ = Math.min(minZ, wz); maxZ = Math.max(maxZ, wz);
            }
            float w = (float) Math.max(maxX - minX, maxZ - minZ);
            float h = (float) (c[4] / 16);
            // Caixas muito compridas na diagonal viram quadrados grandes: limita à menor dimensão para não criar "paredes invisíveis"
            float wMin = (float) Math.min(maxX - minX, maxZ - minZ);
            p.resize(Math.max(0.3f, Math.min(w, wMin + 1.5f)), Math.max(0.3f, h));
            p.setPos(getX() + (minX + maxX) / 2, getY() + c[1] / 16, getZ() + (minZ + maxZ) / 2);
            p.xo = p.getX(); p.yo = p.getY(); p.zo = p.getZ();
            p.xOld = p.getX(); p.yOld = p.getY(); p.zOld = p.getZ();
        }
    }

    @Override
    public boolean canCollideWith(Entity other) {
        if (other instanceof NavePart p && p.getParent() == this) return false;
        return other.canBeCollidedWith() && !isPassengerOfSameVehicle(other);
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // ===================== Tick =====================

    @Override
    public void tick() {
        super.tick();
        NaveTipo t = tipo();
        boolean espaco = level().dimension().equals(SpaceBodies.SPACE);
        if (fireCooldown > 0) fireCooldown--;

        noPhysics = espaco && !t.destrocos();
        if (isControlledByLocalInstance()) {
            lerpSteps = 0;
            syncPacketPositionCodec(getX(), getY(), getZ());
            if (getControllingPassenger() instanceof Player) pilot(t, espaco);
            else if (!ia().isEmpty() || getControllingPassenger() != null) ai.tick(t, espaco);
            else drift(espaco);
        } else if (lerpSteps > 0) {
            double nx = getX() + (lerpX - getX()) / lerpSteps;
            double ny = getY() + (lerpY - getY()) / lerpSteps;
            double nz = getZ() + (lerpZ - getZ()) / lerpSteps;
            setYRot(getYRot() + (float) Mth.wrapDegrees(lerpYRot - getYRot()) / lerpSteps);
            lerpSteps--;
            setPos(nx, ny, nz);
        }
        updateParts();

        if (!level().isClientSide) {
            entityData.set(POUSADA, onGround() || t.destrocos());
            if (getControllingPassenger() == null) setTurbo(false);
            if (aiLife > 0 && --aiLife == 0) {
                despawn();
                return;
            }
            aiTimer++;
            NaveViagem.check(this);
            if (tickCount % 20 == 0 && !t.destrocos() && hp() < t.maxHp * 0.3f) {
                ((ServerLevel) level()).sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + t.height, getZ(), 3, t.width / 3, 0.3, t.width / 3, 0.01);
            }
        } else {
            tickAnimacao();
        }
        if (level().isClientSide && !t.destrocos() && !pousada() && tickCount % 2 == 0) {
            // Rastro dos motores
            for (int i = 0; i < 2; i++) {
                Vec3 back = TemNave.posicaoAssento(position(), getYRot(), (i == 0 ? -1 : 1) * t.width * 3, t.height * 5, t.width * 12);
                level().addParticle(turbo() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, back.x, back.y, back.z, 0, 0, 0);
            }
        }
    }

    /** Sem piloto: cai devagar até o chão (no espaço, fica à deriva). */
    private void drift(boolean espaco) {
        Vec3 v = getDeltaMovement().scale(espaco ? 0.96 : 0.9);
        if (!espaco && !onGround()) v = v.add(0, -0.03, 0);
        if (!espaco && onGround()) v = new Vec3(v.x * 0.5, 0, v.z * 0.5);
        setDeltaMovement(v.y < -0.6 ? new Vec3(v.x, -0.6, v.z) : v);
        move(MoverType.SELF, getDeltaMovement());
    }

    /** Piloto humano (roda no cliente dele; o servidor recebe a posição pelo pacote de veículo do vanilla). */
    private void pilot(NaveTipo t, boolean espaco) {
        if (!(getControllingPassenger() instanceof Player pilot)) return;
        Input in = level().isClientSide ? CLIENT_INPUT.apply(this) : new Input(0, 0, false, false, false);
        float turn = 9f / (1f + t.width * 0.35f);
        float diff = Mth.wrapDegrees(pilot.getYRot() - getYRot());
        setYRot(getYRot() + Mth.clamp(diff, -turn, turn));
        float boostMul = in.boost() ? (espaco ? 6f : 2.2f) : 1f;
        double max = t.speed * boostMul;
        Vec3 v = getDeltaMovement().scale(0.93);
        if (in.forward() != 0) {
            Vec3 dir = Vec3.directionFromRotation(Mth.clamp(pilot.getXRot(), -60, 60), getYRot());
            v = v.add(dir.scale(in.forward() * max * 0.09));
        }
        if (in.strafe() != 0) {
            Vec3 side = Vec3.directionFromRotation(0, getYRot() - 90).scale(in.strafe() * max * 0.04);
            v = v.add(side);
        }
        if (in.up()) v = v.add(0, max * 0.06, 0);
        if (in.down()) v = v.add(0, -max * 0.06, 0);
        if (!in.up() && !in.down() && in.forward() == 0) v = new Vec3(v.x, v.y * 0.8, v.z);
        if (v.length() > max) v = v.normalize().scale(max);
        if (onGround() && in.forward() == 0 && !in.up()) v = new Vec3(v.x * 0.6, Math.min(0, v.y), v.z * 0.6);
        setDeltaMovement(v);
        move(MoverType.SELF, v);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        lerpX = x; lerpY = y; lerpZ = z; lerpYRot = yRot;
        lerpSteps = 6;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        List<Entity> ps = getPassengers();
        return !ps.isEmpty() && ps.get(0) instanceof LivingEntity l ? l : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < tipo().assentos.length;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        if (!hasPassenger(passenger)) return;
        int i = getPassengers().indexOf(passenger);
        double[][] seats = tipo().assentos;
        if (i < 0 || i >= seats.length) return;
        double[] s = seats[i];
        Vec3 p = TemNave.posicaoAssento(position(), getYRot(), s[0], s[1], s[2]);
        move.accept(passenger, p.x, p.y + passenger.getMyRidingOffset(), p.z);
        if (passenger instanceof LanternaEntity npc) {
            npc.setYRot(getYRot());
            npc.setYBodyRot(getYRot());
            npc.setYHeadRot(getYRot());
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        NaveTipo t = tipo();
        Vec3 side = Vec3.directionFromRotation(0, getYRot() + 90).scale(t.width / 2 + 1.2);
        return position().add(side).add(0, 0.5, 0);
    }

    @Override
    public boolean shouldRiderSit() {
        return true;
    }

    // ===================== Interação =====================

    /** Quem pode pilotar: o dono, quem tem a chave desta nave, ou qualquer um se a nave não tem dono. */
    public boolean podePilotar(Player p) {
        if (!ia().isEmpty()) return false;
        if (dono() == null || p.getUUID().equals(dono()) || p.isCreative()) return true;
        for (ItemStack s : p.getInventory().items) {
            if (s.getItem() instanceof TropaItems.ChaveNave && getUUID().equals(TropaItems.ChaveNave.ship(s))) return true;
        }
        return getUUID().equals(TropaItems.ChaveNave.ship(p.getOffhandItem()));
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer sp = (ServerPlayer) player;
        NaveTipo t = tipo();
        ItemStack held = player.getMainHandItem();
        if (t.destrocos()) {
            saquear(sp);
            return InteractionResult.CONSUME;
        }
        if (held.is(TropaItems.FRAGMENTO_CASCO.get()) && hp() < t.maxHp) {
            entityData.set(HP, Math.min(t.maxHp, hp() + t.maxHp * 0.2f));
            if (!player.isCreative()) held.shrink(1);
            level().playSound(null, blockPosition(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.6f, 1.4f);
            sp.displayClientMessage(Component.translatable("nave.lanterna.reparada", Math.round(100 * hp() / t.maxHp)), true);
            return InteractionResult.CONSUME;
        }
        if (!ia().isEmpty()) {
            sp.displayClientMessage(Component.translatable("nave.lanterna.nao_e_sua"), true);
            return InteractionResult.CONSUME;
        }
        if (player.isSecondaryUseActive()) {
            if (!podePilotar(player)) {
                sp.displayClientMessage(Component.translatable("nave.lanterna.trancada"), true);
                return InteractionResult.CONSUME;
            }
            abrirPorta(true);
            NaveInterior.entrar(sp, this);
            return InteractionResult.CONSUME;
        }
        if (getPassengers().isEmpty()) {
            if (!podePilotar(player)) {
                sp.displayClientMessage(Component.translatable("nave.lanterna.trancada"), true);
                return InteractionResult.CONSUME;
            }
            if (dono() == null) {
                setDono(player.getUUID());
                ItemStack key = TropaItems.ChaveNave.forShip(getUUID(), t.id, t.pinturas.equals("tropa") ? getPintura() : null);
                if (!player.getInventory().add(key)) player.drop(key, false);
                sp.displayClientMessage(Component.translatable("nave.lanterna.sua_agora"), false);
            }
        }
        if (canAddPassenger(player) && player.startRiding(this)) {
            if (getControllingPassenger() == player) sp.displayClientMessage(Component.translatable("nave.lanterna.controles"), false);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** Some (encontros que acabaram): leva junto a tripulação de NPCs. */
    public void despawn() {
        for (Entity e : List.copyOf(getPassengers())) {
            e.stopRiding();
            if (!(e instanceof Player)) e.discard();
        }
        discard();
    }

    public void abrirPorta(boolean open) {
        if (portaAberta == open || tipo().porta == null) return;
        portaAberta = open;
        triggerAnim("porta", open ? "misc.door_open" : "misc.door_close");
        level().playSound(null, blockPosition(), open ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT, SoundSource.NEUTRAL, 0.8f, 0.7f);
    }

    public void togglePorta() {
        abrirPorta(!portaAberta);
    }

    public void setHangar(boolean open) {
        if (tipo() == NaveTipo.NAVE_PIRATA_CAPITANIA) triggerAnim("hangar", open ? "misc.hangar_open" : "misc.hangar_close");
    }

    /** Destroços: dá o saque uma vez (peças de nave, às vezes algo valioso). */
    private void saquear(ServerPlayer p) {
        if (lootTaken) {
            p.displayClientMessage(Component.translatable("nave.lanterna.destrocos_vazios"), true);
            return;
        }
        lootTaken = true;
        drop(new ItemStack(TropaItems.FRAGMENTO_CASCO.get(), 2 + random.nextInt(4)));
        if (random.nextInt(3) == 0) drop(new ItemStack(TropaItems.NUCLEO_PROPULSOR.get()));
        if (random.nextInt(2) == 0) drop(new ItemStack(Items.EMERALD, 2 + random.nextInt(6)));
        if (random.nextInt(6) == 0) drop(new ItemStack(TropaItems.BLASTER_PIRATA.get()));
        if (random.nextInt(10) == 0) drop(com.lanterna.item.DormantRingItem.create());
        level().playSound(null, blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.NEUTRAL, 1f, 0.6f);
        com.lanterna.tropa.TropaEvents.onLoot(p, this);
    }

    private void drop(ItemStack s) {
        ItemEntity e = new ItemEntity(level(), getX(), getY() + 1.5, getZ(), s);
        e.setNoGravity(level().dimension().equals(SpaceBodies.SPACE));
        e.setDeltaMovement((random.nextDouble() - 0.5) * 0.2, 0.2, (random.nextDouble() - 0.5) * 0.2);
        level().addFreshEntity(e);
    }

    // ===================== Tiros =====================

    /** Atira de todas as bocas na direção dada (o piloto mira olhando). */
    public void atirar(Vec3 dir, @Nullable Entity shooter) {
        NaveTipo t = tipo();
        if (fireCooldown > 0 || t.bocas.length == 0) return;
        fireCooldown = t == NaveTipo.NAVE_LANCEIRA ? 8 : 14;
        int color = shooterColor();
        float dmg = t == NaveTipo.NAVE_PIRATA_CAPITANIA ? 9f : 6f;
        for (double[] b : t.bocas) {
            Vec3 from = TemNave.posicaoAssento(position(), getYRot(), b[0], b[1], b[2]);
            from = from.add(dir.normalize().scale(1.2));
            TiroPlasma.shoot(level(), shooter != null ? shooter : this, from, dir, 3.0f, dmg, color, t == NaveTipo.NAVE_PIRATA_CAPITANIA);
        }
        triggerAnim("tiro", "attack.fire");
    }

    private int shooterColor() {
        String p = getPintura();
        for (com.lanterna.corps.BuiltinCorps c : com.lanterna.corps.BuiltinCorps.values()) if (c.id().getPath().equals(p)) return c.color();
        return 0xFF3020;
    }

    @Override
    public float getAnguloTorre(float partialTick) {
        if (getControllingPassenger() instanceof Player p) return Mth.wrapDegrees(p.getViewYRot(partialTick) - getViewYRot(partialTick));
        return entityData.get(TORRE);
    }

    // ===================== Dano =====================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || level().isClientSide || isRemoved()) return false;
        Entity src = source.getEntity();
        if (src != null && (hasPassenger(src) || src == this)) return false;
        if (src instanceof Player p && p.isCreative() && p.isShiftKeyDown()) {
            destruir(p, false);
            return true;
        }
        float hp = hp() - amount;
        entityData.set(HP, hp);
        ((ServerLevel) level()).sendParticles(ParticleTypes.CRIT, getX(), getY() + tipo().height / 2, getZ(), 6, 0.6, 0.6, 0.6, 0.2);
        if (src instanceof LivingEntity l) ai.onAttacked(l);
        if (hp <= 0) destruir(src, true);
        return true;
    }

    /** Explode: vira destroços (com saque) e larga peças. */
    public void destruir(@Nullable Entity killer, boolean wreck) {
        NaveTipo t = tipo();
        ServerLevel sl = (ServerLevel) level();
        ejectPassengers();
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + t.height / 2, getZ(), 1, 0, 0, 0, 0);
        sl.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 3f, 0.8f);
        if (t.destrocos()) {
            drop(new ItemStack(TropaItems.FRAGMENTO_CASCO.get(), 1 + random.nextInt(3)));
        } else {
            drop(new ItemStack(TropaItems.FRAGMENTO_CASCO.get(), 2 + random.nextInt(3)));
            if (random.nextInt(2) == 0) drop(new ItemStack(TropaItems.NUCLEO_PROPULSOR.get()));
            if (ia().equals("pirata")) {
                drop(new ItemStack(Items.EMERALD, 4 + random.nextInt(8)));
                if (t == NaveTipo.NAVE_PIRATA_CAPITANIA) drop(new ItemStack(TropaItems.BLASTER_CAPITAO.get()));
            }
            if (wreck) {
                NaveTipo d = NaveTipo.values()[NaveTipo.DESTROCOS_1.ordinal() + random.nextInt(3)];
                NaveEntity w = create(level(), d, "");
                w.moveTo(getX(), getY(), getZ(), getYRot(), 0);
                if (!ia().isEmpty()) w.setAiLife(20 * 60 * 15);
                level().addFreshEntity(w);
            }
            if (killer instanceof ServerPlayer p) com.lanterna.tropa.TropaEvents.onShipDestroyed(p, this);
            else if (killer != null && killer.getControllingPassenger() instanceof ServerPlayer p) com.lanterna.tropa.TropaEvents.onShipDestroyed(p, this);
        }
        discard();
    }

    @Override
    public ItemStack getPickResult() {
        return NaveItem.of(tipo(), getPintura());
    }

    // ===================== Animações =====================

    // Estado da animação (só no cliente), com folga para não piscar entre pousada e voando a cada pulinho
    private boolean animNoAr;
    private int animChao, animAr, animTroca = -100;

    private void tickAnimacao() {
        if (pousada()) { animChao++; animAr = 0; } else { animAr++; animChao = 0; }
        if (animNoAr && animChao > 4) { animNoAr = false; animTroca = tickCount < 10 ? -100 : tickCount; }
        else if (!animNoAr && animAr > 4) { animNoAr = true; animTroca = tickCount < 10 ? -100 : tickCount; }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movimento", 6, state -> {
            NaveTipo t = tipo();
            if (t.destrocos()) return state.setAndContinue(level().dimension().equals(SpaceBodies.SPACE) ? FLOAT : IDLE);
            int since = tickCount - animTroca;
            RawAnimation want;
            if (!animNoAr) want = animTroca > 0 && since < 30 ? LAND : IDLE;
            else if (turbo()) want = BOOST;
            else want = animTroca > 0 && since < 30 ? TAKEOFF : FLY;
            return state.setAndContinue(want);
        }));
        controllers.add(new AnimationController<>(this, "porta", 0, state -> PlayState.STOP)
                .triggerableAnim("misc.door_open", RawAnimation.begin().then("misc.door_open", Animation.LoopType.HOLD_ON_LAST_FRAME))
                .triggerableAnim("misc.door_close", RawAnimation.begin().then("misc.door_close", Animation.LoopType.HOLD_ON_LAST_FRAME)));
        controllers.add(new AnimationController<>(this, "tiro", 0, state -> PlayState.STOP)
                .triggerableAnim("attack.fire", RawAnimation.begin().then("attack.fire", Animation.LoopType.PLAY_ONCE)));
        controllers.add(new AnimationController<>(this, "hangar", 0, state -> PlayState.STOP)
                .triggerableAnim("misc.hangar_open", RawAnimation.begin().then("misc.hangar_open", Animation.LoopType.HOLD_ON_LAST_FRAME))
                .triggerableAnim("misc.hangar_close", RawAnimation.begin().then("misc.hangar_close", Animation.LoopType.HOLD_ON_LAST_FRAME)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(tipo().width * 2.5);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 256 * 256;
    }
}
