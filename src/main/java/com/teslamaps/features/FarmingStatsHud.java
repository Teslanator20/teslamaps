package com.teslamaps.features;

// Counter/crops-per-minute after Skyblocker's FarmingHud.java, contest estimate after NEU's FarmingSkillOverlay.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FarmingStatsHud {

    private static final int LINE = 10;
    private static final long WINDOW_MS = 10_000L;
    private static final int RING = 128;
    private static final Pattern CONTEST = Pattern.compile("(?:Collected|(?:BRONZE|SILVER|GOLD|PLATINUM|DIAMOND) with) ([\\d,]+)");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width;
    private static int ticks;

    private static final long[] counts = new long[RING];
    private static final long[] times = new long[RING];
    private static int head, size;
    private static String counterKey = "";
    private static double bps;

    private FarmingStatsHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("farmingStatsHud", "Farming HUD").ctx(HudEditElement.HudCtx.GARDEN).section("Garden")
                .enabled(() -> cfg().farmingStatsHud)
                .size(() -> Math.max(width, 110) + 4, () -> Math.max(lines.size(), 4) * LINE + 2)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, List.of("§6Cultivating: §f12,345,678",
                        "§6Crops/min: §f1,950", "§6Yaw: §f-90.00 §6Pitch: §f3.00", "§6Contest Estimate: §e1,204,000"), 150));
        Feature.of("FarmingStatsHud").section("Garden").when(() -> cfg().farmingStatsHud)
                .tick(FarmingStatsHud::tick)
                .hudHiddenInInventory("farmingstatshud", FarmingStatsHud::render)
                .config("Garden", b -> {
                    b.label("Farming HUD");
                    b.toggle("Farming HUD", () -> cfg().farmingStatsHud, v -> cfg().farmingStatsHud = v)
                            .desc("Cultivating counter, crops per minute from the tool counter, yaw/pitch and a Jacob contest estimate. Move it in /tmap gui.")
                            .tags("yaw", "pitch", "cultivating", "counter", "contest", "jacob");
                    b.toggle("  Tool Counter", () -> cfg().farmingStatsCounter, v -> cfg().farmingStatsCounter = v);
                    b.toggle("  Crops Per Minute", () -> cfg().farmingStatsCropsMin, v -> cfg().farmingStatsCropsMin = v);
                    b.toggle("  Blocks Per Second", () -> cfg().farmingStatsBps, v -> cfg().farmingStatsBps = v);
                    b.toggle("  Yaw / Pitch", () -> cfg().farmingStatsYawPitch, v -> cfg().farmingStatsYawPitch = v);
                    b.toggle("  Contest Estimate", () -> cfg().farmingStatsContest, v -> cfg().farmingStatsContest = v);
                    b.toggle("  Mouse Locked Hint", () -> cfg().farmingStatsMouseLock, v -> cfg().farmingStatsMouseLock = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!SkyblockUtils.isInGarden() || mc.player == null) {
            lines = List.of();
            size = 0;
            return;
        }
        TeslaMapsConfig c = cfg();
        long now = System.currentTimeMillis();
        long counter = readCounter(mc.player.getMainHandItem(), now);
        if (++ticks % 10 == 0 && c.farmingStatsBps) bps = FarmSpeed.blocksPerSecond();
        if (ticks % 2 != 0) return;

        double cpm = cropsPerMinute(now);
        List<String> out = new ArrayList<>(6);
        if (c.farmingStatsCounter) {
            out.add(counter < 0 ? "§7Cultivating needed"
                    : "§6" + (counterKey.equals("farmed_cultivating") ? "Cultivating" : "Counter") + ": §f"
                    + String.format(Locale.ROOT, "%,d", counter));
        }
        if (c.farmingStatsCropsMin) out.add("§6Crops/min: §f" + String.format(Locale.ROOT, "%,.0f", cpm));
        if (c.farmingStatsBps) out.add("§6Blocks/s: §f" + String.format(Locale.ROOT, "%.1f", bps));
        if (c.farmingStatsYawPitch) {
            out.add("§6Yaw: §f" + String.format(Locale.ROOT, "%.2f", Mth.wrapDegrees(mc.player.getYRot()))
                    + " §6Pitch: §f" + String.format(Locale.ROOT, "%.2f", mc.player.getXRot()));
        }
        if (c.farmingStatsContest) {
            String est = contestEstimate(now, cpm);
            if (est != null) out.add("§6Contest Estimate: §e" + est);
        }
        if (c.farmingStatsMouseLock && FarmingSensitivity.blocksTurning()) out.add("§cMouse Locked");
        int w = 0;
        for (String s : out) w = Math.max(w, mc.font.width(s));
        width = w;
        lines = out;
    }

    private static long readCounter(ItemStack stack, long now) {
        CustomData data = stack.isEmpty() ? null : stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return -1;
        CompoundTag tag = data.copyTag();
        String key = tag.contains("farmed_cultivating") ? "farmed_cultivating" : tag.contains("mined_crops") ? "mined_crops" : null;
        if (key == null) return -1;
        long value = tag.getLongOr(key, 0L);
        if (!key.equals(counterKey)) {
            counterKey = key;
            size = 0;
        }
        int last = (head - 1 + RING) % RING;
        if (size == 0 || counts[last] != value) {
            if (size > 0 && value < counts[last]) size = 0;
            counts[head] = value;
            times[head] = now;
            head = (head + 1) % RING;
            if (size < RING) size++;
        }
        return value;
    }

    private static double cropsPerMinute(long now) {
        if (size < 2) return 0;
        int last = (head - 1 + RING) % RING;
        if (now - times[last] > 3_000L) return 0;
        int first = last;
        for (int i = 1; i < size; i++) {
            int idx = (last - i + RING) % RING;
            if (now - times[idx] > WINDOW_MS) break;
            first = idx;
        }
        long dt = times[last] - times[first];
        if (dt <= 0) return 0;
        return (counts[last] - counts[first]) * 60_000.0 / dt;
    }

    private static String contestEstimate(long now, double cpm) {
        long inHour = now % 3_600_000L;
        if (inHour < 900_000L || inHour > 2_100_000L) return null;
        long collected = -1;
        for (String raw : ScoreboardUtils.getScoreboardLines()) {
            Matcher m = CONTEST.matcher(ScoreboardUtils.cleanLine(raw));
            if (!m.find()) continue;
            String amount = m.group(1);
            int lastComma = amount.lastIndexOf(',');
            int extraZeros = lastComma != -1 ? 4 + lastComma - amount.length() : 0;
            try {
                collected = Long.parseLong(amount.replace(",", "")) * (long) Math.pow(10, Math.max(0, extraZeros));
            } catch (NumberFormatException ignored) {}
            break;
        }
        if (collected < 0) return null;
        if (cpm <= 0) return "N/A";
        long secondsLeft = Math.max(0, (2_100_000L - inHour) / 1000L);
        return String.format(Locale.ROOT, "%,d", collected + (long) (cpm / 60.0 * secondsLeft));
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (hud == null || lines.isEmpty() || mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l, int w) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, l.size() * LINE + 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * LINE, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
