package com.lanterna.mogo;

import com.lanterna.network.ModNetwork;
import com.lanterna.space.SpaceBodies;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Rastro do Mogo (construto do anel verde): o anel acha o planeta-Lanterna, que anda pelo espaço. No espaço, marca o
 * Mogo na tela por 30 s (com a distância, seguindo ele enquanto anda) e solta um rastro de luz na direção dele. Fora
 * do espaço, o anel diz em que ponto do espaço ele está agora.
 */
public final class MogoRastro {
    public static final int DURACAO = 20 * 30;

    public static void usar(ServerPlayer p, int cor) {
        SpaceBodies.Body mogo = SpaceBodies.byId("mogo");
        if (mogo == null) return;
        Vec3 c = mogo.center();
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.6f);
        if (p.level().dimension().equals(MogoMundo.DIM)) {
            p.displayClientMessage(Component.translatable("mogo.lanterna.rastro.aqui").withStyle(ChatFormatting.GREEN), true);
            return;
        }
        if (!p.level().dimension().equals(SpaceBodies.SPACE)) {
            p.sendSystemMessage(Component.translatable("mogo.lanterna.rastro.longe", (int) c.x, (int) c.y, (int) c.z).withStyle(ChatFormatting.GREEN));
            return;
        }
        ModNetwork.sendTo(p, new Pacote(DURACAO));
        // rastro de luz saindo do anel na direção do Mogo
        Vec3 dir = c.subtract(p.getEyePosition()).normalize();
        var l = p.serverLevel();
        for (int i = 2; i < 40; i++) {
            Vec3 q = p.getEyePosition().add(dir.scale(i * 0.9));
            l.sendParticles(p, new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.24f, 1f, 0.48f), 1.6f),
                    true, q.x, q.y, q.z, 2, 0.05, 0.05, 0.05, 0);
        }
        p.displayClientMessage(Component.translatable("mogo.lanterna.rastro.ok", (int) p.position().distanceTo(c)).withStyle(ChatFormatting.GREEN), true);
    }

    public record Pacote(int duracao) {
        public static void encode(Pacote m, FriendlyByteBuf b) { b.writeVarInt(m.duracao); }
        public static Pacote decode(FriendlyByteBuf b) { return new Pacote(b.readVarInt()); }
        public static void handle(Pacote m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.lanterna.client.mogo.ClienteRastroMogo.ligar(m.duracao)));
            ctx.get().setPacketHandled(true);
        }
    }

    private MogoRastro() {}
}
