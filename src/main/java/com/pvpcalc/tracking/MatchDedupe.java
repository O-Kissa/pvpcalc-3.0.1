package com.pvpcalc.tracking;

import com.pvpcalc.util.Clock;

import java.util.HashMap;
import java.util.Map;

/** Prevents the same event from being logged twice (window = ticks). */
public final class MatchDedupe {
    private static final Map<String, Long> RECENT = new HashMap<>();

    private MatchDedupe() {}

    /** True if the key was already seen within windowTicks (1 = same tick only). */
    public static boolean seen(String key, int windowTicks) {
        long now = Clock.tick();
        if (RECENT.size() > 256) RECENT.clear();
        Long last = RECENT.get(key);
        if (last != null && now - last < windowTicks) return true;
        RECENT.put(key, now);
        return false;
    }

    public static void clear() {
        RECENT.clear();
    }
}
