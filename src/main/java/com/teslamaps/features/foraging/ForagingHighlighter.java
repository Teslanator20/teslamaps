package com.teslamaps.features.foraging;

// After Skyblocker's AbstractBlockHighlighter / Lushlilac / SeaLumies / RubyVeilshroom / Honeyhive highlighters
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Filled boxes on Lushlilac, Sea Lumies, Ruby Veilshroom and lootable Honeyhives. */
public final class ForagingHighlighter {

    private static final int CHUNKS_PER_TICK = 4;
    private static final int LUSHLILAC = 0x66FF55FF, SEA_LUMIES = 0x6655FFFF, VEILSHROOM = 0x66FF3333, HONEYHIVE = 0x665555FF;

    private static final Long2ObjectOpenHashMap<LongOpenHashSet> index = new Long2ObjectOpenHashMap<>();
    private static final ArrayDeque<Long> queue = new ArrayDeque<>();
    private static final LongOpenHashSet queued = new LongOpenHashSet();
    private static AABB[] boxes = new AABB[0];
    private static int[] colors = new int[0];
    private static Level lastLevel;
    private static int hash, ticks;
    private static boolean dirty, active;

    private ForagingHighlighter() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ForagingHighlighter").section("Foraging").when(() -> cfg().foragingHighlighter)
                .tick(ForagingHighlighter::tick)
                .world(ForagingHighlighter::render)
                .config("Foraging", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Block Highlighter");
                    b.toggle("Foraging Block Highlighter", () -> c.foragingHighlighter, v -> c.foragingHighlighter = v)
                            .desc("Filled boxes on gatherables. Moonglade: Lushlilac, Sea Lumies. Torrhus: Ruby Veilshroom, lootable Honeyhives.")
                            .tags("lushlilac", "sea lumies", "sea pickle", "veilshroom", "honeyhive", "highlight");
                    b.toggle("  Lushlilac", () -> c.foragingHighlightLushlilac, v -> c.foragingHighlightLushlilac = v);
                    b.toggle("  Sea Lumies", () -> c.foragingHighlightSeaLumies, v -> c.foragingHighlightSeaLumies = v);
                    b.slider("    Minimum Count", 1f, 4f, () -> c.foragingSeaLumiesMin, v -> c.foragingSeaLumiesMin = v, 0);
                    b.toggle("  Ruby Veilshroom", () -> c.foragingHighlightVeilshroom, v -> c.foragingHighlightVeilshroom = v);
                    b.toggle("  Full Honeyhives", () -> c.foragingHighlightHoneyhives, v -> c.foragingHighlightHoneyhives = v);
                });
    }

    private static int settingsHash() {
        TeslaMapsConfig c = cfg();
        int h = Foraging.inMoonglade() ? 1 : Foraging.inTorrhus() ? 2 : 0;
        h = h * 31 + (c.foragingHighlightLushlilac ? 1 : 0);
        h = h * 31 + (c.foragingHighlightSeaLumies ? 1 : 0);
        h = h * 31 + Math.round(c.foragingSeaLumiesMin);
        h = h * 31 + (c.foragingHighlightVeilshroom ? 1 : 0);
        return h * 31 + (c.foragingHighlightHoneyhives ? 1 : 0);
    }

    private static void reset() {
        index.clear();
        queue.clear();
        queued.clear();
        boxes = new AABB[0];
        colors = new int[0];
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        int h = settingsHash();
        if (mc.level != lastLevel || h != hash) {
            lastLevel = mc.level;
            hash = h;
            reset();
            ticks = 0;
        }
        active = Foraging.onForagingIsland();
        if (!active) return;
        if (ticks++ % 20 == 0) enqueue(mc);
        for (int i = 0; i < CHUNKS_PER_TICK && !queue.isEmpty(); i++) {
            long key = queue.poll();
            queued.remove(key);
            LevelChunk chunk = mc.level.getChunkSource().getChunk(ChunkPos.getX(key), ChunkPos.getZ(key), ChunkStatus.FULL, false);
            if (chunk != null) scan(mc.level, chunk, key);
        }
        if (dirty || ticks % 20 == 0) rebuild(mc);
    }

    private static void enqueue(Minecraft mc) {
        int pcx = mc.player.chunkPosition().x(), pcz = mc.player.chunkPosition().z();
        int r = mc.options.renderDistance().get();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                long key = ChunkPos.pack(pcx + dx, pcz + dz);
                if (index.containsKey(key) || queued.contains(key)) continue;
                if (mc.level.getChunkSource().getChunk(pcx + dx, pcz + dz, ChunkStatus.FULL, false) == null) continue;
                queue.add(key);
                queued.add(key);
            }
        }
        var it = index.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            long key = it.next().getLongKey();
            if (Math.abs(ChunkPos.getX(key) - pcx) > r + 2 || Math.abs(ChunkPos.getZ(key) - pcz) > r + 2) {
                it.remove();
                dirty = true;
            }
        }
    }

    private static boolean candidate(BlockState s) {
        return s.is(Blocks.FLOWERING_AZALEA) || s.is(Blocks.SEA_PICKLE) || s.is(Blocks.CRIMSON_FUNGUS) || s.is(Blocks.BEE_NEST);
    }

    private static int color(Level level, BlockPos pos, BlockState s) {
        TeslaMapsConfig c = cfg();
        if (Foraging.inMoonglade()) {
            if (c.foragingHighlightLushlilac && s.is(Blocks.FLOWERING_AZALEA)) return LUSHLILAC;
            if (c.foragingHighlightSeaLumies && s.is(Blocks.SEA_PICKLE)
                    && s.getValue(SeaPickleBlock.PICKLES) >= Math.round(c.foragingSeaLumiesMin)) return SEA_LUMIES;
        } else if (Foraging.inTorrhus()) {
            if (c.foragingHighlightVeilshroom && s.is(Blocks.CRIMSON_FUNGUS)) return VEILSHROOM;
            if (c.foragingHighlightHoneyhives && s.is(Blocks.BEE_NEST)
                    && s.getValue(BeehiveBlock.HONEY_LEVEL) == BeehiveBlock.MAX_HONEY_LEVELS
                    && !level.getBlockState(pos.above()).is(Blocks.BIRCH_FENCE)) return HONEYHIVE;
        }
        return 0;
    }

    private static void scan(Level level, LevelChunk chunk, long key) {
        LongOpenHashSet found = new LongOpenHashSet();
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = ChunkPos.getX(key) << 4, baseZ = ChunkPos.getZ(key) << 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(ForagingHighlighter::candidate)) continue;
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState s = section.getBlockState(x, y, z);
                        if (!candidate(s)) continue;
                        pos.set(baseX + x, baseY + y, baseZ + z);
                        if (color(level, pos, s) != 0) found.add(pos.asLong());
                    }
                }
            }
        }
        index.put(key, found);
        dirty = true;
    }

    public static boolean listening() {
        return active && cfg().foragingHighlighter;
    }

    /** From BlockUpdateMixin, client level. */
    public static void onBlockUpdate(Level level, BlockPos pos, BlockState state) {
        LongOpenHashSet set = index.get(ChunkPos.pack(pos));
        if (set == null) return;
        boolean hit = candidate(state) && color(level, pos, state) != 0;
        if (hit ? set.add(pos.asLong()) : set.remove(pos.asLong())) dirty = true;
    }

    private static void rebuild(Minecraft mc) {
        dirty = false;
        List<AABB> b = new ArrayList<>();
        List<Integer> cs = new ArrayList<>();
        for (LongOpenHashSet set : index.values()) {
            LongIterator it = set.iterator();
            while (it.hasNext()) {
                BlockPos p = BlockPos.of(it.nextLong());
                int c = color(mc.level, p, mc.level.getBlockState(p));
                if (c == 0) continue;
                b.add(new AABB(p));
                cs.add(c);
            }
        }
        int[] arr = new int[cs.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = cs.get(i);
        boxes = b.toArray(new AABB[0]);
        colors = arr;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        AABB[] arr = boxes;
        int[] cs = colors;
        if (!active || arr.length == 0 || cs.length != arr.length) return;
        ESPRenderer.beginBoxBatch(pose, cam, true, false);
        for (int i = 0; i < arr.length; i++) ESPRenderer.batchBox(arr[i], cs[i], 1f);
        ESPRenderer.endBoxBatch();
    }
}
