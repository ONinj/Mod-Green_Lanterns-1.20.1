package com.lanterna.energia;

import com.lanterna.LanternaMod;
import com.lanterna.registry.ModBlockEntities;
import com.lanterna.registry.ModBlocks;
import com.lanterna.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Energia da Emoção: Gerador Emocional (produz Emoção Bruta, um fluido), Refinaria Emocional (Emoção Bruta + redstone
 * = Cristal Emocional), Conversor de Luz (queima cristais e solta energia FE em volta) e a Mineradora Planetária
 * (gasta FE para extrair Essência de Poder dos planetas conquistados).
 * A energia é FE (Forge Energy), então qualquer mod de energia liga na mineradora. Com o Mekanism instalado, a Emoção
 * Bruta pode ser refinada nas máquinas dele e rende Núcleos Emocionais (10x mais energia).
 */
public final class EnergiaRegistro {
    public static final DeferredRegister<FluidType> TIPOS = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, LanternaMod.MODID);
    public static final DeferredRegister<Fluid> FLUIDOS = DeferredRegister.create(ForgeRegistries.FLUIDS, LanternaMod.MODID);

    public static final int COR_EMOCAO = 0xFFE070FF;

    public static final RegistryObject<FluidType> TIPO_EMOCAO = TIPOS.register("emocao_bruta", () -> new FluidType(FluidType.Properties.create()
            .descriptionId("fluid.lanterna.emocao_bruta").lightLevel(10).density(400).viscosity(700).temperature(320)) {
        @Override
        public void initializeClient(Consumer<IClientFluidTypeExtensions> c) {
            c.accept(new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return new ResourceLocation("minecraft", "block/water_still");
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return new ResourceLocation("minecraft", "block/water_flow");
                }

                @Override
                public int getTintColor() {
                    return COR_EMOCAO;
                }
            });
        }
    });

    public static RegistryObject<FlowingFluid> EMOCAO;
    public static RegistryObject<FlowingFluid> EMOCAO_FLUINDO;

    private static ForgeFlowingFluid.Properties props() {
        return new ForgeFlowingFluid.Properties(TIPO_EMOCAO, () -> EMOCAO.get(), () -> EMOCAO_FLUINDO.get());
    }

    static {
        EMOCAO = FLUIDOS.register("emocao_bruta", () -> new ForgeFlowingFluid.Source(props()));
        EMOCAO_FLUINDO = FLUIDOS.register("emocao_bruta_fluindo", () -> new ForgeFlowingFluid.Flowing(props()));
    }

    private static BlockBehaviour.Properties maquina() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4f, 8f).sound(SoundType.NETHERITE_BLOCK)
                .requiresCorrectToolForDrops().noOcclusion().lightLevel(s -> s.hasProperty(MaquinaBlock.ACESO) && s.getValue(MaquinaBlock.ACESO) ? 9 : 2);
    }

    // ---- blocos ----
    public static final RegistryObject<Block> GERADOR = ModBlocks.BLOCKS.register("gerador_emocional",
            () -> new GeradorEmocionalBlock(maquina()));
    public static final RegistryObject<Block> REFINARIA = ModBlocks.BLOCKS.register("refinaria_emocional",
            () -> new MaquinaBlock(maquina(), () -> EnergiaRegistro.REFINARIA_BE.get()));
    public static final RegistryObject<Block> CONVERSOR = ModBlocks.BLOCKS.register("conversor_luz",
            () -> new MaquinaBlock(maquina(), () -> EnergiaRegistro.CONVERSOR_BE.get()));
    public static final RegistryObject<Block> MINERADORA = ModBlocks.BLOCKS.register("mineradora_planetaria",
            () -> new MineradoraBlock(maquina().strength(6f, 30f)));

    // ---- itens ----
    public static final RegistryObject<Item> GERADOR_ITEM = blocoItem("gerador_emocional", GERADOR, Rarity.UNCOMMON);
    public static final RegistryObject<Item> REFINARIA_ITEM = blocoItem("refinaria_emocional", REFINARIA, Rarity.UNCOMMON);
    public static final RegistryObject<Item> CONVERSOR_ITEM = blocoItem("conversor_luz", CONVERSOR, Rarity.UNCOMMON);
    public static final RegistryObject<Item> MINERADORA_ITEM = blocoItem("mineradora_planetaria", MINERADORA, Rarity.EPIC);
    /** Refino próprio (sem Mekanism). */
    public static final RegistryObject<Item> CRISTAL = item("cristal_emocional", Rarity.UNCOMMON);
    /** Caminho do Mekanism: bruto (câmara de reação) -> puro (purificação) -> 2 núcleos (enriquecimento). */
    public static final RegistryObject<Item> CRISTAL_BRUTO = item("cristal_emocional_bruto", Rarity.UNCOMMON);
    public static final RegistryObject<Item> CRISTAL_PURO = item("cristal_emocional_puro", Rarity.RARE);
    public static final RegistryObject<Item> NUCLEO = item("nucleo_emocional", Rarity.RARE);

    // ---- block entities ----
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<GeradorEmocionalBE>> GERADOR_BE = ModBlockEntities.BLOCK_ENTITIES.register("gerador_emocional",
            () -> BlockEntityType.Builder.of(GeradorEmocionalBE::new, GERADOR.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<RefinariaBE>> REFINARIA_BE = ModBlockEntities.BLOCK_ENTITIES.register("refinaria_emocional",
            () -> BlockEntityType.Builder.of(RefinariaBE::new, REFINARIA.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<ConversorBE>> CONVERSOR_BE = ModBlockEntities.BLOCK_ENTITIES.register("conversor_luz",
            () -> BlockEntityType.Builder.of(ConversorBE::new, CONVERSOR.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<MineradoraBE>> MINERADORA_BE = ModBlockEntities.BLOCK_ENTITIES.register("mineradora_planetaria",
            () -> BlockEntityType.Builder.of(MineradoraBE::new, MINERADORA.get()).build(null));

    private static RegistryObject<Item> blocoItem(String nome, RegistryObject<Block> b, Rarity r) {
        return ModItems.ITEMS.register(nome, () -> new BlockItem(b.get(), new Item.Properties().rarity(r)) {
            @Override
            public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> tip, TooltipFlag f) {
                tip.add(Component.translatable("block.lanterna." + nome + ".desc").withStyle(ChatFormatting.GRAY));
            }
        });
    }

    private static RegistryObject<Item> item(String nome, Rarity r) {
        return ModItems.ITEMS.register(nome, () -> new Item(new Item.Properties().rarity(r)) {
            @Override
            public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> tip, TooltipFlag f) {
                tip.add(Component.translatable("item.lanterna." + nome + ".desc").withStyle(ChatFormatting.GRAY));
            }
        });
    }

    public static void init(IEventBus bus) {
        TIPOS.register(bus);
        FLUIDOS.register(bus);
    }

    private EnergiaRegistro() {}
}
