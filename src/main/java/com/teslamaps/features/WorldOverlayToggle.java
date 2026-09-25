package com.teslamaps.features;

// Oracle Economy toggleTerritoryOverlayKey + tickOverlayFade
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.KeyNames;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class WorldOverlayToggle {

    private static final float FADE_MS = 1000f;
    private static boolean hidden;
    private static boolean keyDown;
    private static long changedAt;

    private WorldOverlayToggle() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("WorldOverlayToggle").when(() -> cfg().worldOverlayKey != -1 || hidden)
                .tickAlways(WorldOverlayToggle::tick)
                .config("Render", b -> {
                    b.keybind("Hide World Overlays Key", () -> cfg().worldOverlayKey, v -> cfg().worldOverlayKey = v)
                            .desc("Press to hide or show every ESP box, tracer, waypoint and glow of this mod. Toggles stay as they are.")
                            .tags("esp", "waypoints", "screenshot", "overlay", "hide");
                    b.toggle("  Fade", () -> cfg().worldOverlayFade, v -> cfg().worldOverlayFade = v)
                            .desc("Fades over one second instead of switching instantly");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        int key = cfg().worldOverlayKey;
        boolean down = key != -1 && mc.screen == null && KeyNames.isDown(key);
        if (down && !keyDown) {
            float now = factor();
            hidden = !hidden;
            // continue from the current fade value
            long t = System.currentTimeMillis();
            changedAt = t - (long) ((hidden ? 1f - now : now) * FADE_MS);
            if (mc.player != null)
                mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fWorld overlays "
                        + (hidden ? "§chidden" : "§ashown")));
        }
        keyDown = down;
        if (key == -1) hidden = false;
        if (hidden && factor() <= 0f) com.teslamaps.render.EntityGlow.clear();
    }

    /** 1 = fully shown, 0 = hidden. */
    private static float factor() {
        if (!cfg().worldOverlayFade) return hidden ? 0f : 1f;
        float p = Math.min(1f, (System.currentTimeMillis() - changedAt) / FADE_MS);
        return hidden ? 1f - p : p;
    }

    /** Called before the world pass; false = skip it. Sets the ESP alpha multiplier. */
    public static boolean beginWorld() {
        if (!hidden && (changedAt == 0 || !cfg().worldOverlayFade
                || System.currentTimeMillis() - changedAt >= FADE_MS)) {
            ESPRenderer.fade = 1f;
            return true;
        }
        float f = factor();
        ESPRenderer.fade = f;
        return f > 0f;
    }
}
