package com.pvpcalc.util;

import com.pvpcalc.PvPCalcClient;
import com.pvpcalc.core.Settings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Runs a module and keeps the game alive when it fails. Repeated failures switch the module off (safe mode). */
public final class Guard {
    private static final Map<String, Integer> ERRORS = new HashMap<>();
    private static final Set<String> OFF = new HashSet<>();

    private Guard() {}

    public static void run(String module, Runnable r) {
        if (OFF.contains(module)) return;
        try {
            r.run();
        } catch (Throwable t) {
            count(module, t);
        }
    }

    public static void count(String module, Throwable t) {
        int n = ERRORS.merge(module, 1, Integer::sum);
        PvPCalcClient.LOGGER.error("PvPCalc module '{}' failed ({} times)", module, n, t);
        if (Settings.bool("safeMode") && n >= Settings.i("autoDisableErrors") && OFF.add(module)) {
            try {
                Chat.say("msg.module_off", module, n);
            } catch (Throwable ignored) {
                // chat may not be available
            }
        }
    }

    public static boolean isOff(String module) { return OFF.contains(module); }
    public static Map<String, Integer> errors() { return ERRORS; }
    public static Set<String> disabled() { return OFF; }

    public static void reenableAll() {
        OFF.clear();
        ERRORS.clear();
    }
}
