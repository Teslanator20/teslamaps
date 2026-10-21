// Ported from ADM's p3sim/pack/rooms/RoomPack.kt
package com.teslamaps.adm.sim.pack.rooms;

import com.teslamaps.adm.sim.pack.Pos;

import java.util.List;
import java.util.Objects;

/** {@code type} is {@code chest item bat essence redstoneKey lockedChest}; {@code bucket} keeps the source's name. */
public final class RoomSecret {
    public final String id;
    public final String type;
    public final String bucket;
    public final Pos pos;
    public final String facing;
    public final Pos lever;
    public final List<Pos> alt;
    public final boolean baked;
    public final String src;

    public RoomSecret(String id, String type, String bucket, Pos pos, String facing, Pos lever, List<Pos> alt, boolean baked, String src) {
        this.id = id; this.type = type; this.bucket = bucket; this.pos = pos; this.facing = facing;
        this.lever = lever; this.alt = alt; this.baked = baked; this.src = src;
    }

    @Override public boolean equals(Object o) {
        return o instanceof RoomSecret s && Objects.equals(id, s.id) && Objects.equals(type, s.type) && Objects.equals(bucket, s.bucket)
                && Objects.equals(pos, s.pos) && Objects.equals(facing, s.facing) && Objects.equals(lever, s.lever) && Objects.equals(alt, s.alt)
                && baked == s.baked && Objects.equals(src, s.src);
    }
    @Override public int hashCode() { return Objects.hash(id, type, bucket, pos, facing, lever, alt, baked, src); }
    @Override public String toString() {
        return "RoomSecret(id=" + id + ", type=" + type + ", bucket=" + bucket + ", pos=" + pos + ", facing=" + facing + ", lever=" + lever
                + ", alt=" + alt + ", baked=" + baked + ", src=" + src + ")";
    }
}
