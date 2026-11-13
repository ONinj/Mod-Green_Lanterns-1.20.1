package com.lanterna.fonte;

import com.lanterna.LanternaMod;
import com.lanterna.baterias.Tropa;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.lore.LoreEntities;
import com.lanterna.lore.PlanetLore;
import com.lanterna.lore.entity.Race;
import com.lanterna.lore.entity.RaceEntity;
import com.lanterna.mission.LoreCapability;
import com.lanterna.mission.MissionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * As provações da emoção de cada Tropa, na praça da Bateria Central (etapa 2 do Chamado da Fonte):
 *
 *   Verde     Vontade     três ondas de inimigos sem sair do círculo da Bateria
 *   Amarelo   Medo        caçar dez sombras na escuridão, com o pavor apertando
 *   Vermelho  Fúria       duelo com o Campeão de Gorrak
 *   Laranja   Avareza     entregar à Bateria o que você mais guarda
 *   Azul      Esperança   proteger três Aelínos de um ataque pirata por 90 s
 *   Índigo    Compaixão   curar um pirata ferido (e protegê-lo dos Ecos) em vez de matá-lo
 *   Violeta   Amor        reencontrar o par separado e trazê-lo de volta
 *
 * Morrer ou sair da cidade falha a prova; dá para tentar de novo clicando na Bateria.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProvaFonte {
    private static final class Estado {
        final Tropa tropa;
        final BlockPos centro;
        int ticks, onda, mortes;
        final List<Entity> criados = new ArrayList<>();
        @Nullable Entity especial, par;
        @Nullable ItemStack pedido;
        boolean seguindo;

        Estado(Tropa t, BlockPos c) {
            tropa = t;
            centro = c;
        }
    }

    private static final Map<UUID, Estado> PROVAS = new HashMap<>();

    public static boolean ativa(ServerPlayer p) {
        return PROVAS.containsKey(p.getUUID());
    }

    public static void comecar(ServerPlayer p, Tropa t, BlockPos bateria) {
        ServerLevel l = p.serverLevel();
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PRACAx(), PRACAz());
        Estado e = new Estado(t, new BlockPos(PRACAx(), y, PRACAz()));
        PROVAS.put(p.getUUID(), e);
        p.sendSystemMessage(Component.translatable("prova.lanterna." + t.nome + ".titulo").withStyle(s -> s.withColor(t.cor).withBold(true)));
        p.sendSystemMessage(Component.translatable("prova.lanterna." + t.nome + ".desc"));
        l.playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.get(), SoundSource.PLAYERS, 0.9f, 1.2f);
        switch (t) {
            case VERMELHO -> {
                LanternaEntity c = LanternaEntity.lanterna(l, com.lanterna.corps.BuiltinCorps.RED, true);
                forte(c, 4.0, 1.6);
                c.setCustomName(Component.translatable("prova.lanterna.campeao"));
                c.setCustomNameVisible(true);
                surgir(l, e, c, 14);
                c.setTarget(p);
                e.especial = c;
            }
            case LARANJA -> {
                e.pedido = maisValioso(p);
                p.sendSystemMessage(Component.translatable("prova.lanterna.laranja.pede", e.pedido.getCount(), e.pedido.getHoverName())
                        .withStyle(s -> s.withColor(t.cor)));
            }
            case AZUL -> {
                Race r = Race.ofPlanet(PlanetLore.ODYM.planet);
                for (int i = 0; i < 3; i++) {
                    RaceEntity n = com.lanterna.lore.world.PlanetSites.nativo(l, r, e.centro.offset(-3 + 3 * i, 0, -8));
                    if (n != null) { n.setCustomName(Component.translatable("prova.lanterna.azul.protegido")); n.setCustomNameVisible(true); e.criados.add(n); }
                }
            }
            case INDIGO -> {
                LanternaEntity f = LanternaEntity.pirata(l, false);
                surgir(l, e, f, 6);
                f.setHealth(f.getMaxHealth() * 0.2f);
                f.setCustomName(Component.translatable("prova.lanterna.indigo.ferido"));
                f.setCustomNameVisible(true);
                f.setNoAi(true);
                e.especial = f;
            }
            case VIOLETA -> {
                Race r = Race.ofPlanet(PlanetLore.ZAMARON.planet);
                RaceEntity a = com.lanterna.lore.world.PlanetSites.nativo(l, r, e.centro.offset(0, 0, -10));
                // o par perdido está numa casa longe da praça
                List<BlockPos> casas = com.lanterna.cidade.CidadesPovos.casas(com.lanterna.cidade.CidadeNativa.CENTRO.atY(e.centro.getY()));
                BlockPos longe = casas.get((casas.size() / 2 + l.random.nextInt(casas.size() / 3)) % casas.size());
                l.getChunkAt(longe);
                RaceEntity b = com.lanterna.lore.world.PlanetSites.nativo(l, r, longe);
                if (a != null && b != null) {
                    a.setCustomName(Component.translatable("prova.lanterna.violeta.a")); a.setCustomNameVisible(true);
                    b.setCustomName(Component.translatable("prova.lanterna.violeta.b")); b.setCustomNameVisible(true);
                    b.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60 * 8, 0, false, false));
                    e.especial = a;
                    e.par = b;
                    e.criados.add(a);
                    e.criados.add(b);
                    p.sendSystemMessage(Component.translatable("prova.lanterna.violeta.onde", longe.getX(), longe.getZ()).withStyle(s -> s.withColor(t.cor)));
                }
            }
            default -> {}
        }
    }

    private static int PRACAx() { return FonteChamado.PRACA.getX(); }
    /** O centro da prova é a frente da Bateria (lado norte da praça, onde fica a entrada). */
    private static int PRACAz() { return FonteChamado.PRACA.getZ() - 13; }

    /** Onde fica a Bateria Central em relação ao centro da prova (13 blocos ao sul). */
    private static BlockPos bateria(Estado e) {
        return e.centro.offset(0, 0, 13);
    }

    private static void forte(Mob m, double vida, double dano) {
        var hp = m.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(hp.getBaseValue() * vida);
        var atk = m.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(atk.getBaseValue() * dano);
        m.setHealth(m.getMaxHealth());
    }

    private static void surgir(ServerLevel l, Estado e, Mob m, double raio) {
        // em volta do centro da prova, no chão, nunca em cima da Bateria Central
        BlockPos onde = LugarDeSurgir.achar(l, e.centro, raio * 0.8, raio, bateria(e));
        int x = onde.getX(), y = onde.getY(), z = onde.getZ();
        m.moveTo(x + 0.5, y, z + 0.5, l.random.nextFloat() * 360, 0);
        m.finalizeSpawn(l, l.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null);
        m.setPersistenceRequired();
        l.addFreshEntity(m);
        e.criados.add(m);
        l.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, m.getX(), m.getY() + 1, m.getZ(), 30, 0.4, 0.8, 0.4, 0.1);
    }

    private static Mob inimigo(ServerLevel l, boolean eco) {
        if (eco) {
            Mob m = LoreEntities.ECO_CINZENTO.get().create(l);
            if (m != null) return m;
        }
        return LanternaEntity.pirata(l, false);
    }

    /** O item de maior valor que o jogador carrega (a Avareza pede justamente ele). */
    private static ItemStack maisValioso(ServerPlayer p) {
        Object[][] valor = {{Items.NETHERITE_INGOT, 100, 1}, {Items.NETHERITE_SCRAP, 40, 2}, {Items.DIAMOND_BLOCK, 90, 1}, {Items.DIAMOND, 10, 8},
                {Items.EMERALD_BLOCK, 50, 1}, {Items.EMERALD, 5, 16}, {Items.GOLD_BLOCK, 30, 2}, {Items.GOLD_INGOT, 3, 24}};
        for (Object[] v : valor) {
            var item = (net.minecraft.world.item.Item) v[0];
            if (MissionManager.countItem(p, item) >= (int) v[2]) return new ItemStack(item, (int) v[2]);
        }
        return new ItemStack(com.lanterna.lore.LoreItems.MERITO.get(), 8);
    }

    // ===================== Andamento =====================

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p)) return;
        Estado e = PROVAS.get(p.getUUID());
        if (e == null) return;
        ServerLevel l = p.serverLevel();
        e.ticks++;
        double dist = Math.sqrt(p.blockPosition().distSqr(e.centro));
        if (!p.isAlive() || dist > 260 || PlanetLore.byDimension(l.dimension()) == null) { falhar(p, e, "longe"); return; }
        e.criados.removeIf(x -> !x.isAlive() && !(x == e.especial || x == e.par));
        switch (e.tropa) {
            case VERDE -> {
                if (dist > 16) { falhar(p, e, "verde"); return; }
                if (contaVivos(e) == 0 && e.ticks > 20) {
                    if (e.onda >= 3) { vencer(p, e); return; }
                    e.onda++;
                    int n = 2 + e.onda * 2;
                    for (int i = 0; i < n; i++) { Mob m = inimigo(l, i % 2 == 0); surgir(l, e, m, 15); m.setTarget(p); }
                    p.displayClientMessage(Component.translatable("prova.lanterna.onda", e.onda, 3).withStyle(s -> s.withColor(e.tropa.cor)), true);
                }
            }
            case AMARELO -> {
                if (e.ticks % 80 == 1) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false));
                if (e.ticks % 200 == 1) p.addEffect(new MobEffectInstance(com.lanterna.construct.ModEfeitos.PAVOR.get(), 60, 0, false, false));
                if (contaVivos(e) < 2 && e.mortes + contaVivos(e) < 10) {
                    Mob m = inimigo(l, true);
                    Vec3 tras = p.position().subtract(p.getLookAngle().scale(7));
                    BlockPos atras = new BlockPos((int) Math.floor(tras.x), l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(tras.x), (int) Math.floor(tras.z)), (int) Math.floor(tras.z));
                    // atrás do jogador; se "atrás" for em cima da Bateria, em volta dele no chão
                    if (!LugarDeSurgir.serve(l, atras, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, e.centro.getX(), e.centro.getZ()), bateria(e)))
                        atras = LugarDeSurgir.achar(l, p.blockPosition(), 6, 9, bateria(e));
                    m.moveTo(atras.getX() + 0.5, atras.getY(), atras.getZ() + 0.5, 0, 0);
                    m.finalizeSpawn(l, l.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null);
                    m.setPersistenceRequired();
                    l.addFreshEntity(m);
                    e.criados.add(m);
                    m.setTarget(p);
                    l.playSound(null, m.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.4f, 0.8f);
                }
                if (e.mortes >= 10) vencer(p, e);
                else if (e.ticks > 20 * 180) falhar(p, e, "tempo");
            }
            case VERMELHO -> {
                if (e.especial == null || !e.especial.isAlive()) vencer(p, e);
                else if (e.ticks > 20 * 240) falhar(p, e, "tempo");
                else if (e.ticks % 40 == 0 && e.especial instanceof Mob m) m.setTarget(p);
            }
            case LARANJA -> {
                if (e.ticks > 20 * 120) falhar(p, e, "tempo");
            }
            case AZUL -> {
                long vivos = e.criados.stream().filter(x -> x instanceof RaceEntity && x.isAlive()).count();
                if (vivos < 2) { falhar(p, e, "azul"); return; }
                if (e.ticks % 200 == 20 && e.ticks < 20 * 80) {
                    for (int i = 0; i < 3; i++) { Mob m = inimigo(l, false); surgir(l, e, m, 16); }
                }
                if (e.ticks % 20 == 0) for (Entity x : e.criados) if (x instanceof LanternaEntity pir && x.isAlive()) {
                    LivingEntity alvo = (LivingEntity) e.criados.stream().filter(y -> y instanceof RaceEntity && y.isAlive()).findFirst().orElse(p);
                    pir.setTarget(alvo);
                }
                if (e.ticks >= 20 * 90) vencer(p, e);
            }
            case INDIGO -> {
                if (!(e.especial instanceof LivingEntity f) || !f.isAlive()) { falhar(p, e, "indigo"); return; }
                if (e.ticks % 240 == 40 && e.ticks < 20 * 150) {
                    for (int i = 0; i < 2; i++) { Mob m = inimigo(l, true); surgir(l, e, m, 14); m.setTarget(f); }
                }
                if (e.ticks % 20 == 0) for (Entity x : e.criados) if (x instanceof Mob m && x != f && x.isAlive()) m.setTarget(f);
                if (f.getHealth() >= f.getMaxHealth() - 0.01f) vencer(p, e);
                else if (e.ticks > 20 * 240) falhar(p, e, "tempo");
            }
            case VIOLETA -> {
                if (e.especial == null || e.par == null || !e.especial.isAlive() || !e.par.isAlive()) { falhar(p, e, "violeta"); return; }
                if (e.seguindo && e.par instanceof Mob b && e.ticks % 10 == 0) {
                    if (b.distanceTo(p) > 3) b.getNavigation().moveTo(p, 1.1);
                    if (b.distanceTo(p) > 24) b.teleportTo(p.getX(), p.getY(), p.getZ());
                }
                if (e.par.distanceTo(e.especial) < 6) vencer(p, e);
                else if (e.ticks > 20 * 480) falhar(p, e, "tempo");
            }
        }
    }

    private static long contaVivos(Estado e) {
        return e.criados.stream().filter(x -> x instanceof Mob && !(x instanceof RaceEntity) && x.isAlive() && x != e.especial).count();
    }

    /** Clique na Bateria durante a prova (a Avareza entrega o item aqui). */
    public static void usarBateria(ServerPlayer p) {
        Estado e = PROVAS.get(p.getUUID());
        if (e == null || e.tropa != Tropa.LARANJA || e.pedido == null) return;
        ItemStack mao = p.getItemInHand(InteractionHand.MAIN_HAND);
        if (mao.is(e.pedido.getItem()) && mao.getCount() >= e.pedido.getCount()) {
            mao.shrink(e.pedido.getCount());
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.2f, 0.5f);
            vencer(p, e);
        } else {
            p.displayClientMessage(Component.translatable("prova.lanterna.laranja.pede", e.pedido.getCount(), e.pedido.getHoverName()), true);
        }
    }

    @SubscribeEvent
    public static void onMorte(LivingDeathEvent ev) {
        if (!(ev.getSource().getEntity() instanceof ServerPlayer p)) return;
        Estado e = PROVAS.get(p.getUUID());
        if (e == null) return;
        if (e.criados.contains(ev.getEntity())) {
            e.mortes++;
            if (ev.getEntity() == e.especial && e.tropa == Tropa.INDIGO) falhar(p, e, "indigo");
        }
    }

    /** Índigo: comida cura o ferido. Violeta: falar com o par perdido faz ele seguir você. */
    @SubscribeEvent
    public static void onInteragir(PlayerInteractEvent.EntityInteract ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        Estado e = PROVAS.get(p.getUUID());
        if (e == null) return;
        if (e.tropa == Tropa.INDIGO && ev.getTarget() == e.especial && ev.getTarget() instanceof LivingEntity f) {
            ItemStack s = ev.getItemStack();
            if (s.isEdible() || s.is(Items.POTION) || s.is(Items.GOLDEN_APPLE)) {
                f.heal(s.is(Items.GOLDEN_APPLE) ? 20 : 6);
                if (!p.isCreative()) s.shrink(1);
                p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, f.getX(), f.getY() + f.getBbHeight(), f.getZ(), 4, 0.3, 0.2, 0.3, 0);
                p.displayClientMessage(Component.translatable("prova.lanterna.indigo.cura", Math.round(f.getHealth()), Math.round(f.getMaxHealth())), true);
                ev.setCanceled(true);
            }
        }
        if (e.tropa == Tropa.VIOLETA && ev.getTarget() == e.par && !e.seguindo) {
            e.seguindo = true;
            p.displayClientMessage(Component.translatable("prova.lanterna.violeta.segue").withStyle(s -> s.withColor(e.tropa.cor)), true);
            ev.setCanceled(true);
        }
    }

    private static void vencer(ServerPlayer p, Estado e) {
        acabar(e);
        PROVAS.remove(p.getUUID());
        FonteDados.chamado(p, e.tropa.nome + ":prova", 1);
        FonteDados.essencia(p, FonteDados.essencia(p) + 5);
        p.sendSystemMessage(Component.translatable("prova.lanterna.vitoria").withStyle(s -> s.withColor(e.tropa.cor).withBold(true)));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        var d = LoreCapability.get(p).orElse(null);
        if (d != null) MissionManager.evaluate(p, d);
    }

    private static void falhar(ServerPlayer p, Estado e, String motivo) {
        acabar(e);
        PROVAS.remove(p.getUUID());
        p.sendSystemMessage(Component.translatable("prova.lanterna.falhou." + motivo).withStyle(s -> s.withColor(0xFF6060)));
        p.sendSystemMessage(Component.translatable("prova.lanterna.de_novo"));
    }

    private static void acabar(Estado e) {
        for (Entity x : e.criados) if (x.isAlive()) x.discard();
        if (e.especial != null && e.especial.isAlive()) e.especial.discard();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent ev) {
        Estado e = PROVAS.remove(ev.getEntity().getUUID());
        if (e != null) acabar(e);
    }

    private ProvaFonte() {}
}
