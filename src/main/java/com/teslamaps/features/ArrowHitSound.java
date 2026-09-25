package com.teslamaps.features;

// Ported from NoammAddons' ArrowHitSound.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SoundOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;

public final class ArrowHitSound {

    private static final String ID = "minecraft:entity.arrow.hit_player";

    /** Set while our own code plays the vanilla arrow sound on purpose (Last Breath). */
    public static boolean bypass;

    private ArrowHitSound() {}

    public static void register() {
        Feature.of("ArrowHitSound").section("Sounds").when(() -> TeslaMapsConfig.get().arrowHitSound)
                .config("Sounds", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Arrow Hit");
                    b.toggle("Custom Arrow Hit Sound", () -> c.arrowHitSound, v -> c.arrowHitSound = v)
                            .desc("Replaces the arrow hit 'ding' with a sound of your choice");
                    b.soundDropdown("  Arrow Hit Sound", SoundOptions.keys(), () -> c.arrowHitSoundType, v -> c.arrowHitSoundType = v);
                    b.slider("  Arrow Hit Volume", 0f, 4f, () -> c.arrowHitSoundVolume, v -> c.arrowHitSoundVolume = v, 2);
                    b.slider("  Arrow Hit Pitch", 0.5f, 2f, () -> c.arrowHitSoundPitch, v -> c.arrowHitSoundPitch = v, 2);
                });
    }

    /** SoundEngine.play, main thread. True = drop the vanilla sound. */
    public static boolean replace(SoundInstance instance, String id) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.arrowHitSound || bypass || instance instanceof LoudSound.LoudSoundInstance || !ID.equals(id)) return false;
        if (!c.section("Sounds")) return false;
        String type = c.arrowHitSoundType;
        float vol = c.arrowHitSoundVolume, pitch = c.arrowHitSoundPitch;
        Minecraft.getInstance().execute(() -> LoudSound.play(SoundOptions.resolve(type), vol, pitch));
        return true;
    }
}
