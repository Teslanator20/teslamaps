package com.teslamaps.features;

// After Skyblocker's chocolatefactory/TimeTowerReminder.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeTowerReminder {

    private static final Pattern TOWER = Pattern.compile("^TIME TOWER! Your Chocolate Factory production has increased by \\+[\\d.]+x for (\\d+)h!$");
    private static int timer;

    private TimeTowerReminder() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Events", "Skyblock", true);
        Feature.of("TimeTowerReminder").section("Events").when(() -> cfg().timeTowerReminder)
                .tick(TimeTowerReminder::tick)
                .serverChat(TimeTowerReminder::onChat)
                .config("Events", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Chocolate Factory");
                    b.toggle("Time Tower Reminder", () -> c.timeTowerReminder, v -> c.timeTowerReminder = v)
                            .desc("Reminds you when your Time Tower runs out, so you can activate the next charge. Survives restarts.");
                });
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.startsWith("TIME TOWER!")) return;
        Matcher m = TOWER.matcher(t);
        if (!m.matches()) return;
        cfg().timeTowerEndsAt = System.currentTimeMillis() + Long.parseLong(m.group(1)) * 3_600_000L;
        TeslaMapsConfig.save();
    }

    private static void tick() {
        if (++timer % 20 != 0) return;
        long end = cfg().timeTowerEndsAt;
        if (end <= 0 || System.currentTimeMillis() < end || !SkyblockUtils.isInSkyblock()) return;
        cfg().timeTowerEndsAt = 0;
        TeslaMapsConfig.save();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.gui.setTimes(0, 50, 10);
        mc.gui.setTitle(Component.literal("§dTime Tower ran out"));
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §dYour Time Tower has run out! §eClick to open the Chocolate Factory.")
                .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/cf"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to run /cf")))));
        com.teslamaps.utils.LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.2f);
    }
}
