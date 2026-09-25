package com.teslamaps.features;

// Ported from NoammAddons' MonoAudio.kt + MixinChannel + MixinSoundEngine
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;

public final class MonoAudio {

    public interface Channel {
        void teslamaps$refreshMono();
    }

    public static volatile boolean wasEnabled;

    private MonoAudio() {}

    /** Sound thread: only reads config. */
    public static boolean enabled() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.monoAudio && c.section("Sounds");
    }

    public static double distanceToListener(Vec3 pos) {
        return pos.distanceTo(Minecraft.getInstance().getSoundManager().getListenerTransform().position());
    }

    public static void center(int source, double distance) {
        AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, (float) -distance);
    }

    public static void register() {
        Feature.of("MonoAudio").section("Sounds")
                .config("Sounds", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.label("Mono Audio");
                    b.toggle("Mono Audio", () -> c.monoAudio, v -> c.monoAudio = v)
                            .desc("Plays every sound centred in front of you. Distance still makes sounds quieter. For one-sided hearing or a single earbud.");
                });
    }
}
