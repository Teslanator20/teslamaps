package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/Arena.kt

import com.teslamaps.adm.sim.run.ArenaRegion;
import com.teslamaps.adm.sim.run.WorldRegions;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.zip.GZIPInputStream;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

public final class Arena {
    private Arena() {}

    public static final class Data {
        public final int x0, y0, z0, w, h, d;
        public final BlockState[] states;
        public final short[] cells;

        public Data(int x0, int y0, int z0, int w, int h, int d, BlockState[] states, short[] cells) {
            this.x0 = x0; this.y0 = y0; this.z0 = z0; this.w = w; this.h = h; this.d = d;
            this.states = states;
            this.cells = cells;
        }

        public BlockState get(int x, int y, int z) {
            if (x < x0 || y < y0 || z < z0 || x >= x0 + w || y >= y0 + h || z >= z0 + d) return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            return states[cells[((y - y0) * d + (z - z0)) * w + (x - x0)]];
        }
    }

    private static volatile Data loaded = null;

    public static Data data() {
        Data l = loaded;
        if (l != null) return l;
        synchronized (Arena.class) {
            if (loaded == null) loaded = ArenaFixes.patch(readBin("arena.bin"));
            return loaded;
        }
    }

    public static void unload() { loaded = null; }

    static Data readBin(String name) {
        InputStream stream = Arena.class.getResourceAsStream("/assets/teslamaps/adm/p3sim/" + name);
        if (stream == null) throw new IllegalStateException(name + " missing from the jar");
        try (DataInputStream inp = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new BufferedInputStream(stream), 1 << 16)))) {
            byte[] magic = new byte[4];
            inp.readFully(magic);
            if (!new String(magic, StandardCharsets.UTF_8).equals("P3A1")) throw new IllegalArgumentException("bad arena.bin");
            int x0 = inp.readInt(), y0 = inp.readInt(), z0 = inp.readInt();
            int w = inp.readInt(), h = inp.readInt(), d = inp.readInt();
            BlockState[] states = new BlockState[inp.readInt()];
            for (int i = 0; i < states.length; i++) {
                byte[] b = new byte[inp.readUnsignedShort()];
                inp.readFully(b);
                states[i] = parse(new String(b, StandardCharsets.UTF_8));
            }
            short[] cells = new short[w * h * d];
            int i = 0;
            while (i < cells.length) {
                int n = varint(inp);
                short v = (short) varint(inp);
                Arrays.fill(cells, i, Math.min(cells.length, i + n), v);
                i += n;
            }
            return new Data(x0, y0, z0, w, h, d, states, cells);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static int varint(DataInputStream inp) throws IOException {
        int v = 0, shift = 0;
        while (true) {
            int b = inp.readUnsignedByte();
            v |= (b & 0x7f) << shift;
            if ((b & 0x80) == 0) return v;
            shift += 7;
        }
    }

    public static BlockState parse(String s) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, s, false).blockState();
        } catch (Throwable t) {
            Ec.LOG.warn("[p3sim] unknown block state {}", s);
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
    }

    public static void fill(ChunkAccess chunk) {
        WorldRegions.fillAll(chunk);
        if (ArenaRegion.active()) return;
        if (fillBlocks(chunk)) Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG));
    }

    public static boolean fillBlocks(ChunkAccess chunk) {
        Data a = data();
        int cx = chunk.getPos().getMinBlockX(), cz = chunk.getPos().getMinBlockZ();
        if (cx + 16 <= a.x0 || cz + 16 <= a.z0 || cx >= a.x0 + a.w || cz >= a.z0 + a.d) return false;
        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (int y = a.y0; y < a.y0 + a.h; y++) {
            int si = chunk.getSectionIndex(y);
            if (si < 0 || si >= chunk.getSections().length) continue;
            LevelChunkSection section = chunk.getSection(si);
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                BlockState s = a.get(cx + x, y, cz + z);
                if (s != air) section.setBlockState(x, y & 15, z, s, false);
            }
        }
        ArenaFixes.skulls(chunk);
        return true;
    }

    public static void restore(ServerLevel level, BlockPos pos) {
        BlockState s = data().get(pos.getX(), pos.getY(), pos.getZ());
        if (level.getBlockState(pos) != s) {
            level.setBlock(pos, s, FLAGS);
            ArenaFixes.reskull(level, pos);
        }
    }

    public static int resync(ServerLevel level) {
        Data a = data();
        int fixed = 0;
        for (int cx = a.x0 >> 4; cx <= (a.x0 + a.w - 1) >> 4; cx++) for (int cz = a.z0 >> 4; cz <= (a.z0 + a.d - 1) >> 4; cz++) {
            ChunkAccess chunk = level.getChunk(cx, cz);
            int xs0 = Math.max(a.x0, cx << 4), xs1 = Math.min(a.x0 + a.w, (cx << 4) + 16);
            int zs0 = Math.max(a.z0, cz << 4), zs1 = Math.min(a.z0 + a.d, (cz << 4) + 16);
            for (int y = a.y0; y < a.y0 + a.h; y++) {
                int si = chunk.getSectionIndex(y);
                if (si < 0 || si >= chunk.getSections().length) continue;
                LevelChunkSection section = chunk.getSection(si);
                for (int z = zs0; z < zs1; z++) for (int x = xs0; x < xs1; x++) {
                    BlockState want = a.get(x, y, z);
                    if (section.getBlockState(x & 15, y & 15, z & 15) == want) continue;
                    BlockPos pos = new BlockPos(x, y, z);
                    level.setBlock(pos, want, FLAGS);
                    ArenaFixes.reskull(level, pos);
                    fixed++;
                }
            }
        }
        return fixed;
    }

    public static void set(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos) != state) level.setBlock(pos, state, FLAGS);
    }

    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
}
