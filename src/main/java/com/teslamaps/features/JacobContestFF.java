package com.teslamaps.features;

// Ported from SkyHanni's JacobContestFFNeededDisplay.kt + FarmingContestApi.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JacobContestFF {

    private static final Pattern YOUR_CONTESTS = Pattern.compile("(?:\\(\\d+/\\d+\\) )?Your Contests");
    private static final Pattern TIME = Pattern.compile("(.*) (\\d+)(?:rd|st|nd|th), Year (\\d+)");
    private static final Pattern CROP = Pattern.compile("(.*) Contest");
    private static final String[] BRACKETS = {"DIAMOND", "PLATINUM", "GOLD", "SILVER", "BRONZE"};
    private static final String[] COLORS = {"§b", "§3", "§6", "§f", "§c"};
    private static final Pattern[] BRACKET_PATTERNS = new Pattern[BRACKETS.length];
    private static final String[] MONTHS = {"Early Spring", "Spring", "Late Spring", "Early Summer", "Summer", "Late Summer",
            "Early Autumn", "Autumn", "Late Autumn", "Early Winter", "Winter", "Late Winter"};

    static {
        for (int i = 0; i < BRACKETS.length; i++) {
            BRACKET_PATTERNS[i] = Pattern.compile(BRACKETS[i] + " \\(Top \\d{1,2}%\\): ([\\d,]+)");
        }
    }

    private record Contest(long time, CropStats.Crop crop, int[] brackets) {}

    private static final List<String> SAMPLE = List.of("§6Minimum Farming Fortune needed", "",
            "§7For this §eWheat §7contest:", " §b§lDIAMOND§f: §61,480 FF §7(1,812,000 crops)",
            " §6§lGOLD§f: §6980 FF §7(1,201,000 crops)", "", "§6Your latest Wheat FF: 1,520");

    private static final Map<String, Contest> contests = new HashMap<>();
    private static List<String> lines = List.of();
    private static Slot lastSlot;
    private static int width, ticks, lastBpsHash;
    private static HudEditElement hud;

    private JacobContestFF() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("jacobContestFF", "Jacob Contest FF").section("Garden")
                .enabled(() -> cfg().jacobContestFF)
                .size(() -> CropStats.linesWidth(SAMPLE), () -> CropStats.linesHeight(SAMPLE.size()))
                .preview((g, mc, x, y, s) -> CropStats.drawLines(g, mc, x, y, s, SAMPLE, CropStats.linesWidth(SAMPLE)));
        Feature.of("JacobContestFF").section("Garden").when(() -> cfg().jacobContestFF)
                .tick(JacobContestFF::tick)
                .hudForeground("jacobcontestff", JacobContestFF::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Jacob Contests");
                    b.toggle("Fortune Needed Per Medal", () -> c.jacobContestFF, v -> c.jacobContestFF = v)
                            .desc("Your Contests menu: hover a contest to see the farming fortune each medal needed, "
                                    + "for that contest and the average of your last 10 of that crop. Position in /tmap gui.");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)
                || !YOUR_CONTESTS.matcher(StringUtil.stripTrim(cs.getTitle().getString())).matches()) {
            if (!lines.isEmpty()) lines = List.of();
            lastSlot = null;
            return;
        }
        if (++ticks % 10 == 0) scan(cs);
        Slot hovered = ((HandledScreenAccessor) cs).getFocusedSlot();
        int bpsHash = cfg().cropLatestBps.hashCode() ^ cfg().cropLatestFortune.hashCode();
        if (hovered == lastSlot && bpsHash == lastBpsHash && ticks % 10 != 0) return;
        lastSlot = hovered;
        lastBpsHash = bpsHash;
        Contest contest = null;
        if (hovered != null && !(hovered.container instanceof Inventory) && !hovered.getItem().isEmpty()) {
            contest = parse(hovered.getItem());
        }
        lines = contest == null ? List.of() : build(contest);
        width = CropStats.linesWidth(lines);
    }

    private static void scan(AbstractContainerScreen<?> cs) {
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory || slot.getItem().isEmpty()) continue;
            Contest c = parse(slot.getItem());
            if (c != null) contests.putIfAbsent(StringUtil.stripTrim(slot.getItem().getHoverName().getString()), c);
        }
    }

    private static Contest parse(net.minecraft.world.item.ItemStack stack) {
        String name = StringUtil.stripTrim(stack.getHoverName().getString());
        Matcher tm = TIME.matcher(name);
        if (!tm.matches()) return null;
        int month = -1;
        for (int i = 0; i < MONTHS.length; i++) if (MONTHS[i].equals(tm.group(1))) month = i;
        long time = Long.parseLong(tm.group(3)) * 372 + Math.max(0, month) * 31L + Long.parseLong(tm.group(2));
        CropStats.Crop crop = null;
        int[] brackets = new int[BRACKETS.length];
        boolean scored = false;
        for (String raw : ItemUtil.loreLines(stack)) {
            String l = StringUtil.stripTrim(raw);
            if (l.startsWith("Your score: ")) scored = true;
            if (crop == null) {
                Matcher cm = CROP.matcher(l);
                if (cm.matches()) crop = CropStats.byName(cm.group(1));
            }
            for (int i = 0; i < BRACKETS.length; i++) {
                Matcher bm = BRACKET_PATTERNS[i].matcher(l);
                if (bm.find()) brackets[i] = Integer.parseInt(bm.group(1).replace(",", ""));
            }
        }
        if (crop == null || !scored) return null;
        return new Contest(time, crop, brackets);
    }

    private static List<String> build(Contest contest) {
        CropStats.Crop crop = contest.crop();
        List<String> out = new ArrayList<>();
        out.add("§6Minimum Farming Fortune needed");
        out.add("");
        out.add("§7For this §e" + crop.name() + " §7contest:");
        for (int i = 0; i < BRACKETS.length; i++) out.add(line(i, contest.brackets()[i], crop));
        out.add("");

        List<Contest> same = new ArrayList<>();
        for (Contest c : contests.values()) if (c.crop() == crop) same.add(c);
        same.sort((a, b) -> Long.compare(b.time(), a.time()));
        if (same.size() > 10) same = same.subList(0, 10);
        out.add("§7For the last §e" + same.size() + " " + crop.name() + " §7contests:");
        for (int i = 0; i < BRACKETS.length; i++) {
            long sum = 0;
            int n = 0;
            for (Contest c : same) {
                if (c.brackets()[i] <= 0) continue;
                sum += c.brackets()[i];
                n++;
            }
            out.add(line(i, n == 0 ? 0 : (int) (sum / n), crop));
        }
        out.add("");

        Double bps = CropStats.latestBps(crop);
        if (bps == null) {
            out.add("§cNo " + crop.name() + " blocks/second data,");
            out.add("§cassuming 19.9 instead.");
        } else if (bps < 15.0) {
            out.add("§cYour latest " + crop.name() + " blocks/second: §e" + CropStats.fmt(bps));
            out.add("§cThis is too low, showing 19.9 Blocks/second instead!");
            bps = 19.9;
        }
        out.add("");
        Double ff = CropStats.latestFortune(crop);
        if (ff == null) out.add("§cNo latest " + crop.name() + " FF saved!");
        else out.add("§6Your latest " + crop.name() + " FF: " + CropStats.fmt(Math.ceil(ff)));
        out.add("");
        if (bps == null || ff == null) {
            out.add("§cMissing data from above!");
        } else {
            long predicted = (long) ((100.0 + ff) * bps * crop.baseDrops() * 20 * 60 / 100);
            out.add("§6Predicted " + crop.name() + " crops: " + CropStats.fmt(predicted));
        }
        return out;
    }

    private static String line(int bracket, int counter, CropStats.Crop crop) {
        String label = " " + COLORS[bracket] + "§l" + BRACKETS[bracket] + "§f: ";
        if (counter <= 0) return label + "§8Not found!";
        Double b = CropStats.latestBps(crop);
        double bps = b == null ? 20.0 : b < 15.0 ? 19.9 : b;
        double perBlock = counter / bps / 60 / 20;
        double ff = Math.max(0, perBlock * 100 / crop.baseDrops() - 100);
        return label + "§6" + CropStats.fmt(Math.ceil(ff)) + " FF §7(" + CropStats.fmt(counter) + " crops)";
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> ls = lines;
        if (ls.isEmpty() || hud == null) return;
        CropStats.drawLines(g, Minecraft.getInstance(), hud.x(), hud.y(), hud.scale(), ls, width);
    }
}
