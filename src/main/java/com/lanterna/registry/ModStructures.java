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

    private ModStructures() {}
}
