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
        n.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player != null ? player.getYRot() : 0, 0);
        if (!level.noCollision(n, n.getBoundingBox())) {
            if (player != null) player.displayClientMessage(Component.translatable("nave.lanterna.sem_espaco"), true);
            return InteractionResult.FAIL;
        }
        if (player != null && !t.destrocos()) {
            n.setDono(player.getUUID());
            ItemStack key = TropaItems.ChaveNave.forShip(n.getUUID(), t.id, t.pinturas.equals("tropa") ? pintura : null);
            if (!player.getInventory().add(key)) player.drop(key, false);
        }
        level.addFreshEntity(n);
        if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.CONSUME;
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
