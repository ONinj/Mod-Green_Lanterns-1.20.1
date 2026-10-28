package com.lanterna.client.flight;

import com.lanterna.client.ClientRingData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** Vento no ouvido durante o voo: mais alto e mais agudo quanto mais rápido. */
public class FlightWindSound extends AbstractTickableSoundInstance {
    private final LocalPlayer player;
    private float targetVolume;
    private float targetPitch = 0.8f;

    public FlightWindSound(LocalPlayer player) {
        super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.player = player;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.relative = true; // sempre no ouvido do jogador
    }

    public void setSpeed(double speed) {
        targetVolume = (float) Mth.clamp((speed - 0.15) * 0.75, 0, 0.9);
        targetPitch = (float) Mth.clamp(0.75 + speed * 0.35, 0.75, 1.4);
    }

    public void finish() {
        stop();
    }

    @Override
    public void tick() {
        boolean flying = ClientRingData.hasRing && ClientRingData.flight && ClientRingData.active;
        if (player.isRemoved() || !flying) {
            stop();
            return;
        }
        volume += (targetVolume - volume) * 0.15f;
        pitch += (targetPitch - pitch) * 0.15f;
    }
}
