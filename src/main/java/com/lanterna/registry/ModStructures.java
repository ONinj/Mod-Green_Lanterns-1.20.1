package com.lanterna.registry;

import com.lanterna.LanternaMod;
import com.lanterna.worldgen.FallenRingPiece;
import com.lanterna.worldgen.FallenRingStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Estruturas do mod na Terra: a cratera do Anel Caído. */
public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, LanternaMod.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, LanternaMod.MODID);

    public static final RegistryObject<StructureType<FallenRingStructure>> ANEL_CAIDO =
            TYPES.register("anel_caido", () -> () -> FallenRingStructure.CODEC);

    public static final RegistryObject<StructurePieceType> ANEL_CAIDO_PIECE =
            PIECES.register("anel_caido", () -> (StructurePieceType.ContextlessType) FallenRingPiece::new);

    public static final DeferredRegister<net.minecraft.world.level.levelgen.feature.Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, LanternaMod.MODID);

    /** A cidade única dos planetas das Tropas (0.48), gerada junto com o terreno. */
    public static final RegistryObject<com.lanterna.cidade.CidadeUnica.Recurso> CIDADE_UNICA = FEATURES.register("cidade_unica",
            () -> new com.lanterna.cidade.CidadeUnica.Recurso(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));

    public static final RegistryObject<com.lanterna.cidade.CidadeUnica.Limpeza> CIDADE_LIMPEZA = FEATURES.register("cidade_limpeza",
            () -> new com.lanterna.cidade.CidadeUnica.Limpeza(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));

    /** Hanks (0.48): superfície mecânica, poços de energia e a cidade do Núcleo. */
    public static final RegistryObject<com.lanterna.hanks.HanksGeracao> HANKS_SUPERFICIE = FEATURES.register("hanks_superficie",
            () -> new com.lanterna.hanks.HanksGeracao(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));

    /** A Cidade Aurora (0.48), na Terra. */
    public static final RegistryObject<com.lanterna.terra.AuroraGeracao> AURORA = FEATURES.register("aurora",
            () -> new com.lanterna.terra.AuroraGeracao(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<com.lanterna.terra.AuroraGeracao.Limpeza> AURORA_LIMPEZA = FEATURES.register("aurora_limpeza",
            () -> new com.lanterna.terra.AuroraGeracao.Limpeza(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));

    private ModStructures() {}
}
