package com.teslamaps.features.rift;

// Ported from SkyHanni's LivingCaveSnakeFeatures.kt / LivingCaveSnake.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LivingCaveSnakes {

    private static final String PUNGI = "FROZEN_WATER_PUNGI";
    private static final Set<String> PICKAXES = Set.of("SELF_RECURSIVE_PICKAXE", "ANTI_SENTIENT_PICKAXE", "EON_PICKAXE", "CHRONO_PICKAXE");

    private enum State {
        SPAWNING(0x55FFFF, "§bSpawning snake"),
        ACTIVE(0xFFFF55, "§eActive snake"),
        NOT_TOUCHING_AIR(0xFF5555, "§cNot touching air snake"),
        CALM(0x55FF55, "§aCalm snake");

        final int rgb;
        final String label;

        State(int rgb, String label) { this.rgb = rgb; this.label = label; }
    }

    private enum Role { BREAK, CALM }

    private static final class Snake {
        final List<BlockPos> blocks = new ArrayList<>();
        State state = State.SPAWNING;
        long lastAdd, lastRemove, invalidHeadSince;
        BlockPos lastBroken;
        String sizeLabel = "";

        BlockPos head() { return blocks.get(0); }
        BlockPos tail() { return blocks.get(blocks.size() - 1); }
    }

    private static final List<Snake> snakes = new ArrayList<>();
    private static final Map<BlockPos, Block> original = new HashMap<>();
    private static final List<BlockPos> added = new ArrayList<>();
    private static final List<BlockPos> removed = new ArrayList<>();
    private static Snake selected;
    private static BlockPos lastClicked;
    private static Role role;
    private static boolean bothItems, inCave;
    private static int ticks;
    private static Feature feature;

    private LivingCaveSnakes() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Rift", "Skyblock", true);
        feature = Feature.of("LivingCaveSnakes").section("Rift").when(() -> cfg().caveSnakeHighlight)
                .tick(LivingCaveSnakes::tick)
                .world(LivingCaveSnakes::render)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Living Metal Snakes", () -> c.caveSnakeHighlight, v -> c.caveSnakeHighlight = v)
                            .desc("Living Cave: tracks the Living Metal snakes and colors head and tail by state "
                                    + "(aqua spawning, yellow active, red not touching air, green calm). "
                                    + "Shows while you hold a Pungi or a Rift pickaxe.")
                            .tags("snake", "living", "metal", "cave", "rift", "pungi");
                    b.toggle("  Solo Mode", () -> c.caveSnakeSolo, v -> c.caveSnakeSolo = v)
                            .desc("With pickaxe and Frozen Water Pungi both in the hotbar: only the tail to break is marked.");
                });
    }

    private static boolean on() {
        return inCave && feature != null && feature.active();
    }

    public static boolean listening() {
        return inCave && cfg().caveSnakeHighlight;
    }

    /** From BlockUpdateMixin, main thread. */
    public static void onBlockUpdate(BlockPos pos, BlockState old, BlockState now) {
        if (!on()) return;
        if (now.is(Blocks.LAPIS_BLOCK)) {
            BlockPos p = pos.immutable();
            original.put(p, old.getBlock());
            added.add(p);
        }
        Block orig = original.get(pos);
        if (orig != null && orig == now.getBlock()) {
            BlockPos p = pos.immutable();
            original.remove(p);
            removed.add(p);
        }
    }

    /** From the start-destroy / use-item-on hooks. */
    public static void onClick(BlockPos pos) {
        if (!on()) return;
        lastClicked = pos.immutable();
        for (Snake s : snakes) if (s.blocks.contains(pos)) { selected = s; return; }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (++ticks % 20 == 0) {
            inCave = LivingMetalHelper.livingCave();
            boolean pick = false, pungi = false;
            for (int i = 0; i < 9; i++) {
                String id = ItemUtil.skyblockId(mc.player.getInventory().getItem(i));
                if (PUNGI.equals(id)) pungi = true;
                else if (PICKAXES.contains(id)) pick = true;
            }
            bothItems = pick && pungi;
            if (!inCave) {
                snakes.clear();
                original.clear();
                added.clear();
                removed.clear();
                selected = null;
            }
        }
        String held = ItemUtil.skyblockId(mc.player.getMainHandItem());
        role = PUNGI.equals(held) ? Role.CALM : PICKAXES.contains(held) ? Role.BREAK : null;

        long now = System.currentTimeMillis();
        for (BlockPos p : added) addBlock(p, now);
        added.clear();
        for (BlockPos p : removed) {
            for (int i = snakes.size() - 1; i >= 0; i--) {
                Snake s = snakes.get(i);
                if (s.blocks.contains(p) && removeBlock(s, p, now)) snakes.remove(i);
            }
        }
        removed.clear();

        snakes.removeIf(s -> invalidShape(s) || (s.invalidHeadSince != 0 && now - s.invalidHeadSince > 1000));
        for (Snake s : snakes) tickSnake(mc, s, now);
        if (selected != null && !snakes.contains(selected)) selected = null;
    }

    private static void addBlock(BlockPos p, long now) {
        Snake best = null, bestNotCalm = null;
        double bestD = 3.03, bestNcD = 3.03;
        int found = 0, calm = 0;
        for (Snake s : snakes) {
            if (s.blocks.isEmpty()) continue;
            double d = s.head().distSqr(p);
            if (d >= 3.03) continue;
            found++;
            if (s.state == State.CALM) calm++;
            if (d < bestD) { bestD = d; best = s; }
            if (s.state != State.CALM && d < bestNcD) { bestNcD = d; bestNotCalm = s; }
        }
        Snake target = found > 1 && calm > 0 ? bestNotCalm : best;
        if (target == null) {
            Snake s = new Snake();
            s.blocks.add(p);
            s.sizeLabel = "§b1 blocks";
            snakes.add(s);
            return;
        }
        if (target.blocks.contains(p)) return;
        target.blocks.add(0, p);
        target.lastAdd = now;
        target.invalidHeadSince = 0;
        target.sizeLabel = "§b" + target.blocks.size() + " blocks";
    }

    private static boolean removeBlock(Snake s, BlockPos p, long now) {
        if (s.head().equals(p) && p.equals(lastClicked) && s.blocks.size() > 1) return false;
        s.blocks.remove(p);
        if (s.blocks.isEmpty()) return true;
        if (s.state == State.SPAWNING) s.state = State.ACTIVE;
        s.lastRemove = now;
        s.lastBroken = p;
        s.sizeLabel = "§b" + s.blocks.size() + " blocks";
        return false;
    }

    private static boolean invalidShape(Snake s) {
        if (s.blocks.isEmpty()) return true;
        for (int i = 0; i + 1 < s.blocks.size(); i++) if (s.blocks.get(i).distSqr(s.blocks.get(i + 1)) > 9) return true;
        return false;
    }

    private static void tickSnake(Minecraft mc, Snake s, long now) {
        if (!mc.level.getBlockState(s.head()).is(Blocks.LAPIS_BLOCK)) {
            if (s.invalidHeadSince == 0) s.invalidHeadSince = now;
        } else s.invalidHeadSince = 0;
        if (s.state == State.SPAWNING) return;
        boolean notTouchingAir = false;
        for (BlockPos b : s.blocks) {
            boolean air = false;
            for (Direction d : Direction.values()) {
                if (mc.level.getBlockState(b.relative(d)).isAir()) { air = true; break; }
            }
            if (!air) { notTouchingAir = true; break; }
        }
        s.state = notTouchingAir ? State.NOT_TOUCHING_AIR : now - s.lastAdd > 200 ? State.CALM : State.ACTIVE;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        Role r = role;
        if (r == null || snakes.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean solo = cfg().caveSnakeSolo && bothItems;
        for (Snake s : snakes) {
            if (s.blocks.isEmpty()) continue;
            int size = s.blocks.size();
            boolean sel = s == selected;
            if (solo) {
                if (size > 1) block(pose, cam, tailPos(s, now), 0x0000AA, s, sel);
            } else if (size > 1 && s.state == State.CALM && r == Role.BREAK) {
                block(pose, cam, tailPos(s, now), s.state.rgb, s, sel);
            }
            block(pose, cam, headPos(s, now), s.state.rgb, s, sel);
            boolean headMoving = now - s.lastAdd < 200;
            int edge = 0xFF000000 | s.state.rgb;
            for (BlockPos b : s.blocks) {
                if (headMoving && b == s.head()) continue;
                ESPRenderer.drawBoxOutline(pose, new AABB(b), edge, 2f, cam, false);
            }
        }
    }

    private static Vec3 headPos(Snake s, long now) {
        if (s.blocks.size() < 2) return Vec3.atLowerCornerOf(s.head());
        return lerp(s.blocks.get(1), s.head(), (now - s.lastAdd) / 200.0);
    }

    private static Vec3 tailPos(Snake s, long now) {
        if (s.lastBroken == null) return Vec3.atLowerCornerOf(s.tail());
        return lerp(s.lastBroken, s.tail(), (now - s.lastRemove) / 300.0);
    }

    private static Vec3 lerp(BlockPos a, BlockPos b, double f) {
        f = Math.max(0, Math.min(1, f));
        return new Vec3(a.getX() + (b.getX() - a.getX()) * f, a.getY() + (b.getY() - a.getY()) * f, a.getZ() + (b.getZ() - a.getZ()) * f);
    }

    private static void block(PoseStack pose, Vec3 cam, Vec3 p, int rgb, Snake s, boolean sel) {
        ESPRenderer.drawFilledBox(pose, new AABB(p.x, p.y, p.z, p.x + 1, p.y + 1, p.z + 1), 0xC0000000 | rgb, cam, sel);
        if (!sel) return;
        ESPRenderer.drawText(pose, s.state.label, new Vec3(p.x + 0.5, p.y + 1.5, p.z + 0.5), 1f, cam);
        ESPRenderer.drawText(pose, s.sizeLabel, new Vec3(p.x + 0.5, p.y + 1.2, p.z + 0.5), 1f, cam);
    }
}
