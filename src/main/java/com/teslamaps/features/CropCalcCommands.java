package com.teslamaps.features;

// Ported from SkyHanni's GardenCropsInCommand.kt, GardenCropTimeCommand.kt and FarmingMilestoneCommand.kt
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.CraftCost;
import com.teslamaps.profileviewer.data.GardenRegistry;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CropCalcCommands {

    private static final Pattern TIME = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([dhms])");

    private static final Map<String, String[]> ITEMS = Map.ofEntries(
            Map.entry("WHEAT", new String[]{"ENCHANTED_WHEAT", "ENCHANTED_HAY_BLOCK", "HAY_BLOCK"}),
            Map.entry("CARROT_ITEM", new String[]{"ENCHANTED_CARROT", "ENCHANTED_GOLDEN_CARROT"}),
            Map.entry("POTATO_ITEM", new String[]{"ENCHANTED_POTATO", "ENCHANTED_BAKED_POTATO"}),
            Map.entry("PUMPKIN", new String[]{"ENCHANTED_PUMPKIN", "POLISHED_PUMPKIN"}),
            Map.entry("MELON", new String[]{"ENCHANTED_MELON", "ENCHANTED_MELON_BLOCK"}),
            Map.entry("SUGAR_CANE", new String[]{"ENCHANTED_SUGAR", "ENCHANTED_SUGAR_CANE"}),
            Map.entry("CACTUS", new String[]{"ENCHANTED_CACTUS_GREEN", "ENCHANTED_CACTUS"}),
            Map.entry("INK_SACK:3", new String[]{"ENCHANTED_COCOA", "ENCHANTED_COOKIE"}),
            Map.entry("NETHER_STALK", new String[]{"ENCHANTED_NETHER_STALK", "MUTANT_NETHER_STALK"}),
            Map.entry("MUSHROOM_COLLECTION", new String[]{"ENCHANTED_RED_MUSHROOM", "ENCHANTED_BROWN_MUSHROOM",
                    "ENCHANTED_HUGE_MUSHROOM_2", "ENCHANTED_HUGE_MUSHROOM_1"}),
            Map.entry("DOUBLE_PLANT", new String[]{"ENCHANTED_SUNFLOWER", "COMPACTED_SUNFLOWER"}),
            Map.entry("MOONFLOWER", new String[]{"ENCHANTED_MOONFLOWER", "COMPACTED_MOONFLOWER"}),
            Map.entry("WILD_ROSE", new String[]{"ENCHANTED_WILD_ROSE", "COMPACTED_WILD_ROSE"}));

    private static final Map<String, Double> FALLBACK = Map.ofEntries(
            Map.entry("HAY_BLOCK", 9.0), Map.entry("ENCHANTED_WHEAT", 160.0), Map.entry("ENCHANTED_CARROT", 160.0),
            Map.entry("ENCHANTED_POTATO", 160.0), Map.entry("ENCHANTED_PUMPKIN", 160.0), Map.entry("ENCHANTED_MELON", 160.0),
            Map.entry("ENCHANTED_SUGAR", 160.0), Map.entry("ENCHANTED_CACTUS_GREEN", 160.0), Map.entry("ENCHANTED_COCOA", 160.0),
            Map.entry("ENCHANTED_NETHER_STALK", 160.0), Map.entry("ENCHANTED_RED_MUSHROOM", 160.0),
            Map.entry("ENCHANTED_BROWN_MUSHROOM", 160.0), Map.entry("ENCHANTED_SUNFLOWER", 160.0),
            Map.entry("ENCHANTED_MOONFLOWER", 160.0), Map.entry("ENCHANTED_WILD_ROSE", 160.0));

    private static final Map<String, String> RAW_IDS = Map.of("MUSHROOM_COLLECTION", "RED_MUSHROOM");

    private CropCalcCommands() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CropCalcCommands")
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Crop Calculators");
                    b.toggle("Crop Calculator Commands", () -> c.cropCalcCommands, v -> c.cropCalcCommands = v)
                            .desc("/tmap cropsin <time> <item>, /tmap croptime <amount> <item>, /tmap calccrop <crop> [tier] [target], "
                                    + "/tmap calccroptime <crop> [tier] [target]. Speed comes from your last farming run and fortune.");
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("cropsin")
                        .executes(c -> err("Usage: /tmap cropsin <time> <item>"))
                        .then(ClientCommands.argument("time", StringArgumentType.string())
                                .then(ClientCommands.argument("item", StringArgumentType.greedyString())
                                        .suggests((c, sb) -> suggestItems(sb))
                                        .executes(c -> cropsIn(StringArgumentType.getString(c, "time"),
                                                StringArgumentType.getString(c, "item"))))))
                .then(ClientCommands.literal("croptime")
                        .executes(c -> err("Usage: /tmap croptime <amount> <item>"))
                        .then(ClientCommands.argument("amount", LongArgumentType.longArg(1))
                                .then(ClientCommands.argument("item", StringArgumentType.greedyString())
                                        .suggests((c, sb) -> suggestItems(sb))
                                        .executes(c -> cropTime(LongArgumentType.getLong(c, "amount"),
                                                StringArgumentType.getString(c, "item"))))))
                .then(milestone("calccrop", false))
                .then(milestone("calccroptime", true))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> milestone(String name, boolean time) {
        return ClientCommands.literal(name)
                .executes(c -> err("No crop type entered"))
                .then(ClientCommands.argument("crop", StringArgumentType.word())
                        .suggests((c, sb) -> {
                            for (CropStats.Crop cr : CropStats.CROPS) sb.suggest(cr.eliteLb());
                            return sb.buildFuture();
                        })
                        .executes(c -> calc(StringArgumentType.getString(c, "crop"), null, null, time))
                        .then(ClientCommands.argument("current", IntegerArgumentType.integer(0, 200))
                                .executes(c -> calc(StringArgumentType.getString(c, "crop"),
                                        IntegerArgumentType.getInteger(c, "current"), null, time))
                                .then(ClientCommands.argument("target", IntegerArgumentType.integer(0, 200))
                                        .executes(c -> calc(StringArgumentType.getString(c, "crop"),
                                                IntegerArgumentType.getInteger(c, "current"),
                                                IntegerArgumentType.getInteger(c, "target"), time)))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestItems(
            com.mojang.brigadier.suggestion.SuggestionsBuilder sb) {
        String typed = sb.getRemaining().toLowerCase(Locale.ROOT);
        for (CropStats.Crop cr : CropStats.CROPS) {
            if (cr.name().toLowerCase(Locale.ROOT).startsWith(typed) && !cr.name().equalsIgnoreCase(typed)) sb.suggest(cr.name());
            for (String id : ITEMS.get(cr.key())) {
                String n = itemName(id);
                if (n.toLowerCase(Locale.ROOT).startsWith(typed) && !n.equalsIgnoreCase(typed)) sb.suggest(n);
            }
        }
        return sb.buildFuture();
    }

    private static boolean off() {
        if (cfg().cropCalcCommands) return false;
        msg("§7Enable §fCrop Calculator Commands §7in Garden settings.");
        return true;
    }

    private record Match(String name, CropStats.Crop crop, double perItem) {}

    /** Items whose name contains (or starts with) the search, with their size in raw crops. */
    private static List<Match> find(String search, boolean prefix) {
        String s = search.toLowerCase(Locale.ROOT).trim();
        List<Match> out = new ArrayList<>();
        for (CropStats.Crop cr : CropStats.CROPS) {
            if (hit(cr.name(), s, prefix)) out.add(new Match(cr.name(), cr, 1));
            for (String id : ITEMS.get(cr.key())) {
                String n = itemName(id);
                if (!hit(n, s, prefix)) continue;
                double m = multiplier(id, rawId(cr), 0);
                if (m <= 0) m = FALLBACK.getOrDefault(id, 0.0);
                out.add(new Match(n, cr, m));
            }
        }
        return out;
    }

    private static boolean hit(String name, String s, boolean prefix) {
        String n = name.toLowerCase(Locale.ROOT);
        return prefix ? n.startsWith(s) : n.contains(s);
    }

    private static String rawId(CropStats.Crop cr) {
        return RAW_IDS.getOrDefault(cr.key(), cr.key());
    }

    private static String itemName(String id) {
        String n = SkyblockItems.name(id);
        return n == null || n.isEmpty() ? StringUtil.titleCase(id.replace('_', ' ')) : StringUtil.strip(n);
    }

    /** How many raw crops one item takes, from the NEU recipes; 0 while unknown. */
    private static double multiplier(String id, String raw, int depth) {
        if (id.equals(raw) || (raw.equals("RED_MUSHROOM") && id.equals("BROWN_MUSHROOM"))) return 1;
        if (depth > 4) return 0;
        List<CraftCost.Recipe> recipes = CraftCost.recipesOrNull(id);
        if (recipes == null || recipes.isEmpty()) return 0;
        CraftCost.Recipe r = recipes.getFirst();
        double sum = 0;
        for (Map.Entry<String, Integer> ing : r.ingredients().entrySet()) {
            String ingId = ing.getKey().replace('-', ':');
            double m = multiplier(ingId, raw, depth + 1);
            sum += m * ing.getValue();
        }
        return sum / Math.max(1, r.count());
    }

    private static long seconds(String raw) {
        Matcher m = TIME.matcher(raw.toLowerCase(Locale.ROOT));
        double total = 0;
        boolean any = false;
        while (m.find()) {
            any = true;
            double v = Double.parseDouble(m.group(1));
            total += switch (m.group(2)) {
                case "d" -> v * 86400;
                case "h" -> v * 3600;
                case "m" -> v * 60;
                default -> v;
            };
        }
        return any ? (long) total : -1;
    }

    private static int cropsIn(String rawTime, String item) {
        if (off()) return 1;
        long sec = seconds(rawTime);
        if (sec <= 0) return err("Not a valid time: '" + rawTime + "' (e.g. 1h30m)");
        List<Match> matches = find(item, false);
        if (matches.isEmpty()) return err("No crops found for '" + item + "'");
        StringBuilder b = new StringBuilder("§7Crops farmed in §e" + rawTime + "§7:");
        for (Match m : matches) {
            Double speed = CropStats.cropsPerSecond(m.crop());
            b.append("\n§e").append(m.name());
            if (speed == null) b.append(" §cNo speed data!");
            else if (m.perItem() <= 0) b.append(" §cRecipe still loading, try again");
            else b.append(" §b").append(CropStats.fmt(Math.floor(sec * speed / m.perItem()))).append("x");
        }
        msg(b.toString());
        return 1;
    }

    private static int cropTime(long amount, String item) {
        if (off()) return 1;
        List<Match> matches = find(item, true);
        if (matches.isEmpty()) return err("No crop item found for '" + item + "'.");
        StringBuilder b = new StringBuilder("§7Crop time for " + matches.size() + " items:");
        for (Match m : matches) {
            b.append("\n§e").append(CropStats.fmt(amount)).append("x ").append(m.name());
            if (m.perItem() <= 0) {
                b.append(" §cRecipe still loading, try again");
                continue;
            }
            double full = m.perItem() * amount;
            if (m.perItem() != 1) b.append(" §7(§e").append(CropStats.fmt(full)).append("x ").append(m.crop().name()).append("§7)");
            Double speed = CropStats.cropsPerSecond(m.crop());
            b.append(speed == null ? " §cNo speed data!" : " §b" + CropStats.duration(full / speed));
        }
        msg(b.toString());
        return 1;
    }

    private static long firstAt(CropStats.Crop c, int tier) {
        if (tier <= 0) return 0;
        long lo = 0, hi = 1L << 40;
        while (lo < hi) {
            long mid = (lo + hi) >>> 1;
            if (GardenRegistry.milestone(c.key(), mid).tier() >= tier) hi = mid;
            else lo = mid + 1;
        }
        return lo;
    }

    private static int calc(String cropName, Integer current, Integer target, boolean time) {
        if (off()) return 1;
        CropStats.Crop c = CropStats.byName(cropName);
        if (c == null) return err("Unknown crop '" + cropName + "'");
        if (current == null) {
            Long counter = CropStats.milestones().get(c.key());
            if (counter == null) return err("No crop milestone data! Open /cropmilestones and rerun, or give the tiers.");
            int tier = GardenRegistry.milestone(c.key(), counter).tier();
            long need = firstAt(c, tier + 1) - counter;
            msg("§7" + out(need, c, time) + " needed to reach the next milestone §7(" + (tier + 1) + ")");
            return 1;
        }
        if (target == null) {
            msg("§7" + out(firstAt(c, current), c, time) + " needed for milestone §7" + current);
            return 1;
        }
        if (current >= target) return err("Entered milestone is greater than or the same as target milestone");
        msg("§7" + out(firstAt(c, target) - firstAt(c, current), c, time) + " needed for milestone §7" + current + " §a-> §7" + target);
        return 1;
    }

    private static String out(long amount, CropStats.Crop c, boolean time) {
        if (!time) return CropStats.fmt(amount) + " §a" + c.name();
        Double speed = CropStats.cropsPerSecond(c);
        if (speed == null) return "§cNo speed data for " + c.name() + "§7 -";
        return "§b" + CropStats.duration(amount / speed) + "§7 of farming";
    }

    private static int err(String s) {
        msg("§c" + s);
        return 1;
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §f" + s));
    }
}
