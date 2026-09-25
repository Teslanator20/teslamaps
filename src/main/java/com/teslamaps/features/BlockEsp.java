package com.teslamaps.features;

// Chunk index, palette pre-check and nearest-first cap after FloydAddons' FloydBlockSearch / Bax BlockEspScanner
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.EspStyle;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BlockEsp {

    private static final int MAX_INDEXED = 100_000;
    private static final int CHUNKS_PER_TICK = 4;
    private static final int MAX_TRACERS = 20;

    private static final Long2ObjectOpenHashMap<LongOpenHashSet> index = new Long2ObjectOpenHashMap<>();
    private static final ArrayDeque<Long> queue = new ArrayDeque<>();
    private static final LongOpenHashSet queued = new LongOpenHashSet();
    private static final Map<Block, Integer> targets = new HashMap<>();
    private static long[] shown = new long[0];
    private static int[] shownColors = new int[0];
    private static AABB[] shownBoxes = new AABB[0];
    private static int indexed;
    private static Level lastLevel;
    private static int targetsHash = 0;
    private static int ticks;
    private static boolean dirty;

    private static List<String> allIds;
    private static final Map<String, ItemStack> icons = new HashMap<>();

    private BlockEsp() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("BlockEsp").section("ESP").legit()
                .when(() -> cfg().blockEsp && !cfg().blockEspBlocks.isEmpty())
                .tick(BlockEsp::tick)
                .world(BlockEsp::render)
                .config("ESP", b -> {
                    b.toggle("Block ESP", () -> cfg().blockEsp, v -> cfg().blockEsp = v).cheat()
                            .desc("Highlights every block of the picked types through walls. Also /tmap blockesp add <block> [color]");
                    b.searchList("  Blocks", BlockEsp::options, id -> cfg().blockEspBlocks.containsKey(id), BlockEsp::toggle)
                            .icons(BlockEsp::icon)
                            .colors(id -> cfg().blockEspBlocks.getOrDefault(id, ""), (id, hex) -> cfg().blockEspBlocks.put(id, hex))
                            .pick(BlockEsp::looked)
                            .clearAll(() -> cfg().blockEspBlocks.clear());
                    b.slider("  Max Shown", 100f, 10000f, () -> (float) cfg().blockEspMaxShown,
                            v -> cfg().blockEspMaxShown = Math.round(v / 100f) * 100, 0).desc("Nearest blocks first");
                    b.espStyle("Block", () -> cfg().blockEspStyle);
                });

        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("blockesp")
                        .executes(c -> list(c.getSource()))
                        .then(ClientCommands.literal("list").executes(c -> list(c.getSource())))
                        .then(ClientCommands.literal("clear").executes(c -> {
                            cfg().blockEspBlocks.clear();
                            TeslaMapsConfig.save();
                            c.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §fBlock ESP list cleared"));
                            return 1;
                        }))
                        .then(ClientCommands.literal("add")
                                .then(ClientCommands.argument("block", StringArgumentType.word())
                                        .suggests((c, sb) -> suggest(sb.getRemaining(), sb))
                                        .executes(c -> add(c.getSource(), StringArgumentType.getString(c, "block"), ""))
                                        .then(ClientCommands.argument("color", StringArgumentType.word())
                                                .executes(c -> add(c.getSource(), StringArgumentType.getString(c, "block"),
                                                        StringArgumentType.getString(c, "color"))))))
                        .then(ClientCommands.literal("remove")
                                .then(ClientCommands.argument("block", StringArgumentType.word())
                                        .suggests((c, sb) -> {
                                            for (String id : cfg().blockEspBlocks.keySet()) sb.suggest(id);
                                            return sb.buildFuture();
                                        })
                                        .executes(c -> remove(c.getSource(), StringArgumentType.getString(c, "block"))))))));
    }

    // --- commands ---

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(
            String rem, com.mojang.brigadier.suggestion.SuggestionsBuilder sb) {
        String q = rem.toLowerCase(Locale.ROOT);
        int n = 0;
        for (String id : options()) {
            if (id.startsWith(q) && !id.equals(q)) {
                sb.suggest(id);
                if (++n >= 50) break;
            }
        }
        return sb.buildFuture();
    }

    private static int add(FabricClientCommandSource src, String raw, String color) {
        String id = normalize(raw);
        if (block(id) == null) {
            src.sendError(Component.literal("[TeslaMaps] Unknown block: " + raw));
            return 0;
        }
        if (!color.isEmpty() && !color.matches("(?i)[0-9a-f]{6}([0-9a-f]{2})?")) {
            src.sendError(Component.literal("[TeslaMaps] Color must be RRGGBB or AARRGGBB"));
            return 0;
        }
        cfg().blockEspBlocks.put(id, color.toUpperCase(Locale.ROOT));
        TeslaMapsConfig.save();
        src.sendFeedback(Component.literal("§b[TeslaMaps] §fBlock ESP: added §a" + id
                + (cfg().blockEsp ? "" : " §7(Block ESP is off, turn it on in /tmap > ESP)")));
        return 1;
    }

    private static int remove(FabricClientCommandSource src, String raw) {
        String id = normalize(raw);
        if (cfg().blockEspBlocks.remove(id) == null) {
            src.sendError(Component.literal("[TeslaMaps] Not in the list: " + id));
            return 0;
        }
        TeslaMapsConfig.save();
        src.sendFeedback(Component.literal("§b[TeslaMaps] §fBlock ESP: removed §c" + id));
        return 1;
    }

    private static int list(FabricClientCommandSource src) {
        Map<String, String> m = cfg().blockEspBlocks;
        if (m.isEmpty()) {
            src.sendFeedback(Component.literal("§b[TeslaMaps] §fBlock ESP list is empty. §7/tmap blockesp add <block> [color]"));
            return 1;
        }
        StringBuilder sb = new StringBuilder("§b[TeslaMaps] §fBlock ESP (" + indexed + " found):");
        for (Map.Entry<String, String> e : m.entrySet()) {
            sb.append("\n §7- §f").append(e.getKey());
            if (!e.getValue().isEmpty()) sb.append(" §7#").append(e.getValue());
        }
        src.sendFeedback(Component.literal(sb.toString()));
        return 1;
    }

    // --- config list ---

    private static String normalize(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("minecraft:") ? s.substring(10) : s;
    }

    private static Block block(String id) {
        Identifier key = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (key == null) return null;
        Block b = BuiltInRegistries.BLOCK.getOptional(key).orElse(null);
        return b == null || b == Blocks.AIR ? null : b;
    }

    private static List<String> options() {
        if (allIds == null) {
            List<String> ids = new ArrayList<>();
            for (Identifier key : BuiltInRegistries.BLOCK.keySet()) {
                if (key.getPath().equals("air")) continue;
                ids.add(key.getNamespace().equals("minecraft") ? key.getPath() : key.toString());
            }
            ids.sort(null);
            allIds = ids;
        }
        return allIds;
    }

    private static void toggle(String id) {
        if (cfg().blockEspBlocks.remove(id) == null) cfg().blockEspBlocks.put(id, "");
    }

    private static ItemStack icon(String id) {
        return icons.computeIfAbsent(id, k -> {
            Block b = block(k);
            return b == null ? ItemStack.EMPTY : new ItemStack(b.asItem());
        });
    }

    private static String looked() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        Identifier key = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(hit.getBlockPos()).getBlock());
        return key.getNamespace().equals("minecraft") ? key.getPath() : key.toString();
    }

    // --- index ---

    private static void reset() {
        index.clear();
        queue.clear();
        queued.clear();
        shown = new long[0];
        shownColors = new int[0];
        shownBoxes = new AABB[0];
        indexed = 0;
    }

    private static void rebuildTargets() {
        targets.clear();
        int fallback = EspStyle.of(cfg().blockEspStyle).argb();
        for (Map.Entry<String, String> e : cfg().blockEspBlocks.entrySet()) {
            Block b = block(e.getKey());
            if (b == null) continue;
            String hex = e.getValue();
            targets.put(b, hex == null || hex.isEmpty() ? fallback : TeslaMapsConfig.parseColor(hex));
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        int hash = cfg().blockEspBlocks.hashCode() * 31 + EspStyle.of(cfg().blockEspStyle).color.hashCode();
        if (mc.level != lastLevel || hash != targetsHash) {
            lastLevel = mc.level;
            targetsHash = hash;
            rebuildTargets();
            reset();
            ticks = 0;
        }
        if (targets.isEmpty()) return;

        if (ticks++ % 20 == 0) enqueueNearby(mc);

        for (int i = 0; i < CHUNKS_PER_TICK && !queue.isEmpty(); i++) {
            long key = queue.poll();
            queued.remove(key);
            LevelChunk chunk = mc.level.getChunkSource().getChunk(cx(key), cz(key), ChunkStatus.FULL, false);
            if (chunk != null) scanChunk(chunk, key);
        }

        if (dirty || ticks % 10 == 0) rebuildShown(mc);
    }

    private static int cx(long key) { return net.minecraft.world.level.ChunkPos.getX(key); }
    private static int cz(long key) { return net.minecraft.world.level.ChunkPos.getZ(key); }

    private static void enqueueNearby(Minecraft mc) {
        int pcx = mc.player.chunkPosition().x(), pcz = mc.player.chunkPosition().z();
        int r = mc.options.renderDistance().get();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                long key = net.minecraft.world.level.ChunkPos.pack(pcx + dx, pcz + dz);
                if (index.containsKey(key) || queued.contains(key)) continue;
                if (mc.level.getChunkSource().getChunk(pcx + dx, pcz + dz, ChunkStatus.FULL, false) == null) continue;
                queue.add(key);
                queued.add(key);
            }
        }
        // drop chunks well outside the view
        var it = index.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            long key = e.getLongKey();
            if (Math.abs(cx(key) - pcx) > r + 2 || Math.abs(cz(key) - pcz) > r + 2) {
                indexed -= e.getValue().size();
                it.remove();
                dirty = true;
            }
        }
    }

    private static void scanChunk(LevelChunk chunk, long key) {
        LongOpenHashSet found = new LongOpenHashSet();
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = cx(key) << 4, baseZ = cz(key) << 4;
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()) continue;
            if (!section.maybeHas(s -> targets.containsKey(s.getBlock()))) continue;
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (!targets.containsKey(section.getBlockState(x, y, z).getBlock())) continue;
                        if (indexed + found.size() >= MAX_INDEXED) break;
                        found.add(BlockPos.asLong(baseX + x, baseY + y, baseZ + z));
                    }
                }
            }
        }
        LongOpenHashSet old = index.put(key, found);
        if (old != null) indexed -= old.size();
        indexed += found.size();
        dirty = true;
    }

    /** From BlockUpdateMixin, client level. */
    public static void onBlockUpdate(BlockPos pos, BlockState newState) {
        if (targets.isEmpty()) return;
        LongOpenHashSet set = index.get(net.minecraft.world.level.ChunkPos.pack(pos));
        if (set == null) return;
        long p = pos.asLong();
        if (targets.containsKey(newState.getBlock())) {
            if (indexed < MAX_INDEXED && set.add(p)) { indexed++; dirty = true; }
        } else if (set.remove(p)) {
            indexed--;
            dirty = true;
        }
    }

    public static boolean listening() {
        TeslaMapsConfig c = cfg();
        return c.blockEsp && !targets.isEmpty() && !LegitMode.blocksCheats();
    }

    private static void rebuildShown(Minecraft mc) {
        dirty = false;
        LongArrayList all = new LongArrayList(indexed);
        for (LongOpenHashSet set : index.values()) {
            LongIterator it = set.iterator();
            while (it.hasNext()) all.add(it.nextLong());
        }
        int max = Math.max(1, cfg().blockEspMaxShown);
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        long[] arr = all.toLongArray();
        if (arr.length > max) {
            Long[] boxed = new Long[arr.length];
            for (int i = 0; i < arr.length; i++) boxed[i] = arr[i];
            java.util.Arrays.sort(boxed, java.util.Comparator.comparingDouble(p -> distSq(p, px, py, pz)));
            arr = new long[max];
            for (int i = 0; i < max; i++) arr[i] = boxed[i];
        }
        int[] colors = new int[arr.length];
        AABB[] boxes = new AABB[arr.length];
        for (int i = 0; i < arr.length; i++) {
            Integer c = targets.get(mc.level.getBlockState(BlockPos.of(arr[i])).getBlock());
            colors[i] = c == null ? 0 : c;
            boxes[i] = box(arr[i]);
        }
        shown = arr;
        shownColors = colors;
        shownBoxes = boxes;
    }

    private static double distSq(long p, double px, double py, double pz) {
        double dx = BlockPos.getX(p) + 0.5 - px, dy = BlockPos.getY(p) + 0.5 - py, dz = BlockPos.getZ(p) + 0.5 - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        AABB[] arr = shownBoxes;
        int[] colors = shownColors;
        if (arr.length == 0 || colors.length != arr.length) return;
        EspStyle style = EspStyle.of(cfg().blockEspStyle);
        boolean filled = EspStyle.FILLED.equals(style.mode) || EspStyle.BOTH.equals(style.mode);
        boolean outline = !EspStyle.FILLED.equals(style.mode);
        if (filled) {
            ESPRenderer.beginBoxBatch(pose, cam, true, style.throughWalls);
            for (int i = 0; i < arr.length; i++) if (colors[i] != 0) ESPRenderer.batchBox(arr[i], colors[i], style.lineWidth);
            ESPRenderer.endBoxBatch();
        }
        if (outline) {
            ESPRenderer.beginBoxBatch(pose, cam, false, style.throughWalls);
            for (int i = 0; i < arr.length; i++) if (colors[i] != 0) ESPRenderer.batchBox(arr[i], colors[i] | 0xFF000000, style.lineWidth);
            ESPRenderer.endBoxBatch();
        }
        if (style.tracer) {
            int n = Math.min(MAX_TRACERS, arr.length);
            for (int i = 0; i < n; i++) {
                if (colors[i] == 0) continue;
                ESPRenderer.drawTracerFromCamera(pose, arr[i].getCenter(), colors[i] | 0xFF000000, cam, style.throughWalls);
            }
        }
    }

    private static AABB box(long p) {
        int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
        return new AABB(x, y, z, x + 1, y + 1, z + 1);
    }
}
