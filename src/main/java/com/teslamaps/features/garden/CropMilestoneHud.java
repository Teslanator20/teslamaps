package com.teslamaps.features.garden;

// After SkyHanni's GardenCropMilestoneDisplay and CropMilestonesApi
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.FarmSpeed;
import com.teslamaps.features.GardenMilestoneData;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.features.pets.PetTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.LineOrder;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Milestone tier, progress and time to the next tier for the crop in hand, plus the Mooshroom Cow perk. */
public final class CropMilestoneHud {

    private static final String ID = "cropMilestone";
    private static final String[] KEYS = {"title", "tier", "progress", "percent", "time", "cps", "cpm", "cph", "bps"};
    private static final String[] LABELS = {"Title", "Milestone Tier", "Progress x/y", "Percentage", "Time To Next Tier",
            "Crops/Second", "Crops/Minute", "Crops/Hour", "Blocks/Second"};
    private static final List<String> SAMPLE = List.of("§6Crop Milestones", "§7Wheat §830➜§331", "§e412,334§8/§e800,000",
            "§7Percentage: §e51.5%", "§7In §b6m 28s", "§7Crops/Second§8: §e1,002");
    private static final List<String> SAMPLE_COW = List.of("§6Mooshroom Cow Perk", "§7Mushroom §825➜§326", "§7In §b1h 3m");

    private static final ArrayDeque<long[]> samples = new ArrayDeque<>();
    private static HudEditElement hud, cowHud;
    private static List<String> lines = List.of(), cowLines = List.of();
    private static int width, cowWidth, ticks, warnedTier = -1;
    private static String crop;
    private static long lastGain;

    private CropMilestoneHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        LineOrder.define(ID, KEYS, LABELS);
        hud = HudEditElement.of("cropMilestoneHud", "Crop Milestone").ctx(HudEditElement.HudCtx.GARDEN).section("Garden")
                .enabled(() -> cfg().cropMilestoneHud)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width,
                        () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> {
                    if (lines.isEmpty()) TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE));
                    else TextHud.draw(g, mc, x, y, s, lines, width);
                });
        cowHud = HudEditElement.of("mooshroomPerkHud", "Mooshroom Cow Perk").ctx(HudEditElement.HudCtx.GARDEN).section("Garden")
                .enabled(() -> cfg().mooshroomPerkHud)
                .size(() -> cowLines.isEmpty() ? TextHud.width(SAMPLE_COW) : cowWidth,
                        () -> TextHud.height(cowLines.isEmpty() ? SAMPLE_COW.size() : cowLines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE_COW, TextHud.width(SAMPLE_COW)));
        Feature.of("CropMilestoneHud").section("Garden")
                .when(() -> cfg().cropMilestoneHud || cfg().mooshroomPerkHud)
                .tick(CropMilestoneHud::tick)
                .hudHiddenInInventory("cropmilestonehud", CropMilestoneHud::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Crop Milestones");
                    b.toggle("Crop Milestone HUD", () -> c.cropMilestoneHud, v -> c.cropMilestoneHud = v)
                            .desc("Tier, progress, time to the next tier and crops per second for the crop of the tool in hand. "
                                    + "Open /cropmilestones once for exact numbers, otherwise the tab widget percentage is used.")
                            .tags("milestone", "crops per second", "bps");
                    b.lineOrder("  Lines", ID);
                    b.slider("  Goal Tier (0 = next)", 0f, 100f, () -> (float) c.cropMilestoneGoal,
                            v -> c.cropMilestoneGoal = Math.round(v), 0);
                    b.toggle("  Overflow Past 46", () -> c.cropMilestoneHudOverflow, v -> c.cropMilestoneHudOverflow = v);
                    b.toggle("  Warn Before Tier Up", () -> c.cropMilestoneWarn, v -> c.cropMilestoneWarn = v)
                            .desc("Title when the next tier is less than 6 seconds of farming away.");
                    b.toggle("Mooshroom Cow Perk HUD", () -> c.mooshroomPerkHud, v -> c.mooshroomPerkHud = v)
                            .desc("Mushroom milestone progress while a Mooshroom Cow pet farms other crops.");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!SkyblockUtils.isInGarden() || mc.player == null) {
            lines = List.of();
            cowLines = List.of();
            ticks++;
            return;
        }
        String c = GardenMilestoneData.heldToolCrop();
        if (c != null) crop = c;
        if (++ticks % 10 != 0) return;
        build();
    }

    /** From GardenMilestoneData, milestone crops gained by the tool in hand. */
    public static void onGain(long gain) {
        if (gain <= 0 || gain > 100_000) return;
        long now = System.currentTimeMillis();
        samples.addLast(new long[]{now, gain});
        lastGain = now;
    }

    private static double cropsPerSecond() {
        long now = System.currentTimeMillis();
        while (!samples.isEmpty() && now - samples.peekFirst()[0] > 10_000) samples.pollFirst();
        if (samples.isEmpty() || now - lastGain > 3000) return 0;
        long sum = 0;
        for (long[] s : samples) sum += s[1];
        double span = Math.max(1, (now - samples.peekFirst()[0]) / 1000.0 + 0.5);
        return sum / Math.max(1.0, span);
    }

    private record Info(int tier, int next, long have, long need, boolean maxed) {}

    private static Info info(String name) {
        TeslaMapsConfig c = cfg();
        long total = GardenMilestoneData.counter(name);
        if (total < 0) return null;
        int tier = GardenMilestoneData.tierOf(name, total);
        boolean maxed = tier >= GardenMilestoneData.maxTier(name) && !c.cropMilestoneHudOverflow;
        int next = c.cropMilestoneGoal > tier ? c.cropMilestoneGoal : tier + 1;
        long base = GardenMilestoneData.totalForTier(name, tier);
        long need = GardenMilestoneData.totalForTier(name, next) - base;
        long have = total - base;
        if (c.cropMilestoneGoal > tier + 1) { have = total; need = GardenMilestoneData.totalForTier(name, next); }
        return new Info(tier, next, have, need, maxed);
    }

    private static void build() {
        TeslaMapsConfig c = cfg();
        if (c.cropMilestoneHud && crop != null) {
            Info in = info(crop);
            double cps = cropsPerSecond();
            LineOrder.Lines l = LineOrder.lines(ID);
            l.key("title").add("§6Crop Milestones");
            if (in == null) {
                l.key("tier").add("§cOpen §e/cropmilestones §cto update!");
            } else {
                l.key("tier").add(in.maxed ? "§7" + crop + " §eMAXED" : "§7" + crop + " §8" + in.tier + "➜§3" + in.next);
                if (!in.maxed) {
                    l.key("progress").add("§e" + fmt(in.have) + "§8/§e" + fmt(in.need));
                    double pct = in.need <= 0 ? 100 : in.have * 100.0 / in.need;
                    l.key("percent").add("§7Percentage: §e" + String.format(Locale.ROOT, "%.1f%%", pct));
                    if (cps > 0) {
                        long secs = (long) Math.max(0, (in.need - in.have) / cps);
                        l.key("time").add("§7In §b" + Composter.format(secs * 1000));
                        warn(c, in, secs);
                    }
                }
            }
            if (cps > 0) {
                l.key("cps").add("§7Crops/Second§8: §e" + fmt((long) cps));
                l.key("cpm").add("§7Crops/Minute§8: §e" + fmt((long) (cps * 60)));
                l.key("cph").add("§7Crops/Hour§8: §e" + fmt((long) (cps * 3600)));
            }
            double bps = FarmSpeed.blocksPerSecond();
            if (bps > 0) l.key("bps").add("§7Blocks/Second§8: §e" + String.format(Locale.ROOT, "%.1f", bps));
            lines = l.build();
            width = TextHud.width(lines);
        } else {
            lines = List.of();
        }
        String pet = PetTracker.displayName();
        if (c.mooshroomPerkHud && crop != null && !crop.equals("Mushroom")
                && pet != null && StringUtil.strip(pet).contains("Mooshroom Cow")) {
            Info in = info("Mushroom");
            List<String> out = new ArrayList<>(4);
            out.add("§6Mooshroom Cow Perk");
            if (in == null) {
                out.add("§cOpen §e/cropmilestones §cto update!");
            } else {
                out.add(in.maxed ? "§7Mushroom §eMAXED" : "§7Mushroom §8" + in.tier + "➜§3" + in.next);
                if (!in.maxed) {
                    out.add("§e" + fmt(in.have) + "§8/§e" + fmt(in.need));
                    double bps = FarmSpeed.blocksPerSecond()
                            * (crop.equals("Sugar Cane") || crop.equals("Cactus") ? 2 : 1);
                    if (bps > 0) out.add("§7In §b" + Composter.format((long) ((in.need - in.have) / bps * 1000)));
                }
            }
            cowLines = out;
            cowWidth = TextHud.width(out);
        } else {
            cowLines = List.of();
        }
    }

    private static void warn(TeslaMapsConfig c, Info in, long secs) {
        if (!c.cropMilestoneWarn || secs > 6 || in.tier == warnedTier) return;
        warnedTier = in.tier;
        Minecraft mc = Minecraft.getInstance();
        mc.gui.hud.setTimes(0, 60, 10);
        mc.gui.hud.setTitle(Component.literal("§b" + crop + " " + in.next));
        mc.gui.hud.setSubtitle(Component.literal("§7in " + secs + "s"));
    }

    private static String fmt(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        if (!lines.isEmpty() && hud != null) TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
        if (!cowLines.isEmpty() && cowHud != null) TextHud.draw(g, mc, cowHud.x(), cowHud.y(), cowHud.scale(), cowLines, cowWidth);
    }
}
