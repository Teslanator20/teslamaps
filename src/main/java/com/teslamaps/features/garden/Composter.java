package com.teslamaps.features.garden;

// After SkyHanni's ComposterDisplay, ComposterApi and ComposterUpgradesData
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.NumberUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Composter tab widget as a HUD, time until empty, low / empty warnings. */
public final class Composter {

    public static final String SPEED = "Composter Speed", MULTI = "Multi Drop", FUEL_CAP = "Fuel Cap",
            MATTER_CAP = "Organic Matter Cap", COST = "Cost Reduction";
    private static final String[] UPGRADES = {SPEED, MULTI, FUEL_CAP, MATTER_CAP, COST};
    private static final Pattern UPGRADE = Pattern.compile("^(Composter Speed|Multi Drop|Fuel Cap|Organic Matter Cap|Cost Reduction)(?: ([IVXLCDM]+|\\d+))?$");
    private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*([dhms])");
    private static final long WARN_LOW_MS = 5 * 60_000L, WARN_EMPTY_MS = 2 * 60_000L, ALMOST_MS = 20 * 60_000L;
    private static final List<String> SAMPLE = List.of("§bComposter", "§7Time Left: §e4m 12s",
            "§eMatter: §f120k §8| §2Fuel: §f80k", "§aStored Compost: §f12", "§7Empty in §b1d 3h");
    private static final List<String> SAMPLE_OUT = List.of("§bComposter: §f3h 20m");

    static String matterText = "", fuelText = "", timeText = "", storedText = "";
    private static double matter = -1, fuel = -1;
    private static boolean found;
    private static List<String> lines = List.of(), outLines = List.of();
    private static int width, outWidth, ticks;
    private static long lowMatterAt, lowFuelAt, emptyWarnAt;
    private static HudEditElement hud, outHud;

    private Composter() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("composterHud", "Composter").ctx(HudEditElement.HudCtx.GARDEN).section("Garden")
                .enabled(() -> cfg().composterHud)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width,
                        () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> {
                    if (lines.isEmpty()) TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE));
                    else TextHud.draw(g, mc, x, y, s, lines, width);
                });
        outHud = HudEditElement.of("composterOutside", "Composter Timer").section("Garden")
                .enabled(() -> cfg().composterOutside)
                .size(() -> outLines.isEmpty() ? TextHud.width(SAMPLE_OUT) : outWidth, () -> TextHud.height(1))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE_OUT, TextHud.width(SAMPLE_OUT)));
        Feature.of("Composter").section("Garden")
                .when(() -> cfg().composterHud || cfg().composterOutside || cfg().composterLowWarn
                        || cfg().composterEmptyWarn || cfg().composterOverlay)
                .tick(Composter::tick)
                .hudHiddenInInventory("composterhud", Composter::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Composter");
                    b.toggle("Composter HUD", () -> c.composterHud, v -> c.composterHud = v)
                            .desc("Time left, organic matter, fuel and stored compost from the Composter tab widget, "
                                    + "plus the time until it runs empty. Open Composter Upgrades once for the estimate.")
                            .tags("compost", "organic matter", "fuel");
                    b.toggle("  Timer Outside The Garden", () -> c.composterOutside, v -> c.composterOutside = v)
                            .desc("Small line with the time until the composter is empty, shown everywhere but the Garden.");
                    b.toggle("  Low Matter / Fuel Warning", () -> c.composterLowWarn, v -> c.composterLowWarn = v)
                            .desc("Title and chat when organic matter or fuel drop under the limits below, at most every 5 minutes.");
                    b.slider("    Organic Matter Below", 0f, 200000f, () -> (float) c.composterLowMatter,
                            v -> c.composterLowMatter = Math.round(v / 1000f) * 1000, 0);
                    b.slider("    Fuel Below", 0f, 200000f, () -> (float) c.composterLowFuel,
                            v -> c.composterLowFuel = Math.round(v / 1000f) * 1000, 0);
                    b.toggle("  Almost Empty Warning", () -> c.composterEmptyWarn, v -> c.composterEmptyWarn = v)
                            .desc("Warns when the composter is empty or has less than 20 minutes left. "
                                    + "Outside the Garden the chat line warps you there on click.");
                });
    }

    private static void tick() {
        if (++ticks % 20 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> cs) readUpgrades(cs);
        boolean garden = SkyblockUtils.isInGarden();
        if (garden) readTab();
        long now = System.currentTimeMillis();
        TeslaMapsConfig c = cfg();

        if (garden && found) {
            long empty = estimateEmptyMs();
            if (empty >= 0) {
                boolean moved = Math.abs(c.composterEmptyAt - (now + empty)) > 60_000;
                c.composterEmptyAt = now + empty;
                if (moved) TeslaMapsConfig.save();
            }
            buildLines(empty);
            warnLow(c, now);
        } else if (!garden) {
            lines = List.of();
        }

        long at = c.composterEmptyAt;
        String left;
        if (at <= 0) left = "§7?";
        else if (at <= now) left = "§cempty!";
        else left = "§f" + format(at - now);
        outLines = List.of("§bComposter: " + left);
        outWidth = TextHud.width(outLines);

        if (c.composterEmptyWarn && at > 0 && SkyblockUtils.isInSkyblock() && now - emptyWarnAt >= WARN_EMPTY_MS) {
            if (at <= now) warnEmpty(mc, garden, "§cYour composter is empty!", now);
            else if (at - now < ALMOST_MS) warnEmpty(mc, garden, "§eYour composter in the Garden is almost empty!", now);
        }
    }

    private static void readTab() {
        List<String> tab = TabListUtils.getOrderedLines();
        int start = -1;
        for (int i = 0; i < tab.size(); i++) {
            if (StringUtil.stripTrim(tab.get(i)).equals("Composter:")) { start = i; break; }
        }
        if (start < 0) { found = false; return; }
        String m = null, f = null, t = null, s = null;
        for (int i = start + 1; i < tab.size(); i++) {
            String line = StringUtil.stripTrim(tab.get(i));
            if (line.isEmpty() || line.endsWith(":")) break;
            if (line.startsWith("Organic Matter:")) m = line.substring(15).trim();
            else if (line.startsWith("Fuel:")) f = line.substring(5).trim();
            else if (line.startsWith("Time Left:")) t = line.substring(10).trim();
            else if (line.startsWith("Stored Compost:")) s = line.substring(15).trim();
        }
        found = m != null || f != null || t != null;
        matterText = m == null ? "" : m;
        fuelText = f == null ? "" : f;
        timeText = t == null ? "" : t;
        storedText = s == null ? "" : s;
        matter = m == null ? -1 : NumberUtil.parseSuffixed(m);
        fuel = f == null ? -1 : NumberUtil.parseSuffixed(f);
    }

    private static void buildLines(long empty) {
        List<String> out = new ArrayList<>(5);
        out.add("§bComposter");
        if (!timeText.isEmpty()) out.add("§7Time Left: " + (timeText.equals("INACTIVE") ? "§cINACTIVE" : "§e" + timeText));
        if (!matterText.isEmpty() || !fuelText.isEmpty())
            out.add("§eMatter: §f" + (matterText.isEmpty() ? "?" : matterText) + " §8| §2Fuel: §f" + (fuelText.isEmpty() ? "?" : fuelText));
        if (!storedText.isEmpty()) out.add("§aStored Compost: §f" + storedText);
        out.add(empty < 0 ? "§cOpen Composter Upgrades!" : "§7Empty in §b" + format(empty));
        lines = out;
        width = TextHud.width(out);
    }

    private static void warnLow(TeslaMapsConfig c, long now) {
        if (!c.composterLowWarn) return;
        if (matter >= 0 && matter <= c.composterLowMatter && now - lowMatterAt >= WARN_LOW_MS) {
            lowMatterAt = now;
            alert("§cYour Organic Matter is low!");
        }
        if (fuel >= 0 && fuel <= c.composterLowFuel && now - lowFuelAt >= WARN_LOW_MS) {
            lowFuelAt = now;
            alert("§cYour Fuel is low!");
        }
    }

    private static void alert(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + msg));
        mc.gui.setTimes(0, 60, 10);
        mc.gui.setTitle(Component.literal(msg));
        mc.gui.setSubtitle(Component.empty());
    }

    private static void warnEmpty(Minecraft mc, boolean garden, String msg, long now) {
        if (mc.player == null) return;
        emptyWarnAt = now;
        Component line = Component.literal("§b[TeslaMaps] " + msg);
        if (!garden) {
            line = Component.literal("§b[TeslaMaps] " + msg + " §e[Warp to Garden]").withStyle(st -> st
                    .withClickEvent(new ClickEvent.RunCommand("/warp garden"))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to warp to the Garden"))));
        }
        mc.player.sendSystemMessage(line);
        mc.gui.setTimes(0, 50, 10);
        mc.gui.setTitle(Component.literal("§eComposter Warning!"));
        mc.gui.setSubtitle(Component.empty());
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        TeslaMapsConfig c = cfg();
        if (SkyblockUtils.isInGarden()) {
            if (c.composterHud && !lines.isEmpty() && hud != null) TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
        } else if (c.composterOutside && outHud != null && !outLines.isEmpty() && SkyblockUtils.isInSkyblock()) {
            TextHud.draw(g, mc, outHud.x(), outHud.y(), outHud.scale(), outLines, outWidth);
        }
    }

    // ---- upgrade levels and the SkyHanni formulas

    private static void readUpgrades(AbstractContainerScreen<?> cs) {
        if (!StringUtil.stripTrim(cs.getTitle().getString()).equals("Composter Upgrades")) return;
        boolean changed = false;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            Matcher m = UPGRADE.matcher(StringUtil.stripTrim(stack.getHoverName().getString()));
            if (!m.matches()) continue;
            int level = m.group(2) == null ? 0 : roman(m.group(2));
            Integer old = cfg().composterUpgrades.put(m.group(1), level);
            if (old == null || old != level) changed = true;
        }
        if (changed) TeslaMapsConfig.save();
    }

    public static boolean hasUpgrades() {
        return !cfg().composterUpgrades.isEmpty();
    }

    public static int level(String upgrade, String plusOne) {
        return cfg().composterUpgrades.getOrDefault(upgrade, 0) + (upgrade.equals(plusOne) ? 1 : 0);
    }

    public static double maxMatter(String plusOne) { return 40_000 + level(MATTER_CAP, plusOne) * 30_000; }
    public static double maxFuel(String plusOne) { return 100_000 + level(FUEL_CAP, plusOne) * 30_000; }
    public static double multiDrop(String plusOne) { return level(MULTI, plusOne) * 0.03; }
    public static double secondsPerCompost(String plusOne) { return 600.0 / (1 + level(SPEED, plusOne) * 0.2); }
    public static double matterPer(String plusOne) { return 4000.0 * (1 - level(COST, plusOne) / 100.0); }
    public static double fuelPer(String plusOne) { return 2000.0 * (1 - level(COST, plusOne) / 100.0); }
    public static double matter() { return matter; }
    public static double fuel() { return fuel; }
    public static boolean found() { return found; }
    public static List<String> upgradeNames() { return List.of(UPGRADES); }

    /** Ms until the composter runs out of either resource, -1 without upgrade data. */
    private static long estimateEmptyMs() {
        if (!hasUpgrades() || matter < 0 || fuel < 0) return -1;
        double next = timeText.equals("INACTIVE") ? 0 : parseSeconds(timeText);
        double per = secondsPerCompost(null);
        double frac = next / per;
        double byMatter = untilEnd(matter, frac, matterPer(null), per);
        double byFuel = untilEnd(fuel, frac, fuelPer(null), per);
        return (long) ((next + Math.min(byMatter, byFuel)) * 1000);
    }

    private static double untilEnd(double amount, double frac, double per, double seconds) {
        double left = amount - frac * per;
        return seconds * Math.max(0, Math.floor(left / per));
    }

    public static double parseSeconds(String s) {
        Matcher m = DURATION.matcher(s);
        double total = 0;
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            total += switch (m.group(2)) {
                case "d" -> n * 86400.0;
                case "h" -> n * 3600.0;
                case "m" -> n * 60.0;
                default -> n;
            };
        }
        return total;
    }

    public static String format(long ms) {
        long s = ms / 1000;
        long d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60, sec = s % 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }

    static int roman(String s) {
        if (s.chars().allMatch(Character::isDigit)) return Integer.parseInt(s);
        int result = 0, prev = 0;
        String u = s.toUpperCase(Locale.ROOT);
        for (int i = u.length() - 1; i >= 0; i--) {
            int v = switch (u.charAt(i)) {
                case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50;
                case 'C' -> 100; case 'D' -> 500; case 'M' -> 1000;
                default -> 0;
            };
            result += v < prev ? -v : v;
            prev = Math.max(prev, v);
        }
        return result;
    }
}
