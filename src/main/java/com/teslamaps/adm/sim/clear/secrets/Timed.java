// Ported from ADM's p3sim/clear/secrets/SecretFinales.kt
package com.teslamaps.adm.sim.clear.secrets;

public class Timed<T> {
    public final int due;
    public final T what;
    public final String owner;

    public Timed(int due, T what, String owner) { this.due = due; this.what = what; this.owner = owner; }

    public Timed(int due, T what) { this(due, what, null); }
}
