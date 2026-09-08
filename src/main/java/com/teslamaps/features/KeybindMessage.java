package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonWaypoints;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

public class KeybindMessage {
    private static final Set<Integer> heldKeys = new HashSet<>();
    private static final Set<Integer> heldWp = new HashSet<>();
    private static long screenOpenMs = 0L; // last time a screen (e.g. chat) was open

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null || mc.getWindow() == null) return;
        if (mc.screen != null) { screenOpenMs = System.currentTimeMillis(); heldKeys.clear(); heldWp.clear(); return; }

        // grace period after closing a screen: still track key state, but don't fire — stops a key you
        // just typed (e.g. "o" in a chat message) from being seen as a fresh press the moment chat closes
        boolean fire = System.currentTimeMillis() - screenOpenMs >= TeslaMapsConfig.get().hotkeyChatDelay;

        long handle = mc.getWindow().handle();
        for (TeslaMapsConfig.Keybind kb : TeslaMapsConfig.get().keybinds) {
            if (kb.key == -1 || kb.message == null || kb.message.isBlank()) continue;
            boolean down = com.teslamaps.utils.KeyNames.isDown(kb.key);
            boolean wasDown = heldKeys.contains(kb.key);
            if (down && !wasDown && fire) send(kb.message);   // edge: just pressed
            if (down) heldKeys.add(kb.key); else heldKeys.remove(kb.key);
        }

        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        pollWp(handle, cfg.waypointAddKey, fire, () -> wpResult(DungeonWaypoints.addAtTarget(
                TeslaMapsConfig.parseColor(cfg.waypointAddColor), cfg.waypointAddFilled, cfg.waypointAddThroughWalls)));
        pollWp(handle, cfg.waypointAdd2Key, fire, () -> wpResult(DungeonWaypoints.addAtTarget(
                TeslaMapsConfig.parseColor(cfg.waypointAdd2Color), cfg.waypointAdd2Filled, cfg.waypointAdd2ThroughWalls)));
        pollWp(handle, cfg.waypointAdd3Key, fire, () -> wpResult(DungeonWaypoints.addAtTarget(
                TeslaMapsConfig.parseColor(cfg.waypointAdd3Color), cfg.waypointAdd3Filled, cfg.waypointAdd3ThroughWalls)));
        pollWp(handle, cfg.waypointRemoveKey, fire, () -> wpResult(DungeonWaypoints.removeNearest()));
        pollWp(handle, cfg.waypointClearKey, fire, () -> wpResult(DungeonWaypoints.clearRoom()));
    }

    private static void pollWp(long handle, int key, boolean fire, Runnable action) {
        if (key == -1) return;
        boolean down = com.teslamaps.utils.KeyNames.isDown(key);
        boolean was = heldWp.contains(key);
        if (down && !was && fire) action.run();
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
