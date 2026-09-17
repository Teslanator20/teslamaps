package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.screen.MapConfigScreen;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Opens the config the same way /tmap does. Handled straight out of KeyboardHandler.keyPress
 * instead of polling, so the press can be swallowed before vanilla hands it to any KeyMapping -
 * plenty of mods sit on right shift and would open their own screen on top otherwise.
 */
public class ConfigHotkey {

    /** @return true when the key belongs to us and nothing else should see it. */
    public static boolean handleKey(long window, int action, int key) {
        if (typing()) return false;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.itemSearchKey != -1 && key == c.itemSearchKey && itemSearch(window, action)) return true;
        if (c.configScreenKey == -1 || key != c.configScreenKey) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null || window != mc.getWindow().handle()) return false;

        // a screen owns its own input, and the key could just be a modifier while typing
        boolean ours = mc.screen instanceof MapConfigScreen;
        if (mc.screen != null && !ours) return false;

        if (action == GLFW.GLFW_PRESS) {
            mc.setScreen(ours ? null : new MapConfigScreen());
        }
        // release and repeat go the same way, otherwise the other mod still sees half the press
        return c.configScreenKeyExclusive;
    }

    /** Typing in a text field or rebinding a key - the press is text, not a hotkey. */
    private static boolean typing() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return false;
        if (mc.screen instanceof MapConfigScreen s && s.isBindingKey()) return true;
        return mc.screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox box && box.isFocused();
    }

    private static boolean itemSearch(long window, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null || window != mc.getWindow().handle()) return false;
        boolean ours = mc.screen instanceof com.teslamaps.screen.ItemSearchScreen;
        if (mc.screen != null && !ours) return false;
        if (action == GLFW.GLFW_PRESS) {
            if (ours) {
                mc.setScreen(null);
            } else {
                com.teslamaps.screen.ItemSearchScreen s = new com.teslamaps.screen.ItemSearchScreen(null);
                s.swallowNextChar = true;
                mc.setScreen(s);
            }
        }
        return true;
    }
}
