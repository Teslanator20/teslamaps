package com.teslamaps.features.kuudra;

// Ported from IQ's BallistaBuildSoundMixin
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SoundOptions;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

public final class KuudraSounds {

    private static final String[] MODES = {"Mute Anvil", "Mute All", "Replace Anvil"};
    private static final Identifier ANVIL = SoundEvents.ANVIL_LAND.location();

    private KuudraSounds() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("KuudraSounds").section("Kuudra").when(() -> cfg().kuuBallistaSounds)
                .config("Kuudra", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Build Sounds");
                    b.toggle("Ballista Build Sounds", () -> c.kuuBallistaSounds, v -> c.kuuBallistaSounds = v)
                            .desc("Only during the build phase: mutes the anvil hammering, all sounds, or swaps the anvil for your own sound");
                    b.dropdown("  Mode", MODES, () -> c.kuuBallistaSoundMode, v -> c.kuuBallistaSoundMode = v);
                    b.soundDropdown("  Replacement Sound", SoundOptions.keys(), () -> c.kuuBallistaSoundType, v -> c.kuuBallistaSoundType = v);
                    b.slider("  Replacement Volume", 0f, 2f, () -> c.kuuBallistaSoundVolume, v -> c.kuuBallistaSoundVolume = v, 2);
                });
    }

    /** handleSoundEvent, main thread. True = cancel. */
    public static boolean onSoundPacket(ClientboundSoundPacket packet) {
        TeslaMapsConfig c = cfg();
        if (!c.kuuBallistaSounds || Kuudra.phase() != Kuudra.Phase.BUILD || !c.section("Kuudra")) return false;
        String mode = c.kuuBallistaSoundMode;
        if ("Mute All".equals(mode)) return true;
        if (!ANVIL.equals(packet.getSound().value().location())) return false;
        if ("Replace Anvil".equals(mode)) LoudSound.play(SoundOptions.resolve(c.kuuBallistaSoundType), c.kuuBallistaSoundVolume, packet.getPitch());
        return true;
    }
}
