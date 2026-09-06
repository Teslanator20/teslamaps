package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Warns before the Trophy Fishing Umberella runs out and again when it is gone. The remaining
 * time comes from the deployable's own nametag, the despawn line only confirms it.
 */
public class UmbrellaWarning {

    private static final String DESPAWNED = "Umberella despawned";

    private static boolean warned = false;

    public static void onChatMessage(String text) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.umbrellaWarning) return;
        // Hypixel spells it "Umberella", the plain spelling is here so both land
        String clean = StringUtil.strip(text).trim();
        if (!clean.contains(DESPAWNED) && !clean.contains("Umbrella despawned")) return;

        warned = false;
        Deployables.clear(Deployables.Deployable.UMBERELLA);
        alert(c, "§c§lUMBERELLA GONE");
    }

    public static void tick() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.umbrellaWarning || c.umbrellaWarnBefore <= 0) return;

        long left = Deployables.secondsLeft(Deployables.Deployable.UMBERELLA);
        // arm again as soon as a fresh umbrella pushes the timer back up, otherwise
        // replacing one before it ran out would swallow the next warning
        if (left < 0 || left > c.umbrellaWarnBefore) {
            warned = false;
            return;
        }
        if (warned) return;
        warned = true;
        alert(c, "§6§lUMBERELLA " + left + "s");
    }

    public static void reset() {
        warned = false;
    }

    private static void alert(TeslaMapsConfig c, String title) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (c.umbrellaWarningTitle) {
            mc.gui.setTimes(0, 40, 5);
            mc.gui.setTitle(Component.literal(title));
        }
        if (c.umbrellaWarningVolume > 0f) {
            com.teslamaps.utils.LoudSound.play(
                    com.teslamaps.utils.SoundOptions.resolve(c.umbrellaWarningSound),
                    c.umbrellaWarningVolume, 1.0f);
        }
    }
}
