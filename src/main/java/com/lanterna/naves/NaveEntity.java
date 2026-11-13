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
    private final NavePart[] parts = new NavePart[MAX_PARTES];
    /** Caixas de colisão: cada peça do modelo é dividida em pedaços menores quando a nave está girada. */
    public static final int MAX_PARTES = 64;
    private boolean portaAberta;
    private int fireCooldown;
    /** Chamado pela chave: ponto de pouso e de onde veio (a nave desce sozinha, entrando na atmosfera). */
    @Nullable private Vec3 pouso, partida;
    private int chamadaTicks, chamadaTotal;
    private boolean somTurbo;
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

    /** As caixas da nave no mundo (uma por peça girada; em ângulo, cada peça vira uma grade de caixas menores). */
    public static List<AABB> caixas(NaveTipo t, double px, double py, double pz, float yawGraus, boolean dividir) {
        List<AABB> out = new java.util.ArrayList<>();
        double yaw = Math.toRadians(yawGraus);
        double cos = Math.cos(yaw), sin = Math.sin(yaw);
        float yawMod = Math.abs(Mth.wrapDegrees(yawGraus)) % 90f;
        boolean alinhada = Math.min(yawMod, 90f - yawMod) < 4f;
        for (double[] c : t.colisao) {
            double x0 = c[0] / 16, z0 = c[2] / 16, w = c[3] / 16, d = c[5] / 16;
            double y0 = py + c[1] / 16, y1 = y0 + c[4] / 16;
            // pedaços quase quadrados (a caixa alinhada de um quadrado girado cresce pouco); no máximo 8 por peça
            int nx = 1, nz = 1;
            if (dividir && !alinhada) {
                double lado = Math.max(1.25, Math.sqrt(w * d / 8.0));
                nx = Math.max(1, (int) Math.round(w / lado));
                nz = Math.max(1, (int) Math.round(d / lado));
                while (nx * nz > 8) { if (nx >= nz) nx--; else nz--; }
            }
            for (int i = 0; i < nx; i++) for (int k = 0; k < nz; k++) {
                double a0 = x0 + w * i / nx, a1 = x0 + w * (i + 1) / nx, b0 = z0 + d * k / nz, b1 = z0 + d * (k + 1) / nz;
                double minX = 1e9, maxX = -1e9, minZ = 1e9, maxZ = -1e9;
                for (double[] q : new double[][]{{a0, b0}, {a1, b0}, {a0, b1}, {a1, b1}}) {
                    // modelo: +x = esquerda, -z = frente  ->  mundo
                    double lx = q[0], fz = -q[1];
                    double wx = -sin * fz + cos * lx, wz = cos * fz + sin * lx;
                    minX = Math.min(minX, wx); maxX = Math.max(maxX, wx);
                    minZ = Math.min(minZ, wz); maxZ = Math.max(maxZ, wz);
                }
                if (!alinhada && nx * nz > 1) {
                    // tira a sobra dos cantos: encolhe o pedaço girado até a área ficar perto da real
                    double ex = maxX - minX, ez = maxZ - minZ, real = (a1 - a0) * (b1 - b0);
                    double k2 = Math.sqrt(Math.max(0.5, real / (ex * ez)));
                    double cx = (minX + maxX) / 2, cz = (minZ + maxZ) / 2;
                    double hx = ex * Math.max(0.72, k2) / 2, hz = ez * Math.max(0.72, k2) / 2;
                    minX = cx - hx; maxX = cx + hx; minZ = cz - hz; maxZ = cz + hz;
                }
                out.add(new AABB(px + minX, y0, pz + minZ, px + maxX, Math.max(y1, y0 + 0.3), pz + maxZ));
            }
        }
        return out;
    }

    private void updateParts() {
        List<AABB> cx = caixas(tipo(), getX(), getY(), getZ(), getYRot(), true);
        for (int i = 0; i < parts.length; i++) {
            NavePart p = parts[i];
            if (i >= cx.size()) {
                p.resize(0.1f, 0.1f);
                p.setPos(getX(), getY(), getZ());
            } else {
                AABB b = cx.get(i);
                p.resize(Math.max(0.3, b.getXsize()), Math.max(0.3, b.getZsize()), (float) Math.max(0.3, b.getYsize()));
                p.setPos((b.minX + b.maxX) / 2, b.minY, (b.minZ + b.maxZ) / 2);
            }
            p.xo = p.getX(); p.yo = p.getY(); p.zo = p.getZ();
            p.xOld = p.getX(); p.yOld = p.getY(); p.zOld = p.getZ();
        }
    }

    /** Se uma caixa (de um jogador, por exemplo) bate em alguma parte da nave. */
    public boolean bateNaNave(AABB box) {
        for (NavePart p : parts) if (p.canBeCollidedWith() && p.getBoundingBox().intersects(box)) return true;
        return false;
    }

    /** Até onde a nave vai a partir do centro (para achar um lugar livre do lado de fora). */
    public double alcance() {
        double r = tipo().width / 2;
        for (NavePart p : parts) if (p.isPickable()) {
            AABB b = p.getBoundingBox();
            r = Math.max(r, Math.max(Math.max(Math.abs(b.minX - getX()), Math.abs(b.maxX - getX())), Math.max(Math.abs(b.minZ - getZ()), Math.abs(b.maxZ - getZ()))));
        }
        return r;
    }

    /** Topo da nave (a parte mais alta). */
    public double topo() {
        double y = getY() + tipo().height;
        for (NavePart p : parts) if (p.isPickable()) y = Math.max(y, p.getBoundingBox().maxY);
        return y;
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

    // ===================== Chamada pela chave =====================

    /** Começa a descida: a nave aparece em "de" e pousa em "em" (chamada pela chave do dono). */
    public void chamar(Vec3 de, Vec3 em) {
        partida = de;
        pouso = em;
        chamadaTicks = 0;
        Vec3 d = em.subtract(de);
        chamadaTotal = (int) Math.max(120, Math.min(220, d.length() * 1.2));
        setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        moveTo(de.x, de.y, de.z, getYRot(), 0);
        noPhysics = true;
        level().playSound(null, em.x, em.y, em.z, com.lanterna.registry.ModSounds.NAVE_REENTRADA.get(), SoundSource.NEUTRAL, 6f, 1f);
    }

    public boolean chegando() { return pouso != null; }

    /**
     * Reentrada: desce em curva (rápido no alto, freando perto do chão), envolta em fogo e fumaça enquanto está alta,
     * e pousa no ponto marcado.
     */
    private void chegadaTick(NaveTipo t) {
        chamadaTicks++;
        double f = Math.min(1, chamadaTicks / (double) chamadaTotal);
        double e = 1 - Math.pow(1 - f, 3); // freia no fim
        Vec3 alvo = partida.lerp(pouso, e);
        // a curva: chega por cima, não em linha reta
        double arco = Math.sin(f * Math.PI) * 6;
        Vec3 p = new Vec3(alvo.x, alvo.y + arco, alvo.z);
        Vec3 v = p.subtract(position());
        setDeltaMovement(v);
        setPos(p.x, p.y, p.z);
        noPhysics = f < 0.85;
        ServerLevel sl = (ServerLevel) level();
        double alto = getY() - pouso.y;
        if (alto > 12) {
            double w = t.width * 0.6;
            sl.sendParticles(ParticleTypes.FLAME, getX(), getY() + t.height * 0.3, getZ(), 12, w, t.height * 0.4, w, 0.05);
            sl.sendParticles(ParticleTypes.LAVA, getX(), getY(), getZ(), 2, w, 0.4, w, 0);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX() - v.x * 4, getY() - v.y * 4, getZ() - v.z * 4, 8, w, 0.5, w, 0.02);
        } else if (chamadaTicks % 2 == 0) {
            sl.sendParticles(ParticleTypes.CLOUD, getX(), pouso.y + 0.2, getZ(), 6, t.width * 0.6, 0.05, t.width * 0.6, 0.06);
        }
        if (f >= 1) {
            setPos(pouso.x, pouso.y, pouso.z);
            setDeltaMovement(Vec3.ZERO);
            noPhysics = false;
            pouso = null;
            partida = null;
            sl.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 2, t.width * 0.4, 0.1, t.width * 0.4, 0);
            sl.playSound(null, blockPosition(), com.lanterna.registry.ModSounds.NAVE_POUSAR.get(), SoundSource.NEUTRAL, 2f, 0.9f);
            abrirPorta(true);
            NaveInterior.lembrarPosicao(this);
        }
    }

    // ===================== Queda =====================

    /**
     * A nave não sofre (nem repassa) dano de queda. O vanilla acumula a "altura de queda" de qualquer veículo que desce
     * sem tocar o chão e, ao pousar, aplica o dano em quem está sentado: descendo devagar de 60 blocos dava dano de
     * 60 blocos no jogador.
     */
    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double dy, boolean onGround, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos) {
        fallDistance = 0;
    }

    // ===================== Tick =====================

    @Override
    public void tick() {
        super.tick();
        NaveTipo t = tipo();
        boolean espaco = level().dimension().equals(SpaceBodies.SPACE);
        if (fireCooldown > 0) fireCooldown--;

        noPhysics = espaco && !t.destrocos();
        if (!level().isClientSide && pouso != null) {
            chegadaTick(t);
        } else if (isControlledByLocalInstance()) {
            lerpSteps = 0;
            // sóis e buracos negros puxam a nave de quem pilota (o servidor puxa as sem piloto)
            if (espaco && !t.destrocos() && getControllingPassenger() instanceof Player)
                setDeltaMovement(getDeltaMovement().add(com.lanterna.space.Perigos.puxao(position())));
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
        fallDistance = 0;
        for (Entity e : getPassengers()) e.fallDistance = 0;

        if (!level().isClientSide) {
            entityData.set(POUSADA, onGround() || t.destrocos());
            if (getControllingPassenger() == null) setTurbo(false);
            if (aiLife > 0 && --aiLife == 0) {
                despawn();
                return;
            }
            aiTimer++;
            NaveViagem.check(this);
            if (tickCount % 40 == 0 && pouso == null) NaveInterior.lembrarPosicao(this);
            if (tickCount % 20 == 0 && !t.destrocos() && hp() < t.maxHp * 0.3f) {
                ((ServerLevel) level()).sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + t.height, getZ(), 3, t.width / 3, 0.3, t.width / 3, 0.01);
            }
        } else {
            tickAnimacao();
            tickInclinacao();
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
        // Curva com embalo: vira mais rápido no começo e assenta suave no rumo (naves grandes viram mais devagar)
        float turnMax = 7f / (1f + t.width * 0.3f);
        float diff = Mth.wrapDegrees(pilot.getYRot() - getYRot());
        float step = Mth.clamp(diff * 0.22f, -turnMax, turnMax);
        setYRot(getYRot() + step);
        float boostMul = in.boost() ? (espaco ? 6f : 2.2f) : 1f;
        double max = t.speed * boostMul;

        // Velocidade desejada (para onde o nariz aponta) — a nave "persegue" ela aos poucos:
        // acelera e freia com inércia, e nas curvas o embalo escorrega um pouco antes de alinhar
        Vec3 alvo = Vec3.ZERO;
        if (in.forward() != 0) {
            Vec3 dir = Vec3.directionFromRotation(Mth.clamp(pilot.getXRot(), -50, 50), getYRot());
            alvo = alvo.add(dir.scale(in.forward() > 0 ? max * in.forward() : max * 0.4 * in.forward()));
        }
        if (in.strafe() != 0) alvo = alvo.add(Vec3.directionFromRotation(0, getYRot() - 90).scale(in.strafe() * max * 0.45));
        if (in.up()) alvo = alvo.add(0, max * 0.55, 0);
        if (in.down()) alvo = alvo.add(0, -max * 0.55, 0);
        Vec3 v = getDeltaMovement();
        double ganho = in.forward() != 0 || in.up() || in.down() || in.strafe() != 0
                ? (in.boost() ? 0.1 : 0.08)
                : (espaco ? 0.015 : 0.06);
        // horizontal mais "pesado" que o vertical: parece uma nave com massa
        v = new Vec3(Mth.lerp(ganho, v.x, alvo.x), Mth.lerp(ganho * 1.4, v.y, alvo.y), Mth.lerp(ganho, v.z, alvo.z));
        // Parada sem nada apertado no ar (fora do espaço): flutua com um balanço leve
        if (!espaco && alvo.lengthSqr() == 0 && !onGround()) v = v.add(0, Mth.sin(tickCount * 0.08f) * 0.004, 0);
        if (onGround() && in.forward() == 0 && !in.up()) v = new Vec3(v.x * 0.6, Math.min(0, v.y), v.z * 0.6);
        setDeltaMovement(v);
        move(MoverType.SELF, v);
    }

    // ===================== Inclinação visual (só no cliente) =====================

    private float visRoll, visRollO, visPitch, visPitchO;

    /** Inclina de lado nas curvas e levanta/baixa o nariz ao subir/descer. */
    private void tickInclinacao() {
        visRollO = visRoll;
        visPitchO = visPitch;
        float alvoRoll = 0, alvoPitch = 0;
        if (!pousada() && !tipo().destrocos()) {
            float yawRate = Mth.wrapDegrees(getYRot() - yRotO);
            double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
            double hs = Math.sqrt(dx * dx + dz * dz);
            float fator = (float) Mth.clamp(hs / 0.5, 0.35, 1.3);
            float limite = tipo().width > 4 ? 14f : 22f;
            alvoRoll = Mth.clamp(-yawRate * 3.2f * fator, -limite, limite);
            alvoPitch = (float) Mth.clamp(Math.toDegrees(Math.atan2(dy, Math.max(hs, 0.25))) * 0.55, -16, 16);
        }
        visRoll += (alvoRoll - visRoll) * 0.12f;
        visPitch += (alvoPitch - visPitch) * 0.1f;
    }

    public float visRoll(float pt) { return Mth.lerp(pt, visRollO, visRoll); }
    public float visPitch(float pt) { return Mth.lerp(pt, visPitchO, visPitch); }

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
        Vec3 p = level().isClientSide
                ? TemNave.posicaoAssento(position(), getYRot(), s[0], s[1], s[2], visPitch(1f), visRoll(1f), getBbHeight() * 0.5)
                : TemNave.posicaoAssento(position(), getYRot(), s[0], s[1], s[2]);
        move.accept(passenger, p.x, p.y + passenger.getMyRidingOffset(), p.z);
        if (passenger instanceof LanternaEntity npc) {
            npc.setYRot(getYRot());
            npc.setYBodyRot(getYRot());
            npc.setYHeadRot(getYRot());
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 v = lugarLivreFora(passenger.getDimensions(net.minecraft.world.entity.Pose.STANDING), true);
        return v != null ? v : super.getDismountLocationForPassenger(passenger);
    }

    /**
     * Um lugar do lado de fora da nave para alguém do tamanho dado: primeiro do lado da porta (esquerda), depois a
     * direita, atrás e na frente; no chão se tiver chão perto, senão no ar ao lado. Último recurso: em cima da nave.
     */
    @org.jetbrains.annotations.Nullable
    public Vec3 lugarLivreFora(EntityDimensions dim, boolean emCimaSeNada) {
        updateParts();
        double alc = alcance();
        double[] porta = tipo().porta;
        float lado = porta != null && porta[0] < 0 ? -90 : 90;
        float[] dirs = {lado, -lado, 180, 0, lado + 45, -lado - 45, lado + 135, -lado - 135};
        Vec3 noAr = null;
        for (float dir : dirs) {
            Vec3 d = Vec3.directionFromRotation(0, getYRot() + dir);
            for (double dist = 1.0; dist <= alc + 3.0; dist += 0.5) {
                double x = getX() + d.x * dist, z = getZ() + d.z * dist;
                // procura chão de cima da nave até alguns blocos abaixo dela
                for (double y0 = Math.floor(getY() + 2); y0 >= getY() - 6; y0 -= 1) {
                    net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(x, y0, z);
                    double h = level().getBlockFloorHeight(bp);
                    if (!net.minecraft.world.entity.vehicle.DismountHelper.isBlockFloorValid(h)) continue;
                    Vec3 p = new Vec3(x, bp.getY() + h, z);
                    AABB box = dim.makeBoundingBox(p);
                    if (level().noCollision(box) && !bateNaNave(box.inflate(0.05))) return p;
                }
                if (noAr == null) {
                    Vec3 p = new Vec3(x, getY() + 0.1, z);
                    AABB box = dim.makeBoundingBox(p);
                    if (level().noCollision(box) && !bateNaNave(box.inflate(0.1))) noAr = p;
                }
            }
        }
        if (noAr != null) return noAr;
        if (!emCimaSeNada) return null;
        return new Vec3(getX(), topo() + 0.05, getZ());
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
        level().playSound(null, blockPosition(), com.lanterna.registry.ModSounds.NAVE_PORTA.get(), SoundSource.NEUTRAL, 0.9f, open ? 1.0f : 0.85f);
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
        level().playSound(null, getX(), getY(), getZ(), com.lanterna.registry.ModSounds.NAVE_TIRO.get(), SoundSource.NEUTRAL, 1.2f, 0.9f + random.nextFloat() * 0.2f);
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
        NaveInterior.esquecerPosicao(sl.getServer(), getUUID());
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
        if (animNoAr && animChao > 4) {
            animNoAr = false; animTroca = tickCount < 10 ? -100 : tickCount;
            if (animTroca > 0) level().playLocalSound(getX(), getY(), getZ(), com.lanterna.registry.ModSounds.NAVE_POUSAR.get(), SoundSource.NEUTRAL, 1.2f, 1f, false);
        } else if (!animNoAr && animAr > 4) {
            animNoAr = true; animTroca = tickCount < 10 ? -100 : tickCount;
            if (animTroca > 0) level().playLocalSound(getX(), getY(), getZ(), com.lanterna.registry.ModSounds.NAVE_DECOLAR.get(), SoundSource.NEUTRAL, 1.2f, 1f, false);
        }
        if (turbo() && !somTurbo) level().playLocalSound(getX(), getY(), getZ(), com.lanterna.registry.ModSounds.NAVE_TURBO.get(), SoundSource.NEUTRAL, 1.3f, 1f, false);
        somTurbo = turbo();
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
