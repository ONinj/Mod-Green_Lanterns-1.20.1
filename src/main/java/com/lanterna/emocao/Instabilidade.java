package com.lanterna.emocao;

import com.lanterna.LanternaConfig;
import com.lanterna.LanternaMod;
import com.lanterna.baterias.BateriaNucleoBlockEntity;
import com.lanterna.baterias.Tropa;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.lore.Factions;
import com.lanterna.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * 0.59.1: as emoções instáveis e a imersão na Bateria Central.
 *
 * INSTABILIDADE (depois que o Anel da Vida se parte na saga do Necron: as emoções ficaram instáveis)
 *  - A emoção do anel se acumula com o que o portador vive: levar dano, ferir, matar, ficar com pouca vida. Cada Tropa
 *    sente mais uma coisa: a Raiva (vermelho) ferve mais rápido; o Medo (amarelo) cresce com pouca vida; a Avareza
 *    (laranja) a cada item recolhido; o Amor (violeta) e a Compaixão (índigo) quando alguém próximo dele é ferido.
 *  - Fora de combate ela baixa sozinha.
 *  - No limite, o anel exige controle: o portador tem 10 s para recitar o juramento da Tropa (a mesma tela do
 *    juramento da Bateria Central). Conseguiu: ganha 1 de maestria daquela emoção. Não conseguiu (ou fugiu): FRENESI
 *    por 20 s — mais força e velocidade, constructos 30% mais fortes, mas a fome dispara e, no fim, vem o cansaço.
 *  - Maestria: cada vez que resiste o limite sobe (a emoção demora mais para transbordar). Com DOMINIO resistências
 *    o portador domina a emoção (no futuro: entrar em frenesi de propósito).
 *
 * IMERSÃO (o Larfleeze ficou bilhões de anos ao lado do Ofidiano, a entidade da Avareza, dentro da Bateria Central
 * laranja, e se tornou o mais poderoso da Tropa)
 *  - Cada entidade emocional mora na Bateria Central da sua Tropa. Ficar perto dela com o anel daquela Tropa banha o
 *    portador naquela luz: o tempo acumula e, em 4 níveis (30 min, 2 h, 5 h e 10 h), os constructos ficam até 16% mais
 *    fortes para sempre. O preço: quanto mais imerso, mais instável a emoção (o limite baixa).
 *
 * Tudo fica no NBT persistente do jogador ("lanterna_emocao").
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Instabilidade {
    public static final String TIPO = "frenesi";
    public static final int DOMINIO = 10;
    static final int DUR_FRENESI = 20 * 20, DUR_CANSACO = 10 * 20, PRAZO = 10 * 20;
    static final int[] NIVEIS_IMERSAO = {1800, 7200, 18000, 36000};

    // ===================== dados =====================

    static CompoundTag dados(Player p) {
        CompoundTag t = p.getPersistentData();
        if (!t.contains("lanterna_emocao")) t.put("lanterna_emocao", new CompoundTag());
        return t.getCompound("lanterna_emocao");
    }

    @Nullable
    public static Tropa tropa(Player p) {
        BuiltinCorps c = Factions.playerCorps(p);
        if (c == null) return null;
        for (Tropa t : Tropa.values()) if (t.corps() == c) return t;
        return null;
    }

    /** As emoções estão instáveis para este jogador (sistema ligado e o Anel da Vida já se partiu)? */
    public static boolean instavel(Player p) {
        return LanternaConfig.INSTABILIDADE.get() && com.lanterna.necron.SagaNecron.tag(p).getBoolean("dividido");
    }

    public static int maestria(Player p, Tropa t) { return dados(p).getCompound("maestria").getInt(t.nome); }

    public static int imersao(Player p, Tropa t) { return dados(p).getCompound("imersao").getInt(t.nome); }

    public static int nivelImersao(Player p, Tropa t) {
        int s = imersao(p, t), n = 0;
        for (int lim : NIVEIS_IMERSAO) if (s >= lim) n++;
        return n;
    }

    public static float limite(Player p, Tropa t) {
        return 100 + 12 * Math.min(maestria(p, t), DOMINIO) - 6 * nivelImersao(p, t);
    }

    public static float medidor(Player p) { return dados(p).getFloat("medidor"); }

    static long agora(ServerPlayer p) { return p.serverLevel().getGameTime(); }

    public static boolean emFrenesi(Player p) {
        return p instanceof ServerPlayer sp && dados(p).getLong("frenesi") > agora(sp);
    }

    /** Multiplica o poder dos constructos: frenesi (+30%) e a imersão na Bateria (+4% por nível). */
    public static float poder(Player p) {
        float k = 1;
        Tropa t = tropa(p);
        if (t != null && LanternaConfig.IMERSAO.get()) k += 0.04f * nivelImersao(p, t);
        if (emFrenesi(p)) k *= 1.3f;
        return k;
    }

    // ===================== a emoção se acumula =====================

    static void somar(ServerPlayer p, float v) {
        if (v <= 0 || !instavel(p)) return;
        Tropa t = tropa(p);
        if (t == null) return;
        CompoundTag d = dados(p);
        if (d.getLong("frenesi") > agora(p)) return;
        d.putFloat("medidor", Math.min(limite(p, t) * 1.2f, d.getFloat("medidor") + v));
        d.putLong("combate", agora(p));
    }

    @SubscribeEvent
    public static void ferido(LivingHurtEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) return;
        float dano = e.getAmount();
        if (v instanceof ServerPlayer p) {
            Tropa t = tropa(p);
            if (t != null) somar(p, dano * (t == Tropa.VERMELHO ? 1.5f : 1f));
        }
        if (e.getSource().getEntity() instanceof ServerPlayer a && a != v) {
            Tropa t = tropa(a);
            if (t != null) somar(a, dano * (t == Tropa.VERMELHO ? 0.6f : 0.35f));
        }
        // Amor e Compaixão: alguém próximo (bicho dele, aldeão, outro jogador) sendo ferido
        if (!(v instanceof Enemy)) {
            for (ServerPlayer p : ((ServerLevel) v.level()).getEntitiesOfClass(ServerPlayer.class, v.getBoundingBox().inflate(12))) {
                if (p == v || p == e.getSource().getEntity()) continue;
                Tropa t = tropa(p);
                boolean dele = v instanceof OwnableEntity o && p.getUUID().equals(o.getOwnerUUID());
                if (t == Tropa.VIOLETA && (dele || v instanceof Player)) somar(p, dano * 1.2f);
                else if (t == Tropa.INDIGO && (dele || v instanceof Player || v instanceof AbstractVillager)) somar(p, dano);
            }
        }
    }

    @SubscribeEvent
    public static void morte(LivingDeathEvent e) {
        if (e.getSource().getEntity() instanceof ServerPlayer p && (e.getEntity() instanceof Enemy || e.getEntity() instanceof Player)) {
            Tropa t = tropa(p);
            if (t != null) somar(p, t == Tropa.VERMELHO ? 6 : 4);
        }
    }

    @SubscribeEvent
    public static void recolheu(EntityItemPickupEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && tropa(p) == Tropa.LARANJA) somar(p, 0.6f);
    }

    // ===================== a cada segundo =====================

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 7) return;
        CompoundTag d = dados(p);
        long agora = agora(p);
        Tropa t = tropa(p);
        if (p.tickCount % 100 == 7) imersao(p, t, d);
        // frenesi: a luz transborda
        long fr = d.getLong("frenesi");
        if (fr > agora) {
            int cor = t != null ? t.cor : 0xFF3A20;
            ((ServerLevel) p.level()).sendParticles(new DustParticleOptions(new org.joml.Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 1.6f),
                    p.getX(), p.getY() + 1, p.getZ(), 14, 0.45, 0.7, 0.45, 0);
            p.causeFoodExhaustion(1.6f);
            if (fr - agora <= 20) {
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DUR_CANSACO, 0, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DUR_CANSACO, 0, false, true));
                p.displayClientMessage(Component.translatable("emocao.lanterna.frenesi_fim").withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }
        if (t == null || !instavel(p)) return;
        // a prova em andamento: passou do prazo sem resposta = frenesi
        long prova = d.getLong("prova");
        if (prova > 0) {
            if (agora > prova + 60) resposta(p, 0);
            return;
        }
        float m = d.getFloat("medidor");
        // Medo: com pouca vida, cresce
        if (p.getHealth() < p.getMaxHealth() * 0.3f) m += t == Tropa.AMARELO ? 3 : 1.2f;
        // fora de combate (10 s sem ferir nem ser ferido), baixa
        if (agora - d.getLong("combate") > 200) m = Math.max(0, m - 2.5f);
        d.putFloat("medidor", m);
        if (m >= limite(p, t) && agora >= d.getLong("pausa")) provar(p, t, d);
    }

    /** O anel exige controle: abre o juramento com 10 s. */
    static void provar(ServerPlayer p, Tropa t, CompoundTag d) {
        d.putLong("prova", agora(p) + PRAZO);
        d.putString("provaTropa", t.nome);
        p.level().playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5f, 0.7f);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 0.5f);
        ModNetwork.sendTo(p, new com.lanterna.juramento.Juramento.Abrir(TIPO, t.nome, t.cor));
    }

    /** O jogador respondeu (palavras > 0 = recitou tudo a tempo). */
    public static void resposta(ServerPlayer p, int palavras) {
        CompoundTag d = dados(p);
        long prova = d.getLong("prova");
        if (prova <= 0) return;
        Tropa t = null;
        for (Tropa x : Tropa.values()) if (x.nome.equals(d.getString("provaTropa"))) t = x;
        d.putLong("prova", 0);
        d.putFloat("medidor", 0);
        long agora = agora(p);
        long inicio = prova - PRAZO;
        // não dá para recitar em menos de 0,12 s por palavra
        boolean ok = palavras > 0 && agora - inicio >= palavras * 2L && agora <= prova + 60;
        if (t == null) return;
        Component nome = Component.translatable("emocao.lanterna.emocao." + t.nome);
        if (ok) {
            CompoundTag ma = d.getCompound("maestria");
            int n = ma.getInt(t.nome) + 1;
            ma.putInt(t.nome, n);
            d.put("maestria", ma);
            d.putLong("pausa", agora + 20 * 60 * 3);
            p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.3f);
            ((ServerLevel) p.level()).sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
            if (n == DOMINIO) p.sendSystemMessage(Component.translatable("emocao.lanterna.dominio", nome).withStyle(s -> s.withColor(t2(d, p))));
            else p.sendSystemMessage(Component.translatable("emocao.lanterna.resistiu", nome, Math.min(n, DOMINIO), DOMINIO).withStyle(ChatFormatting.AQUA));
        } else {
            d.putLong("frenesi", agora + DUR_FRENESI);
            d.putLong("pausa", agora + DUR_FRENESI + 20 * 60 * 5);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, DUR_FRENESI, 1, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DUR_FRENESI, 0, false, true));
            p.level().playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.2f, 0.8f);
            p.sendSystemMessage(Component.translatable("emocao.lanterna.frenesi", nome).withStyle(s -> s.withColor(t2(d, p))));
            LanternaMod.LOGGER.info("Lanterna: {} entrou em frenesi ({})", p.getGameProfile().getName(), t.nome);
        }
    }

    private static int t2(CompoundTag d, ServerPlayer p) {
        Tropa t = tropa(p);
        return t != null ? t.cor : 0xFFFFFF;
    }

    // ===================== imersão na Bateria Central =====================

    static void imersao(ServerPlayer p, @Nullable Tropa t, CompoundTag d) {
        if (t == null || !LanternaConfig.IMERSAO.get()) return;
        BateriaNucleoBlockEntity b = bateriaPerto(p, t);
        if (b == null) return;
        CompoundTag im = d.getCompound("imersao");
        int antes = nivelImersao(p, t);
        im.putInt(t.nome, im.getInt(t.nome) + 5);
        d.put("imersao", im);
        // a luz da Bateria vem até ele
        var c = b.getBlockPos();
        ServerLevel l = (ServerLevel) p.level();
        org.joml.Vector3f cor = t.corVetor();
        for (int i = 0; i < 6; i++) {
            double u = p.getRandom().nextDouble();
            l.sendParticles(new DustParticleOptions(cor, 1.2f), c.getX() + 0.5 + (p.getX() - c.getX() - 0.5) * u, c.getY() + 6 + (p.getY() + 1 - c.getY() - 6) * u,
                    c.getZ() + 0.5 + (p.getZ() - c.getZ() - 0.5) * u, 1, 0.1, 0.1, 0.1, 0);
        }
        int depois = nivelImersao(p, t);
        if (depois > antes) {
            p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 1.4f);
            p.sendSystemMessage(Component.translatable("emocao.lanterna.imersao", Component.translatable("bateria.lanterna." + t.nome), depois, NIVEIS_IMERSAO.length,
                    depois * 4).withStyle(s -> s.withColor(t.cor)));
        }
    }

    /** A Bateria Central da Tropa do anel, a até 24 blocos (procura nos pedaços em volta). */
    @Nullable
    public static BateriaNucleoBlockEntity bateriaPerto(ServerPlayer p, Tropa t) {
        var pl = com.lanterna.lore.PlanetLore.byDimension(p.level().dimension());
        if (pl == null || Tropa.doPlaneta(pl) != t) return null;
        int cx = p.blockPosition().getX() >> 4, cz = p.blockPosition().getZ() >> 4;
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if (!p.level().hasChunk(cx + x, cz + z)) continue;
            for (var be : p.level().getChunk(cx + x, cz + z).getBlockEntities().values())
                if (be instanceof BateriaNucleoBlockEntity b && b.getBlockPos().distToCenterSqr(p.getX(), b.getBlockPos().getY(), p.getZ()) < 24 * 24) return b;
        }
        return null;
    }

    // ===================== comando =====================

    public static Component status(ServerPlayer p) {
        Tropa t = tropa(p);
        if (t == null) return Component.translatable("emocao.lanterna.sem_anel");
        return Component.translatable("emocao.lanterna.status", Component.translatable("emocao.lanterna.emocao." + t.nome),
                Math.round(medidor(p)), Math.round(limite(p, t)), Math.min(maestria(p, t), DOMINIO), DOMINIO,
                nivelImersao(p, t), NIVEIS_IMERSAO.length, imersao(p, t) / 60,
                Component.translatable(instavel(p) ? "emocao.lanterna.instavel_sim" : "emocao.lanterna.instavel_nao"));
    }

    /** Teste: enche a emoção até o limite. */
    public static void provocar(ServerPlayer p) {
        Tropa t = tropa(p);
        if (t == null) return;
        CompoundTag d = dados(p);
        d.putLong("pausa", 0);
        d.putLong("prova", 0);
        d.putFloat("medidor", limite(p, t));
        d.putLong("combate", agora(p));
    }

    public static void somarImersao(ServerPlayer p, int segundos) {
        Tropa t = tropa(p);
        if (t == null) return;
        CompoundTag d = dados(p), im = d.getCompound("imersao");
        im.putInt(t.nome, Math.max(0, im.getInt(t.nome) + segundos));
        d.put("imersao", im);
    }

    private Instabilidade() {}
}
