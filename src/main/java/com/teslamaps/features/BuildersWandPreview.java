package com.teslamaps.features;

// Ported from Skyblocker's BuildersWandPreview.java, zapper from NEU's CustomItemEffects.java
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
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

public final class BuildersWandPreview {

    private static final int MAX_WAND = 241;
    private static final int MAX_ZAPPER = 164;
    private static final int PLOT_SIZE = 96, PLOT_OFFSET = 48;
    private static final int REMOVE = 0x60FF3030;

    private static List<AABB> boxes = List.of();
    private static boolean removing;
    private static int ticks;
    private static boolean island, garden;
    private static boolean attackWasDown;
    private static long lastUndo;

    private BuildersWandPreview() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("BuildersWandPreview").section("Garden").when(() -> cfg().buildersWandPreview)
                .tick(BuildersWandPreview::tick)
                .world(BuildersWandPreview::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Building Tools");
                    b.toggle("Builder's Wand Preview", () -> c.buildersWandPreview, v -> c.buildersWandPreview = v)
                            .desc("Private Island / Garden: boxes where the wand will place blocks");
                    b.toggle("  Builder's Ruler Preview", () -> c.buildersRulerPreview, v -> c.buildersRulerPreview = v)
                            .desc("Garden: the ruler line, red boxes for the blocks it removes while sneaking");
                    b.toggle("  Block Zapper Preview", () -> c.blockZapperPreview, v -> c.blockZapperPreview = v)
                            .desc("Private Island: red boxes for the blocks the zapper would remove");
                    b.toggle("  Left-Click Zapper = /undozap", () -> c.blockZapperUndo, v -> c.blockZapperUndo = v);
                    b.color("  Preview Color", () -> c.buildersWandColor, v -> c.buildersWandColor = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        if (++ticks % 20 == 0) {
            garden = SkyblockUtils.isInGarden();
            island = !garden && SkyblockUtils.isInArea("Private Island");
        }
        String id = (island || garden) ? ItemUtil.skyblockId(mc.player.getMainHandItem()) : "";
        undo(mc, c, id);
        if (ticks % 2 != 0) return;
        List<AABB> out = new ArrayList<>();
        removing = false;
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && mc.level != null) {
            switch (id) {
                case "BUILDERS_WAND" -> wand(mc.level, hit, out);
                case "BUILDERS_RULER" -> { if (garden && c.buildersRulerPreview) ruler(mc, hit, out); }
                case "BLOCK_ZAPPER" -> { if (island && c.blockZapperPreview) { removing = true; zapper(mc.level, hit.getBlockPos(), out); } }
                default -> {}
            }
        }
        boxes = out;
    }

    private static void undo(Minecraft mc, TeslaMapsConfig c, String id) {
        boolean down = mc.gui.screen() == null && mc.options.keyAttack.isDown();
        boolean edge = down && !attackWasDown;
        attackWasDown = down;
        if (!edge || !c.blockZapperUndo || !island || !"BLOCK_ZAPPER".equals(id)) return;
        long now = System.currentTimeMillis();
        if (now - lastUndo < 1000 || mc.getConnection() == null) return;
        lastUndo = now;
        mc.getConnection().sendCommand("undozap");
    }

    private static void wand(Level level, BlockHitResult hit, List<AABB> out) {
        BlockPos hitPos = hit.getBlockPos();
        Direction side = hit.getDirection();
        if (!level.getBlockState(hitPos.relative(side)).isAir()) return;
        BlockState state = level.getBlockState(hitPos);
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        q.add(hitPos);
        visited.add(hitPos);
        while (!q.isEmpty()) {
            BlockPos cur = q.poll();
            for (Direction dir : Direction.values()) {
                if (dir == side || dir == side.getOpposite()) continue;
                BlockPos n = cur.relative(dir);
                if (visited.contains(n) || !level.getBlockState(n).equals(state)) continue;
                if (!level.getBlockState(n.relative(side)).isAir()) continue;
                visited.add(n);
                q.add(n);
            }
            if (visited.size() > MAX_WAND) return;
        }
        for (BlockPos p : visited) out.add(new AABB(p.relative(side)));
    }

    private static void ruler(Minecraft mc, BlockHitResult hit, List<AABB> out) {
        boolean sneaking = mc.player.isShiftKeyDown();
        BlockPos start = hit.getBlockPos();
        Block startBlock = Blocks.AIR;
        if (!sneaking) start = start.relative(hit.getDirection());
        else startBlock = mc.level.getBlockState(start).getBlock();
        Direction dir = hit.getDirection().getOpposite();
        if (dir == Direction.UP || dir == Direction.DOWN || !sneaking) dir = mc.player.getDirection();
        removing = sneaking;
        BlockPos.MutableBlockPos pos = start.mutable();
        for (int i = 0; i < MAX_WAND; i++) {
            if (!inPlot(start, pos)) break;
            BlockState st = mc.level.getBlockState(pos);
            if (sneaking ? !st.is(startBlock) : !st.isAir()) break;
            out.add(new AABB(pos.immutable()));
            pos.move(dir);
        }
    }

    private static boolean inPlot(BlockPos a, BlockPos b) {
        return Math.floorDiv(a.getX() + PLOT_OFFSET, PLOT_SIZE) == Math.floorDiv(b.getX() + PLOT_OFFSET, PLOT_SIZE)
                && Math.floorDiv(a.getZ() + PLOT_OFFSET, PLOT_SIZE) == Math.floorDiv(b.getZ() + PLOT_OFFSET, PLOT_SIZE);
    }

    private static boolean zappable(Level level, BlockPos pos, Block block) {
        BlockState st = level.getBlockState(pos);
        if (!st.is(block) || st.hasBlockEntity() || isCrop(st)) return false;
        return !isCrop(level.getBlockState(pos.above()));
    }

    private static boolean isCrop(BlockState st) {
        return st.getBlock() instanceof CropBlock || st.is(Blocks.SUGAR_CANE) || st.is(Blocks.CACTUS)
                || st.is(Blocks.NETHER_WART) || st.is(Blocks.PUMPKIN_STEM) || st.is(Blocks.MELON_STEM)
                || st.is(Blocks.COCOA) || st.is(Blocks.BEDROCK) || st.is(Blocks.BARRIER);
    }

    private static void zapper(Level level, BlockPos start, List<AABB> out) {
        BlockState first = level.getBlockState(start);
        if (first.isAir()) return;
        Block block = first.getBlock();
        if (!zappable(level, start, block)) return;
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        q.add(start);
        seen.add(start);
        while (!q.isEmpty() && seen.size() < MAX_ZAPPER) {
            BlockPos cur = q.poll();
            for (Direction dir : Direction.values()) {
                BlockPos n = cur.relative(dir);
                if (seen.contains(n) || !zappable(level, n, block)) continue;
                seen.add(n);
                q.add(n);
                if (seen.size() >= MAX_ZAPPER) break;
            }
        }
        for (BlockPos p : seen) out.add(new AABB(p));
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<AABB> list = boxes;
        if (list.isEmpty()) return;
        int color = removing ? REMOVE : TeslaMapsConfig.parseColor(cfg().buildersWandColor);
        int line = color | 0xFF000000;
        ESPRenderer.beginBoxBatch(pose, cam, true, false);
        for (AABB b : list) ESPRenderer.batchBox(b, color, 1f);
        ESPRenderer.endBoxBatch();
        ESPRenderer.beginBoxBatch(pose, cam, false, false);
        for (AABB b : list) ESPRenderer.batchBox(b, line, 1.5f);
        ESPRenderer.endBoxBatch();
    }
}
