// Ported from ADM's p3sim/pack/mobs/MobPack.kt
package com.teslamaps.adm.sim.pack.mobs;

import java.util.Objects;

/** How the nametag stand is placed and written. {@code head} is the legacy-coded prefix (ends in the star part). */
public final class MobTag {
    public final int idOffset;
    public final double dy;
    public final String head;
    public final String nameColor;
    public final boolean bold;
    /** The modifier is written as its own segment (the trap-room Deathmite). */
    public final boolean modSplit;

    public MobTag(int idOffset, double dy, String head, String nameColor, boolean bold, boolean modSplit) {
        this.idOffset = idOffset; this.dy = dy; this.head = head; this.nameColor = nameColor; this.bold = bold; this.modSplit = modSplit;
    }

    public MobTag(int idOffset, double dy, String head, String nameColor, boolean bold) { this(idOffset, dy, head, nameColor, bold, false); }

    @Override public boolean equals(Object o) {
        return o instanceof MobTag t && idOffset == t.idOffset && Double.compare(dy, t.dy) == 0 && Objects.equals(head, t.head)
                && Objects.equals(nameColor, t.nameColor) && bold == t.bold && modSplit == t.modSplit;
    }
    @Override public int hashCode() { return Objects.hash(idOffset, dy, head, nameColor, bold, modSplit); }
    @Override public String toString() {
        return "MobTag(idOffset=" + idOffset + ", dy=" + dy + ", head=" + head + ", nameColor=" + nameColor + ", bold=" + bold + ", modSplit=" + modSplit + ")";
    }
}
