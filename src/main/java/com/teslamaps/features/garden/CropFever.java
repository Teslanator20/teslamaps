package com.teslamaps.features.garden;

// After Devonian's CropFeverTimer, alert idea from SkyOcean's CropFeverEffects
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SoundOptions;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Crop Fever countdown for the Pest Overlay, optional title + sound on start. */
public final class CropFever {

    private static final String START = "WOAH! You caught a case of the CROP FEVER for 60 seconds!";
    private static final String END = "GONE! Your CROP FEVER has been cured!";
    private static long endsAt;

    private CropFever() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CropFever").section("Garden")
                .when(() -> cfg().cropFeverTimer || cfg().cropFeverAlert)
                .serverChat(CropFever::onChat);
    }

    private static void onChat(String raw) {
        String s = StringUtil.stripTrim(raw);
        if (s.startsWith(START)) {
            endsAt = System.currentTimeMillis() + 60_000L;
            if (cfg().cropFeverAlert) alert();
        } else if (s.startsWith(END)) {
            endsAt = 0;
        }
    }

    private static void alert() {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.hud.setTimes(0, 40, 10);
        mc.gui.hud.setTitle(Component.literal("§6§lCROP FEVER"));
        mc.gui.hud.setSubtitle(Component.literal("§e60s"));
        var sound = SoundOptions.resolve(cfg().cropFeverSound);
        if (sound != null) LoudSound.play(sound, 1.0f, 1.0f);
    }

    /** Seconds left, or -1 while not active. */
    public static int secondsLeft() {
        if (endsAt == 0) return -1;
        long left = endsAt - System.currentTimeMillis();
        if (left <= 0) {
            endsAt = 0;
            return -1;
        }
        return (int) ((left + 999) / 1000);
    }

    public static boolean shown() {
        return cfg().cropFeverTimer && secondsLeft() >= 0;
    }
}
