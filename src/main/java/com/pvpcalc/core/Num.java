package com.pvpcalc.core;

import java.util.Locale;

/** NaN / infinity / divide-by-zero safe helpers. Every formula in the mod goes through these. */
public final class Num {
    private Num() {}

    public static double clean(double v, double def) {
        return (Double.isNaN(v) || Double.isInfinite(v)) ? def : v;
    }

    public static double clamp(double v, double lo, double hi) {
        v = clean(v, lo);
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double pct(double v) {
        return clamp(v, 0.0, 100.0);
    }

    public static double div(double a, double b, double def) {
        if (b == 0.0 || Double.isNaN(b) || Double.isInfinite(b)) return def;
        return clean(a / b, def);
    }

    public static String fmt(double v, int decimals) {
        v = clean(v, 0.0);
        int d = (int) clamp(decimals, 0, 6);
        return String.format(Locale.ROOT, "%." + d + "f", v);
    }

    public static String fmt1(double v) {
        return fmt(v, 1);
    }

    public static double wrapDeg(double d) {
        d = clean(d, 0.0) % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }
}
