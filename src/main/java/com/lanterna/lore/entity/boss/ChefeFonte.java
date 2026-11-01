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

    private int golpe;

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

    @Override
    protected void bossTick(LivingEntity target) {
        Cfg c = cfg();
        boolean f2 = phase() >= 2;
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
        tryMelee(target, 1.5, f2 ? 22 : 30);
        if (specialCooldown == 0) {
            String[] lista = f2 ? c.golpes2() : c.golpes();
            String id = lista[golpe++ % lista.length];
            triggerAnim("attack", "special");
            float dano = (float) getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 0.8f;
            ConstrutosFonte.usar(this, id, target, dano, c.cor());
            specialCooldown = f2 ? 55 : 80;
        }
        if (f2 && tickCount % 400 == 0) ConstrutosFonte.usar(this, c.invoca(), target, 8f, c.cor());
    }

    @Override
    protected void onPhaseChange(int phase) {
        super.onPhaseChange(phase);
        Cfg c = cfg();
        LivingEntity t = getTarget();
        if (t != null) for (int i = 0; i < 2; i++) ConstrutosFonte.usar(this, c.invoca(), t, 8f, c.cor());
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
