package com.lanterna.lore.entity.boss;

import com.lanterna.construct.fonte.ConstrutosFonte;
import com.lanterna.fonte.FonteDados;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;

/**
 * Os Corações das Emoções: os chefes das Fontes, um por Tropa. Cada um luta com os próprios construtos da sua
 * emoção (o mesmo motor da Árvore da Luz) e muda de fase na metade da vida, invocando aliados e acelerando.
 *
 *   Colosso de Jade (Vontade) · Mãe das Sombras (Medo) · Serpente Escarlate (Fúria) · Rei Faminto (Avareza)
 *   Arcanjo da Aurora (Esperança) · Cervo Sereno (Compaixão) · Rainha de Cristal (Amor)
 *
 * Derrotar o Coração pela primeira vez dá 60 de Essência e o Coração da Emoção: +10% de dano e +20% de carga
 * máxima para sempre com o anel daquela Tropa.
 */
public class ChefeFonte extends LoreBoss {
    /** tropa (nome da Bateria), cor, golpes da fase 1, golpes da fase 2, invocação, voa, velocidade. */
    public record Cfg(String tropa, int cor, String[] golpes, String[] golpes2, String invoca, boolean voa, double vel, BossEvent.BossBarColor barra) {}

    public static final Map<String, Cfg> CFG = Map.of(
            "colosso_de_jade", new Cfg("verde", 0x4CFF8A, new String[]{"g_canhao", "g_grilhoes", "g_lanca"}, new String[]{"g_meteoros", "g_metralhadora", "g_canhao"}, "g_sentinela", false, 0.9, BossEvent.BossBarColor.GREEN),
            "mae_das_sombras", new Cfg("amarelo", 0xFFE040, new String[]{"y_morcegos", "y_espinhos", "y_garras"}, new String[]{"y_prisao", "y_forca", "y_morcegos"}, "y_aranha", true, 1.0, BossEvent.BossBarColor.YELLOW),
            "serpente_escarlate", new Cfg("vermelho", 0xFF3A20, new String[]{"r_vomito", "r_punho", "r_erupcao"}, new String[]{"r_chuva", "r_corrente", "r_vomito"}, "r_cao", false, 0.8, BossEvent.BossBarColor.RED),
            "rei_faminto", new Cfg("laranja", 0xFF9A30, new String[]{"o_moedas", "o_garra", "o_tentaculos"}, new String[]{"o_vortice", "o_mina", "o_moedas"}, "o_sanguessuga", false, 0.7, BossEvent.BossBarColor.YELLOW),
            "arcanjo_da_aurora", new Cfg("azul", 0x5ABAFF, new String[]{"b_raio", "b_estrela", "b_purificar"}, new String[]{"b_aurora", "b_raio", "b_estrela"}, "b_anjo", true, 1.0, BossEvent.BossBarColor.BLUE),
            "cervo_sereno", new Cfg("indigo", 0x8A70FF, new String[]{"i_raizes", "i_olhar", "i_bastao"}, new String[]{"i_lagrimas", "i_silencio", "i_olhar"}, "i_espirito", false, 1.1, BossEvent.BossBarColor.PURPLE),
            "rainha_de_cristal", new Cfg("violeta", 0xFF6AD0, new String[]{"v_lanca", "v_jardim", "v_flecha"}, new String[]{"v_coracoes", "v_paixao", "v_lanca"}, "v_cavaleiro", true, 0.9, BossEvent.BossBarColor.PINK));

    // ---- força dos Corações: golpes muito pesados, mas lentos e anunciados ----
    /** Dano de cada construto (antes da armadura): fase 1 / fase 2. */
    public static final float DANO_F1 = 22f, DANO_F2 = 28f;
    /** Tempo entre construtos (ticks): fase 1 / fase 2. */
    public static final int RECARGA_F1 = 170, RECARGA_F2 = 130;
    /** Aviso antes do construto (o chefe para e concentra a luz). */
    public static final int AVISO = 30;
    /** Golpe máximo da fase 2: a cada 30 s, com 3 s de aviso. */
    public static final int SUPREMO = 600, AVISO_SUPREMO = 60;
    /** Nenhum jogador perde mais que esta fração da vida máxima para o chefe em 1 segundo (nunca mata de um golpe). */
    public static final float TETO = 0.6f;

    private int golpe;
    private int carregando;
    private String preparado;
    private int supremo = SUPREMO;
    private int carregandoSupremo;

    public ChefeFonte(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.WHITE);
        bar.setColor(cfg().barra());
    }

    public Cfg cfg() {
        Cfg c = CFG.get(bossId());
        return c != null ? c : CFG.get("colosso_de_jade");
    }

    @Override
    protected String[] extraAnims() {
        return new String[]{"special"};
    }

    @Override
    protected boolean usesPhase2Loop() {
        return true;
    }

    @Override
    protected int barRgb() {
        return cfg().cor();
    }

    private void anunciar(LivingEntity alvo, int cor, float intensidade) {
        ServerLevel sl = (ServerLevel) level();
        var pó = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 2.2f);
        sl.sendParticles(pó, getX(), getY() + getBbHeight() * 0.6, getZ(), (int) (12 * intensidade), getBbWidth() * 0.6, getBbHeight() * 0.4, getBbWidth() * 0.6, 0.05);
        // marca no chão embaixo do alvo: dá tempo de sair de perto
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8 + tickCount * 0.1, r = 2.2 * intensidade;
            sl.sendParticles(pó, alvo.getX() + Math.cos(a) * r, alvo.getY() + 0.15, alvo.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    private float danoPorAcerto(String id, float total) {
        var d = com.lanterna.construct.fonte.DefsFonte.get(id);
        if (d == null) return total;
        int acertos = switch (d.comp()) {
            case SALVA -> Math.min(8, d.qtd());
            case CHUVA -> d.qtd();
            default -> 1;
        };
        // vários tiros: cada um fere menos, mas se todos acertarem doem o dobro de um golpe só
        return acertos <= 1 ? total : total * 2f / acertos;
    }

    @Override
    protected void bossTick(LivingEntity target) {
        Cfg c = cfg();
        boolean f2 = phase() >= 2;
        // ---- golpe supremo (fase 2) ----
        if (carregandoSupremo > 0) {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0.2, c.voa() ? 0.2 : 1, 0.2));
            anunciar(target, c.cor(), 2.2f);
            if (--carregandoSupremo == 0) {
                triggerAnim("attack", "special");
                for (String id : c.golpes2()) ConstrutosFonte.usar(this, id, target, danoPorAcerto(id, DANO_F2 * 1.3f), c.cor());
                ((ServerLevel) level()).playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.5f, 0.6f);
                specialCooldown = RECARGA_F2;
            }
            return;
        }
        if (f2 && --supremo <= 0) {
            supremo = SUPREMO;
            carregandoSupremo = AVISO_SUPREMO;
            ((ServerLevel) level()).playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 3f, 0.7f);
            for (Player p : playersNear(64))
                p.displayClientMessage(Component.translatable("chefe.lanterna.supremo", getDisplayName()).withStyle(st -> st.withColor(c.cor()).withBold(true)), true);
            return;
        }
        // ---- construto anunciado ----
        if (carregando > 0) {
            getNavigation().stop();
            anunciar(target, c.cor(), 1f);
            if (--carregando == 0 && preparado != null) {
                triggerAnim("attack", "special");
                ConstrutosFonte.usar(this, preparado, target, danoPorAcerto(preparado, f2 ? DANO_F2 : DANO_F1), c.cor());
                preparado = null;
                specialCooldown = f2 ? RECARGA_F2 : RECARGA_F1;
            }
            return;
        }
        if (c.voa()) {
            // flutua acima do chão, perseguindo o alvo
            double alt = target.getY() + 3.5 + Math.sin(tickCount * 0.05) * 1.2;
            var v = target.position().subtract(position());
            var h = new net.minecraft.world.phys.Vec3(v.x, 0, v.z);
            double dist = h.length();
            var mov = dist > 7 ? h.normalize().scale(0.22 * c.vel() * (f2 ? 1.3 : 1)) : net.minecraft.world.phys.Vec3.ZERO;
            setDeltaMovement(mov.x, (alt - getY()) * 0.08, mov.z);
            setNoGravity(true);
        } else {
            chase(target, c.vel() * (f2 ? 1.25 : 1.0));
        }
        // corpo a corpo: muito pesado, mas lento
        tryMelee(target, 1.5, f2 ? 45 : 60);
        if (specialCooldown == 0) {
            String[] lista = f2 ? c.golpes2() : c.golpes();
            preparado = lista[golpe++ % lista.length];
            carregando = AVISO;
            ((ServerLevel) level()).playSound(null, blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2f, 0.6f);
        }
        if (f2 && tickCount % 400 == 0) ConstrutosFonte.usar(this, c.invoca(), target, 12f, c.cor());
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Supremo", supremo);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Supremo")) supremo = tag.getInt("Supremo");
    }

    /** Teto de dano: o Coração bate muito forte, mas nunca leva mais de 60% da vida máxima de alguém em 1 segundo. */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.lanterna.LanternaMod.MODID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Teto {
        private static final Map<UUID, float[]> JANELA = new java.util.HashMap<>();

        @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public static void onDamage(net.minecraftforge.event.entity.living.LivingDamageEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getSource().getEntity() instanceof ChefeFonte)) return;
            long agora = p.level().getGameTime();
            float[] j = JANELA.computeIfAbsent(p.getUUID(), k -> new float[]{agora, 0, -1000});
            if (agora - (long) j[0] >= 20) { j[0] = agora; j[1] = 0; }
            float resta = Math.max(0, p.getMaxHealth() * TETO - j[1]);
            float dano = Math.min(e.getAmount(), resta);
            // golpes pesados e espaçados: o que chega logo depois de um golpe (menos de 1,5 s) vale só um quarto
            if (agora - (long) j[2] < 30 && agora != (long) j[2]) dano *= 0.25f;
            else if (agora != (long) j[2]) j[2] = agora;
            // o golpe nunca mata quem estava com mais da metade da vida
            if (p.getHealth() > p.getMaxHealth() * 0.5f) dano = Math.min(dano, p.getHealth() - 1f);
            j[1] += dano;
            if (dano <= 0) e.setCanceled(true);
            else e.setAmount(dano);
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onSair(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
            JANELA.remove(e.getEntity().getUUID());
        }
    }

    @Override
    protected void onPhaseChange(int phase) {
        super.onPhaseChange(phase);
        Cfg c = cfg();
        LivingEntity t = getTarget();
        if (t != null) for (int i = 0; i < 3; i++) ConstrutosFonte.usar(this, c.invoca(), t, 12f, c.cor());
        heal(getMaxHealth() * 0.08f);
        addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 1));
    }

    @Override
    protected void onDefeated() {
        Cfg c = cfg();
        ServerLevel sl = (ServerLevel) level();
        com.lanterna.fonte.FonteMundo.DERROTA.put(c.tropa(), sl.getGameTime());
        sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2, getZ(), 200, 2, 2, 2, 0.3);
        for (UUID id : fighters) {
            if (!(sl.getPlayerByUUID(id) instanceof ServerPlayer sp) || sp.distanceTo(this) > 96) continue;
            String chave = "coracao:" + c.tropa();
            boolean primeira = !FonteDados.tag(sp).getBoolean(chave);
            FonteDados.tag(sp).putBoolean(chave, true);
            FonteDados.essencia(sp, FonteDados.essencia(sp) + (primeira ? 60 : 15));
            sp.sendSystemMessage(Component.translatable(primeira ? "chefe.lanterna.coracao" : "chefe.lanterna.de_novo",
                    Component.translatable("bateria.lanterna." + c.tropa())).withStyle(s -> s.withColor(c.cor()).withBold(true)));
            com.lanterna.construct.Affinity.add(sp, primeira ? 300 : 60);
        }
        sl.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 1f, 1.4f);
    }

    /** O Coração da Emoção (chefe da Fonte derrotado) dá +10% de dano e +20% de carga máxima com o anel daquela Tropa. */
    public static boolean temCoracao(Player p) {
        var t = com.lanterna.construct.fonte.ArvoreDaLuz.corps(p);
        if (t == null) return false;
        for (var b : com.lanterna.baterias.Tropa.values())
            if (b.corps().id().getPath().equals(t)) return FonteDados.tag(p).getBoolean("coracao:" + b.nome);
        return false;
    }
}
