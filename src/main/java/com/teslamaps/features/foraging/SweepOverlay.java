package com.teslamaps.features.foraging;

// Ported from Skyblocker's SweepOverlay
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Marks the logs the current Sweep stat fells from the looked-at log (or a thrown axe's hit). */
public final class SweepOverlay {

    private static final Pattern SWEEP = Pattern.compile("Sweep:\\s*∮?\\s*([\\d,]+)");
    private static final int MAX_BASIC = 36;
    private static final Set<String> AXES = Set.of("JUNGLE_AXE", "TREECAPITATOR_AXE", "FIG_AXE", "FIGSTONE_AXE",
            "HELIX_CHOPPER", "ROOKIE_AXE", "PROMISING_AXE", "SWEET_AXE", "EFFICIENT_AXE");
    private static final Set<String> THROWABLE = Set.of("JUNGLE_AXE", "TREECAPITATOR_AXE", "FIG_AXE", "FIGSTONE_AXE", "HELIX_CHOPPER");
    private static final int[] FIG = {7, 9, 11, 16, 21, 26, 34, 42, 50, 62, 74, 86, 102, 118, 134, 154, 174, 194, 214, 234};
    private static final int[] MANGROVE = {50, 55, 60, 75, 90, 105, 130, 155, 180, 230, 280, 330, 405, 480, 555, 655, 755, 855, 955, 1055};
    private static final int[] HELIX = {150, 160, 170, 200, 230, 260, 320, 380, 440, 540, 640, 740, 890, 1040, 1190, 1390, 1590, 1790, 1990, 2190};

    private static float sweep;
    private static int ticks;
    private static boolean warned;
    private static List<AABB> boxes = List.of();
    private static boolean thrown;

    private SweepOverlay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SweepOverlay").section("Foraging").when(() -> cfg().sweepOverlay)
                .tick(SweepOverlay::tick)
                .world(SweepOverlay::render)
                .config("Foraging", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Sweep Overlay", () -> c.sweepOverlay, v -> c.sweepOverlay = v)
                            .desc("Marks the logs your Sweep stat fells from the log you look at. Needs the Sweep stat in /tablist")
                            .tags("sweep", "logs", "throwing axe");
                    b.color("  Color", () -> c.sweepOverlayColor, v -> c.sweepOverlayColor = v);
                    b.toggle("  Thrown Axe Preview", () -> c.sweepOverlayThrown, v -> c.sweepOverlayThrown = v)
                            .desc("Throwable axes: predicts where the axe lands (50 blocks, with gravity), dimmer color, half the logs");
                });
    }

    private static boolean validLocation() {
        String a = SkyblockUtils.getArea();
        return Foraging.onForagingIsland() || a.equalsIgnoreCase("Hub") || a.equalsIgnoreCase("The Park")
                || a.equalsIgnoreCase("Private Island");
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (ticks++ % 20 == 0) readSweep(mc);
        boxes = List.of();
        if (!validLocation() || mc.level == null) return;
        String id = ItemUtil.skyblockId(mc.player.getMainHandItem());
        boolean axe = AXES.contains(id), throwable = THROWABLE.contains(id);
        if (!axe && !throwable) return;
        BlockPos start = null;
        thrown = false;
        if (axe && mc.hitResult instanceof BlockHitResult hit && mc.hitResult.getType() == HitResult.Type.BLOCK
                && isLog(mc.level.getBlockState(hit.getBlockPos()))) {
            start = hit.getBlockPos();
        } else if (throwable && cfg().sweepOverlayThrown) {
            start = thrownAxe(mc, mc.player.getEyePosition(1f), mc.player.getViewVector(1f), mc.player.getXRot());
            thrown = start != null;
        }
        if (start == null || sweep <= 0) return;
        boxes = connected(mc.level, start, thrown);
    }

    private static void readSweep(Minecraft mc) {
        for (String l : TabListUtils.getTabListLines()) {
            if (!l.contains("Sweep")) continue;
            Matcher m = SWEEP.matcher(StringUtil.strip(l));
            if (m.find()) {
                try {
                    sweep = Float.parseFloat(m.group(1).replace(",", ""));
                    return;
                } catch (NumberFormatException ignored) {}
            }
        }
        sweep = 0;
        if (!warned && Foraging.onForagingIsland()) {
            warned = true;
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§b[TeslaMaps] §cSweep Overlay: no Sweep stat in the tab list. Add it with /tablist."));
        }
    }

    private static BlockPos thrownAxe(Minecraft mc, Vec3 start, Vec3 look, double pitch) {
        double angle = Math.abs(pitch / 90);
        double gravity = ((1 - angle) * (2.65 + 2.51827 * angle - 2.43475 * angle * angle + 1.22748 * angle * angle * angle)) / 1000;
        for (int i = 0; i < 50; i++) {
            Vec3 pos = start.add(look.scale(i)).subtract(0, gravity * i * i, 0);
            BlockPos b = BlockPos.containing(pos);
            if (isLog(mc.level.getBlockState(b))) return b;
        }
        return null;
    }

    private static boolean isFig(BlockState s) { return s.is(Blocks.STRIPPED_SPRUCE_LOG) || s.is(Blocks.STRIPPED_SPRUCE_WOOD); }
    private static boolean isMangrove(BlockState s) { return s.is(Blocks.MANGROVE_LOG) || s.is(Blocks.MANGROVE_WOOD); }
    private static boolean helixBirch(BlockState s) { return s.is(Blocks.STRIPPED_BIRCH_LOG) || s.is(Blocks.STRIPPED_BIRCH_WOOD); }
    private static boolean helixMangrove(BlockState s) { return s.is(Blocks.STRIPPED_MANGROVE_LOG) || s.is(Blocks.STRIPPED_MANGROVE_WOOD); }

    private static boolean isLog(BlockState s) {
        if (Foraging.inMoonglade()) return isFig(s) || isMangrove(s);
        if (Foraging.inTorrhus()) return helixBirch(s) || helixMangrove(s);
        if (SkyblockUtils.getArea().equalsIgnoreCase("Hub")) return s.is(Blocks.OAK_LOG) || s.is(Blocks.OAK_WOOD);
        return s.is(BlockTags.LOGS);
    }

    // Helix trees fell one wood colour at a time
    private static boolean sameWood(BlockState a, BlockState b) {
        if (!Foraging.inTorrhus()) return true;
        return (helixBirch(a) && helixBirch(b)) || (helixMangrove(a) && helixMangrove(b));
    }

    private static int maxWood(BlockState s) {
        if (!Foraging.onForagingIsland()) return (int) Math.min(1 + sweep, MAX_BASIC);
        int[] req = Foraging.inMoonglade() ? (isFig(s) ? FIG : isMangrove(s) ? MANGROVE : null) : HELIX;
        if (req == null) return 0;
        for (int i = 0; i < req.length; i++) if (sweep < req[i]) return i;
        return req.length;
    }

    private static List<AABB> connected(Level level, BlockPos start, boolean thrown) {
        BlockState first = level.getBlockState(start);
        int max = maxWood(first);
        if (thrown) max /= 2;
        List<AABB> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (out.size() < max && !queue.isEmpty()) {
            BlockPos p = queue.poll();
            BlockState cur = level.getBlockState(p);
            if (!isLog(cur)) continue;
            out.add(new AABB(p));
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                BlockPos n = p.offset(dx, dy, dz);
                if (seen.contains(n)) continue;
                BlockState ns = level.getBlockState(n);
                if (isLog(ns) && sameWood(cur, ns)) {
                    seen.add(n);
                    queue.add(n);
                }
            }
        }
        return out;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<AABB> b = boxes;
        if (b.isEmpty()) return;
        int c = TeslaMapsConfig.parseColor(cfg().sweepOverlayColor);
        if (thrown) {
            int r = (int) (((c >> 16) & 0xFF) * 0.7), g = (int) (((c >> 8) & 0xFF) * 0.7), bl = (int) ((c & 0xFF) * 0.7);
            c = (c & 0xFF000000) | (r << 16) | (g << 8) | bl;
        }
        for (AABB box : b) ESPRenderer.drawFilledBox(pose, box, c, cam, false);
    }
}
