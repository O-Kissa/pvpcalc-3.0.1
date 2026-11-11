package com.pvpcalc.tracking;

import com.pvpcalc.PvPCalcClient;
import com.pvpcalc.core.KitDefs;
import com.pvpcalc.core.MatchData;
import com.pvpcalc.core.MiniJson;
import com.pvpcalc.core.Num;
import com.pvpcalc.core.Persist;
import com.pvpcalc.core.PlayerData;
import com.pvpcalc.core.Settings;
import com.pvpcalc.logging.CsvStore;
import com.pvpcalc.util.Dirs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Long-term statistics (kit results, streaks, versus players, records, history). Survives /pvpcalc reset. */
public final class StatsStore {
    public static Persist p = new Persist();
    private static boolean dirty;

    private StatsStore() {}

    public static void load() {
        try {
            Path f = Dirs.file("stats.json");
            if (Files.exists(f)) p = Persist.fromJson(Files.readString(f, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            PvPCalcClient.LOGGER.error("Could not read stats.json, starting empty", e);
            p = new Persist();
        }
        loadKits();
        writeTables();
    }

    @SuppressWarnings("unchecked")
    private static void loadKits() {
        Path f = Dirs.file("kits.json");
        try {
            if (Files.exists(f)) {
                Map<String, Object> m = MiniJson.parseObject(Files.readString(f, StandardCharsets.UTF_8));
                for (Map.Entry<String, Object> e : m.entrySet()) {
                    if (!(e.getValue() instanceof List) || KitDefs.resolve(e.getKey()).isEmpty()) continue;
                    List<Object> l = (List<Object>) e.getValue();
                    if (l.size() < 5) continue;
                    int[] r = new int[5];
                    for (int i = 0; i < 5; i++) r[i] = ((Number) l.get(i)).intValue();
                    KitDefs.RES.put(KitDefs.resolve(e.getKey()).get(), r);
                }
            } else {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<String, int[]> e : KitDefs.RES.entrySet()) {
                    List<Object> l = new ArrayList<>();
                    for (int v : e.getValue()) l.add(v);
                    out.put(e.getKey(), l);
                }
                Files.createDirectories(Dirs.dir());
                Files.writeString(f, MiniJson.write(out, true), StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException e) {
            PvPCalcClient.LOGGER.warn("kits.json unreadable, using built-in kit resources", e);
        }
    }

    public static void markDirty() {
        dirty = true;
    }

    public static synchronized void save() {
        if (!dirty) return;
        try {
            Files.createDirectories(Dirs.dir());
            Path tmp = Dirs.file("stats.json.tmp");
            Files.writeString(tmp, p.toJson(), StandardCharsets.UTF_8);
            Files.move(tmp, Dirs.file("stats.json"), StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException e) {
            PvPCalcClient.LOGGER.error("Could not save stats.json", e);
        }
    }

    public static void resetAll() {
        p = new Persist();
        dirty = true;
        save();
        writeTables();
    }

    public static void recordMatch(MatchData m) {
        PlayerData prim = m.primary();
        p.recordResult(m.kit, m.result, prim == null ? null : prim.uuid, prim == null ? null : prim.name);
        double allMaxHit = m.maxHit;
        int allMaxCombo = m.maxCombo;
        for (PlayerData pd : m.players.values()) {
            p.updateRecord(m.kit, pd.uuid, pd.name, pd.maxHit, pd.maxCombo);
            if (pd.hackPct >= Settings.i("hack.alertThreshold")) p.flagMatch(pd.uuid, pd.name);
        }
        p.updateRecord(m.kit, "*", "", allMaxHit, allMaxCombo);
        Persist.Hist h = new Persist.Hist();
        h.kit = m.kit; h.result = m.result; h.dealt = m.dealt; h.taken = m.taken; h.acc = m.accuracy(); h.crit = m.critPct();
        p.addHistory(h);
        dirty = true;
        save();
        writeTables();
    }

    private static String n(double v) {
        return Settings.is("csv.delimiter", "semicolon") ? Num.fmt(v, 1).replace('.', ',') : Num.fmt(v, 1);
    }

    public static void writeTables() {
        List<String[]> kits = new ArrayList<>();
        for (Map.Entry<String, Persist.KitRec> e : p.kits.entrySet()) {
            Persist.KitRec r = e.getValue();
            kits.add(new String[]{e.getKey(), String.valueOf(r.wins), String.valueOf(r.losses),
                    n(Num.div(r.wins * 100.0, r.wins + r.losses, 0.0)), String.valueOf(r.streak), String.valueOf(r.best)});
        }
        CsvStore.writeTable("kit_stats.csv", new String[]{"Kit", "Wins", "Losses", "Win %", "Win Streak", "Best Streak"}, kits);

        List<String[]> vs = new ArrayList<>();
        for (Map.Entry<String, Map<String, Persist.VsRec>> e : p.vs.entrySet()) {
            int w = 0, l = 0;
            for (Map.Entry<String, Persist.VsRec> k : e.getValue().entrySet()) {
                vs.add(new String[]{p.names.getOrDefault(e.getKey(), "?"), e.getKey(), k.getKey(),
                        String.valueOf(k.getValue().wins), String.valueOf(k.getValue().losses)});
                w += k.getValue().wins;
                l += k.getValue().losses;
            }
            vs.add(new String[]{p.names.getOrDefault(e.getKey(), "?"), e.getKey(), "ALL", String.valueOf(w), String.valueOf(l)});
        }
        CsvStore.writeTable("vs_players.csv", new String[]{"Player", "UUID", "Kit", "Wins", "Losses"}, vs);

        List<String[]> rec = new ArrayList<>();
        for (Map.Entry<String, Persist.Rec> e : p.records.entrySet()) {
            String key = e.getKey();
            int bar = key.lastIndexOf('|');
            String kit = bar < 0 ? key : key.substring(0, bar), uuid = bar < 0 ? "" : key.substring(bar + 1);
            String player = uuid.equals("*") ? "ALL PLAYERS" : p.names.getOrDefault(uuid, uuid);
            rec.add(new String[]{kit, player, n(e.getValue().maxHit), String.valueOf(e.getValue().maxCombo)});
        }
        CsvStore.writeTable("records.csv", new String[]{"Kit", "Player", "Max Hit", "Longest Combo"}, rec);

        List<String[]> sus = new ArrayList<>();
        for (Map.Entry<String, Integer> e : p.flagged.entrySet()) {
            sus.add(new String[]{p.names.getOrDefault(e.getKey(), "?"), e.getKey(), String.valueOf(e.getValue()),
                    e.getValue() >= Settings.i("hack.repeatMatches") ? "YES" : "no"});
        }
        CsvStore.writeTable("hack_suspects.csv", new String[]{"Player", "UUID", "Matches Flagged", "Repeat Suspect"}, sus);
    }

    public static boolean isRepeatSuspect(String uuid) {
        return p.flagged.getOrDefault(uuid, 0) >= Settings.i("hack.repeatMatches");
    }
}
