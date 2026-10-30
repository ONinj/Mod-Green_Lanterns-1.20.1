package com.lanterna.tropa;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.Especie;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.lore.Factions;
import com.lanterna.lore.entity.LoreCreatures;
import com.lanterna.lore.entity.boss.LoreBoss;
import com.lanterna.mission.LoreCapability;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import com.lanterna.naves.NaveEntity;
import com.lanterna.naves.NaveTipo;
import com.lanterna.power.RingPowers;
import com.lanterna.space.SpaceBodies;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Eventos da vida de Lanterna: XP (inimigos, naves, descobertas), aliados da Tropa que ajudam na briga,
 * e a emoção da Tropa (Prova da Emoção e a recarga própria do anel depois dela).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TropaEvents {

    // ===================== XP =====================

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        // Morte por perto: a Tropa Preta sente
        for (ServerPlayer p : victim.level().getEntitiesOfClass(ServerPlayer.class, victim.getBoundingBox().inflate(16))) {
            if (p != victim && is(p, BuiltinCorps.BLACK)) emocao(p, 10);
        }
        Contratos.creditar(victim, event.getSource().getEntity());
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        int xp = 0;
        Component why = victim.getDisplayName();
        if (victim instanceof LanternaEntity l) {
            xp = switch (l.papel()) {
                case PIRATA -> 30;
                case CAPITAO -> 250;
                case LANTERNA -> l.isOficial() ? 120 : 60;
                case MERCADOR -> 0;
            };
            if (l.papel() == Especie.Papel.MERCADOR) {
                killer.sendSystemMessage(Component.translatable("tropa.lanterna.matou_inocente"));
            }
        } else if (victim instanceof LoreBoss) {
            xp = 400;
        } else if (victim instanceof LoreCreatures.Eco) {
            xp = 10;
        } else if (victim instanceof Enemy) {
            xp = 2;
        }
        if (xp > 0) Nivel.addXp(killer, xp, why);
        // Amarelo: quem vê alguém cair perto de um Lanterna Amarelo sente medo
        if (is(killer, BuiltinCorps.YELLOW)) {
            int n = 0;
            for (LivingEntity e : victim.level().getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(12),
                    e -> e instanceof Enemy && e != victim)) {
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
                n++;
            }
            emocao(killer, 15 + 15 * n);
        }
    }

    public static void onShipDestroyed(ServerPlayer p, NaveEntity n) {
        NaveTipo t = n.tipo();
        int xp = t == NaveTipo.NAVE_PIRATA_CAPITANIA ? 1200 : 200;
        Nivel.addXp(p, xp, Component.translatable("entity.lanterna." + t.id));
        if (n.ia().equals("pirata")) MissionManager.onShip(p, t == NaveTipo.NAVE_PIRATA_CAPITANIA ? "capitania" : "nave_pirata");
        if (n.ia().equals("inimiga")) MissionManager.onShip(p, "nave_inimiga");
    }

    public static void onLoot(ServerPlayer p, NaveEntity n) {
        Nivel.addXp(p, 25, Component.translatable("entity.lanterna.destrocos"));
    }

    /** Conversa com um Lanterna NPC (resgates dos contratos). */
    public static void onTalkNpc(ServerPlayer p, LanternaEntity npc) {
        if (npc.getPersistentData().getBoolean("lanterna_resgate")) {
            npc.getPersistentData().remove("lanterna_resgate");
            var body = SpaceBodies.bodyAt(npc.level().dimension(), npc.getX(), npc.getZ());
            MissionManager.onTalk(p, "resgate", body != null ? body.id() : "oa");
            npc.setHealth(npc.getMaxHealth());
            npc.triggerAnim("acao", "misc.salute");
            p.sendSystemMessage(Component.translatable("tropa.lanterna.resgatado").withStyle(s -> s.withColor(npc.corEnergia())));
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        SpaceBodies.Body b = SpaceBodies.bodyAt(event.getTo(), p.getX(), p.getZ());
        if (b == null || b == SpaceBodies.EARTH) return;
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d != null && d.visitar(b.id())) {
            Nivel.addXp(p, 150, Component.translatable("nivel.lanterna.descoberta", b.displayName()));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) Nivel.aplicar(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Nivel.aplicar(p);
            p.setHealth(p.getMaxHealth());
        }
    }

    // ===================== Aliados e emoção na briga =====================

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        // Jogador atacou: os Lanternas da mesma Tropa por perto entram na briga
        if (event.getSource().getEntity() instanceof ServerPlayer p && !(victim instanceof Player)) {
            BuiltinCorps mine = Factions.playerCorps(p);
            if (mine != null && !(victim instanceof LanternaEntity l && l.aliadoDe(p))) {
                for (LanternaEntity ally : victim.level().getEntitiesOfClass(LanternaEntity.class, p.getBoundingBox().inflate(24),
                        a -> a.aliadoDe(p) && a.getTarget() == null && a != victim)) ally.setTarget(victim);
            }
            float dmg = event.getAmount();
            if (is(p, BuiltinCorps.RED)) emocao(p, Math.round(dmg * 2));
            if (is(p, BuiltinCorps.GREEN) && p.getHealth() < p.getMaxHealth() * 0.5f) emocao(p, Math.round(dmg * 3));
        }
        // Jogador apanhou: os aliados defendem
        if (victim instanceof ServerPlayer p && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != p) {
            for (LanternaEntity ally : p.level().getEntitiesOfClass(LanternaEntity.class, p.getBoundingBox().inflate(24),
                    a -> a.aliadoDe(p) && a != attacker && !(attacker instanceof LanternaEntity l && l.tropa() == a.tropa()))) {
                if (ally.getTarget() == null) ally.setTarget(attacker);
            }
        }
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || event.getAmount() <= 0) return;
        if (e instanceof ServerPlayer p && is(p, BuiltinCorps.BLUE)) emocao(p, Math.round(event.getAmount() * 4));
        // Índigo: alguém se cura perto (não é o próprio)
        if (e instanceof Animal || e instanceof AbstractVillager || e instanceof Player) {
            for (ServerPlayer p : e.level().getEntitiesOfClass(ServerPlayer.class, e.getBoundingBox().inflate(12))) {
                if (p != e && is(p, BuiltinCorps.INDIGO)) emocao(p, Math.round(event.getAmount() * 5));
            }
        }
    }

    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && is(p, BuiltinCorps.ORANGE)) {
            emocao(p, Math.min(20, event.getItem().getItem().getCount()));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 20 != 11) return;
        BuiltinCorps c = Factions.playerCorps(p);
        if (c == null) return;
        if (c == BuiltinCorps.BLUE || c == BuiltinCorps.VIOLET) {
            int near = 0;
            for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(c == BuiltinCorps.BLUE ? 16 : 12), e -> e != p)) {
                if (e instanceof Player || (e instanceof TamableAnimal t && p.getUUID().equals(t.getOwnerUUID()))
                        || (e instanceof LanternaEntity l && l.aliadoDe(p))) near++;
            }
            if (near > 0) emocao(p, Math.min(6, near * (c == BuiltinCorps.BLUE ? 2 : 3)));
        }
        // Depois da Prova: o anel puxa um pouco de carga da própria emoção o tempo todo
        LoreData d = LoreCapability.get(p).orElse(null);
        RingData ring = ModCapabilities.get(p).orElse(null);
        if (d != null && d.autoRecarga() && ring != null && ring.hasRing() && p.tickCount % 40 == 11 && ring.charge() < ring.maxCharge()) {
            RingPowers.setCharge(p, ring, ring.charge() + 2);
        }
    }

    // ===================== Emoção =====================

    private static boolean is(Player p, BuiltinCorps c) {
        return Factions.playerCorps(p) == c;
    }

    /** Soma pontos de emoção (durante a Prova) e, depois dela, recarrega o anel. */
    public static void emocao(ServerPlayer p, int pontos) {
        if (pontos <= 0) return;
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null) return;
        boolean prova = d.active().keySet().stream().anyMatch(k -> k.startsWith("prova_"));
        if (prova) d.addEmocao(pontos);
        if (d.autoRecarga()) {
            RingData ring = ModCapabilities.get(p).orElse(null);
            if (ring != null && ring.hasRing() && ring.charge() < ring.maxCharge()) {
                RingPowers.setCharge(p, ring, Math.min(ring.maxCharge(), ring.charge() + pontos * 2));
            }
        }
    }

    private TropaEvents() {}
}
