// Ported from ADM's p3sim/run/FailureLog.kt
package com.teslamaps.adm.sim.run;

import java.util.HashMap;
import java.util.function.Supplier;

/** First failure of a kind in full, then held back and counted. Server thread only. */
public class FailureLog {
    private static final class Seen {
        long lastReported;
        int held;

        Seen(long lastReported, int held) { this.lastReported = lastReported; this.held = held; }
    }

    private final long everyMillis;
    private final Supplier<Long> clock;
    private final HashMap<String, Seen> seen = new HashMap<>();

    public FailureLog(long everyMillis, Supplier<Long> clock) {
        this.everyMillis = everyMillis;
        this.clock = clock;
    }

    public FailureLog(long everyMillis) { this(everyMillis, System::currentTimeMillis); }

    public FailureLog() { this(30_000L); }

    /** null to stay quiet, else how many were held back since the last report. */
    public Integer admit(String key) {
        long now = clock.get();
        Seen s = seen.get(key);
        if (s == null) { seen.put(key, new Seen(now, 0)); return 0; }
        if (now - s.lastReported < everyMillis) { s.held++; return null; }
        int held = s.held;
        s.lastReported = now; s.held = 0;
        return held;
    }

    public String suffix(int held) { return held > 0 ? " (" + held + " more since the last report)" : ""; }
}
