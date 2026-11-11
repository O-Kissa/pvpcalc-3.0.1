package com.pvpcalc.core;

/** Running mean / standard deviation (a player's own "normal" behaviour). */
public final class Welford {
    public int n;
    private double mean, m2;

    public void add(double x) {
        x = Num.clean(x, 0.0);
        n++;
        double d = x - mean;
        mean += d / n;
        m2 += d * (x - mean);
    }

    public double mean() { return mean; }
    public double std() { return n < 2 ? 0.0 : Math.sqrt(Math.max(0.0, m2 / (n - 1))); }

    /** Evidence multiplier from the player's own baseline: <1 when the value is normal for them, >1 when far above. */
    public double factor(double value, int minN) {
        if (n < minN) return 1.0;
        double sd = Math.max(std(), 0.5);
        double z = (value - mean) / sd;
        if (z <= 1.0) return 0.5;
        if (z >= 3.0) return 1.3;
        return 1.0;
    }
}
