package com.teslamaps.features;

// Ported from Skysoft's features/misc/ForIntrests.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.SoundOptions;
import com.teslamaps.utils.StringUtil;

import java.util.ArrayDeque;

public final class DeathSound {

    private static final long WINDOW = 5 * 60_000L;
    private static final int SPECIAL_COUNT = 10;
    private static final ArrayDeque<Long> deaths = new ArrayDeque<>();

    private DeathSound() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("DeathSound").section("Sounds").when(() -> cfg().deathSound)
                .serverChat(DeathSound::onChat)
                .config("Sounds", b -> {
                    b.toggle("Death Sound", () -> cfg().deathSound, v -> cfg().deathSound = v)
                            .desc("Plays when you die. 10 deaths within 5 minutes play the special sound");
                    b.soundDropdown("  Sound", SoundOptions.keys(), () -> cfg().deathSoundType, v -> cfg().deathSoundType = v);
                    b.soundDropdown("  Special Sound (10 deaths)", SoundOptions.keys(), () -> cfg().deathSoundSpecial, v -> cfg().deathSoundSpecial = v);
                    b.slider("  Volume", 0f, 2f, () -> cfg().deathSoundVolume, v -> cfg().deathSoundVolume = v, 2);
                    b.buttonRow(new String[]{"Test Sound", "Test Special"},
                            new Runnable[]{() -> play(false), () -> play(true)});
                });
    }

    private static void onChat(String raw) {
        if (!StringUtil.stripTrim(raw).startsWith("☠ You ") || !SkyblockUtils.isInSkyblock()) return;
        long now = System.currentTimeMillis();
        while (!deaths.isEmpty() && now - deaths.peekFirst() > WINDOW) deaths.pollFirst();
        deaths.addLast(now);
        boolean special = deaths.size() >= SPECIAL_COUNT;
        if (special) deaths.clear();
        play(special);
    }

    private static void play(boolean special) {
        TeslaMapsConfig c = cfg();
        LoudSound.play(SoundOptions.resolve(special ? c.deathSoundSpecial : c.deathSoundType), c.deathSoundVolume, 1.0f);
    }
}
