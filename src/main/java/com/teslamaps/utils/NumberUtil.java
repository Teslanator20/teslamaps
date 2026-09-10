package com.teslamaps.utils;

import java.util.Locale;

/**
 * Suffixed number parsing/formatting for SkyBlock text (1.4m, 12,345).
 * Ported from bax's utils/NumberUtils; formatting is pinned to Locale.ROOT
 * so a German JVM does not print "1,4m".
 */
public final class NumberUtil {
    private NumberUtil() {}

    /** "1.4m" / "12,345" / "3k" -> double. 0 on anything unparsable. */
    public static double parseSuffixed(String num) {
        if (num == null || num.isEmpty()) return 0;
        num = num.replace(",", "").trim();
        if (num.isEmpty()) return 0;
        double multiplier = 1;
        char last = num.charAt(num.length() - 1);
        switch (last) {
            case 'k', 'K' -> multiplier = 1_000d;
            case 'm', 'M' -> multiplier = 1_000_000d;
            case 'b', 'B' -> multiplier = 1_000_000_000d;
            default -> { }
        }
        if (multiplier != 1) num = num.substring(0, num.length() - 1);
        try {
            return Double.parseDouble(num) * multiplier;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String formatSuffixed(double number) {
        if (number >= 1_000_000_000d) return String.format(Locale.ROOT, "%.1fb", number / 1_000_000_000d);
        if (number >= 1_000_000d) return String.format(Locale.ROOT, "%.1fm", number / 1_000_000d);
        if (number >= 1_000d) return String.format(Locale.ROOT, "%.1fk", number / 1_000d);
        return String.format(Locale.ROOT, "%.0f", number);
    }

    public static int parseCommaInt(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            return Integer.parseInt(s.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
