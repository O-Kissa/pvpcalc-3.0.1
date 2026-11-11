package com.pvpcalc.util;

import com.pvpcalc.core.Num;

/** Counters for the mixin hooks (used by /pvpcalc check) and the server TPS estimate. */
public final class Hooks {
    public static volatile int attack, status, explosion, timeUpdate;
    private static long lastTime;
    private static double tps = 20.0;

    private Hooks() {}

    /** Called when the server's time-update packet arrives (it is sent every 20 server ticks). */
    public static void onTimeUpdate() {
        timeUpdate++;
        long now = System.currentTimeMillis();
        if (lastTime != 0) {
            double sec = (now - lastTime) / 1000.0;
            if (sec > 0.2 && sec < 30.0) {
                double inst = Num.clamp(20.0 / sec, 0.0, 20.0);
                tps = tps * 0.7 + inst * 0.3;
            }
        }
        lastTime = now;
    }

    public static double tps() {
        return Num.clamp(tps, 1.0, 20.0);
    }

    public static void reset() {
        lastTime = 0;
        tps = 20.0;
    }
}
