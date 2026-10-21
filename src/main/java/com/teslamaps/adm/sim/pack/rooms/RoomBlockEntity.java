// Ported from ADM's p3sim/pack/rooms/RoomPack.kt
package com.teslamaps.adm.sim.pack.rooms;

import com.teslamaps.adm.sim.pack.Pos;

import java.util.List;
import java.util.Objects;

/** {@code type} is {@code skull chest trapped_chest sign hanging_sign banner dispenser dropper bed other}. */
public final class RoomBlockEntity {
    public final Pos pos;
    public final String type;
    public final Integer tex;
    public final List<String> text;
    public final boolean marker;
    public final String src;

    public RoomBlockEntity(Pos pos, String type, Integer tex, List<String> text, boolean marker, String src) {
        this.pos = pos; this.type = type; this.tex = tex; this.text = text; this.marker = marker; this.src = src;
    }

    @Override public boolean equals(Object o) {
        return o instanceof RoomBlockEntity b && Objects.equals(pos, b.pos) && Objects.equals(type, b.type) && Objects.equals(tex, b.tex)
                && Objects.equals(text, b.text) && marker == b.marker && Objects.equals(src, b.src);
    }
    @Override public int hashCode() { return Objects.hash(pos, type, tex, text, marker, src); }
    @Override public String toString() {
        return "RoomBlockEntity(pos=" + pos + ", type=" + type + ", tex=" + tex + ", text=" + text + ", marker=" + marker + ", src=" + src + ")";
    }
}
