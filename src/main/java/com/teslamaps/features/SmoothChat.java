package com.teslamaps.features;

// Ported from Skysoft's ChatMotionProfile.kt / ChatMotionSettings.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;

/** New chat lines slide in, the input bar slides up on open. */
public final class SmoothChat {

    private static final double MESSAGE_TRAVEL_RATIO = 0.72;
    private static final double MESSAGE_RESPONSE = 7.25;
    private static final double INPUT_TRAVEL_RATIO = 0.018;
    private static final double MIN_INPUT_TRAVEL = 5.0;
    private static final double MAX_INPUT_TRAVEL = 9.0;
    private static final double INPUT_RESPONSE = 6.0;

    private static long messageStart = Long.MIN_VALUE;
    private static long openStart = Long.MIN_VALUE;

    private SmoothChat() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SmoothChat")
                .config("Chat", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Smooth Chat");
                    b.toggle("Animate New Messages", () -> c.smoothChatMessages, v -> c.smoothChatMessages = v)
                            .desc("New chat lines slide into place.")
                            .tags("smooth", "animation");
                    b.slider("  Message Slide (ms)", 10f, 300f, () -> (float) c.smoothChatMessageMs, v -> c.smoothChatMessageMs = Math.round(v));
                    b.toggle("Animate Chat Open", () -> c.smoothChatOpen, v -> c.smoothChatOpen = v)
                            .desc("The input bar slides up when chat opens.")
                            .tags("smooth", "animation");
                    b.slider("  Open Slide (ms)", 10f, 700f, () -> (float) c.smoothChatOpenMs, v -> c.smoothChatOpenMs = Math.round(v));
                    b.toggle("Hide Message Indicator", () -> c.chatHideMessageIndicator, v -> c.chatHideMessageIndicator = v)
                            .desc("Hides the coloured bar left of chat lines (system / modified / not secure).");
                });
    }

    private static boolean on() {
        return cfg().section("Chat");
    }

    public static void onMessageAdded() {
        if (cfg().smoothChatMessages) messageStart = System.nanoTime();
    }

    public static void onChatOpened(boolean sleeping) {
        openStart = cfg().smoothChatOpen && !sleeping ? System.nanoTime() : Long.MIN_VALUE;
    }

    public static void onChatClosed() {
        openStart = Long.MIN_VALUE;
    }

    public static boolean hideIndicator() {
        return cfg().chatHideMessageIndicator && on();
    }

    public static float messageOffset(int lineHeight, int scrollPos) {
        TeslaMapsConfig c = cfg();
        if (!c.smoothChatMessages || scrollPos != 0 || !on()) return 0f;
        return (float) (lineHeight * MESSAGE_TRAVEL_RATIO * settle(progress(messageStart, c.smoothChatMessageMs), MESSAGE_RESPONSE));
    }

    public static float inputOffset(int guiHeight) {
        TeslaMapsConfig c = cfg();
        if (!c.smoothChatOpen || !on()) return 0f;
        double travel = Math.max(MIN_INPUT_TRAVEL, Math.min(MAX_INPUT_TRAVEL, guiHeight * INPUT_TRAVEL_RATIO));
        return (float) (travel * settle(progress(openStart, c.smoothChatOpenMs), INPUT_RESPONSE));
    }

    private static float progress(long start, int ms) {
        if (start == Long.MIN_VALUE || ms <= 0) return 1f;
        long elapsed = Math.max(0L, System.nanoTime() - start);
        return Math.min(1f, elapsed / (ms * 1_000_000f));
    }

    private static double settle(float progress, double response) {
        double t = Math.max(0.0, Math.min(1.0, progress));
        if (t == 0.0) return 1.0;
        if (t == 1.0) return 0.0;
        double end = (1.0 + response) * Math.exp(-response);
        double cur = (1.0 + response * t) * Math.exp(-response * t);
        return Math.max(0.0, Math.min(1.0, (cur - end) / (1.0 - end)));
    }
}
