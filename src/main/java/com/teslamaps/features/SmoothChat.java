package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;

public final class SmoothChat {

    private SmoothChat() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SmoothChat")
                .config("Chat", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Message Indicator");
                    b.toggle("Hide Message Indicator", () -> c.chatHideMessageIndicator, v -> c.chatHideMessageIndicator = v)
                            .desc("Hides the coloured bar left of chat lines (system / modified / not secure).");
                });
    }

    public static boolean hideIndicator() {
        return cfg().chatHideMessageIndicator && cfg().section("Chat");
    }
}
