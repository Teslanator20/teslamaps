package com.teslamaps.features.garden;

// After SkyHanni's GardenCropMilestoneDisplay and CropMilestonesApi
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.FarmSpeed;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.features.pets.PetTracker;
import com.teslamaps.profileviewer.data.GardenRegistry;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.LineOrder;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Milestone tier, progress and time to the next tier for the crop in hand, plus the Mooshroom Cow perk. */
public final class CropMilestoneHud {

    private static final String ID = "cropMilestone";
    private static final String[] KEYS = {"title", "tier", "progress", "percent", "time", "cps", "cpm", "cph", "bps"};
    private static final String[] LABELS = {"Title", "Milestone Tier", "Progress x/y", "Percentage", "Time To Next Tier",
            "Crops/Second", "Crops/Minute", "Crops/Hour", "Blocks/Second"};
    private static final Pattern TAB = Pattern.compile("^([A-Za-z ]+) (\\d+): (?:([\\d.]+)%|MAX)$");
    private static final Pattern HARVEST = Pattern.compile("^Harvest (.+?) on ");
    private static final Pattern TOTAL = Pattern.compile("^Total: ([\\d,]+)");
    private static final String[][] TOOLS = {
            {"THEORETICAL_HOE_WHEAT", "WHEAT"}, {"THEORETICAL_HOE_CARROT", "CARROT_ITEM"},
            {"THEORETICAL_HOE_POTATO", "POTATO_ITEM"}, {"THEORETICAL_HOE_WARTS", "NETHER_STALK"},
            {"THEORETICAL_HOE_CANE", "SUGAR_CANE"}, {"THEORETICAL_HOE_WILD_ROSE", "WILD_ROSE"},
            {"THEORETICAL_HOE_SUNFLOWER", "DOUBLE_PLANT"}, {"PUMPKIN_DICER", "PUMPKIN"}, {"MELON_DICER", "MELON"},
            {"COCO_CHOPPER", "INK_SACK:3"}, {"CACTUS_KNIFE", "CACTUS"}, {"FUNGI_CUTTER", "MUSHROOM_COLLECTION"}};
    private static final List<String> SAMPLE = List.of("§6Crop Milestones", "§7Wheat §830➜§331", "§e412,334§8/§e800,000",
            "§7Percentage: §e51.5%", "§7In §b6m 28s", "§7Crops/Second§8: §e1,002");
    private static final List<String> SAMPLE_COW = List.of("§6Mooshroom Cow Perk", "§7Mushroom §825➜§326", "§7In §b1h 3m");

    private record TabTier(int tier, double percent, boolean max) {}

    private static final Map<String, TabTier> tabTiers = new HashMap<>();
    private static final ArrayDeque<long[]> samples = new ArrayDeque<>();
    private static HudEditElement hud, cowHud;
    private static List<String> lines = List.of(), cowLines = List.of();
    private static int width, cowWidth, ticks, warnedTier = -1;
    private static String crop, lastToolUuid;
    private static long lastCounter = -1, lastGain;

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
        if (mc.screen instanceof AbstractContainerScreen<?> cs && ticks % 10 == 0) readMenu(cs);
        if (!SkyblockUtils.isInGarden() || mc.player == null) {
            lines = List.of();
            cowLines = List.of();
            ticks++;
            return;
        }
        ItemStack held = mc.player.getMainHandItem();
        String c = cropOf(ItemUtil.skyblockId(held));
        if (c != null) {
            crop = c;
            counter(held, c);
        }
        if (++ticks % 10 != 0) return;
        readTab();
        build();
    }

    private static String cropOf(String id) {
        if (id == null || id.isEmpty()) return null;
        for (String[] t : TOOLS) if (id.startsWith(t[0])) return t[1];
        return null;
    }

    private static void counter(ItemStack held, String c) {
        CustomData d = held.get(DataComponents.CUSTOM_DATA);
        if (d == null) return;
        var tag = d.copyTag();
        long value = tag.getLongOr("farmed_cultivating", -1L);
        if (value < 0) value = tag.getLongOr("mined_crops", -1L);
        if (value < 0) return;
        String uuid = ItemUtil.itemUuid(held);
        if (!java.util.Objects.equals(uuid, lastToolUuid)) {
            lastToolUuid = uuid;
            lastCounter = value;
            return;
        }
        long gain = value - lastCounter;
        lastCounter = value;
        if (gain <= 0 || gain > 100_000) return;
        long now = System.currentTimeMillis();
        samples.addLast(new long[]{now, gain});
        lastGain = now;
        Long total = cfg().cropMilestoneTotals.get(c);
        if (total != null) cfg().cropMilestoneTotals.put(c, total + gain);
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

    private static void readMenu(AbstractContainerScreen<?> cs) {
        if (!StringUtil.stripTrim(cs.getTitle().getString()).startsWith("Crop Milestones")) return;
        boolean changed = false;
        for (Slot s : cs.getMenu().slots) {
            if (s.container instanceof Inventory || !s.hasItem()) continue;
            String cropKey = null;
            long total = -1;
            for (String l : ItemUtil.loreLines(s.getItem())) {
                String t = l.trim();
                Matcher h = HARVEST.matcher(t);
                if (h.find()) cropKey = keyForName(h.group(1));
                Matcher m = TOTAL.matcher(t);
                if (m.find()) total = Long.parseLong(m.group(1).replace(",", ""));
            }
            if (cropKey == null || total < 0) continue;
            Long old = cfg().cropMilestoneTotals.put(cropKey, total);
            if (old == null || old != total) changed = true;
        }
        if (changed) TeslaMapsConfig.save();
    }

    private static String keyForName(String name) {
        String n = name.trim();
        if (n.equals("Melon Slice")) n = "Melon";
        if (n.equals("Red Mushroom") || n.equals("Brown Mushroom")) n = "Mushroom";
        for (GardenRegistry.Crop c : GardenRegistry.CROPS) if (c.display().equalsIgnoreCase(n)) return c.key();
        return null;
    }

    private static void readTab() {
        for (String line : TabListUtils.getTabListLines()) {
            String s = StringUtil.stripTrim(line);
            if (s.indexOf(':') < 0) continue;
            Matcher m = TAB.matcher(s);
            if (!m.matches()) continue;
            String key = keyForName(m.group(1));
            if (key == null) continue;
            tabTiers.put(key, new TabTier(Integer.parseInt(m.group(2)),
                    m.group(3) == null ? 100 : Double.parseDouble(m.group(3)), m.group(3) == null));
        }
    }

    private static String display(String key) {
        for (GardenRegistry.Crop c : GardenRegistry.CROPS) if (c.key().equals(key)) return c.display();
        return key;
    }

    private record Info(int tier, int next, long have, long need, boolean maxed, boolean exact) {}

    private static Info info(String key) {
        TeslaMapsConfig c = cfg();
        Long total = c.cropMilestoneTotals.get(key);
        if (total != null) {
            GardenRegistry.Milestone ms = GardenRegistry.milestone(key, total);
            int tier = ms.tier();
            boolean maxed = tier >= 46 && !c.cropMilestoneHudOverflow;
            int next = c.cropMilestoneGoal > tier ? c.cropMilestoneGoal : tier + 1;
            long need = totalFor(key, next) - totalFor(key, tier);
            long have = total - totalFor(key, tier);
            if (c.cropMilestoneGoal > tier + 1) { have = total; need = totalFor(key, next); }
            return new Info(tier, next, have, need, maxed, true);
        }
        TabTier t = tabTiers.get(key);
        if (t == null) return null;
        long need = totalFor(key, t.tier + 1) - totalFor(key, t.tier);
        return new Info(t.tier, t.tier + 1, (long) (need * t.percent / 100), need,
                t.max && !c.cropMilestoneHudOverflow, false);
    }

    /** Crops needed from zero to reach a tier, overflowing at the last bracket. */
    private static long totalFor(String key, int tier) {
        long lo = 0, hi = 1L << 40;
        while (lo < hi) {
            long mid = (lo + hi) >>> 1;
            if (GardenRegistry.milestone(key, mid).tier() >= tier) hi = mid;
            else lo = mid + 1;
        }
        return lo;
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
                l.key("tier").add(in.maxed ? "§7" + display(crop) + " §eMAXED" : "§7" + display(crop) + " §8" + in.tier + "➜§3" + in.next);
                if (!in.maxed) {
                    l.key("progress").add("§e" + fmt(in.have) + "§8/§e" + fmt(in.need) + (in.exact ? "" : " §8~"));
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
        if (c.mooshroomPerkHud && crop != null && !crop.equals("MUSHROOM_COLLECTION")
                && pet != null && StringUtil.strip(pet).contains("Mooshroom Cow")) {
            Info in = info("MUSHROOM_COLLECTION");
            List<String> out = new ArrayList<>(4);
            out.add("§6Mooshroom Cow Perk");
            if (in == null) {
                out.add("§cOpen §e/cropmilestones §cto update!");
            } else {
                out.add(in.maxed ? "§7Mushroom §eMAXED" : "§7Mushroom §8" + in.tier + "➜§3" + in.next);
                if (!in.maxed) {
                    out.add("§e" + fmt(in.have) + "§8/§e" + fmt(in.need));
                    double bps = FarmSpeed.blocksPerSecond()
                            * (crop.equals("SUGAR_CANE") || crop.equals("CACTUS") ? 2 : 1);
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
        mc.gui.setTimes(0, 60, 10);
        mc.gui.setTitle(Component.literal("§b" + display(crop) + " " + in.next));
        mc.gui.setSubtitle(Component.literal("§7in " + secs + "s"));
    }

    private static String fmt(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (!lines.isEmpty() && hud != null) TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
        if (!cowLines.isEmpty() && cowHud != null) TextHud.draw(g, mc, cowHud.x(), cowHud.y(), cowHud.scale(), cowLines, cowWidth);
    }
}
