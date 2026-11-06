package com.lanterna.registry;

import com.lanterna.LanternaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Sons próprios do mod (feitos para o mod, sem samples de terceiros). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SONS = DeferredRegister.create(Registries.SOUND_EVENT, LanternaMod.MODID);

    public static final RegistryObject<SoundEvent> NAVE_MOTOR = som("nave.motor");
    public static final RegistryObject<SoundEvent> NAVE_TURBO = som("nave.turbo");
    public static final RegistryObject<SoundEvent> NAVE_DECOLAR = som("nave.decolar");
    public static final RegistryObject<SoundEvent> NAVE_POUSAR = som("nave.pousar");
    public static final RegistryObject<SoundEvent> NAVE_REENTRADA = som("nave.reentrada");
    public static final RegistryObject<SoundEvent> NAVE_PORTA = som("nave.porta");
    public static final RegistryObject<SoundEvent> NAVE_TIRO = som("nave.tiro");
    public static final RegistryObject<SoundEvent> NAVE_CHAMADO = som("nave.chamado");

    private static RegistryObject<SoundEvent> som(String id) {
        return SONS.register(id, () -> SoundEvent.createVariableRangeEvent(LanternaMod.id(id)));
    }

    private ModSounds() {}
}
