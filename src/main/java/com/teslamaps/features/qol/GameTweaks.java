package com.teslamaps.features.qol;

// Confirm Disconnect from Devonian's Misc.kt / PauseScreenMixin, Focus Loss Prevention from FloydAddons
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;

public class GameTweaks {

    public static void register() {
        Feature.of("GameTweaks").config("Interface", b -> {
            TeslaMapsConfig c = b.config();
            b.label("Game Tweaks");
            b.toggle("Focus Loss Prevention", () -> c.focusLossPrevention, v -> c.focusLossPrevention = v)
                    .desc("Alt-tabbing out no longer opens the pause menu. Your Minecraft option stays untouched.")
                    .tags("pause", "alt tab", "focus");
            b.toggle("Confirm Disconnect", () -> c.confirmDisconnect, v -> c.confirmDisconnect = v)
                    .desc("The Disconnect button needs a second click, at least the set time after the first.")
                    .tags("disconnect", "pause", "leave");
            b.slider("  Second Click After (ms)", 0f, 1000f, () -> c.confirmDisconnectMs, v -> c.confirmDisconnectMs = v, 0);
        });
    }
}
