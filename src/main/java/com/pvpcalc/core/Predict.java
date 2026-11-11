package com.pvpcalc.core;

/** Win estimate from time-to-kill of both sides. */
public final class Predict {
    private Predict() {}

    public static final double TOTEM_EXTRA_HP = 9.0; // 1 HP + 8 absorption after a pop, roughly

    public static double ttk(double effectiveHp, double dmgPerHit, double intervalSec) {
        int hits = DamageMath.hitsToKill(effectiveHp, dmgPerHit);
        if (hits == Integer.MAX_VALUE) return 1e9;
        return hits * Math.max(0.05, intervalSec);
    }

    /** Percent chance that "me" wins. Lower TTK is better; k controls how sharply a difference counts. */
    public static double winPct(double ttkMe, double ttkOpp) {
        if (ttkMe >= 1e9 && ttkOpp >= 1e9) return 50.0;
        if (ttkMe >= 1e9) return 1.0;
        if (ttkOpp >= 1e9) return 99.0;
        double k = 2.5;
        double a = Math.pow(ttkOpp, k), b = Math.pow(ttkMe, k);
        return Num.clamp(100.0 * Num.div(a, a + b, 0.5), 1.0, 99.0);
    }
}
