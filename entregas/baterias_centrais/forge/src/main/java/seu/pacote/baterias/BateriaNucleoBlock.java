package seu.pacote.baterias;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/** Bloco que desenha a Bateria gigante. Fica embaixo do piso, no centro da câmara. */
public class BateriaNucleoBlock extends BaseEntityBlock {
    public static final EnumProperty<Tropa> TROPA = EnumProperty.create("tropa", Tropa.class);

    public BateriaNucleoBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(TROPA, Tropa.VERDE));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(TROPA); }
    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState s) { return new BateriaNucleoBlockEntity(pos, s); }
}
