package com.pvpcalc.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small dependency-free JSON reader/writer (objects, arrays, strings, numbers, booleans, null). */
public final class MiniJson {
    private final String s;
    private int i;

    private MiniJson(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        MiniJson p = new MiniJson(text == null ? "" : text);
        p.ws();
        Object v = p.val();
        p.ws();
        if (p.i != p.s.length()) throw new IllegalArgumentException("Trailing data at " + p.i);
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object o = parse(text);
        if (!(o instanceof Map)) throw new IllegalArgumentException("Not a JSON object");
        return (Map<String, Object>) o;
    }

    private IllegalArgumentException err(String m) {
        return new IllegalArgumentException(m + " at " + i);
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object val() {
        if (i >= s.length()) throw err("Unexpected end");
        char c = s.charAt(i);
        if (c == '{') return obj();
        if (c == '[') return arr();
        if (c == '"') return str();
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        return num();
    }

    private Map<String, Object> obj() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            if (i >= s.length() || s.charAt(i) != '"') throw err("Expected key");
            String k = str();
            ws();
            if (i >= s.length() || s.charAt(i) != ':') throw err("Expected ':'");
            i++;
            ws();
            m.put(k, val());
            ws();
            if (i >= s.length()) throw err("Unterminated object");
            char c = s.charAt(i++);
            if (c == '}') return m;
            if (c != ',') throw err("Expected ',' or '}'");
        }
    }

    private List<Object> arr() {
        List<Object> l = new ArrayList<>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
        while (true) {
            ws();
            l.add(val());
            ws();
            if (i >= s.length()) throw err("Unterminated array");
            char c = s.charAt(i++);
            if (c == ']') return l;
            if (c != ',') throw err("Expected ',' or ']'");
        }
    }

    private String str() {
        StringBuilder sb = new StringBuilder();
        i++;
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                if (i >= s.length()) break;
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw err("Bad unicode escape");
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
        throw err("Unterminated string");
    }

    private Double num() {
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (st == i) throw err("Unexpected character '" + s.charAt(i) + "'");
        return Double.valueOf(s.substring(st, i));
    }

    // ---------------- writer ----------------

    public static String write(Object o, boolean pretty) {
        StringBuilder sb = new StringBuilder();
        w(sb, o, pretty, 0);
        return sb.toString();
    }

    private static void nl(StringBuilder sb, boolean pretty, int d) {
        if (!pretty) return;
        sb.append('\n');
        for (int k = 0; k < d; k++) sb.append("  ");
    }

    private static void w(StringBuilder sb, Object o, boolean pretty, int d) {
        if (o == null) { sb.append("null"); return; }
        if (o instanceof Boolean) { sb.append(o); return; }
        if (o instanceof Number) {
            double v = Num.clean(((Number) o).doubleValue(), 0.0);
            if (v == Math.rint(v) && Math.abs(v) < 1e15) sb.append((long) v); else sb.append(v);
            return;
        }
        if (o instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) o;
            if (m.isEmpty()) { sb.append("{}"); return; }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                nl(sb, pretty, d + 1);
                q(sb, String.valueOf(e.getKey()));
                sb.append(pretty ? ": " : ":");
                w(sb, e.getValue(), pretty, d + 1);
            }
            nl(sb, pretty, d);
            sb.append('}');
            return;
        }
        if (o instanceof List) {
            List<?> l = (List<?>) o;
            if (l.isEmpty()) { sb.append("[]"); return; }
            sb.append('[');
            boolean first = true;
            for (Object x : l) {
                if (!first) sb.append(',');
                first = false;
                nl(sb, pretty, d + 1);
                w(sb, x, pretty, d + 1);
            }
            nl(sb, pretty, d);
            sb.append(']');
            return;
        }
        q(sb, String.valueOf(o));
    }

    private static void q(StringBuilder sb, String s) {
        sb.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
            }
        }
        sb.append('"');
    }
}
