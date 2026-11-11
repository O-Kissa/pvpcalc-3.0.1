package com.pvpcalc.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Mod language (English default, Finnish optional). Texts come from assets/pvpcalc/lang/*.json. */
public final class Lang {
    private static final Map<String, String> EN = new HashMap<>();
    private static final Map<String, String> FI = new HashMap<>();
    private static boolean loaded;

    private Lang() {}

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        read("/assets/pvpcalc/lang/en_us.json", EN);
        read("/assets/pvpcalc/lang/fi_fi.json", FI);
    }

    private static void read(String res, Map<String, String> into) {
        try (InputStream in = Lang.class.getResourceAsStream(res)) {
            if (in == null) return;
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (Map.Entry<String, Object> e : MiniJson.parseObject(json).entrySet()) {
                if (e.getValue() != null) into.put(e.getKey(), String.valueOf(e.getValue()));
            }
        } catch (IOException | RuntimeException ex) {
            // a broken language file must never crash the game; keys fall back to English / the key itself
        }
    }

    public static boolean fi() {
        return "fi".equalsIgnoreCase(Settings.str("language"));
    }

    public static boolean has(String key) {
        load();
        return EN.containsKey(key);
    }

    public static boolean hasFi(String key) {
        load();
        return FI.containsKey(key);
    }

    public static String t(String key, Object... args) {
        load();
        String v = fi() ? FI.get(key) : EN.get(key);
        if (v == null) v = EN.get(key);
        if (v == null) return key;
        for (int i = 0; i < args.length; i++) v = v.replace("{" + i + "}", String.valueOf(args[i]));
        return v;
    }

    public static Map<String, String> english() {
        load();
        return EN;
    }

    public static Map<String, String> finnish() {
        load();
        return FI;
    }
}
