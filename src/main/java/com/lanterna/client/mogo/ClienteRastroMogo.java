package com.lanterna.client.mogo;

import com.lanterna.client.mission.Waypoints;
import com.lanterna.space.SpaceBodies;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Marca do Rastro do Mogo na tela (some depois de 30 s ou ao sair do espaço). */
public final class ClienteRastroMogo {
    private static long ate;

    public static void ligar(int duracao) {
        var mc = Minecraft.getInstance();
        if (mc.level != null) ate = mc.level.getGameTime() + duracao;
    }

    @Nullable
    public static Waypoints.Target alvo(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.level.getGameTime() > ate) return null;
        if (!mc.level.dimension().equals(SpaceBodies.SPACE)) { ate = 0; return null; }
        SpaceBodies.Body mogo = SpaceBodies.byId("mogo");
        if (mogo == null) return null;
        Vec3 c = mogo.center();
        double dist = Math.max(0, mc.player.position().distanceTo(c) - mogo.radius());
        return new Waypoints.Target(c, dist, Component.translatable("mogo.lanterna.rastro.dica"),
                Component.translatable("planet.lanterna.mogo"), 0x3CFF7A, null);
    }

    private ClienteRastroMogo() {}
}
