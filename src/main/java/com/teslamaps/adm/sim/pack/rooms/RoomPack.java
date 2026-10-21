// Ported from ADM's p3sim/pack/rooms/RoomPack.kt
package com.teslamaps.adm.sim.pack.rooms;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.pack.JsonKt;
import com.teslamaps.adm.sim.pack.PackFiles;
import com.teslamaps.adm.sim.pack.PackFormatException;
import com.teslamaps.adm.sim.pack.PackReport;
import com.teslamaps.adm.sim.pack.PackSource;
import com.teslamaps.adm.sim.pack.Pos;
import com.teslamaps.adm.sim.pack.Rot;
import com.teslamaps.adm.sim.pack.Tile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The loaded room library. Volumes decode on first use and are cached; {@link #volume} is safe to call from the chunk
 * generator's worker threads. Close it when the sim server stops.
 */
public class RoomPack implements java.io.Closeable {
    public final int version;
    public final String mc;
    public final String generated;
    public final List<RoomDesign> designs;
    public final List<SkullTexture> textures;
    public final List<CoreEntry> cores;
    public final Map<String, String> aliases;
    private final BlobSource blobs;
    private final PackReport report;

    private final Map<String, RoomDesign> byId;
    private final Map<String, List<RoomDesign>> byFamily;
    private final Map<String, List<RoomDesign>> byName;
    private final Map<Integer, CoreEntry> coreIndex;
    private final ConcurrentHashMap<String, RoomVolume> volumes = new ConcurrentHashMap<>();
    private final Set<String> failed = ConcurrentHashMap.newKeySet();

    public RoomPack(int version, String mc, String generated, List<RoomDesign> designs, List<SkullTexture> textures, List<CoreEntry> cores,
                    Map<String, String> aliases, BlobSource blobs, PackReport report) {
        this.version = version;
        this.mc = mc;
        this.generated = generated;
        this.designs = designs;
        this.textures = textures;
        this.cores = cores;
        this.aliases = aliases;
        this.blobs = blobs;
        this.report = report;
        LinkedHashMap<String, RoomDesign> id = new LinkedHashMap<>();
        LinkedHashMap<String, List<RoomDesign>> fam = new LinkedHashMap<>();
        LinkedHashMap<String, List<RoomDesign>> nm = new LinkedHashMap<>();
        for (RoomDesign d : designs) {
            id.put(d.id, d);
            fam.computeIfAbsent(d.family, k -> new ArrayList<>()).add(d);
            nm.computeIfAbsent(d.name, k -> new ArrayList<>()).add(d);
        }
        this.byId = id;
        this.byFamily = fam;
        this.byName = nm;
        LinkedHashMap<Integer, CoreEntry> ci = new LinkedHashMap<>();
        for (CoreEntry c : cores) ci.put(c.core, c);
        this.coreIndex = ci;
    }

    public RoomDesign design(String id) { return byId.get(id); }

    /** Every build of a family, in pack order. */
    public List<RoomDesign> designsOfFamily(String family) {
        List<RoomDesign> l = byFamily.get(family);
        return l != null ? l : List.of();
    }

    /** Every design with this in-game name (builds share a name). */
    public List<RoomDesign> designsByName(String name) {
        List<RoomDesign> l = byName.get(normalise(name));
        return l != null ? l : List.of();
    }

    /** The design and variant that produce this Odin core, or null. */
    public CoreEntry designByCore(int core) { return coreIndex.get(core); }

    /** The family a room name belongs to: through the pack's {@code aliases}, else the name with spaces as underscores. */
    public String familyOf(String name) {
        String n = normalise(name);
        String a = aliases.get(n);
        if (a != null) return a;
        a = aliases.get(name);
        if (a != null) return a;
        return n.replace(' ', '_');
    }

    public SkullTexture texture(int index) { return index >= 0 && index < textures.size() ? textures.get(index) : null; }

    /** The design's base block volume, decoded on first use. Null (with one message in the report) when unavailable. */
    public RoomVolume volume(RoomDesign design) {
        RoomVolume cached = volumes.get(design.id);
        if (cached != null) return cached;
        if (failed.contains(design.id)) return null;
        BlobRef ref = design.vol;
        BlobSource source = blobs;
        if (ref == null || source == null) { failed.add(design.id); report.warn("Room " + design.id + " has no block data."); return null; }
        try {
            byte[] bytes = source.read(ref.off, ref.len);
            if (ref.crc32 != null && !RoomBlobs.crc32Hex(bytes).equals(ref.crc32.toLowerCase(Locale.ROOT))) throw new PackFormatException("checksum mismatch");
            if (!ref.kind.equals("volume")) throw new PackFormatException("the blob is a " + ref.kind + ", not a volume");
            RoomVolume v = RoomBlobs.decodeVolume(bytes);
            if (v.w != design.w || v.d != design.d || v.h != design.h || v.y0 != design.y0) {
                throw new PackFormatException("the volume is " + v.w + "x" + v.d + "x" + v.h + " at y" + v.y0 + ", the index says "
                        + design.w + "x" + design.d + "x" + design.h + " at y" + design.y0);
            }
            RoomVolume prev = volumes.putIfAbsent(design.id, v);
            return prev != null ? prev : v;
        } catch (Exception e) {
            failed.add(design.id);
            report.warn("Room " + design.id + " is unavailable: " + e.getMessage());
            return null;
        }
    }

    /** The variant [variantId] of [design] when given and found, else the base volume. Variants are not cached. */
    public RoomVolume volumeFor(RoomDesign design, String variantId) {
        if (variantId == null) return volume(design);
        RoomVariant v = null;
        for (RoomVariant x : design.variants) if (x.id.equals(variantId)) { v = x; break; }
        if (v == null) { report.warn("Room " + design.id + " has no variant " + variantId + "; the base build is used."); return volume(design); }
        RoomVolume vv = variantVolume(design, v);
        return vv != null ? vv : volume(design);
    }

    /** The volume with a variant's patch applied; not cached. Null when unavailable. */
    public RoomVolume variantVolume(RoomDesign design, RoomVariant variant) {
        RoomVolume base = volume(design);
        if (base == null) return null;
        BlobSource source = blobs;
        if (source == null) return null;
        try {
            byte[] bytes = source.read(variant.vol.off, variant.vol.len);
            if (variant.vol.crc32 != null && !RoomBlobs.crc32Hex(bytes).equals(variant.vol.crc32.toLowerCase(Locale.ROOT))) {
                throw new PackFormatException("checksum mismatch");
            }
            return base.withPatch(RoomBlobs.decodePatch(bytes));
        } catch (Exception e) {
            report.warn("Variant " + variant.id + " of " + design.id + " is unavailable: " + e.getMessage());
            return null;
        }
    }

    /** Frees the file handle. */
    @Override public void close() { if (blobs != null) blobs.close(); }

    public static final String FORMAT = "ads-rooms";
    public static final int SUPPORTED_VERSION = 1;

    /** U+200B stripped. */
    public static String normalise(String name) { return name.replace("​", ""); }

    public static RoomPack load(Path dir) { return load(dir, new PackReport()); }

    public static RoomPack load(Path dir, PackReport report) { return load(PackSource.ofDir(dir), report); }

    public static RoomPack load(PackSource source) { return load(source, new PackReport()); }

    /** Load from a resolved {@link PackSource}: each of the two files may be the jar's copy or the override folder's. Never throws. */
    public static RoomPack load(PackSource source, PackReport report) {
        Path indexPath = source.file(PackFiles.ROOMS_INDEX);
        if (indexPath == null) { report.error("The room library " + PackFiles.ROOMS_INDEX + " is missing from " + source.missingFrom() + "."); return null; }
        JsonObject root;
        try (InputStream in = Files.newInputStream(indexPath)) {
            root = JsonKt.readGzipJson(in);
        } catch (Exception e) {
            report.error("The room library " + PackFiles.ROOMS_INDEX + " is unreadable" + source.note(PackFiles.ROOMS_INDEX) + ": " + e.getMessage());
            return null;
        }
        if (!RoomPackKt.checkHeader(root, FORMAT, SUPPORTED_VERSION, PackFiles.ROOMS_INDEX, report)) return null;

        String blocksNameRaw = JsonKt.str(root, "blocksFile");
        String blocksName = blocksNameRaw != null ? blocksNameRaw : PackFiles.ROOMS_BLOCKS;
        Path blocksPath = source.file(blocksName);
        if (blocksPath == null) { report.error("The room library's block file " + blocksName + " is missing from " + source.missingFrom() + "."); return null; }
        long size;
        try {
            size = Files.size(blocksPath);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        Long wantSize = JsonKt.long_(root, "blocksFileBytes");
        if (wantSize != null && wantSize != size) {
            report.error(blocksName + " is " + size + " bytes but the index expects " + wantSize + ". The room library is incomplete or its two files do not belong together; an override folder must hold both room files, from the same build.");
            return null;
        }
        String want = JsonKt.str(root, "blocksFileCrc32");
        if (want != null) {
            String got;
            try { got = fileCrc32(blocksPath); } catch (Exception e) { report.error(blocksName + " is unreadable" + source.note(blocksName) + ": " + e.getMessage()); return null; }
            if (!got.equals(want.toLowerCase(Locale.ROOT))) {
                report.error(blocksName + " does not match its index (checksum " + got + ", expected " + want + "). An override folder must hold both room files, from the same build.");
                return null;
            }
        }
        FileBlobSource blobSource;
        try { blobSource = new FileBlobSource(blocksPath); } catch (Exception e) { report.error(blocksName + " cannot be opened" + source.note(blocksName) + ": " + e.getMessage()); return null; }
        RoomPack pack = fromIndex(root, blobSource, report);
        if (pack == null) blobSource.close();
        return pack;
    }

    public static RoomPack fromIndex(JsonObject root, BlobSource blobs) { return fromIndex(root, blobs, new PackReport()); }

    /** Builds the pack from an already parsed index and a blob source (tests, tools). Null when the header is wrong. */
    public static RoomPack fromIndex(JsonObject root, BlobSource blobs, PackReport report) {
        if (!RoomPackKt.checkHeader(root, FORMAT, SUPPORTED_VERSION, PackFiles.ROOMS_INDEX, report)) return null;
        List<SkullTexture> textures = new ArrayList<>();
        for (JsonObject t : JsonKt.objects(JsonKt.arr(root, "textures"))) {
            String id = JsonKt.str(t, "id");
            if (id == null) continue;
            String role = JsonKt.str(t, "role");
            textures.add(new SkullTexture(id, JsonKt.str(t, "value"), JsonKt.str(t, "profileId"), role != null ? role : "unknown"));
        }
        ArrayList<RoomDesign> designs = new ArrayList<>();
        for (JsonObject o : JsonKt.objects(JsonKt.arr(root, "designs"))) {
            try {
                designs.add(parseDesign(o));
            } catch (Exception e) {
                String id = JsonKt.str(o, "id");
                report.warn("A room design was skipped (" + (id != null ? id : "no id") + "): " + e.getMessage());
            }
        }
        List<CoreEntry> cores = new ArrayList<>();
        for (JsonObject c : JsonKt.objects(JsonKt.arr(root, "cores"))) {
            Integer core = JsonKt.int_(c, "core");
            if (core == null) continue;
            String design = JsonKt.str(c, "design");
            if (design == null) continue;
            cores.add(new CoreEntry(core, design, JsonKt.str(c, "variant")));
        }
        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        JsonObject al = JsonKt.obj(root, "aliases");
        if (al != null) {
            for (Map.Entry<String, JsonElement> en : al.entrySet()) {
                String v = JsonKt.asStr(en.getValue());
                if (v != null) aliases.put(normalise(en.getKey()), v);
            }
        }
        if (designs.isEmpty()) report.warn("The room library has no designs.");
        Integer version = JsonKt.int_(root, "version");
        return new RoomPack(version != null ? version : 1, JsonKt.str(root, "mc"), JsonKt.str(root, "generated"), designs, textures, cores, aliases, blobs, report);
    }

    private static <T> T or(T v, T def) { return v != null ? v : def; }

    private static RoomDesign parseDesign(JsonObject o) {
        String id = JsonKt.str(o, "id");
        if (id == null) throw new PackFormatException("no id");
        String shape = JsonKt.str(o, "shape");
        if (shape == null) throw new PackFormatException("no shape");
        String nameRaw = JsonKt.str(o, "name");
        String name = normalise(nameRaw != null ? nameRaw : id.replace('_', ' '));
        String family = or(JsonKt.str(o, "family"), id);
        String buildKey = JsonKt.str(o, "buildKey");
        String type = or(JsonKt.str(o, "type"), "normal");
        List<Tile> tiles = JsonKt.asTiles(o.get("tiles"));
        Integer w = JsonKt.int_(o, "W");
        if (w == null) throw new PackFormatException("no W");
        Integer d = JsonKt.int_(o, "D");
        if (d == null) throw new PackFormatException("no D");
        int h = or(JsonKt.int_(o, "H"), 0);
        int y0 = or(JsonKt.int_(o, "y0"), 0);
        int roofY = or(JsonKt.int_(o, "roofY"), 0);
        List<Rot> rotations = new ArrayList<>();
        for (String s : JsonKt.strings(JsonKt.arr(o, "rotations"))) { Rot r = Rot.fromWord(s); if (r != null) rotations.add(r); }
        String doors = JsonKt.str(o, "doors");
        JsonObject so = JsonKt.obj(o, "source");
        RoomSource source = so != null ? new RoomSource(JsonKt.str(so, "blocks"), JsonKt.str(so, "file"), JsonKt.str(so, "ref"), JsonKt.int_(so, "captureRotation")) : null;
        JsonObject vo = JsonKt.obj(o, "vol");
        BlobRef vol = vo != null ? parseBlob(vo) : null;

        List<DoorSlot> slots = new ArrayList<>();
        for (JsonObject s : JsonKt.objects(JsonKt.arr(o, "slots"))) {
            Tile tile = JsonKt.asTile(s.get("tile"));
            if (tile == null) continue;
            String side = JsonKt.str(s, "side");
            if (side == null || side.isEmpty()) continue;
            JsonObject seal = JsonKt.obj(s, "seal");
            slots.add(new DoorSlot(tile, side.charAt(0), or(JsonKt.bool(s, "open"), false), seal != null ? parseSeal(seal) : null));
        }

        List<RoomBlockEntity> blockEntities = new ArrayList<>();
        for (JsonObject b : JsonKt.objects(JsonKt.arr(o, "blockEntities"))) {
            Integer x = JsonKt.int_(b, "x");
            if (x == null) continue;
            Integer y = JsonKt.int_(b, "y");
            if (y == null) continue;
            Integer z = JsonKt.int_(b, "z");
            if (z == null) continue;
            JsonArray text = JsonKt.arr(b, "text");
            blockEntities.add(new RoomBlockEntity(new Pos(x, y, z), or(JsonKt.str(b, "type"), "other"), JsonKt.int_(b, "tex"),
                    text != null ? JsonKt.strings(text) : null, or(JsonKt.bool(b, "marker"), false), JsonKt.str(b, "src")));
        }

        List<RoomSecret> secrets = new ArrayList<>();
        for (JsonObject s : JsonKt.objects(JsonKt.arr(o, "secrets"))) {
            Integer x = JsonKt.int_(s, "x");
            if (x == null) continue;
            Integer y = JsonKt.int_(s, "y");
            if (y == null) continue;
            Integer z = JsonKt.int_(s, "z");
            if (z == null) continue;
            secrets.add(new RoomSecret(or(JsonKt.str(s, "id"), "s?"), or(JsonKt.str(s, "type"), "chest"), JsonKt.str(s, "bucket"), new Pos(x, y, z),
                    JsonKt.str(s, "facing"), JsonKt.asPos(s.get("lever")), JsonKt.asPosList(s.get("alt")), or(JsonKt.bool(s, "baked"), false), JsonKt.str(s, "src")));
        }

        List<RoomLever> levers = new ArrayList<>();
        for (JsonObject l : JsonKt.objects(JsonKt.arr(o, "levers"))) {
            Pos pos = JsonKt.asPos(l.get("pos"));
            if (pos == null) continue;
            levers.add(new RoomLever(pos, JsonKt.asPosList(l.get("opens"))));
        }

        List<List<Pos>> crypts = new ArrayList<>();
        JsonArray ca = JsonKt.arr(o, "crypts");
        if (ca != null) for (JsonElement e : ca) crypts.add(JsonKt.asPosList(e));

        List<List<Pos>> superboomWalls = new ArrayList<>();
        JsonArray sa = JsonKt.arr(o, "superboomWalls");
        if (sa != null) for (JsonElement e : sa) superboomWalls.add(JsonKt.asPosList(e));

        List<Pos> fallingBlocks = JsonKt.asPosList(o.get("fallingBlocks"));

        List<RoomCrusher> crushers = new ArrayList<>();
        for (JsonObject c : JsonKt.objects(JsonKt.arr(o, "crushers"))) {
            crushers.add(new RoomCrusher(or(JsonKt.str(c, "id"), "crusher"), JsonKt.ints(JsonKt.arr(c, "slab")), JsonKt.str(c, "axis"),
                    or(JsonKt.int_(c, "sign"), 1), or(JsonKt.int_(c, "sweep"), 0), JsonKt.str(c, "material")));
        }

        List<RoomTripwire> tripwires = new ArrayList<>();
        JsonArray ta = JsonKt.arr(o, "tripwires");
        if (ta != null) {
            for (JsonElement t : ta) {
                JsonArray a = JsonKt.asArr(t);
                if (a == null) continue;
                if (a.size() < 3) continue;
                Integer x = JsonKt.asIntOrNull(a.get(0));
                if (x == null) continue;
                Integer y = JsonKt.asIntOrNull(a.get(1));
                if (y == null) continue;
                Integer z = JsonKt.asIntOrNull(a.get(2));
                if (z == null) continue;
                tripwires.add(new RoomTripwire(new Pos(x, y, z), JsonKt.asStr(getOrNull(a, 3))));
            }
        }

        RoomPuzzle puzzle = null;
        JsonObject p = JsonKt.obj(o, "puzzle");
        if (p != null) {
            LinkedHashMap<String, List<Pos>> anchors = new LinkedHashMap<>();
            JsonObject an = JsonKt.obj(p, "anchors");
            if (an != null) for (Map.Entry<String, JsonElement> en : an.entrySet()) anchors.put(en.getKey(), anchorList(en.getValue()));
            puzzle = new RoomPuzzle(or(JsonKt.str(p, "kind"), "unknown"), anchors);
        }

        JsonObject odin = JsonKt.obj(o, "odin");

        List<RoomVariant> variants = new ArrayList<>();
        for (JsonObject v : JsonKt.objects(JsonKt.arr(o, "variants"))) {
            String vid = JsonKt.str(v, "id");
            if (vid == null) continue;
            String kind = or(JsonKt.str(v, "kind"), "state");
            Integer core = JsonKt.int_(v, "core");
            String srcFile = JsonKt.str(v, "srcFile");
            int changed = or(JsonKt.int_(v, "changedCells"), 0);
            JsonObject vv = JsonKt.obj(v, "vol");
            if (vv == null) continue;
            variants.add(new RoomVariant(vid, kind, core, srcFile, changed, parseBlob(vv)));
        }

        List<String> gaps = JsonKt.strings(JsonKt.arr(o, "gaps"));

        List<Integer> baseCores = new ArrayList<>();
        JsonArray bc = JsonKt.arr(o, "baseCores");
        if (bc != null) for (JsonElement e : bc) { Integer i = JsonKt.asIntOrNull(e); if (i != null) baseCores.add(i); }

        String clear = JsonKt.str(o, "clear");

        return new RoomDesign(id, name, family, buildKey, type, shape, tiles, w, d, h, y0, roofY, rotations, doors, source, vol, slots,
                blockEntities, secrets, levers, crypts, superboomWalls, fallingBlocks, crushers, tripwires, puzzle, odin, variants, gaps,
                baseCores, clear);
    }

    /** An anchor is one {@code [x,y,z]} or a list of them. */
    private static List<Pos> anchorList(JsonElement v) {
        Pos p = JsonKt.asPos(v);
        if (p != null) return new ArrayList<>(List.of(p));
        return JsonKt.asPosList(v);
    }

    private static JsonElement getOrNull(JsonArray a, int i) { return i < a.size() ? a.get(i) : null; }

    private static BlobRef parseBlob(JsonObject o) {
        return new BlobRef(or(JsonKt.str(o, "kind"), "volume"), or(JsonKt.long_(o, "off"), 0L), or(JsonKt.int_(o, "len"), 0),
                JsonKt.str(o, "crc32"), or(JsonKt.int_(o, "nonAir"), 0));
    }

    private static SlotSeal parseSeal(JsonObject o) {
        List<SealCell> cells = new ArrayList<>();
        JsonArray arr = JsonKt.arr(o, "cells");
        if (arr != null) {
            for (JsonElement c : arr) {
                JsonArray a = JsonKt.asArr(c);
                if (a == null) continue;
                if (a.size() < 4) continue;
                Integer x = JsonKt.asIntOrNull(a.get(0));
                if (x == null) continue;
                Integer y = JsonKt.asIntOrNull(a.get(1));
                if (y == null) continue;
                Integer z = JsonKt.asIntOrNull(a.get(2));
                if (z == null) continue;
                String state = JsonKt.asStr(a.get(3));
                if (state == null) continue;
                cells.add(new SealCell(new Pos(x, y, z), state));
            }
        }
        return new SlotSeal(JsonKt.str(o, "src"), or(JsonKt.bool(o, "incomplete"), false), cells);
    }

    private static String fileCrc32(Path path) throws IOException {
        java.util.zip.CRC32 c = new java.util.zip.CRC32();
        try (InputStream s = Files.newInputStream(path)) {
            byte[] buf = new byte[1 << 16];
            while (true) {
                int n = s.read(buf);
                if (n < 0) break;
                c.update(buf, 0, n);
            }
        }
        return String.format(Locale.ROOT, "%08x", c.getValue());
    }
}
