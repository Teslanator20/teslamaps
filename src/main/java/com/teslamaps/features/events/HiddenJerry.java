package com.teslamaps.features.events;

// After Skytils' MayorJerry.kt / MayorJerryTracker.kt and Skyblocker's JerryTimer.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mayor Jerry: hidden Jerry title + sound, cooldown timer until the next one can spawn, and a tracker. */
public final class HiddenJerry {

    private static final Pattern JERRY = Pattern.compile("\\b(Green|Blue|Purple|Golden) Jerry\\b");
    private static final Pattern CLAIMED = Pattern.compile("^☺ You claimed (?<what>.+) from the Jerry Box!$");
    private static final Pattern NUMBER = Pattern.compile("([\\d,]+)");
    private static final String[] COLORS = {"Green", "Blue", "Purple", "Golden"};
    private static final String[] CODES = {"§a", "§9", "§5", "§6"};
    private static final String SAMPLE = "§6Jerry: §e5:12";
    private static final long READY_MS = 6 * 60_000L, MIN_MS = 5 * 60_000L;

    private static Tracker tracker;
    private static HudEditElement timerHud;
    private static long lastJerry;
    private static boolean readyAnnounced = true;
    private static String timerLine;

    private HiddenJerry() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        tracker = Tracker.create("mayor_jerry", "§a§lJerry Tracker").maxRows(20);
        for (int i = 0; i < COLORS.length; i++) {
            String k = "JERRY_" + COLORS[i].toUpperCase();
            tracker.fixedValue(k, 0).name(k, CODES[i] + COLORS[i] + " Jerry");
        }
        timerHud = HudEditElement.of("hiddenJerryTimer", "Hidden Jerry Timer").section("Events")
                .enabled(() -> cfg().hiddenJerryAlert && cfg().hiddenJerryTimer)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .scaleRange(0.5f, 4f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        HudEditElement.of("jerryTracker", "Jerry Tracker").section("Events")
                .enabled(() -> cfg().hiddenJerryAlert && cfg().hiddenJerryTracker)
                .size(() -> Math.max(80, tracker.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, tracker.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> tracker.draw(g, mc, x, y, s));
        Feature.of("HiddenJerry").section("Events").when(() -> cfg().hiddenJerryAlert)
                .tick(HiddenJerry::tick)
                .serverChat(HiddenJerry::onChat)
                .hud("hiddenjerrytimer", HiddenJerry::renderTimer)
                .hud("jerrytracker", HiddenJerry::renderTracker)
                .config("Events", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Mayor Jerry");
                    b.toggle("Hidden Jerry Alert", () -> c.hiddenJerryAlert, v -> c.hiddenJerryAlert = v)
                            .desc("Jerrypocalypse: reacts to the \"Green / Blue / Purple / Golden Jerry\" line")
                            .tags("jerry", "jerrypocalypse", "mayor", "hidden jerry", "jerry box");
                    b.toggle("  Title", () -> c.hiddenJerryTitle, v -> c.hiddenJerryTitle = v);
                    b.toggle("  Sound", () -> c.hiddenJerrySound, v -> c.hiddenJerrySound = v)
                            .desc("Also plays when the next Jerry can spawn (6 minutes later)");
                    b.toggle("  Cooldown Timer HUD", () -> c.hiddenJerryTimer, v -> c.hiddenJerryTimer = v)
                            .desc("Time since the last Jerry, green \"Ready!\" after 6 minutes. Move it in /tmap gui.");
                    b.toggle("  Jerry Tracker", () -> c.hiddenJerryTracker, v -> c.hiddenJerryTracker = v)
                            .desc("Found Jerrys per color and what the Jerry Boxes gave. Move it in /tmap gui.");
                    b.trackerButtons(tracker);
                });
    }

    private static void tick() {
        tracker.tick();
        if (lastJerry == 0) { timerLine = null; return; }
        long since = System.currentTimeMillis() - lastJerry;
        if (since >= READY_MS) {
            timerLine = "§6Jerry: §aReady!";
            if (!readyAnnounced) {
                readyAnnounced = true;
                if (cfg().hiddenJerrySound) LoudSound.play(SoundEvents.VILLAGER_TRADE, 1f, 1f);
            }
        } else {
            long s = since / 1000;
            timerLine = "§6Jerry: " + (since >= MIN_MS ? "§e" : "§c") + (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
        }
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.startsWith("☺")) return;
        TeslaMapsConfig c = cfg();
        Matcher box = CLAIMED.matcher(t);
        if (box.matches()) {
            claimed(box.group("what"));
            return;
        }
        if (t.contains("Jerry Box")) return;
        Matcher m = JERRY.matcher(t);
        if (!m.find()) return;
        String color = m.group(1);
        int i = java.util.Arrays.asList(COLORS).indexOf(color);
        lastJerry = System.currentTimeMillis();
        readyAnnounced = false;
        tracker.add("JERRY_" + color.toUpperCase(), 1);
        tracker.markActivity();
        Minecraft mc = Minecraft.getInstance();
        if (c.hiddenJerryTitle) mc.gui.setTitle(Component.literal(CODES[i] + "§l" + color.toUpperCase() + " JERRY!"));
        if (c.hiddenJerrySound) LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f);
    }

    private static void claimed(String what) {
        Matcher n = NUMBER.matcher(what);
        long amount = n.find() ? Long.parseLong(n.group(1).replace(",", "")) : 1;
        if (what.endsWith(" coins")) {
            tracker.addCoins(amount);
        } else if (what.contains(" XP")) {
            String label = what.replaceFirst("^[\\d,]+\\s*", "").trim();
            tracker.fixedValue(label, 0).name(label, "§b" + label);
            tracker.add(label, amount);
        } else {
            String name = what.replaceFirst("^(?:an? |[\\d,]+x? )", "").trim();
            String id = SkyblockItems.idForName(name);
            if (id != null) {
                tracker.add(id, what.matches("^[\\d,]+x? .*") ? amount : 1);
            } else {
                tracker.fixedValue(name, 0).name(name, "§f" + name);
                tracker.add(name, 1);
            }
        }
        tracker.markActivity();
    }

    private static void renderTimer(GuiGraphicsExtractor g, DeltaTracker d) {
        String line = timerLine;
        if (line == null || !cfg().hiddenJerryTimer || timerHud == null || !SkyblockUtils.isInSkyblock()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, timerHud.x(), timerHud.y(), timerHud.scale(), line);
    }

    private static void renderTracker(GuiGraphicsExtractor g, DeltaTracker d) {
        TeslaMapsConfig c = cfg();
        if (!c.hiddenJerryTracker || !SkyblockUtils.isInSkyblock() || lastJerry == 0 && !"Jerry".equalsIgnoreCase(com.teslamaps.api.ElectionApi.mayor())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || com.teslamaps.utils.HudVisibility.suppressed()) return;
        tracker.draw(g, mc, c.jerryTrackerX, c.jerryTrackerY, c.jerryTrackerScale);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(mc.font, text, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
