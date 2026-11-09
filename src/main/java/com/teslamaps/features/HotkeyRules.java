package com.teslamaps.features;

// Concept from SkyOcean's HotkeyManager (key sequences) and Bax's AutomationEngine (delay, cooldown, local actions)
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class HotkeyRules {

    private record Pending(long at, Runnable run) {}

    private static final Map<TeslaMapsConfig.Keybind, Long> armed = new IdentityHashMap<>();
    private static final Map<TeslaMapsConfig.Keybind, Long> lastFire = new IdentityHashMap<>();
    private static final List<Pending> pending = new ArrayList<>();

    private HotkeyRules() {}

    public static boolean enabled() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.hotkeyConditions && c.hotkeyAdvanced;
    }

    public static void armSequence(TeslaMapsConfig.Keybind kb) {
        armed.put(kb, System.currentTimeMillis() + Math.max(50, Math.min(2000, kb.seqWindow)));
    }

    public static boolean sequenceDone(TeslaMapsConfig.Keybind kb) {
        Long until = armed.remove(kb);
        return until != null && System.currentTimeMillis() <= until;
    }

    public static boolean cooldownOk(TeslaMapsConfig.Keybind kb) {
        long now = System.currentTimeMillis();
        Long last = lastFire.get(kb);
        if (kb.cooldownMs > 0 && last != null && now - last < kb.cooldownMs) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal(
                    "§b[TeslaMaps] §7Hotkey on cooldown (" + ((kb.cooldownMs - (now - last)) / 100) / 10.0 + "s)"));
            return false;
        }
        lastFire.put(kb, now);
        return true;
    }

    public static void schedule(int delayMs, Runnable r) {
        if (delayMs <= 0) { r.run(); return; }
        if (pending.size() < 32) pending.add(new Pending(System.currentTimeMillis() + Math.min(delayMs, 60_000), r));
    }

    public static void runPending() {
        if (pending.isEmpty()) return;
        long now = System.currentTimeMillis();
        List<Runnable> due = null;
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (now < p.at) continue;
            it.remove();
            if (due == null) due = new ArrayList<>();
            due.add(p.run);
        }
        if (due != null) due.forEach(Runnable::run);
    }

    /** Local-only steps: sound, title:Text, note:Text. Nothing here talks to the server. */
    public static void runActions(String actions) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        for (String raw : actions.split(",")) {
            String a = raw.trim();
            if (a.isEmpty()) continue;
            String low = a.toLowerCase(java.util.Locale.ROOT);
            if (low.equals("sound")) {
                mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1f);
            } else if (low.startsWith("title:")) {
                mc.gui.hud.setTimes(5, 30, 10);
                mc.gui.hud.setTitle(Component.literal(a.substring(6).replace('&', '§')));
            } else if (low.startsWith("note:")) {
                mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §f" + a.substring(5).replace('&', '§')));
            }
        }
    }
}
