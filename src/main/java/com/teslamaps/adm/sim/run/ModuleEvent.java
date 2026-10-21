// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Map;
import java.util.Objects;

public final class ModuleEvent implements RunEvent {
    public final int t;
    public final String source;
    public final String name;
    public final Map<String, Object> data;

    public ModuleEvent(int t, String source, String name, Map<String, Object> data) {
        this.t = t;
        this.source = source;
        this.name = name;
        this.data = data;
    }

    public ModuleEvent(int t, String source, String name) { this(t, source, name, java.util.Collections.emptyMap()); }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof ModuleEvent x && Objects.equals(t, x.t) && Objects.equals(source, x.source) && Objects.equals(name, x.name) && Objects.equals(data, x.data);
    }
    @Override public int hashCode() { return Objects.hash(t, source, name, data); }
    @Override public String toString() { return "ModuleEvent(" + "t=" + t + ", " + "source=" + source + ", " + "name=" + name + ", " + "data=" + data + ")"; }
}
