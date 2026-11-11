package com.pvpcalc.util;

/** Own tick counter (incremented once per client tick while a world is loaded). */
public final class Clock {
    private static long tick;

    private Clock() {}

    public static long tick() { return tick; }
    public static void advance() { tick++; }
    public static double seconds() { return tick / 20.0; }
}
