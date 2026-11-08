package com.lanterna.baterias;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class BateriaNucleoBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("misc.idle");
    private static final RawAnimation ATIVO = RawAnimation.begin().thenLoop("misc.ativo");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public BateriaNucleoBlockEntity(BlockPos pos, BlockState state) { super(ModBaterias.NUCLEO_BE.get(), pos, state); }

    public Tropa getTropa() { return getBlockState().getValue(BateriaNucleoBlock.TROPA); }

    /** "ativo" quando alguém está dentro da câmara (até 4 blocos do centro) */
    private boolean alguemNaCamara() {
        if (level == null) return false;
        return level.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 2, worldPosition.getZ() + 0.5, 4.0, false) != null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "principal", 10, st -> st.setAndContinue(alguemNaCamara() ? ATIVO : IDLE)));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    /** o modelo é enorme: sem isso ele some quando o bloco do núcleo sai da tela */
    @Override public AABB getRenderBoundingBox() { return new AABB(worldPosition).inflate(16, 0, 16).expandTowards(0, 40, 0); }
}
