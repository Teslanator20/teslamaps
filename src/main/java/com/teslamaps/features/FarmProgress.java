package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
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

/**
 * Shows how far along the current farming lane the player is.
 * <p>
 * The lane is two marked points; the bar is laid out spatially, so its left edge
 * is always the lower coordinate. The fill grows from whichever end the current
 * pass started at, which means it empties and flips side by itself when the
 * player turns around for the next row — no explicit reset needed.
 */
public class FarmProgress {

    private static final float DIRECTION_DEADZONE = 0.05f;
    private static final float WARN_HYSTERESIS = 2.0f;

    private static double lastPos = Double.NaN;
    private static int direction = 1;
    private static float shownFraction = Float.NaN;

    public static boolean hasLane() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        return config.farmLaneSet;
    }

    /** True when the lane runs along X rather than Z. */
    private static boolean alongX() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        return Math.abs(config.farmX2 - config.farmX1) >= Math.abs(config.farmZ2 - config.farmZ1);
    }

    private static double laneMin() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        return alongX() ? Math.min(config.farmX1, config.farmX2) : Math.min(config.farmZ1, config.farmZ2);
    }

    private static double laneMax() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        return alongX() ? Math.max(config.farmX1, config.farmX2) : Math.max(config.farmZ1, config.farmZ2);
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
        TeslaMapsConfig.save();

        String axis = alongX() ? "X" : "Z";
        mc.player.sendSystemMessage(Component.literal(String.format(
                "§b[TeslaMaps] §fPoint %d set. Lane along §e%s§f, length §e%.1f§f blocks",
                which, axis, laneMax() - laneMin())));
    }

    public static void clear() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        config.farmLaneSet = false;
        TeslaMapsConfig.save();
        lastPos = Double.NaN;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fFarm lane cleared"));
        }
    }

    public static void tick() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.farmBar || !hasLane() || !SkyblockUtils.isInGarden()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double pos = playerPos();
        if (!Double.isNaN(lastPos)) {
            double delta = pos - lastPos;
            if (delta > DIRECTION_DEADZONE && direction != 1) {
                direction = 1;
                lastBeep = 0L;
            } else if (delta < -DIRECTION_DEADZONE && direction != -1) {
                direction = -1;
                lastBeep = 0L;
            }
        }
        lastPos = pos;

        double target = direction > 0 ? laneMax() : laneMin();
        double left = Math.abs(target - pos);

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
