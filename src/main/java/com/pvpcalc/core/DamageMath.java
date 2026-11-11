package com.pvpcalc.core;

import java.util.function.DoubleUnaryOperator;

/** Pure combat formulas (no Minecraft classes) so they can be unit-tested. */
public final class DamageMath {
    private DamageMath() {}

    public static final class Def {
        public double armor, toughness, epf, absorption, health = 20.0;
        public int resist, breach;
        public boolean blocking;
    }

    public static final class Melee {
        public boolean v18;
        public double base;        // weapon attack damage WITHOUT Strength/Weakness
        public int strength, weakness;
        public double charge = 1.0; // attack cooldown progress (1.9+ only)
        public boolean crit;
        public double ench;        // enchant damage bonus for this target (already version-correct)
        public double smash;       // mace smash bonus
    }

    public static double sharpness(int level, boolean v18) {
        if (level <= 0) return 0.0;
        return v18 ? 1.25 * level : 0.5 * level + 0.5;
    }

    /** Raw melee damage before any defense. */
    public static double meleeRaw(Melee m) {
        double total;
        if (m.v18) {
            double attr = m.base * (1.0 + 1.3 * m.strength) - 0.5 * m.weakness;
            if (m.crit) attr *= 1.5;
            total = attr + m.ench;
        } else {
            double attr = Math.max(0.0, m.base + 3.0 * m.strength - 4.0 * m.weakness);
            double h = Num.clamp(m.charge, 0.0, 1.0);
            double f = attr * (0.2 + h * h * 0.8);
            if (m.crit) f *= 1.5;
            total = f + (m.ench + m.smash) * h;
        }
        return Math.max(0.0, Num.clean(total, 0.0));
    }

    public static double armorReduce(double dmg, double armor, double toughness, int breach) {
        double eff = Num.clamp(armor * (1.0 - 0.15 * breach), 0.0, 30.0);
        double f = 2.0 + toughness / 4.0;
        double g = Num.clamp(eff - Num.div(dmg, f, 0.0), eff * 0.2, 20.0);
        return dmg * (1.0 - g / 25.0);
    }

    public static double armorReduce18(double dmg, double armor) {
        return dmg * (1.0 - Num.clamp(armor, 0.0, 20.0) * 0.04);
    }

    public static double protReduce(double dmg, double epf, boolean v18) {
        double e = Num.clamp(epf, 0.0, 20.0);
        if (v18) e = Math.min(20.0, e * 0.75);
        return dmg * (1.0 - e / 25.0);
    }

    public static double resistReduce(double dmg, int level) {
        return level <= 0 ? dmg : dmg * Math.max(0.0, 1.0 - 0.2 * level);
    }

    /** Applies shield, armor, Resistance and Protection in vanilla order. Result is HP the victim loses before absorption. */
    public static double applyDefense(double raw, Def d, boolean v18) {
        if (d == null) return raw;
        if (d.blocking) return v18 ? raw * 0.5 : 0.0; // 1.9+: shield blocks fully, 1.8: sword block halves
        double x = v18 ? armorReduce18(raw, d.armor) : armorReduce(raw, d.armor, d.toughness, d.breach);
        x = resistReduce(x, d.resist);
        x = protReduce(x, d.epf, v18);
        return Math.max(0.0, Num.clean(x, 0.0));
    }

    /** Explosion damage scales with difficulty (this is how vanilla does it; plain melee does not). */
    public static double difficulty(double dmg, String diff) {
        if ("EASY".equalsIgnoreCase(diff)) return Math.min(dmg / 2.0 + 1.0, dmg);
        if ("HARD".equalsIgnoreCase(diff)) return dmg * 1.5;
        return dmg;
    }

    public static double explosion(double dist, double power, double exposure) {
        double q = power * 2.0;
        double d = Num.div(dist, q, 2.0);
        if (d > 1.0) return 0.0;
        double impact = (1.0 - d) * Num.clamp(exposure, 0.0, 1.0);
        return (int) ((impact * impact + impact) / 2.0 * 7.0 * q + 1.0);
    }

    /** Native mace smash bonus (4/block up to 3, 2/block up to 8, then 1/block) plus Density 0.5 per level per block. */
    public static double maceBonus(double fall, int density) {
        if (fall <= 1.5) return 0.0;
        double b;
        if (fall <= 3.0) b = 4.0 * fall;
        else if (fall <= 8.0) b = 12.0 + 2.0 * (fall - 3.0);
        else b = 22.0 + (fall - 8.0);
        return b + 0.5 * density * fall;
    }

    /** Smallest fall height (blocks) whose resulting damage reaches targetHp, or NaN when impossible. */
    public static double fallNeeded(DoubleUnaryOperator damageAtFall, double targetHp) {
        if (targetHp <= 0) return 0.0;
        if (damageAtFall.applyAsDouble(1.51) >= targetHp) return 1.5;
        double lo = 1.5, hi = 500.0;
        if (damageAtFall.applyAsDouble(hi) < targetHp) return Double.NaN;
        for (int k = 0; k < 60; k++) {
            double mid = (lo + hi) / 2.0;
            if (damageAtFall.applyAsDouble(mid) >= targetHp) hi = mid; else lo = mid;
        }
        return hi;
    }

    public static int hitsToKill(double effectiveHp, double dmgPerHit) {
        if (dmgPerHit <= 0.0) return Integer.MAX_VALUE;
        return (int) Math.max(1, Math.ceil(Num.clean(effectiveHp, 0.0) / dmgPerHit - 1e-9));
    }
}
