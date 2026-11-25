package com.hotel.util;

import java.util.*;
import java.util.regex.*;

/** Minimal JSON helpers so the project needs no third-party libraries. */
public final class Json {
    private Json() {}
    public record Raw(String text) {}
    public static Raw raw(String json) { return new Raw(json); }

    public static String str(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c < 0x20 ? String.format("\\u%04x", (int) c) : String.valueOf(c));
            }
        }
        return sb.append('"').toString();
    }

    private static String val(Object v) {
        if (v == null) return "null";
        if (v instanceof Raw r) return r.text();
        if (v instanceof Double d) return String.format(Locale.ROOT, "%.2f", d);
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        return str(v.toString());
    }

    /** obj("a", 1, "b", "x") -> {"a":1,"b":"x"} */
    public static String obj(Object... kv) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append(str((String) kv[i])).append(':').append(val(kv[i + 1]));
        }
        return sb.append('}').toString();
    }

    public static String array(Collection<String> jsonItems) { return "[" + String.join(",", jsonItems) + "]"; }

    private static final Pattern FIELD = Pattern.compile("\"(\\w+)\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|([^,}\\s]+))");

    /** Parses a flat JSON object of string/number values. */
    public static Map<String, String> parse(String body) {
        Map<String, String> m = new HashMap<>();
        Matcher mt = FIELD.matcher(body == null ? "" : body);
        while (mt.find()) {
            String v = mt.group(2) != null ? mt.group(2).replace("\\\"", "\"").replace("\\\\", "\\") : mt.group(3);
            m.put(mt.group(1), v);
        }
        return m;
    }
}
