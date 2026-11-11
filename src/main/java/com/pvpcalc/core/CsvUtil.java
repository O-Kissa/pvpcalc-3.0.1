package com.pvpcalc.core;

/** Excel-friendly CSV cell escaping. */
public final class CsvUtil {
    public static final String MARKER = "--- NEW MATCH STARTED ---";

    private CsvUtil() {}

    private static boolean numeric(String s) {
        try {
            Double.parseDouble(s.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static String esc(String v, String delim, boolean protect) {
        if (v == null) return "";
        String s = v;
        if (protect && !s.isEmpty() && !s.equals(MARKER)) {
            char c = s.charAt(0);
            boolean risky = c == '=' || c == '+' || c == '@' || c == '\t' || c == '\r'
                    || (c == '-' && !numeric(s));
            if (risky) s = "'" + s;
        }
        boolean quote = s.contains(delim) || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (s.indexOf('"') >= 0) s = s.replace("\"", "\"\"");
        return quote ? "\"" + s + "\"" : s;
    }

    public static String row(String delim, boolean protect, String... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(delim);
            sb.append(esc(cells[i], delim, protect));
        }
        return sb.toString();
    }
}
