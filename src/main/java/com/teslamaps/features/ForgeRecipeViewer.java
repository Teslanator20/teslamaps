package com.teslamaps.features;

public final class ForgeRecipeViewer {

    private ForgeRecipeViewer() {}

    static String time(long s) {
        if (s <= 0) return "?";
        long d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60, sec = s % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (sec > 0 && d == 0) sb.append(sec).append("s");
        return sb.toString().trim();
    }
}
