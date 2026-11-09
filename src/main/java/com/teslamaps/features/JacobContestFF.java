package com.teslamaps.features;

// Ported from SkyHanni's JacobContestFFNeededDisplay.kt + FarmingContestApi.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.garden.JacobContestMenus;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class JacobContestFF {

    private static final Pattern YOUR_CONTESTS = Pattern.compile("(?:\\(\\d+/\\d+\\) )?Your Contests");
    private static final String[] COLORS = {"§b", "§3", "§6", "§f", "§c"};

    private record Contest(long time, CropStats.Crop crop, int[] brackets) {}

    private static final List<String> SAMPLE = List.of("§6Minimum Farming Fortune needed", "",
            "§7For this §eWheat §7contest:", " §b§lDIAMOND§f: §61,480 FF §7(1,812,000 crops)",
            " §6§lGOLD§f: §6980 FF §7(1,201,000 crops)", "", "§6Your latest Wheat FF: 1,520");

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
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)
                || !YOUR_CONTESTS.matcher(StringUtil.stripTrim(cs.getTitle().getString())).matches()) {
            if (!lines.isEmpty()) lines = List.of();
            lastSlot = null;
            return;
        }
        ++ticks;
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

    private static Contest contest(long time, JacobContestMenus.Contest c) {
        CropStats.Crop crop = c.crop() == null ? null : CropStats.byName(c.crop().cropName);
        if (crop == null) return null;
        JacobContestMenus.Bracket[] all = JacobContestMenus.Bracket.values();
        int[] brackets = new int[all.length];
        for (int i = 0; i < all.length; i++) brackets[i] = c.brackets().getOrDefault(all[i], 0);
        return new Contest(time, crop, brackets);
    }

    private static Contest parse(net.minecraft.world.item.ItemStack stack) {
        long time = JacobContestMenus.sbTime(StringUtil.stripTrim(stack.getHoverName().getString()));
        if (time <= 0) return null;
        JacobContestMenus.Contest c = JacobContestMenus.contests().get(time);
        return c == null ? null : contest(time, c);
    }

    private static List<String> build(Contest contest) {
        CropStats.Crop crop = contest.crop();
        List<String> out = new ArrayList<>();
        out.add("§6Minimum Farming Fortune needed");
        out.add("");
        out.add("§7For this §e" + crop.name() + " §7contest:");
        for (int i = 0; i < COLORS.length; i++) out.add(line(i, contest.brackets()[i], crop));
        out.add("");

        List<Contest> same = new ArrayList<>();
        for (Map.Entry<Long, JacobContestMenus.Contest> e : JacobContestMenus.contests().entrySet()) {
            Contest c = contest(e.getKey(), e.getValue());
            if (c != null && c.crop() == crop) same.add(c);
        }
        same.sort((a, b) -> Long.compare(b.time(), a.time()));
        if (same.size() > 10) same = same.subList(0, 10);
        out.add("§7For the last §e" + same.size() + " " + crop.name() + " §7contests:");
        for (int i = 0; i < COLORS.length; i++) {
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
        String label = " " + COLORS[bracket] + "§l" + JacobContestMenus.Bracket.values()[bracket].name() + "§f: ";
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
