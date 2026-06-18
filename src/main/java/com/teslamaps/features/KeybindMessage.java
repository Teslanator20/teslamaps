package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

/**
 * Unlimited custom hotkeys that send configurable chat messages. Keys are polled directly via
 * GLFW (configured in teslamaps' own GUI, /tmap msg) rather than vanilla key mappings.
 */
public class KeybindMessage {
    private static final Set<Integer> heldKeys = new HashSet<>();

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null || mc.getWindow() == null) return;
        // Don't fire while any screen is open (typing in chat / the config GUI).
        if (mc.screen != null) { heldKeys.clear(); return; }

        long handle = mc.getWindow().handle();
        for (TeslaMapsConfig.Keybind kb : TeslaMapsConfig.get().keybinds) {
            if (kb.key < 0 || kb.message == null || kb.message.isBlank()) continue;
            boolean down = GLFW.glfwGetKey(handle, kb.key) == GLFW.GLFW_PRESS;
            boolean wasDown = heldKeys.contains(kb.key);
            if (down && !wasDown) send(kb.message);   // edge: just pressed
            if (down) heldKeys.add(kb.key); else heldKeys.remove(kb.key);
        }
    }

    private static void send(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        if (message.startsWith("/")) {
            mc.getConnection().sendCommand(message.substring(1));
        } else {
            mc.getConnection().sendChat(message);
        }
    }
}
