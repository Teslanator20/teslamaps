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
    private static final java.util.Map<TeslaMapsConfig.Keybind, Long> lastFire = new java.util.IdentityHashMap<>();

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null || mc.getWindow() == null) return;
        HotkeyRules.runPending();
        if (mc.gui.screen() != null) { screenOpenMs = System.currentTimeMillis(); heldKeys.clear(); heldWp.clear(); return; }

        // grace period after closing a screen: still track key state, but don't fire — stops a key you
        // just typed (e.g. "o" in a chat message) from being seen as a fresh press the moment chat closes
        boolean fire = System.currentTimeMillis() - screenOpenMs >= TeslaMapsConfig.get().hotkeyChatDelay;

        long handle = mc.getWindow().handle();
        List<TeslaMapsConfig.Keybind> binds = TeslaMapsConfig.get().keybinds;
        edges.clear();
        polled.clear();
        boolean adv = HotkeyRules.enabled();
        for (TeslaMapsConfig.Keybind kb : binds) {
            poll(kb.key);
            if (adv) poll(kb.seqKey);
        }
        if (fire && !edges.isEmpty()) {
            int mods = heldModifiers(mc);
            boolean combos = TeslaMapsConfig.get().hotkeyCombos;
            long now = System.currentTimeMillis();
            for (TeslaMapsConfig.Keybind kb : binds) {
                if (!kb.enabled || kb.message == null) continue;
                if (adv && kb.seqKey != -1 && edges.contains(kb.seqKey) && HotkeyRules.sequenceDone(kb)) {
                    fireBind(kb, true);
                    continue;
                }
                if (!edges.contains(kb.key)) continue;
                if (combos) {
                    if (kb.key2 != -1 ? !com.teslamaps.utils.KeyNames.isDown(kb.key2) : comboShadowed(binds, kb, mods)) continue;
                }
                if (!(kb.modifiers != 0 ? kb.modifiers == mods : !shadowed(binds, kb.key, mods))) continue;
                if (adv && kb.seqKey != -1) { HotkeyRules.armSequence(kb); continue; }
                if (combos) {
                    Long last = lastFire.get(kb);
                    if (last != null && now - last < 200) continue;
                    lastFire.put(kb, now);
                }
                fireBind(kb, adv);
            }
        }

        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        pollWp(handle, cfg.waypointAddKey, fire, () -> wpResult(DungeonWaypoints.addAtTarget(
                TeslaMapsConfig.parseColor(cfg.waypointAddColor), cfg.waypointAddFilled, cfg.waypointAddThroughWalls, cfg.waypointAddType)));
        pollWp(handle, cfg.waypointAdd2Key, fire, () -> wpResult(DungeonWaypoints.addAtTarget(
                TeslaMapsConfig.parseColor(cfg.waypointAdd2Color), true, false, cfg.waypointAdd2Type)));
        pollWp(handle, cfg.waypointAdd3Key, fire, () -> wpResult(DungeonWaypoints.addAtTarget(
                TeslaMapsConfig.parseColor(cfg.waypointAdd3Color), false, true, cfg.waypointAdd3Type)));
        pollWp(handle, cfg.waypointRemoveKey, fire, () -> wpResult(DungeonWaypoints.removeNearest()));
        pollWp(handle, cfg.waypointClearKey, fire, () -> wpResult(DungeonWaypoints.clearRoom()));
        pollWp(handle, cfg.waypointEditModeKey, fire, () -> wpResult(DungeonWaypoints.toggleEditMode()));
    }

    private static void poll(int key) {
        if (key == -1 || !polled.add(key)) return;
        boolean down = com.teslamaps.utils.KeyNames.isDown(key);
        if (down && !heldKeys.contains(key)) edges.add(key);   // edge: just pressed
        if (down) heldKeys.add(key); else heldKeys.remove(key);
    }

    private static void fireBind(TeslaMapsConfig.Keybind kb, boolean adv) {
        boolean hasActions = adv && kb.actions != null && !kb.actions.isBlank();
        if (kb.message.isBlank() && !HotkeyCondition.hasFail(kb) && !hasActions) return;
        if (!TeslaMapsConfig.get().hotkeyConditions) { if (!kb.message.isBlank()) send(kb.message); return; }
        if (!HotkeyCondition.islandOk(kb)) return;
        String msg;
        if (HotkeyCondition.passes(kb)) msg = kb.message;
        else if (kb.failMessage != null && !kb.failMessage.isBlank()) { msg = kb.failMessage; hasActions = false; }
        else return;
        if (!adv) { if (!msg.isBlank()) send(msg); return; }
        if (!HotkeyRules.cooldownOk(kb)) return;
        final String out = msg;
        final boolean act = hasActions;
        HotkeyRules.schedule(kb.delayMs, () -> {
            if (act) HotkeyRules.runActions(kb.actions);
            if (!out.isBlank()) send(out);
        });
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

    // Skyblocker Shortcuts: a plain bind stays quiet while a combo on the same key is held
    private static boolean comboShadowed(List<TeslaMapsConfig.Keybind> binds, TeslaMapsConfig.Keybind self, int mods) {
        for (TeslaMapsConfig.Keybind kb : binds) {
            if (kb == self || !kb.enabled || kb.key != self.key || kb.key2 == -1) continue;
            if ((kb.modifiers == 0 || kb.modifiers == mods) && com.teslamaps.utils.KeyNames.isDown(kb.key2)) return true;
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
        if (TeslaMapsConfig.get().hotkeyPlaceholders) message = com.teslamaps.utils.Placeholders.apply(message);
        if (message.startsWith("/")) {
            mc.getConnection().sendCommand(message.substring(1));
        } else {
            mc.getConnection().sendChat(message);
        }
    }
}
