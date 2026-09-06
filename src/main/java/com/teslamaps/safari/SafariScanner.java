package com.teslamaps.safari;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/**
 * Dumps the fixed furniture of the Safari so it can be baked into a resource later. The layout is
 * static across instances, so one pass per biome is enough. Nothing is sent to the server - this
 * only reads chunks the client already has.
 */
public class SafariScanner {

    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("teslamaps");
    private static final Path SCAN = DIR.resolve("safari_scan.txt");
    private static final Path DEBUG = DIR.resolve("safari_debug.txt");

    public static List<String> scan(int chunkRadius) {
        List<String> report = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            report.add("§cNot in a world.");
            return report;
        }

        SafariData.Biome biome = SafariData.currentBiome();
        String biomeName = biome == null ? "unknown" : biome.label;

        Map<String, List<BlockPos>> blocks = new LinkedHashMap<>();
        blocks.put("bell", new ArrayList<>());
        blocks.put("bee_nest", new ArrayList<>());
        blocks.put("campfire", new ArrayList<>());
        blocks.put("cobbled_deepslate", new ArrayList<>());
        blocks.put("tuff", new ArrayList<>());
        blocks.put("lectern", new ArrayList<>());

        int cx = mc.player.chunkPosition().x();
        int cz = mc.player.chunkPosition().z();
        int chunks = 0;
        for (int x = cx - chunkRadius; x <= cx + chunkRadius; x++) {
            for (int z = cz - chunkRadius; z <= cz + chunkRadius; z++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(x, z, false);
                if (chunk == null) continue;
                chunks++;
                chunk.findBlocks(SafariScanner::interesting, (pos, state) -> {
                    String key = keyFor(state);
                    if (key != null) blocks.get(key).add(pos.immutable());
                });
            }
        }

        // Snoozle walls are the small patches that carry both materials at once
        List<BlockPos> snoozle = snoozleWalls(blocks.get("cobbled_deepslate"), blocks.get("tuff"));

        List<String> paintings = new ArrayList<>();
        for (Painting p : mc.level.getEntitiesOfClass(Painting.class,
                mc.player.getBoundingBox().inflate(chunkRadius * 16.0), e -> true)) {
            paintings.add(fmt(p.blockPosition()));
        }

        StringBuilder out = new StringBuilder();
        out.append("=== Safari scan | biome=").append(biomeName)
           .append(" | area=").append(com.teslamaps.utils.SkyblockUtils.getArea())
           .append(" | chunks=").append(chunks)
           .append(" | player=").append(fmt(mc.player.blockPosition())).append("\n");

        appendList(out, "bells", blocks.get("bell"));
        appendList(out, "bee_nests", blocks.get("bee_nest"));
        appendList(out, "campfires", blocks.get("campfire"));
        appendList(out, "snoozle_walls", snoozle);
        appendList(out, "lecterns", blocks.get("lectern"));
        out.append("paintings (").append(paintings.size()).append("): ")
           .append(String.join(" | ", paintings)).append("\n\n");

        write(SCAN, out.toString());

        report.add("§b[Safari] §7Scanned §f" + chunks + " §7chunks in §f" + biomeName);
        report.add("§7Bells §f" + blocks.get("bell").size()
                + " §8| §7Nests §f" + blocks.get("bee_nest").size()
                + " §8| §7Campfires §f" + blocks.get("campfire").size()
                + " §8| §7Snoozle walls §f" + snoozle.size()
                + " §8| §7Paintings §f" + paintings.size());
        report.add("§7Appended to §fconfig/teslamaps/safari_scan.txt");
        return report;
    }

    private static boolean interesting(BlockState state) {
        return keyFor(state) != null;
    }

    private static String keyFor(BlockState state) {
        if (state.is(Blocks.BELL)) return "bell";
        // only 3 BEE_NEST turned up but the wiki lists 11 bee spots, so the rest are hives
        if (state.is(Blocks.BEE_NEST) || state.is(Blocks.BEEHIVE)) return "bee_nest";
        if (state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) return "campfire";
        if (state.is(Blocks.COBBLED_DEEPSLATE)) return "cobbled_deepslate";
        if (state.is(Blocks.TUFF)) return "tuff";
        if (state.is(Blocks.LECTERN)) return "lectern";
        return null;
    }

    /**
     * The first pass matched any deepslate next to any tuff and found 30-50 spots instead of 5,
     * because both are ordinary Cavern terrain. A real Snoozle wall is a small mixed patch, so
     * require both materials nearby AND few blocks of either in the wider area around it.
     */
    private static List<BlockPos> snoozleWalls(List<BlockPos> deepslate, List<BlockPos> tuff) {
        List<BlockPos> hits = new ArrayList<>();
        Set<Long> used = new HashSet<>();

        for (BlockPos d : deepslate) {
            BlockPos near = null;
            for (BlockPos t : tuff) {
                if (d.distSqr(t) <= 4) { near = t; break; }
            }
            if (near == null) continue;

            int bulk = 0;
            for (BlockPos o : deepslate) if (o.distSqr(d) <= 100) bulk++;
            for (BlockPos o : tuff) if (o.distSqr(d) <= 100) bulk++;
            if (bulk > 24) continue;   // natural formation, not a breakable patch

            long key = new BlockPos(d.getX() / 8, d.getY() / 8, d.getZ() / 8).asLong();
            if (used.add(key)) hits.add(d);
        }
        return hits;
    }

    /** Everything we guess about in the ESP, written out so the guesses can be checked. */
    public static List<String> debug(int radius) {
        List<String> report = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            report.add("§cNot in a world.");
            return report;
        }

        BlockPos self = mc.player.blockPosition();
        StringBuilder out = new StringBuilder();
        out.append("=== Safari debug\n");
        out.append("area          : ").append(com.teslamaps.utils.SkyblockUtils.getArea()).append("\n");
        out.append("inSafari      : ").append(SafariData.inSafari()).append("\n");
        out.append("player pos    : ").append(fmt(self)).append("\n");
        out.append("biome raw id  : ").append(mc.level.getBiome(self).getRegisteredName()).append("\n");
        out.append("biome mapped  : ").append(SafariData.currentBiome()).append("\n");
        out.append("run active    : ").append(SafariTracker.isActive()).append("\n");
        out.append("ticksFrozen   : ").append(mc.player.getTicksFrozen()).append("\n\n");

        out.append("--- HUD lines\n");
        for (String line : SafariTracker.lines()) out.append(SafariTracker.plain(line)).append("\n");
        out.append("\n--- entities within ").append(radius).append("\n");

        AABB box = mc.player.getBoundingBox().inflate(radius);
        int n = 0;
        for (Entity e : mc.level.getEntitiesOfClass(Entity.class, box, x -> true)) {
            if (e == mc.player) continue;
            n++;
            out.append(String.format("%-22s %-24s ", e.getType().toString(), fmt(e.blockPosition())));
            out.append("biome=").append(mc.level.getBiome(e.blockPosition()).getRegisteredName());

            if (e.getCustomName() != null) {
                out.append(" name='").append(e.getCustomName().getString().replace("§", "&")).append("'");
            }
            AABB b = e.getBoundingBox();
            out.append(String.format(" size=%.2fx%.2f", b.getXsize(), b.getYsize()));

            if (e instanceof Display.ItemDisplay id) {
                SlotAccess slot = id.getSlot(0);
                ItemStack stack = slot == null ? ItemStack.EMPTY : slot.get();
                out.append(" item=").append(stack.isEmpty() ? "-" : stack.getItem().toString());
                out.append(" interp=").append(id.getPosRotInterpolationDuration());
            }
            if (e instanceof ArmorStand as) {
                ItemStack head = as.getItemBySlot(EquipmentSlot.HEAD);
                out.append(" head=").append(head.isEmpty() ? "-" : head.getItem().toString());
                out.append(" scale=").append(as.getScale());
            }
            if (e instanceof Shulker sh) out.append(" shulkerColor=").append(sh.getColor());
            out.append("\n");
        }
        out.append("(").append(n).append(" entities)\n\n");

        write(DEBUG, out.toString());

        report.add("§b[Safari] §7biome §f" + mc.level.getBiome(self).getRegisteredName()
                + " §7-> §f" + SafariData.currentBiome());
        report.add("§7Area §f" + com.teslamaps.utils.SkyblockUtils.getArea()
                + " §8| §7inSafari §f" + SafariData.inSafari()
                + " §8| §7run §f" + SafariTracker.isActive());
        report.add("§7" + n + " entities written to §fconfig/teslamaps/safari_debug.txt");
        return report;
    }

    private static void appendList(StringBuilder out, String name, List<BlockPos> list) {
        out.append(name).append(" (").append(list.size()).append("): ");
        List<String> parts = new ArrayList<>();
        for (BlockPos p : list) parts.add(fmt(p));
        out.append(String.join(" | ", parts)).append("\n");
    }

    private static String fmt(BlockPos p) {
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }

    private static void write(Path path, String text) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
