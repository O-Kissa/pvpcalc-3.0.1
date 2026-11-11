package com.pvpcalc.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One registry for every setting. Commands, the GUI, status, export/import and reset all use this,
 * so a change made in one place is immediately visible in all the others.
 */
public final class Settings {
    public enum Type { BOOL, INT, DOUBLE, ENUM, STRING }

    public static final class Def {
        public final String id, tab;
        public final Type type;
        public final Object def;
        public final double min, max;
        public final List<String> options;
        public boolean hidden;

        Def(String tab, String id, Type type, Object def, double min, double max, List<String> options) {
            this.tab = tab; this.id = id; this.type = type; this.def = def;
            this.min = min; this.max = max; this.options = options;
        }
    }

    public static final int CONFIG_VERSION = 3;
    public static final String[] TABS = {"general", "pvp", "reports", "hack", "checks", "hud", "csv", "hotkeys", "advanced"};
    public static final String[] CHECKS = {"reach", "speed", "fly", "aimbot", "autoclicker", "velocity", "nofall", "autototem", "mace"};

    public static final List<Def> ALL = new ArrayList<>();
    private static final Map<String, Def> BY_ID = new LinkedHashMap<>();
    private static final Map<String, Object> VALUES = new LinkedHashMap<>();
    public static volatile boolean dirty;

    private static Def add(String tab, String id, Type t, Object def, double min, double max, List<String> opts) {
        Def d = new Def(tab, id, t, def, min, max, opts);
        ALL.add(d);
        BY_ID.put(id, d);
        VALUES.put(id, def);
        return d;
    }
    private static Def b(String tab, String id, boolean def) { return add(tab, id, Type.BOOL, def, 0, 1, null); }
    private static Def i(String tab, String id, int def, int min, int max) { return add(tab, id, Type.INT, def, min, max, null); }
    private static Def d(String tab, String id, double def, double min, double max) { return add(tab, id, Type.DOUBLE, def, min, max, null); }
    private static Def s(String tab, String id, String def) { return add(tab, id, Type.STRING, def, 0, 0, null); }
    private static Def e(String tab, String id, String def, String... opts) { return add(tab, id, Type.ENUM, def, 0, 0, List.of(opts)); }

    static {
        String G = "general", P = "pvp", R = "reports", H = "hack", C = "checks", U = "hud", V = "csv", A = "advanced";
        b(G, "enabled", true);
        e(G, "language", "en", "en", "fi");
        s(G, "chatPrefix", "[PvPCalc]");
        i(G, "messageCooldown", 3, 0, 60);
        b(G, "updateNotice", true);
        b(G, "performanceMode", false);
        b(G, "quietMode", false);
        b(G, "serverPresets", false);
        b(G, "firstRunDone", false).hidden = true;

        List<String> kits = new ArrayList<>();
        kits.add("");
        kits.addAll(KitDefs.all());
        add(P, "kit", Type.ENUM, "", 0, 0, kits);
        e(P, "combatSystem", "1.9+", "1.9+", "1.8");
        e(P, "target", "looked", "looked", "self");
        b(P, "assumeShield", false);
        e(P, "difficulty", "NORMAL", "EASY", "NORMAL", "HARD");
        e(P, "healthUnit", "hp", "hp", "hearts", "both");
        i(P, "decimals", 1, 0, 3);
        e(P, "cooldownAssumption", "current", "current", "full");
        e(P, "critHandling", "auto", "auto", "always", "off");
        e(P, "countEffects", "both", "both", "own", "opponent", "ignore");
        i(P, "cps18", 8, 1, 20);
        d(P, "spearFactor", 3.0, 0.0, 20.0);
        b(P, "smpMode", false);
        b(P, "trackTotems", true);
        b(P, "trackGapples", true);
        b(P, "trackPearls", true);
        b(P, "trackXp", true);
        b(P, "trackPotions", true);
        i(P, "matchInactivity", 120, 0, 600);
        i(P, "goalAccuracy", 60, 0, 100);
        i(P, "goalCrit", 30, 0, 100);
        i(P, "trendWindow", 10, 1, 100);
        b(P, "soundPop", true);
        b(P, "soundKill", true);
        s(P, "popSound", "minecraft:block.note_block.pling");
        s(P, "killSound", "minecraft:entity.player.levelup");

        b(R, "autoReport", true);
        e(R, "reportTemplate", "short", "short", "full", "discord");
        b(R, "anonymousCopy", false);
        s(R, "disclaimer", "Estimate, not proof");

        b(H, "hack.enabled", true);
        b(H, "hack.autoTrackHit", true);
        b(H, "hack.autoTrackNearest", false);
        i(H, "hack.autoTrackRadius", 12, 3, 64);
        e(H, "hack.sensitivity", "normal", "low", "normal", "high");
        i(H, "hack.alertThreshold", 80, 10, 100);
        e(H, "hack.alertMode", "both", "both", "chat", "sound", "off");
        b(H, "hack.centerText", true);
        s(H, "hack.alertSound", "minecraft:entity.warden.sonic_boom");
        b(H, "hack.logCsv", true);
        i(H, "hack.minRowSeconds", 60, 10, 600);
        i(H, "hack.minSamples", 40, 5, 500);
        i(H, "hack.confirmCount", 3, 1, 10);
        i(H, "hack.confirmWindow", 20, 2, 120);
        d(H, "hack.decayPerSec", 0.4, 0.0, 10.0);
        b(H, "hack.pingComp", true);
        i(H, "hack.pingLimit", 300, 50, 1000);
        b(H, "hack.tpsComp", true);
        b(H, "hack.autoWhitelistTeam", true);
        s(H, "hack.whitelist", "");
        b(H, "hack.training", false);
        i(H, "hack.maxAnalyzed", 8, 1, 30);
        b(H, "hack.baseline", true);
        i(H, "hack.repeatMatches", 2, 2, 10);
        s(H, "hack.falsePositives", "all");

        double[] lim = {3.5, 1.3, 14, 70, 16, 0.15, 4.5, 2, 9};
        boolean[] on = {true, true, true, true, true, false, false, false, false};
        for (int k = 0; k < CHECKS.length; k++) {
            String c = CHECKS[k];
            b(C, "chk." + c + ".on", on[k]);
            d(C, "chk." + c + ".weight", 1.0, 0.0, 3.0);
            d(C, "chk." + c + ".limit", lim[k], 0.0, 1000.0);
            i(C, "chk." + c + ".alert", 0, 0, 100);
        }

        b(U, "hud.visible", true);
        b(U, "hud.minimized", false);
        e(U, "hud.corner", "tr", "tr", "tl", "br", "bl");
        i(U, "hud.x", 4, 0, 2000);
        i(U, "hud.y", 4, 0, 2000);
        i(U, "hud.opacity", 100, 10, 100);
        s(U, "hud.color", "FFFFFF");
        e(U, "hud.rule", "always", "always", "weapon", "hit");
        b(U, "hud.colorCode", true);
        i(U, "hud.green", 30, 0, 100);
        i(U, "hud.yellow", 60, 0, 100);
        b(U, "hud.oppHp", true);
        b(U, "hud.durability", true);
        b(U, "hud.counters", true);
        b(U, "hud.mace", true);
        b(U, "hud.prediction", true);
        b(U, "hud.hack", true);
        b(U, "hud.dmg", true);
        b(U, "hud.htk", true);
        b(U, "hud.ttk", false);
        b(U, "hud.cps", false);
        b(U, "hud.distance", false);

        e(V, "csv.delimiter", "comma", "comma", "semicolon");
        b(V, "csv.bom", true);
        i(V, "csv.maxMb", 20, 1, 500);
        b(V, "csv.formulaProtect", true);

        b(A, "debug", false);
        b(A, "safeMode", true);
        i(A, "autoDisableErrors", 5, 1, 50);
        i(A, "tickBudgetMs", 8, 1, 50);
        s(A, "serverRules", "");
    }

    private Settings() {}

    // ---------------- access ----------------

    public static Def def(String id) { return BY_ID.get(id); }
    public static boolean exists(String id) { return BY_ID.containsKey(id); }
    public static List<String> ids() { return new ArrayList<>(BY_ID.keySet()); }
    public static Object get(String id) { return VALUES.get(id); }

    public static boolean bool(String id) {
        Object o = VALUES.get(id);
        return o instanceof Boolean && (Boolean) o;
    }
    public static int i(String id) {
        Object o = VALUES.get(id);
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }
    public static double dbl(String id) {
        Object o = VALUES.get(id);
        return o instanceof Number ? ((Number) o).doubleValue() : 0.0;
    }
    public static String str(String id) {
        Object o = VALUES.get(id);
        return o == null ? "" : String.valueOf(o);
    }
    public static boolean is(String id, String v) { return str(id).equalsIgnoreCase(v); }

    public static boolean isDefault(String id) {
        Def d = BY_ID.get(id);
        return d == null || String.valueOf(d.def).equals(String.valueOf(VALUES.get(id)));
    }

    public static String show(String id) {
        Object v = VALUES.get(id);
        if (v instanceof Double) return Num.fmt((Double) v, 2);
        String s = String.valueOf(v);
        return s.isEmpty() ? "-" : s;
    }

    public static String showDefault(String id) {
        Def d = BY_ID.get(id);
        if (d == null) return "?";
        if (d.def instanceof Double) return Num.fmt((Double) d.def, 2);
        String s = String.valueOf(d.def);
        return s.isEmpty() ? "-" : s;
    }

    /** Sets a value from text. Returns null on success, otherwise an error key (err.*). Numbers are clamped. */
    public static String set(String id, String text) {
        Def d = BY_ID.get(id);
        if (d == null) return "err.unknown_setting";
        if (text == null) text = "";
        Object nv;
        switch (d.type) {
            case BOOL: {
                String t = text.trim().toLowerCase(Locale.ROOT);
                if (t.equals("on") || t.equals("true") || t.equals("yes") || t.equals("1") || t.equals("paalle") || t.equals("päällä")) nv = true;
                else if (t.equals("off") || t.equals("false") || t.equals("no") || t.equals("0") || t.equals("pois")) nv = false;
                else return "err.bad_bool";
                break;
            }
            case INT: {
                try {
                    double v = Double.parseDouble(text.trim().replace(',', '.'));
                    nv = (int) Math.round(Num.clamp(v, d.min, d.max));
                } catch (NumberFormatException ex) {
                    return "err.bad_number";
                }
                break;
            }
            case DOUBLE: {
                try {
                    double v = Double.parseDouble(text.trim().replace(',', '.'));
                    nv = Num.clamp(v, d.min, d.max);
                } catch (NumberFormatException ex) {
                    return "err.bad_number";
                }
                break;
            }
            case ENUM: {
                String t = text.trim();
                if (t.equalsIgnoreCase("none")) t = "";
                String found = null;
                for (String o : d.options) if (o.equalsIgnoreCase(t)) found = o;
                if (found == null) return "err.bad_enum";
                nv = found;
                break;
            }
            default:
                nv = text;
        }
        if (!String.valueOf(nv).equals(String.valueOf(VALUES.get(id)))) {
            VALUES.put(id, nv);
            dirty = true;
        }
        return null;
    }

    public static void reset(String id) {
        Def d = BY_ID.get(id);
        if (d != null && !isDefault(id)) { VALUES.put(id, d.def); dirty = true; }
    }

    public static void resetTab(String tab) {
        for (Def d : ALL) if (d.tab.equals(tab) && !d.hidden) reset(d.id);
    }

    public static void resetAll() {
        for (Def d : ALL) reset(d.id);
        dirty = true;
    }

    /** Cycles an ENUM/BOOL value forward (dir=1) or back (dir=-1). */
    public static void cycle(String id, int dir) {
        Def d = BY_ID.get(id);
        if (d == null) return;
        if (d.type == Type.BOOL) { set(id, bool(id) ? "off" : "on"); return; }
        if (d.type != Type.ENUM) return;
        int idx = d.options.indexOf(str(id));
        if (idx < 0) idx = 0;
        idx = (idx + dir + d.options.size()) % d.options.size();
        set(id, d.options.get(idx));
    }

    // ---------------- persistence ----------------

    public static String toJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("configVersion", CONFIG_VERSION);
        root.put("values", new LinkedHashMap<>(VALUES));
        return MiniJson.write(root, true);
    }

    /** Returns null on success, otherwise an error key. Unknown keys are ignored; bad values keep the old value. */
    public static String fromJson(String json) {
        try {
            Map<String, Object> root = MiniJson.parseObject(json);
            Object vals = root.get("values");
            if (!(vals instanceof Map)) return "err.bad_file";
            for (Map.Entry<?, ?> en : ((Map<?, ?>) vals).entrySet()) {
                String id = String.valueOf(en.getKey());
                if (!BY_ID.containsKey(id)) continue;
                Object v = en.getValue();
                if (v == null) continue;
                String text = (v instanceof Double && ((Double) v) == Math.rint((Double) v) && BY_ID.get(id).type != Type.DOUBLE)
                        ? String.valueOf(((Double) v).longValue()) : String.valueOf(v);
                set(id, text);
            }
            return null;
        } catch (RuntimeException ex) {
            return "err.bad_file";
        }
    }

    public static void load(Path file) {
        try {
            if (Files.exists(file)) fromJson(Files.readString(file, StandardCharsets.UTF_8));
            else dirty = true;
        } catch (IOException | RuntimeException ex) {
            dirty = true; // keep defaults, rewrite a valid file
        }
    }

    /** Writes through a temporary file and moves it into place so a crash never leaves a half-written config. */
    public static synchronized boolean save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, toJson(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
}
