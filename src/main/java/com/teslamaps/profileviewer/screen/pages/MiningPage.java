package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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

/** Heart of the Mountain: tier from the tree xp, powder, glacite tunnels and the perk tree. */
public class MiningPage extends ProfileViewerPage {

    private static final int MITHRIL_COLOR = 0xFF55FF55;
    private static final int GEMSTONE_COLOR = 0xFFFF55FF;
    private static final int GLACITE_COLOR = 0xFF55FFFF;

    private static final List<String> CRYSTALS = List.of(
            "jade_crystal", "amber_crystal", "amethyst_crystal", "sapphire_crystal", "topaz_crystal",
            "jasper_crystal", "ruby_crystal", "opal_crystal", "onyx_crystal", "aquamarine_crystal",
            "peridot_crystal", "citrine_crystal");

    @Override
    public String getTabName() {
        return "Mining";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.STONE_PICKAXE);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        SkyblockProfile profile = getProfile();
        if (profile == null) return;
        drawTree(ctx, w -> build(profile.getMemberData(), w), width, x, y, mouseX, mouseY);
    }

    private Ui build(JsonObject member, int width) {
        SkillTreeInfo tree = new SkillTreeInfo(member, SkillTreeInfo.Type.MINING);
        JsonObject core = object(member, "mining_core");
        JsonObject glacite = object(member, "glacite_player_data");

        List<PerkRegistry.Perk> perks = PerkRegistry.hotm();
        Ui treeView = PerkTreeView.component(perks, tree.nodes, tree.level, PerkTreeView.MINING, tree.disabled);

        // only the perk tree shrinks when the page is too tall; scaling the whole page would
        // pull the text away from both edges and leave the window half empty
        Ui page = compose(width, tree, core, glacite, treeView);
        int available = NeuStyle.PANEL_HEIGHT - 20;
        if (page.height() > available && treeView.height() > 0) {
            int rest = page.height() - treeView.height();
            float factor = Math.max(0.5f, (available - rest) / (float) treeView.height());
            if (factor < 1f) page = compose(width, tree, core, glacite, new Ui.Scaled(treeView, factor));
        }
        return page;
    }

    // everything next to the tree, in two columns when there is room for them
    private Ui compose(int width, SkillTreeInfo tree, JsonObject core, JsonObject glacite, Ui treeView) {
        int sideWidth = Math.max(150, width - treeView.width() - 10);
        int half = Math.max(110, (sideWidth - 10) / 2);

        Ui side = sideWidth >= 230
                ? Ui.row(10, Ui.V.TOP,
                        Ui.col(6, Kit.section("Glacite Tunnels", glacite(glacite, half)),
                                Kit.section("Powder", powder(half, tree, core))),
                        Kit.section("Crystals", crystals(core, half)))
                : Ui.col(6, Kit.section("Glacite Tunnels", glacite(glacite, sideWidth)),
                        Kit.section("Crystals", crystals(core, sideWidth)),
                        Kit.section("Powder", powder(sideWidth, tree, core)));

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
                                new Ui.Label("Heart of the Mountain", NeuStyle.TEXT, Ui.SUBTITLE),
                                new Ui.Label("Level " + tree.level + "/" + tree.maxLevel,
                                        tree.isMaxed() ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT_AQUA)),
                        Ui.row(8, Ui.V.CENTER,
                                new Ui.Label("Loadout " + tree.slot + " - " + tree.tokensSpent + " tokens",
                                        NeuStyle.TEXT_GRAY, Ui.SMALL),
                                new Ui.Label(right, NeuStyle.TEXT_GRAY))),
                Kit.bar(width, tree.progress()));
    }

    /** Hypixel stores the balance, so the lifetime total is balance plus what this loadout spent. */
    private Ui powder(int width, SkillTreeInfo tree, JsonObject core) {
        List<Kit.TileData> tiles = new ArrayList<>();
        tiles.add(powderTile("Mithril", "mithril", MITHRIL_COLOR, tree, core));
        tiles.add(powderTile("Gemstone", "gemstone", GEMSTONE_COLOR, tree, core));
        tiles.add(powderTile("Glacite", "glacite", GLACITE_COLOR, tree, core));
        return Kit.tileRow(width, tiles);
    }

    private Kit.TileData powderTile(String label, String key, int color, SkillTreeInfo tree, JsonObject core) {
        long current = tree.total(core, key);
        long spent = tree.spent(core, key);
        return new Kit.TileData(label, Kit.compact(current) + " / " + Kit.compact(current + spent), color);
    }

    private Ui glacite(JsonObject glacite, int width) {
        if (glacite == null) return new Ui.Label("No glacite data", NeuStyle.TEXT_GRAY);

        List<Ui> rows = new ArrayList<>();
        rows.add(row(width, "Mineshafts", String.valueOf(asInt(get(glacite, "mineshafts_entered"))), NeuStyle.TEXT));

        JsonObject corpses = object(glacite, "corpses_looted");
        if (corpses != null) {
            int total = 0;
            for (Map.Entry<String, JsonElement> entry : corpses.entrySet()) total += asInt(entry.getValue());
            List<String> tooltip = new ArrayList<>();
            tooltip.add("§aCorpses looted");
            for (Map.Entry<String, JsonElement> entry : corpses.entrySet()) {
                tooltip.add("§7" + Kit.prettify(entry.getKey()) + ": §f" + asInt(entry.getValue()));
            }
            rows.add(new Ui.Tip(row(width, "Corpses", String.valueOf(total), NeuStyle.TEXT), tooltip));
        }

        int fossils = glacite.has("fossils_donated") && glacite.get("fossils_donated").isJsonArray()
                ? glacite.getAsJsonArray("fossils_donated").size() : 0;
        rows.add(row(width, "Fossils", fossils + " / 11", fossils >= 11 ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT));
        rows.add(row(width, "Fossil dust", Kit.compact(asLong(get(glacite, "fossil_dust"))), NeuStyle.TEXT));
        return Ui.col(3, rows);
    }

    private Ui crystals(JsonObject core, int width) {
        JsonObject found = object(core, "crystals");
        if (found == null) return new Ui.Label("No crystal data", NeuStyle.TEXT_GRAY);

        List<Ui> icons = new ArrayList<>();
        for (String crystal : CRYSTALS) {
            JsonObject data = object(found, crystal);
            String state = data != null && data.has("state") && data.get("state").isJsonPrimitive()
                    ? data.get("state").getAsString() : "NOT_FOUND";
            boolean have = state.equals("FOUND") || state.equals("PLACED");
            String name = Kit.prettify(crystal);

            List<String> tooltip = List.of(
                    "§a" + name,
                    "§7State: §f" + Kit.prettify(state),
                    "§7Found: §f" + asInt(get(data, "total_found")),
                    "§7Placed: §f" + asInt(get(data, "total_placed")));

            icons.add(new Ui.Tip(new Ui.Icon(Kit.icon(have ? "amethyst_shard" : "gray_dye"), 16, false, null), tooltip));
        }

        int perRow = Math.max(1, width / 18);
        List<Ui> rows = new ArrayList<>();
        for (int i = 0; i < icons.size(); i += perRow) {
            rows.add(Ui.row(2, Ui.V.TOP, new ArrayList<>(icons.subList(i, Math.min(i + perRow, icons.size())))));
        }
        return Ui.col(2, rows);
    }

    private Ui row(int width, String label, String value, int color) {
        return new Ui.SpaceBetween(width,
                new Ui.Label(label, NeuStyle.TEXT_GRAY),
                new Ui.Label(value, color));
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject()
                ? parent.getAsJsonObject(key) : null;
    }

    private static JsonElement get(JsonObject parent, String key) {
        return parent != null && parent.has(key) ? parent.get(key) : null;
    }
}
