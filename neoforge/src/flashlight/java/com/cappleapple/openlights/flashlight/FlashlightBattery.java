package com.cappleapple.openlights.flashlight;

/** Battery arithmetic shared by the item tick and RF/FE capability. */
public final class FlashlightBattery {
    private FlashlightBattery() {}
    public static int clamp(int stored, int capacity) {
        return Math.max(0, Math.min(stored, Math.max(0, capacity)));
    }
    public static int accepted(int stored, int capacity, int requested, int limit) {
        return Math.max(0, Math.min(Math.min(requested, Math.max(0, limit)), Math.max(0, capacity) - clamp(stored, capacity)));
    }
    public static boolean canRun(int stored, int capacity, int cost) {
        return clamp(stored, capacity) >= Math.max(0, cost);
    }
    public static int remaining(int stored, int capacity, int cost) {
        return Math.max(0, clamp(stored, capacity) - Math.max(0, cost));
    }
}
