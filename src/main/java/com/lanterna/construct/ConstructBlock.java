package com.lanterna.construct;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Bloco de energia dos construtos (parede, ponte, jaula). É sólido, brilha na cor da Tropa
 * e some sozinho quando o tempo do construto acaba.
 */
public class ConstructBlock extends BaseEntityBlock {

    public ConstructBlock(Properties props) {
        super(props);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState other, net.minecraft.core.Direction dir) {
        return other.is(this) || super.skipRendering(state, other, dir);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ConstructRegistry.CONSTRUCT_BLOCK_ENTITY.get(), (l, p, s, be) -> be.serverTick());
    }

    /** Coloca um bloco de construto (só no ar, água ou plantas). */
    public static boolean place(ServerLevel level, BlockPos pos, int color, int lifeTicks) {
        BlockState here = level.getBlockState(pos);
        if (!here.isAir() && !here.canBeReplaced() && !here.is(ConstructRegistry.CONSTRUCT_BLOCK.get())) return false;
        if (level.isOutsideBuildHeight(pos)) return false;
        level.setBlock(pos, ConstructRegistry.CONSTRUCT_BLOCK.get().defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof Entity be) {
            be.color = color;
            be.expireAt = level.getGameTime() + lifeTicks;
            be.setChanged();
            level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
        }
        return true;
    }

    public static class Entity extends BlockEntity {
        int color = 0x2BFF4A;
        long expireAt;

        public Entity(BlockPos pos, BlockState state) {
            super(ConstructRegistry.CONSTRUCT_BLOCK_ENTITY.get(), pos, state);
        }

        public int color() {
            return color;
        }

        void serverTick() {
            if (level == null) return;
            if (expireAt > 0 && level.getGameTime() >= expireAt) {
                if (level instanceof ServerLevel sl) {
                    float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;
                    sl.sendParticles(new DustParticleOptions(new Vector3f(r, g, b), 1.0f), worldPosition.getX() + 0.5,
                            worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0);
                }
                level.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), 3);
            }
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            tag.putInt("Color", color);
            tag.putLong("Expire", expireAt);
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            color = tag.getInt("Color");
            expireAt = tag.getLong("Expire");
            if (level != null && level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
            }
        }

        @Override
        public CompoundTag getUpdateTag() {
            CompoundTag tag = super.getUpdateTag();
            saveAdditional(tag);
            return tag;
        }

        @Nullable
        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }

        @Override
        public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt) {
            super.onDataPacket(net, pkt);
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
        }
    }
}
