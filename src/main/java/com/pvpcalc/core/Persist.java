package com.pvpcalc.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Long-term stats. NOT cleared by /pvpcalc reset (only by "stats resetall"). Stored in stats.json. */
public final class Persist {
    public static final class KitRec { public int wins, losses, streak, best; }
    public static final class VsRec { public int wins, losses; }
    public static final class Rec { public double maxHit; public int maxCombo; }
    public static final class Hist {
        public String kit = "", result = "";
        public double dealt, taken, acc, crit;
    }

    public final Map<String, KitRec> kits = new TreeMap<>();
    public final Map<String, String> names = new TreeMap<>();
    public final Map<String, Map<String, VsRec>> vs = new TreeMap<>();
    public final Map<String, Rec> records = new TreeMap<>();
    public final List<Hist> history = new ArrayList<>();
    public final Map<String, Integer> flagged = new TreeMap<>();

    public static String recKey(String kit, String uuid) { return kit + "|" + uuid; }

    public KitRec kit(String kit) { return kits.computeIfAbsent(kit, k -> new KitRec()); }

    /** result: WIN / LOSS / other (ignored). uuid may be null when no opponent is known. */
    public void recordResult(String kit, String result, String uuid, String name) {
        boolean win = "WIN".equals(result), loss = "LOSS".equals(result);
        if (!win && !loss) return;
        for (String k : new String[]{kit, "ALL"}) {
            KitRec r = kit(k);
            if (win) { r.wins++; r.streak++; r.best = Math.max(r.best, r.streak); }
            else { r.losses++; r.streak = 0; }
        }
        if (uuid != null && !uuid.isEmpty()) {
            if (name != null && !name.isEmpty()) names.put(uuid, name);
            VsRec v = vs.computeIfAbsent(uuid, u -> new TreeMap<>()).computeIfAbsent(kit, k -> new VsRec());
            if (win) v.wins++; else v.losses++;
        }
    }

    public void updateRecord(String kit, String uuid, String name, double maxHit, int maxCombo) {
        if (name != null && !name.isEmpty() && uuid != null) names.put(uuid, name);
        Rec r = records.computeIfAbsent(recKey(kit, uuid == null ? "" : uuid), k -> new Rec());
        r.maxHit = Math.max(r.maxHit, maxHit);
        r.maxCombo = Math.max(r.maxCombo, maxCombo);
    }

    public void addHistory(Hist h) {
        history.add(h);
        while (history.size() > 500) history.remove(0);
    }

    /** Kit with the most wins against this player (ties: fewer losses). Null when no wins recorded. */
    public String recommend(String uuid) {
        Map<String, VsRec> m = vs.get(uuid);
        if (m == null) return null;
        String best = null;
        VsRec bv = null;
        for (Map.Entry<String, VsRec> e : m.entrySet()) {
            VsRec v = e.getValue();
            if (v.wins <= 0) continue;
            if (bv == null || v.wins > bv.wins || (v.wins == bv.wins && v.losses < bv.losses)) { best = e.getKey(); bv = v; }
        }
        return best;
    }

    /** Average damage dealt and win rate in the last N matches of a kit versus the N before. {recent, previous, recentWin%, prevWin%, count}. */
    public double[] trend(String kit, int window) {
        List<Hist> list = new ArrayList<>();
        for (Hist h : history) if (kit == null || kit.isEmpty() || h.kit.equals(kit)) list.add(h);
        int n = list.size();
        int w = Math.max(1, window);
        double[] out = new double[5];
        out[4] = n;
        if (n == 0) return out;
        int from = Math.max(0, n - w), pfrom = Math.max(0, from - w);
        out[0] = avgDealt(list, from, n);
        out[2] = winRate(list, from, n);
        if (from > pfrom) {
            out[1] = avgDealt(list, pfrom, from);
            out[3] = winRate(list, pfrom, from);
        } else {
            out[1] = Double.NaN;
            out[3] = Double.NaN;
        }
        return out;
    }

    private static double avgDealt(List<Hist> l, int a, int b) {
        double s = 0;
        for (int i = a; i < b; i++) s += l.get(i).dealt;
        return Num.div(s, b - a, 0.0);
    }

    private static double winRate(List<Hist> l, int a, int b) {
        int w = 0, g = 0;
        for (int i = a; i < b; i++) {
            String r = l.get(i).result;
            if ("WIN".equals(r)) { w++; g++; } else if ("LOSS".equals(r)) g++;
        }
        return Num.div(w * 100.0, g, 0.0);
    }

    public int flagMatch(String uuid, String name) {
        if (name != null && !name.isEmpty()) names.put(uuid, name);
        return flagged.merge(uuid, 1, Integer::sum);
    }

    // ---------------- JSON ----------------

    public String toJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> k = new LinkedHashMap<>();
        kits.forEach((n, r) -> { Map<String, Object> m = new LinkedHashMap<>(); m.put("w", r.wins); m.put("l", r.losses); m.put("s", r.streak); m.put("b", r.best); k.put(n, m); });
        root.put("kits", k);
        root.put("names", new LinkedHashMap<>(names));
        Map<String, Object> v = new LinkedHashMap<>();
        vs.forEach((u, mm) -> { Map<String, Object> x = new LinkedHashMap<>(); mm.forEach((kit, r) -> { Map<String, Object> m = new LinkedHashMap<>(); m.put("w", r.wins); m.put("l", r.losses); x.put(kit, m); }); v.put(u, x); });
        root.put("vs", v);
        Map<String, Object> rc = new LinkedHashMap<>();
        records.forEach((key, r) -> { Map<String, Object> m = new LinkedHashMap<>(); m.put("h", r.maxHit); m.put("c", r.maxCombo); rc.put(key, m); });
        root.put("records", rc);
        List<Object> h = new ArrayList<>();
        for (Hist x : history) { Map<String, Object> m = new LinkedHashMap<>(); m.put("k", x.kit); m.put("r", x.result); m.put("d", x.dealt); m.put("t", x.taken); m.put("a", x.acc); m.put("c", x.crit); h.add(m); }
        root.put("history", h);
        root.put("flagged", new LinkedHashMap<>(flagged));
        return MiniJson.write(root, false);
    }

    @SuppressWarnings("unchecked")
    public static Persist fromJson(String json) {
        Persist p = new Persist();
        try {
            Map<String, Object> root = MiniJson.parseObject(json);
            Object o = root.get("kits");
            if (o instanceof Map) for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                Map<String, Object> m = (Map<String, Object>) e.getValue();
                KitRec r = new KitRec(); r.wins = i(m, "w"); r.losses = i(m, "l"); r.streak = i(m, "s"); r.best = i(m, "b");
                p.kits.put(e.getKey(), r);
            }
            o = root.get("names");
            if (o instanceof Map) for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) p.names.put(e.getKey(), String.valueOf(e.getValue()));
            o = root.get("vs");
            if (o instanceof Map) for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                Map<String, VsRec> mm = new TreeMap<>();
                for (Map.Entry<String, Object> e2 : ((Map<String, Object>) e.getValue()).entrySet()) {
                    Map<String, Object> m = (Map<String, Object>) e2.getValue();
                    VsRec r = new VsRec(); r.wins = i(m, "w"); r.losses = i(m, "l"); mm.put(e2.getKey(), r);
                }
                p.vs.put(e.getKey(), mm);
            }
            o = root.get("records");
            if (o instanceof Map) for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                Map<String, Object> m = (Map<String, Object>) e.getValue();
                Rec r = new Rec(); r.maxHit = d(m, "h"); r.maxCombo = i(m, "c"); p.records.put(e.getKey(), r);
            }
            o = root.get("history");
            if (o instanceof List) for (Object x : (List<Object>) o) {
                Map<String, Object> m = (Map<String, Object>) x;
                Hist h = new Hist(); h.kit = String.valueOf(m.get("k")); h.result = String.valueOf(m.get("r"));
                h.dealt = d(m, "d"); h.taken = d(m, "t"); h.acc = d(m, "a"); h.crit = d(m, "c"); p.history.add(h);
            }
            o = root.get("flagged");
            if (o instanceof Map) for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet())
                p.flagged.put(e.getKey(), ((Number) e.getValue()).intValue());
        } catch (RuntimeException ex) {
            return new Persist();
        }
        return p;
    }

    private static int i(Map<String, Object> m, String k) { Object o = m.get(k); return o instanceof Number ? ((Number) o).intValue() : 0; }
    private static double d(Map<String, Object> m, String k) { Object o = m.get(k); return o instanceof Number ? ((Number) o).doubleValue() : 0.0; }
}
