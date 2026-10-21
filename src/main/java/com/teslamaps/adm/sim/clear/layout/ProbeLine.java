package com.teslamaps.adm.sim.clear.layout;

// Ported from ADM's p3sim/clear/layout/ProbeReport.kt

import java.util.Objects;

public final class ProbeLine {
    public final boolean ok;
    public final String text;

    public ProbeLine(boolean ok, String text) {
        this.ok = ok;
        this.text = text;
    }

    @Override
    public boolean equals(Object o) { return o instanceof ProbeLine p && ok == p.ok && Objects.equals(text, p.text); }

    @Override
    public int hashCode() { return Objects.hash(ok, text); }

    @Override
    public String toString() { return "ProbeLine(ok=" + ok + ", text=" + text + ")"; }
}
