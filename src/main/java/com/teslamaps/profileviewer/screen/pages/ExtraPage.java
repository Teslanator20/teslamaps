package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonObject;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import com.teslamaps.profileviewer.ui.Kit;
import com.teslamaps.profileviewer.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Everything that has no page of its own: combat totals, essence, slayers, account odds and ends. */
public class ExtraPage extends ProfileViewerPage {

    private static final List<String> ESSENCE_KEYS = List.of(
            "WITHER", "DRAGON", "SPIDER", "UNDEAD", "DIAMOND", "GOLD", "ICE", "CRIMSON");
    private static final List<Integer> ESSENCE_COLORS = List.of(
            0xFFAAAAAA, 0xFFAA00AA, 0xFF777777, 0xFF00AA00, 0xFF55FFFF, 0xFFFFAA00, 0xFF88DDFF, 0xFFFF5555);

    private static final List<String> SLAYER_KEYS = List.of(
            "zombie", "spider", "wolf", "enderman", "blaze", "vampire");
    private static final List<String> SLAYER_NAMES = List.of(
            "Revenant", "Tarantula", "Sven", "Voidgloom", "Inferno", "Riftstalker");
    private static final List<Integer> SLAYER_COLORS = List.of(
            0xFF00AA00, 0xFF777777, 0xFFAAAAAA, 0xFFAA00AA, 0xFFFF5500, 0xFFFF5555);

    @Override
    public String getTabName() {
        return "Extra";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.EXPERIENCE_BOTTLE);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        JsonObject member = profile.getMemberData();
        int available = NeuStyle.PANEL_HEIGHT - y - 6;
        drawTree(ctx, w -> fitted(member, w, available), width, x, y, mouseX, mouseY);
    }

    private Ui fitted(JsonObject member, int width, int available) {
        Ui tree = build(member, width, 2);
        for (int columns = 3; columns <= 4 && tree.height() > available; columns++) {
            tree = build(member, width, columns);
        }
        return tree;
    }

    private Ui build(JsonObject member, int width, int columns) {
        List<Ui> parts = new ArrayList<>();

        Ui slayers = slayers(member, width, columns);
        if (slayers != null) parts.add(Kit.section("Slayers", slayers));

        parts.add(Kit.section("Account", account(member, width)));

        if (com.teslamaps.profileviewer.PvExtras.fishing()) parts.add(Kit.section("Fishing", fishing(member, width)));

        Ui essence = essence(member, width);
        if (essence != null) parts.add(Kit.section("Essence", essence));

        return Ui.col(9, parts);
    }

    private Ui account(JsonObject member, int width) {
        JsonObject stats = object(member, "player_stats");
        long kills = sum(object(stats, "kills"));
        long deaths = sum(object(stats, "deaths"));
        double kd = deaths > 0 ? (double) kills / deaths : kills;

        JsonObject auctions = object(stats, "auctions");
        // "sold" does not exist, the count sits in total_sold.total
        long sold = auctions != null && auctions.has("total_sold")
                ? asLong(get(object(auctions, "total_sold"), "total"))
                : asLong(get(auctions, "completed"));
        long earned = asLong(get(auctions, "gold_earned"));

        long fished = asLong(get(object(stats, "items_fished"), "total"));

        List<Kit.TileData> tiles = new ArrayList<>();
        tiles.add(new Kit.TileData("Kills", Kit.compact(kills), NeuStyle.TEXT));
        tiles.add(new Kit.TileData("Deaths", Kit.compact(deaths), NeuStyle.TEXT));
        tiles.add(new Kit.TileData("K/D", String.format(java.util.Locale.ROOT, "%.2f", kd), NeuStyle.TEXT_AQUA));
        if (!com.teslamaps.profileviewer.PvExtras.fishing()) {
            tiles.add(new Kit.TileData("Items Fished", Kit.compact(fished), NeuStyle.TEXT_AQUA));
        }
        tiles.add(new Kit.TileData("Auctions Sold", Kit.compact(sold), NeuStyle.TEXT));
        tiles.add(new Kit.TileData("Coins Earned", Kit.compact(earned), NeuStyle.TEXT_GOLD));
        tiles.add(new Kit.TileData("First Join", firstJoin(member), NeuStyle.TEXT_GRAY));

        return Kit.tileRow(width, tiles);
    }

    private Ui fishing(JsonObject member, int width) {
        JsonObject stats = object(member, "player_stats");
        JsonObject fished = object(stats, "items_fished");
        long seaCreatures = asLong(get(stats, "sea_creature_kills"));
        return Kit.tileRow(width, List.of(
                new Kit.TileData("Items Fished", Kit.compact(asLong(get(fished, "total"))), NeuStyle.TEXT_AQUA),
                new Kit.TileData("Treasure", Kit.compact(asLong(get(fished, "treasure"))), NeuStyle.TEXT_GOLD),
                new Kit.TileData("Large Treasure", Kit.compact(asLong(get(fished, "large_treasure"))), NeuStyle.TEXT_GOLD),
                new Kit.TileData("Sea Creatures", Kit.compact(seaCreatures), NeuStyle.TEXT_GREEN),
                new Kit.TileData("Dolphin Milestone", dolphinTier(seaCreatures), NeuStyle.TEXT_AQUA),
                new Kit.TileData("Trophy Caught", Kit.compact(asLong(get(object(member, "trophy_fish"), "total_caught"))), NeuStyle.TEXT)));
    }

    /** Dolphin pet rarity milestone from total sea creature kills. */
    private static String dolphinTier(long kills) {
        if (kills >= 100_000) return "Legendary";
        if (kills >= 25_000) return "Epic";
        if (kills >= 5_000) return "Rare";
        if (kills >= 1_000) return "Uncommon";
        if (kills >= 250) return "Common";
        return "-";
    }

    private Ui essence(JsonObject member, int width) {
        JsonObject essence = object(object(member, "currencies"), "essence");
        if (essence == null) return null;

        List<Kit.TileData> tiles = new ArrayList<>();
        for (int i = 0; i < ESSENCE_KEYS.size(); i++) {
            long amount = asLong(get(object(essence, ESSENCE_KEYS.get(i)), "current"));
            if (amount <= 0) continue;
            tiles.add(new Kit.TileData(Kit.prettify(ESSENCE_KEYS.get(i)), Kit.compact(amount), ESSENCE_COLORS.get(i)));
        }

        return tiles.isEmpty() ? null : Kit.tileRow(width, tiles);
    }

    // total xp needed per level; zombie, spider and the wolf/eman/blaze group differ, vampire has 5
    private static final long[] XP_ZOMBIE = {5, 15, 200, 1000, 5000, 20000, 100000, 400000, 1000000};
    private static final long[] XP_SPIDER = {5, 25, 200, 1000, 5000, 20000, 100000, 400000, 1000000};
    private static final long[] XP_DEFAULT = {10, 30, 250, 1500, 5000, 20000, 100000, 400000, 1000000};
    private static final long[] XP_VAMPIRE = {20, 75, 240, 840, 2400};

    private static long[] xpTable(String key) {
        return switch (key) {
            case "zombie" -> XP_ZOMBIE;
            case "spider" -> XP_SPIDER;
            case "vampire" -> XP_VAMPIRE;
            default -> XP_DEFAULT;
        };
    }

    /** One full width row per slayer with its xp bar; level from the xp, not the claim state. */
    private Ui slayers(JsonObject member, int width, int columns) {
        JsonObject bosses = object(object(member, "slayer"), "slayer_bosses");
        if (bosses == null) return null;

        columns = Math.max(1, Math.min(columns, width / 150));
        int cellWidth = Kit.cellWidth(width, columns, 12);

        List<Ui> rows = new ArrayList<>();
        for (int i = 0; i < SLAYER_KEYS.size(); i++) {
            JsonObject slayer = object(bosses, SLAYER_KEYS.get(i));
            long xp = asLong(get(slayer, "xp"));

            long[] table = xpTable(SLAYER_KEYS.get(i));
            int level = 0;
            for (long threshold : table) if (xp >= threshold) level++;
            boolean maxed = level >= table.length;
            double progress = maxed ? 1
                    : (xp - (level > 0 ? table[level - 1] : 0))
                            / (double) (table[level] - (level > 0 ? table[level - 1] : 0));

            List<String> tooltip = new ArrayList<>();
            tooltip.add("§a" + SLAYER_NAMES.get(i) + " Slayer");
            tooltip.add("§7Level §f" + level + "§7/§f" + table.length);
            tooltip.add("§7Total XP: §f" + com.teslamaps.profileviewer.ui.Kit.dots(xp));
            if (!maxed) tooltip.add("§7Next level: §f" + com.teslamaps.profileviewer.ui.Kit.dots(table[level]) + " XP");

            long total = 0;
            for (int tier = 0; tier < 5; tier++) {
                long kills = asLong(get(slayer, "boss_kills_tier_" + tier));
                long attempts = asLong(get(slayer, "boss_attempts_tier_" + tier));
                if (kills == 0 && attempts == 0) continue;
                total += kills;
                tooltip.add("§7Tier " + (tier + 1) + ": §f" + com.teslamaps.profileviewer.ui.Kit.dots(kills) + " kills"
                        + (attempts > kills ? " §8(" + com.teslamaps.profileviewer.ui.Kit.dots(attempts) + " started)" : ""));
            }
            tooltip.add("§7Bosses killed: §f" + com.teslamaps.profileviewer.ui.Kit.dots(total));
            String rank = com.teslamaps.profileviewer.PvExtras.rankLine(SLAYER_KEYS.get(i) + "-slayer");
            if (rank != null) tooltip.add(rank);

            int color = xp > 0 ? SLAYER_COLORS.get(i) : NeuStyle.TEXT_DARK;
            rows.add(new Ui.Tip(Ui.col(2,
                    new Ui.SpaceBetween(cellWidth,
                            Ui.row(5, Ui.V.CENTER,
                                    new Ui.Label(SLAYER_NAMES.get(i), color),
                                    new Ui.Label(String.valueOf(level),
                                            maxed ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT)),
                            new Ui.Label(Kit.compact(xp) + " XP", NeuStyle.TEXT_GRAY)),
                    Kit.bar(cellWidth, progress, maxed ? NeuStyle.TEXT_GOLD : color)),
                    tooltip));
        }

        return rows.isEmpty() ? null : Kit.grid(rows, columns, 12, 6);
    }

    private static String firstJoin(JsonObject member) {
        long first = asLong(get(object(member, "profile"), "first_join"));
        if (first <= 0) return "-";
        return new java.text.SimpleDateFormat("MMM d, yyyy").format(new java.util.Date(first));
    }

    private static long sum(JsonObject object) {
        if (object == null) return 0;
        long total = 0;
        for (var entry : object.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) total += asLong(entry.getValue());
        }
        return total;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject()
                ? parent.getAsJsonObject(key) : null;
    }

    private static com.google.gson.JsonElement get(JsonObject parent, String key) {
        return parent != null && parent.has(key) ? parent.get(key) : null;
    }
}
