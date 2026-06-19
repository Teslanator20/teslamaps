package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonWaypoints;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

/**
 * Unlimited custom hotkeys that send configurable chat messages. Keys are polled directly via
 * GLFW (configured in teslamaps' own GUI, /tmap msg) rather than vanilla key mappings.
 */
public class KeybindMessage {
    private static final Set<Integer> heldKeys = new HashSet<>();
    private static final Set<Integer> heldWp = new HashSet<>();

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null || mc.getWindow() == null) return;
        // Don't fire while any screen is open (typing in chat / the config GUI).
        if (mc.screen != null) { heldKeys.clear(); heldWp.clear(); return; }

        long handle = mc.getWindow().handle();
        for (TeslaMapsConfig.Keybind kb : TeslaMapsConfig.get().keybinds) {
            if (kb.key < 0 || kb.message == null || kb.message.isBlank()) continue;
            boolean down = GLFW.glfwGetKey(handle, kb.key) == GLFW.GLFW_PRESS;
            boolean wasDown = heldKeys.contains(kb.key);
            if (down && !wasDown) send(kb.message);   // edge: just pressed
            if (down) heldKeys.add(kb.key); else heldKeys.remove(kb.key);
        }

        // Dungeon waypoint edit keybinds (configured in /tmap config -> Waypoints)
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        pollWp(handle, cfg.waypointAddKey, () -> wpResult(DungeonWaypoints.addAtTarget(0xFF55FFFF, false)));
        pollWp(handle, cfg.waypointRemoveKey, () -> wpResult(DungeonWaypoints.removeNearest()));
        pollWp(handle, cfg.waypointClearKey, () -> wpResult(DungeonWaypoints.clearRoom()));
    }

    private static void pollWp(long handle, int key, Runnable action) {
        if (key < 0) return;
        boolean down = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        boolean was = heldWp.contains(key);
        if (down && !was) action.run();
        if (down) heldWp.add(key); else heldWp.remove(key);
    }

    private static void wpResult(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && s != null) mc.player.sendSystemMessage(Component.literal("§b[WP] §r" + s));
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
