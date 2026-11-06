package com.lanterna.naves.client;

import com.lanterna.LanternaMod;
import com.lanterna.naves.NaveEntity;
import com.lanterna.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Ronco do motor de cada nave voando por perto: fica mais alto e mais agudo com a velocidade e no turbo,
 * e some quando a nave pousa ou se afasta.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class SomMotorNave extends AbstractTickableSoundInstance {
    private static final Map<Integer, SomMotorNave> TOCANDO = new HashMap<>();
    private static final double ALCANCE = 64;

    private final NaveEntity nave;
    private float vol;

    private SomMotorNave(NaveEntity nave) {
        super(ModSounds.NAVE_MOTOR.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.nave = nave;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = nave.getX(); this.y = nave.getY(); this.z = nave.getZ();
    }

    private boolean deveTocar() {
        return nave.isAlive() && !nave.tipo().destrocos() && (!nave.pousada() || nave.isVehicle());
    }

    @Override
    public void tick() {
        if (!nave.isAlive() || Minecraft.getInstance().player == null || nave.distanceTo(Minecraft.getInstance().player) > ALCANCE + 8) {
            stop();
            return;
        }
        x = nave.getX(); y = nave.getY(); z = nave.getZ();
        double vel = Math.sqrt(Math.pow(nave.getX() - nave.xo, 2) + Math.pow(nave.getY() - nave.yo, 2) + Math.pow(nave.getZ() - nave.zo, 2));
        float alvo = deveTocar() ? (float) Mth.clamp(0.35 + vel * 0.9 + (nave.turbo() ? 0.4 : 0), 0.3, 1.6) : 0f;
        vol += (alvo - vol) * 0.12f;
        volume = Math.max(0.001f, vol * (nave.tipo().width > 6 ? 1.5f : 1f));
        pitch = (float) Mth.clamp(0.75 + vel * 0.35 + (nave.turbo() ? 0.35 : 0), 0.6, 2.0) * (nave.tipo().width > 6 ? 0.7f : 1f);
        if (alvo == 0 && vol < 0.02f) stop();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.END || mc.level == null || mc.player == null || mc.isPaused()) return;
        TOCANDO.values().removeIf(SomMotorNave::isStopped);
        if (mc.level.getGameTime() % 5 != 0) return;
        for (NaveEntity n : mc.level.getEntitiesOfClass(NaveEntity.class, mc.player.getBoundingBox().inflate(ALCANCE))) {
            if (TOCANDO.containsKey(n.getId()) || n.tipo().destrocos() || (n.pousada() && !n.isVehicle())) continue;
            SomMotorNave s = new SomMotorNave(n);
            TOCANDO.put(n.getId(), s);
            mc.getSoundManager().play(s);
        }
    }
}
