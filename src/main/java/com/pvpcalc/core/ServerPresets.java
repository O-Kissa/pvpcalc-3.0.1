package com.pvpcalc.core;

import java.util.Locale;

/** Ready-made settings for well known servers (estimates; shown in chat when presets are enabled). */
public final class ServerPresets {
    public static final class Preset {
        public final String host, combat;
        public final double reach;
        public final boolean smp;
        Preset(String host, String combat, double reach, boolean smp) {
            this.host = host; this.combat = combat; this.reach = reach; this.smp = smp;
        }
    }

    private static final Preset[] LIST = {
            new Preset("hypixel.net", "1.8", 3.6, false),
            new Preset("minemen.club", "1.8", 3.6, false),
            new Preset("donutsmp.net", "1.9+", 3.5, true),
    };

    private ServerPresets() {}

    public static Preset match(String address) {
        if (address == null) return null;
        String a = address.toLowerCase(Locale.ROOT);
        int c = a.indexOf(':');
        if (c > 0) a = a.substring(0, c);
        for (Preset p : LIST) if (a.equals(p.host) || a.endsWith("." + p.host)) return p;
        return null;
    }
}
