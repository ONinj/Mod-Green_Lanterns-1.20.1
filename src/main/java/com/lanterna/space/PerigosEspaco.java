package com.lanterna.space;

import com.lanterna.LanternaMod;
import com.lanterna.naves.NaveEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Lado do servidor dos perigos do espaço ({@link Perigos}): queimaduras perto dos sóis, morte dentro deles e no horizonte
 * dos buracos negros, avisos na tela, e o puxão sobre naves sem piloto, criaturas e itens soltos no espaço
 * (o puxão de quem voa ou pilota é calculado no cliente dele).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PerigosEspaco {
    public static final ResourceKey<DamageType> SOL = ResourceKey.create(Registries.DAMAGE_TYPE, LanternaMod.id("sol"));
    public static final ResourceKey<DamageType> BURACO = ResourceKey.create(Registries.DAMAGE_TYPE, LanternaMod.id("buraco_negro"));

    private static DamageSource fonte(ServerLevel l, ResourceKey<DamageType> k) {
        return new DamageSource(l.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(k));
    }

    @SubscribeEvent
    public static void jogador(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (!p.level().dimension().equals(SpaceBodies.SPACE) || p.isSpectator() || !p.isAlive()) return;
        ServerLevel l = p.serverLevel();
        Vec3 pos = p.position();
        for (Perigos.Perigo h : Perigos.near(pos, 1)) {
            double d = pos.distanceTo(h.centro());
            if (d > h.alcance()) continue;
            if (h.tipo() == Perigos.Tipo.SOL) sol(p, l, h, d);
            else buraco(p, l, h, d);
            // aviso: só quando a gravidade já pesa
            if (d < h.alcance() * 0.75 && p.tickCount % 30 == 0) {
                boolean critico = d < h.zonaDano() * 1.25;
                p.displayClientMessage(Component.translatable(critico ? "perigo.lanterna.aviso_critico" : "perigo.lanterna.aviso",
                        h.nomeExibido(), (int) Math.max(0, d - h.raio()))
                        .withStyle(s -> s.withColor(critico ? 0xFF3020 : 0xFFB040)), true);
            }
        }
    }

    private static void sol(ServerPlayer p, ServerLevel l, Perigos.Perigo h, double d) {
        if (p.isCreative()) return;
        if (d < h.raio()) {
            p.hurt(fonte(l, SOL), 100000f);
            return;
        }
        if (d > h.zonaDano()) return;
        // quanto mais perto, mais forte (de 1 a 8 de dano por segundo) — o anel segura um pouco, mas não tudo
        double t = 1 - (d - h.raio()) / (h.zonaDano() - h.raio());
        p.setRemainingFireTicks(Math.max(p.getRemainingFireTicks(), 60));
        if (p.tickCount % 10 == 0) p.hurt(fonte(l, SOL), (float) Mth.lerp(t, 0.5, 4.0));
        if (p.tickCount % 4 == 0) {
            Vec3 c = p.position();
            l.sendParticles(ParticleTypes.FLAME, c.x, c.y + 1, c.z, 3, 0.4, 0.6, 0.4, 0.02);
        }
    }

    private static void buraco(ServerPlayer p, ServerLevel l, Perigos.Perigo h, double d) {
        if (p.isCreative()) return;
        if (d < h.raio()) {
            l.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1f, 0.4f);
            p.hurt(fonte(l, BURACO), 100000f);
            return;
        }
        if (d > h.zonaDano()) return;
        // forças de maré: rasgam aos poucos
        if (p.tickCount % 10 == 0) p.hurt(fonte(l, BURACO), (float) Mth.lerp(1 - (d - h.raio()) / (h.zonaDano() - h.raio()), 1.0, 5.0));
        if (p.tickCount % 3 == 0) {
            Vec3 c = p.position();
            l.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 1, c.z, 4, 0.6, 0.8, 0.6, 0.05);
        }
    }

    /** Naves sem piloto humano, criaturas e itens também caem nos sóis e buracos negros. */
    @SubscribeEvent
    public static void mundo(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.START || !(e.level instanceof ServerLevel l) || !l.dimension().equals(SpaceBodies.SPACE)) return;
        if (l.players().isEmpty()) return;
        List<Entity> todos = new ArrayList<>();
        for (Entity en : l.getAllEntities()) todos.add(en);
        for (Entity en : todos) {
            if (en instanceof Player || en.isPassenger() || !en.isAlive()) continue;
            if (!(en instanceof LivingEntity || en instanceof NaveEntity || en instanceof ItemEntity)) continue;
            if (en instanceof NaveEntity n && (n.getControllingPassenger() instanceof Player || n.tipo().destrocos())) continue;
            Vec3 pos = en.position();
            for (Perigos.Perigo h : Perigos.near(pos, 1)) {
                double d = pos.distanceTo(h.centro());
                if (d > h.alcance()) continue;
                if (d < h.raio()) {
                    if (en instanceof LivingEntity le) le.hurt(fonte(l, h.tipo() == Perigos.Tipo.SOL ? SOL : BURACO), 100000f);
                    else if (en.getPassengers().stream().noneMatch(x -> x instanceof Player)) en.discard();
                    break;
                }
                Vec3 a = Perigos.puxao(h, pos);
                en.setDeltaMovement(en.getDeltaMovement().add(a));
                en.hurtMarked = true;
            }
        }
    }

    private PerigosEspaco() {}
}
