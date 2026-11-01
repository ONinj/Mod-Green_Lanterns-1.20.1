package seu.pacote.baterias;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Campo de energia da câmara (funciona como o portal do Nether: fique dentro por 2 segundos).
 * retorno=false -> leva para a dimensão fonte_<tropa>; retorno=true (na plataforma de chegada) -> volta.
 */
public class BateriaPortalBlock extends Block {
    public static final EnumProperty<Tropa> TROPA = BateriaNucleoBlock.TROPA;
    public static final BooleanProperty RETORNO = BooleanProperty.create("retorno");
    private static final int TEMPO = 40;          // ticks parado dentro do campo
    private static final int ESPERA = 100;        // ticks de espera depois de atravessar

    public BateriaPortalBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(TROPA, Tropa.VERDE).setValue(RETORNO, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(TROPA, RETORNO); }
    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }   // o brilho vem do modelo
    @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Shapes.empty(); }

    public static ResourceKey<Level> dimensao(Tropa t) {
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(ModBaterias.MODID, "fonte_" + t.nome));
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity e) {
        if (level.isClientSide || !(e instanceof ServerPlayer p) || p.isPassenger()) return;
        CompoundTag d = p.getPersistentData();
        long agora = level.getGameTime();
        if (d.getLong("bateria_espera") > agora) return;
        // conta o tempo contínuo dentro do campo
        if (agora - d.getLong("bateria_ultimo") > 2) d.putLong("bateria_inicio", agora);
        d.putLong("bateria_ultimo", agora);
        long dentro = agora - d.getLong("bateria_inicio");
        Tropa t = state.getValue(TROPA);
        if (dentro == 1) level.playSound(null, pos, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.6f, 1.4f);
        if (dentro < TEMPO) { p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0, false, false)); return; }
        d.putLong("bateria_espera", agora + ESPERA);
        ServerLevel atual = (ServerLevel) level;
        if (state.getValue(RETORNO)) {
            BateriaTeleporte.voltar(p);
            return;
        }
        if (!AcessoBateria.podeEntrar(p, t)) {
            p.displayClientMessage(Component.literal("A Bateria Central não reconhece você."), true);
            Vec3 fora = p.position().subtract(Vec3.atCenterOf(pos)).normalize().scale(1.2);
            p.setDeltaMovement(fora.x, 0.4, fora.z); p.hurtMarked = true;
            return;
        }
        ServerLevel destino = atual.getServer().getLevel(dimensao(t));
        if (destino == null) { p.displayClientMessage(Component.literal("Dimensão fonte_" + t.nome + " não encontrada."), true); return; }
        // guarda de onde veio para a volta
        d.putString("bateria_volta_dim", atual.dimension().location().toString());
        d.putDouble("bateria_volta_x", p.getX()); d.putDouble("bateria_volta_y", p.getY()); d.putDouble("bateria_volta_z", p.getZ());
        d.putFloat("bateria_volta_yaw", p.getYRot());
        p.changeDimension(destino, new BateriaTeleporte(t));
    }

    @Override
    public void animateTick(BlockState s, Level level, BlockPos pos, RandomSource r) {
        DustParticleOptions pt = new DustParticleOptions(s.getValue(TROPA).corVetor(), 1.2f);
        for (int i = 0; i < 2; i++)
            level.addParticle(pt, pos.getX() + r.nextDouble(), pos.getY() + r.nextDouble(), pos.getZ() + r.nextDouble(), 0, 0.05, 0);
    }
}
