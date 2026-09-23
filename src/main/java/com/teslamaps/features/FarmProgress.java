package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.PlayerHeadRenderer;
import com.teslamaps.render.gui.TeslaGui;
import com.teslamaps.screen.AppleColors;
import com.teslamaps.utils.AnimClock;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.SoundOptions;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shows how far along the current farming lane the player is.
 * <p>
 * The lane is two marked points; the bar is laid out spatially, so its left edge
 * is always the lower coordinate. The fill grows from whichever end the current
 * pass started at, which means it empties and flips side by itself when the
 * player turns around for the next row — no explicit reset needed.
 */
public class FarmProgress {

    public static void register() {
        Feature.of("FarmLaneCorners").section("Garden").when(() -> TeslaMapsConfig.get().farmLaneCorners)
                .world(FarmProgress::renderWorld);
    }

    private static final float DIRECTION_DEADZONE = 0.05f;
    private static final float WARN_HYSTERESIS = 2.0f;

    private static double lastPos = Double.NaN;
    private static int direction = 1;
    private static float shownFraction = Float.NaN;

    private static String currentCrop = null;
    private static long lastNoLaneHint = 0L;

    private static TeslaMapsConfig.FarmLane cropLane() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.farmLanePerCrop || currentCrop == null || config.farmLanes == null) return null;
        return config.farmLanes.get(currentCrop);
    }

    public static boolean hasLane() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (config.farmLanePerCrop) return cropLane() != null;
        return config.farmLaneSet;
    }

    /** True when the lane runs along X rather than Z. */
    private static boolean alongX() {
        TeslaMapsConfig.FarmLane lane = cropLane();
        if (lane != null) return lane.alongX;
        TeslaMapsConfig config = TeslaMapsConfig.get();
        return Math.abs(config.farmX2 - config.farmX1) >= Math.abs(config.farmZ2 - config.farmZ1);
    }

    private static double laneMin() {
        TeslaMapsConfig.FarmLane lane = cropLane();
        if (lane != null) return lane.min;
        TeslaMapsConfig config = TeslaMapsConfig.get();
        return alongX() ? Math.min(config.farmX1, config.farmX2) : Math.min(config.farmZ1, config.farmZ2);
    }

    private static double laneMax() {
        TeslaMapsConfig.FarmLane lane = cropLane();
        if (lane != null) return lane.max;
        TeslaMapsConfig config = TeslaMapsConfig.get();
        return alongX() ? Math.max(config.farmX1, config.farmX2) : Math.max(config.farmZ1, config.farmZ2);
    }

    /** Crop key of a broken block, sunflower/moonflower and mushroom parts merged. */
    private static String cropKey(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path.contains("mushroom")) return "mushroom";
        if (path.equals("sunflower") || path.contains("moonflower")) return "sunflower";
        if (path.equals("carved_pumpkin")) return "pumpkin";
        if (path.equals("hay_block")) return "wheat";
        return path;
    }

    private static String cropName(String key) {
        StringBuilder sb = new StringBuilder();
        for (String part : key.split("_")) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    public static void onCropBreak(BlockState state) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!detecting && !(config.farmBar && config.farmLanePerCrop)) return;
        if (!FarmingSensitivity.isFarmingTool(mc.player.getMainHandItem())) return;
        String key = cropKey(state);

        if (detecting) detect(key, mc.player.position());

        if (!config.farmLanePerCrop || !config.farmBar) return;
        if (!key.equals(currentCrop)) {
            currentCrop = key;
            lastPos = Double.NaN;
            shownFraction = Float.NaN;
            resetSpeed();
        }
        if (cropLane() == null && !detecting) {
            long now = System.currentTimeMillis();
            if (now - lastNoLaneHint < 30_000L) return;
            lastNoLaneHint = now;
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fNo §e" + cropName(key)
                    + "§f lane yet · §e/tmap farm detect§f or set both lane points"));
        }
    }

    // Lane detection, ported from SkyHanni's FarmingLaneCreator: farm two rows.
    private static boolean detecting = false;
    private static Vec3 detectStart = null;
    private static Vec3 detectLast = null;
    private static Vec3 detectEnd = null;
    private static String detectCrop = null;
    private static double detectMax = 0;

    public static void toggleDetection() {
        detecting = !detecting;
        resetDetection();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(detecting
                ? "§b[TeslaMaps] §fLane detection on. Farm two rows to find the lane ends."
                : "§b[TeslaMaps] §fLane detection stopped."));
    }

    private static void resetDetection() {
        detectStart = null;
        detectLast = null;
        detectEnd = null;
        detectCrop = null;
        detectMax = 0;
    }

    private static void detect(String crop, Vec3 here) {
        Minecraft mc = Minecraft.getInstance();
        if (detectLast == null) {
            detectStart = here;
            detectLast = here;
            detectCrop = crop;
            detectMax = 0;
            return;
        }
        if (!crop.equals(detectCrop)) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §cDifferent crop broken, lane detection stopped"));
            detecting = false;
            resetDetection();
            return;
        }
        if (detectLast.distanceTo(here) < 0.5) return;
        detectLast = here;
        double distance = detectStart.distanceTo(here);
        if (distance > detectMax) {
            detectMax = distance;
            detectEnd = null;
        } else if (detectEnd == null) {
            detectEnd = here;
        } else if (detectEnd.distanceTo(here) > 2) {
            saveLane(detectStart, detectEnd, crop);
        }
    }

    private static void saveLane(Vec3 a, Vec3 b, String crop) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        config.farmX1 = a.x;
        config.farmZ1 = a.z;
        config.farmX2 = b.x;
        config.farmZ2 = b.z;
        config.farmLaneSet = true;
        if (config.farmLanePerCrop) {
            config.farmLanes.put(crop, laneOf(a.x, a.z, b.x, b.z));
            currentCrop = crop;
        }
        TeslaMapsConfig.save();
        detecting = false;
        resetDetection();
        lastPos = Double.NaN;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(String.format(
                "§b[TeslaMaps] §e%s§f lane saved, length §e%.1f§f blocks", cropName(crop), laneMax() - laneMin())));
    }

    private static TeslaMapsConfig.FarmLane laneOf(double x1, double z1, double x2, double z2) {
        TeslaMapsConfig.FarmLane lane = new TeslaMapsConfig.FarmLane();
        lane.alongX = Math.abs(x2 - x1) >= Math.abs(z2 - z1);
        lane.min = lane.alongX ? Math.min(x1, x2) : Math.min(z1, z2);
        lane.max = lane.alongX ? Math.max(x1, x2) : Math.max(z1, z2);
        return lane;
    }

    // Time left from the speed along the lane over the last second.
    private static final int SPEED_TICKS = 20;
    private static final double[] speedRing = new double[SPEED_TICKS];
    private static int speedHead = 0;
    private static int speedSamples = 0;
    private static String timeText = "";
    private static int timeColor = 0xFFAAAAAA;
    private static double secondsLeft = -1;
    private static boolean titledThisPass = false;

    private static void resetSpeed() {
        speedHead = 0;
        speedSamples = 0;
        secondsLeft = -1;
        timeText = "";
    }

    private static void speedTick(double pos, double left) {
        speedRing[speedHead] = pos;
        speedHead = (speedHead + 1) % SPEED_TICKS;
        if (speedSamples < SPEED_TICKS) speedSamples++;
        secondsLeft = -1;
        if (speedSamples < SPEED_TICKS) {
            timeText = "…";
            timeColor = com.teslamaps.render.gui.HudStyle.dim();
            return;
        }
        double speed = Math.abs(pos - speedRing[speedHead]) * 20.0 / (SPEED_TICKS - 1);
        if (speed < 0.05) {
            timeText = "Paused";
            timeColor = 0xFFFFFF55;
        } else if (speed < 1) {
            timeText = "Too slow";
            timeColor = AppleColors.ACCENT_RED;
        } else {
            secondsLeft = left / speed;
            timeText = secondsLeft < 20 ? String.format("%.1fs", secondsLeft) : (int) secondsLeft + "s";
            timeColor = 0xFF55FFFF;
        }
    }

    private static double playerPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        return alongX() ? mc.player.getX() : mc.player.getZ();
    }

    public static void setPoint(int which) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (which == 1) {
            config.farmX1 = mc.player.getX();
            config.farmZ1 = mc.player.getZ();
        } else {
            config.farmX2 = mc.player.getX();
            config.farmZ2 = mc.player.getZ();
        }
        config.farmLaneSet = Math.abs(config.farmX2 - config.farmX1) > 1
                || Math.abs(config.farmZ2 - config.farmZ1) > 1;
        if (config.farmLanePerCrop && config.farmLaneSet && currentCrop != null) {
            config.farmLanes.put(currentCrop, laneOf(config.farmX1, config.farmZ1, config.farmX2, config.farmZ2));
        }
        TeslaMapsConfig.save();

        String axis = alongX() ? "X" : "Z";
        mc.player.sendSystemMessage(Component.literal(String.format(
                "§b[TeslaMaps] §fPoint %d set. Lane along §e%s§f, length §e%.1f§f blocks",
                which, axis, laneMax() - laneMin())));
    }

    public static void clear() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        config.farmLaneSet = false;
        if (config.farmLanePerCrop && currentCrop != null) config.farmLanes.remove(currentCrop);
        TeslaMapsConfig.save();
        lastPos = Double.NaN;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fFarm lane cleared"));
        }
    }

    public static void tick() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!SkyblockUtils.isInGarden()) {
            currentCrop = null;
            detecting = false;
        }
        if (!config.farmBar || !hasLane() || !SkyblockUtils.isInGarden()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double pos = playerPos();
        if (!Double.isNaN(lastPos)) {
            double delta = pos - lastPos;
            if (delta > DIRECTION_DEADZONE && direction != 1) {
                direction = 1;
                lastBeep = 0L;
                titledThisPass = false;
                resetSpeed();
            } else if (delta < -DIRECTION_DEADZONE && direction != -1) {
                direction = -1;
                lastBeep = 0L;
                titledThisPass = false;
                resetSpeed();
            }
        }
        lastPos = pos;

        double target = direction > 0 ? laneMax() : laneMin();
        double left = Math.abs(target - pos);

        if (config.farmLaneTimeLeft || config.farmLaneWarnSeconds > 0) speedTick(pos, left);
        if (config.farmLaneWarnSeconds > 0 && !titledThisPass && secondsLeft >= 0
                && secondsLeft <= config.farmLaneWarnSeconds && mc.gui != null) {
            titledThisPass = true;
            mc.gui.setTimes(0, config.farmLaneWarnSeconds * 20, 5);
            mc.gui.setTitle(Component.literal("§eLane Switch"));
            mc.gui.setSubtitle(Component.literal(""));
        }

        if (left <= config.farmWarnDistance) {
            beep(config, left);
        } else if (left > config.farmWarnDistance + WARN_HYSTERESIS) {
            lastBeep = 0L;
        }
    }

    private static long lastBeep = 0L;

    private static final long BEEP_SLOWEST = 700L;
    private static final long BEEP_FASTEST = 130L;

    /**
     * Repeats the warning inside the marked distance, faster and higher the closer
     * the end gets. A single beep at the threshold is easy to miss mid-row; the
     * rising rate says how much lane is left without having to look at the bar.
     */
    private static void beep(TeslaMapsConfig config, double left) {
        float closeness = config.farmWarnDistance <= 0 ? 1f
                : 1f - (float) Math.clamp(left / config.farmWarnDistance, 0, 1);

        long interval = (long) (BEEP_SLOWEST - (BEEP_SLOWEST - BEEP_FASTEST) * closeness);
        long now = System.currentTimeMillis();
        if (now - lastBeep < interval) return;
        lastBeep = now;

        var sound = SoundOptions.resolve(config.farmWarnSound);
        if (sound == null) return;
        LoudSound.play(sound, config.farmWarnVolume * (0.55f + 0.45f * closeness),
                1.0f + closeness * 0.7f);
    }

    public static void renderWorld(PoseStack pose, Vec3 cam) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.farmBar || !hasLane() || !SkyblockUtils.isInGarden()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean x = alongX();
        double y = Math.min(mc.player.getY(), 76.0);
        for (int i = 0; i < 2; i++) {
            double v = i == 0 ? laneMin() : laneMax();
            double px = x ? v : mc.player.getX();
            double pz = x ? mc.player.getZ() : v;
            BlockPos bp = BlockPos.containing(px, y, pz);
            ESPRenderer.drawBeaconBeam(pose, bp, 0xCCFFFF55, cam);
            ESPRenderer.drawFilledBox(pose, new AABB(bp), 0x55FFFF55, cam, true);
            ESPRenderer.drawText(pose, "§eLane Corner", Vec3.atCenterOf(bp).add(0, 1, 0), 1.5f, cam);
        }
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.farmBar || !hasLane() || !SkyblockUtils.isInGarden()) return;
        draw(context, Minecraft.getInstance(), config.farmBarX, config.farmBarY, config.farmBarScale);
    }

    // Panel geometry, in unscaled units.
    private static final int PANEL_WIDTH = 136;
    private static final int PANEL_HEIGHT = 40;
    private static final int PADDING = 10;
    private static final int TRACK_HEIGHT = 6;
    private static final int TRACK_Y = 24;
    private static final int HEAD_SIZE = 12;

    public static int barWidth(float scale) {
        return (int) (PANEL_WIDTH * scale);
    }

    public static int barHeight(float scale) {
        return (int) (PANEL_HEIGHT * scale);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        double min = laneMin();
        double max = laneMax();
        double length = Math.max(max - min, 0.001);
        double pos = Math.clamp(playerPos(), min, max);

        float fraction = (float) ((pos - min) / length);
        double target = direction > 0 ? max : min;
        double left = Math.abs(target - pos);
        boolean warning = left <= TeslaMapsConfig.get().farmWarnDistance;

        // Ease the marker instead of snapping it to the raw position every frame.
        // A big jump (teleport, new lane) is taken instantly, otherwise the head
        // would slide across the whole bar.
        AnimClock.update();
        if (Float.isNaN(shownFraction) || Math.abs(fraction - shownFraction) > 0.4f) {
            shownFraction = fraction;
        } else {
            shownFraction += (fraction - shownFraction) * AnimClock.lerpFactor(0.3f);
        }

        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(scale, scale);

        com.teslamaps.render.gui.HudStyle.panel(context, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, 6);

        int trackWidth = PANEL_WIDTH - PADDING * 2;
        // Header: label on the left, remaining distance right-aligned.
        context.text(mc.font, Component.literal("Lane"), PADDING, 8,
                com.teslamaps.render.gui.HudStyle.label(), true);
        if (TeslaMapsConfig.get().farmLaneTimeLeft && !timeText.isEmpty()) {
            context.text(mc.font, Component.literal(timeText), PADDING + mc.font.width("Lane") + 6, 8, timeColor, true);
        }

        String remaining = String.format("%.0f", left);
        String unit = "m";
        int unitWidth = mc.font.width(unit);
        int valueWidth = mc.font.width(remaining);
        int rightEdge = PANEL_WIDTH - PADDING;
        context.text(mc.font, Component.literal(remaining),
                rightEdge - unitWidth - valueWidth, 8,
                warning ? AppleColors.ACCENT_RED : AppleColors.TEXT_PRIMARY, true);
        context.text(mc.font, Component.literal(unit), rightEdge - unitWidth, 8,
                com.teslamaps.render.gui.HudStyle.dim(), true);

        // Fill grows from the end this pass began at, so it empties on the turn.
        TeslaGui.progressTrack(context, PADDING, TRACK_Y, trackWidth, TRACK_HEIGHT,
                direction > 0 ? shownFraction : 1.0f - shownFraction, direction > 0,
                warning ? AppleColors.ACCENT_RED : AppleColors.ACCENT_GREEN);

        if (mc.player != null) {
            float headCentre = PADDING + trackWidth * shownFraction;
            float headX = Math.clamp(headCentre - HEAD_SIZE / 2.0f,
                    PADDING - 2, PANEL_WIDTH - PADDING - HEAD_SIZE + 2);
            float headY = TRACK_Y + TRACK_HEIGHT / 2.0f - HEAD_SIZE / 2.0f;
            // Ring behind the head so it stays readable over both track and fill.
            TeslaGui.roundedRect(context, headX - 1, headY - 1, HEAD_SIZE + 2, HEAD_SIZE + 2, 3,
                    TeslaGui.withAlpha(0x000000, 190));
            // The head blit only takes ints, so the sub-pixel part goes into the
            // matrix — otherwise it snaps to whole pixels and looks stepped.
            context.pose().pushMatrix();
            context.pose().translate(headX - (int) headX, headY - (int) headY);
            PlayerHeadRenderer.drawPlayerHead(context, (int) headX, (int) headY, HEAD_SIZE,
                    mc.player.getUUID());
            context.pose().popMatrix();
        }

        context.pose().popMatrix();
    }
}
