package com.lanterna.client;

/** Estado do efeito de tela durante a entrega do anel. */
public final class ClientCutscene {
    private static String corps = "";
    private static int color;
    private static int ticksLeft;
    private static int total;

    public static void start(String corpsId, int rgb, int duration) {
        corps = corpsId;
        color = rgb;
        total = Math.max(1, duration);
        ticksLeft = total;
    }

    public static void tick() {
        if (ticksLeft > 0) ticksLeft--;
    }

    public static boolean active() {
        return ticksLeft > 0;
    }

    /** 0 no início, 1 no fim, suavizado entre ticks. */
    public static float progress(float partialTick) {
        return Math.min(1f, (total - ticksLeft + partialTick) / total);
    }

    public static int ticksLeft() { return ticksLeft; }
    public static int color() { return color; }
    public static boolean isRed() { return corps.endsWith(":red"); }

    /** Nome curto da Tropa: "green", "yellow"... */
    public static String style() {
        int i = corps.indexOf(':');
        return i >= 0 ? corps.substring(i + 1) : corps;
    }

    public static void reset() {
        ticksLeft = 0;
    }

    private ClientCutscene() {}
}
