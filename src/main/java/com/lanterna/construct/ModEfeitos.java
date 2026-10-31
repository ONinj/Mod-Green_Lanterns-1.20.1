package com.lanterna.construct;

import com.lanterna.LanternaMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Efeitos dos construtos das Tropas.
 *  Pavor (Amarelo): o medo trava a vontade — jogador não faz construtos e vê a tela tremer; criaturas fogem.
 *  Encanto (Violeta): o amor desarma — criaturas não atacam ninguém e seguem quem encantou; jogadores ficam fracos.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModEfeitos {
    public static final DeferredRegister<MobEffect> EFEITOS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, LanternaMod.MODID);

    public static final RegistryObject<MobEffect> PAVOR = EFEITOS.register("pavor", () -> new Efeito(MobEffectCategory.HARMFUL, 0xFFD21E));
    public static final RegistryObject<MobEffect> ENCANTO = EFEITOS.register("encanto", () -> new Efeito(MobEffectCategory.HARMFUL, 0xE058FF));

    public static class Efeito extends MobEffect {
        public Efeito(MobEffectCategory c, int cor) {
            super(c, cor);
        }
    }

    public static boolean comPavor(net.minecraft.world.entity.LivingEntity e) {
        return e.hasEffect(PAVOR.get());
    }

    /** Criaturas com Pavor fogem de quem está perto; com Encanto, param de brigar. */
    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) return;
        boolean pavor = mob.hasEffect(PAVOR.get()), encanto = mob.hasEffect(ENCANTO.get());
        if (!pavor && !encanto) return;
        mob.setTarget(null);
        if (pavor && mob instanceof PathfinderMob pm && mob.tickCount % 10 == 0) {
            Player perto = mob.level().getNearestPlayer(mob, 16);
            if (perto != null) {
                Vec3 longe = DefaultRandomPos.getPosAway(pm, 12, 5, perto.position());
                if (longe != null) pm.getNavigation().moveTo(longe.x, longe.y, longe.z, 1.4);
            }
        }
    }

    public static void init(net.minecraftforge.eventbus.api.IEventBus modBus) {
        EFEITOS.register(modBus);
    }

    private ModEfeitos() {}
}
