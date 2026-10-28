package com.lanterna.corps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.lanterna.LanternaMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Carrega todas as Tropas dos datapacks (pasta "lanterna_corps").
 * Recarrega com /reload, então dá para ajustar valores sem recompilar.
 */
public class CorpsManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static Map<ResourceLocation, CorpsDefinition> corps = Map.of();

    public CorpsManager() {
        super(GSON, "lanterna_corps");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, CorpsDefinition> loaded = new HashMap<>();
        map.forEach((id, json) -> {
            try {
                loaded.put(id, CorpsDefinition.fromJson(id, json.getAsJsonObject()));
            } catch (Exception e) {
                LanternaMod.LOGGER.error("Erro ao ler a Tropa {}: {}", id, e.getMessage());
            }
        });
        corps = Map.copyOf(loaded);
        LanternaMod.LOGGER.info("Lanterna: {} Tropas carregadas", corps.size());
    }

    public static CorpsDefinition get(ResourceLocation id) {
        CorpsDefinition def = corps.get(id);
        return def != null ? def : CorpsDefinition.fallback(id);
    }

    public static boolean exists(ResourceLocation id) {
        return corps.containsKey(id);
    }

    public static Collection<ResourceLocation> ids() {
        return corps.keySet();
    }
}
