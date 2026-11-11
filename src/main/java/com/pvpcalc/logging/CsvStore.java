package com.pvpcalc.logging;

import com.pvpcalc.PvPCalcClient;
import com.pvpcalc.core.CsvUtil;
import com.pvpcalc.core.Num;
import com.pvpcalc.core.Settings;
import com.pvpcalc.tracking.MatchManager;
import com.pvpcalc.util.Dirs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All CSV output. Event rows are buffered and written every second (and on shutdown); tables are rewritten
 * atomically and only when their content changed. No timestamps anywhere.
 */
public final class CsvStore {
    public static final String SESSION = "session_data.csv";
    public static final String[] SESSION_HEAD = {"Event", "Kit Context", "Player", "Details", "Value"};
    public static final String MATCHES = "matches.csv";
    public static final String[] MATCHES_HEAD = {"Match", "Kit", "Result", "Opponents", "Damage Dealt", "Damage Taken", "Swings", "Hits",
            "Accuracy %", "Crits", "Crit %", "Max Hit", "Combos", "Max Combo", "My Gapples", "My Totems", "Avg Reaction s", "Style",
            "Spacing %", "Death Info", "Hack Chance %"};
    public static final String PLAYERS = "match_players.csv";
    public static final String[] PLAYERS_HEAD = {"Match", "Kit", "Player", "Damage Dealt", "Damage Taken", "Hits", "Crits", "Crit %",
            "Their Hits", "Combos", "Max Combo", "Their Max Combo", "Totem Pops", "Gapples", "Ench Gapples", "Pearls", "XP Bottles",
            "Potions", "Avg Distance", "Hack Chance %", "Died"};
    public static final String LIVE = "live_match.csv";
    public static final String[] LIVE_HEAD = {"Match", "Kit", "Player", "Damage Dealt", "Damage Taken", "Hits", "Crit %", "Totem Pops",
            "Gapples", "Pearls", "XP Bottles", "Max Combo", "Hack Chance %"};

    private static final List<String> PENDING = new ArrayList<>();
    private static final Map<String, Integer> LAST_HASH = new HashMap<>();
    private static final Map<String, String> DELIM_CACHE = new HashMap<>();

    private CsvStore() {}

    public static Path file(String name) {
        return Dirs.file(name);
    }

    private static String configuredDelim() {
        return Settings.is("csv.delimiter", "semicolon") ? ";" : ",";
    }

    /** Delimiter of an existing file (read from its header) so one file never mixes delimiters. */
    private static synchronized String delimOf(String name) {
        String d = DELIM_CACHE.get(name);
        if (d != null) return d;
        d = configuredDelim();
        try {
            Path f = file(name);
            if (Files.exists(f) && Files.size(f) > 0) {
                String first = Files.readAllLines(f, StandardCharsets.UTF_8).get(0);
                d = first.contains(";") ? ";" : ",";
            }
        } catch (IOException | RuntimeException ignored) {
            // use the configured one
        }
        DELIM_CACHE.put(name, d);
        return d;
    }

    /** Numbers use a decimal comma when the delimiter is a semicolon (Finnish Excel). */
    public static String num(String table, double v) {
        String s = Num.fmt(v, 2);
        return delimOf(table).equals(";") ? s.replace('.', ',') : s;
    }

    private static byte[] headerBytes(String name, String[] head) {
        String d = configuredDelim();
        String line = CsvUtil.row(d, false, head) + "\r\n";
        byte[] body = line.getBytes(StandardCharsets.UTF_8);
        if (!Settings.bool("csv.bom")) return body;
        byte[] out = new byte[3 + body.length];
        out[0] = (byte) 0xEF; out[1] = (byte) 0xBB; out[2] = (byte) 0xBF;
        System.arraycopy(body, 0, out, 3, body.length);
        return out;
    }

    private static void create(String name, String[] head) throws IOException {
        Files.createDirectories(Dirs.dir());
        DELIM_CACHE.remove(name);
        Files.write(file(name), headerBytes(name, head), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        DELIM_CACHE.put(name, configuredDelim());
    }

    public static synchronized void ensure(String name, String[] head) {
        try {
            Path f = file(name);
            if (!Files.exists(f) || Files.size(f) == 0) create(name, head);
        } catch (IOException e) {
            PvPCalcClient.LOGGER.error("Could not prepare {}", name, e);
        }
    }

    public static void init() {
        ensure(SESSION, SESSION_HEAD);
        ensure(MATCHES, MATCHES_HEAD);
        ensure(PLAYERS, PLAYERS_HEAD);
        ensure(LIVE, LIVE_HEAD);
    }

    // ---------------- event log ----------------

    public static void event(String event, String player, String details, double value) {
        eventRaw(event, MatchManager.kitContext(), player, details, Double.isNaN(value) ? "" : num(SESSION, value));
    }

    public static synchronized void eventRaw(String event, String kit, String player, String details, String value) {
        if (!Settings.bool("enabled")) return;
        String d = delimOf(SESSION);
        PENDING.add(CsvUtil.row(d, Settings.bool("csv.formulaProtect"), event, kit, player, details, value));
        if (PENDING.size() > 500) flush();
    }

    public static synchronized void flush() {
        if (PENDING.isEmpty()) return;
        try {
            ensure(SESSION, SESSION_HEAD);
            archiveIfLarge();
            StringBuilder sb = new StringBuilder();
            for (String l : PENDING) sb.append(l).append("\r\n");
            try (FileChannel ch = FileChannel.open(file(SESSION), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                ch.write(ByteBuffer.wrap(sb.toString().getBytes(StandardCharsets.UTF_8)));
                ch.force(true);
            }
            PENDING.clear();
        } catch (IOException e) {
            PvPCalcClient.LOGGER.error("Could not write CSV rows (kept in memory)", e);
        }
    }

    private static void archiveIfLarge() throws IOException {
        long max = Settings.i("csv.maxMb") * 1024L * 1024L;
        Path f = file(SESSION);
        if (Files.exists(f) && Files.size(f) > max) {
            int n = 1;
            while (Files.exists(file("session_data_" + n + ".csv"))) n++;
            Files.move(f, file("session_data_" + n + ".csv"));
            DELIM_CACHE.remove(SESSION);
            create(SESSION, SESSION_HEAD);
        }
    }

    // ---------------- tables ----------------

    public static synchronized void appendRow(String table, String[] head, String... cells) {
        try {
            ensure(table, head);
            String line = CsvUtil.row(delimOf(table), Settings.bool("csv.formulaProtect"), cells) + "\r\n";
            try (FileChannel ch = FileChannel.open(file(table), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                ch.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
                ch.force(true);
            }
        } catch (IOException e) {
            PvPCalcClient.LOGGER.error("Could not append to {}", table, e);
        }
    }

    /** Rewrites a whole table atomically, but only if the content differs from what was written last time. */
    public static synchronized void writeTable(String name, String[] head, List<String[]> rows) {
        try {
            String d = configuredDelim();
            StringBuilder sb = new StringBuilder();
            sb.append(CsvUtil.row(d, false, head)).append("\r\n");
            for (String[] r : rows) sb.append(CsvUtil.row(d, Settings.bool("csv.formulaProtect"), r)).append("\r\n");
            String text = sb.toString();
            int h = text.hashCode();
            Integer old = LAST_HASH.get(name);
            if (old != null && old == h && Files.exists(file(name))) return; // nothing changed: no needless write
            Files.createDirectories(Dirs.dir());
            byte[] body = text.getBytes(StandardCharsets.UTF_8);
            byte[] out = body;
            if (Settings.bool("csv.bom")) {
                out = new byte[3 + body.length];
                out[0] = (byte) 0xEF; out[1] = (byte) 0xBB; out[2] = (byte) 0xBF;
                System.arraycopy(body, 0, out, 3, body.length);
            }
            Path tmp = file(name + ".tmp");
            Files.write(tmp, out);
            try {
                Files.move(tmp, file(name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file(name), StandardCopyOption.REPLACE_EXISTING);
            }
            LAST_HASH.put(name, h);
            DELIM_CACHE.put(name, d);
        } catch (IOException e) {
            PvPCalcClient.LOGGER.error("Could not write table {}", name, e);
        }
    }

    /** /pvpcalc reset: clears the session tables only. Long-term stats are untouched. */
    public static synchronized boolean reset() {
        try {
            PENDING.clear();
            LAST_HASH.clear();
            create(SESSION, SESSION_HEAD);
            create(MATCHES, MATCHES_HEAD);
            create(PLAYERS, PLAYERS_HEAD);
            create(LIVE, LIVE_HEAD);
            return true;
        } catch (IOException e) {
            PvPCalcClient.LOGGER.error("Could not reset CSV files", e);
            return false;
        }
    }

    public static synchronized boolean backup() {
        try {
            flush();
            int n = 1;
            while (Files.exists(file("backup_" + n + "_" + SESSION))) n++;
            Files.copy(file(SESSION), file("backup_" + n + "_" + SESSION));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static synchronized boolean writable() {
        try {
            Files.createDirectories(Dirs.dir());
            Path t = file("write_test.tmp");
            Files.writeString(t, "ok");
            Files.delete(t);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
