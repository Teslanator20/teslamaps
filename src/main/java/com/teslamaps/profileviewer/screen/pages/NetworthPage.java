package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.api.DungeonData;
import com.teslamaps.api.HypixelApi;
import com.teslamaps.features.EstimatedValue;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.profileviewer.data.InventoryDecoder;
import com.teslamaps.profileviewer.data.PetData;
import com.teslamaps.profileviewer.data.SkillData;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.data.SkyblockProfiles;
import com.teslamaps.profileviewer.screen.ItemGrid;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import com.teslamaps.profileviewer.screen.ProfileViewerScreen;
import com.teslamaps.profileviewer.ui.Kit;
import com.teslamaps.profileviewer.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Networth per container with the most valuable items, plus Senither weight. */
public class NetworthPage extends ProfileViewerPage {

    private record Source(String category, InventoryDecoder.Slot slot) {}

    private record Valued(String category, InventoryDecoder.Slot slot, String name, int count, double value) {}

    private static final String[][] CATEGORIES = {
            {"Inventory", "chest"}, {"Armor & Equipment", "diamond_chestplate"}, {"Wardrobe", "leather_chestplate"},
            {"Ender Chest", "ender_chest"}, {"Backpacks", "bundle"}, {"Accessories", "golden_apple"},
            {"Personal Vault", "iron_door"}, {"Bags", "fishing_rod"}, {"Pets", "bone"}, {"Sacks", "leather"},
            {"Museum", "gold_block"}};

    private List<Source> sources;
    private volatile JsonObject museum;
    private volatile boolean museumLoading;
    private boolean museumDecoded;
    private int valuedVersion = -1;

    private final Map<String, Double> totals = new LinkedHashMap<>();
    private final Map<String, List<Valued>> top = new LinkedHashMap<>();
    private List<Valued> overall = List.of();
    private double itemTotal;
    private double[] weight;
    private Object treeKey = new Object();

    @Override
    public String getTabName() {
        return "Networth";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.GOLD_INGOT);
    }

    @Override
    public void init(ProfileViewerScreen parent, SkyblockProfiles profiles) {
        super.init(parent, profiles);
        sources = null;
        museum = null;
        museumLoading = false;
        museumDecoded = false;
        valuedVersion = -1;
        weight = null;
    }

    @Override
    public void onSelected() {
        PriceManager.ensureFresh();
        SkyblockProfile profile = getProfile();
        if (profile != null && museum == null && !museumLoading) {
            museumLoading = true;
            HypixelApi.getMuseum(profile.getProfileId()).thenAccept(data -> {
                museum = data;
                museumLoading = false;
            });
        }
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        if (sources == null) {
            sources = decodeAll(profile);
            weight = senither(profile);
        }
        if (!museumDecoded && museum != null) {
            museumDecoded = true;
            sources.addAll(museumItems(profile, museum));
            valuedVersion = -1;
        }
        int prices = PriceManager.version();
        if (prices != valuedVersion) {
            valuedVersion = prices;
            value(profile);
            treeKey = new Object();
        }

        drawTree(ctx, cachedTree(treeKey, w -> build(profile, w)), width, x, y, mouseX, mouseY);
    }

    // ---------------------------------------------------------------- data

    private List<Source> decodeAll(SkyblockProfile profile) {
        List<Source> out = new ArrayList<>();
        JsonObject member = profile.getMemberData();
        JsonObject inv = object(member, "inventory");
        add(out, "Inventory", InventoryDecoder.decode(inv, "inv_contents"));
        add(out, "Armor & Equipment", InventoryDecoder.decode(inv, "inv_armor"));
        add(out, "Armor & Equipment", InventoryDecoder.decode(inv, "equipment_contents"));
        add(out, "Ender Chest", InventoryDecoder.decode(inv, "ender_chest_contents"));
        add(out, "Accessories", InventoryDecoder.decode(inv, "bag_contents.talisman_bag"));
        add(out, "Personal Vault", InventoryDecoder.decode(inv, "personal_vault_contents"));
        add(out, "Bags", InventoryDecoder.decode(inv, "bag_contents.fishing_bag"));
        add(out, "Bags", InventoryDecoder.decode(inv, "bag_contents.potion_bag"));
        add(out, "Bags", InventoryDecoder.decode(inv, "bag_contents.quiver"));

        JsonObject packs = object(inv, "backpack_contents");
        if (packs != null) for (String key : packs.keySet()) add(out, "Backpacks", InventoryDecoder.decode(packs, key));
        JsonObject icons = object(inv, "backpack_icons");
        if (icons != null) for (String key : icons.keySet()) add(out, "Backpacks", InventoryDecoder.decode(icons, key));

        List<InventoryDecoder.Slot> wardrobe = InventoryDecoder.decode(inv, "wardrobe_contents");
        if (!wardrobe.isEmpty()) {
            add(out, "Wardrobe", wardrobe);
        } else {
            addLoadout(out, object(object(member, "loadout"), "armor"));
        }
        addLoadout(out, object(object(member, "loadout"), "equipment"));
        return out;
    }

    private static void addLoadout(List<Source> out, JsonObject sets) {
        if (sets == null) return;
        for (Map.Entry<String, JsonElement> set : sets.entrySet()) {
            if (!set.getValue().isJsonObject()) continue;
            JsonObject pieces = set.getValue().getAsJsonObject();
            for (String piece : pieces.keySet()) {
                if (pieces.get(piece).isJsonObject()) add(out, "Wardrobe", InventoryDecoder.decode(pieces, piece));
            }
        }
    }

    private static void add(List<Source> out, String category, List<InventoryDecoder.Slot> slots) {
        for (InventoryDecoder.Slot slot : slots) {
            if (!slot.isEmpty() && slot.skyblockId != null) out.add(new Source(category, slot));
        }
    }

    private static List<Source> museumItems(SkyblockProfile profile, JsonObject data) {
        List<Source> out = new ArrayList<>();
        JsonObject members = object(data, "members");
        if (members == null) return out;
        String owner = profile.getOwnerUuid() == null ? "" : profile.getOwnerUuid().replace("-", "");
        JsonObject member = null;
        for (String key : members.keySet()) {
            if (key.replace("-", "").equalsIgnoreCase(owner)) member = object(members, key);
        }
        if (member == null && members.size() == 1) member = object(members, members.keySet().iterator().next());
        if (member == null) return out;

        JsonObject items = object(member, "items");
        if (items != null) {
            for (String key : items.keySet()) {
                JsonObject entry = object(items, key);
                JsonObject stored = object(entry, "items");
                if (stored != null && stored.has("data")) {
                    add(out, "Museum", InventoryDecoder.decodeBase64(stored.get("data").getAsString()));
                }
            }
        }
        if (member.has("special") && member.get("special").isJsonArray()) {
            for (JsonElement element : member.getAsJsonArray("special")) {
                JsonObject stored = element.isJsonObject() ? object(element.getAsJsonObject(), "items") : null;
                if (stored != null && stored.has("data")) {
                    add(out, "Museum", InventoryDecoder.decodeBase64(stored.get("data").getAsString()));
                }
            }
        }
        return out;
    }

    private void value(SkyblockProfile profile) {
        totals.clear();
        top.clear();
        for (String[] category : CATEGORIES) totals.put(category[0], 0.0);

        List<Valued> all = new ArrayList<>();
        for (Source source : sources) {
            InventoryDecoder.Slot slot = source.slot();
            double each = EstimatedValue.compute(ItemGrid.stackFor(slot));
            if (each <= 0) each = InventoriesPage.unitPrice(slot.skyblockId);
            if (each <= 0) continue;
            String name = slot.name != null ? slot.name : slot.skyblockId;
            all.add(new Valued(source.category(), slot, name, slot.count, each * slot.count));
        }

        for (PetData pet : profile.getPets()) {
            Double bin = PriceManager.lowestBinOrNull(pet.getType() + ";" + pet.getRarityOrdinal());
            double value = bin == null ? 0 : bin;
            if (pet.getHeldItem() != null) value += InventoriesPage.unitPrice(pet.getHeldItem());
            if (value <= 0) continue;
            String name = pet.getRarityCode() + "[Lvl " + pet.getLevel() + "] " + pet.getDisplayName();
            all.add(new Valued("Pets", null, name, 1, value));
        }

        JsonObject counts = object(object(profile.getMemberData(), "inventory"), "sacks_counts");
        if (counts != null) {
            for (Map.Entry<String, JsonElement> e : counts.entrySet()) {
                long amount = asLong(e.getValue());
                if (amount <= 0) continue;
                double value = amount * InventoriesPage.unitPrice(e.getKey());
                if (value <= 0) continue;
                String name = com.teslamaps.utils.SkyblockItems.coloredName(e.getKey());
                all.add(new Valued("Sacks", null, name == null ? Kit.prettify(e.getKey()) : name,
                        (int) Math.min(Integer.MAX_VALUE, amount), value));
            }
        }

        all.sort((a, b) -> Double.compare(b.value(), a.value()));
        itemTotal = 0;
        for (Valued v : all) {
            itemTotal += v.value();
            totals.merge(v.category(), v.value(), Double::sum);
            List<Valued> list = top.computeIfAbsent(v.category(), k -> new ArrayList<>());
            if (list.size() < 10) list.add(v);
        }
        overall = all.stream().filter(v -> v.slot() != null).limit(10).toList();
    }

    // ---------------------------------------------------------------- senither weight

    private static final String[] WEIGHT_SKILLS = {"mining", "foraging", "enchanting", "farming", "combat", "fishing", "alchemy", "taming"};
    private static final double[][] SKILL_WEIGHT = {{1.18207448, 259634}, {1.232826, 259634}, {0.96976583, 882758},
            {1.217848139, 220689}, {1.15797687265, 275862}, {1.406418, 88274}, {1, 1103448}, {1.14744, 441379}};
    private static final int[] SKILL_MAX = {60, 50, 60, 60, 60, 50, 50, 50};
    private static final String[] WEIGHT_SLAYERS = {"zombie", "spider", "wolf", "enderman"};
    private static final double[][] SLAYER_WEIGHT = {{2208, 0.15}, {2118, 0.08}, {1962, 0.015}, {1430, 0.017}};
    private static final double CATA_WEIGHT = 0.0002149604615;
    private static final double CLASS_WEIGHT = 4.5254834e-06;
    private static final double CATA_50 = 569809640;

    /** {skills, slayer, dungeons}, each base plus overflow. */
    private static double[] senither(SkyblockProfile profile) {
        JsonObject exp = object(object(profile.getMemberData(), "player_data"), "experience");
        double skills = 0;
        for (int i = 0; i < WEIGHT_SKILLS.length; i++) {
            double xp = exp != null && exp.has("SKILL_" + WEIGHT_SKILLS[i].toUpperCase())
                    ? exp.get("SKILL_" + WEIGHT_SKILLS[i].toUpperCase()).getAsDouble() : 0;
            if (xp <= 0) continue;
            SkillData skill = new SkillData(WEIGHT_SKILLS[i], xp, WEIGHT_SKILLS[i], SKILL_MAX[i]);
            double level = skill.getLevel() + (skill.getLevel() < SKILL_MAX[i] ? skill.getProgress() : 0);
            double maxXp = SkillData.xpForLevel(SKILL_MAX[i]);
            double base = Math.pow(level * 10, 0.5 + SKILL_WEIGHT[i][0] + level / 100) / 1250;
            skills += xp <= maxXp ? base : Math.round(base) + Math.pow((xp - maxXp) / SKILL_WEIGHT[i][1], 0.968);
        }

        double slayer = 0;
        JsonObject bosses = object(object(profile.getMemberData(), "slayer"), "slayer_bosses");
        for (int i = 0; i < WEIGHT_SLAYERS.length; i++) {
            long xp = asLong(get(object(bosses, WEIGHT_SLAYERS[i]), "xp"));
            double divider = SLAYER_WEIGHT[i][0];
            if (xp <= 1_000_000) {
                slayer += xp / divider;
                continue;
            }
            double modifier = SLAYER_WEIGHT[i][1];
            double remaining = xp - 1_000_000;
            double overflow = 0;
            while (remaining > 0) {
                double left = Math.min(remaining, 1_000_000);
                overflow += Math.pow(left / (divider * (1.5 + modifier)), 0.942);
                modifier += SLAYER_WEIGHT[i][1];
                remaining -= left;
            }
            slayer += 1_000_000 / divider + overflow;
        }

        double dungeons = 0;
        DungeonData data = profile.getDungeonData();
        if (data != null) {
            dungeons += dungeonWeight(data.getCatacombsXp(), data.getCatacombsLevel() + data.getCatacombsProgress(), CATA_WEIGHT);
            for (DungeonData.ClassData cls : data.getClasses().values()) {
                dungeons += dungeonWeight(cls.getXp(), cls.getLevel() + cls.getProgress(), CLASS_WEIGHT);
            }
        }
        return new double[]{skills, slayer, dungeons};
    }

    private static double dungeonWeight(double xp, double level, double factor) {
        double base = Math.pow(Math.min(50, level), 4.5) * factor;
        if (xp <= CATA_50) return base;
        double splitter = (4 * CATA_50) / base;
        return Math.floor(base) + Math.pow((xp - CATA_50) / splitter, 0.968);
    }

    // ---------------------------------------------------------------- layout

    private Ui build(SkyblockProfile profile, int width) {
        double purse = profile.getCoinPurse();
        double bank = profile.getBankBalance();
        double networth = itemTotal + purse + bank;

        List<Ui> parts = new ArrayList<>();
        parts.add(new Ui.SpaceBetween(width,
                Ui.row(5, Ui.V.CENTER,
                        new Ui.Icon(Kit.icon("gold_ingot"), 12, false, null),
                        new Ui.Label("Networth " + Kit.compact((long) networth), NeuStyle.TEXT_GOLD)),
                new Ui.Label(PriceManager.isLoaded() ? "insta-sell / lowest BIN" : "loading prices...",
                        NeuStyle.TEXT_DARK, Ui.SMALL)));
        parts.add(Kit.tileRow(width, List.of(
                new Kit.TileData("Items", Kit.compact((long) itemTotal), NeuStyle.TEXT_GOLD),
                new Kit.TileData("Purse", Kit.compact((long) purse), NeuStyle.TEXT_GOLD),
                new Kit.TileData("Bank", profile.hasBankData() ? Kit.compact((long) bank) : "api off", NeuStyle.TEXT_GOLD))));

        int columns = Math.max(1, Math.min(3, width / 130));
        int cellWidth = Kit.cellWidth(width, columns, 12);
        List<Ui> rows = new ArrayList<>();
        for (String[] category : CATEGORIES) {
            double total = totals.getOrDefault(category[0], 0.0);
            boolean pending = category[0].equals("Museum") && museum == null;
            List<String> tip = new ArrayList<>();
            tip.add("§a" + category[0] + " §6" + Kit.compact((long) total));
            for (Valued v : top.getOrDefault(category[0], List.of())) {
                tip.add("§f" + v.name() + (v.count() > 1 ? " §8x" + Kit.compact(v.count()) : "")
                        + " §6" + Kit.compact((long) v.value()));
            }
            if (pending) tip.add("§7Loading museum...");
            rows.add(new Ui.Tip(Ui.row(5, Ui.V.CENTER,
                    new Ui.Icon(Kit.icon(category[1]), 12, false, null),
                    new Ui.SpaceBetween(cellWidth - 17,
                            new Ui.Label(category[0], NeuStyle.TEXT_GRAY),
                            new Ui.Label(pending ? "..." : Kit.compact((long) total),
                                    total > 0 ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT_DARK))), tip));
        }
        parts.add(Kit.section("Categories", Kit.grid(rows, columns, 12, 5)));

        if (!overall.isEmpty()) {
            List<Ui> items = new ArrayList<>();
            int itemColumns = Math.max(1, Math.min(2, width / 180));
            int itemWidth = Kit.cellWidth(width, itemColumns, 12);
            for (Valued v : overall) {
                items.add(Ui.row(5, Ui.V.CENTER, new SlotIcon(v.slot()),
                        new Ui.SpaceBetween(itemWidth - 21,
                                new Ui.Label(Kit.clip(com.teslamaps.utils.StringUtil.strip(v.name()), itemWidth - 70), NeuStyle.TEXT),
                                new Ui.Label(Kit.compact((long) v.value()), NeuStyle.TEXT_GOLD))));
            }
            parts.add(Kit.section("Most Valuable", Kit.grid(items, itemColumns, 12, 3)));
        }

        if (weight != null) {
            double total = weight[0] + weight[1] + weight[2];
            parts.add(Kit.section("Senither Weight", Kit.tileRow(width, List.of(
                    new Kit.TileData("Total", String.valueOf(Math.round(total)), NeuStyle.TEXT_AQUA),
                    new Kit.TileData("Skills", String.valueOf(Math.round(weight[0])), NeuStyle.TEXT_GREEN),
                    new Kit.TileData("Slayer", String.valueOf(Math.round(weight[1])), NeuStyle.TEXT_RED),
                    new Kit.TileData("Dungeons", String.valueOf(Math.round(weight[2])), NeuStyle.TEXT_LIGHT_PURPLE)))));
        }
        return Ui.col(8, parts);
    }

    /** Resolves the stack per draw, the item repo answers asynchronously. */
    private static final class SlotIcon extends Ui {
        private final InventoryDecoder.Slot slot;

        SlotIcon(InventoryDecoder.Slot slot) {
            this.slot = slot;
        }

        public int width() {
            return 16;
        }

        public int height() {
            return 16;
        }

        public void render(GuiGraphicsExtractor ctx, int x, int y, int mouseX, int mouseY) {
            ItemStack stack = ItemGrid.stackFor(slot);
            com.teslamaps.features.RarityBackground.draw(ctx, x, y, stack);
            ctx.item(stack, x, y);
            if (over(mouseX, mouseY, x, y, 16, 16)) hoveredStack = stack;
        }
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }

    private static JsonElement get(JsonObject parent, String key) {
        return parent != null && parent.has(key) ? parent.get(key) : null;
    }
}
