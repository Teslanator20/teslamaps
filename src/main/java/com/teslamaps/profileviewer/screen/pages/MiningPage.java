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

    private static final List<String> NUCLEUS_CRYSTALS = List.of(
            "jade_crystal", "amethyst_crystal", "topaz_crystal", "sapphire_crystal", "amber_crystal");

    // api key, item id, name - the api stores the short key
    private static final String[][] FOSSILS = {
            {"HELIX", "HELIX", "Helix Fossil"},
            {"FOOTPRINT", "FOOTPRINT_FOSSIL", "Footprint Fossil"},
            {"CLAW", "CLAW_FOSSIL", "Claw Fossil"},
            {"TUSK", "TUSK_FOSSIL", "Tusk Fossil"},
            {"SPINE", "SPINE_FOSSIL", "Spine Fossil"},
            {"WEBBED", "WEBBED_FOSSIL", "Webbed Fossil"},
            {"CLUBBED", "CLUBBED_FOSSIL", "Clubbed Fossil"},
            {"UGLY", "UGLY_FOSSIL", "Ugly Fossil"},
    };

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
        JsonObject member = profile.getMemberData();
        drawTree(ctx, cachedTree(member, w -> build(member, w)), width, x, y, mouseX, mouseY);
    }

    private Ui build(JsonObject member, int width) {
        SkillTreeInfo tree = new SkillTreeInfo(member, SkillTreeInfo.Type.MINING);
        JsonObject core = object(member, "mining_core");
        JsonObject glacite = object(member, "glacite_player_data");

        List<PerkRegistry.Perk> perks = PerkRegistry.hotm();
        Ui treeView = PerkTreeView.component(perks, tree.nodes, tree.level, PerkTreeView.MINING, tree.disabled);

        // only the perk tree shrinks when the page is too tall; scaling the whole page would
        // pull the text away from both edges and leave the window half empty.
        // the factor comes from the taller of hotm/hotf, so both trees render at the same
        // node size and the page doesn't jump when swapping between the two hearts
        float factor = sharedTreeFactor(header(width, tree).height() + 6);
        Ui page = compose(member, width, tree, core, glacite,
                factor < 1f ? new Ui.Scaled(treeView, factor) : treeView);
        return page;
    }

    // everything next to the tree, in two columns when there is room for them
    private Ui compose(JsonObject member, int width, SkillTreeInfo tree, JsonObject core, JsonObject glacite, Ui treeView) {
        int sideWidth = Math.max(150, width - treeView.width() - 10);
        int half = Math.max(110, (sideWidth - 10) / 2);

        Ui gear = com.teslamaps.profileviewer.PvExtras.gear()
                ? Kit.section("Gear", com.teslamaps.profileviewer.data.GearScan.grid(
                        com.teslamaps.profileviewer.data.GearScan.mining(member), 18))
                : null;
        Ui crystals = Kit.section("Crystals", crystals(core, sideWidth >= 230 ? half : sideWidth));
        Ui side = sideWidth >= 230
                ? Ui.row(10, Ui.V.TOP,
                        Ui.col(6, Kit.section("Glacite Tunnels", glacite(glacite, half)),
                                Kit.section("Powder", powder(half, tree, core))),
                        gear == null ? crystals : Ui.col(6, crystals, gear))
                : gear == null
                        ? Ui.col(6, Kit.section("Glacite Tunnels", glacite(glacite, sideWidth)), crystals,
                                Kit.section("Powder", powder(sideWidth, tree, core)))
                        : Ui.col(6, Kit.section("Glacite Tunnels", glacite(glacite, sideWidth)), crystals,
                                Kit.section("Powder", powder(sideWidth, tree, core)), gear);

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

        java.util.Set<String> donated = new java.util.HashSet<>();
        if (glacite.has("fossils_donated") && glacite.get("fossils_donated").isJsonArray()) {
            for (JsonElement element : glacite.getAsJsonArray("fossils_donated")) {
                if (element.isJsonPrimitive()) donated.add(element.getAsString().toUpperCase(java.util.Locale.ROOT));
            }
        }
        int fossils = donated.size();
        int max = Math.max(FOSSILS.length, fossils);
        rows.add(row(width, "Fossils", fossils + " / " + max, fossils >= max ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT));
        rows.add(fossilHeads(donated, width));
        rows.add(row(width, "Fossil dust", Kit.compact(asLong(get(glacite, "fossil_dust"))), NeuStyle.TEXT));
        long commissions = profiles == null ? -1 : profiles.getCommissions();
        if (commissions >= 0) rows.add(row(width, "Commissions", Kit.dots(commissions), NeuStyle.TEXT));
        return Ui.col(3, rows);
    }

    /** Every fossil as its head, the ones not donated yet as grey dye. */
    private Ui fossilHeads(java.util.Set<String> donated, int width) {
        List<Ui> icons = new ArrayList<>();
        for (String[] fossil : FOSSILS) {
            boolean have = donated.contains(fossil[0]) || donated.contains(fossil[1]);
            ItemStack icon = have ? com.teslamaps.utils.SkyblockItems.stackNow(fossil[1]) : ItemStack.EMPTY;
            if (icon.isEmpty()) icon = Kit.icon(have ? "bone" : "gray_dye");
            icons.add(new Ui.Tip(new Ui.Icon(icon, 12, false, null), List.of(
                    "§f" + fossil[2], have ? "§aDONATED" : "§cNot donated")));
        }
        int perRow = Math.max(1, (width + 2) / 14);
        List<Ui> rows = new ArrayList<>();
        for (int i = 0; i < icons.size(); i += perRow) {
            rows.add(Ui.row(2, Ui.V.TOP, new ArrayList<>(icons.subList(i, Math.min(i + perRow, icons.size())))));
        }
        return Ui.col(2, rows);
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

            // the real crystal item (paper + hypixel's item model, like the ingame menu);
            // greyed to a dye only while it was never found
            net.minecraft.world.item.ItemStack icon = have
                    ? com.teslamaps.utils.SkyblockItems.stackNow(crystal.toUpperCase(java.util.Locale.ROOT))
                    : net.minecraft.world.item.ItemStack.EMPTY;
            if (icon.isEmpty()) icon = Kit.icon(have ? "amethyst_shard" : "gray_dye");
            icons.add(new Ui.Tip(new Ui.Icon(icon, 16, false, null), tooltip));
        }

        int perRow = Math.max(1, width / 18);
        List<Ui> rows = new ArrayList<>();
        for (int i = 0; i < icons.size(); i += perRow) {
            rows.add(Ui.row(2, Ui.V.TOP, new ArrayList<>(icons.subList(i, Math.min(i + perRow, icons.size())))));
        }

        // a nucleus run places all five, so the smallest placement count is the run count
        long nucleus = Long.MAX_VALUE;
        for (String crystal : NUCLEUS_CRYSTALS) nucleus = Math.min(nucleus, asLong(get(object(found, crystal), "total_placed")));
        rows.add(row(width, "Nucleus Runs", Kit.dots(nucleus), NeuStyle.TEXT));
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
