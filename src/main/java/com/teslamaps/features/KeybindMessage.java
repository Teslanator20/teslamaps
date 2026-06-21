/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
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

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null || mc.getWindow() == null) return;
        if (mc.screen != null) { heldKeys.clear(); heldWp.clear(); return; }

        long handle = mc.getWindow().handle();
        for (TeslaMapsConfig.Keybind kb : TeslaMapsConfig.get().keybinds) {
            if (kb.key < 0 || kb.message == null || kb.message.isBlank()) continue;
            boolean down = GLFW.glfwGetKey(handle, kb.key) == GLFW.GLFW_PRESS;
            boolean wasDown = heldKeys.contains(kb.key);
            if (down && !wasDown) send(kb.message);   // edge: just pressed
            if (down) heldKeys.add(kb.key); else heldKeys.remove(kb.key);
        }

        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        pollWp(handle, cfg.waypointAddKey, () -> wpResult(DungeonWaypoints.addAtTarget(
                TeslaMapsConfig.parseColor(cfg.waypointAddColor), cfg.waypointAddFilled, cfg.waypointAddThroughWalls)));
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
