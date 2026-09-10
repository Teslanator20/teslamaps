package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Lets the garden panels be dragged and scaled straight from the inventory,
 * without opening the HUD editor. Only the panels that are visible while the
 * inventory is open take part.
 */
public class HudDragInInventory {

    /** A click on a panel, with the button that made it. */
    public interface ClickAction {
        void onClick(double mouseX, double mouseY, int button);
    }

    private record Panel(String name,
                         Supplier<Boolean> visible,
                         Supplier<int[]> bounds,
                         Consumer<int[]> setPosition,
                         Supplier<Float> scale,
                         Consumer<Float> setScale,
                         ClickAction onClick) {}

    private static final Panel[] PANELS = {
            // width/height come from the last frame the map drew, so it matches whatever
            // scale and grid size is currently on screen
            new Panel("Map",
                    () -> TeslaMapsConfig.get().mapEnabled
                            && com.teslamaps.render.MapRenderer.mapW() > 0,
                    () -> panelBounds(TeslaMapsConfig.get().mapX,
                            TeslaMapsConfig.get().mapY,
                            com.teslamaps.render.MapRenderer.mapW(),
                            com.teslamaps.render.MapRenderer.mapH()),
                    xy -> {
                        TeslaMapsConfig.get().mapX = xy[0];
                        TeslaMapsConfig.get().mapY = xy[1];
                    },
                    () -> TeslaMapsConfig.get().mapScale,
                    value -> TeslaMapsConfig.get().mapScale = value,
                    null),

            new Panel("Invincibility",
                    DungeonTimers::invincibilityVisible,
                    () -> panelBounds(TeslaMapsConfig.get().invincibilityX,
                            TeslaMapsConfig.get().invincibilityY,
                            DungeonTimers.invincibilityWidth(TeslaMapsConfig.get().invincibilityScale),
                            DungeonTimers.invincibilityHeight(TeslaMapsConfig.get().invincibilityScale)),
                    xy -> {
                        TeslaMapsConfig.get().invincibilityX = xy[0];
                        TeslaMapsConfig.get().invincibilityY = xy[1];
                    },
                    () -> TeslaMapsConfig.get().invincibilityScale,
                    value -> TeslaMapsConfig.get().invincibilityScale = value,
                    null),

            new Panel("Pests",
                    PestOverlay::shouldRender,
                    () -> panelBounds(TeslaMapsConfig.get().pestOverlayX,
                            TeslaMapsConfig.get().pestOverlayY,
                            PestOverlay.width(TeslaMapsConfig.get().pestOverlayScale),
                            PestOverlay.height(TeslaMapsConfig.get().pestOverlayScale)),
                    xy -> {
                        TeslaMapsConfig.get().pestOverlayX = xy[0];
                        TeslaMapsConfig.get().pestOverlayY = xy[1];
                    },
                    () -> TeslaMapsConfig.get().pestOverlayScale,
                    value -> TeslaMapsConfig.get().pestOverlayScale = value,
                    null),

            new Panel("Profit",
                    FarmProfitOverlay::shouldRender,
                    () -> panelBounds(TeslaMapsConfig.get().farmProfitX,
                            TeslaMapsConfig.get().farmProfitY,
                            FarmProfitOverlay.width(TeslaMapsConfig.get().farmProfitScale),
                            FarmProfitOverlay.height(TeslaMapsConfig.get().farmProfitScale)),
                    xy -> {
                        TeslaMapsConfig.get().farmProfitX = xy[0];
                        TeslaMapsConfig.get().farmProfitY = xy[1];
                    },
                    () -> TeslaMapsConfig.get().farmProfitScale,
                    value -> TeslaMapsConfig.get().farmProfitScale = value,
                    FarmProfitOverlay::onClick),

            new Panel("Lane",
                    () -> TeslaMapsConfig.get().farmBar && FarmProgress.hasLane(),
                    () -> panelBounds(TeslaMapsConfig.get().farmBarX,
                            TeslaMapsConfig.get().farmBarY,
                            FarmProgress.barWidth(TeslaMapsConfig.get().farmBarScale),
                            FarmProgress.barHeight(TeslaMapsConfig.get().farmBarScale)),
                    xy -> {
                        TeslaMapsConfig.get().farmBarX = xy[0];
                        TeslaMapsConfig.get().farmBarY = xy[1];
                    },
                    () -> TeslaMapsConfig.get().farmBarScale,
                    value -> TeslaMapsConfig.get().farmBarScale = value,
                    null),

            new Panel("Tracker",
                    SkillTracker::shouldRender,
                    () -> panelBounds(TeslaMapsConfig.get().skillTrackerX,
                            TeslaMapsConfig.get().skillTrackerY,
                            SkillTracker.width(TeslaMapsConfig.get().skillTrackerScale),
                            SkillTracker.height(TeslaMapsConfig.get().skillTrackerScale)),
                    xy -> {
                        TeslaMapsConfig.get().skillTrackerX = xy[0];
                        TeslaMapsConfig.get().skillTrackerY = xy[1];
                    },
                    () -> TeslaMapsConfig.get().skillTrackerScale,
                    value -> TeslaMapsConfig.get().skillTrackerScale = value,
                    SkillTracker::onClick),

            new Panel("Bestiary",
                    BestiaryTracker::shouldRender,
                    () -> panelBounds(TeslaMapsConfig.get().bestiaryTrackerX,
                            TeslaMapsConfig.get().bestiaryTrackerY,
                            BestiaryTracker.width(TeslaMapsConfig.get().bestiaryTrackerScale),
                            BestiaryTracker.height(TeslaMapsConfig.get().bestiaryTrackerScale)),
                    xy -> {
                        TeslaMapsConfig.get().bestiaryTrackerX = xy[0];
                        TeslaMapsConfig.get().bestiaryTrackerY = xy[1];
                    },
                    () -> TeslaMapsConfig.get().bestiaryTrackerScale,
                    value -> TeslaMapsConfig.get().bestiaryTrackerScale = value,
                    BestiaryTracker::onClick),

            new Panel("Charmed",
                    CharmedVisitors::shouldRender,
                    () -> panelBounds(TeslaMapsConfig.get().charmedVisitorX,
                            TeslaMapsConfig.get().charmedVisitorY,
                            CharmedVisitors.width(TeslaMapsConfig.get().charmedVisitorScale),
                            CharmedVisitors.height(TeslaMapsConfig.get().charmedVisitorScale)),
                    xy -> {
                        TeslaMapsConfig.get().charmedVisitorX = xy[0];
                        TeslaMapsConfig.get().charmedVisitorY = xy[1];
                    },
                    () -> TeslaMapsConfig.get().charmedVisitorScale,
                    value -> TeslaMapsConfig.get().charmedVisitorScale = value,
                    CharmedVisitors::onClick),

            new Panel("Routine",
                    () -> TeslaMapsConfig.get().pestRoutineHud,
                    () -> panelBounds(TeslaMapsConfig.get().pestRoutineX,
                            TeslaMapsConfig.get().pestRoutineY,
                            PestRoutine.panelWidth(TeslaMapsConfig.get().pestRoutineScale),
                            PestRoutine.panelHeight(TeslaMapsConfig.get().pestRoutineScale)),
                    xy -> {
                        TeslaMapsConfig.get().pestRoutineX = xy[0];
                        TeslaMapsConfig.get().pestRoutineY = xy[1];
                    },
                    () -> TeslaMapsConfig.get().pestRoutineScale,
                    value -> TeslaMapsConfig.get().pestRoutineScale = value,
                    null),
    };

    private static int[] panelBounds(int x, int y, int width, int height) {
        return new int[]{x, y, width, height};
    }

    private static Panel dragging = null;
    private static int offsetX;
    private static int offsetY;
    private static int pressX;
    private static int pressY;

    private static Panel panelAt(double mouseX, double mouseY) {
        if (!TeslaMapsConfig.get().hudDragInInventory) return null;
        for (Panel panel : PANELS) {
            if (!panel.visible().get()) continue;
            int[] b = panel.bounds().get();
            if (mouseX >= b[0] && mouseX <= b[0] + b[2]
                    && mouseY >= b[1] && mouseY <= b[1] + b[3]) {
                return panel;
            }
        }
        return null;
    }

    private static int pressButton;

    public static boolean handleClick(double mouseX, double mouseY, int button) {
        Panel panel = panelAt(mouseX, mouseY);
        if (panel == null) return false;

        int[] b = panel.bounds().get();
        dragging = panel;
        offsetX = (int) mouseX - b[0];
        offsetY = (int) mouseY - b[1];
        pressX = (int) mouseX;
        pressY = (int) mouseY;
        pressButton = button;
        return true;
    }

    private static final int SNAP_DISTANCE = 8;

    public static boolean handleDrag(double mouseX, double mouseY) {
        if (dragging == null) return false;

        int x = Math.max(0, (int) mouseX - offsetX);
        int y = Math.max(0, (int) mouseY - offsetY);

        int[] self = dragging.bounds().get();
        int[] snapped = snap(x, y, self[2], self[3]);
        dragging.setPosition().accept(snapped);
        return true;
    }

    /**
     * Pulls a dragged panel onto its neighbours' edges. Pulling further than the
     * threshold releases it again, so panels can be stuck together and taken
     * apart without a modifier key.
     */
    private static int[] snap(int x, int y, int width, int height) {
        int bestX = x;
        int bestY = y;
        int closestX = SNAP_DISTANCE + 1;
        int closestY = SNAP_DISTANCE + 1;

        for (Panel other : PANELS) {
            if (other == dragging || !other.visible().get()) continue;
            int[] b = other.bounds().get();
            int otherLeft = b[0];
            int otherTop = b[1];
            int otherRight = b[0] + b[2];
            int otherBottom = b[1] + b[3];

            // Edge to edge, plus aligning the same edges.
            for (int[] candidate : new int[][]{
                    {otherRight, 0}, {otherLeft - width, 0},
                    {otherLeft, 0}, {otherRight - width, 0}}) {
                int distance = Math.abs(x - candidate[0]);
                if (distance < closestX) {
                    closestX = distance;
                    bestX = candidate[0];
                }
            }

            for (int[] candidate : new int[][]{
                    {otherBottom, 0}, {otherTop - height, 0},
                    {otherTop, 0}, {otherBottom - height, 0}}) {
                int distance = Math.abs(y - candidate[0]);
                if (distance < closestY) {
                    closestY = distance;
                    bestY = candidate[0];
                }
            }
        }

        return new int[]{
                closestX <= SNAP_DISTANCE ? Math.max(0, bestX) : x,
                closestY <= SNAP_DISTANCE ? Math.max(0, bestY) : y
        };
    }

    private static final int CLICK_SLOP = 3;

    public static boolean handleRelease(double mouseX, double mouseY) {
        if (dragging == null) return false;

        // A press that never moved is a click on the panel, not a drag; the
        // drag handler runs first, so this is where the two are told apart.
        boolean moved = Math.abs(mouseX - pressX) > CLICK_SLOP
                || Math.abs(mouseY - pressY) > CLICK_SLOP;
        ClickAction action = dragging.onClick();

        dragging = null;
        if (!moved && action != null) {
            action.onClick(mouseX, mouseY, pressButton);
            return true;
        }
        TeslaMapsConfig.save();
        return true;
    }

    public static boolean handleScroll(double mouseX, double mouseY, double amount) {
        Panel panel = panelAt(mouseX, mouseY);
        if (panel == null) return false;

        float scale = panel.scale().get() + (amount > 0 ? 0.1f : -0.1f);
        panel.setScale().accept(Math.clamp(scale, 0.5f, 5.0f));
        TeslaMapsConfig.save();
        return true;
    }

    /** Name of the panel under the cursor, for the hint line. */
    public static String hovered(double mouseX, double mouseY) {
        Panel panel = panelAt(mouseX, mouseY);
        return panel == null ? null : panel.name();
    }
}
