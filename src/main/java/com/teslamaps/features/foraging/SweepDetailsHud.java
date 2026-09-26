package com.teslamaps.features.foraging;

// Ported from Skyblocker's SweepDetailsListener / SweepDetailsHudWidget
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Sweep Details lines shown on the HUD for 5s instead of chat. */
public final class SweepDetailsHud {

    private static final Pattern DETAILS = Pattern.compile("^Sweep Details: ([\\d,.]+)\\S? Sweep");
    private static final Pattern TOUGHNESS = Pattern.compile("^(.+?) Tree Toughness: ([\\d,.]+) ([\\d,.]+) Logs");
    private static final Pattern THROW = Pattern.compile("^Axe throw: (-[\\d.]+)% Sweep ([\\d,.]+) Logs");
    private static final Pattern STYLE = Pattern.compile("^Wrong Style: (-[\\d.]+)% Sweep ([\\d,.]+) Logs ([a-zA-Z ]*)!*");
    private static final long SHOW_MS = 5_000;
    private static final int ROW_H = 10;

    private static boolean active;
    private static long lastMatch;
    private static float maxSweep, lastSweep;
    private static String tree = "", toughness = "", logs = "", throwPct = "", stylePct = "", style = "";
    private static List<String> lines = List.of();
    private static ItemStack icon = ItemStack.EMPTY;
    private static int width = 80;

    private SweepDetailsHud() {}

    public static void register() {
        Feature.of("SweepDetailsHud").section("Foraging").when(() -> cfg().sweepDetailsHud)
                .hud("sweepdetails", SweepDetailsHud::render);
        HudEditElement.of("sweepDetailsHud", "Sweep Details").ctx(HudEditElement.HudCtx.FORAGING).section("Foraging")
                .enabled(() -> cfg().sweepDetailsHud)
                .size(() -> width, () -> Math.max(1, previewLines().size()) * ROW_H + 4)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, previewLines()));
    }

    private static TeslaMapsConfig cfg() {
        return TeslaMapsConfig.get();
    }

    /** @return true when the line was taken over by the HUD. */
    static boolean handle(String clean) {
        String t = clean.trim();
        if (!t.contains("Sweep") && !t.contains("Toughness")) return false;
        Matcher m = DETAILS.matcher(t);
        if (m.find()) {
            reset();
            active = true;
            lastMatch = System.currentTimeMillis();
            maxSweep = parse(m.group(1));
            lastSweep = maxSweep;
            rebuild();
            return true;
        }
        if (active && System.currentTimeMillis() > lastMatch + SHOW_MS) reset();
        if (!active) return false;

        if ((m = TOUGHNESS.matcher(t)).find()) {
            tree = m.group(1).trim();
            toughness = m.group(2);
            logs = m.group(3);
            icon = new ItemStack(logFor(tree));
        } else if ((m = THROW.matcher(t)).find()) {
            throwPct = m.group(1);
            lastSweep *= 1 + parse(throwPct) / 100f;
            logs = m.group(2);
        } else if ((m = STYLE.matcher(t)).find()) {
            stylePct = m.group(1);
            lastSweep *= 1 + parse(stylePct) / 100f;
            logs = m.group(2);
            style = m.group(3).trim();
        } else {
            return false;
        }
        rebuild();
        return true;
    }

    private static float parse(String s) {
        try {
            return Float.parseFloat(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return -1f;
        }
    }

    private static net.minecraft.world.item.Item logFor(String tree) {
        return switch (tree.toLowerCase(Locale.ROOT)) {
            case "fig" -> Items.STRIPPED_SPRUCE_LOG;
            case "mangrove" -> Items.MANGROVE_LOG;
            case "helix" -> Items.STRIPPED_MANGROVE_WOOD;
            case "jungle" -> Items.JUNGLE_LOG;
            case "acacia" -> Items.ACACIA_LOG;
            case "dark oak" -> Items.DARK_OAK_LOG;
            case "spruce" -> Items.SPRUCE_LOG;
            case "birch" -> Items.BIRCH_LOG;
            case "oak" -> Items.OAK_LOG;
            default -> Items.RED_CONCRETE;
        };
    }

    private static void reset() {
        active = false;
        maxSweep = lastSweep = -1;
        tree = toughness = logs = throwPct = stylePct = style = "";
        icon = ItemStack.EMPTY;
        lines = List.of();
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static void rebuild() {
        List<String> out = new ArrayList<>();
        out.add("   §6" + (tree.isEmpty() ? "Unknown" : tree) + " Tree");
        if (!toughness.isEmpty()) out.add("§7Toughness: §f" + toughness);
        out.add(maxSweep > lastSweep + 0.01f
                ? "§7Sweep: §c" + fmt(lastSweep) + " §f(§a" + fmt(maxSweep) + "§f)"
                : "§7Sweep: §a" + fmt(maxSweep));
        if (!logs.isEmpty()) out.add("§7Logs: §a" + logs);
        if (!throwPct.isEmpty()) out.add("§cThrow penalty: " + throwPct + "%");
        if (!stylePct.isEmpty()) {
            out.add("§cStyle penalty: " + stylePct + "%");
            if (!style.isEmpty()) out.add("§7Use: §e" + style);
        }
        lines = out;
        width = measure(out);
    }

    private static int measure(List<String> out) {
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (String l : out) w = Math.max(w, mc.font.width(l));
        return w + 6;
    }

    private static List<String> previewLines() {
        if (!lines.isEmpty()) return lines;
        return List.of("   §6Fig Tree", "§7Toughness: §f3.5", "§7Sweep: §a314.2", "§7Logs: §a12");
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!active || lines.isEmpty()) return;
        if (System.currentTimeMillis() > lastMatch + SHOW_MS) {
            reset();
            return;
        }
        if (!Foraging.whereTreesGrow() || com.teslamaps.utils.HudVisibility.suppressed()) return;
        TeslaMapsConfig c = cfg();
        draw(g, Minecraft.getInstance(), c.sweepDetailsHudX, c.sweepDetailsHudY, c.sweepDetailsHudScale, lines);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> rows) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, 0, 0, width, rows.size() * ROW_H + 4);
        for (int i = 0; i < rows.size(); i++) {
            g.text(mc.font, rows.get(i), 3, 3 + i * ROW_H, 0xFFFFFFFF);
        }
        ItemStack ic = rows == lines ? icon : ItemStack.EMPTY;
        if (!ic.isEmpty()) {
            pose.pushMatrix();
            pose.translate(2, 2);
            pose.scale(0.625f, 0.625f);
            g.item(ic, 0, 0);
            pose.popMatrix();
        }
        pose.popMatrix();
    }
}
