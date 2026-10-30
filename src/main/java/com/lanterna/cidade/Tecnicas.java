package com.lanterna.cidade;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.construct.ConstructEntity;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.lore.Factions;
import com.lanterna.mission.LoreCapability;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import com.lanterna.power.RingPowers;
import com.lanterna.space.SpaceBodies;
import com.lanterna.tropa.Nivel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.*;

/**
 * Técnicas: poderes avançados ensinados pelos Capitães do Palácio. Equipadas em até 2 teclas (3 para a Guarda de Honra).
 *
 *   olhar     Olhar do Anel: inimigos brilham através das paredes; minérios e baús perto acendem (10 s)
 *   bastiao   Bastião: cúpula parada que segura tiros e empurra inimigos para fora (15 s)
 *   onda      Onda de Vontade: impacto em volta que fere, derruba e empurra
 *   salto     Salto de Dobra: teletransporte curto para onde você olha (no espaço, um salto longo)
 *   armadura  Armadura de Construto: vida extra, força e resistência (20 s)
 *   comando   Comando: dois Lanternas da sua Tropa lutam com você (60 s)
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Tecnicas {
    public record Info(int custo, int espera, String capitao) {}

    public static final Map<String, Info> INFO = Map.of(
            "olhar", new Info(60, 45 * 20, "aurith"),
            "bastiao", new Info(110, 60 * 20, "ilith"),
            "onda", new Info(80, 25 * 20, "drevan"),
            "salto", new Info(40, 6 * 20, "lumiae"),
            "armadura", new Info(150, 90 * 20, "brakhul"),
            "comando", new Info(200, 180 * 20, "qiri"));

    private static final Map<UUID, Map<String, Long>> ESPERA = new HashMap<>();
    private record Efeito(ServerLevel level, UUID dono, String tipo, Vec3 pos, long fim, List<UUID> extra) {}
    private static final List<Efeito> EFEITOS = new ArrayList<>();

    public static int slots(LoreData d) {
        return d.honra() ? 3 : 2;
    }

    // ===================== Aprender =====================

    public static void desbloquear(ServerPlayer p, LoreData d, String tec) {
        d.addTecnica(tec);
        for (int i = 0; i < slots(d); i++) if (d.slotTecnica(i).isEmpty()) { d.setSlotTecnica(i, tec); break; }
        cerimonia(p, Component.translatable("tecnica.lanterna.aprendida"), Component.translatable("tecnica.lanterna." + tec));
        p.sendSystemMessage(Component.translatable("tecnica.lanterna.aprendida_chat", Component.translatable("tecnica.lanterna." + tec),
                Component.translatable("tecnica.lanterna." + tec + ".desc")).withStyle(s -> s.withColor(0x7DFF9A)));
        MissionManager.sync(p, d);
    }

    public static void honra(ServerPlayer p, LoreData d) {
        d.setHonra(true);
        cerimonia(p, Component.translatable("palacio.lanterna.honra.titulo"), Component.translatable("palacio.lanterna.honra.sub"));
        MissionManager.sync(p, d);
    }

    private static void cerimonia(ServerPlayer p, Component titulo, Component sub) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(titulo.copy().withStyle(s -> s.withColor(0x7DFF9A))));
        p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        ServerLevel l = p.serverLevel();
        l.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        l.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 0.7f);
        for (int i = 0; i < 60; i++) {
            double a = Math.PI * 2 * i / 60;
            l.sendParticles(ParticleTypes.END_ROD, p.getX() + Math.cos(a) * 2, p.getY() + 0.2 + (i % 6) * 0.4, p.getZ() + Math.sin(a) * 2, 1, 0, 0.05, 0, 0.01);
        }
    }

    // ===================== Equipar e usar (pacote do cliente) =====================

    public static void equipar(ServerPlayer p, String tec, int slot) {
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null || slot < 0 || slot >= slots(d)) return;
        if (!tec.isEmpty() && !d.tecnicas().contains(tec)) return;
        d.setSlotTecnica(slot, tec);
        MissionManager.sync(p, d);
    }

    public static void usar(ServerPlayer p, int slot) {
        LoreData d = LoreCapability.get(p).orElse(null);
        RingData ring = ModCapabilities.get(p).orElse(null);
        if (d == null || ring == null || slot < 0 || slot >= slots(d)) return;
        String tec = d.slotTecnica(slot);
        if (tec.isEmpty() || !d.tecnicas().contains(tec)) {
            p.displayClientMessage(Component.translatable("tecnica.lanterna.vazio", slot + 1), true);
            return;
        }
        if (!ring.hasRing() || !ring.powerActive()) {
            p.displayClientMessage(Component.translatable("message.lanterna.power_needed"), true);
            return;
        }
        Info info = INFO.get(tec);
        long now = p.level().getGameTime();
        Map<String, Long> cds = ESPERA.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        long pronto = cds.getOrDefault(tec, 0L);
        if (now < pronto) {
            p.displayClientMessage(Component.translatable("tecnica.lanterna.espera", Component.translatable("tecnica.lanterna." + tec), (pronto - now) / 20 + 1), true);
            return;
        }
        boolean espaco = p.level().dimension().equals(SpaceBodies.SPACE);
        int custo = Nivel.custoComDesconto(p, tec.equals("salto") && espaco ? info.custo * 3 : info.custo);
        if (ring.charge() < custo && !p.isCreative()) {
            p.displayClientMessage(Component.translatable("message.lanterna.no_charge"), true);
            return;
        }
        boolean ok = switch (tec) {
            case "olhar" -> olhar(p, ring.color());
            case "bastiao" -> bastiao(p, ring.color());
            case "onda" -> onda(p, ring.color());
            case "salto" -> salto(p, ring.color(), espaco);
            case "armadura" -> armadura(p, ring.color());
            case "comando" -> comando(p);
            default -> false;
        };
        if (!ok) return;
        if (!p.isCreative()) RingPowers.setCharge(p, ring, ring.charge() - custo);
        int espera = tec.equals("salto") && espaco ? 20 * 20 : info.espera;
        cds.put(tec, now + espera);
    }

    private static DustParticleOptions poeira(int c, float s) {
        return new DustParticleOptions(new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f), s);
    }

    /** É inimigo de quem usou a técnica? */
    private static boolean inimigo(ServerPlayer p, Entity e) {
        if (e == p || !(e instanceof LivingEntity le) || !le.isAlive()) return false;
        if (e instanceof LanternaEntity l) return l.hostilA(p) || l.getTarget() == p || l.isPirata();
        if (e instanceof Mob m && m.getTarget() == p) return true;
        return e instanceof Enemy;
    }

    // ---- Olhar do Anel ----
    private static boolean olhar(ServerPlayer p, int cor) {
        ServerLevel l = p.serverLevel();
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(48), e -> inimigo(p, e))) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
        }
        EFEITOS.add(new Efeito(l, p.getUUID(), "olhar", p.position(), l.getGameTime() + 200, List.of()));
        l.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.8f);
        l.sendParticles(poeira(cor, 2f), p.getX(), p.getEyeY(), p.getZ(), 30, 0.6, 0.3, 0.6, 0.1);
        return true;
    }

    /** A cada segundo do Olhar: minérios e baús perto acendem (só para quem usou). */
    private static void olharTick(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        BlockPos c = p.blockPosition();
        int n = 0;
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-10, -8, -10), c.offset(10, 8, 10))) {
            var st = l.getBlockState(q);
            boolean ouro = st.is(Tags.Blocks.ORES);
            boolean bau = !ouro && l.getBlockEntity(q) instanceof BaseContainerBlockEntity;
            if (!ouro && !bau) continue;
            l.sendParticles(p, bau ? ParticleTypes.WAX_ON : ParticleTypes.GLOW, true, q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0);
            if (++n > 80) break;
        }
    }

    // ---- Bastião ----
    private static boolean bastiao(ServerPlayer p, int cor) {
        ServerLevel l = p.serverLevel();
        ConstructEntity s = ConstructEntity.create(p, ConstructEntity.Kind.SHIELD, cor, 300, 0, 3.4f);
        s.setPos(p.getX(), p.getY(), p.getZ());
        l.addFreshEntity(s);
        EFEITOS.add(new Efeito(l, p.getUUID(), "bastiao", p.position(), l.getGameTime() + 300, List.of(s.getUUID())));
        l.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 1.3f);
        return true;
    }

    private static void bastiaoTick(Efeito e, ServerPlayer p) {
        Vec3 c = e.pos.add(0, 1, 0);
        double r = 5.5;
        for (Projectile pr : e.level.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(r))) {
            if (pr.getOwner() == p || pr.position().distanceTo(c) > r) continue;
            if (pr.getOwner() instanceof LanternaEntity l && l.aliadoDe(p)) continue;
            e.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pr.getX(), pr.getY(), pr.getZ(), 6, 0.1, 0.1, 0.1, 0.1);
            pr.discard();
        }
        for (Entity m : e.level.getEntities(p, new AABB(c, c).inflate(r), x -> inimigo(p, x))) {
            Vec3 d = m.position().subtract(e.pos).multiply(1, 0, 1);
            if (d.lengthSqr() < 1e-3) d = new Vec3(1, 0, 0);
            Vec3 push = d.normalize().scale(0.6);
            m.push(push.x, 0.15, push.z);
            m.hurtMarked = true;
        }
    }

    // ---- Onda de Vontade ----
    private static boolean onda(ServerPlayer p, int cor) {
        ServerLevel l = p.serverLevel();
        float dano = 10f * Nivel.forca(p);
        for (Entity e : l.getEntities(p, p.getBoundingBox().inflate(8), x -> inimigo(p, x))) {
            double d = e.distanceTo(p);
            if (d > 8) continue;
            e.hurt(p.damageSources().playerAttack(p), dano * (float) (1.2 - d / 10));
            Vec3 push = e.position().subtract(p.position()).normalize().scale(2.2 - d * 0.15);
            e.push(push.x, 0.55, push.z);
            e.hurtMarked = true;
        }
        for (int ring = 1; ring <= 4; ring++) for (int i = 0; i < 40; i++) {
            double a = Math.PI * 2 * i / 40;
            l.sendParticles(poeira(cor, 2.2f), p.getX() + Math.cos(a) * ring * 2, p.getY() + 0.3, p.getZ() + Math.sin(a) * ring * 2, 1, 0, 0.05, 0, 0);
        }
        l.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
        l.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1f, 1.4f);
        return true;
    }

    // ---- Salto de Dobra ----
    private static boolean salto(ServerPlayer p, int cor, boolean espaco) {
        ServerLevel l = p.serverLevel();
        Vec3 olho = p.getEyePosition(), dir = p.getLookAngle();
        double alcance = espaco ? 600 : 30;
        Vec3 fim = olho.add(dir.scale(alcance));
        Vec3 dest = fim;
        if (!espaco) {
            BlockHitResult hit = l.clip(new ClipContext(olho, fim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (hit.getType() == HitResult.Type.BLOCK) dest = hit.getLocation().subtract(dir.scale(1.2));
            if (dest.distanceTo(olho) < 2) return false;
        }
        Vec3 de = p.position();
        l.sendParticles(poeira(cor, 2f), de.x, de.y + 1, de.z, 40, 0.4, 0.8, 0.4, 0.2);
        l.sendParticles(ParticleTypes.REVERSE_PORTAL, de.x, de.y + 1, de.z, 30, 0.3, 0.6, 0.3, 0.1);
        p.teleportTo(dest.x, dest.y - 1.4, dest.z);
        p.fallDistance = 0;
        l.sendParticles(poeira(cor, 2f), p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.8, 0.4, 0.2);
        l.playSound(null, de.x, de.y, de.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.6f);
        l.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7f, 2f);
        return true;
    }

    // ---- Armadura de Construto ----
    private static boolean armadura(ServerPlayer p, int cor) {
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 3, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 1, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 0, false, true));
        ServerLevel l = p.serverLevel();
        ConstructEntity s = ConstructEntity.create(p, ConstructEntity.Kind.SHIELD, cor, 400, 0, 0.85f);
        s.setPos(p.getX(), p.getY(), p.getZ());
        l.addFreshEntity(s);
        l.playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS, 1.2f, 0.8f);
        l.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.6f);
        return true;
    }

    // ---- Comando ----
    private static boolean comando(ServerPlayer p) {
        BuiltinCorps c = Factions.playerCorps(p);
        if (c == null) return false;
        if (c == BuiltinCorps.WHITE) c = BuiltinCorps.GREEN;
        ServerLevel l = p.serverLevel();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            LanternaEntity a = LanternaEntity.lanterna(l, c, i == 0);
            Vec3 at = p.position().add((i == 0 ? -2 : 2), 0.5, -1);
            a.moveTo(at.x, at.y, at.z, p.getYRot(), 0);
            a.getPersistentData().putBoolean("lanterna_encontro", true);
            a.getPersistentData().putBoolean("lanterna_comando", true);
            l.addFreshEntity(a);
            l.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 30, 0.3, 0.8, 0.3, 0.05);
            ids.add(a.getUUID());
        }
        EFEITOS.add(new Efeito(l, p.getUUID(), "comando", p.position(), l.getGameTime() + 1200, ids));
        l.playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
        return true;
    }

    private static void comandoTick(Efeito e, ServerPlayer p) {
        Entity alvo = p.getLastHurtByMob() != null ? p.getLastHurtByMob() : p.getLastHurtMob();
        for (UUID id : e.extra) {
            if (!(e.level.getEntity(id) instanceof LanternaEntity a)) continue;
            if (a.distanceTo(p) > 14) a.teleportTo(p.getX() + 1.5, p.getY() + 0.5, p.getZ() + 1.5);
            a.restrictTo(p.blockPosition(), 10);
            if (alvo instanceof LivingEntity le && le.isAlive() && a.getTarget() == null && le != a) a.setTarget(le);
        }
    }

    // ===================== Tick dos efeitos =====================

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || EFEITOS.isEmpty()) return;
        long tick = event.getServer().getTickCount();
        EFEITOS.removeIf(e -> {
            ServerPlayer p = e.level.getServer().getPlayerList().getPlayer(e.dono);
            boolean acabou = p == null || e.level.getGameTime() >= e.fim;
            if (acabou) {
                if (e.tipo.equals("comando")) for (UUID id : e.extra) {
                    Entity a = e.level.getEntity(id);
                    if (a != null) { e.level.sendParticles(ParticleTypes.END_ROD, a.getX(), a.getY() + 1, a.getZ(), 20, 0.3, 0.8, 0.3, 0.05); a.discard(); }
                }
                return true;
            }
            switch (e.tipo) {
                case "olhar" -> { if (tick % 20 == 0) olharTick(p); }
                case "bastiao" -> bastiaoTick(e, p);
                case "comando" -> { if (tick % 20 == 0) comandoTick(e, p); }
                default -> {}
            }
            return false;
        });
    }

    private Tecnicas() {}
}
