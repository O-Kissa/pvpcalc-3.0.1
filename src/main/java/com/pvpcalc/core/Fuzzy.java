package com.pvpcalc.core;

import java.util.Collection;
import java.util.Locale;

/** "Did you mean ...?" helper. */
public final class Fuzzy {
    private Fuzzy() {}

    public static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    /** Closest option within a tolerance that scales with word length, or null. */
    public static String closest(String input, Collection<String> options) {
        if (input == null || input.isEmpty()) return null;
        String in = input.toLowerCase(Locale.ROOT);
        String best = null;
        int bestD = Integer.MAX_VALUE;
        for (String o : options) {
            String ol = o.toLowerCase(Locale.ROOT);
            int d = distance(in, ol);
            if (ol.startsWith(in) || in.startsWith(ol)) d = Math.min(d, 1);
            if (d < bestD) { bestD = d; best = o; }
        }
        int tol = Math.max(2, in.length() / 3);
        return bestD <= tol ? best : null;
    }
}
