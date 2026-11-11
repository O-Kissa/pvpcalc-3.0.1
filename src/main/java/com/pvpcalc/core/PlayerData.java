package com.pvpcalc.core;

/** Per-opponent numbers inside one match. */
public final class PlayerData {
    public final String uuid;
    public String name;
    public double dealt, taken, maxHit, hpEnd = -1.0;
    public int hits, crits, theirHits;
    public int combo, maxCombo, combos, theirCombo, theirMaxCombo;
    public int totems, gapples, enchGapples, pearls, xp, potions;
    public double distSum;
    public int distTicks, spacingTicks, approach, retreat;
    public double hackPct;
    public int hackSamples;
    public String hackTop = "";
    public boolean died;

    public PlayerData(String uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public double critPct() { return Num.pct(Num.div(crits * 100.0, hits, 0.0)); }
    public double avgDist() { return Num.div(distSum, distTicks, 0.0); }
    public double spacingPct() { return Num.pct(Num.div(spacingTicks * 100.0, distTicks, 0.0)); }
    public double aggression() { return Num.div(approach, approach + retreat, 0.5); }
}
