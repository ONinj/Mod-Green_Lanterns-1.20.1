package com.lanterna.naves;

import com.lanterna.lore.Factions;
import com.lanterna.registry.ModItems;
import com.lanterna.tropa.TropaItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Item que põe uma nave no mundo (a nave fica sua e você ganha a chave). A pintura vai no NBT ("Pintura"). */
public class NaveItem extends Item {
    public static final Map<NaveTipo, RegistryObject<Item>> ITEMS = new EnumMap<>(NaveTipo.class);

    static {
        for (NaveTipo t : new NaveTipo[]{NaveTipo.NAVE_LANCEIRA, NaveTipo.NAVE_PATRULHA, NaveTipo.NAVE_CARGUEIRO,
                NaveTipo.NAVE_PIRATA, NaveTipo.NAVE_PIRATA_CAPITANIA, NaveTipo.DESTROCOS_1}) {
            String id = t.destrocos() ? "destrocos" : t.id;
            ITEMS.put(t, ModItems.ITEMS.register(id, () -> new NaveItem(t, new Item.Properties().stacksTo(1)
                    .rarity(t == NaveTipo.NAVE_PIRATA_CAPITANIA ? Rarity.EPIC : Rarity.RARE))));
        }
    }

    private final NaveTipo tipo;

    public NaveItem(NaveTipo tipo, Properties p) {
        super(p);
        this.tipo = tipo;
    }

    public static ItemStack of(NaveTipo tipo, String pintura) {
        NaveTipo key = tipo.destrocos() ? NaveTipo.DESTROCOS_1 : tipo;
        RegistryObject<Item> r = ITEMS.get(key);
        if (r == null) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(r.get());
        if (pintura != null && !pintura.isEmpty()) s.getOrCreateTag().putString("Pintura", pintura);
        return s;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        NaveTipo t = tipo;
        if (t.destrocos()) t = NaveTipo.values()[NaveTipo.DESTROCOS_1.ordinal() + level.random.nextInt(3)];
        String pintura = stack.hasTag() ? stack.getTag().getString("Pintura") : "";
        if (pintura.isEmpty()) {
            if (t.pinturas.equals("tropa")) {
                var c = player != null ? Factions.playerCorps(player) : null;
                pintura = c != null && c != com.lanterna.corps.BuiltinCorps.WHITE ? c.id().getPath() : "green";
            } else if (t.pinturas.equals("12")) pintura = String.valueOf(1 + level.random.nextInt(2));
        }
        BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
        NaveEntity n = NaveEntity.create(level, t, pintura);
        float yaw = player != null ? player.getYRot() : 0;
        Vec3 lugar = lugar(level, t, pos, yaw, player);
        if (lugar == null) {
            if (player != null) player.displayClientMessage(Component.translatable("nave.lanterna.sem_espaco"), true);
            return InteractionResult.FAIL;
        }
        n.moveTo(lugar.x, lugar.y, lugar.z, yaw, 0);
        if (player != null && !t.destrocos()) {
            n.setDono(player.getUUID());
            ItemStack key = TropaItems.ChaveNave.forShip(n.getUUID(), t.id, t.pinturas.equals("tropa") ? pintura : null);
            if (!player.getInventory().add(key)) player.drop(key, false);
        }
        level.addFreshEntity(n);
        if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    /**
     * Onde pôr a nave: no ponto clicado, mas empurrada para a frente até nenhuma parte dela pegar em quem colocou, e
     * subindo um pouco se o terreno for irregular. A nave fica virada para onde o jogador olha.
     */
    @Nullable
    static Vec3 lugar(Level level, NaveTipo t, BlockPos pos, float yaw, @Nullable Player player) {
        Vec3 frente = Vec3.directionFromRotation(0, yaw);
        // primeiro tenta no chão (indo para a frente), só depois um pouco acima
        for (int[] faixa : new int[][]{{0, 1}, {2, 3}}) for (double avanco = 0; avanco <= 14; avanco += 0.5) {
            double x = pos.getX() + 0.5 + frente.x * avanco, z = pos.getZ() + 0.5 + frente.z * avanco;
            for (int sobe = faixa[0]; sobe <= faixa[1]; sobe++) {
                double y = pos.getY() + sobe;
                java.util.List<AABB> caixas = NaveEntity.caixas(t, x, y, z, yaw, false);
                boolean ok = true;
                for (AABB b : caixas) {
                    AABB dentro = b.deflate(0.02);
                    if (!level.noCollision(dentro) || player != null && dentro.intersects(player.getBoundingBox().inflate(0.4))) { ok = false; break; }
                }
                if (ok) return new Vec3(x, y, z);
            }
        }
        return null;
    }

    @Override
    public Component getName(ItemStack stack) {
        String p = stack.hasTag() ? stack.getTag().getString("Pintura") : "";
        Component base = Component.translatable("entity.lanterna." + (tipo.destrocos() ? "destrocos" : tipo.id));
        if (!p.isEmpty() && tipo.pinturas.equals("tropa")) return Component.translatable("item.lanterna.nave_cor", base, Component.translatable("corps.lanterna." + p));
        return base;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.lanterna.nave.desc").withStyle(ChatFormatting.GRAY));
    }
}
