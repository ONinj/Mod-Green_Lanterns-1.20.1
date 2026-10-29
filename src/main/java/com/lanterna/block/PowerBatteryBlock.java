package com.lanterna.block;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.CorpsDefinition;
import com.lanterna.corps.CorpsManager;
import com.lanterna.power.RingPowers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class PowerBatteryBlock extends BaseEntityBlock {
    /** Caixa de 1 bloco (o modelo de algumas baterias passa um pouco da altura, é só visual). */
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<BatteryShape> CORPS =
            net.minecraft.world.level.block.state.properties.EnumProperty.create("corps", BatteryShape.class);

    public PowerBatteryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CORPS, BatteryShape.GREEN));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CORPS);
    }

    /** O formato vem da Tropa gravada no item. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(CORPS,
                BatteryShape.of(com.lanterna.item.PowerBatteryItem.getCorps(context.getItemInHand())));
    }

    /** Pegar a bateria com o botão do meio devolve a da Tropa certa. */
    @Override
    public net.minecraft.world.item.ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof PowerBatteryBlockEntity battery) {
            return com.lanterna.item.PowerBatteryItem.createBlock(battery.getCorps(), battery.getColor());
        }
        return super.getCloneItemStack(level, pos, state);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PowerBatteryBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // BaseEntityBlock deixa invisível por padrão
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Clique direito com o anel no dedo = recarregar. */
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof PowerBatteryBlockEntity battery)) return InteractionResult.PASS;

        RingData data = ModCapabilities.get(player).orElse(null);
        if (data == null || !data.hasRing()) {
            player.displayClientMessage(Component.translatable("message.lanterna.battery_no_ring"), true);
            return InteractionResult.CONSUME;
        }

        CorpsDefinition def = CorpsManager.get(battery.getCorps());
        if (!RingPowers.recharge(serverPlayer, data, def)) {
            player.displayClientMessage(Component.translatable("message.lanterna.battery_wrong_corps"), true);
            level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.7f, 0.6f);
            return InteractionResult.CONSUME;
        }

        RingPowers.playRechargeEffects(serverPlayer, def);

        if (level instanceof ServerLevel server) {
            server.sendParticles(new DustParticleOptions(rgb(def.color()), 1.5f),
                    pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 40, 0.4, 0.5, 0.4, 0.02);
        }
        return InteractionResult.CONSUME;
    }

    /** Partículas suaves na cor da Tropa, só no cliente. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) return;
        if (level.getBlockEntity(pos) instanceof PowerBatteryBlockEntity battery) {
            level.addParticle(new DustParticleOptions(rgb(battery.getColor()), 0.8f),
                    pos.getX() + 0.3 + random.nextDouble() * 0.4,
                    pos.getY() + 0.3 + random.nextDouble() * 0.6,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4,
                    0.0, 0.02, 0.0);
        }
    }

    public static Vector3f rgb(int color) {
        return new Vector3f(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f);
    }
}
