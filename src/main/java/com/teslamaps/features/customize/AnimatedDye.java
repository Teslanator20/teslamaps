package com.teslamaps.features.customize;

import java.util.List;

// Ported from Skyblocker's CustomArmorAnimatedDyes.AnimatedDye
public record AnimatedDye(List<Keyframe> keyframes, boolean cycleBack, float delay, float duration) {
    public record Keyframe(int color, float time) {}
}
