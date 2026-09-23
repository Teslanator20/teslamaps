package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.screen.SoundManagerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

// Ported from NoammAddons' SoundManager.kt
public final class SoundVolumes {

    private static final LinkedHashSet<Identifier> recent = new LinkedHashSet<>();
    private static long recentVersion = 0;

    private static Map<Identifier, Float> cache = new HashMap<>();
    private static Map<String, Float> cachedFrom = null;
    private static int cachedSize = -1;

    private SoundVolumes() {}

    public static float multiplier(Identifier id) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.soundManager || c.soundVolumes.isEmpty() || id == null) return 1f;
        if (cachedFrom != c.soundVolumes || cachedSize != c.soundVolumes.size()) rebuild(c);
        Float m = cache.get(id);
        return m == null ? 1f : m;
    }

    private static void rebuild(TeslaMapsConfig c) {
        Map<Identifier, Float> m = new HashMap<>();
        for (Map.Entry<String, Float> e : c.soundVolumes.entrySet()) {
            Identifier id = Identifier.tryParse(e.getKey());
            if (id != null && e.getValue() != null) m.put(id, e.getValue());
        }
        cache = m;
        cachedFrom = c.soundVolumes;
        cachedSize = c.soundVolumes.size();
    }

    public static void record(SoundInstance instance) {
        if (!TeslaMapsConfig.get().soundManager) return;
        if (Minecraft.getInstance().screen instanceof SoundManagerScreen) return;
        Identifier id;
        try { id = instance.getIdentifier(); } catch (Throwable t) { return; }
        if (id == null) return;
        synchronized (recent) {
            recent.remove(id);
            recent.add(id);
            if (recent.size() > 100) recent.remove(recent.iterator().next());
            recentVersion++;
        }
    }

    public static long recentVersion() {
        synchronized (recent) { return recentVersion; }
    }

    /** Newest first. */
    public static List<String> recentIds() {
        List<String> out = new ArrayList<>();
        synchronized (recent) {
            for (Identifier id : recent) out.add(id.toString());
        }
        java.util.Collections.reverse(out);
        return out;
    }

    public static void clearRecent() {
        synchronized (recent) { recent.clear(); recentVersion++; }
    }

    public static int percent(String id) {
        Float v = TeslaMapsConfig.get().soundVolumes.get(id);
        return v == null ? 100 : Math.round(v * 100f);
    }

    public static void setPercent(String id, int percent) {
        int p = Math.clamp(Math.round(percent / 5f) * 5, 0, 200);
        Map<String, Float> map = TeslaMapsConfig.get().soundVolumes;
        if (p == 100) map.remove(id);
        else map.put(id, p / 100f);
        cachedSize = -1;
    }

    public static void preview(SoundEvent sound) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1f, 0.25f));
    }
}
