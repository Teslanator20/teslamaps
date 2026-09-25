package com.teslamaps.features;

// Ported from SkyHanni's PestSpawnSound.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.SoundOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayDeque;

public final class PestSpawnSound {

    private static final String[] MODES = {"Default", "Muted", "Custom", "Plumber"};
    private static final float[] PLUMBER_PITCH = {0.890899f, 0.890899f, 0.890899f, 0.707107f, 0.890899f, 1.059463f, 0.529732f};
    private static final int[] PLUMBER_DELAY = {0, 166, 333, 333, 166, 333, 666};

    private record Note(long at, float pitch) {}

    private static final ArrayDeque<Note> queue = new ArrayDeque<>();
    private static long lastSpawn;
    private static Feature feature;

    private PestSpawnSound() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("PestSpawnSound").section("Garden").when(() -> !"Default".equals(cfg().pestSpawnSoundMode))
                .tick(PestSpawnSound::tick)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.dropdown("Pest Spawn Sound", MODES, () -> c.pestSpawnSoundMode, v -> c.pestSpawnSoundMode = v)
                            .desc("Hypixel's pest spawn note: keep it, mute it, replace it with a repeated sound of your choice, or play a short plumber melody.")
                            .tags("pest", "spawn", "sound", "mute", "plumber", "mario");
                    b.soundDropdown("  Sound", SoundOptions.keys(), () -> c.pestSpawnSoundName, v -> c.pestSpawnSoundName = v);
                    b.slider("  Pitch", 0.5f, 2f, () -> c.pestSpawnSoundPitch, v -> c.pestSpawnSoundPitch = v, 2);
                    b.slider("  Volume", 0f, 4f, () -> c.pestSpawnSoundVolume, v -> c.pestSpawnSoundVolume = v, 2);
                    b.slider("  Repeat Count", 1f, 20f, () -> (float) c.pestSpawnSoundRepeat, v -> c.pestSpawnSoundRepeat = Math.round(v), 0);
                    b.slider("  Repeat Delay (ms)", 50f, 1000f, () -> (float) c.pestSpawnSoundDelay, v -> c.pestSpawnSoundDelay = Math.round(v), 0);
                    b.button("  Test Sound", () -> play(c.pestSpawnSoundMode));
                });
    }

    public static boolean enabled(TeslaMapsConfig c) {
        return !"Default".equals(c.pestSpawnSoundMode);
    }

    /** From SoundPlayMixin, main thread. True cancels the sound. */
    public static boolean onSound(SoundInstance s, String id, float vol, float pitch) {
        if (s instanceof LoudSound.LoudSoundInstance || feature == null || !feature.active()) return false;
        if (!id.equals("minecraft:block.note_block.bass") || Math.abs(pitch - 1.4920635f) > 0.001f || Math.abs(vol - 1f) > 0.001f) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !SkyblockUtils.isInGarden()) return false;
        if (!s.isRelative() && mc.player.distanceToSqr(s.getX(), s.getY(), s.getZ()) >= 15 * 15) return false;
        String mode = cfg().pestSpawnSoundMode;
        long now = System.currentTimeMillis();
        if (now - lastSpawn >= 5000L) play(mode);
        lastSpawn = now;
        return true;
    }

    private static void play(String mode) {
        long now = System.currentTimeMillis();
        TeslaMapsConfig c = cfg();
        if ("Custom".equals(mode)) {
            for (int i = 0; i < c.pestSpawnSoundRepeat; i++) queue.add(new Note(now + (long) i * c.pestSpawnSoundDelay, c.pestSpawnSoundPitch));
        } else if ("Plumber".equals(mode)) {
            long t = now;
            for (int i = 0; i < PLUMBER_PITCH.length; i++) {
                t += PLUMBER_DELAY[i];
                queue.add(new Note(t, PLUMBER_PITCH[i]));
            }
        }
    }

    private static void tick() {
        if (queue.isEmpty()) return;
        long now = System.currentTimeMillis();
        SoundEvent sound = null;
        while (!queue.isEmpty() && queue.peekFirst().at() <= now) {
            Note n = queue.pollFirst();
            if (sound == null) sound = SoundOptions.resolve(cfg().pestSpawnSoundName);
            if (sound != null) LoudSound.play(sound, cfg().pestSpawnSoundVolume, n.pitch());
        }
    }
}
