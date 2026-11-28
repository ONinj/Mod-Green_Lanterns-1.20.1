package com.lanterna.fonte;

import com.lanterna.LanternaMod;
import com.lanterna.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 0.60.4: a prisão da entidade, dentro da Fonte (ao norte, depois do Salão do Conselho), como a cela da Kurama: uma
 * sala escura com chão de água, grades gigantes e um selo de papel no portão. A entidade da Tropa fica presa atrás das
 * grades (desenhada no cliente: EntidadesBateria) e vem até elas quando alguém se aproxima. Clique direito nas grades:
 * ela fala, presa, e ameaça a Tropa de quem a prendeu.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Prisao {
    /** A linha das grades (z) e o meio da cela. */
    public static final int GRADES_Z = -62, LARGURA = 30;
    public static final BlockPos CELA = new BlockPos(0, 100, -92);

    public static boolean nasGrades(BlockPos p) {
        return Math.abs(p.getZ() - GRADES_Z) <= 1 && Math.abs(p.getX()) <= LARGURA && p.getY() >= 100 && p.getY() <= 142;
    }

    @SubscribeEvent
    public static void clique(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !nasGrades(e.getPos())) return;
        String tropa = Fonte.fonteDe(p.level());
        if (tropa == null) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        var anel = com.lanterna.lore.Factions.playerCorps(p);
        String alvo = anel != null ? anel.id().getPath() : com.lanterna.fonte.Desafios.tropa(tropa).corps().id().getPath();
        p.level().playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.6f, 0.45f);
        p.level().playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.4f, 0.6f);
        ModNetwork.sendTo(p, new Fala(tropa, alvo));
    }

    /** Servidor → cliente: abre o diálogo da entidade presa (tropa da Fonte, Tropa do anel do jogador). */
    public record Fala(String tropa, String alvo) {
        public static void encode(Fala m, FriendlyByteBuf b) { b.writeUtf(m.tropa); b.writeUtf(m.alvo); }
        public static Fala decode(FriendlyByteBuf b) { return new Fala(b.readUtf(), b.readUtf()); }
        public static void handle(Fala m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.lanterna.client.fonte.PrisaoScreen.abrir(m.tropa, m.alvo));
            ctx.get().setPacketHandled(true);
        }
    }

    private Prisao() {}
}
