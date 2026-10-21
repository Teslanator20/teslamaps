// Ported from ADM's p3sim/pack/Packs.kt
package com.teslamaps.adm.sim.pack;

import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.pack.layouts.LayoutPack;
import com.teslamaps.adm.sim.pack.mobs.MobPack;
import com.teslamaps.adm.sim.pack.rooms.RoomPack;
import com.teslamaps.adm.sim.pack.runstate.RunStatePack;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class Packs {
    private Packs() {}

    private static final org.slf4j.Logger log = Ec.LOG;
    private static final HashSet<String> loggedOverrides = new HashSet<>();

    public static volatile PackSet current = null;

    public static PackSet load() { return load(PackFiles.defaultDir()); }
    public static PackSet load(Path dir) { return load(dir, BundledPacks.JAR); }
    public static PackSet load(Path dir, BundledPacks bundled) { return load(dir, bundled, null); }

    public static synchronized PackSet load(Path dir, BundledPacks bundled, Path cacheRoot) {
        PackReport report = new PackReport();
        PackSource source;
        try {
            source = PackSource.resolve(dir, bundled, cacheRoot != null ? cacheRoot : PackFiles.cacheRootFor(dir), report);
        } catch (Throwable t) {
            report.error("ADS could not look for its data packs: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
            source = PackSource.ofDir(dir);
        }
        for (String name : PackFiles.BUNDLED) {
            if (source.origin(name) == PackOrigin.OVERRIDE && loggedOverrides.add(dir + "/" + name)) {
                log.info("[ads] using your copy of {} in {} instead of the one bundled in the jar", name, dir);
                report.info("Using your copy of " + name + " from " + dir + " instead of the bundled one.");
            }
        }
        RoomPack rooms = RoomPack.load(source, report);
        LayoutPack layouts = LayoutPack.load(source, rooms, report);
        MobPack mobs = MobPack.load(source, report);
        RunStatePack runstate = RunStatePack.load(source, report);
        PackSet set = new PackSet(dir, rooms, layouts, mobs, runstate, report, source);
        PackSet old = current;
        current = set;
        if (old != null && old != set) old.retire();
        return set;
    }

    public static CompletableFuture<PackSet> loadAsync() { return loadAsync(PackFiles.defaultDir()); }
    public static CompletableFuture<PackSet> loadAsync(Path dir) { return loadAsync(dir, BundledPacks.JAR); }
    public static CompletableFuture<PackSet> loadAsync(Path dir, BundledPacks bundled) { return loadAsync(dir, bundled, null); }

    public static CompletableFuture<PackSet> loadAsync(Path dir, BundledPacks bundled, Path cacheRoot) {
        return CompletableFuture.supplyAsync(() -> load(dir, bundled, cacheRoot));
    }

    public static PackSet ensureLoaded() {
        PackSet c = current;
        return c != null ? c : load();
    }

    public static void unload() {
        PackSet c = current;
        current = null;
        if (c != null) c.retire();
    }

    public static String missingMessage(Path dir) {
        return "ADS could not load its data packs. They are built into the Engineer Client jar (" + PackFiles.ROOMS_INDEX + ", " + PackFiles.ROOMS_BLOCKS + ", "
                + PackFiles.LAYOUTS + ", " + PackFiles.MOBS + ", " + PackFiles.RUNSTATE + "), "
                + "so this copy of the mod is damaged or incomplete; installing it again should fix that. While developing you can put your own copies in " + dir
                + " instead. The boss practice works without them.";
    }

    public static String unpackFailedMessage(Path folder, Path dir) {
        return "ADS could not load its data packs. They are built into the Engineer Client jar, but copying them out into " + folder + " failed. "
                + "While developing you can put your own copies in " + dir + " instead. The boss practice works without them.";
    }

    public static List<String> userLines(PackSet set) {
        ArrayList<String> lines = new ArrayList<>();
        List<String> missing = set.missing();
        Path unpackFailedIn = set.source.unpackFailedIn;
        if (missing.size() == 4) {
            List<String> out = new ArrayList<>();
            out.add(unpackFailedIn != null ? unpackFailedMessage(unpackFailedIn, set.dir) : missingMessage(set.dir));
            for (String e : set.report.errors()) if (!e.contains("missing from")) out.add(e);
            return out;
        }
        if (!missing.isEmpty()) lines.add("ADS could not load the " + String.join(", ", missing) + ". "
                + (!set.canBuildDungeon() ? "A full run needs the room library and the layout pool." : "The run goes on with built-in defaults for what is missing."));
        for (String e : set.report.errors()) {
            boolean seen = false;
            for (String l : lines) if (l.contains(e)) { seen = true; break; }
            if (!seen) lines.add(e);
        }
        lines.addAll(set.report.warnings());
        return lines;
    }
}
