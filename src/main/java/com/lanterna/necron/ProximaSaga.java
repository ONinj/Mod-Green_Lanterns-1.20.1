package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.lore.LoreWorldData;
import com.lanterna.lore.PlanetLore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * 0.57.1: o caminho deixado pronto para a próxima saga (Parallax).
 *
 * Cada jogador guarda como terminou a saga do Necron ({@link #caminho}): "heroi" (fez o Ritual da Terra) ou
 * "corrompido" (aceitou a Morte e o Necron venceu). O mundo guarda que o Parallax despertou (nos dois finais) e, no
 * final corrompido, que os últimos Lanternas juraram purificar o Arauto.
 *
 * A ideia do dono para a próxima saga: quem seguiu o Necron é purificado pelos Lanternas que restaram (sem poder
 * escolher), mas a alma purificada não volta vazia: ele acaba se tornando o próprio Parallax. Quem foi herói enfrenta
 * o Parallax que acordou em Korugar.
 *
 * Enquanto a próxima saga não chega, ficam só os presságios: para o corrompido, de vez em quando um brilho amarelo no
 * fundo do anel negro e um sussurro; para o herói, a Bateria de Korugar que treme quando ele chega lá.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProximaSaga {
    public static final String HEROI = "heroi", CORROMPIDO = "corrompido";
    /** Marcas do mundo. */
    public static final String PARALLAX = "saga:proxima:parallax", PURIFICADORES = "saga:proxima:purificadores";

    /** Como este jogador terminou a saga do Necron ("heroi", "corrompido" ou "" se ainda não terminou). */
    public static String caminho(Player p) { return SagaNecron.tag(p).getString("proxima_saga"); }

    /** O destino guardado para a próxima saga: quem seguiu o Necron vai virar o Parallax depois de purificado. */
    public static boolean destinoParallax(Player p) { return CORROMPIDO.equals(caminho(p)); }

    public static boolean parallaxDesperto(MinecraftServer s) { return LoreWorldData.get(s).siteBuilt(PARALLAX); }

    public static boolean purificadoresJuraram(MinecraftServer s) { return LoreWorldData.get(s).siteBuilt(PURIFICADORES); }

    /** Fim da linha do herói (o Ritual da Terra). */
    public static void heroi(ServerPlayer p) {
        if (!CORROMPIDO.equals(caminho(p))) SagaNecron.tag(p).putString("proxima_saga", HEROI);
        LoreWorldData.get(p.server).markSite(PARALLAX);
    }

    /** Fim da linha corrompida (a Noite Eterna). */
    public static void corrompido(ServerPlayer p) {
        SagaNecron.tag(p).putString("proxima_saga", CORROMPIDO);
        LoreWorldData.get(p.server).markSite(PARALLAX);
        LoreWorldData.get(p.server).markSite(PURIFICADORES);
    }

    // ===================== presságios =====================

    private static final String[] SUSSURROS = {"necron.lanterna.pressagio_1", "necron.lanterna.pressagio_2", "necron.lanterna.pressagio_3"};

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 200 != 77) return;
        if (!destinoParallax(p)) return;
        // de vez em quando (uma vez a cada 15 minutos, em média), o anel negro brilha amarelo por um instante
        if (p.getRandom().nextInt(45) != 0) return;
        var l = p.serverLevel();
        l.sendParticles(p, new DustParticleOptions(new Vector3f(1f, 0.85f, 0.15f), 1.2f), true, p.getX(), p.getY() + 1.1, p.getZ(), 14, 0.25, 0.3, 0.25, 0);
        p.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.4f, 0.5f);
        p.sendSystemMessage(Component.translatable(SUSSURROS[p.getRandom().nextInt(SUSSURROS.length)]).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
    }

    @SubscribeEvent
    public static void mudouDeMundo(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !HEROI.equals(caminho(p))) return;
        PlanetLore pl = PlanetLore.byDimension(e.getTo());
        if (pl == null || !pl.planet.equals("korugar") || p.getRandom().nextInt(3) != 0) return;
        p.sendSystemMessage(Component.translatable("necron.lanterna.pressagio_korugar").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
        p.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.5f, 1.6f);
    }

    /** Teste: esquece o caminho. */
    public static void zerar(ServerPlayer p) {
        SagaNecron.tag(p).remove("proxima_saga");
    }

    private ProximaSaga() {}
}
