package com.teslamaps.adm.sim.clear.layout;

// Ported from ADM's p3sim/clear/layout/LayoutRegion.kt

import com.teslamaps.adm.sim.Arena;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.RoomBlockEntity;
import com.teslamaps.adm.sim.pack.rooms.RoomVolume;
import com.teslamaps.adm.sim.pack.rooms.SkullTexture;
import com.teslamaps.adm.sim.run.WorldRegion;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

public class LayoutRegion implements WorldRegion {
    private static class Pasted {
        final RoomInstance inst;
        final Placement p;
        final RoomVolume v;
        final BlockState[] states;
        final boolean[] wantsEntity;
        final Map<Long, RoomBlockEntity> records;
        final int x0;
        final int x1;
        final int z0;
        final int z1;

        Pasted(RoomInstance inst, BlockEntityMaker maker) {
            this.inst = inst;
            this.p = inst.placement;
            this.v = inst.volume;
            this.states = new BlockState[v.palette.size()];
            for (int i = 0; i < states.length; i++) {
                String s = v.palette.get(i);
                if (i == 0 || s.equals("minecraft:air")) states[i] = null;
                else {
                    BlockState st = BlockStates.rotated(s, p.quarterTurns());
                    states[i] = st.isAir() ? null : st;
                }
            }
            this.wantsEntity = new boolean[states.length];
            for (int i = 0; i < states.length; i++) wantsEntity[i] = states[i] != null && maker.wants(states[i]);
            HashMap<Long, RoomBlockEntity> recs = new HashMap<>();
            for (RoomBlockEntity b : inst.design.blockEntities) if (!b.marker) recs.put(key(b.pos.x, b.pos.y, b.pos.z), b);
            this.records = recs;
            this.x0 = p.originX;
            this.x1 = p.originX + p.boxW() - 1;
            this.z0 = p.originZ;
            this.z1 = p.originZ + p.boxD() - 1;
        }

        boolean overlaps(int cx, int cz) { return cx + 15 >= x0 && cx <= x1 && cz + 15 >= z0 && cz <= z1; }

        static long key(int x, int y, int z) { return ((long) x << 40) | ((long) z << 20) | ((long) y & 0xFFFFF); }
    }

    private final String name;
    private final BlockEntityMaker maker;
    private final List<Pasted> rooms;

    private final Map<Long, Map<Integer, BlockState>> overlays;

    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;

    private final Set<Long> generated = ConcurrentHashMap.newKeySet();

    public LayoutRegion(String name, List<RoomInstance> instances, List<Edit> edits, List<SkullTexture> textures) {
        this.name = name;
        this.maker = new BlockEntityMaker(textures);
        List<Pasted> rs = new ArrayList<>(instances.size());
        for (RoomInstance i : instances) rs.add(new Pasted(i, maker));
        this.rooms = rs;

        HashMap<Long, Map<Integer, BlockState>> byChunk = new HashMap<>();
        int ax = Integer.MAX_VALUE, bx = Integer.MIN_VALUE, az = Integer.MAX_VALUE, bz = Integer.MIN_VALUE;
        for (Edit e : edits) {
            BlockState st = BlockStates.rotated(e.state, e.quarterTurns);
            byChunk.computeIfAbsent(chunkKey(e.x >> 4, e.z >> 4), k -> new HashMap<>()).put(cellKey(e.x & 15, e.y, e.z & 15), st);
            ax = Math.min(ax, e.x); bx = Math.max(bx, e.x); az = Math.min(az, e.z); bz = Math.max(bz, e.z);
        }
        for (Pasted r : rooms) { ax = Math.min(ax, r.x0); bx = Math.max(bx, r.x1); az = Math.min(az, r.z0); bz = Math.max(bz, r.z1); }
        this.overlays = byChunk;
        if (ax > bx) { ax = DungeonFrame.ORIGIN; bx = ax; az = ax; bz = ax; }
        this.minX = ax; this.maxX = bx; this.minZ = az; this.maxZ = bz;
    }

    @Override public String name() { return name; }
    @Override public int minX() { return minX; }
    @Override public int maxX() { return maxX; }
    @Override public int minZ() { return minZ; }
    @Override public int maxZ() { return maxZ; }

    public static class Stats {
        public final int chunks;
        public final long totalNanos;
        public final long maxNanos;
        public final long cells;

        public Stats(int chunks, long totalNanos, long maxNanos, long cells) {
            this.chunks = chunks;
            this.totalNanos = totalNanos;
            this.maxNanos = maxNanos;
            this.cells = cells;
        }

        public double totalMs() { return totalNanos / 1e6; }

        public double maxMs() { return maxNanos / 1e6; }

        @Override
        public String toString() { return String.format(Locale.ROOT, "%d chunks, %.0f ms total, %.1f ms slowest, %d blocks", chunks, totalMs(), maxMs(), cells); }
    }

    private final AtomicLong nanos = new AtomicLong();
    private final AtomicLong slowest = new AtomicLong();
    private final AtomicLong cellCount = new AtomicLong();

    public Stats stats() { return new Stats(generated.size(), nanos.get(), slowest.get(), cellCount.get()); }

    public int roomCount() { return rooms.size(); }

    // ---- generation

    @Override
    public void fill(ChunkAccess chunk) {
        long t0 = System.nanoTime();
        int bx = chunk.getPos().getMinBlockX();
        int bz = chunk.getPos().getMinBlockZ();
        long key = chunkKey(chunk.getPos().x(), chunk.getPos().z());
        Map<Integer, BlockState> overlay = overlays.get(key);
        long cells = 0L;
        for (Pasted r : rooms) if (r.overlaps(bx, bz)) cells += paste(chunk, r, bx, bz, overlay);
        if (overlay != null) cells += applyOverlay(chunk, bx, bz, overlay);
        generated.add(key);
        long dt = System.nanoTime() - t0;
        nanos.addAndGet(dt);
        cellCount.addAndGet(cells);
        long m = slowest.get();
        while (dt > m && !slowest.compareAndSet(m, dt)) m = slowest.get();
    }

    private long paste(ChunkAccess chunk, Pasted r, int bx, int bz, Map<Integer, BlockState> overlay) {
        int xa = Math.max(bx, r.x0), xb = Math.min(bx + 15, r.x1);
        int za = Math.max(bz, r.z0), zb = Math.min(bz + 15, r.z1);
        int n = (xb - xa + 1) * (zb - za + 1);
        int[] wx = new int[n], wz = new int[n], cx = new int[n], cz = new int[n];
        int i = 0;
        for (int z = za; z <= zb; z++) for (int x = xa; x <= xb; x++) {
            Pair<Integer, Integer> c = r.p.toCanonical(x, z);
            if (c == null) continue;
            wx[i] = x; wz[i] = z; cx[i] = c.first; cz[i] = c.second; i++;
        }
        RoomVolume v = r.v;
        short[] cells = v.cells;
        long written = 0L;
        LevelChunkSection section = null;
        int sectionIndex = Integer.MIN_VALUE;
        int sectionCount = chunk.getSections().length;
        for (int y = v.y0; y < v.y0 + v.h; y++) {
            int si = chunk.getSectionIndex(y);
            if (si < 0 || si >= sectionCount) continue;
            if (si != sectionIndex) { section = chunk.getSection(si); sectionIndex = si; }
            LevelChunkSection sec = section;
            if (sec == null) continue;
            int layer = (y - v.y0) * v.d;
            for (int k = 0; k < i; k++) {
                int idx = cells[(layer + cz[k]) * v.w + cx[k]] & 0xFFFF;
                if (idx == 0) continue;
                BlockState state = r.states[idx];
                if (state == null) continue;
                int lx = wx[k] & 15, lz = wz[k] & 15;
                if (overlay != null && overlay.containsKey(cellKey(lx, y, lz))) continue;
                sec.setBlockState(lx, y & 15, lz, state, false);
                written++;
                if (r.wantsEntity[idx]) {
                    BlockPos pos = new BlockPos(wx[k], y, wz[k]);
                    BlockEntity be = maker.create(pos, state, r.records.get(Pasted.key(cx[k], y, cz[k])));
                    if (be != null) chunk.setBlockEntity(be);
                }
            }
        }
        return written;
    }

    private long applyOverlay(ChunkAccess chunk, int bx, int bz, Map<Integer, BlockState> overlay) {
        long written = 0L;
        int sectionCount = chunk.getSections().length;
        for (Map.Entry<Integer, BlockState> en : overlay.entrySet()) {
            int k = en.getKey();
            BlockState state = en.getValue();
            int y = cellY(k), lx = cellX(k), lz = cellZ(k);
            int si = chunk.getSectionIndex(y);
            if (si < 0 || si >= sectionCount) continue;
            BlockState old = chunk.getSection(si).setBlockState(lx, y & 15, lz, state, false);
            written++;
            BlockPos pos = new BlockPos(bx + lx, y, bz + lz);
            if (old.hasBlockEntity() && !(old.getBlock() == state.getBlock())) chunk.removeBlockEntity(pos);
            if (maker.wants(state) && chunk.getBlockEntity(pos) == null) {
                BlockEntity be = maker.create(pos, state, null);
                if (be != null) chunk.setBlockEntity(be);
            }
        }
        return written;
    }

    // ---- resync

    @Override
    public int resync(ServerLevel level) {
        int fixed = 0;
        for (long key : new ArrayList<>(generated)) {
            int cxk = (int) (key >> 32);
            int czk = (int) key;
            try {
                fixed += resyncChunk(level, cxk, czk);
            } catch (Exception e) {
                Ec.LOG.warn("[ads] layout resync of chunk " + cxk + "," + czk + " failed", e);
            }
        }
        return fixed;
    }

    private int resyncChunk(ServerLevel level, int cxk, int czk) {
        LevelChunk chunk = level.getChunk(cxk, czk);
        int bx = cxk << 4;
        int bz = czk << 4;
        Map<Integer, BlockState> overlay = overlays.get(chunkKey(cxk, czk));
        BlockState air = Blocks.AIR.defaultBlockState();
        HashSet<Integer> seen = new HashSet<>();
        int[] fixed = {0};
        int sectionCount = chunk.getSections().length;
        Checker check = (x, y, z, want, record) -> {
            int si = chunk.getSectionIndex(y);
            if (si < 0 || si >= sectionCount) return;
            if (chunk.getSection(si).getBlockState(x & 15, y & 15, z & 15) == want) return;
            BlockPos pos = new BlockPos(x, y, z);
            level.setBlock(pos, want, Arena.FLAGS);
            if (maker.wants(want)) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) maker.apply(be, record);
            }
            // setBlock gives a dispenser or a hopper the entity generation never made
            else if (maker.forbids(want)) level.removeBlockEntity(pos);
            fixed[0]++;
        };
        for (Pasted r : rooms) {
            if (!r.overlaps(bx, bz)) continue;
            RoomVolume v = r.v;
            for (int z = Math.max(bz, r.z0); z <= Math.min(bz + 15, r.z1); z++) for (int x = Math.max(bx, r.x0); x <= Math.min(bx + 15, r.x1); x++) {
                // only the room's own columns
                if (!r.p.ownsColumn(x, z)) continue;
                Pair<Integer, Integer> c = r.p.toCanonical(x, z);
                if (c == null) continue;
                int ix = c.first, iz = c.second;
                for (int y = v.y0; y < v.y0 + v.h; y++) {
                    int k = cellKey(x & 15, y, z & 15);
                    BlockState over = overlay != null ? overlay.get(k) : null;
                    if (over != null) {
                        if (seen.add(k)) check.check(x, y, z, over, null);
                        continue;
                    }
                    int idx = v.cells[((y - v.y0) * v.d + iz) * v.w + ix] & 0xFFFF;
                    BlockState st = r.states[idx];
                    check.check(x, y, z, st != null ? st : air, r.records.get(Pasted.key(ix, y, iz)));
                }
            }
        }
        if (overlay != null) for (Map.Entry<Integer, BlockState> en : overlay.entrySet()) {
            int k = en.getKey();
            if (!seen.add(k)) continue;
            check.check(bx + cellX(k), cellY(k), bz + cellZ(k), en.getValue(), null);
        }
        return fixed[0];
    }

    @FunctionalInterface
    private interface Checker {
        void check(int x, int y, int z, BlockState want, RoomBlockEntity record);
    }

    private static long chunkKey(int cx, int cz) { return ((long) cx << 32) | ((long) cz & 0xFFFFFFFFL); }

    private static int cellKey(int lx, int y, int lz) { return ((y + 64) << 8) | (lz << 4) | lx; }

    private static int cellX(int k) { return k & 15; }

    private static int cellZ(int k) { return (k >> 4) & 15; }

    private static int cellY(int k) { return (k >> 8) - 64; }
}
