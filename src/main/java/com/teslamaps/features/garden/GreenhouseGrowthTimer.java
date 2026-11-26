package com.teslamaps.features.garden;

// Ported from SkyHanni's GrowthCycle.kt and Devonian's GrowthStageTimer.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GreenhouseGrowthTimer {

    private static final Pattern NEXT = Pattern.compile("^Next Stage: ((?:\\d\\d?[hms] ?)+)$");
    private static final Pattern PART = Pattern.compile("(\\d+)([hms])");
    private static final long HIDE_AFTER_MS = 60 * 60_000L;
    private static final List<String> SAMPLE = List.of("§6Next Greenhouse Growth Stage: §a1h 40m");

    private static List<String> lines = List.of();
    private static int width;
    private static int ticks;
    private static HudEditElement hud;

    private GreenhouseGrowthTimer() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("greenhouseGrowthTimer", "Greenhouse Growth Stage").section("Garden")
                .enabled(() -> cfg().greenhouseGrowthTimer)
                .size(() -> TextHud.width(SAMPLE), () -> TextHud.height(1))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        Feature.of("GreenhouseGrowthTimer").section("Garden").when(() -> cfg().greenhouseGrowthTimer)
                .tick(GreenhouseGrowthTimer::tick)
                .hudHiddenInInventory("greenhousegrowth", GreenhouseGrowthTimer::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Greenhouse Growth Stage");
                    b.toggle("Greenhouse Growth Timer", () -> c.greenhouseGrowthTimer, v -> c.greenhouseGrowthTimer = v)
                            .desc("Reads \"Next Stage\" from the Crop Diagnostics menu and counts down to the next greenhouse growth stage, OVERDUE once it passed. Position in /tmap gui.")
                            .tags("greenhouse", "growth", "stage", "crop diagnostics", "timer");
                    b.toggle("  Only When Overdue", () -> c.greenhouseGrowthOnlyOverdue, v -> c.greenhouseGrowthOnlyOverdue = v);
                    b.toggle("  Notify When Ready", () -> c.greenhouseGrowthNotify, v -> c.greenhouseGrowthNotify = v)
                            .desc("Sound and chat line when the stage is ready");
                });
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        if (mc.gui.screen() instanceof AbstractContainerScreen<?> cs
                && StringUtil.stripTrim(cs.getTitle().getString()).equals("Crop Diagnostics")) {
            read(cs, c);
        }

        long now = System.currentTimeMillis();
        long end = c.greenhouseNextStage;
        if (end > 0 && now >= end && !c.greenhouseGrowthNotified) {
            c.greenhouseGrowthNotified = true;
            TeslaMapsConfig.save();
            if (c.greenhouseGrowthNotify && mc.player != null) {
                LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.5f);
                mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §aGreenhouse Growth Stage is ready in the Garden"));
            }
        }

        String text;
        if (end <= 0 || now - end > HIDE_AFTER_MS) {
            text = SkyblockUtils.isInGarden() && !c.greenhouseGrowthOnlyOverdue ? "§6Next Greenhouse Growth Stage: §cOpen Crop Diagnostics" : null;
        } else if (now >= end) {
            text = "§6Next Greenhouse Growth Stage: §cOVERDUE";
        } else {
            long left = end - now;
            text = c.greenhouseGrowthOnlyOverdue ? null
                    : "§6Next Greenhouse Growth Stage: " + (left > 10 * 60_000L ? "§a" : left > 60_000L ? "§e" : "§c") + format(left);
        }
        if (text == null) {
            if (!lines.isEmpty()) lines = List.of();
            return;
        }
        if (lines.isEmpty() || !lines.get(0).equals(text)) {
            lines = List.of(text);
            width = TextHud.width(lines);
        }
    }

    private static void read(AbstractContainerScreen<?> cs, TeslaMapsConfig c) {
        List<Slot> slots = cs.getMenu().slots;
        if (slots.size() <= 20) return;
        for (String line : ItemUtil.loreLines(slots.get(20).getItem())) {
            Matcher m = NEXT.matcher(line.trim());
            if (!m.matches()) continue;
            long secs = 0;
            Matcher p = PART.matcher(m.group(1));
            while (p.find()) {
                long v = Long.parseLong(p.group(1));
                secs += switch (p.group(2)) { case "h" -> v * 3600; case "m" -> v * 60; default -> v; };
            }
            long end = System.currentTimeMillis() + secs * 1000;
            if (Math.abs(end - c.greenhouseNextStage) > 5000) {
                c.greenhouseNextStage = end;
                c.greenhouseGrowthNotified = false;
                TeslaMapsConfig.save();
            }
            return;
        }
    }

    private static String format(long millis) {
        long s = millis / 1000;
        long h = s / 3600, m = s % 3600 / 60, sec = s % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || lines.isEmpty() || hud == null || !SkyblockUtils.isInSkyblock()) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
