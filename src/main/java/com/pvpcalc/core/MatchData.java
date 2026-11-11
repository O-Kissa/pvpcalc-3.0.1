package com.pvpcalc.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything measured during one match. */
public final class MatchData {
    public int id;
    public String kit = "NONE";
    public long startTick, endTick;
    public int swings, hits, crits, myTotems, myGapples;
    public double dealt, taken, maxHit;
    public int maxCombo, combos;
    public String result = "UNKNOWN";
    public String deathInfo = "";
    public String lastHit = "";
    public double lastHitDmg, overkill = Double.NaN, oppHpLeft = Double.NaN;
    public final List<Double> reactions = new ArrayList<>();
    public final Map<String, PlayerData> players = new LinkedHashMap<>();
    public boolean closed;
    public String winPrediction = "";

    public PlayerData player(String uuid, String name) {
        PlayerData p = players.get(uuid);
        if (p == null) {
            p = new PlayerData(uuid, name);
            players.put(uuid, p);
        }
        if (name != null && !name.isEmpty()) p.name = name;
        return p;
    }

    /** The opponent with the most damage exchanged. */
    public PlayerData primary() {
        PlayerData best = null;
        for (PlayerData p : players.values()) {
            if (best == null || p.dealt + p.taken > best.dealt + best.taken) best = p;
        }
        return best;
    }

    public double accuracy() { return Num.pct(Num.div(hits * 100.0, swings, 0.0)); }
    public double critPct() { return Num.pct(Num.div(crits * 100.0, hits, 0.0)); }
    public double avgReaction() {
        if (reactions.isEmpty()) return -1.0;
        double s = 0;
        for (double r : reactions) s += r;
        return s / reactions.size();
    }
    public double spacingPct() {
        double ticks = 0, sp = 0;
        for (PlayerData p : players.values()) { ticks += p.distTicks; sp += p.spacingTicks; }
        return Num.pct(Num.div(sp * 100.0, ticks, 0.0));
    }
    public double aggression() {
        double a = 0, r = 0;
        for (PlayerData p : players.values()) { a += p.approach; r += p.retreat; }
        return Num.div(a, a + r, 0.5);
    }
    public String style() {
        double g = aggression();
        return g >= 0.58 ? "aggressive" : (g <= 0.42 ? "defensive" : "balanced");
    }
    public double matchHack() {
        double m = 0;
        for (PlayerData p : players.values()) m = Math.max(m, p.hackPct);
        return m;
    }
}
