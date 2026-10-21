// Ported from ADM's p3sim/pack/Packs.kt
package com.teslamaps.adm.sim.pack;

import com.teslamaps.adm.sim.pack.layouts.LayoutPack;
import com.teslamaps.adm.sim.pack.mobs.MobPack;
import com.teslamaps.adm.sim.pack.rooms.RoomPack;
import com.teslamaps.adm.sim.pack.runstate.RunStatePack;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PackSet implements java.io.Closeable {
    /** The override folder (it may not exist; the bundled packs do not need it). */
    public final Path dir;
    public final RoomPack rooms;
    public final LayoutPack layouts;
    public final MobPack mobs;
    public final RunStatePack runstate;
    public final PackReport report;
    /** Where each file came from. */
    public final PackSource source;

    public PackSet(Path dir, RoomPack rooms, LayoutPack layouts, MobPack mobs, RunStatePack runstate, PackReport report, PackSource source) {
        this.dir = dir;
        this.rooms = rooms;
        this.layouts = layouts;
        this.mobs = mobs;
        this.runstate = runstate;
        this.report = report;
        this.source = source;
    }

    public PackSet(Path dir, RoomPack rooms, LayoutPack layouts, MobPack mobs, RunStatePack runstate, PackReport report) {
        this(dir, rooms, layouts, mobs, runstate, report, PackSource.ofDir(dir));
    }

    /** What a full run needs at the least: the room library and the layout pool. */
    public boolean canBuildDungeon() { return rooms != null && layouts != null; }

    public List<String> missing() {
        List<String> out = new ArrayList<>();
        if (rooms == null) out.add("room library (" + PackFiles.ROOMS_INDEX + " + " + PackFiles.ROOMS_BLOCKS + ")");
        if (layouts == null) out.add("layout pool (" + PackFiles.LAYOUTS + ")");
        if (mobs == null) out.add("mob tables (" + PackFiles.MOBS + ")");
        if (runstate == null) out.add("run-state tables (" + PackFiles.RUNSTATE + ")");
        return out;
    }

    public List<String> userLines() { return Packs.userLines(this); }

    public List<String> originLines() {
        List<String> out = new ArrayList<>();
        for (String name : PackFiles.BUNDLED) {
            PackOrigin o = source.origin(name);
            String where;
            if (o == PackOrigin.JAR) where = "the copy bundled in the jar";
            else if (o == PackOrigin.OVERRIDE) where = "your copy in " + dir + " (overrides the jar)";
            else where = "not found";
            out.add(name + ": " + where);
        }
        return out;
    }

    private int pins = 0;
    private boolean retired = false;

    /** A running Run pins the set it was started with; pair every pin with one unpin. */
    public synchronized void pin() { pins++; }

    public synchronized void unpin() { pins--; if (pins <= 0 && retired) close(); }

    public synchronized void retire() { retired = true; if (pins <= 0) close(); }

    @Override public void close() { if (rooms != null) rooms.close(); }
}
