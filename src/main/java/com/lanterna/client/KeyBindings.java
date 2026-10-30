package com.lanterna.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {
    public static final String CATEGORY = "key.categories.lanterna";

    public static final KeyMapping TOGGLE_FLIGHT = new KeyMapping(
            "key.lanterna.toggle_flight",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY);

    public static final KeyMapping POCKET_RECHARGE = new KeyMapping(
            "key.lanterna.pocket_recharge",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY);

    public static final KeyMapping OPEN_LANTERN = new KeyMapping(
            "key.lanterna.open_lantern",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY);

    public static final KeyMapping TOGGLE_POWER = new KeyMapping(
            "key.lanterna.toggle_power",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY);

    public static final KeyMapping STAR_MAP = new KeyMapping(
            "key.lanterna.star_map",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY);

    // ---- Construtos e missões ----
    public static final KeyMapping CONSTRUCT_WHEEL = new KeyMapping(
            "key.lanterna.construct_wheel", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);

    public static final KeyMapping CONSTRUCT_USE = new KeyMapping(
            "key.lanterna.construct_use", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);

    public static final KeyMapping[] PRESETS = {
            preset(1, GLFW.GLFW_KEY_1), preset(2, GLFW.GLFW_KEY_2), preset(3, GLFW.GLFW_KEY_3), preset(4, GLFW.GLFW_KEY_4)};

    public static final KeyMapping JOURNAL = new KeyMapping(
            "key.lanterna.journal", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);

    // ---- Naves ----
    public static final KeyMapping TRAVAR_MIRA = new KeyMapping(
            "key.lanterna.travar_mira", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_TAB, CATEGORY);

    public static final KeyMapping NAVE_DESCER = new KeyMapping(
            "key.lanterna.nave_descer", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);
    public static final KeyMapping NAVE_PORTA = new KeyMapping(
            "key.lanterna.nave_porta", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);
    public static final KeyMapping NAVE_HANGAR = new KeyMapping(
            "key.lanterna.nave_hangar", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

    private static KeyMapping preset(int n, int key) {
        return new KeyMapping("key.lanterna.preset_" + n, KeyConflictContext.IN_GAME,
                net.minecraftforge.client.settings.KeyModifier.ALT, InputConstants.Type.KEYSYM, key, CATEGORY);
    }

    private KeyBindings() {}
}
