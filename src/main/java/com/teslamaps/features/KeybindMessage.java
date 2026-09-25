package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonWaypoints;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KeybindMessage {
    private static final Set<Integer> heldKeys = new HashSet<>();
    private static final Set<Integer> edges = new HashSet<>();
    private static final Set<Integer> polled = new HashSet<>();
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
        List<TeslaMapsConfig.Keybind> binds = TeslaMapsConfig.get().keybinds;
        edges.clear();
        polled.clear();
        for (TeslaMapsConfig.Keybind kb : binds) {
            if (kb.key == -1 || !polled.add(kb.key)) continue;
            boolean down = com.teslamaps.utils.KeyNames.isDown(kb.key);
            if (down && !heldKeys.contains(kb.key)) edges.add(kb.key);   // edge: just pressed
            if (down) heldKeys.add(kb.key); else heldKeys.remove(kb.key);
        }
        if (fire && !edges.isEmpty()) {
            int mods = heldModifiers(mc);
            for (TeslaMapsConfig.Keybind kb : binds) {
                if (!kb.enabled || !edges.contains(kb.key) || kb.message == null || (kb.message.isBlank() && !HotkeyCondition.hasFail(kb))) continue;
                if (!(kb.modifiers != 0 ? kb.modifiers == mods : !shadowed(binds, kb.key, mods))) continue;
                if (!TeslaMapsConfig.get().hotkeyConditions) { send(kb.message); continue; }
                if (!HotkeyCondition.islandOk(kb)) continue;
                if (HotkeyCondition.passes(kb)) { if (!kb.message.isBlank()) send(kb.message); }
                else if (kb.failMessage != null && !kb.failMessage.isBlank()) send(kb.failMessage);
            }
        }

        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        pollWp(handle, cfg.waypointAddKey, fire, () -> wpResult(DungeonWaypoints.addAtTarget(
                TeslaMapsConfig.parseColor(cfg.waypointAddColor), cfg.waypointAddFilled, cfg.waypointAddThroughWalls, cfg.waypointAddType)));
        pollWp(handle, cfg.waypointAdd2Key, fire, () -> wpResult(DungeonWaypoints.addAtTarget(
                TeslaMapsConfig.parseColor(cfg.waypointAdd2Color), cfg.waypointAdd2Filled, cfg.waypointAdd2ThroughWalls, cfg.waypointAdd2Type)));
        pollWp(handle, cfg.waypointAdd3Key, fire, () -> wpResult(DungeonWaypoints.addAtTarget(
                TeslaMapsConfig.parseColor(cfg.waypointAdd3Color), cfg.waypointAdd3Filled, cfg.waypointAdd3ThroughWalls, cfg.waypointAdd3Type)));
        pollWp(handle, cfg.waypointRemoveKey, fire, () -> wpResult(DungeonWaypoints.removeNearest()));
        pollWp(handle, cfg.waypointClearKey, fire, () -> wpResult(DungeonWaypoints.clearRoom()));
        pollWp(handle, cfg.waypointEditModeKey, fire, () -> wpResult(DungeonWaypoints.toggleEditMode()));
    }

    public static int heldModifiers(Minecraft mc) {
        return (mc.hasShiftDown() ? GLFW.GLFW_MOD_SHIFT : 0)
                | (mc.hasControlDown() ? GLFW.GLFW_MOD_CONTROL : 0)
                | (mc.hasAltDown() ? GLFW.GLFW_MOD_ALT : 0);
    }

    /** A plain bind stays quiet while a bind on the same key with exactly these modifiers fires. */
    private static boolean shadowed(List<TeslaMapsConfig.Keybind> binds, int key, int mods) {
        if (mods == 0) return false;
        for (TeslaMapsConfig.Keybind kb : binds) {
            if (kb.enabled && kb.key == key && kb.modifiers == mods) return true;
        }
        return false;
    }

    public static String modifierPrefix(int mods) {
        return ((mods & GLFW.GLFW_MOD_CONTROL) != 0 ? "Ctrl+" : "")
                + ((mods & GLFW.GLFW_MOD_ALT) != 0 ? "Alt+" : "")
                + ((mods & GLFW.GLFW_MOD_SHIFT) != 0 ? "Shift+" : "");
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
        ExecutionLog.log("Hotkey: " + message);
        if (message.startsWith("/")) {
            mc.getConnection().sendCommand(message.substring(1));
        } else {
            mc.getConnection().sendChat(message);
        }
    }
}
