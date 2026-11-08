package com.lanterna.mogo;

import com.lanterna.LanternaMod;
import com.lanterna.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MogoEntidades {
    public static RegistryObject<EntityType<RochaMogo>> ROCHA;
    public static RegistryObject<EntityType<OlhoMogo>> OLHO;
    public static RegistryObject<EntityType<Ferida>> FERIDA;

    public static void init() {
        ROCHA = ModEntities.ENTITIES.register("rocha_mogo", () -> EntityType.Builder.<RochaMogo>of(RochaMogo::new, MobCategory.MISC)
                .sized(1.6f, 1.6f).clientTrackingRange(12).updateInterval(1).build("rocha_mogo"));
        OLHO = ModEntities.ENTITIES.register("olho_mogo", () -> EntityType.Builder.of(OlhoMogo::new, MobCategory.MISC)
                .sized(1.4f, 2.6f).clientTrackingRange(10).fireImmune().build("olho_mogo"));
        FERIDA = ModEntities.ENTITIES.register("ferida_mogo", () -> EntityType.Builder.of(Ferida::new, MobCategory.MONSTER)
                .sized(3.2f, 4.2f).clientTrackingRange(10).fireImmune().build("ferida_mogo"));
    }

    @SubscribeEvent
    public static void atributos(EntityAttributeCreationEvent e) {
        e.put(OLHO.get(), OlhoMogo.atributos().build());
        e.put(FERIDA.get(), Ferida.atributos().build());
    }

    private MogoEntidades() {}
}
