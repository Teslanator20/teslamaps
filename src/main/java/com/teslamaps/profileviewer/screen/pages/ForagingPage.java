package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.profileviewer.data.ForagingRepo;
import com.teslamaps.profileviewer.data.PerkRegistry;
import com.teslamaps.profileviewer.data.SkillTreeInfo;
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
import java.util.List;
import java.util.Map;

/** Heart of the Forest, following skyblock-pv: level from the tree xp, gifts and whispers per loadout. */
public class ForagingPage extends ProfileViewerPage {

    private static final Map<String, String> TREE_ICONS = Map.of(
            "FIG", "stripped_birch_log", "MANGROVE", "mangrove_log", "HELIX", "pale_oak_log");

    private static final Map<String, String> STARLYN = Map.of(
            "FIG", "agatha", "MANGROVE", "agatha", "HELIX", "miria");

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
        drawTree(ctx, w -> build(profile.getMemberData(), w), width, x, y, mouseX, mouseY);
    }

    private Ui build(JsonObject member, int width) {
        SkillTreeInfo tree = new SkillTreeInfo(member, SkillTreeInfo.Type.FORAGING);
        JsonObject core = object(member, "foraging_core");
        JsonObject foraging = object(member, "foraging");

        List<PerkRegistry.Perk> perks = PerkRegistry.hotf();
        Ui treeView = PerkTreeView.component(perks, tree.nodes, tree.level, PerkTreeView.FORAGING, tree.disabled);

        // only the tree scales, exactly to the free height - letting the whole page shrink made
        // the tree end up at half of THIS again and everything else unreadable
        // only the tree scales down to the free height, and never up - it should look exactly
        // like the mining page, not bigger
        Ui page = compose(width, tree, core, foraging, treeView);
        int available = NeuStyle.PANEL_HEIGHT - 20;
        if (page.height() > available && treeView.height() > 0) {
            int rest = page.height() - treeView.height();
            float factor = Math.max(0.5f, (available - rest) / (float) treeView.height());
            if (factor < 1f) page = compose(width, tree, core, foraging, new Ui.Scaled(treeView, factor));
        }
        return page;
    }

    /**
     * Next to the tree, in two columns. Stacked under each other the side content was taller than
     * the window and dragged the whole page - tree included - through the shrink.
     */
    private Ui compose(int width, SkillTreeInfo tree, JsonObject core, JsonObject foraging, Ui treeView) {
        int sideWidth = Math.max(150, width - treeView.width() - 10);
        int half = Math.max(110, (sideWidth - 10) / 2);

        // mirror of the mining page: whispers where the powder sits, gifts below, bests right
        Ui side = sideWidth >= 230
                ? Ui.row(10, Ui.V.TOP,
                        Ui.col(6, Kit.section("Whispers", whispers(half, tree, core)),
                                Kit.section("Tree Gifts", gifts(foraging, half))),
                        Ui.col(6, Kit.section("Personal Bests", personalBests(foraging, half)),
                                Kit.section("Today", today(core, foraging, half))))
                : Ui.col(6, Kit.section("Whispers", whispers(sideWidth, tree, core)),
                        Kit.section("Tree Gifts", gifts(foraging, sideWidth)),
                        Kit.section("Personal Bests", personalBests(foraging, sideWidth)),
                        Kit.section("Today", today(core, foraging, sideWidth)));

        return Ui.col(6, header(width, tree), Ui.row(10, Ui.V.TOP, treeView, side));
    }

    private Ui header(int width, SkillTreeInfo tree) {
        String right = tree.isMaxed()
                ? Kit.compact(tree.xp) + " XP - MAX"
                : Kit.compact(tree.xpIntoLevel) + " / " + Kit.compact(tree.xpForLevel) + " XP";

        // the loadout used to be its own line, which cost the tree a row of nodes
        return Ui.col(3,
                new Ui.SpaceBetween(width,
                        Ui.row(6, Ui.V.CENTER,
                                new Ui.Label("Heart of the Forest", NeuStyle.TEXT, Ui.SUBTITLE),
                                new Ui.Label("Level " + tree.level + "/" + tree.maxLevel,
                                        tree.isMaxed() ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT_GREEN)),
                        Ui.row(8, Ui.V.CENTER,
                                new Ui.Label("Loadout " + tree.slot + " - " + tree.tokensSpent + " tokens",
                                        NeuStyle.TEXT_GRAY, Ui.SMALL),
                                new Ui.Label(right, NeuStyle.TEXT_GRAY))),
                Kit.bar(width, tree.progress()));
    }

    /** Same spot and shape as HotM's powder tiles: current / lifetime per whisper type. */
    private Ui whispers(int width, SkillTreeInfo tree, JsonObject core) {
        long forestTotal = tree.total(core, "forest");
        long forestLeft = forestTotal - tree.spent(core, "forest");
        long desertTotal = tree.total(core, "desert");
        long desertLeft = desertTotal - tree.spent(core, "desert");

        return Kit.tileRow(width, List.of(
                new Kit.TileData("Forest", Kit.compact(Math.max(0, forestLeft)) + " / " + Kit.compact(forestTotal), NeuStyle.TEXT_AQUA),
                new Kit.TileData("Desert", Kit.compact(Math.max(0, desertLeft)) + " / " + Kit.compact(desertTotal), NeuStyle.TEXT_GOLD)));
    }

    private Ui today(JsonObject core, JsonObject foraging, int width) {
        int fish = foraging != null && foraging.has("fish_family") && foraging.get("fish_family").isJsonArray()
                ? foraging.getAsJsonArray("fish_family").size() : 0;

        return Ui.col(3,
                row(width, "Trees cut", String.valueOf(asInt(get(core, "daily_trees_cut"))), NeuStyle.TEXT_GREEN),
                row(width, "Gifts", String.valueOf(asInt(get(core, "daily_gifts"))), NeuStyle.TEXT_GREEN),
                row(width, "Fish Family", String.valueOf(fish), NeuStyle.TEXT));
    }

    private Ui row(int width, String label, String value, int color) {
        return new Ui.SpaceBetween(width,
                new Ui.Label(label, NeuStyle.TEXT_GRAY),
                new Ui.Label(value, color));
    }

    private Ui gifts(JsonObject foraging, int width) {
        JsonObject trees = object(foraging, "tree_gifts");
        JsonObject claimed = object(trees, "milestone_tier_claimed");

        List<Ui> cards = new ArrayList<>();
        for (String tree : ForagingRepo.trees()) {
            long amount = asLong(get(trees, tree));
            int tier = asInt(get(claimed, tree));
            ForagingRepo.Milestone milestone = ForagingRepo.milestone(tree, amount);
            int max = milestone.maxTier();
            boolean maxed = max > 0 && tier >= max;
            int color = maxed ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT_AQUA;
            int bodyWidth = width - 16 - 6;

            // only name and tier on the line - the gift count squeezed the name down to one letter
            Ui body = Ui.col(2,
                    new Ui.SpaceBetween(bodyWidth,
                            new Ui.Label(Kit.prettify(tree), NeuStyle.TEXT),
                            new Ui.Label(tier + "/" + max, color)),
                    Kit.bar(bodyWidth, maxed ? 1 : milestone.progress(), color));

            List<String> tooltip = List.of(
                    "§a" + Kit.prettify(tree) + " Gifts",
                    "§7Milestone §f" + tier + "§7/§f" + max,
                    "§7Total gifts: §f" + com.teslamaps.profileviewer.ui.Kit.dots(amount),
                    maxed ? "§6All milestones claimed"
                            : "§7Next milestone at §f" + com.teslamaps.profileviewer.ui.Kit.dots(milestone.next()));

            cards.add(new Ui.Tip(Ui.row(6, Ui.V.CENTER,
                    new Ui.Icon(Kit.icon(TREE_ICONS.getOrDefault(tree, "oak_log")), 16, false, null), body), tooltip));
        }
        return Ui.col(5, cards);
    }

    /** Fig and Mangrove are Agatha's contests, Helix is Miria's; the caps come from the repo. */
    private Ui personalBests(JsonObject foraging, int width) {
        JsonObject bests = object(object(foraging, "starlyn"), "personal_bests");

        List<Ui> rows = new ArrayList<>();
        for (String tree : ForagingRepo.trees()) {
            long best = asLong(get(bests, tree + "_LOG"));
            long max = ForagingRepo.value(tree.toLowerCase() + "_personal_best");
            boolean full = max > 0 && best >= max;
            // compact on both sides, the long form crushed the label next to it
            String value = max > 0
                    ? Kit.compact(Math.min(best, max)) + " / " + Kit.compact(max)
                    : Kit.compact(best);

            rows.add(new Ui.Tip(new Ui.SpaceBetween(width,
                    new Ui.Label(Kit.prettify(tree) + " Log", NeuStyle.TEXT_GRAY),
                    new Ui.Label(value, full ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT)),
                    List.of("§a" + Kit.prettify(tree) + " Log",
                            "§7Personal best: §f" + com.teslamaps.profileviewer.ui.Kit.dots(best),
                            "§7Counts up to §f" + com.teslamaps.profileviewer.ui.Kit.dots(max),
                            "§7Contest: §f" + Kit.prettify(STARLYN.getOrDefault(tree, "")))));
        }

        for (String contest : List.of("agatha", "miria")) {
            long best = asLong(get(bests, contest));
            if (best <= 0) continue;
            rows.add(new Ui.SpaceBetween(width,
                    new Ui.Label(Kit.prettify(contest) + " Contest", NeuStyle.TEXT_GRAY),
                    new Ui.Label(com.teslamaps.profileviewer.ui.Kit.dots(best), NeuStyle.TEXT)));
        }

        return rows.isEmpty() ? new Ui.Label("None yet", NeuStyle.TEXT_GRAY) : Ui.col(3, rows);
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject()
                ? parent.getAsJsonObject(key) : null;
    }

    private static JsonElement get(JsonObject parent, String key) {
        return parent != null && parent.has(key) ? parent.get(key) : null;
    }
}
