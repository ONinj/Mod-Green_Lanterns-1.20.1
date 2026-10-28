package com.lanterna.corps;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Uma Tropa do espectro emocional, lida de data/<namespace>/lanterna_corps/<nome>.json
 *
 * Exemplo:
 * {
 *   "color": "#2BFF4A",
 *   "emotion": "will",
 *   "max_charge": 1000,
 *   "flight_drain": 2
 * }
 */
public record CorpsDefinition(ResourceLocation id, int color, String emotion, int maxCharge, int flightDrain) {

    public static CorpsDefinition fromJson(ResourceLocation id, JsonObject json) {
        String colorText = GsonHelper.getAsString(json, "color", "#2BFF4A").replace("#", "");
        int color = Integer.parseInt(colorText, 16) & 0xFFFFFF;
        String emotion = GsonHelper.getAsString(json, "emotion", "will");
        int maxCharge = Math.max(1, GsonHelper.getAsInt(json, "max_charge", 1000));
        int flightDrain = Math.max(0, GsonHelper.getAsInt(json, "flight_drain", 2));
        return new CorpsDefinition(id, color, emotion, maxCharge, flightDrain);
    }

    public static CorpsDefinition fallback(ResourceLocation id) {
        return new CorpsDefinition(id, BuiltinCorps.colorOf(id), "will", 1000, 2);
    }

    /** Chave de tradução curta, ex: corps.lanterna.green -> "Verde" */
    public static String nameKey(ResourceLocation id) {
        return "corps." + id.getNamespace() + "." + id.getPath();
    }

    /** Chave de tradução longa, ex: corps.lanterna.green.full -> "Tropa dos Lanternas Verdes" */
    public static String fullNameKey(ResourceLocation id) {
        return nameKey(id) + ".full";
    }
}
