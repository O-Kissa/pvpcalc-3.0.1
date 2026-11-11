package com.pvpcalc.core;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns individual violations into a 0-100 "chance" per check and overall.
 * Evidence needs several violations inside a time window (confirmation) and decays over time.
 */
public final class HackModel {
    public static final class Cfg {
        public double decayPerSec = 0.4;
        public int confirmCount = 3;
        public double confirmWindowSec = 20.0;

        public static Cfg fromSettings() {
            Cfg c = new Cfg();
            c.decayPerSec = Settings.dbl("hack.decayPerSec");
            c.confirmCount = Settings.i("hack.confirmCount");
            c.confirmWindowSec = Settings.i("hack.confirmWindow");
            return c;
        }
    }

    private static final class CheckState {
        double evidence, last;
        final ArrayDeque<double[]> pending = new ArrayDeque<>(); // {time, points}
        int total;
    }

    private final Map<String, CheckState> checks = new LinkedHashMap<>();
    public final Welford cpsBase = new Welford();
    public final Welford speedBase = new Welford();
    public int samples;
    public int violationsTotal;
    private final Map<String, Integer> lastValue = new HashMap<>();

    private CheckState st(String c) { return checks.computeIfAbsent(c, k -> new CheckState()); }

    private void decay(CheckState s, double now, Cfg cfg) {
        double dt = Math.max(0.0, now - s.last);
        s.evidence = Math.max(0.0, s.evidence - dt * cfg.decayPerSec);
        s.last = now;
    }

    public void violation(String check, double points, double weight, double nowSec, Cfg cfg) {
        CheckState s = st(check);
        decay(s, nowSec, cfg);
        points = Num.clamp(points, 0.0, 10.0) * Num.clamp(weight, 0.0, 3.0);
        if (points <= 0.0) return;
        violationsTotal++;
        s.total++;
        while (!s.pending.isEmpty() && nowSec - s.pending.peekFirst()[0] > cfg.confirmWindowSec) s.pending.pollFirst();
        s.pending.addLast(new double[]{nowSec, points});
        if (s.pending.size() >= cfg.confirmCount) {
            for (double[] p : s.pending) s.evidence += p[1];
            s.pending.clear();
        }
    }

    public double chance(String check, double nowSec, Cfg cfg) {
        CheckState s = checks.get(check);
        if (s == null) return 0.0;
        decay(s, nowSec, cfg);
        return Num.pct(100.0 * (1.0 - Math.exp(-s.evidence / 8.0)));
    }

    /** Combined chance: 1 - product(1 - chance_i). */
    public double total(double nowSec, Cfg cfg) {
        double keep = 1.0;
        for (String c : checks.keySet()) keep *= 1.0 - chance(c, nowSec, cfg) / 100.0;
        return Num.pct((1.0 - keep) * 100.0);
    }

    public String top(double nowSec, Cfg cfg) {
        String best = "";
        double b = 0.0;
        for (String c : checks.keySet()) {
            double v = chance(c, nowSec, cfg);
            if (v > b) { b = v; best = c; }
        }
        return best;
    }

    public int violationCount(String check) {
        CheckState s = checks.get(check);
        return s == null ? 0 : s.total;
    }

    /** True if "value" differs from the last remembered value (used to skip writing unchanged CSV rows). */
    public boolean changed(String key, int value) {
        Integer old = lastValue.put(key, value);
        return old == null || old != value;
    }

    public boolean highConfidence(int minSamples) {
        return samples >= minSamples * 5;
    }
}
