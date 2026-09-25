package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.profileviewer.data.CollectionRepo;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import com.teslamaps.profileviewer.ui.Kit;
import com.teslamaps.profileviewer.ui.Ui;
import com.teslamaps.utils.SkyblockItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Collections per category, cells and icon mapping ported from schrumbo's pv. */
public class CollectionsPage extends ProfileViewerPage {
    private static final int RAIL = 92;
    private static final int ROW_HEIGHT = 30;

    private int category = 0;
    private int page = 0;
    private int pageCount = 1;
    private int perPage = 12;

    private int lastLeft;
    private int lastTop;
    private int pagerX;
    private int pagerY;
    private int pagerW;

    @Override
    public boolean isFixedLayout() {
        return true;
    }

    @Override
    public String getTabName() {
        return "Collections";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.PAINTING);
    }

    @Override
    public void onSelected() {
        CollectionRepo.load();
        page = 0;
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        Font tr = Minecraft.getInstance().font;
        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        int left = x - 8;
        int top = y - 8;
        lastLeft = left;
        lastTop = top;

        CollectionRepo.load();
        if (!CollectionRepo.isLoaded()) {
            ctx.text(tr, "Loading collections...", left + 20, top + 20, NeuStyle.TEXT_GRAY);
            return;
        }

        List<CollectionRepo.Category> categories = CollectionRepo.categories();
        if (categories.isEmpty()) return;
        boolean minions = com.teslamaps.profileviewer.PvExtras.minions();
        int railCount = categories.size() + (minions ? 1 : 0);
        category = Math.min(category, railCount - 1);

        for (int i = 0; i < railCount; i++) {
            int bx = left + 6;
            int by = top + 10 + i * 20;
            boolean selected = i == category;
            boolean hovered = mouseX >= bx && mouseX < bx + RAIL - 6 && mouseY >= by && mouseY < by + 18;

            if (selected) NeuStyle.slimBox(ctx, bx, by, RAIL - 6, 18);
            ctx.text(tr, i < categories.size() ? categories.get(i).name() : "Minions", bx + 6, by + 5,
                    selected ? NeuStyle.TEXT_YELLOW : hovered ? NeuStyle.TEXT : NeuStyle.TEXT_GRAY);
        }

        NeuStyle.divider(ctx, left + RAIL + 1, top + 8, NeuStyle.PANEL_HEIGHT - 16);

        int contentX = left + RAIL + 8;
        int contentWidth = left + width + 8 - contentX;
        int contentTop = top + 24;
        int available = top + height + 8 - contentTop - 14;

        if (category >= categories.size()) {
            pageCount = 1;
            pagerW = 0;
            if (minionTree == null || minionProfile != profile || minionWidth != contentWidth) {
                minionProfile = profile;
                minionWidth = contentWidth;
                minionTree = minionTree(profile, contentWidth);
            }
            ctx.text(tr, minionHeader, contentX, top + 10, NeuStyle.TEXT_YELLOW);
            drawTree(ctx, minionTree, contentX, contentTop, contentWidth, mouseX, mouseY);
            return;
        }

        CollectionRepo.Category current = categories.get(category);
        List<CollectionRepo.Entry> items = current.items();

        // rows per page from the real cell height, not a guess - a guessed 30 pushed rows that
        // still fit onto the next page
        int cellWidth = Kit.cellWidth(contentWidth, 2, 10);
        Map<String, Long> collected = profile.getCollections();
        int cellHeight = items.isEmpty() ? ROW_HEIGHT
                : cell(items.getFirst(), collected.getOrDefault(items.getFirst().id(), 0L), cellWidth).height();
        int rowsPerPage = Math.max(1, (available + 5) / (cellHeight + 5));
        perPage = rowsPerPage * 2;
        pageCount = Math.max(1, (items.size() + perPage - 1) / perPage);
        page = Math.min(page, pageCount - 1);

        int maxed = 0;
        for (CollectionRepo.Entry entry : items) {
            if (CollectionRepo.tier(entry, collected.getOrDefault(entry.id(), 0L)) >= entry.maxTiers()) maxed++;
        }
        ctx.text(tr, current.name() + " - " + maxed + " / " + items.size() + " maxed",
                contentX, top + 10, NeuStyle.TEXT_YELLOW);

        List<Ui> cells = new ArrayList<>();
        for (int i = 0; i < perPage; i++) {
            int index = page * perPage + i;
            if (index >= items.size()) break;
            cells.add(cell(items.get(index), collected.getOrDefault(items.get(index).id(), 0L), cellWidth));
        }
        // the real content width, otherwise drawTree assumes a rail on both sides and shrinks
        drawTree(ctx, Kit.grid(cells, 2, 10, 5), contentX, contentTop, contentWidth, mouseX, mouseY);

        if (pageCount > 1) {
            String pager = "< " + (page + 1) + " / " + pageCount + " >";
            pagerX = contentX + (contentWidth - tr.width(pager)) / 2;
            pagerY = top + height + 8 - 12;
            pagerW = tr.width(pager);
            ctx.text(tr, pager, pagerX, pagerY, NeuStyle.TEXT);
        } else {
            pagerW = 0;
        }
    }

    private Ui minionTree;
    private Object minionProfile;
    private int minionWidth;
    private String minionHeader = "";

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"};

    /** Minion grid per category like /craftedgenerators: highest unbroken tier, tiers in the tooltip. */
    private Ui minionTree(SkyblockProfile profile, int width) {
        Map<String, java.util.Set<Integer>> crafted = com.teslamaps.profileviewer.data.MinionData.crafted(profile);
        int slot = 18;
        int perRow = Math.max(1, (width + 1) / (slot + 1));
        int unique = 0;
        int maxedTotal = 0;
        for (java.util.Set<Integer> tiers : crafted.values()) unique += tiers.size();

        List<Ui> sections = new ArrayList<>();
        for (Map.Entry<String, List<String>> category : com.teslamaps.profileviewer.data.MinionData.categories().entrySet()) {
            List<Ui> icons = new ArrayList<>();
            int unlocked = 0;
            int maxed = 0;
            for (String minion : category.getValue()) {
                int max = com.teslamaps.profileviewer.data.MinionData.maxTier(minion);
                java.util.Set<Integer> tiers = crafted.getOrDefault(minion, java.util.Set.of());
                int shown = 0;
                while (shown < max && tiers.contains(shown + 1)) shown++;
                int count = 0;
                for (int t = 1; t <= max; t++) if (tiers.contains(t)) count++;
                boolean isMax = count >= max;
                if (count > 0) unlocked++;
                if (isMax) maxed++;

                String name = Kit.prettify(minion) + " Minion";
                List<String> tooltip = new ArrayList<>();
                tooltip.add((isMax ? "§a" : count == 0 ? "§c" : "§e") + name);
                for (int t = 1; t <= max; t++) {
                    boolean have = tiers.contains(t);
                    tooltip.add((have ? "§a✔" : "§c✖") + " Tier " + ROMAN[Math.min(t, ROMAN.length) - 1]);
                }
                icons.add(new Ui.Tip(com.teslamaps.profileviewer.ui.SlotIcon.ofId(
                                minion + "_GENERATOR_" + Math.max(1, shown), Kit.icon("player_head"), slot, true)
                        .corner(shown > 0 ? String.valueOf(shown) : "-",
                                isMax ? NeuStyle.TEXT_GOLD : shown > 0 ? 0xFFFFFFFF : NeuStyle.TEXT_RED),
                        tooltip));
            }
            maxedTotal += maxed;
            List<Ui> rows = new ArrayList<>();
            for (int i = 0; i < icons.size(); i += perRow) {
                rows.add(Ui.row(1, Ui.V.TOP, new ArrayList<>(icons.subList(i, Math.min(i + perRow, icons.size())))));
            }
            sections.add(Ui.col(2,
                    new Ui.Label(Kit.prettify(category.getKey()) + " " + unlocked + "/" + category.getValue().size()
                            + (maxed > 0 ? " - " + maxed + " maxed" : ""), NeuStyle.TEXT_GREEN, Ui.SMALL),
                    Ui.col(1, rows)));
        }
        minionHeader = "Minions - " + unique + " unique crafts, " + maxedTotal + " maxed";
        return Ui.col(5, sections);
    }

    private final Map<CollectionRepo.Entry, Ui> cellCache = new java.util.IdentityHashMap<>();
    private Object cellProfile;
    private List<CollectionRepo.Category> cellRepo;
    private int cellCacheWidth = -1;
    private int cellRankVersion = -1;

    private Ui cell(CollectionRepo.Entry entry, long amount, int cellWidth) {
        SkyblockProfile profile = getProfile();
        List<CollectionRepo.Category> repo = CollectionRepo.categories();
        int rankVersion = com.teslamaps.profileviewer.PvExtras.ranks() ? com.teslamaps.profileviewer.data.EliteRanks.version() : -1;
        if (profile != cellProfile || repo != cellRepo || cellWidth != cellCacheWidth || rankVersion != cellRankVersion) {
            cellRankVersion = rankVersion;
            cellCache.clear();
            cellProfile = profile;
            cellRepo = repo;
            cellCacheWidth = cellWidth;
        }
        Ui cached = cellCache.get(entry);
        if (cached != null) return cached;
        Ui built = buildCell(entry, amount, cellWidth);
        cellCache.put(entry, built);
        return built;
    }

    private Ui buildCell(CollectionRepo.Entry entry, long amount, int cellWidth) {
        int tier = CollectionRepo.tier(entry, amount);
        boolean maxed = tier >= entry.maxTiers();
        int color = maxed ? NeuStyle.TEXT_GOLD : tier == 0 ? NeuStyle.TEXT_GRAY : NeuStyle.TEXT_AQUA;
        int bodyWidth = cellWidth - 16 - 5;

        long previous = tier == 0 ? 0 : entry.tiers().get(Math.min(tier, entry.tiers().size()) - 1);
        long next = tier < entry.tiers().size() ? entry.tiers().get(tier) : previous;
        double progress = maxed || next <= previous ? 1 : (double) (amount - previous) / (next - previous);

        Ui body = Ui.col(2,
                new Ui.SpaceBetween(bodyWidth,
                        new Ui.Label(Kit.clip(entry.name(), bodyWidth - 34), maxed ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT),
                        new Ui.Label(tier + "/" + entry.maxTiers(), color)),
                Kit.bar(bodyWidth, progress, maxed ? NeuStyle.TEXT_GOLD : 0xFF55FFFF),
                new Ui.Label(Kit.compact(amount), NeuStyle.TEXT_GRAY, Ui.SMALL));

        List<String> tooltip = new ArrayList<>(List.of(
                "§f" + entry.name(),
                "§7Tier §f" + tier + "§7/§f" + entry.maxTiers(),
                "§7Total: §f" + com.teslamaps.profileviewer.ui.Kit.dots(amount)));
        if (maxed) {
            tooltip.add("§7Maxed");
        } else {
            tooltip.add("§7Next: §f" + Kit.compact(amount) + "§7/§f" + Kit.compact(next));
            tooltip.add("§7To max: §e" + Kit.dots(CollectionRepo.toMax(entry, amount)));
        }
        if (com.teslamaps.profileviewer.PvExtras.ranks()) {
            String rank = com.teslamaps.profileviewer.data.EliteRanks.line(
                    com.teslamaps.profileviewer.data.EliteRanks.collectionRank(entry.id()));
            if (rank != null) tooltip.add(rank);
        }

        return new Ui.Tip(Ui.row(5, Ui.V.CENTER, new Ui.Icon(icon(entry.id()), 16, false, null), body), tooltip);
    }

    // ---- icons ------------------------------------------------------------------------------

    private static final Map<String, ItemStack> icons = new HashMap<>();
    private static Map<String, String> skulls;

    private static Map<String, String> skulls() {
        if (skulls != null) return skulls;
        Map<String, String> loaded = new HashMap<>();
        try (InputStream stream = CollectionsPage.class
                .getResourceAsStream("/assets/teslamaps/pv/collection_skulls.json")) {
            if (stream != null) {
                JsonObject root = JsonParser.parseReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                for (String key : root.keySet()) loaded.put(key, root.get(key).getAsString());
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to read collection_skulls.json: {}", e.toString());
        }
        skulls = loaded;
        return skulls;
    }

    private static ItemStack icon(String key) {
        ItemStack cached = icons.get(key);
        if (cached != null) return cached;

        String texture = skulls().get(key);
        ItemStack stack = texture != null
                ? SkyblockItems.headFromTexture(texture)
                : Kit.icon(ICON_MAP.getOrDefault(key, key.split(":")[0].toLowerCase()));
        if (stack.isEmpty()) stack = new ItemStack(Items.PAPER);
        icons.put(key, stack);
        return stack;
    }

    /** Legacy 1.8 collection keys mapped onto modern items, ported from pv. */
    private static final Map<String, String> ICON_MAP = Map.ofEntries(
            Map.entry("WHEAT", "wheat"), Map.entry("SEEDS", "wheat_seeds"), Map.entry("CARROT_ITEM", "carrot"),
            Map.entry("POTATO_ITEM", "potato"), Map.entry("PUMPKIN", "pumpkin"), Map.entry("MELON", "melon_slice"),
            Map.entry("SUGAR_CANE", "sugar_cane"), Map.entry("CACTUS", "cactus"), Map.entry("NETHER_STALK", "nether_wart"),
            Map.entry("MUSHROOM_COLLECTION", "red_mushroom"), Map.entry("INK_SACK:3", "cocoa_beans"),
            Map.entry("RAW_CHICKEN", "chicken"), Map.entry("MUTTON", "mutton"), Map.entry("PORK", "porkchop"),
            Map.entry("RAW_FISH", "cod"), Map.entry("RABBIT", "rabbit"), Map.entry("LEATHER", "leather"),
            Map.entry("FEATHER", "feather"),
            Map.entry("COBBLESTONE", "cobblestone"), Map.entry("COAL", "coal"), Map.entry("IRON_INGOT", "iron_ingot"),
            Map.entry("GOLD_INGOT", "gold_ingot"), Map.entry("DIAMOND", "diamond"), Map.entry("LAPIS", "lapis_lazuli"),
            Map.entry("INK_SACK:4", "lapis_lazuli"), Map.entry("EMERALD", "emerald"), Map.entry("REDSTONE", "redstone"),
            Map.entry("QUARTZ", "quartz"), Map.entry("OBSIDIAN", "obsidian"), Map.entry("GLOWSTONE_DUST", "glowstone_dust"),
            Map.entry("GRAVEL", "gravel"), Map.entry("ICE", "ice"), Map.entry("NETHERRACK", "netherrack"),
            Map.entry("SAND", "sand"), Map.entry("SAND:1", "red_sand"), Map.entry("ENDER_STONE", "end_stone"),
            Map.entry("MYCEL", "mycelium"), Map.entry("SULPHUR", "glowstone_dust"), Map.entry("SULPHUR_ORE", "glowstone"),
            Map.entry("HARD_STONE", "stone"), Map.entry("MITHRIL_ORE", "prismarine_crystals"),
            Map.entry("TUNGSTEN", "netherite_scrap"), Map.entry("GLACITE", "blue_ice"),
            Map.entry("ROTTEN_FLESH", "rotten_flesh"), Map.entry("BONE", "bone"), Map.entry("STRING", "string"),
            Map.entry("SPIDER_EYE", "spider_eye"), Map.entry("SLIME_BALL", "slime_ball"),
            Map.entry("ENDER_PEARL", "ender_pearl"), Map.entry("BLAZE_ROD", "blaze_rod"),
            Map.entry("GHAST_TEAR", "ghast_tear"), Map.entry("MAGMA_CREAM", "magma_cream"),
            Map.entry("CHILI_PEPPER", "sweet_berries"),
            Map.entry("LOG", "oak_log"), Map.entry("LOG:1", "spruce_log"), Map.entry("LOG:2", "birch_log"),
            Map.entry("LOG:3", "jungle_log"), Map.entry("LOG_2", "acacia_log"), Map.entry("LOG_2:1", "dark_oak_log"),
            Map.entry("MANGROVE_LOG", "mangrove_log"), Map.entry("FIG_LOG", "stripped_birch_log"),
            Map.entry("TENDER_WOOD", "pale_oak_log"),
            Map.entry("RAW_FISH:1", "salmon"), Map.entry("RAW_FISH:2", "tropical_fish"),
            Map.entry("RAW_FISH:3", "pufferfish"), Map.entry("PRISMARINE_SHARD", "prismarine_shard"),
            Map.entry("PRISMARINE_CRYSTALS", "prismarine_crystals"), Map.entry("CLAY_BALL", "clay_ball"),
            Map.entry("WATER_LILY", "lily_pad"), Map.entry("INK_SACK", "ink_sac"), Map.entry("SPONGE", "sponge"),
            Map.entry("MAGMA_FISH", "pufferfish"),
            Map.entry("AGARICUS_CAP", "brown_mushroom"), Map.entry("CADUCOUS_STEM", "warped_roots"),
            Map.entry("HALF_EATEN_CARROT", "golden_carrot"), Map.entry("HEMOVIBE", "redstone_ore"),
            Map.entry("METAL_HEART", "heart_of_the_sea"), Map.entry("TIMITE", "nether_star"),
            Map.entry("WILTED_BERBERIS", "dead_bush"),
            Map.entry("MOONFLOWER", "lily_of_the_valley"), Map.entry("WILD_ROSE", "poppy"),
            Map.entry("DOUBLE_PLANT", "sunflower"), Map.entry("LUSHLILAC", "lilac"), Map.entry("VINESAP", "vine"),
            Map.entry("SEA_LUMIES", "glow_berries"), Map.entry("LOTUS", "pink_petals"));

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<CollectionRepo.Category> categories = CollectionRepo.categories();
        int railCount = categories.size() + (com.teslamaps.profileviewer.PvExtras.minions() ? 1 : 0);
        for (int i = 0; i < railCount; i++) {
            int bx = lastLeft + 6;
            int by = lastTop + 10 + i * 20;
            if (mouseX >= bx && mouseX < bx + RAIL - 6 && mouseY >= by && mouseY < by + 18) {
                category = i;
                page = 0;
                return true;
            }
        }

        if (pagerW > 0 && mouseY >= pagerY - 2 && mouseY <= pagerY + 10) {
            if (mouseX >= pagerX - 4 && mouseX <= pagerX + 10) {
                page = (page - 1 + pageCount) % pageCount;
                return true;
            }
            if (mouseX >= pagerX + pagerW - 10 && mouseX <= pagerX + pagerW + 4) {
                page = (page + 1) % pageCount;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (pageCount <= 1) return false;
        page = (int) Math.max(0, Math.min(pageCount - 1, page - amount));
        return true;
    }
}
