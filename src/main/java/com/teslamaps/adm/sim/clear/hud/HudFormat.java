// Ported from ADM's p3sim/clear/hud/HudText.kt
package com.teslamaps.adm.sim.clear.hud;

import com.teslamaps.adm.sim.util.Pair;

public final class HudFormat {
    private HudFormat() {}

    public static final char SECTION = '§';

    /** `13879` -> `13,879`. */
    public static String commas(long n) {
        String s = Long.toString(Math.abs(n));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) sb.append(',');
            sb.append(s.charAt(i));
        }
        return (n < 0 ? "-" : "") + sb;
    }

    public static String commas(int n) { return commas((long) n); }

    public static String commas(double d) { return commas(Math.round(d)); }

    /** `3k`, `526M`, `1B`; below 1,000 the plain number. */
    public static String abbreviate(long n) {
        if (n < 1_000L) return Long.toString(n);
        if (n < 1_000_000L) return (n / 1_000L) + "k";
        if (n < 1_000_000_000L) return (n / 1_000_000L) + "M";
        return (n / 1_000_000_000L) + "B";
    }

    /** `13s`, `04m 10s`, `1h 04m 10s`. */
    public static String elapsed(int ticks) {
        int total = Math.max(0, ticks) / 20;
        int h = total / 3600;
        int m = (total / 60) % 60;
        int s = total % 60;
        if (h > 0) return h + "h " + two(m) + "m " + two(s) + "s";
        if (m > 0) return two(m) + "m " + two(s) + "s";
        return two(s) + "s";
    }

    /** `m:ss` of the countdown, whole seconds rounded up. */
    public static String countdown(int ticksLeft) {
        int s = (Math.max(0, ticksLeft) + 19) / 20;
        return (s / 60) + ":" + two(s % 60);
    }

    public static String two(int n) { return n < 10 ? "0" + n : Integer.toString(n); }

    private static final int[] ROMAN_V = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] ROMAN_R = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    public static String roman(int n) {
        int left = Math.clamp(n, 1, 3999);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ROMAN_V.length; i++) while (left >= ROMAN_V[i]) { sb.append(ROMAN_R[i]); left -= ROMAN_V[i]; }
        return sb.toString();
    }

    /** Removes every `§x` pair. */
    public static String strip(String text) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == SECTION && i + 1 < text.length()) { i += 2; continue; }
            sb.append(text.charAt(i));
            i++;
        }
        return sb.toString();
    }

    /** The colour and style codes in force at the end of text. */
    public static String lastColors(String text) {
        String result = "";
        int i = text.length() - 2;
        while (i >= 0) {
            if (text.charAt(i) == SECTION) {
                char c = Character.toLowerCase(text.charAt(i + 1));
                if ("0123456789abcdef".indexOf(c) >= 0) return "" + SECTION + c + result;
                if (c == 'r') return result;
                if ("klmno".indexOf(c) >= 0) result = "" + SECTION + c + result;
                i -= 2;
            } else i--;
        }
        return result;
    }

    /** Hypixel's sidebar cut: prefix and suffix of at most limit characters each. */
    public static Pair<String, String> splitLine(String text, int limit) {
        if (text.length() <= limit) return Pair.of(text, "");
        int cut = limit;
        if (text.charAt(cut - 1) == SECTION) cut--;
        String prefix = text.substring(0, cut);
        String rest = text.substring(cut);
        String carry = lastColors(prefix);
        String suffix = carry.length() + rest.length() <= limit ? carry + rest : rest;
        if (suffix.length() > limit) {
            suffix = suffix.substring(0, limit);
            if (suffix.charAt(suffix.length() - 1) == SECTION) suffix = suffix.substring(0, suffix.length() - 1);
        }
        return Pair.of(prefix, suffix);
    }

    public static Pair<String, String> splitLine(String text) { return splitLine(text, 16); }

    /** The Hypixel class name for a stored class string, or null. */
    public static String className(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "archer" -> "Archer";
            case "berserk", "berserker" -> "Berserk";
            case "healer" -> "Healer";
            case "mage" -> "Mage";
            case "tank" -> "Tank";
            default -> null;
        };
    }

    /** `[H] [M] [B] [T] [A]`. */
    public static char classLetter(String raw) {
        String n = className(raw);
        if (n == null) return 'M';
        return switch (n) {
            case "Archer" -> 'A';
            case "Berserk" -> 'B';
            case "Healer" -> 'H';
            case "Mage" -> 'M';
            case "Tank" -> 'T';
            default -> 'M';
        };
    }

    public static String sidebarName(String name, int max) { return name.length() <= max ? name : name.substring(0, max); }

    public static String sidebarName(String name) { return sidebarName(name, HudConstants.SIDEBAR_NAME_MAX); }
}
