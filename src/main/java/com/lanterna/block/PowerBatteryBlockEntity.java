package com.lanterna.block;

import com.lanterna.corps.BuiltinCorps;
import com.lanterna.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Guarda a qual Tropa esta bateria pertence. Sincroniza com o cliente
 * para o bloco ser pintado na cor certa.
 */
public class PowerBatteryBlockEntity extends BlockEntity {
    private ResourceLocation corps = BuiltinCorps.GREEN.id();
    private int color = BuiltinCorps.GREEN.color();

    public PowerBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWER_BATTERY.get(), pos, state);
    }

    public ResourceLocation getCorps() {
        return corps;
    }

    public int getColor() {
        return color;
    }

    public void setCorps(ResourceLocation corps, int color) {
        this.corps = corps;
        this.color = color;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Corps", corps.toString());
        tag.putInt("Color", color);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Corps")) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("Corps"));
            if (id != null) corps = id;
        }
        color = tag.contains("Color") ? tag.getInt("Color") : BuiltinCorps.colorOf(corps);
        loadedFromTag = true;
        syncShape(); // /setblock e itens com dados: o formato acompanha a Tropa

        // No cliente, força o bloco a ser redesenhado com a cor nova
        if (level != null && level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
        }
    }

    private boolean loadedFromTag;

    /** Baterias antigas (de versões anteriores) ganham o formato certo da Tropa ao carregar. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (loadedFromTag) syncShape();
    }

    private void syncShape() {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(PowerBatteryBlock.CORPS)) return;
        BatteryShape shape = BatteryShape.of(corps);
        if (state.getValue(PowerBatteryBlock.CORPS) != shape) {
            level.setBlock(worldPosition, state.setValue(PowerBatteryBlock.CORPS, shape), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
