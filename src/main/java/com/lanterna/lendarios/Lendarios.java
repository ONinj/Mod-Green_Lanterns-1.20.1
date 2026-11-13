package com.lanterna.lendarios;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.Especie;
import com.lanterna.especies.EspeciesEntities;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.lore.Factions;
import com.lanterna.lore.LoreItems;
import com.lanterna.tropa.Nivel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * O comportamento dos Lanternas lendários (são Lanternas NPC marcados com {@link #TAG}):
 *  - muita vida, muito dano, barra de chefe, uma onda de energia de tempos em tempos e, contra inimigos, chama ajudantes
 *    da Tropa aos 66% e aos 33% de vida; não saem da arena e se curam quando ficam sozinhos;
 *  - Tropa inimiga: luta de verdade. Ao morrer, todos que lutaram ganham muita experiência, a Insígnia do lendário e
 *    Fragmentos Lendários; ele volta em 30 minutos;
 *  - mesma Tropa ou aliada: clique nele para um duelo de treino. Ninguém morre: quem chega no fim da vida perde
 *    (o lendário para em 12%). Vencer dá experiência e Fragmentos (a Insígnia na primeira vitória); espera de 20 min.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Lendarios {
    public static final String TAG = "lanterna_lendario";
    private static final String CASA = "lanterna_lendario_casa", ONDA = "lanterna_lendario_onda", AJUDA = "lanterna_lendario_ajuda",
            CURAR = "lanterna_lendario_curar";
    private static final long ESPERA_DUELO_MS = 20L * 60 * 1000;
    private static final int XP_CHEFE = 4000, XP_DUELO = 1200;

    private static final Map<UUID, LanternaEntity> VIVOS = new HashMap<>();
    private static final Map<UUID, ServerBossEvent> BARRAS = new HashMap<>();
    private static final Map<UUID, Set<UUID>> LUTARAM = new HashMap<>();
    /** lendário -> duelo em andamento. */
    private static final Map<UUID, Duelo> DUELOS = new HashMap<>();
    private static final Map<UUID, Integer> SOZINHO = new HashMap<>();

    private record Duelo(UUID jogador, long inicio) {}

    @Nullable
    public static Lendario de(Entity e) {
        if (!(e instanceof LanternaEntity l)) return null;
        String id = l.getPersistentData().getString(TAG);
        return id.isEmpty() ? null : Lendario.byId(id);
    }

    // ------------------------------------------------------------------ criação

    public static LanternaEntity surgir(ServerLevel level, Lendario l, BlockPos centro) {
        LanternaEntity e = new LanternaEntity(EspeciesEntities.LANTERNA_NPC.get(), level);
        vestir(e, l);
        e.getPersistentData().putString(TAG, l.id);
        e.getPersistentData().putLong(CASA, centro.above().asLong());
        e.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(l.dano);
        var armor = e.getAttribute(Attributes.ARMOR);
        if (armor != null) armor.setBaseValue(12);
        var kb = e.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(0.8);
        var fr = e.getAttribute(Attributes.FOLLOW_RANGE);
        if (fr != null) fr.setBaseValue(48);
        e.setHealth((float) l.vida);
        e.setCustomName(l.nome().copy().withStyle(s -> s.withColor(l.corps.color()).withBold(true)));
        e.setCustomNameVisible(true);
        e.moveTo(centro.getX() + 0.5, centro.getY() + 0.1, centro.getZ() + 0.5 - 6, 0, 0);
        level.addFreshEntity(e);
        level.playSound(null, centro, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3f, 0.6f);
        raio(level, e.position(), l.corps.color(), 6, 60);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, e.getBoundingBox().inflate(64))) {
            p.sendSystemMessage(fala(l, inimigo(l, p) ? "chega_inimigo" : "chega_aliado"));
        }
        return e;
    }

    /** Espécie, traje e atributos do lendário (também atualiza os que nasceram numa versão antiga). */
    private static void vestir(LanternaEntity e, Lendario l) {
        float f = e.getMaxHealth() > 0 ? e.getHealth() / e.getMaxHealth() : 1f;
        e.setup(l.especie, l.pele, Especie.Papel.LANTERNA, l.corps, l.roupa(), true, l == Lendario.MAO_NEGRA);
        e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(l.vida);
        e.setHealth((float) l.vida * Math.max(0.05f, f));
    }

    private static Component fala(Lendario l, String chave) {
        return Component.literal("<").append(l.nome()).append("> ")
                .append(Component.translatable("lendario.lanterna." + l.id + "." + chave)).withStyle(s -> s.withColor(l.corps.color()));
    }

    /** Tropa do jogador é inimiga da do lendário? (sem anel: não é) */
    public static boolean inimigo(Lendario l, Player p) {
        BuiltinCorps c = Factions.playerCorps(p);
        return c != null && Factions.enemies(l.corps, c);
    }

    // ------------------------------------------------------------------ registro dos vivos

    @SubscribeEvent
    public static void entrou(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide) return;
        Lendario l = de(e.getEntity());
        if (l == null) return;
        LanternaEntity le = (LanternaEntity) e.getEntity();
        VIVOS.put(le.getUUID(), le);
        if (!l.especie.id.equals(le.getEspecie())) {
            vestir(le, l);
            le.setCustomName(l.nome().copy().withStyle(st -> st.withColor(l.corps.color()).withBold(true)));
        }
    }

    @SubscribeEvent
    public static void saiu(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide) return;
        UUID id = e.getEntity().getUUID();
        if (VIVOS.remove(id) != null) {
            ServerBossEvent b = BARRAS.remove(id);
            if (b != null) b.removeAllPlayers();
        }
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || VIVOS.isEmpty() || e.getServer().getTickCount() % 5 != 0) return;
        for (LanternaEntity le : VIVOS.values().toArray(new LanternaEntity[0])) {
            if (!le.isAlive() || !(le.level() instanceof ServerLevel level)) continue;
            Lendario l = de(le);
            if (l == null) continue;
            if (le.getPersistentData().getBoolean(CURAR)) {
                le.getPersistentData().remove(CURAR);
                le.setHealth(le.getMaxHealth());
            }
            barra(le, l, level);
            casa(le, l);
            duelo(le, l);
            onda(le, l, level);
            ajuda(le, l, level);
        }
    }

    private static void barra(LanternaEntity le, Lendario l, ServerLevel level) {
        ServerBossEvent b = BARRAS.computeIfAbsent(le.getUUID(), k -> {
            ServerBossEvent n = new ServerBossEvent(le.getDisplayName(), cor(l.corps), BossEvent.BossBarOverlay.NOTCHED_10);
            n.setDarkenScreen(false);
            return n;
        });
        b.setProgress(le.getHealth() / le.getMaxHealth());
        for (ServerPlayer p : level.players()) {
            boolean perto = p.distanceToSqr(le) < 56 * 56;
            if (perto && !b.getPlayers().contains(p)) b.addPlayer(p);
            else if (!perto && b.getPlayers().contains(p)) b.removePlayer(p);
        }
    }

    private static BossEvent.BossBarColor cor(BuiltinCorps c) {
        return switch (c) {
            case GREEN -> BossEvent.BossBarColor.GREEN;
            case YELLOW, ORANGE -> BossEvent.BossBarColor.YELLOW;
            case RED -> BossEvent.BossBarColor.RED;
            case BLUE, INDIGO -> BossEvent.BossBarColor.BLUE;
            case VIOLET -> BossEvent.BossBarColor.PINK;
            case BLACK -> BossEvent.BossBarColor.PURPLE;
            default -> BossEvent.BossBarColor.WHITE;
        };
    }

    /** Não sai da arena; sozinho por 30 s, volta para o trono e se cura. */
    private static void casa(LanternaEntity le, Lendario l) {
        BlockPos casa = BlockPos.of(le.getPersistentData().getLong(CASA));
        Vec3 c = Vec3.atBottomCenterOf(casa);
        if (le.position().distanceTo(c) > 34) {
            le.teleportTo(c.x, c.y + 0.5, c.z);
            le.getNavigation().stop();
            if (le.getTarget() != null && le.getTarget().position().distanceTo(c) > 40) le.setTarget(null);
        }
        boolean alguem = !le.level().getEntitiesOfClass(Player.class, le.getBoundingBox().inflate(48), p -> !p.isSpectator() && !p.isCreative()).isEmpty();
        int s = alguem ? 0 : SOZINHO.getOrDefault(le.getUUID(), 0) + 5;
        SOZINHO.put(le.getUUID(), s);
        if (s >= 600 && le.getHealth() < le.getMaxHealth()) {
            le.setHealth(le.getMaxHealth());
            le.getPersistentData().putInt(AJUDA, 0);
            LUTARAM.remove(le.getUUID());
        }
    }

    /** Onda de energia: a cada 8 a 12 s em combate, empurra e machuca quem está perto (no duelo, só o duelista). */
    private static void onda(LanternaEntity le, Lendario l, ServerLevel level) {
        LivingEntity alvo = le.getTarget();
        if (alvo == null || le.distanceTo(alvo) > 18) return;
        long agora = level.getGameTime();
        if (agora < le.getPersistentData().getLong(ONDA)) return;
        le.getPersistentData().putLong(ONDA, agora + 160 + level.random.nextInt(80));
        Duelo d = DUELOS.get(le.getUUID());
        raio(level, le.position().add(0, 1, 0), l.corps.color(), 7, 90);
        level.playSound(null, le.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.2f, 1.4f);
        for (LivingEntity v : level.getEntitiesOfClass(LivingEntity.class, le.getBoundingBox().inflate(7.5), x -> x != le)) {
            boolean vale = d != null ? v.getUUID().equals(d.jogador()) : le.hostilA(v);
            if (!vale) continue;
            v.hurt(le.damageSources().mobAttack(le), (float) l.dano * 0.6f);
            Vec3 k = v.position().subtract(le.position()).normalize().scale(1.6);
            v.push(k.x, 0.6, k.z);
            v.hurtMarked = true;
        }
    }

    /** Contra inimigos: chama dois Lanternas da Tropa aos 66% e aos 33% de vida. */
    private static void ajuda(LanternaEntity le, Lendario l, ServerLevel level) {
        if (DUELOS.containsKey(le.getUUID()) || !(le.getTarget() instanceof Player alvo)) return;
        int feitas = le.getPersistentData().getInt(AJUDA);
        float f = le.getHealth() / le.getMaxHealth();
        if ((feitas == 0 && f < 0.66f) || (feitas == 1 && f < 0.33f)) {
            le.getPersistentData().putInt(AJUDA, feitas + 1);
            le.level().getServer().getPlayerList().getPlayers().stream().filter(p -> p.level() == level && p.distanceToSqr(le) < 64 * 64)
                    .forEach(p -> p.sendSystemMessage(fala(l, "ajuda")));
            for (int i = 0; i < 2; i++) {
                LanternaEntity a = LanternaEntity.lanterna(level, l.corps, true);
                double ang = level.random.nextDouble() * Math.PI * 2;
                a.moveTo(le.getX() + Math.cos(ang) * 4, le.getY() + 2, le.getZ() + Math.sin(ang) * 4, 0, 0);
                a.getPersistentData().putBoolean("lanterna_encontro", true);
                level.addFreshEntity(a);
                a.setTarget(alvo);
                raio(level, a.position(), l.corps.color(), 2, 20);
            }
        }
    }

    private static void raio(ServerLevel level, Vec3 c, int cor, double r, int n) {
        var op = new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 2.0f);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            level.sendParticles(op, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0.1, 0, 0);
        }
    }

    // ------------------------------------------------------------------ dano

    @SubscribeEvent
    public static void apanhou(LivingHurtEvent e) {
        // o lendário bate forte
        Lendario atk = de(e.getSource().getEntity());
        if (atk != null && e.getSource().getEntity() != e.getEntity()) e.setAmount(e.getAmount() * 1.4f);
        // quem bateu no lendário entra na lista das recompensas
        if (de(e.getEntity()) != null && e.getSource().getEntity() instanceof ServerPlayer p)
            LUTARAM.computeIfAbsent(e.getEntity().getUUID(), k -> new HashSet<>()).add(p.getUUID());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void dano(LivingDamageEvent e) {
        // duelista: nunca morre pelo lendário (perde o duelo)
        if (e.getEntity() instanceof ServerPlayer p && de(e.getSource().getEntity()) != null) {
            LanternaEntity le = (LanternaEntity) e.getSource().getEntity();
            Duelo d = DUELOS.get(le.getUUID());
            if (d != null && d.jogador().equals(p.getUUID()) && e.getAmount() >= p.getHealth()) {
                e.setAmount(Math.max(0, p.getHealth() - 1));
                fimDuelo(le, p, false);
            }
            return;
        }
        Lendario l = de(e.getEntity());
        if (l == null) return;
        LanternaEntity le = (LanternaEntity) e.getEntity();
        Duelo d = DUELOS.get(le.getUUID());
        if (d == null) return;
        Entity quem = e.getSource().getEntity();
        // no duelo, só o duelista bate
        if (quem == null || !quem.getUUID().equals(d.jogador())) {
            e.setAmount(0);
            return;
        }
        float piso = le.getMaxHealth() * 0.12f;
        if (le.getHealth() - e.getAmount() <= piso) {
            e.setAmount(Math.max(0, le.getHealth() - piso));
            if (quem instanceof ServerPlayer sp) fimDuelo(le, sp, true);
        }
    }

    @SubscribeEvent
    public static void morreu(LivingDeathEvent e) {
        if (!(e.getEntity().level() instanceof ServerLevel level)) return;
        // duelista morreu de outra coisa: acaba o duelo
        if (e.getEntity() instanceof ServerPlayer p) {
            DUELOS.entrySet().removeIf(x -> x.getValue().jogador().equals(p.getUUID()));
            return;
        }
        Lendario l = de(e.getEntity());
        if (l == null) return;
        LanternaEntity le = (LanternaEntity) e.getEntity();
        DUELOS.remove(le.getUUID());
        var s = ArenaLendaria.estado(level.getServer(), l);
        s.vivo = false;
        s.volta = level.getGameTime() + ArenaLendaria.VOLTA;
        ArenaLendaria.marcar(level.getServer());
        Set<UUID> lutaram = LUTARAM.remove(le.getUUID());
        if (lutaram == null) lutaram = new HashSet<>();
        if (e.getSource().getEntity() instanceof ServerPlayer matou) lutaram.add(matou.getUUID());
        Component anuncio = Component.translatable("lendario.lanterna.derrotado", l.nome(),
                e.getSource().getEntity() != null ? e.getSource().getEntity().getDisplayName() : Component.literal("?"))
                .withStyle(st -> st.withColor(l.corps.color()).withBold(true));
        level.getServer().getPlayerList().broadcastSystemMessage(anuncio, false);
        for (UUID id : lutaram) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p == null || p.distanceToSqr(le) > 128 * 128) continue;
            Nivel.addXp(p, XP_CHEFE, l.nome());
            dar(p, insignia(l));
            dar(p, new ItemStack(LoreItems.FRAGMENTO_LENDARIO.get(), 3));
            dar(p, new ItemStack(Items.DIAMOND, 3));
            p.getPersistentData().putBoolean("lanterna_venceu_" + l.id, true);
        }
        raio(level, le.position().add(0, 1, 0), l.corps.color(), 5, 80);
    }

    private static void dar(ServerPlayer p, ItemStack s) {
        ItemHandlerHelper.giveItemToPlayer(p, s);
    }

    public static ItemStack insignia(Lendario l) {
        var it = LoreItems.INSIGNIAS.get(l);
        return it == null ? ItemStack.EMPTY : new ItemStack(it.get());
    }

    // ------------------------------------------------------------------ duelo

    /** Clique no lendário. */
    public static InteractionResult interagir(ServerPlayer p, LanternaEntity le) {
        Lendario l = de(le);
        if (l == null) return InteractionResult.PASS;
        le.getLookControl().setLookAt(p);
        if (p.isCreative() || p.isSpectator()) {
            p.sendSystemMessage(fala(l, "aliado"));
            return InteractionResult.CONSUME;
        }
        if (inimigo(l, p)) {
            p.sendSystemMessage(fala(l, "inimigo"));
            le.setTarget(p);
            return InteractionResult.CONSUME;
        }
        Duelo atual = DUELOS.get(le.getUUID());
        if (atual != null) {
            if (!atual.jogador().equals(p.getUUID())) p.displayClientMessage(Component.translatable("lendario.lanterna.ocupado", l.nome()), true);
            return InteractionResult.CONSUME;
        }
        if (Factions.playerCorps(p) == null) {
            p.sendSystemMessage(fala(l, "sem_anel"));
            return InteractionResult.CONSUME;
        }
        long espera = p.getPersistentData().getLong("lanterna_duelo_" + l.id) - System.currentTimeMillis();
        if (espera > 0) {
            p.sendSystemMessage(fala(l, "descansa"));
            p.displayClientMessage(Component.translatable("lendario.lanterna.espera", (espera / 60000) + 1).withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        DUELOS.put(le.getUUID(), new Duelo(p.getUUID(), le.level().getGameTime()));
        le.setHealth(le.getMaxHealth());
        p.sendSystemMessage(fala(l, "duelo"));
        p.displayClientMessage(Component.translatable("lendario.lanterna.duelo_inicio", l.nome()).withStyle(s -> s.withColor(l.corps.color())), true);
        le.level().playSound(null, le.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1f, 1.4f);
        le.irritar(p);
        return InteractionResult.CONSUME;
    }

    /** Duelo passando de 3 minutos, ou o duelista sumiu: acaba sem vencedor. */
    private static void duelo(LanternaEntity le, Lendario l) {
        Duelo d = DUELOS.get(le.getUUID());
        if (d == null) return;
        ServerPlayer p = le.level().getServer().getPlayerList().getPlayer(d.jogador());
        boolean longe = p == null || p.level() != le.level() || p.distanceToSqr(le) > 60 * 60;
        if (longe || le.level().getGameTime() - d.inicio() > 20 * 180) {
            DUELOS.remove(le.getUUID());
            if (p != null) {
                le.acalmar(p);
                p.sendSystemMessage(fala(l, "duelo_fim"));
            }
            le.setHealth(le.getMaxHealth());
        }
    }

    private static void fimDuelo(LanternaEntity le, ServerPlayer p, boolean venceu) {
        Lendario l = de(le);
        if (l == null || DUELOS.remove(le.getUUID()) == null) return;
        le.acalmar(p);
        le.getPersistentData().putBoolean(CURAR, true);   // cura no próximo tick (o golpe ainda vai ser aplicado)
        p.getPersistentData().putLong("lanterna_duelo_" + l.id, System.currentTimeMillis() + ESPERA_DUELO_MS);
        if (venceu) {
            p.sendSystemMessage(fala(l, "perdeu"));
            Nivel.addXp(p, XP_DUELO, l.nome());
            dar(p, new ItemStack(LoreItems.FRAGMENTO_LENDARIO.get(), 1));
            String primeira = "lanterna_venceu_" + l.id;
            if (!p.getPersistentData().getBoolean(primeira) || p.getRandom().nextInt(4) == 0) dar(p, insignia(l));
            p.getPersistentData().putBoolean(primeira, true);
            com.lanterna.hanks.HanksMissoes.venceuDuelo(p, l);
            le.level().playSound(null, le.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        } else {
            p.sendSystemMessage(fala(l, "venceu"));
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 20 * 8, 2));
            Nivel.addXp(p, XP_DUELO / 5, l.nome());
        }
    }

    private Lendarios() {}
}
