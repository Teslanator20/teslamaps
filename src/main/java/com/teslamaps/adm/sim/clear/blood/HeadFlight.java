// Ported from ADM's p3sim/clear/blood/HeadFlight.kt
package com.teslamaps.adm.sim.clear.blood;

/** One thrown wall head's flight (pure): a straight line at even speed, spinning, gone on arrival. */
public class HeadFlight {
    public final double fromX, fromY, fromZ;
    public final double toX, toY, toZ;
    /** The stand's yaw when it left. */
    public final float startYaw;
    /** Ticks the whole flight takes (at least 1). */
    public final int ticks;

    public HeadFlight(double fromX, double fromY, double fromZ, double toX, double toY, double toZ, int ticks, float startYaw) {
        this.fromX = fromX;
        this.fromY = fromY;
        this.fromZ = fromZ;
        this.toX = toX;
        this.toY = toY;
        this.toZ = toZ;
        this.startYaw = startYaw;
        this.ticks = Math.max(ticks, 1);
    }

    public HeadFlight(double fromX, double fromY, double fromZ, double toX, double toY, double toZ, int ticks) {
        this(fromX, fromY, fromZ, toX, toY, toZ, ticks, 0f);
    }

    /** Blocks per tick. */
    public double speed() {
        return Math.sqrt((toX - fromX) * (toX - fromX) + (toY - fromY) * (toY - fromY) + (toZ - fromZ) * (toZ - fromZ)) / ticks;
    }

    public double x(int tick) { return lerp(fromX, toX, tick); }

    public double y(int tick) { return lerp(fromY, toY, tick); }

    public double z(int tick) { return lerp(fromZ, toZ, tick); }

    /** Start yaw plus SPIN_PER_TICK a tick, wrapped to 0..360. */
    public float yaw(int tick) { return (float) Math.floorMod((int) (startYaw + SPIN_PER_TICK * Math.clamp(tick, 0, ticks)), 360); }

    public boolean arrived(int tick) { return tick >= ticks; }

    /** Full position sync every SYNC_EVERY ticks and on the last one. */
    public boolean syncDue(int tick) { return tick % SYNC_EVERY == 0 || tick == ticks; }

    private double lerp(double a, double b, int tick) { return a + (b - a) * Math.clamp(tick, 0, ticks) / ticks; }

    public static final int SYNC_EVERY = 3;

    /** UNMEASURED spin, degrees a tick. */
    public static final float SPIN_PER_TICK = 4f;
}
