package com.teslamaps.features;

// After SkyHanni's event/spook/SpookyChestAlert.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class SpookyChestAlert {

    private SpookyChestAlert() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SpookyChestAlert").section("Events").when(() -> cfg().spookyChestAlert)
                .serverChat(SpookyChestAlert::onChat)
                .config("Events", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Spooky Festival");
                    b.toggle("Spooky Chest Alert", () -> c.spookyChestAlert, v -> c.spookyChestAlert = v)
                            .desc("Title when a Trick or Treat Chest or Party Chest appears.");
                    b.toggle("  Compact Title", () -> c.spookyChestCompact, v -> c.spookyChestCompact = v);
                    b.toggle("  Play Sound", () -> c.spookyChestSound, v -> c.spookyChestSound = v);
                });
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        String color, type, chest;
        if (t.equals("SPOOKY! A Trick or Treat Chest has appeared!")) {
            color = "§6";
            type = "SPOOKY";
            chest = "TRICK OR TREAT CHEST";
        } else if (t.equals("PARTY! A Party Chest has appeared!")) {
            color = "§c";
            type = "PARTY";
            chest = "PARTY CHEST";
        } else {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setTimes(0, 40, 10);
        mc.gui.setTitle(Component.literal(color + "§l" + (cfg().spookyChestCompact ? type + " CHEST" : chest + "!")));
        if (cfg().spookyChestSound) com.teslamaps.utils.LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 2f);
    }
}
