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

    private KeyBindings() {}
}
