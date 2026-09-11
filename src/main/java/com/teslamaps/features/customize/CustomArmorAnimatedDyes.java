package com.teslamaps.features.customize;

import com.teslamaps.utils.OkLabColor;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/** Interpolates an {@link AnimatedDye}'s keyframes once per frame. Ported from Skyblocker. */
public class CustomArmorAnimatedDyes {
    private static final Map<AnimatedDye, StateTracker> STATE_TRACKERS = new HashMap<>();
    private static int frames;

    /** Called once per frame so a dye used by several pieces only interpolates once. */
    public static void newFrame() {
        frames++;
    }

    public static int animateColorTransition(AnimatedDye dye) {
        StateTracker tracked = STATE_TRACKERS.computeIfAbsent(dye, StateTracker::new);
        if (tracked.lastRecordedFrame == frames) return tracked.lastColor;
        tracked.lastRecordedFrame = frames;
        return tracked.interpolate(dye, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks());
    }

    public static void cleanTrackers() {
        STATE_TRACKERS.clear();
    }

    private static class StateTracker {
        private float progress = 0;
        private boolean onBackCycle = false;
        private int lastColor = 0;
        private int lastRecordedFrame = 0;

        private StateTracker(AnimatedDye dye) {
            if (dye.delay() > 0) {
                if (dye.cycleBack()) {
                    onBackCycle = true;
                    progress = dye.delay() / dye.duration();
                } else {
                    progress = 1 - dye.delay() / dye.duration();
                }
                progress = clamp(progress);
            }
        }

        private int interpolate(AnimatedDye dye, float deltaTicks) {
            update(dye, deltaTicks);

            int keyframe = 0;
            // keyframe cannot be the last one or keyframe + 1 goes out of bounds
            while (keyframe < dye.keyframes().size() - 2 && dye.keyframes().get(keyframe + 1).time() < progress) keyframe++;

            AnimatedDye.Keyframe current = onBackCycle ? dye.keyframes().get(keyframe + 1) : dye.keyframes().get(keyframe);
            AnimatedDye.Keyframe next = onBackCycle ? dye.keyframes().get(keyframe) : dye.keyframes().get(keyframe + 1);

            float colorProgress = (progress - current.time()) / (next.time() - current.time());
            colorProgress = clamp(colorProgress);

            return lastColor = OkLabColor.interpolate(current.color(), next.color(), colorProgress);
        }

        private void update(AnimatedDye dye, float deltaTicks) {
            float v = deltaTicks * 0.05f / dye.duration();
            if (onBackCycle) {
                progress -= v;
                if (progress <= 0f) {
                    onBackCycle = false;
                    progress = Math.abs(progress);
                }
            } else {
                progress += v;
                if (progress >= 1f) {
                    if (dye.cycleBack()) {
                        onBackCycle = true;
                        progress = 2f - progress;
                    } else {
                        progress %= 1f;
                    }
                }
            }
            progress = clamp(progress);
        }

        private static float clamp(float progress) {
            return Math.clamp(progress, 0, 1);
        }
    }
}
