package com.teslamaps.adm.leap.splits;

import java.util.Locale;

/** One timed section; stop is null while it runs. */
public record Split(String label, Stamp start, Stamp stop) {

    public static String seconds(long ms) {
        return String.format(Locale.ROOT, "%.2f", ms / 1000.0) + "s";
    }

    public static String minutes(long ms) {
        long tenths = Math.round(ms / 100.0);
        return (tenths / 600) + "m " + String.format(Locale.ROOT, "%.1f", tenths % 600 / 10.0) + "s";
    }
}
