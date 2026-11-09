package com.teslamaps.adm.leap.splits;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Ported from ADM's splits/SubSplitGrades.kt
public final class SubSplitGrades {

    private SubSplitGrades() {}

    public enum Clock { TICKS, REAL }

    public record Bands(Clock clock, long[] limits, long floor, boolean fromStart, Long lateAfter) {}

    private static Bands ticks(long floor, long... l) { return new Bands(Clock.TICKS, l, floor, false, null); }
    private static Bands real(long floor, long... l) { return new Bands(Clock.REAL, l, floor, false, null); }
    private static Bands filler() { return new Bands(Clock.TICKS, null, 0, false, null); }

    private static Bands stepped(Clock clock, long floor, long onTime, long step, boolean fromStart) {
        return new Bands(clock, new long[]{onTime, onTime, onTime + step, onTime + 2 * step, onTime + 3 * step}, floor, fromStart, null);
    }

    public static final Map<String, Bands> BANDS = new HashMap<>();
    public static final Map<String, Bands> BANDS_M7 = new HashMap<>();

    static {
        BANDS.put("watcher.dialogue", ticks(229, 255, 294, 307, 324, 355));
        BANDS.put("watcher.wait", ticks(45, 63, 78, 114, 155, 200));
        BANDS.put("watcher.camp", ticks(485, 520, 560, 600, 640, 670));
        BANDS.put("watcher.clear", ticks(0, 2, 6, 10, 16, 25));
        BANDS.put("maxor.crystals", stepped(Clock.TICKS, 107, 110, 10, false));
        BANDS.put("maxor.lure", stepped(Clock.TICKS, 124, 128, 10, true));
        BANDS.put("maxor.cooldown", stepped(Clock.TICKS, 83, 85, 10, false));
        BANDS.put("maxor.kill", ticks(0, 1, 3, 5, 7, 17));
        BANDS.put("maxor.animation", filler());
        BANDS.put("storm.opening", filler());
        BANDS.put("storm.crush1", stepped(Clock.TICKS, 10, 13, 20, false));
        BANDS.put("storm.pin", ticks(0, 1, 2, 3, 6, 14));
        BANDS.put("storm.flight", ticks(78, 86, 90, 93, 99, 168));
        BANDS.put("storm.crush2", stepped(Clock.TICKS, 0, 20, 20, false));
        BANDS.put("storm.kill", ticks(1, 2, 3, 5, 18, 24));
        BANDS.put("storm.animation", filler());
        BANDS.put("terms.s1", real(8100, 8700, 9500, 11000, 13200, 17200));
        BANDS.put("terms.s2", real(6700, 7700, 9400, 12600, 16100, 21800));
        BANDS.put("terms.s3", real(6650, 7800, 11400, 13000, 16700, 18800));
        BANDS.put("terms.s4", real(4450, 5000, 7600, 8800, 12600, 25100));
        BANDS.put("goldor.leaps", ticks(7, 9, 15, 21, 29, 43));
        BANDS.put("goldor.kill", ticks(77, 85, 96, 108, 124, 134));
        BANDS.put("necron.intro", filler());
        BANDS.put("necron.trip1", ticks(11, 12, 14, 16, 18, 26));
        BANDS.put("necron.lock1", new Bands(Clock.TICKS, null, 0, false, 275L));
        BANDS.put("necron.kill", stepped(Clock.TICKS, 140, 155, 20, false));
        BANDS.put("split.open", ticks(100, 160, 280, 380, 500, 640));
        BANDS.put("split.blood", ticks(907, 920, 1000, 1100, 1200, 1400));
        BANDS.put("split.portal", ticks(40, 76, 88, 100, 120, 160));
        BANDS.put("split.enter", ticks(1100, 1240, 1400, 1560, 1700, 1900));
        BANDS.put("split.maxor", ticks(263, 272, 280, 300, 340, 400));
        BANDS.put("split.storm", ticks(820, 824, 840, 870, 960, 1080));
        BANDS.put("split.terms", real(25000, 28000, 31000, 37000, 41000, 48000));
        BANDS.put("split.goldor", ticks(96, 100, 126, 140, 160, 180));
        BANDS.put("split.necron", ticks(409, 425, 436, 447, 490, 540));

        BANDS_M7.put("necron.kill", new Bands(Clock.TICKS, new long[]{106, 128, 150, 172, 192}, 99, false, null));
        BANDS_M7.put("necron.relics", ticks(89, 101, 111, 121, 150, 212));
        BANDS_M7.put("split.necron", ticks(457, 482, 510, 533, 570, 633));
        BANDS_M7.put("split.dragons", ticks(523, 533, 547, 566, 591, 623));
    }

    public static Bands bands(String id, String floor) {
        if ("F7".equals(floor)) return BANDS.get(id);
        if ("M7".equals(floor)) return BANDS_M7.get(id);
        return null;
    }

    private static Bands anyBands(String id, String floor) {
        Bands b = bands(id, floor);
        if (b == null) b = BANDS.get(id);
        if (b == null) b = BANDS_M7.get(id);
        return b;
    }

    public static final Map<String, String> MAIN_SPLITS = Map.of(
            "Blood Open", "split.open", "Blood Clear", "split.blood", "Portal Entry", "split.portal", "Boss Entry", "split.enter",
            "Maxor", "split.maxor", "Storm", "split.storm", "Terminals", "split.terms", "Goldor", "split.goldor",
            "Necron", "split.necron", "Cleared", "split.dragons");

    public static long value(String id, long realMs, long ticks) { return value(id, realMs, ticks, ticks); }

    public static long value(String id, long realMs, long ticks, long fromStartTicks) {
        Bands b = BANDS.get(id);
        if (b == null) return ticks;
        if (b.clock == Clock.REAL) return realMs;
        if (b.fromStart || b.lateAfter != null) return fromStartTicks;
        return ticks;
    }

    public static Clock clock(String id) {
        Bands b = BANDS.get(id);
        return b == null ? Clock.TICKS : b.clock;
    }

    public static boolean canBeBest(String id, long value, String floor) {
        Bands b = anyBands(id, floor);
        return b != null && b.limits != null && value >= b.floor;
    }

    private static String lastText;
    private static Map<String, Long> lastBests;

    public static synchronized Map<String, Long> parseBests(String text) {
        if (text == null) text = "";
        if (lastText != null && lastText.equals(text)) return new HashMap<>(lastBests);
        Map<String, Long> m = new HashMap<>();
        for (String e : text.split(",")) {
            String[] kv = e.split("=");
            if (kv.length != 2) continue;
            try { m.put(kv[0].trim(), Long.parseLong(kv[1].trim())); } catch (NumberFormatException ignored) { }
        }
        lastText = text;
        lastBests = new HashMap<>(m);
        return m;
    }

    public static String formatBests(Map<String, Long> bests) {
        List<String> keys = new ArrayList<>(bests.keySet());
        keys.sort(null);
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            if (sb.length() > 0) sb.append(',');
            sb.append(k).append('=').append(bests.get(k));
        }
        return sb.toString();
    }
}
