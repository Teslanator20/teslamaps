package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.profileviewer.PvExtras;
import com.teslamaps.profileviewer.data.CollectionRepo;
import com.teslamaps.profileviewer.data.EliteRanks;
import com.teslamaps.profileviewer.data.SbLevels;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.data.SkyblockProfiles;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import com.teslamaps.profileviewer.ui.Kit;
import com.teslamaps.profileviewer.ui.Ui;
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

/** SkyBlock level per category (after NEU's LevelPage) and the chocolate factory (after NEU's HoppityPage). */
public class LevelPage extends ProfileViewerPage {
    private static final List<String> RARITIES = List.of("common", "uncommon", "rare", "epic", "legendary", "mythic", "divine");
    private static final int[] RARITY_COLORS = {0xFFFFFFFF, 0xFF55FF55, 0xFF5555FF, 0xFFAA00AA, 0xFFFFAA00, 0xFFFF55FF, 0xFF55FFFF};
    private static final String[][] EMPLOYEES = {
            {"rabbit_bro", "Rabbit Bro"}, {"rabbit_cousin", "Rabbit Cousin"}, {"rabbit_sis", "Rabbit Sis"},
            {"rabbit_father", "Rabbit Daddy"}, {"rabbit_grandma", "Rabbit Granny"}, {"rabbit_uncle", "Rabbit Uncle"},
            {"rabbit_dog", "Rabbit Dog"}};

    private static boolean hoppity = false;
    private static Map<String, String> rabbitRarity;
    private static Map<String, Integer> rarityTotals;

    private Ui tree;
    private Object treeProfile;
    private int treeWidth;
    private int treeVersion;
    private boolean treeHoppity;
    private int lastLeft;
    private int lastTop;
    private int[] levelTab;
    private int[] hoppityTab;

    @Override
    public void init(com.teslamaps.profileviewer.screen.ProfileViewerScreen parent, SkyblockProfiles profiles) {
        super.init(parent, profiles);
        tree = null;
        SkyblockProfile profile = getProfile();
        if (profile != null && profiles != null && PvExtras.levelPage()) {
            EliteRanks.request(profiles.getUuid(), profile.getProfileId());
        }
    }

    @Override
    public boolean isAvailable(SkyblockProfiles profiles) {
        return PvExtras.levelPage();
    }

    @Override
    public boolean isFixedLayout() {
        return true;
    }

    @Override
    public String getTabName() {
        return "Level & Hoppity";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.NETHER_STAR);
    }

    @Override
    public void onSelected() {
        CollectionRepo.load();
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

        String a = "SkyBlock Level";
        String b = "Chocolate Factory";
        int gap = 12;
        int ax = left + NeuStyle.PANEL_WIDTH / 2 - (tr.width(a) + gap + tr.width(b)) / 2;
        int bx = ax + tr.width(a) + gap;
        NeuStyle.window(ctx, ax - 5, top + 2, tr.width(a) + 10, 14);
        NeuStyle.window(ctx, bx - 5, top + 2, tr.width(b) + 10, 14);
        if (!hoppity) NeuStyle.slimBox(ctx, ax - 5, top + 2, tr.width(a) + 10, 14);
        else NeuStyle.slimBox(ctx, bx - 5, top + 2, tr.width(b) + 10, 14);
        ctx.text(tr, a, ax, top + 5, hoppity ? NeuStyle.TEXT_GRAY : NeuStyle.TEXT_YELLOW);
        ctx.text(tr, b, bx, top + 5, hoppity ? NeuStyle.TEXT_YELLOW : NeuStyle.TEXT_GRAY);
        levelTab = new int[]{ax - 5, top + 2, tr.width(a) + 10, 14};
        hoppityTab = new int[]{bx - 5, top + 2, tr.width(b) + 10, 14};

        int contentWidth = width;
        int version = EliteRanks.version() + (CollectionRepo.isLoaded() ? 1 << 20 : 0);
        if (tree == null || treeProfile != profile || treeWidth != contentWidth || treeVersion != version || treeHoppity != hoppity) {
            treeProfile = profile;
            treeWidth = contentWidth;
            treeVersion = version;
            treeHoppity = hoppity;
            tree = hoppity ? hoppity(profile, contentWidth) : levels(profile, contentWidth);
        }
        drawTree(ctx, tree, x, y + 16, contentWidth, mouseX, mouseY);
    }

    private static boolean inside(int[] r, double x, double y) {
        return r != null && x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inside(levelTab, mouseX, mouseY)) {
            hoppity = false;
            return true;
        }
        if (inside(hoppityTab, mouseX, mouseY)) {
            hoppity = true;
            return true;
        }
        return false;
    }

    // ---- level ------------------------------------------------------------------------------

    private Ui levels(SkyblockProfile profile, int width) {
        double level = profile.getSkyblockLevel();
        int whole = (int) level;
        List<String> headTip = new ArrayList<>(List.of("§bSkyBlock Level " + whole,
                "§7Total XP: §f" + Kit.dots(Math.round(level * 100))));
        String rank = PvExtras.rankLine("skyblockxp");
        if (rank != null) headTip.add(rank);

        Ui header = new Ui.Tip(Ui.col(3,
                new Ui.SpaceBetween(width,
                        new Ui.Label("Level " + whole, NeuStyle.TEXT_AQUA, Ui.SUBTITLE),
                        new Ui.Label(Math.round((level - whole) * 100) + " / 100 XP", NeuStyle.TEXT_GRAY)),
                Kit.bar(width, level - whole)), headTip);

        int columns = 2;
        int cellWidth = Kit.cellWidth(width, columns, 12);
        List<Ui> cells = new ArrayList<>();
        for (SbLevels.Category category : SbLevels.compute(profile)) {
            double progress = category.max() > 0 ? Math.min(1, category.xp() / (double) category.max()) : 0;
            List<String> tip = new ArrayList<>();
            tip.add("§a" + category.name());
            for (SbLevels.Line line : category.lines()) {
                String color = line.max() > 0 && line.got() >= line.max() ? "§a" : "§e";
                tip.add("§6" + line.name() + ": " + color + Kit.dots(line.got()) + "§8/" + Kit.dots(line.max()));
            }
            if (category.name().equals("Core") && !CollectionRepo.isLoaded()) tip.add("§8Collections still loading");
            int body = cellWidth - 16 - 6;
            cells.add(new Ui.Tip(Ui.row(6, Ui.V.CENTER,
                    new Ui.Icon(Kit.icon(category.icon()), 16, false, null),
                    Ui.col(2,
                            new Ui.SpaceBetween(body,
                                    new Ui.Label(category.name(), NeuStyle.TEXT),
                                    new Ui.Label(Kit.dots(category.xp()) + " / " + Kit.dots(category.max()),
                                            progress >= 1 ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT_GRAY, Ui.SMALL)),
                            Kit.bar(body, progress, progress >= 1 ? NeuStyle.TEXT_GOLD : 0xFF55FFFF))), tip));
        }
        return Ui.col(10, header, Kit.grid(cells, columns, 12, 8),
                new Ui.Label("Estimated like NEU; what the api does not show is counted under Other", NeuStyle.TEXT_DARK, Ui.SMALL));
    }

    // ---- hoppity ----------------------------------------------------------------------------

    private static void loadRabbits() {
        if (rabbitRarity != null) return;
        Map<String, String> rarity = new HashMap<>();
        Map<String, Integer> totals = new HashMap<>();
        try (InputStream stream = LevelPage.class.getResourceAsStream("/assets/teslamaps/pv/hoppity.json")) {
            if (stream != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("rarities").entrySet()) {
                    int count = 0;
                    for (JsonElement name : e.getValue().getAsJsonArray()) {
                        rarity.put(name.getAsString(), e.getKey());
                        count++;
                    }
                    totals.put(e.getKey(), count);
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to read hoppity.json: {}", e.toString());
        }
        rabbitRarity = rarity;
        rarityTotals = totals;
    }

    private static JsonObject obj(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }

    private Ui hoppity(SkyblockProfile profile, int width) {
        JsonObject easter = obj(obj(profile.getMemberData(), "events"), "easter");
        if (easter == null) return new Ui.Label("No chocolate factory on this profile", NeuStyle.TEXT_GRAY);
        loadRabbits();

        int barnLevel = asInt(easter.get("rabbit_barn_capacity_level"));
        int rank = EliteRanks.rank("chocolate");
        List<Kit.TileData> tiles = new ArrayList<>(List.of(
                new Kit.TileData("Chocolate", Kit.compact(asLong(easter.get("chocolate"))), NeuStyle.TEXT_GOLD),
                new Kit.TileData("Since Prestige", Kit.compact(asLong(easter.get("chocolate_since_prestige"))), NeuStyle.TEXT_GOLD),
                new Kit.TileData("All-Time", Kit.compact(asLong(easter.get("total_chocolate"))), NeuStyle.TEXT_GOLD),
                new Kit.TileData("Spent", Kit.compact(asLong(obj(easter, "shop") == null ? null : obj(easter, "shop").get("chocolate_spent"))), NeuStyle.TEXT),
                new Kit.TileData("Prestige", String.valueOf(Math.max(1, asInt(easter.get("chocolate_level")))), NeuStyle.TEXT_LIGHT_PURPLE),
                new Kit.TileData("Leaderboard", rank > 0 ? "#" + Kit.dots(rank) : "-", NeuStyle.TEXT_AQUA)));

        // rabbits per rarity: uniques / all, duplicates in the tooltip
        JsonObject rabbits = obj(easter, "rabbits");
        Map<String, int[]> found = new HashMap<>();
        if (rabbits != null) {
            for (Map.Entry<String, JsonElement> e : rabbits.entrySet()) {
                if (!e.getValue().isJsonPrimitive()) continue;
                String rarity = rabbitRarity.get(e.getKey());
                if (rarity == null) continue;
                int count = asInt(e.getValue());
                if (count <= 0) continue;
                int[] entry = found.computeIfAbsent(rarity, k -> new int[2]);
                entry[0]++;
                entry[1] += count - 1;
            }
        }
        int columns = Math.max(1, Math.min(4, width / 95));
        int cellWidth = Kit.cellWidth(width, columns, 10);
        List<Ui> rarityCells = new ArrayList<>();
        int uniques = 0;
        int dupes = 0;
        int all = 0;
        for (int i = 0; i < RARITIES.size(); i++) {
            String rarity = RARITIES.get(i);
            int[] entry = found.getOrDefault(rarity, new int[2]);
            int total = rarityTotals.getOrDefault(rarity, 0);
            uniques += entry[0];
            dupes += entry[1];
            all += total;
            String name = Kit.prettify(rarity);
            rarityCells.add(new Ui.Tip(Ui.col(2,
                    new Ui.SpaceBetween(cellWidth,
                            new Ui.Label(name, RARITY_COLORS[i]),
                            new Ui.Label(entry[0] + "/" + total, entry[0] >= total && total > 0 ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT)),
                    Kit.bar(cellWidth, total > 0 ? entry[0] / (double) total : 0, RARITY_COLORS[i])),
                    List.of("§f" + name + " Rabbits", "§7Unique: §f" + entry[0] + "/" + total, "§7Duplicates: §f" + entry[1])));
        }

        List<Ui> upgrades = new ArrayList<>();
        JsonObject employees = obj(easter, "employees");
        for (String[] employee : EMPLOYEES) {
            upgrades.add(row(cellWidth, employee[1], String.valueOf(employees == null ? 0 : asInt(employees.get(employee[0])))));
        }
        JsonObject tower = obj(easter, "time_tower");
        upgrades.add(row(cellWidth, "Hand-Baked", String.valueOf(asInt(easter.get("click_upgrades")) + 1)));
        upgrades.add(row(cellWidth, "Time Tower", String.valueOf(tower == null ? 0 : asInt(tower.get("level")))));
        upgrades.add(row(cellWidth, "Rabbit Shrine", String.valueOf(asInt(easter.get("rabbit_rarity_upgrades")))));
        upgrades.add(row(cellWidth, "Coach Jackrabbit", String.valueOf(asInt(easter.get("chocolate_multiplier_upgrades")))));
        upgrades.add(row(cellWidth, "Barn Capacity", String.valueOf(barnLevel * 2 + 18)));

        return Ui.col(8,
                Kit.tileRow(width, tiles),
                Kit.section("Rabbits - " + uniques + "/" + all + " unique, " + dupes + " duplicates",
                        Kit.grid(rarityCells, columns, 10, 5)),
                Kit.section("Upgrades", Kit.grid(upgrades, columns, 10, 3)));
    }

    private static Ui row(int width, String label, String value) {
        return new Ui.SpaceBetween(width, new Ui.Label(label, NeuStyle.TEXT_GRAY), new Ui.Label(value, NeuStyle.TEXT));
    }
}
