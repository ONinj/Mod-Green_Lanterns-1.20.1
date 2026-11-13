package com.lanterna.lore.entity.boss;

import com.lanterna.corps.BuiltinCorps;
import com.lanterna.lore.LoreItems;
import com.lanterna.lore.PlanetLore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Os Bosses das Tropas: oito criaturas do mod, uma por emoção, cada uma no planeta da sua Tropa. Não substituem os
 * bosses dos planetas: depois que o boss do planeta cai, o Altar do Desafio chama também o Boss da Tropa (agachado,
 * com 8 minérios).
 *
 *   Karvhul, o Forjador Rubro (Fúria, Ysmault)        Vesk Morrath, o Carcereiro (Medo, Korugar)
 *   Ghor'Maddu, a Arca Viva (Avareza, Okaara)         Seraphel do Vitral (Esperança, Odym)
 *   Auun e Iel, os Gêmeos Velados (Compaixão, Nok)    Rainha Calithe do Espelho (Amor, Zamaron)
 *   Morthaal, o Coletor de Lanternas (Morte, Ryut)    Verdan, o Arquiteto (Vontade, Oa)
 *
 * Animações (GeckoLib) em três controladores: "move" (misc.idle / move.walk), "attack" (attack.*, disparados) e
 * "estado" (misc.*: os de uma vez são disparados; os de fase/postura ficam em loop enquanto valem).
 * misc.death toca ao morrer e o corpo fica deitado 2 s antes de sumir.
 */
public final class ChefesTropa {
    /** id do boss -> planeta da Tropa dele. */
    public static final Map<String, PlanetLore> PLANETA = new HashMap<>();
    /** planeta -> id do Boss da Tropa. */
    public static final Map<PlanetLore, String> DO_PLANETA = new HashMap<>();

    static {
        reg("karvhul_forjador_rubro", PlanetLore.YSMAULT);
        reg("vesk_morrath_carcereiro", PlanetLore.KORUGAR);
        reg("ghormaddu_arca_viva", PlanetLore.OKAARA);
        reg("seraphel_do_vitral", PlanetLore.ODYM);
        reg("gemeos_velados", PlanetLore.NOK);
        reg("calithe_rainha_do_espelho", PlanetLore.ZAMARON);
        reg("morthaal_coletor_de_lanternas", PlanetLore.RYUT);
        reg("verdan_o_arquiteto", PlanetLore.OA);
    }

    private static void reg(String id, PlanetLore p) {
        PLANETA.put(id, p);
        DO_PLANETA.put(p, id);
    }

    // =====================================================================================================
    // Base
    // =====================================================================================================

    public abstract static class Base extends LoreBoss {
        private static final RawAnimation MORTE = RawAnimation.begin().thenPlayAndHold("misc.death");
        private static final RawAnimation MOVE_IDLE = RawAnimation.begin().thenLoop("misc.idle");
        private static final RawAnimation MOVE_WALK = RawAnimation.begin().thenLoop("move.walk");
        /** Golpe que está "carregando" (dispara o efeito alguns ticks depois da animação começar). */
        protected String golpe;
        protected int golpeEm;

        protected Base(EntityType<? extends Monster> type, Level level) {
            super(type, level, BossEvent.BossBarColor.WHITE);
            bar.setColor(cor(corps()));
            xpReward = 400;
        }

        public PlanetLore planeta() {
            return PLANETA.getOrDefault(bossId(), PlanetLore.OA);
        }

        public BuiltinCorps corps() {
            return planeta().corps;
        }

        private static BossEvent.BossBarColor cor(BuiltinCorps c) {
            return switch (c) {
                case GREEN -> BossEvent.BossBarColor.GREEN;
                case YELLOW, ORANGE -> BossEvent.BossBarColor.YELLOW;
                case RED -> BossEvent.BossBarColor.RED;
                case BLUE -> BossEvent.BossBarColor.BLUE;
                case INDIGO, BLACK -> BossEvent.BossBarColor.PURPLE;
                case VIOLET -> BossEvent.BossBarColor.PINK;
                default -> BossEvent.BossBarColor.WHITE;
            };
        }

        @Override
        protected int barRgb() {
            return corps().color();
        }

        /** Ataques extras (sem o "attack."), ex: {"smash", "chains"}. */
        protected abstract String[] ataques();

        /** misc.* disparados uma vez (sem o "misc."), ex: {"hoard"}. */
        protected String[] miscUmaVez() {
            return new String[0];
        }

        /** misc.* em loop agora (fase 2, postura), ou null. */
        protected String estadoLoop() {
            return null;
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, "move", 5, state -> {
                if (isDeadOrDying()) return PlayState.STOP;
                return state.setAndContinue(state.isMoving() ? MOVE_WALK : MOVE_IDLE);
            }));
            AnimationController<Base> attack = new AnimationController<>(this, "attack", 0, state -> PlayState.STOP);
            attack.triggerableAnim("strike", RawAnimation.begin().thenPlay("attack.strike"));
            for (String a : ataques()) attack.triggerableAnim(a, RawAnimation.begin().thenPlay("attack." + a));
            controllers.add(attack);
            AnimationController<Base> estado = new AnimationController<>(this, "estado", 4, state -> {
                if (isDeadOrDying()) return state.setAndContinue(MORTE);
                String l = estadoLoop();
                return l == null ? PlayState.STOP : state.setAndContinue(RawAnimation.begin().thenLoop("misc." + l));
            });
            estado.triggerableAnim("death", MORTE);
            for (String m : miscUmaVez()) estado.triggerableAnim(m, RawAnimation.begin().thenPlay("misc." + m));
            controllers.add(estado);
        }

        /** O loop de estado depende de dados que o cliente precisa saber: usa o STATE sincronizado do LoreBoss. */
        protected boolean flag(int bit) {
            return (state() & (1 << bit)) != 0;
        }

        protected void setFlag(int bit, boolean on) {
            int s = state();
            setState(on ? s | (1 << bit) : s & ~(1 << bit));
        }

        // ---- golpes com tempo de preparo ----

        protected void preparar(String nome, int ticks, boolean ataque) {
            golpe = nome;
            golpeEm = ticks;
            triggerAnim(ataque ? "attack" : "estado", nome);
        }

        @Override
        protected void bossTick(LivingEntity target) {
            if (golpe != null) {
                if (--golpeEm <= 0) {
                    String g = golpe;
                    golpe = null;
                    executar(g, target);
                }
                return;
            }
            mover(target);
            if (specialCooldown <= 0 && especial(target)) return;
            tryMelee(target, 1.0, 30);
        }

        protected void mover(LivingEntity target) {
            chase(target, 1.0);
        }

        /** Escolhe um especial (true se começou um). */
        protected abstract boolean especial(LivingEntity target);

        /** O golpe preparado acontece agora. */
        protected abstract void executar(String golpe, LivingEntity target);

        protected int recarga(int base) {
            return phase() >= 2 ? (int) (base * 0.7) : base;
        }

        protected float dano() {
            return (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        }

        protected ServerLevel sl() {
            return (ServerLevel) level();
        }

        protected void anel(Vec3 c, double r, int n) {
            int cor = corps().color();
            var op = new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 2.2f);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                sl().sendParticles(op, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 1, 0, 0.1, 0, 0);
            }
        }

        protected void puxar(LivingEntity alvo, double forca) {
            Vec3 d = position().subtract(alvo.position()).normalize().scale(forca);
            alvo.push(d.x, 0.35, d.z);
            alvo.hurtMarked = true;
        }

        // ---- morte: misc.death e o corpo fica 2 s ----

        @Override
        public void die(DamageSource source) {
            if (!level().isClientSide) triggerAnim("estado", "death");
            super.die(source);
        }

        @Override
        protected void tickDeath() {
            if (++deathTime >= 60 && !level().isClientSide() && !isRemoved()) {
                level().broadcastEntityEvent(this, (byte) 60);
                remove(RemovalReason.KILLED);
            }
        }

        // ---- recompensas ----

        @Override
        protected void onDefeated() {
            for (java.util.UUID id : fighters) {
                if (sl().getPlayerByUUID(id) instanceof ServerPlayer sp && sp.distanceTo(this) < 96)
                    com.lanterna.tropa.Nivel.addXp(sp, 1200, getType().getDescription());
            }
        }

        @Override
        protected List<ItemStack> extraLoot() {
            List<ItemStack> l = new ArrayList<>();
            l.add(new ItemStack(planeta().dropItem(), 1));
            l.add(new ItemStack(LoreItems.FRAGMENTO_LENDARIO.get(), 2));
            l.add(new ItemStack(Items.DIAMOND, 4));
            return l;
        }
    }

    // =====================================================================================================
    // Karvhul, o Forjador Rubro (Fúria)
    // =====================================================================================================

    public static class Karvhul extends Base {
        public Karvhul(EntityType<? extends Monster> type, Level level) { super(type, level); }

        @Override protected String[] ataques() { return new String[]{"smash", "chains"}; }
        @Override protected String estadoLoop() { return phase() >= 2 ? "rage" : null; }

        @Override
        protected void onPhaseChange(int p) {
            super.onPhaseChange(p);
            var sp = getAttribute(Attributes.MOVEMENT_SPEED);
            if (sp != null) sp.setBaseValue(sp.getBaseValue() * 1.25);
        }

        @Override
        protected boolean especial(LivingEntity t) {
            double d = distanceTo(t);
            if (d < 7) { preparar("smash", 14, true); specialCooldown = recarga(120); return true; }
            if (d > 8 && d < 22) { preparar("chains", 8, true); specialCooldown = recarga(140); return true; }
            return false;
        }

        @Override
        protected void executar(String g, LivingEntity t) {
            if (g.equals("smash")) {
                // punhos de escória no chão: dano em área e fogo
                Vec3 c = position();
                areaDamage(c, 6, dano() * 1.3f, 1.4);
                for (Player p : playersNear(6)) p.setSecondsOnFire(5);
                sl().sendParticles(ParticleTypes.LAVA, c.x, c.y + 0.3, c.z, 40, 3, 0.2, 3, 0);
                sl().sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.3, c.z, 80, 3.5, 0.3, 3.5, 0.05);
                anel(c, 5, 48);
                level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.6f, 0.6f);
            } else if (g.equals("chains") && t.distanceTo(this) < 24) {
                // ganchos em brasa: puxa para perto
                puxar(t, 2.4);
                t.hurt(damageSources().mobAttack(this), dano() * 0.4f);
                t.setSecondsOnFire(3);
                level().playSound(null, t.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.5f, 0.6f);
            }
        }
    }

    // =====================================================================================================
    // Vesk Morrath, o Carcereiro (Medo)
    // =====================================================================================================

    public static class Vesk extends Base {
        public Vesk(EntityType<? extends Monster> type, Level level) { super(type, level); }

        @Override protected String[] ataques() { return new String[]{"grab", "legs"}; }
        @Override protected String estadoLoop() { return phase() >= 2 ? "phase2" : null; }

        @Override
        protected boolean especial(LivingEntity t) {
            double d = distanceTo(t);
            if (d < 4.5) { preparar("grab", 10, true); specialCooldown = recarga(100); return true; }
            if (d < 7) { preparar("legs", 8, true); specialCooldown = recarga(90); return true; }
            return false;
        }

        @Override
        protected void executar(String g, LivingEntity t) {
            if (g.equals("grab") && t.distanceTo(this) < 6) {
                t.hurt(damageSources().mobAttack(this), dano());
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3));
                t.push(0, 0.6, 0);
                t.hurtMarked = true;
            } else if (g.equals("legs")) {
                // as patas de aranha descem em volta
                areaDamage(position(), 5, dano() * 0.8f, 0.6);
                for (Player p : playersNear(5)) p.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
                anel(position(), 4, 36);
            }
        }

        @Override
        protected void idleTick() {}

        @Override
        public void aiStep() {
            super.aiStep();
            // fase 2: a chama da cabeça cresce; perto dela, o medo cega
            if (!level().isClientSide && phase() >= 2 && tickCount % 20 == 0) {
                for (Player p : playersNear(10)) {
                    p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0));
                    p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, 0));
                }
                sl().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + getBbHeight(), getZ(), 12, 0.4, 0.4, 0.4, 0.02);
            }
        }
    }

    // =====================================================================================================
    // Ghor'Maddu, a Arca Viva (Avareza)
    // =====================================================================================================

    public static class GhorMaddu extends Base {
        public GhorMaddu(EntityType<? extends Monster> type, Level level) { super(type, level); }

        @Override protected String[] ataques() { return new String[]{"pinch", "coins"}; }
        @Override protected String[] miscUmaVez() { return new String[]{"hoard"}; }

        /** Quantos itens engoliu (0 a 8): cresce 5% por item. Guardado no STATE (sincronizado). */
        public int engolidos() {
            return Math.min(8, state());
        }

        @Override
        protected boolean especial(LivingEntity t) {
            double d = distanceTo(t);
            List<ItemEntity> itens = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(8));
            if (!itens.isEmpty() && engolidos() < 8) { preparar("hoard", 10, false); specialCooldown = recarga(80); return true; }
            if (d < 5) { preparar("pinch", 10, true); specialCooldown = recarga(70); return true; }
            if (d < 14) { preparar("coins", 14, true); specialCooldown = recarga(160); return true; }
            return false;
        }

        @Override
        protected void executar(String g, LivingEntity t) {
            switch (g) {
                case "pinch" -> {
                    if (t.distanceTo(this) < 7) {
                        t.hurt(damageSources().mobAttack(this), dano() * 1.5f);
                        level().playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1f, 1.4f);
                    }
                }
                case "coins" -> {
                    // gira e solta um tornado de moedas
                    var moeda = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET));
                    sl().sendParticles(moeda, getX(), getY() + 1.5, getZ(), 120, 6, 1.2, 6, 0.4);
                    for (Player p : playersNear(10)) {
                        if (!hasLineOfSight(p)) continue;
                        p.hurt(damageSources().mobAttack(this), dano() * 0.7f);
                        Vec3 k = p.position().subtract(position()).normalize().scale(1.2);
                        p.push(k.x, 0.3, k.z);
                        p.hurtMarked = true;
                    }
                    level().playSound(null, blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 1.5f, 1.6f);
                }
                case "hoard" -> {
                    // engole os itens do chão: cura um pouco e cresce
                    int n = 0;
                    for (ItemEntity it : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(8))) {
                        it.discard();
                        n++;
                    }
                    if (n > 0) {
                        setState(Math.min(8, state() + 1));
                        heal(getMaxHealth() * 0.04f * Math.min(n, 5));
                        refreshDimensions();
                        level().playSound(null, blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.HOSTILE, 2f, 0.5f);
                    }
                }
                default -> {}
            }
        }

        @Override
        public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
            return super.getDimensions(pose).scale(1f + 0.05f * engolidos());
        }

        @Override
        public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> key) {
            super.onSyncedDataUpdated(key);
            refreshDimensions();
        }
    }

    // =====================================================================================================
    // Seraphel do Vitral (Esperança)
    // =====================================================================================================

    public static class Seraphel extends Base {
        private int vulneravel, cura;

        public Seraphel(EntityType<? extends Monster> type, Level level) {
            super(type, level);
            setNoGravity(true);
        }

        @Override protected String[] ataques() { return new String[]{"light"}; }
        @Override protected String estadoLoop() { return flag(0) ? "vulnerable" : flag(1) ? "heal" : null; }

        @Override
        protected void mover(LivingEntity t) {
            // flutua uns 3 blocos acima do chão, a meia distância
            double d = distanceTo(t);
            Vec3 alvo = t.position().add(0, 3.5, 0);
            Vec3 dir = alvo.subtract(position());
            double vel = d > 9 ? 0.18 : d < 5 ? -0.12 : 0;
            Vec3 m = dir.normalize().scale(vel);
            setDeltaMovement(getDeltaMovement().scale(0.8).add(m.x, (alvo.y - getY()) * 0.04, m.z));
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (level().isClientSide) return;
            setNoGravity(true);
            if (getTarget() == null) setDeltaMovement(getDeltaMovement().multiply(0.8, 0.5, 0.8));
            // a cada 15 s abre as asas por 5 s: a única janela de dano cheio
            if (vulneravel > 0) {
                if (--vulneravel == 0) setFlag(0, false);
            } else if (tickCount % 300 == 150 && getTarget() != null) {
                vulneravel = 100;
                setFlag(0, true);
                for (Player p : playersNear(40))
                    p.displayClientMessage(Component.translatable("boss.lanterna.seraphel_do_vitral.vulneravel").withStyle(s -> s.withColor(0x9AD8FF)), true);
            }
            if (cura > 0 && --cura == 0) setFlag(1, false);
        }

        @Override
        protected boolean especial(LivingEntity t) {
            if (getHealth() < getMaxHealth() * 0.6f && cura == 0 && vulneravel == 0 && random.nextInt(4) == 0) {
                cura = 40;
                setFlag(1, true);
                heal(getMaxHealth() * 0.06f);
                for (LivingEntity a : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(16), e -> e instanceof Monster && e != this))
                    a.heal(a.getMaxHealth() * 0.3f);
                sl().sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2, getZ(), 40, 1.5, 1.5, 1.5, 0.05);
                specialCooldown = recarga(200);
                return true;
            }
            if (hasLineOfSight(t) && distanceTo(t) < 24) { preparar("light", 14, true); specialCooldown = recarga(70); return true; }
            return false;
        }

        @Override
        protected void executar(String g, LivingEntity t) {
            if (!hasLineOfSight(t)) return;
            // raio de luz das mãos
            Vec3 a = position().add(0, getBbHeight() * 0.6, 0), b = t.position().add(0, 1, 0);
            for (int i = 0; i <= 20; i++) {
                Vec3 p = a.lerp(b, i / 20.0);
                sl().sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
            }
            t.hurt(damageSources().indirectMagic(this, this), dano());
        }

        @Override
        protected float damageTaken(DamageSource source, float amount) {
            return vulneravel > 0 || source.is(DamageTypeTags.BYPASSES_ARMOR) ? amount : amount * 0.35f;
        }
    }

    // =====================================================================================================
    // Auun e Iel, os Gêmeos Velados (Compaixão)
    // =====================================================================================================

    public static class Gemeos extends Base {
        private int dorEspera;

        public Gemeos(EntityType<? extends Monster> type, Level level) { super(type, level); }

        @Override protected String[] ataques() { return new String[]{"hook"}; }
        @Override protected String[] miscUmaVez() { return new String[]{"share_pain", "split"}; }

        @Override
        protected void onPhaseChange(int p) {
            super.onPhaseChange(p);
            if (p == 2) triggerAnim("estado", "split");
        }

        @Override
        protected boolean especial(LivingEntity t) {
            double d = distanceTo(t);
            if (d > 6 && d < 20) { preparar("hook", 10, true); specialCooldown = recarga(110); return true; }
            return false;
        }

        @Override
        protected void executar(String g, LivingEntity t) {
            if (t.distanceTo(this) < 22) {
                puxar(t, 2.0);
                t.hurt(damageSources().mobAttack(this), dano() * 0.5f);
            }
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (dorEspera > 0) dorEspera--;
        }

        @Override
        protected float damageTaken(DamageSource source, float amount) {
            // dano compartilhado: os dois sentem, e uma parte volta para quem bateu
            if (!level().isClientSide && dorEspera == 0) {
                dorEspera = 40;
                triggerAnim("estado", "share_pain");
                if (source.getEntity() instanceof LivingEntity atk && atk != this)
                    atk.hurt(damageSources().indirectMagic(this, this), amount * 0.15f);
            }
            return amount * 0.85f;
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            boolean ok = super.doHurtTarget(target);
            // fase 2: os dois atacam, e o golpe pega em volta
            if (ok && phase() >= 2) areaDamage(target.position(), 2.5, dano() * 0.5f, 0.5);
            return ok;
        }
    }

    // =====================================================================================================
    // Rainha Calithe do Espelho (Amor)
    // =====================================================================================================

    public static class Calithe extends Base {
        private int reflete;

        public Calithe(EntityType<? extends Monster> type, Level level) { super(type, level); }

        @Override protected String[] ataques() { return new String[]{"thrust", "shards"}; }
        @Override protected String estadoLoop() { return flag(0) ? "reflect" : null; }

        @Override
        public void aiStep() {
            super.aiStep();
            if (level().isClientSide) return;
            if (reflete > 0) {
                if (--reflete == 0) setFlag(0, false);
            } else if (tickCount % 240 == 120 && getTarget() != null) {
                reflete = 80;
                setFlag(0, true);
            }
        }

        @Override
        protected void mover(LivingEntity t) {
            if (reflete == 0) super.mover(t);
            else getNavigation().stop();
        }

        @Override
        protected boolean especial(LivingEntity t) {
            if (reflete > 0) return false;
            double d = distanceTo(t);
            if (d > 4 && d < 12) { preparar("thrust", 10, true); specialCooldown = recarga(90); return true; }
            if (d < 8) { preparar("shards", 14, true); specialCooldown = recarga(140); return true; }
            return false;
        }

        @Override
        protected void executar(String g, LivingEntity t) {
            if (g.equals("thrust")) {
                // investida com a lança
                Vec3 dir = t.position().subtract(position()).normalize();
                setDeltaMovement(dir.scale(1.6).add(0, 0.1, 0));
                hurtMarked = true;
                if (distanceTo(t) < 9) t.hurt(damageSources().mobAttack(this), dano() * 1.2f);
            } else {
                // estilhaços de cristal em volta
                areaDamage(position(), 7, dano() * 0.75f, 0.8);
                sl().sendParticles(ParticleTypes.CRIT, getX(), getY() + 1.5, getZ(), 80, 4, 1, 4, 0.3);
                anel(position(), 6, 48);
            }
        }

        @Override
        protected float damageTaken(DamageSource source, float amount) {
            if (reflete > 0) {
                // o escudo-espelho devolve projéteis e segura metade do resto
                if (source.is(DamageTypeTags.IS_PROJECTILE)) {
                    if (source.getEntity() instanceof LivingEntity atk) atk.hurt(damageSources().indirectMagic(this, this), amount);
                    level().playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.5f, 1.4f);
                    return 0;
                }
                return amount * 0.5f;
            }
            return amount;
        }
    }

    // =====================================================================================================
    // Morthaal, o Coletor de Lanternas (Morte)
    // =====================================================================================================

    public static class Morthaal extends Base {
        public Morthaal(EntityType<? extends Monster> type, Level level) { super(type, level); }

        @Override protected String[] ataques() { return new String[]{"reap"}; }
        @Override protected String[] miscUmaVez() { return new String[]{"release_souls"}; }

        @Override
        protected boolean especial(LivingEntity t) {
            int almas = level().getEntitiesOfClass(net.minecraft.world.entity.monster.Vex.class, getBoundingBox().inflate(32)).size();
            if (almas < 3 && random.nextInt(3) == 0) { preparar("release_souls", 16, false); specialCooldown = recarga(260); return true; }
            if (distanceTo(t) < 6) { preparar("reap", 12, true); specialCooldown = recarga(90); return true; }
            return false;
        }

        @Override
        protected void executar(String g, LivingEntity t) {
            if (g.equals("reap")) {
                // golpe de gadanha em arco na frente
                Vec3 frente = Vec3.directionFromRotation(0, getYRot());
                for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(6), x -> x != this && !(x instanceof LoreBoss))) {
                    Vec3 d = e.position().subtract(position());
                    if (d.horizontalDistance() > 6.5 || d.normalize().dot(frente) < -0.1) continue;
                    e.hurt(damageSources().mobAttack(this), dano() * 1.2f);
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
                }
                sl().sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + frente.x * 3, getY() + 1.5, getZ() + frente.z * 3, 5, 1.5, 0.3, 1.5, 0);
            } else {
                // solta as almas presas
                for (int i = 0; i < 3; i++) {
                    var v = EntityType.VEX.create(level());
                    if (v == null) continue;
                    v.moveTo(getX() + random.nextGaussian(), getY() + 2.5, getZ() + random.nextGaussian(), 0, 0);
                    v.finalizeSpawn(sl(), level().getCurrentDifficultyAt(blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
                    v.setOwner(this);
                    v.setLimitedLife(20 * 25);
                    v.setTarget(t);
                    level().addFreshEntity(v);
                }
                sl().sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 2, getZ(), 30, 1, 1, 1, 0.05);
                level().playSound(null, blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 2f, 0.6f);
            }
        }
    }

    // =====================================================================================================
    // Verdan, o Arquiteto (Vontade)
    // =====================================================================================================

    public static class Verdan extends Base {
        /** Blocos da prisão de construto e quando somem. */
        private final Map<BlockPos, BlockState> prisao = new HashMap<>();
        private int prisaoAte;

        public Verdan(EntityType<? extends Monster> type, Level level) { super(type, level); }

        @Override protected String[] ataques() { return new String[]{"hammer", "build"}; }
        @Override protected String estadoLoop() { return phase() >= 2 ? "phase2" : null; }

        @Override
        protected boolean especial(LivingEntity t) {
            double d = distanceTo(t);
            if (d < 5.5) { preparar("hammer", 12, true); specialCooldown = recarga(90); return true; }
            if (d < 18 && prisao.isEmpty()) { preparar("build", 18, true); specialCooldown = recarga(220); return true; }
            return false;
        }

        @Override
        protected void executar(String g, LivingEntity t) {
            if (g.equals("hammer")) {
                Vec3 c = position().add(Vec3.directionFromRotation(0, getYRot()).scale(2.5));
                areaDamage(c, 4.5, dano() * 1.25f, 1.2);
                anel(c, 4, 40);
                level().playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5f, 0.7f);
            } else {
                // prisão de construto em volta do alvo (só no ar; some sozinha em 5 s)
                BlockPos b = t.blockPosition();
                BlockState vidro = Blocks.LIME_STAINED_GLASS.defaultBlockState();
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 2; dy++) {
                    boolean borda = Math.abs(dx) == 1 || Math.abs(dz) == 1 || dy == -1 || dy == 2;
                    if (!borda) continue;
                    BlockPos p = b.offset(dx, dy, dz);
                    if (!level().getBlockState(p).isAir()) continue;
                    prisao.put(p, level().getBlockState(p));
                    level().setBlock(p, vidro, 3);
                }
                prisaoAte = tickCount + 100;
                level().playSound(null, b, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.2f, 1.6f);
            }
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (!level().isClientSide && !prisao.isEmpty() && (tickCount >= prisaoAte || !isAlive())) soltar();
        }

        private void soltar() {
            prisao.forEach((p, s) -> {
                if (level().getBlockState(p).is(Blocks.LIME_STAINED_GLASS)) level().setBlock(p, s, 3);
            });
            prisao.clear();
        }

        @Override
        public void remove(RemovalReason reason) {
            if (!level().isClientSide) soltar();
            super.remove(reason);
        }

        @Override
        protected int recarga(int base) {
            // fase 2: os construtos giram rápido e ele age mais vezes
            return phase() >= 2 ? (int) (base * 0.55) : base;
        }
    }

    private ChefesTropa() {}
}
