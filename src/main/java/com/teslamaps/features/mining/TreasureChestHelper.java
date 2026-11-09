package com.teslamaps.features.mining;

// Ported from Skyblocker's CrystalsChestHighlighter (NEU / Skytils do the same); timer after SkyHanni's PowderChestTimer
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Boxes the treasure chest you just uncovered in the Hollows and marks the lockpick spot. */
public final class TreasureChestHelper {

    private static final String SPAWN = "You uncovered a treasure chest!";
    private static final long PARTICLE_LIFE = 250;

    private static final long LIFE = 60_000;
    private static final String[] SAMPLE = {"§a42s §8(§e3 §bchests§8)"};

    private static final Map<BlockPos, Long> chests = new LinkedHashMap<>();
    private static final List<BlockPos> ordered = new ArrayList<>();
    private static final List<String> labels = new ArrayList<>();
    private static final List<Integer> colors = new ArrayList<>();
    private static String hudLine;
    private static HudEditElement hud;
    private static final List<double[]> particles = new ArrayList<>();
    private static int waiting;
    private static int picks, needed;
    private static boolean on;
    private static Object lastLevel;

    private TreasureChestHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("treasureChestTimer", "Treasure Chest Timer").ctx(HudEditElement.HudCtx.MINING).section("Mining")
                .enabled(() -> cfg().treasureChestHelper && cfg().treasureChestTimer)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE[0]), () -> 9)
                .preview((g, mc, x, y, s) -> drawText(g, mc, x, y, s, SAMPLE[0]));
        Feature.of("TreasureChestHelper").section("Mining").when(() -> cfg().treasureChestHelper)
                .tick(TreasureChestHelper::tick)
                .serverChat(TreasureChestHelper::onChat)
                .world(TreasureChestHelper::render)
                .hud("treasurechesttimer", TreasureChestHelper::renderHud)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Treasure Chest Helper", () -> c.treasureChestHelper, v -> c.treasureChestHelper = v)
                            .desc("Boxes the treasure chest you just uncovered in the Crystal Hollows; it goes away once opened.")
                            .tags("treasure", "chest", "lockpick", "crystal hollows", "gemstone");
                    b.toggle("  Lockpick Helper", () -> c.treasureChestLockpick, v -> c.treasureChestLockpick = v)
                            .desc("Marks where the lock particle is and counts the picks while you look at the chest");
                    b.color("  Chest Color", () -> c.colorTreasureChest, v -> c.colorTreasureChest = v);
                    b.toggle("  Chest Timer", () -> c.treasureChestTimer, v -> c.treasureChestTimer = v)
                            .desc("Keeps every chest you uncover for its 60 seconds: time left above it, lines to the nearest ones and a count HUD (/tmap gui)")
                            .tags("powder", "chest timer", "treasure");
                    b.toggle("    Color By Time Left", () -> c.treasureChestTimerColor, v -> c.treasureChestTimerColor = v)
                            .desc("Green to red instead of the chest color");
                    b.slider("    Lines To Nearest Chests", 0, 5, () -> (float) c.treasureChestTimerLines,
                            v -> c.treasureChestTimerLines = Math.round(v), 0);
                    b.toggle("Mute Treasure Chest Sounds", () -> c.muteTreasureChest, v -> c.muteTreasureChest = v)
                            .desc("Crystal Hollows only; real level-ups and other chests stay audible")
                            .tags("mute", "sound", "treasure", "chest", "levelup", "jingle");
                    b.toggle("  Discover Jingle", () -> c.muteTreasureChestDiscover, v -> c.muteTreasureChestDiscover = v);
                    b.toggle("  Chest Open Sound", () -> c.muteTreasureChestOpen, v -> c.muteTreasureChestOpen = v);
                });
    }

    /** From BlockUpdateMixin, main thread. */
    public static boolean listening() {
        return on;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        on = SkyblockUtils.isInArea("Crystal Hollows");
        ordered.clear();
        labels.clear();
        colors.clear();
        hudLine = null;
        if (!on || chests.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean expire = cfg().treasureChestTimer;
        chests.entrySet().removeIf(e -> !mc.level.getBlockState(e.getKey()).is(Blocks.CHEST) || (expire && now - e.getValue() > LIFE));
        if (!cfg().treasureChestTimer || chests.isEmpty()) return;
        ordered.addAll(chests.keySet());
        Vec3 me = mc.player.position();
        ordered.sort((a, b) -> Double.compare(a.distToCenterSqr(me), b.distToCenterSqr(me)));
        long soonest = Long.MAX_VALUE;
        for (BlockPos p : ordered) {
            long left = LIFE - (now - chests.get(p));
            soonest = Math.min(soonest, left);
            labels.add(code(left) + (left / 1000) + "s");
            colors.add(timeColor(left));
        }
        int n = chests.size();
        hudLine = code(soonest) + (soonest / 1000) + "s §8(§e" + n + " §b" + (n == 1 ? "chest" : "chests") + "§8)";
    }

    private static int timeColor(long left) {
        double r = Math.max(0, Math.min(1, left / (double) LIFE));
        return 0xFF000000 | ((int) (255 * (1 - r)) << 16) | ((int) (255 * r) << 8);
    }

    private static String code(long left) {
        return left > 30_000 ? "§a" : left > 15_000 ? "§6" : left > 5_000 ? "§c" : "§4";
    }

    private static void reset() {
        chests.clear();
        ordered.clear();
        labels.clear();
        colors.clear();
        hudLine = null;
        particles.clear();
        waiting = 0;
        picks = 0;
    }

    private static void onChat(String text) {
        if (on && StringUtil.stripTrim(text).equals(SPAWN)) waiting++;
    }

    public static void onBlockUpdate(BlockPos pos, BlockState newState) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.isSameThread() || !active()) return;
        if (waiting > 0 && newState.is(Blocks.CHEST)) {
            if (pos.distToCenterSqr(mc.player.position()) > 100) return;
            chests.put(pos.immutable(), System.currentTimeMillis());
            picks = 0;
            waiting--;
        } else if (newState.isAir() && chests.remove(pos) != null) {
            picks = 0;
        }
    }

    private static boolean active() {
        TeslaMapsConfig c = cfg();
        return on && c.treasureChestHelper && c.section("Mining");
    }

    public static void onParticle(ClientboundLevelParticlesPacket packet) {
        if (!Minecraft.getInstance().isSameThread() || chests.isEmpty() || !active() || !cfg().treasureChestLockpick) return;
        if (packet.particle().getType() != ParticleTypes.CRIT) return;
        particles.add(new double[]{packet.x(), packet.y(), packet.z(), System.currentTimeMillis()});
        if (particles.size() > 64) particles.remove(0);
    }

    public static void onSound(ClientboundSoundPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || mc.player == null || chests.isEmpty() || !active()) return;
        SoundEvent sound = packet.getSound().value();
        if (sound.location().equals(SoundEvents.EXPERIENCE_ORB_PICKUP.location()) && packet.getPitch() == 1f) {
            Vec3 eye = mc.player.getEyePosition(0);
            Vec3 end = eye.add(mc.player.getViewVector(0).scale(mc.player.blockInteractionRange()));
            BlockHitResult hit = mc.player.level().isBlockInLine(new ClipBlockStateContext(eye, end, s -> s.is(Blocks.CHEST)));
            if (hit.getType() != HitResult.Type.MISS) {
                picks++;
                particles.clear();
            }
        } else if (sound.location().equals(SoundEvents.VILLAGER_NO.location())) {
            picks = 0;
            particles.clear();
        } else if (sound.location().equals(SoundEvents.CHEST_OPEN.location())) {
            needed = Math.min(picks, 5);
            picks = 0;
            particles.clear();
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!on || chests.isEmpty()) return;
        int color = TeslaMapsConfig.parseColor(cfg().colorTreasureChest) | 0xFF000000;
        boolean timer = cfg().treasureChestTimer && ordered.size() == labels.size();
        if (timer && !ordered.isEmpty()) {
            boolean byTime = cfg().treasureChestTimerColor;
            for (int i = 0; i < ordered.size(); i++) {
                BlockPos p = ordered.get(i);
                ESPRenderer.drawBoxOutline(pose, AABB.ofSize(Vec3.atCenterOf(p).subtract(0, 0.0625, 0), 0.885, 0.885, 0.885),
                        byTime ? colors.get(i) : color, 3f, cam, false);
                WaypointSet.drawLabel(pose, cam, labels.get(i), Vec3.atCenterOf(p).add(0, 0.9, 0), 0.8f, false);
            }
            int lines = Math.min(cfg().treasureChestTimerLines, ordered.size());
            for (int i = 0; i < lines; i++) {
                int lc = byTime ? colors.get(i) : color;
                Vec3 to = Vec3.atCenterOf(ordered.get(i));
                if (i == 0) ESPRenderer.drawTracerFromCamera(pose, to, lc, cam, false, 3f);
                else ESPRenderer.drawLine(pose, Vec3.atCenterOf(ordered.get(i - 1)), to, lc, 3f, cam, false);
            }
        } else {
            for (BlockPos p : chests.keySet()) {
                ESPRenderer.drawBoxOutline(pose, AABB.ofSize(Vec3.atCenterOf(p).subtract(0, 0.0625, 0), 0.885, 0.885, 0.885),
                        color, 3f, cam, false);
            }
        }
        if (!cfg().treasureChestLockpick) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.hitResult instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK || !chests.containsKey(bh.getBlockPos())) return;
        Vec3 c = Vec3.atCenterOf(bh.getBlockPos());
        long now = System.currentTimeMillis();
        double sx = 0, sy = 0, sz = 0;
        int n = 0;
        for (int i = 0; i < particles.size(); i++) {
            double[] pt = particles.get(i);
            if (now - pt[3] > PARTICLE_LIFE) continue;
            double dx = pt[0] - c.x, dy = pt[1] - c.y, dz = pt[2] - c.z;
            if (dx * dx + dy * dy + dz * dz > 0.64) continue;
            sx += pt[0]; sy += pt[1]; sz += pt[2];
            n++;
        }
        if (n > 0) {
            Vec3 spot = new Vec3(sx / n, sy / n, sz / n);
            ESPRenderer.drawFilledBox(pose, AABB.ofSize(spot, 0.1, 0.1, 0.1), color, cam, false);
        }
        if (needed > 0) {
            WaypointSet.drawLabel(pose, cam, "§e" + Math.min(picks, needed) + "/" + needed, c.add(0, 0.8, 0), 0.8f, false);
        }
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        String line = hudLine;
        if (line == null || hud == null || !on || !cfg().treasureChestTimer) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        drawText(g, mc, hud.x(), hud.y(), hud.scale(), line);
    }

    private static void drawText(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.text(mc.font, text, 0, 0, 0xFFFFFFFF);
        pose.popMatrix();
    }
}
