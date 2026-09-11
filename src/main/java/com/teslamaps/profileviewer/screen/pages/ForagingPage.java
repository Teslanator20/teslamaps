package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.profileviewer.data.PerkRegistry;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.PerkTreeView;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import com.teslamaps.profileviewer.ui.Kit;
import com.teslamaps.profileviewer.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Heart of the Forest: the perk tree, forest whispers and the Galatea tree gifts. */
public class ForagingPage extends ProfileViewerPage {

    private static final int GIFT_MILESTONE_MAX = 7;

    // cumulative tree xp per tier, same brackets the mountain uses
    private static final long[] TIER_XP = {3000, 12000, 37000, 97000, 197000, 347000, 557000, 847000, 1247000};

    private static final Map<String, String> TREE_ICONS = Map.of(
            "FIG", "mangrove_log", "MANGROVE", "mangrove_log", "HELIX", "pale_oak_log");

    @Override
    public String getTabName() {
        return "Foraging";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.JUNGLE_SAPLING);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        SkyblockProfile profile = getProfile();
        if (profile == null) return;
        drawTree(ctx, build(profile.getMemberData(), width), x, y, mouseX, mouseY);
    }

    private Ui build(JsonObject member, int width) {
        JsonObject skillTree = object(member, "skill_tree");
        JsonObject nodes = skillTree != null && skillTree.has("nodes")
                ? object(skillTree.getAsJsonObject("nodes"), "foraging") : null;
        JsonObject core = object(member, "foraging_core");
        JsonObject foraging = object(member, "foraging");

        int tier = nodes != null ? asInt(nodes.get("center_of_the_forest")) : 0;
        long xp = skillTree != null ? asLong(get(object(skillTree, "experience"), "foraging")) : 0;

        Map<String, Integer> levels = new HashMap<>();
        if (nodes != null) {
            for (Map.Entry<String, JsonElement> entry : nodes.entrySet()) {
                if (entry.getKey().startsWith("toggle_")) continue;
                levels.put(entry.getKey(), asInt(entry.getValue()));
            }
        }

        List<PerkRegistry.Perk> perks = PerkRegistry.hotf();
        Ui tree = PerkTreeView.component(perks, levels, tier, PerkTreeView.FORAGING);
        int sideWidth = Math.max(140, width - tree.width() - 10);

        return Ui.col(10,
                header(width, tier, xp, skillTree),
                tiles(width, core, foraging),
                Ui.row(10, Ui.V.TOP, tree, Ui.col(10,
                        Kit.section("Tree Gifts", gifts(foraging, sideWidth)),
                        Kit.section("Personal Bests", personalBests(foraging, sideWidth)))));
    }

    private Ui header(int width, int tier, long xp, JsonObject skillTree) {
        int spent = 0;
        JsonObject tokens = object(skillTree, "tokens_spent");
        if (tokens != null) {
            for (Map.Entry<String, JsonElement> entry : tokens.entrySet()) {
                if (entry.getKey().startsWith("forest")) spent += asInt(entry.getValue());
            }
        }
        return Ui.col(4,
                new Ui.SpaceBetween(width,
                        Ui.row(6, Ui.V.CENTER,
                                new Ui.Label("Heart of the Forest", NeuStyle.TEXT, Ui.SUBTITLE),
                                new Ui.Label("Tier " + tier, NeuStyle.TEXT_GREEN)),
                        new Ui.Label(Kit.compact(xp) + " XP", NeuStyle.TEXT_GRAY)),
                Kit.bar(width, progress(xp)),
                new Ui.Label(spent + " tokens spent", NeuStyle.TEXT_GRAY));
    }

    private Ui tiles(int width, JsonObject core, JsonObject foraging) {
        long whispers = 0;
        JsonObject whisperData = object(core, "whispers");
        if (whisperData != null) {
            for (Map.Entry<String, JsonElement> entry : whisperData.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    whispers += asLong(entry.getValue().getAsJsonObject().get("total"));
                }
            }
        }
        int fish = foraging != null && foraging.has("fish_family") && foraging.get("fish_family").isJsonArray()
                ? foraging.getAsJsonArray("fish_family").size() : 0;

        return Kit.tileRow(width, List.of(
                new Kit.TileData("Whispers", Kit.compact(whispers), NeuStyle.TEXT_AQUA),
                new Kit.TileData("Trees Today", String.valueOf(asInt(get(core, "daily_trees_cut"))), NeuStyle.TEXT_GREEN),
                new Kit.TileData("Gifts Today", String.valueOf(asInt(get(core, "daily_gifts"))), NeuStyle.TEXT_GREEN),
                new Kit.TileData("Fish Family", String.valueOf(fish), NeuStyle.TEXT)));
    }

    private Ui gifts(JsonObject foraging, int width) {
        JsonObject trees = object(foraging, "tree_gifts");
        if (trees == null) return new Ui.Label("No tree gifts yet", NeuStyle.TEXT_GRAY);

        JsonObject claimed = object(trees, "milestone_tier_claimed");
        List<Ui> cards = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : trees.entrySet()) {
            if (entry.getKey().equals("milestone_tier_claimed")) continue;
            if (!entry.getValue().isJsonPrimitive()) continue;

            long amount = asLong(entry.getValue());
            int tier = asInt(get(claimed, entry.getKey()));
            boolean maxed = tier >= GIFT_MILESTONE_MAX;
            int color = maxed ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT_AQUA;
            int bodyWidth = width - 16 - 6;

            Ui body = Ui.col(2,
                    new Ui.SpaceBetween(bodyWidth,
                            new Ui.Label(Kit.prettify(entry.getKey()), NeuStyle.TEXT),
                            Ui.row(6, Ui.V.CENTER,
                                    new Ui.Label(tier + "/" + GIFT_MILESTONE_MAX, color),
                                    new Ui.Label(Kit.compact(amount) + " gifts", NeuStyle.TEXT_GRAY))),
                    Kit.bar(bodyWidth, (double) tier / GIFT_MILESTONE_MAX, color));

            String icon = TREE_ICONS.getOrDefault(entry.getKey(), "oak_log");
            cards.add(Ui.row(6, Ui.V.CENTER, new Ui.Icon(Kit.icon(icon), 16, false, null), body));
        }
        return cards.isEmpty() ? new Ui.Label("No tree gifts yet", NeuStyle.TEXT_GRAY) : Ui.col(5, cards);
    }

    private Ui personalBests(JsonObject foraging, int width) {
        JsonObject bests = object(object(foraging, "starlyn"), "personal_bests");
        if (bests == null) return new Ui.Label("None yet", NeuStyle.TEXT_GRAY);

        List<Ui> rows = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : bests.entrySet()) {
            rows.add(new Ui.SpaceBetween(width,
                    new Ui.Label(Kit.prettify(entry.getKey()), NeuStyle.TEXT_GRAY),
                    new Ui.Label(String.format("%,d", asLong(entry.getValue())), NeuStyle.TEXT)));
        }
        return rows.isEmpty() ? new Ui.Label("None yet", NeuStyle.TEXT_GRAY) : Ui.col(3, rows);
    }

    /** Progress inside whatever xp bracket the player currently sits in. */
    private static double progress(long xp) {
        long base = 0;
        for (long threshold : TIER_XP) {
            if (xp < threshold) return (double) (xp - base) / (threshold - base);
            base = threshold;
        }
        return 1;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject()
                ? parent.getAsJsonObject(key) : null;
    }

    private static JsonElement get(JsonObject parent, String key) {
        return parent != null && parent.has(key) ? parent.get(key) : null;
    }
}
