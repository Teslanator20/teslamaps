package com.teslamaps.features;

// Ported from SkyHanni's SeeThroughWindow.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.KeyNames;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class SeeThroughWindow {

    private static boolean active;
    private static boolean changed;
    private static boolean wasDown;
    private static float applied = 1f;
    private static Object level;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SeeThroughWindow")
                .tickAlways(SeeThroughWindow::tick)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("See-Through Window");
                    b.toggle("See-Through Window", () -> c.seeThroughWindow, v -> c.seeThroughWindow = v)
                            .desc("A key makes the game window see-through, e.g. to watch something behind it while farming. Resets on world change.")
                            .tags("opacity", "transparent", "window");
                    b.keybind("  Toggle Key", () -> c.seeThroughWindowKey, v -> c.seeThroughWindowKey = v);
                    b.slider("  Opacity %", 5f, 100f, () -> (float) c.seeThroughWindowOpacity,
                            v -> c.seeThroughWindowOpacity = Math.round(v), 0);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        if (mc.level != level) {
            level = mc.level;
            active = false;
        }
        boolean on = c.seeThroughWindow && c.section("Garden");
        boolean down = on && mc.screen == null && KeyNames.isDown(c.seeThroughWindowKey);
        if (down && !wasDown) active = !active;
        wasDown = down;
        if (!on) active = false;
        apply(mc, active ? Math.max(0.05f, Math.min(1f, c.seeThroughWindowOpacity / 100f)) : 1f);
    }

    private static void apply(Minecraft mc, float alpha) {
        if (mc.getWindow() == null) return;
        if (alpha == 1f && !changed) return;
        if (alpha == applied && changed) return;
        GLFW.glfwSetWindowOpacity(mc.getWindow().handle(), alpha);
        applied = alpha;
        changed = alpha != 1f;
    }
}
