package com.teslamaps.dungeon;

// Arrows device ported from Odin's ArrowsDevice.kt (+ plate reset from Skyblocker's TargetPractice.java),
// lights from Skyblocker's LightsOn.java
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class F7Devices {

    private F7Devices() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    // ---- F7 phase (NoammAddons LocationUtils) ----

    /** 1..5 while in the F7/M7 boss, else 0. */
    public static int phase() {
        Minecraft mc = Minecraft.getInstance();
        DungeonFloor f = DungeonManager.getCurrentFloor();
        if (mc.player == null || f == null || f.getLevel() != 7 || !DungeonManager.isInBossArea()) return 0;
        double y = mc.player.getY();
        if (y > 210) return 1;
        if (y > 155) return 2;
        if (y > 100) return 3;
        if (y > 45) return 4;
        return 5;
    }

    // ---- arrows device ----

    private static final List<BlockPos> TARGETS = List.of(
            new BlockPos(68, 130, 50), new BlockPos(66, 130, 50), new BlockPos(64, 130, 50),
            new BlockPos(68, 128, 50), new BlockPos(66, 128, 50), new BlockPos(64, 128, 50),
            new BlockPos(68, 126, 50), new BlockPos(66, 126, 50), new BlockPos(64, 126, 50));
    private static final BlockPos PLATE = new BlockPos(63, 127, 35);
    private static final AABB ARROWS_ROOM = new AABB(20, 100, 30, 89, 151, 51);
    private static final Pattern DEVICE_DONE = Pattern.compile("^(.{1,16}) completed a device! \\((\\d)/(\\d)\\)$");

    private static final Set<BlockPos> marked = new HashSet<>();
    private static BlockPos target = null;
    private static List<Vec3> aims = List.of();
    private static boolean arrowsDone = false;

    // ---- lights device ----

    private static final BlockPos[] LEVERS = {
            new BlockPos(62, 136, 142), new BlockPos(58, 136, 142), new BlockPos(60, 135, 142),
            new BlockPos(60, 134, 142), new BlockPos(62, 133, 142), new BlockPos(58, 133, 142)};
    private static final List<AABB> offLevers = new ArrayList<>();

    private static Object lastLevel = null;

    public static void register() {
        Feature.of("ArrowsDevice").when(() -> cfg().arrowsDeviceSolver)
                .world(F7Devices::renderArrows)
                .serverChat(F7Devices::onChat);
        Feature.of("LightsDevice").when(() -> cfg().lightsDeviceHighlight)
                .tick(F7Devices::tickLights)
                .world(F7Devices::renderLights);
        Feature.of("F7Devices.reset").tick(F7Devices::tickReset);
    }

    private static void tickReset() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            resetArrows();
            arrowsDone = false;
            offLevers.clear();
        }
    }

    public static void resetArrows() {
        marked.clear();
        target = null;
        aims = List.of();
    }

    /** From BlockUpdateMixin, client level, inside a dungeon. */
    public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        TeslaMapsConfig c = cfg();
        if (!c.arrowsDeviceSolver) return;
        if (pos.equals(PLATE) && newState.is(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE)) {
            int power = newState.getValue(BlockStateProperties.POWER);
            boolean wasOff = oldState.is(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE)
                    && oldState.getValue(BlockStateProperties.POWER) == 0;
            if (power < 1 || wasOff) {
                resetArrows();
                if (power >= 1) arrowsDone = false;
            }
            return;
        }
        if (!TARGETS.contains(pos) || phase() != 3) return;
        if (oldState.is(Blocks.EMERALD_BLOCK) && newState.is(Blocks.DYED_TERRACOTTA.blue())) {
            marked.add(pos.immutable());
            if (pos.equals(target)) target = null;
            if (c.arrowsDeviceAim) aims = aimPoints(pos);
        } else if (oldState.is(Blocks.DYED_TERRACOTTA.blue()) && newState.is(Blocks.EMERALD_BLOCK)) {
            marked.remove(pos);
            target = pos.immutable();
            if (c.arrowsDeviceAim) aims = aimPoints(pos);
        }
    }

    private static void onChat(String raw) {
        if (arrowsDone) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ARROWS_ROOM.contains(mc.player.position()) || phase() != 3) return;
        Matcher m = DEVICE_DONE.matcher(StringUtil.strip(raw));
        if (!m.matches() || !m.group(1).equals(mc.player.getName().getString())) return;
        arrowsDone = true;
        resetArrows();
        if (cfg().arrowsDeviceAlert) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §aSharp shooter device complete"));
            mc.gui.hud.setTimes(0, 30, 5);
            mc.gui.hud.setTitle(Component.literal("§aDevice Complete"));
        }
    }

    private static void renderArrows(PoseStack pose, Vec3 cam) {
        if (marked.isEmpty() && target == null) return;
        if (phase() != 3) return;
        TeslaMapsConfig c = cfg();
        int hit = TeslaMapsConfig.parseColor(c.arrowsDeviceHitColor);
        for (BlockPos p : marked) ESPRenderer.drawFilledBox(pose, new AABB(p).inflate(0.01), hit, cam, false);
        BlockPos t = target;
        if (t != null) ESPRenderer.drawFilledBox(pose, new AABB(t).inflate(0.01), TeslaMapsConfig.parseColor(c.arrowsDeviceTargetColor), cam, false);
        if (c.arrowsDeviceAim) {
            List<Vec3> a = aims;
            for (int i = 0; i < a.size(); i++) {
                Vec3 v = a.get(i);
                int col = i == 0 ? 0x8055FF55 : i == 1 ? 0x80FFAA00 : 0x80FF5555;
                ESPRenderer.drawFilledBox(pose, new AABB(v.x - 0.5, v.y - 0.5, v.z - 0.1, v.x + 0.5, v.y + 0.5, v.z + 0.9), col, cam, true);
            }
        }
    }

    private record Aim(Vec3 pos, Set<BlockPos> covers) {}

    private static List<Vec3> aimPoints(BlockPos tgt) {
        List<Aim> green = new ArrayList<>();
        List<Aim> rest = new ArrayList<>();
        for (int i = 0; i < TARGETS.size(); i++) {
            for (int j = i + 1; j < TARGETS.size(); j++) {
                BlockPos a = TARGETS.get(i), b = TARGETS.get(j);
                if (Math.abs(a.getX() - b.getX()) != 2 || a.getY() != b.getY() || a.getZ() != b.getZ()) continue;
                Set<BlockPos> cov = new HashSet<>();
                if (!marked.contains(a)) cov.add(a);
                if (!marked.contains(b)) cov.add(b);
                if (cov.isEmpty()) continue;
                Aim aim = new Aim(new Vec3((a.getX() + b.getX()) / 2.0 + 0.5, a.getY() + 0.5, a.getZ() + 0.5), cov);
                (a.equals(tgt) || b.equals(tgt) ? green : rest).add(aim);
            }
        }
        Aim first = null;
        for (Aim a : green) if (first == null || a.covers.size() > first.covers.size()) first = a;
        if (first == null) return List.of();
        List<Aim> out = new ArrayList<>();
        out.add(first);
        Set<BlockPos> covered = new HashSet<>(first.covers);
        for (int n = 0; n < 2; n++) {
            Aim best = null;
            int bestNew = -1, bestSize = -1;
            double bestDist = Double.MAX_VALUE;
            Vec3 last = out.get(out.size() - 1).pos;
            for (Aim a : rest) {
                if (out.contains(a)) continue;
                int fresh = 0;
                for (BlockPos p : a.covers) if (!covered.contains(p)) fresh++;
                double d = last.distanceTo(a.pos);
                if (fresh > bestNew || (fresh == bestNew && (a.covers.size() > bestSize
                        || (a.covers.size() == bestSize && d < bestDist)))) {
                    best = a; bestNew = fresh; bestSize = a.covers.size(); bestDist = d;
                }
            }
            if (best == null) break;
            out.add(best);
            covered.addAll(best.covers);
        }
        List<Vec3> pts = new ArrayList<>();
        for (Aim a : out) pts.add(a.pos);
        return pts;
    }

    // ---- lights ----

    private static void tickLights() {
        offLevers.clear();
        if (phase() != 3) return;
        Minecraft mc = Minecraft.getInstance();
        for (BlockPos p : LEVERS) {
            BlockState s = mc.level.getBlockState(p);
            if (!s.is(Blocks.LEVER) || s.getValue(BlockStateProperties.POWERED)) continue;
            VoxelShape shape = s.getShape(mc.level, p);
            offLevers.add(shape.isEmpty() ? new AABB(p) : shape.bounds().move(p));
        }
    }

    private static void renderLights(PoseStack pose, Vec3 cam) {
        if (offLevers.isEmpty()) return;
        int col = TeslaMapsConfig.parseColor(cfg().lightsDeviceColor);
        for (AABB b : offLevers) ESPRenderer.drawFilledBox(pose, b, col, cam, false);
    }
}
