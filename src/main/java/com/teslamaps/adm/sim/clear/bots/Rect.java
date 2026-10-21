// Ported from ADM's p3sim/clear/bots/RoomWalk.kt
package com.teslamaps.adm.sim.clear.bots;

/** An axis-aligned piece of floor, world coordinates. */
public class Rect {
    public final double x0;
    public final double z0;
    public final double x1;
    public final double z1;

    public Rect(double x0, double z0, double x1, double z1) {
        this.x0 = x0;
        this.z0 = z0;
        this.x1 = x1;
        this.z1 = z1;
    }

    public double area() { return (x1 - x0) * (z1 - z0); }
}
