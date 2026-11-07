package com.lanterna.construct.oficina;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.construct.Constructs;
import com.lanterna.particle.GlowOrbOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Um construto desenhado pelo jogador, no mundo. A forma (os cubinhos) vem sincronizada no próprio construto, então
 * todo mundo que está perto vê o mesmo desenho, na cor do anel de quem fez.
 */
public class ConstrutoProprio extends Entity {
    private static final EntityDataAccessor<CompoundTag> PROJETO = SynchedEntityData.defineId(ConstrutoProprio.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Optional<UUID>> DONO = SynchedEntityData.defineId(ConstrutoProprio.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> COR = SynchedEntityData.defineId(ConstrutoProprio.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VIDA = SynchedEntityData.defineId(ConstrutoProprio.class, EntityDataSerializers.INT);
    /** Tick (idade) do último golpe da arma (-100 = nenhum). */
    private static final EntityDataAccessor<Integer> GOLPE = SynchedEntityData.defineId(ConstrutoProprio.class, EntityDataSerializers.INT);
    /** Queda: altura do chão onde vai bater (para o anel de aviso). */
    private static final EntityDataAccessor<Float> ALVO_Y = SynchedEntityData.defineId(ConstrutoProprio.class, EntityDataSerializers.FLOAT);

    /** Montar e desmontar (ticks). */
    public static final int MONTAR = 10, DESMONTAR = 10;

    private Projeto projeto = new Projeto();
    private int idade;
    private float forca = 1f;
    private boolean bateu;
    private int golpeDano = -1;

    public ConstrutoProprio(EntityType<? extends ConstrutoProprio> type, Level level) {
        super(type, level);
        noCulling = true;
    }

    public static ConstrutoProprio criar(ServerPlayer dono, Projeto p, int cor, float forca) {
        ConstrutoProprio c = new ConstrutoProprio(OficinaRegistro.CONSTRUTO.get(), dono.level());
        c.entityData.set(PROJETO, p.salvar());
        c.entityData.set(DONO, Optional.of(dono.getUUID()));
        c.entityData.set(COR, cor);
        c.entityData.set(VIDA, p.comp.duracao);
        c.projeto = p;
        c.forca = forca;
        c.refreshDimensions();
        return c;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(PROJETO, new CompoundTag());
        entityData.define(DONO, Optional.empty());
        entityData.define(COR, 0x40FF70);
        entityData.define(VIDA, 200);
        entityData.define(GOLPE, -100);
        entityData.define(ALVO_Y, 0f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (PROJETO.equals(key)) {
            projeto = Projeto.ler(entityData.get(PROJETO));
            refreshDimensions();
        }
    }

    public Projeto projeto() { return projeto; }
    public Comportamento comp() { return projeto.comp; }
    public int cor() { return entityData.get(COR); }
    public int idade() { return idade; }
    public int vida() { return entityData.get(VIDA); }
    public int golpe() { return entityData.get(GOLPE); }
    public float alvoY() { return entityData.get(ALVO_Y); }

    @Nullable
    public UUID donoId() {
        return entityData.get(DONO).orElse(null);
    }

    @Nullable
    public LivingEntity dono() {
        UUID id = donoId();
        if (id == null) return null;
        Player p = level().getPlayerByUUID(id);
        return p;
    }

    // ===================== Tamanho =====================

    /** Caixa do desenho no espaço do construto (sem girar): {x0,y0,z0,x1,y1,z1} em blocos. */
    public float[] caixaLocal() {
        int[] b = projeto.limites();
        float k = projeto.bloco();
        if (b == null) return new float[]{-0.25f, 0, -0.25f, 0.25f, 0.5f, 0.25f};
        int m = Projeto.MEIO;
        return new float[]{(b[0] - m) * k, b[1] * k, (b[2] - m) * k, (b[3] + 1 - m) * k, (b[4] + 1) * k, (b[5] + 1 - m) * k};
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (projeto == null || projeto.vazio() || (comp() != Comportamento.VEICULO && comp() != Comportamento.ESTRUTURA))
            return EntityDimensions.scalable(0.6f, 0.6f);
        float[] c = caixaLocal();
        float w = Math.max(c[3] - c[0], c[5] - c[2]);
        if (comp() == Comportamento.VEICULO) w = Math.min(w, 4f);
        return EntityDimensions.scalable(Mth.clamp(w, 0.6f, 6f), Mth.clamp(c[4] - c[1], 0.4f, 6f));
    }

    // ===================== Tick =====================

    @Override
    public void tick() {
        super.tick();
        idade++;
        if (projeto.vazio() && !level().isClientSide && idade > 2) { discard(); return; }
        LivingEntity dono = dono();
        switch (comp()) {
            case ARMA -> segurar(dono);
            case ESCUDO -> flutuarNaFrente(dono);
            case PROJETIL -> voar(dono);
            case QUEDA -> cair(dono);
            case VEICULO -> rodar();
            case ESTRUTURA -> firme(dono);
        }
        if (!level().isClientSide) {
            if (dono == null && comp() != Comportamento.QUEDA && comp() != Comportamento.PROJETIL) { acabar(); }
            if (idade >= vida() + DESMONTAR) {
                ejectPassengers();
                discard();
            } else if (idade == vida()) {
                ejectPassengers();
                level().playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.8f, 1.4f);
            }
        } else {
            if (idade < MONTAR && idade % 2 == 0) {
                float[] c = caixaLocal();
                level().addParticle(GlowOrbOptions.of(cor(), 0.08f), getX(), getY() + (c[4] + c[1]) / 2, getZ(), 0, 0.02, 0);
            }
            if (comp() == Comportamento.QUEDA && pousou < 0 && getY() <= alvoY() + 0.01) pousou = idade;
            // desmontar: estilhaça em faíscas que se espalham e sobem
            if (!estilhacou && idade >= vida()) {
                estilhacou = true;
                float[] c = caixaLocal();
                int n = Mth.clamp((int) ((c[3] - c[0]) * (c[4] - c[1]) * (c[5] - c[2]) * 30), 10, 40);
                for (int i = 0; i < n; i++) {
                    double x = Mth.lerp(random.nextDouble(), c[0], c[3]), y = Mth.lerp(random.nextDouble(), c[1], c[4]), z = Mth.lerp(random.nextDouble(), c[2], c[5]);
                    double cx = (c[0] + c[3]) / 2, cy = (c[1] + c[4]) / 2, cz = (c[2] + c[5]) / 2;
                    level().addParticle(GlowOrbOptions.of(cor(), 0.05f + random.nextFloat() * 0.06f), getX() + x, getY() + y, getZ() + z,
                            (x - cx) * 0.12, (y - cy) * 0.1 + 0.03, (z - cz) * 0.12);
                }
            }
        }
    }

    /** Só no cliente: quando a queda bateu no chão (para a onda de choque) e se já estilhaçou. */
    public float pousou = -1;
    private boolean estilhacou;

    /** Termina já (desmonta). */
    public void acabar() {
        if (idade < vida()) entityData.set(VIDA, idade);
    }

    public boolean desmontando() {
        return idade >= vida();
    }

    // ----- Arma -----

    /** Onde a mão segura: um pouco à direita e à frente do peito. */
    public static Vec3 mao(LivingEntity d, float pt) {
        float yaw = (float) Math.toRadians(Mth.rotLerp(pt, d.yBodyRotO, d.yBodyRot));
        Vec3 frente = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 direita = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        return d.getPosition(pt).add(0, d.getBbHeight() * 0.48, 0).add(direita.scale(0.38)).add(frente.scale(0.42));
    }

    private void segurar(@Nullable LivingEntity d) {
        if (d == null) return;
        Vec3 m = mao(d, 1f);
        setPos(m.x, m.y, m.z);
        setYRot(d.getYHeadRot());
        setXRot(d.getXRot());
        if (!level().isClientSide && golpeDano >= 0 && idade >= golpeDano) {
            golpeDano = -1;
            acertarGolpe(d);
        }
    }

    /** Clique esquerdo com a arma: golpe em arco na frente. */
    public void golpear() {
        if (comp() != Comportamento.ARMA || desmontando() || idade - golpe() < 9) return;
        entityData.set(GOLPE, idade);
        golpeDano = idade + 3;
        LivingEntity d = dono();
        if (d != null) {
            d.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            level().playSound(null, d.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 0.7f);
            level().playSound(null, d.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 0.6f);
        }
    }

    private void acertarGolpe(LivingEntity d) {
        float[] c = caixaLocal();
        double alcance = Mth.clamp(1.6 + (c[4] - c[1]) * 1.1, 2.2, 5.5);
        Vec3 olho = d.getEyePosition();
        Vec3 olhar = d.getLookAngle();
        float dano = 6f * forca * (float) Mth.clamp(0.8 + (c[4] - c[1]) * 0.25, 0.8, 1.6);
        int n = 0;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, d.getBoundingBox().inflate(alcance))) {
            if (e == d || !e.isAlive() || e.isAlliedTo(d)) continue;
            Vec3 para = e.getBoundingBox().getCenter().subtract(olho);
            if (para.length() > alcance + e.getBbWidth() / 2) continue;
            if (para.normalize().dot(olhar) < 0.25) continue;
            if (e.hurt(dano(d), dano)) {
                n++;
                Vec3 kb = para.multiply(1, 0, 1).normalize().scale(0.6);
                e.push(kb.x, 0.25, kb.z);
            }
        }
        if (level() instanceof ServerLevel sl) {
            for (int i = -4; i <= 4; i++) {
                Vec3 v = olhar.yRot((float) Math.toRadians(i * 12)).scale(alcance * 0.8);
                sl.sendParticles(GlowOrbOptions.of(cor(), 0.12f), olho.x + v.x, olho.y + v.y - 0.3, olho.z + v.z, 1, 0, 0, 0, 0);
            }
            if (n > 0) sl.playSound(null, d.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1f, 0.8f);
        }
    }

    private DamageSource dano(LivingEntity d) {
        return d instanceof Player p ? damageSources().playerAttack(p) : damageSources().mobAttack(d);
    }

    // ----- Escudo -----

    private void flutuarNaFrente(@Nullable LivingEntity d) {
        if (d == null) return;
        Vec3 olhar = d.getLookAngle();
        float[] c = caixaLocal();
        Vec3 p = d.getEyePosition().add(olhar.scale(1.3 + (c[5] - c[2]) * 0.3)).subtract(0, (c[4] - c[1]) / 2, 0);
        setPos(p.x, p.y, p.z);
        setYRot(d.getYHeadRot());
        setXRot(d.getXRot());
        if (!level().isClientSide && !desmontando()) {
            AABB zona = new AABB(p, p).inflate(Math.max(c[3] - c[0], c[4] - c[1]) / 2 + 0.6);
            for (Projectile pr : level().getEntitiesOfClass(Projectile.class, zona.move(0, (c[4] - c[1]) / 2, 0))) {
                if (pr.getOwner() == d) continue;
                ((ServerLevel) level()).sendParticles(GlowOrbOptions.of(cor(), 0.12f), pr.getX(), pr.getY(), pr.getZ(), 6, 0.1, 0.1, 0.1, 0.05);
                level().playSound(null, pr.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.3f);
                pr.discard();
            }
        }
    }

    /** Dano que vem da frente quando o escudo está de pé (chamado pelo evento de dano). */
    public boolean protegeDe(Vec3 origem) {
        LivingEntity d = dono();
        if (d == null || desmontando() || idade < MONTAR / 2) return false;
        Vec3 para = origem.subtract(d.getEyePosition()).normalize();
        return para.dot(d.getLookAngle()) > 0.25;
    }

    // ----- Projétil -----

    public void lancar(LivingEntity d) {
        Vec3 olhar = d.getLookAngle();
        Vec3 p = d.getEyePosition().add(olhar.scale(1.2)).subtract(0, 0.3, 0);
        setPos(p.x, p.y, p.z);
        setDeltaMovement(olhar.scale(1.7));
        setYRot(d.getYHeadRot());
        setXRot(d.getXRot());
    }

    private void voar(@Nullable LivingEntity d) {
        if (desmontando()) { setDeltaMovement(getDeltaMovement().scale(0.5)); }
        Vec3 v = getDeltaMovement();
        Vec3 de = position(), ate = de.add(v);
        if (!level().isClientSide && !bateu && idade > 1) {
            HitResult bloco = level().clip(new ClipContext(de, ate, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            float[] c = caixaLocal();
            double raio = Math.max(0.35, Math.max(c[3] - c[0], c[4] - c[1]) * 0.35);
            LivingEntity alvo = null;
            double melhor = Double.MAX_VALUE;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(de, ate).inflate(raio + 1))) {
                if (e == d || !e.isAlive() || (d != null && e.isAlliedTo(d))) continue;
                Optional<Vec3> h = e.getBoundingBox().inflate(raio).clip(de, ate);
                if (h.isPresent() && h.get().distanceToSqr(de) < melhor) { melhor = h.get().distanceToSqr(de); alvo = e; }
            }
            if (alvo != null) {
                impacto(d, alvo.position().add(0, alvo.getBbHeight() / 2, 0), alvo, 9f * forca, 1.8);
            } else if (bloco.getType() != HitResult.Type.MISS) {
                impacto(d, bloco.getLocation(), null, 0, 1.8);
            }
        }
        if (!bateu) setPos(ate.x, ate.y, ate.z);
        if (level().isClientSide && idade % 1 == 0)
            level().addParticle(GlowOrbOptions.of(cor(), 0.06f), getX(), getY(), getZ(), 0, 0, 0);
    }

    private void impacto(@Nullable LivingEntity d, Vec3 onde, @Nullable LivingEntity direto, float danoDireto, double area) {
        bateu = true;
        setDeltaMovement(Vec3.ZERO);
        setPos(onde.x, onde.y, onde.z);
        if (direto != null && danoDireto > 0) direto.hurt(d != null ? dano(d) : damageSources().magic(), danoDireto);
        float danoArea = (comp() == Comportamento.QUEDA ? 16f : 4f) * forca;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(onde, onde).inflate(area))) {
            if (e == d || e == direto || !e.isAlive() || (d != null && e.isAlliedTo(d))) continue;
            double dist = e.position().distanceTo(onde);
            if (dist > area) continue;
            float f = (float) (1 - dist / (area + 0.5));
            e.hurt(d != null ? dano(d) : damageSources().magic(), danoArea * Math.max(0.3f, f));
            Vec3 kb = e.position().subtract(onde).multiply(1, 0, 1).normalize().scale(0.5 + f);
            e.push(kb.x, 0.3 + f * 0.5, kb.z);
        }
        if (level() instanceof ServerLevel sl) {
            boolean grande = comp() == Comportamento.QUEDA;
            sl.sendParticles(grande ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, onde.x, onde.y, onde.z, 1, 0, 0, 0, 0);
            sl.sendParticles(GlowOrbOptions.of(cor(), grande ? 0.3f : 0.15f), onde.x, onde.y + 0.2, onde.z, grande ? 60 : 18, area * 0.4, 0.3, area * 0.4, 0.15);
            if (grande) for (int i = 0; i < 36; i++) {
                double a = i * Math.PI * 2 / 36;
                sl.sendParticles(ParticleTypes.CLOUD, onde.x + Math.cos(a) * area * 0.8, onde.y + 0.2, onde.z + Math.sin(a) * area * 0.8, 1, 0, 0, 0, 0.05);
            }
            sl.playSound(null, onde.x, onde.y, onde.z, grande ? SoundEvents.GENERIC_EXPLODE : SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, grande ? 2.5f : 1f, grande ? 0.7f : 1.2f);
        }
        acabar();
    }

    // ----- Queda do céu -----

    public void preparaQueda(LivingEntity d) {
        Vec3 olho = d.getEyePosition();
        HitResult h = level().clip(new ClipContext(olho, olho.add(d.getLookAngle().scale(48)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, d));
        Vec3 alvo = h.getLocation();
        int chao = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(alvo.x), Mth.floor(alvo.z));
        double y = h.getType() == HitResult.Type.MISS ? chao : Math.min(alvo.y, chao);
        entityData.set(ALVO_Y, (float) y);
        setPos(alvo.x, y + 26, alvo.z);
        setYRot(d.getYHeadRot());
        setDeltaMovement(0, -0.2, 0);
    }

    private void cair(@Nullable LivingEntity d) {
        if (bateu) return;
        if (idade < MONTAR) return; // monta lá em cima antes de cair
        Vec3 v = getDeltaMovement();
        double vy = Math.max(-2.6, v.y - 0.16);
        setDeltaMovement(0, vy, 0);
        double ny = getY() + vy;
        if (!level().isClientSide) {
            if (idade % 3 == 0 && level() instanceof ServerLevel sl) {
                float[] c = caixaLocal();
                double r = Math.max(1.5, (c[3] - c[0]) * 0.6);
                for (int i = 0; i < 16; i++) {
                    double a = i * Math.PI / 8 + idade * 0.1;
                    sl.sendParticles(GlowOrbOptions.of(cor(), 0.07f), getX() + Math.cos(a) * r, alvoY() + 0.1, getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
            if (ny <= alvoY()) {
                float[] c = caixaLocal();
                double area = Mth.clamp(1.8 + Math.max(c[3] - c[0], c[5] - c[2]) * 0.55, 2.5, 7);
                impacto(d, new Vec3(getX(), alvoY(), getZ()), null, 0, area);
                setPos(getX(), alvoY(), getZ());
                return;
            }
        }
        setPos(getX(), Math.max(ny, alvoY()), getZ());
    }

    // ----- Veículo -----

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        Entity p = getFirstPassenger();
        return p instanceof LivingEntity l ? l : null;
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return comp() == Comportamento.VEICULO && getPassengers().isEmpty();
    }

    @Override
    public double getPassengersRidingOffset() {
        float[] c = caixaLocal();
        return Math.max(0.3, (c[4] - c[1]) * 0.55);
    }

    @Override
    public boolean isPickable() {
        return comp() == Comportamento.VEICULO && !isRemoved();
    }

    @Override
    public net.minecraft.world.InteractionResult interact(Player p, net.minecraft.world.InteractionHand hand) {
        if (comp() == Comportamento.VEICULO && p.getUUID().equals(donoId()) && !p.isPassenger() && !desmontando()) {
            if (!level().isClientSide) p.startRiding(this);
            return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
        }
        return net.minecraft.world.InteractionResult.PASS;
    }

    private float velocidade;

    private void rodar() {
        if (isControlledByLocalInstance()) {
            LivingEntity m = getControllingPassenger();
            float alvoVel = 0;
            if (m != null && !desmontando()) {
                float yaw = Mth.rotLerp(0.25f, getYRot(), m.getYRot());
                setYRot(yaw);
                yRotO = yaw;
                alvoVel = m.zza > 0 ? 0.85f : m.zza < 0 ? -0.3f : 0;
            }
            velocidade += Mth.clamp(alvoVel - velocidade, -0.05f, 0.04f);
            float rad = (float) Math.toRadians(getYRot());
            Vec3 frente = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
            // flutua a ~1 bloco do chão
            int chao = level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(getX()), Mth.floor(getZ()));
            double alvoY = chao + 0.6;
            double vy = Mth.clamp((alvoY - getY()) * 0.25, -0.5, 0.45);
            setDeltaMovement(frente.scale(velocidade).add(0, vy, 0));
            move(MoverType.SELF, getDeltaMovement());
            if (horizontalCollision) velocidade *= 0.5f;
        }
        LivingEntity m = getControllingPassenger();
        double andou = Math.sqrt((getX() - xo) * (getX() - xo) + (getZ() - zo) * (getZ() - zo));
        if (!level().isClientSide && andou > 0.45 && m != null) {
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.3))) {
                if (e == m || e.isAlliedTo(m) || hasPassenger(e)) continue;
                if (e.hurt(dano(m), 4f * forca)) {
                    Vec3 kb = e.position().subtract(position()).multiply(1, 0, 1).normalize().scale(0.9);
                    e.push(kb.x, 0.4, kb.z);
                }
            }
        }
        if (level().isClientSide && Math.abs(velocidade) > 0.2f && idade % 2 == 0)
            level().addParticle(GlowOrbOptions.of(cor(), 0.05f), getX(), getY() + 0.05, getZ(), 0, 0, 0);
    }

    // ----- Estrutura -----

    public void colocar(LivingEntity d) {
        Vec3 olho = d.getEyePosition();
        HitResult h = level().clip(new ClipContext(olho, olho.add(d.getLookAngle().scale(20)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, d));
        Vec3 alvo = h.getType() == HitResult.Type.MISS ? olho.add(d.getLookAngle().scale(6)) : h.getLocation();
        int chao = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(alvo.x), Mth.floor(alvo.z));
        setPos(alvo.x, Math.min(Math.max(alvo.y, chao), alvo.y + 3), alvo.z);
        setYRot(d.getYHeadRot());
    }

    private void firme(@Nullable LivingEntity d) {
        if (level().isClientSide || desmontando()) return;
        AABB caixa = getBoundingBox();
        for (Projectile pr : level().getEntitiesOfClass(Projectile.class, caixa.inflate(0.4))) {
            if (d != null && pr.getOwner() == d) continue;
            ((ServerLevel) level()).sendParticles(GlowOrbOptions.of(cor(), 0.1f), pr.getX(), pr.getY(), pr.getZ(), 5, 0.1, 0.1, 0.1, 0.05);
            pr.discard();
        }
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, caixa)) {
            if (e == d || (d != null && e.isAlliedTo(d))) continue;
            Vec3 fora = e.position().subtract(position()).multiply(1, 0, 1);
            if (fora.lengthSqr() < 1e-4) fora = new Vec3(1, 0, 0);
            fora = fora.normalize().scale(0.35);
            e.push(fora.x, 0.05, fora.z);
            e.hurtMarked = true;
        }
    }

    // ===================== Gravação =====================

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        // construtos não sobrevivem a recarregar o mundo
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    public void setForca(float f) {
        forca = f;
    }

    /** Lista de construtos ativos de um dono (para a arma, o escudo e o limite). */
    public static List<ConstrutoProprio> doDono(Level l, UUID dono, AABB area) {
        return l.getEntitiesOfClass(ConstrutoProprio.class, area, c -> dono.equals(c.donoId()) && !c.desmontando());
    }
}
