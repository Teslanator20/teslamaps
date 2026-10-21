// Ported from ADM's p3sim/pack/PackSource.kt
package com.teslamaps.adm.sim.pack;

import com.teslamaps.adm.sim.util.Pair;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public class PackSource {
    /** The packs folder whose files override the bundled ones (null for a source built from nothing). */
    public final Path overrideDir;
    private final Map<String, Pair<Path, PackOrigin>> resolved;
    /** Where the bundled files were unpacked, or null when none had to be. */
    public final Path cacheDir;
    private final boolean bundledLooked;
    /** The cache folder the bundled files could not be unpacked into, or null. */
    public final Path unpackFailedIn;

    private PackSource(Path overrideDir, Map<String, Pair<Path, PackOrigin>> resolved, Path cacheDir, boolean bundledLooked, Path unpackFailedIn) {
        this.overrideDir = overrideDir;
        this.resolved = resolved;
        this.cacheDir = cacheDir;
        this.bundledLooked = bundledLooked;
        this.unpackFailedIn = unpackFailedIn;
    }

    private PackSource(Path overrideDir, Map<String, Pair<Path, PackOrigin>> resolved, Path cacheDir, boolean bundledLooked) {
        this(overrideDir, resolved, cacheDir, bundledLooked, null);
    }

    private PackSource(Path overrideDir, Map<String, Pair<Path, PackOrigin>> resolved, Path cacheDir) {
        this(overrideDir, resolved, cacheDir, false);
    }

    /** The path of [name] to read, or null. A file the bundle does not carry is looked up in the override folder alone. */
    public Path file(String name) {
        Pair<Path, PackOrigin> r = resolved.get(name);
        if (r != null && r.first != null) return r.first;
        if (overrideDir == null) return null;
        Path p = overrideDir.resolve(name);
        return Files.isRegularFile(p) ? p : null;
    }

    public PackOrigin origin(String name) {
        Pair<Path, PackOrigin> r = resolved.get(name);
        if (r != null && r.second != null) return r.second;
        return overrideDir != null && Files.isRegularFile(overrideDir.resolve(name)) ? PackOrigin.OVERRIDE : null;
    }

    /** For "X is missing from ...": the places a file was looked for. */
    public String missingFrom() { return bundledLooked ? "the jar and " + overrideDir : "" + overrideDir; }

    /** For "X is unreadable...": a note on which copy failed, empty when it is the plain file in the folder. */
    public String note(String name) { return origin(name) == PackOrigin.JAR ? " (the copy bundled in the jar)" : ""; }

    /** Every file read straight from [dir] (tests, tools). */
    public static PackSource ofDir(Path dir) { return new PackSource(dir, Map.of(), null); }

    public static PackSource resolve(Path overrideDir, BundledPacks bundled, Path cacheRoot, PackReport report) {
        return resolve(overrideDir, bundled, cacheRoot, report, PackFiles.BUNDLED);
    }

    /**
     * Resolves [names]: a file in [overrideDir] wins; the others come from [bundled], unpacked into a sub-folder of [cacheRoot]
     * named by the hash of all the bundled content. Never throws.
     */
    public static PackSource resolve(Path overrideDir, BundledPacks bundled, Path cacheRoot, PackReport report, List<String> names) {
        LinkedHashMap<String, Pair<Path, PackOrigin>> found = new LinkedHashMap<>();
        for (String n : names) {
            Path p = overrideDir.resolve(n);
            if (Files.isRegularFile(p)) found.put(n, Pair.of(p, PackOrigin.OVERRIDE));
        }
        List<String> need = new ArrayList<>();
        for (String n : names) if (!found.containsKey(n)) need.add(n);
        Path cache = null;
        Path failedIn = null;
        if (!need.isEmpty()) {
            try {
                cache = unpack(bundled, names, need, cacheRoot, report);
                if (cache != null) {
                    for (String n : need) if (Files.isRegularFile(cache.resolve(n))) found.put(n, Pair.of(cache.resolve(n), PackOrigin.JAR));
                }
            } catch (Exception e) {
                report.error("ADS could not unpack its bundled data packs into " + cacheRoot + ": " + e.getClass().getSimpleName() + ": "
                        + (e.getMessage() != null ? e.getMessage() : "no details") + ". Check that this folder is writable and the disk is not full.");
                failedIn = cacheRoot;
            }
        }
        return new PackSource(overrideDir, found, cache, bundled != BundledPacks.NONE, failedIn);
    }

    private static Path unpack(BundledPacks bundled, List<String> all, List<String> need, Path cacheRoot, PackReport report) throws IOException {
        HashMap<String, Long> sizes = new HashMap<>();
        HashMap<String, String> hashes = new HashMap<>();
        MessageDigest outer = sha256();
        for (String n : all) {
            InputStream in = bundled.open(n);
            if (in == null) continue;
            Pair<Long, String> d;
            try (in) { d = digest(in); }
            long size = d.first;
            String hex = d.second;
            sizes.put(n, size);
            hashes.put(n, hex);
            outer.update((n + "\n" + size + "\n" + hex + "\n").getBytes(StandardCharsets.UTF_8));
        }
        boolean any = false;
        for (String n : need) if (sizes.containsKey(n)) { any = true; break; }
        if (!any) return null;
        String full = hex(outer.digest());
        String id = full.substring(0, Math.min(HASH_CHARS, full.length()));
        Path dir = cacheRoot.resolve(id);
        Files.createDirectories(dir);
        for (String n : need) {
            Long sizeBoxed = sizes.get(n);
            if (sizeBoxed == null) continue;
            long size = sizeBoxed;
            String hex = hashes.get(n);
            if (hex == null) throw new java.util.NoSuchElementException("Key " + n + " is missing in the map.");
            Path target = dir.resolve(n);
            if (isIntact(target, size, hex)) continue;
            Path tmp = Files.createTempFile(dir, n, ".part");
            try {
                InputStream in = bundled.open(n);
                if (in == null) throw new IOException(n + " vanished from the jar");
                try (in) { Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING); }
                if (Files.size(tmp) != size) throw new IOException(n + " was unpacked with the wrong size");
                place(tmp, target, size, hex);
            } finally {
                Files.deleteIfExists(tmp);
            }
        }
        removeOlder(cacheRoot, id, report);
        return dir;
    }

    private static void place(Path tmp, Path target, long size, String sha256) throws IOException {
        try {
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, target);
            }
        } catch (IOException e) {
            if (!isIntact(target, size, sha256)) throw e;
        }
    }

    private static boolean isIntact(Path file, long size, String sha256) {
        try {
            if (!(Files.isRegularFile(file) && Files.size(file) == size)) return false;
            try (InputStream in = Files.newInputStream(file)) {
                return digest(in).second.equals(sha256);
            }
        } catch (IOException e) {
            return false;
        }
    }

    private static void removeOlder(Path cacheRoot, String keep, PackReport report) {
        try {
            try (Stream<Path> s = Files.list(cacheRoot)) {
                for (Path d : s.toList()) {
                    String name = d.getFileName().toString();
                    if (name.equals(keep) || !Files.isDirectory(d) || name.length() != HASH_CHARS || !allHex(name)) continue;
                    try { deleteRecursively(d.toFile()); } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            report.info("Old unpacked data packs in " + cacheRoot + " were not cleaned up: " + e.getMessage());
        }
    }

    private static boolean allHex(String s) {
        for (int i = 0; i < s.length(); i++) if ("0123456789abcdef".indexOf(s.charAt(i)) < 0) return false;
        return true;
    }

    /** Kotlin's File.deleteRecursively: children first, keeps going past failures. */
    private static boolean deleteRecursively(File f) {
        boolean ok = true;
        if (f.isDirectory() && !Files.isSymbolicLink(f.toPath())) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) ok = deleteRecursively(k) && ok;
        }
        return (f.delete() || !f.exists()) && ok;
    }

    private static Pair<Long, String> digest(InputStream input) throws IOException {
        MessageDigest md = sha256();
        byte[] buf = new byte[1 << 16];
        long total = 0L;
        while (true) {
            int n = input.read(buf);
            if (n < 0) break;
            md.update(buf, 0, n);
            total += n;
        }
        return Pair.of(total, hex(md.digest()));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }

    private static final int HASH_CHARS = 16;
}
