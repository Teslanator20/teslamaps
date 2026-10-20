package com.teslamaps.adm.leap.splits;

import java.util.List;

// Ported from ADM's storm/StormCrush.kt
public final class StormCrush {

    private StormCrush() {}

    public static final int CHECK_PERIOD = 20;
    public static final double HEAD = 3.0;
    public static final int ARMED_TICKS = 60;
    public static final int ZONE = 6;
    public static final int PILLAR = 7;
    public static final int TOP = 204;
    public static final int FLOOR = 169;

    public record Pillar(String name, String colour, int minX, int minZ) {
        public int columnX() { return minX + 3; }
        public int columnZ() { return minZ + 3; }
    }

    public static final List<Pillar> PILLARS = List.of(
            new Pillar("Purple", "§5", 97, 62),
            new Pillar("Yellow", "§e", 43, 62),
            new Pillar("Green", "§a", 43, 38),
            new Pillar("Red", "§c", 97, 38));

    public static double inset(Pillar p, double x, double z) {
        return Math.min(Math.min(x - p.minX, p.minX + ZONE - x), Math.min(z - p.minZ, p.minZ + ZONE - z));
    }

    public static double distance(Pillar p, double x, double z) {
        double dx = Math.max(Math.max(p.minX - x, 0.0), x - (p.minX + ZONE));
        double dz = Math.max(Math.max(p.minZ - z, 0.0), z - (p.minZ + ZONE));
        return Math.hypot(dx, dz);
    }

    public static Pillar nearest(double x, double z) {
        Pillar best = PILLARS.get(0);
        for (Pillar p : PILLARS) if (distance(p, x, z) < distance(best, x, z)) best = p;
        return best;
    }

    public static double headAbove(double y, int bottom) { return y + HEAD - bottom; }

    public static boolean armed(Integer lastStep, int now) {
        return lastStep != null && now - lastStep >= 0 && now - lastStep <= ARMED_TICKS;
    }

    public record Verdict(Pillar pillar, double inset, Double head) {
        public boolean inside() { return inset >= 0 && head != null && head >= 0; }
    }

    public static Verdict judge(Pillar p, double x, double y, double z, Integer bottom) {
        return new Verdict(p, inset(p, x, z), bottom == null ? null : headAbove(y, bottom));
    }

    public static boolean isCheck(int ticks) { return ticks > 0 && ticks % CHECK_PERIOD == 0; }
}
