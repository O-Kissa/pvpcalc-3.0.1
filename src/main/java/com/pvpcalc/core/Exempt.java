package com.pvpcalc.core;

import java.util.Locale;

/** Situations in which movement checks are skipped (known false positives). */
public final class Exempt {
    public static final int ELYTRA = 1, WIND = 2, FIREWORK = 4, PEARL = 8, EXPLOSION = 16, VEHICLE = 32,
            WATER = 64, LAVA = 128, CLIMB = 256, LEVITATION = 512, SLOWFALL = 1024, TELEPORT = 2048,
            RESPAWN = 4096, RIPTIDE = 8192, KNOCKBACK = 16384, LUNGE = 32768, BLOCK = 65536,
            GAMEMODE = 131072, LAG = 262144, SOFT = 524288;
    public static final String[] NAMES = {"elytra", "wind_charge", "firework", "pearl", "explosion", "vehicle",
            "water", "lava", "climb", "levitation", "slow_fall", "teleport", "respawn", "riptide", "knockback",
            "lunge", "block", "gamemode", "lag", "soft"};

    private Exempt() {}

    public static int bit(String name) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return 1 << i;
        return 0;
    }

    /** "all" or a comma separated list of reason names -> bitmask of reasons that are enabled. */
    public static int mask(String list) {
        if (list == null || list.trim().isEmpty() || list.trim().equalsIgnoreCase("all")) return (1 << NAMES.length) - 1;
        int m = 0;
        for (String s : list.split(",")) m |= bit(s.trim().toLowerCase(Locale.ROOT));
        return m;
    }

    public static String describe(int m) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < NAMES.length; i++) if ((m & (1 << i)) != 0) {
            if (sb.length() > 0) sb.append(',');
            sb.append(NAMES[i]);
        }
        return sb.toString();
    }
}
