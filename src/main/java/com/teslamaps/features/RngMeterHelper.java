package com.teslamaps.features;

// Ported from NEU's ItemTooltipRngListener
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.NumberUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** RNG Meter menus: odds as fractions, runs/bosses to fill the meter, coins per score / XP, tier preview. */
public final class RngMeterHelper {

    private static final Pattern ODDS = Pattern.compile("^Odds: (.+) \\(([\\d.]+)%\\)$");
    private static final Pattern ODDS_SELECTED = Pattern.compile("^Odds: (.+) \\(([\\d.]+)% ([\\d.]+)%\\)$");
    private static final Pattern RUNS = Pattern.compile("^(?:Dungeon Score|Slayer XP|Nucleus XP): ([\\d,.kKmM]+)/([\\d,.kKmM]+)$");
    private static final Pattern RUNS_SELECTED = Pattern.compile("^\\s*[━\\- ]*\\s*([\\d,]+)/([\\d,.kKmM]+)$");
    private static final Pattern TIER_AMOUNT = Pattern.compile("^Tier (\\S+) amount: (\\d+).*$");

    private static final Map<String, Integer> DUNGEON = new LinkedHashMap<>();
    private static final Map<String, Integer> NUCLEUS = Map.of("§6Completion", 1000);
    private static final Map<String, Integer> SLAYER_TIERS = Map.of("Revenant Horror", 5, "Tarantula Broodfather", 5,
            "Sven Packmaster", 4, "Voidgloom Seraph", 4, "Inferno Demonlord", 4, "Riftstalker Bloodfiend", 5);
    private static final int[] SLAYER_XP = {5, 25, 100, 500, 1500};
    private static final int[] VAMPIRE_XP = {10, 25, 60, 120, 150};
    private static final int[] SLAYER_COST = {2000, 7500, 20000, 50000, 100000};
    private static final String[] TIER_COLORS = {"§a", "§e", "§c", "§4", "§5"};

    static {
        DUNGEON.put("§b§lS+", 300);
        DUNGEON.put("§6§lS", 189);
    }

    private static Feature feature;
    private static Screen lastScreen;
    private static String title = "";
    private static boolean inMenu;
    private static boolean fractions;
    private static boolean shiftLast, arrowLast;
    private static int selected;
    private static final Map<ItemStack, List<Component>> cache = new IdentityHashMap<>();
    private static int ticks;

    private RngMeterHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("RngMeterHelper")
                .when(() -> cfg().rngMeterHelper)
                .tick(RngMeterHelper::tick)
                .config("Tooltips & Value", b -> {
                    b.label("RNG Meter");
                    b.toggle("RNG Meter Helper", () -> cfg().rngMeterHelper, v -> cfg().rngMeterHelper = v)
                            .desc("Slayer / Dungeon / Nucleus RNG Meter menus: extra tooltip info per drop")
                            .tags("rng", "meter", "slayer", "dungeon", "odds");
                    b.toggle("  Odds As Fraction (Shift Toggles)", () -> cfg().rngMeterFractions, v -> cfg().rngMeterFractions = v);
                    b.toggle("  Runs / Bosses Needed (Arrows Switch Tier)", () -> cfg().rngMeterRuns, v -> cfg().rngMeterRuns = v);
                    b.toggle("  Coins Per Score / XP", () -> cfg().rngMeterCoinsPer, v -> cfg().rngMeterCoinsPer = v);
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!inMenu || feature == null || !feature.active() || lines.isEmpty()) return;
            List<Component> out = cache.get(stack);
            if (out == null) {
                out = build(stack, lines);
                if (cache.size() > 128) cache.clear();
                cache.put(stack, out);
            }
            if (out.isEmpty()) return;
            lines.clear();
            lines.addAll(out);
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) {
            if (lastScreen != null) {
                lastScreen = null;
                inMenu = false;
                cache.clear();
            }
            return;
        }
        if (cs != lastScreen || ticks++ % 20 == 0) {
            if (cs != lastScreen) ticks = 1;
            lastScreen = cs;
            title = StringUtil.stripTrim(cs.getTitle().getString());
            inMenu = title.endsWith(" RNG Meter") || SLAYER_TIERS.containsKey(title);
            cache.clear();
            if (inMenu) PriceManager.ensureFresh();
        }
        if (!inMenu) return;
        long h = mc.getWindow().handle();
        boolean shift = com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT);
        if (shift && !shiftLast && cfg().rngMeterFractions) {
            fractions = !fractions;
            cache.clear();
        }
        shiftLast = shift;
        boolean left = com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LEFT);
        boolean right = com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT);
        if (!arrowLast && (left || right)) {
            boolean dec = title.contains("Catacombs") ? right : left;
            selected = Math.max(0, selected + (dec ? -1 : 1));
            cache.clear();
        }
        arrowLast = left || right;
    }

    private static List<Component> build(ItemStack stack, List<Component> in) {
        boolean any = false;
        for (Component c : in) {
            String s = c.getString();
            if (s.contains("Odds:") || s.contains("Progress:") || s.contains("Dungeon Score:") || s.contains("Slayer XP:")
                    || s.contains("Nucleus XP:") || s.contains("RNG Meter")) { any = true; break; }
        }
        if (!any) return List.of();
        List<Component> out = new ArrayList<>(in.size() + 6);
        boolean nextProgress = false;
        for (Component line : in) {
            String plain = line.getString().trim();
            if (plain.startsWith("Odds:") && cfg().rngMeterFractions && fraction(out, line, plain)) continue;
            if (nextProgress || plain.startsWith("Dungeon Score:") || plain.startsWith("Slayer XP:") || plain.startsWith("Nucleus XP:")) {
                Matcher m = RUNS.matcher(plain);
                if (!m.matches()) m = RUNS_SELECTED.matcher(plain);
                if (m.matches()) {
                    long having = (long) NumberUtil.parseSuffixed(m.group(1).replace(",", ""));
                    long needed = (long) NumberUtil.parseSuffixed(m.group(2).replace(",", ""));
                    boolean progressLine = nextProgress;
                    nextProgress = false;
                    if (cfg().rngMeterRuns && needed > 0) {
                        if (runs(out, in, stack, having, needed, progressLine ? line : null)) {
                            if (!progressLine) out.add(line);
                            continue;
                        }
                    } else if (cfg().rngMeterCoinsPer && needed > 0) {
                        String label = title.contains("Catacombs") ? "Score" : "XP";
                        String coins = coinsPer(stack, needed, 1, 1, 0, label);
                        out.add(line);
                        if (coins != null) out.add(Component.literal(coins));
                        continue;
                    }
                }
                nextProgress = false;
            }
            if (plain.startsWith("Progress:")) nextProgress = true;
            out.add(line);
        }
        return out;
    }

    private static boolean fraction(List<Component> out, Component line, String plain) {
        Matcher m = ODDS.matcher(plain);
        Matcher sel = ODDS_SELECTED.matcher(plain);
        String result;
        if (m.matches()) {
            result = m.group(1) + " §7(1/" + chance(m.group(2)) + ")";
        } else if (sel.matches()) {
            result = sel.group(1) + " §7(§8§m1/" + chance(sel.group(2)) + "§r §71/" + chance(sel.group(3)) + ")";
        } else {
            return false;
        }
        if (fractions) {
            out.add(Component.literal("§7Odds: §f" + result));
            out.add(Component.literal("§8[Press SHIFT to show odds as percentages]"));
        } else {
            out.add(line);
            out.add(Component.literal("§8[Press SHIFT to show odds as fractions]"));
        }
        return true;
    }

    private static String chance(String pct) {
        try {
            return String.format("%,d", (long) (100.0 / Double.parseDouble(pct)));
        } catch (NumberFormatException e) {
            return "?";
        }
    }

    private record RunData(Map<String, Integer> gain, String plural, String singular, boolean slayer) {}

    private static RunData runData() {
        if (title.contains("Catacombs")) return new RunData(DUNGEON, "Runs", "Run", false);
        if (title.contains("Crystal Nucleus")) return new RunData(NUCLEUS, "Runs", "Run", false);
        String slayer = title.endsWith(" RNG Meter") ? title.substring(0, title.length() - 10) : title;
        Integer max = SLAYER_TIERS.get(slayer);
        if (max == null) return null;
        int[] xp = slayer.equals("Riftstalker Bloodfiend") ? VAMPIRE_XP : SLAYER_XP;
        Map<String, Integer> tiers = new LinkedHashMap<>();
        for (int i = 0; i < max; i++) tiers.put(TIER_COLORS[i] + "Tier " + (i + 1), xp[i]);
        return new RunData(tiers, "Bosses", "Boss", true);
    }

    private static boolean runs(List<Component> out, List<Component> in, ItemStack stack, long having, long needed, Component progress) {
        RunData d = runData();
        if (d == null || d.gain().isEmpty()) return false;
        if (selected >= d.gain().size()) selected = d.gain().size() - 1;
        List<String> names = new ArrayList<>(d.gain().keySet());
        String name = names.get(selected);
        int gain = d.gain().get(name);
        long runsNeeded = needed / gain;
        long runsHaving = having / gain;
        Component progressHeader = null;
        if (progress != null && !out.isEmpty()) progressHeader = out.removeLast();
        out.add(Component.literal("§9Stats for " + name + "§9: " + (d.gain().size() > 1 ? "[§l§m< §9Switch§l➡§9]" : "")));
        out.add(Component.literal("   §7" + d.plural() + " completed: §e" + String.format("%,d", runsHaving)
                + " §7(of §e" + String.format("%,d", runsNeeded) + " §7needed)"));
        if (cfg().rngMeterCoinsPer) {
            int perTier = amountPerTier(in);
            int cost = d.slayer() && selected < SLAYER_COST.length ? SLAYER_COST[selected] : 0;
            String coins = coinsPer(stack, needed, gain, perTier, cost, d.singular());
            if (coins != null) out.add(Component.literal("   " + coins));
        }
        out.add(Component.literal(" "));
        if (progressHeader != null) out.add(progressHeader);
        if (progress != null) out.add(progress);
        return true;
    }

    private static int amountPerTier(List<Component> lines) {
        for (Component c : lines) {
            Matcher m = TIER_AMOUNT.matcher(c.getString().trim());
            if (!m.matches()) continue;
            if (roman(m.group(1)) == selected + 1) {
                try { return Integer.parseInt(m.group(2)); } catch (NumberFormatException ignored) {}
            }
        }
        return 1;
    }

    private static int roman(String s) {
        return switch (s) {
            case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4; case "V" -> 5;
            default -> { try { yield Integer.parseInt(s); } catch (NumberFormatException e) { yield 0; } }
        };
    }

    private static String coinsPer(ItemStack stack, long needed, int multiplier, int amount, int cost, String label) {
        String id = ItemUtil.skyblockId(stack);
        if (id.isEmpty()) id = PriceManager.idForName(stack.getHoverName().getString());
        if (id == null) return null;
        Double p = PriceManager.getPriceOrNull(id, LoreCosts.source());
        if (p == null || p <= 0) return null;
        double per = p * amount / needed * multiplier - cost;
        return "§7Coins per " + label + ": " + (per >= 0 ? "§a" : "§c") + EstimatedValue.fmt(per) + " coins";
    }
}
