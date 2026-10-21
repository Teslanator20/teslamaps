package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/GhostCapture.kt

import com.teslamaps.adm.sim.util.Pair;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class GhostStore {
    private GhostStore() {}

    private static final HashMap<String, GhostRun> cache = new HashMap<>();
    public static File root = null;

    private static File dir() { return root != null ? root : Ec.configDir().resolve("p3sim-best").toFile(); }

    private static File file(String skill, String clazz) {
        return new File(new File(dir(), skill.replaceAll("[^A-Za-z0-9 _-]", "_")), clazz + ".json.gz");
    }

    public static synchronized GhostRun best(String skill, String clazz) {
        String key = skill + "/" + clazz;
        GhostRun v = cache.get(key);
        if (v == null) {
            v = read(skill, clazz);
            cache.put(key, v);
        }
        return v;
    }

    private static GhostRun read(String skill, String clazz) {
        try {
            File f = file(skill, clazz);
            if (!f.exists()) return null;
            try (InputStream raw = new FileInputStream(f)) {
                try (GZIPInputStream in = new GZIPInputStream(raw)) {
                    return GhostRun.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean better(GhostRun nw, GhostRun old) { return old == null || nw.time <= old.time; }

    public static synchronized Pair<Boolean, GhostRun> offer(GhostRun run) {
        GhostRun old = best(run.skill, run.clazz);
        if (!better(run, old)) return Pair.of(false, old);
        File f = file(run.skill, run.clazz);
        f.getParentFile().mkdirs();
        File tmp = new File(f.getPath() + ".tmp");
        try (OutputStream out = new GZIPOutputStream(new FileOutputStream(tmp))) {
            out.write(run.toJson().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f); }
        cache.put(run.skill + "/" + run.clazz, run);
        return Pair.of(true, old);
    }

    public static synchronized void forget(String skill, String clazz) { file(skill, clazz).delete(); cache.remove(skill + "/" + clazz); }

    public static synchronized void clearCache() { cache.clear(); }
}
