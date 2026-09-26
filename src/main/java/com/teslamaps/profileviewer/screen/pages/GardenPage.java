package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonObject;
import com.teslamaps.api.HypixelApi;
import com.teslamaps.profileviewer.data.GardenRegistry;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.data.SkyblockProfiles;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import com.teslamaps.profileviewer.screen.ProfileViewerScreen;
import com.teslamaps.profileviewer.ui.Kit;
import com.teslamaps.profileviewer.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Garden: level, plots, crop milestones and the composter. Laid out like schrumbo's farming page. */
public class GardenPage extends ProfileViewerPage {

    private static final List<String> COMPOSTER_KEYS = List.of(
            "SPEED", "MULTI_DROP", "FUEL_CAP", "ORGANIC_MATTER_CAP", "COST_REDUCTION");
    private static final List<String> COMPOSTER_NAMES = List.of(
            "Speed", "Multi Drop", "Fuel Cap", "Organic Matter Cap", "Cost Reduction");
    private static final int COMPOSTER_MAX = 25;

    private JsonObject garden;
    private boolean loading = true;

    @Override
    public String getTabName() {
        return "Garden";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.GOLDEN_HOE);
    }

    @Override
    public void init(ProfileViewerScreen parent, SkyblockProfiles profiles) {
        super.init(parent, profiles);
        garden = null;
        SkyblockProfile profile = getProfile();
        jacobs = profile == null || !extra(true) ? null : jacobs(profile.getMemberData());
        load();
    }

    @Override
    public void onSelected() {
        if (garden == null && !loading) load();
    }

    private void load() {
        SkyblockProfile profile = getProfile();
        if (profile == null) return;
        loading = true;
        HypixelApi.getGarden(profile.getProfileId()).thenAccept(data -> {
            garden = data != null && data.has("garden") && data.get("garden").isJsonObject()
                    ? data.getAsJsonObject("garden") : data;
            loading = false;
        });
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        if (loading) {
            ctx.text(net.minecraft.client.Minecraft.getInstance().font, "Loading garden...",
                    x, y + 6, NeuStyle.TEXT_GRAY);
            return;
        }
        if ((garden == null || garden.has("error")) && jacobs != null) {
            drawTree(ctx, cachedTree(jacobs, w -> jacobsSection(w, 2)), width, x, y, mouseX, mouseY);
            return;
        }
        if (garden == null || garden.has("error")) {
            ctx.text(net.minecraft.client.Minecraft.getInstance().font, "No garden data",
                    x, y + 6, NeuStyle.TEXT_GRAY);
            return;
        }

        int available = NeuStyle.PANEL_HEIGHT - y - 6;
        drawTree(ctx, cachedTree(garden, w -> fitted(w, available)), width, x, y, mouseX, mouseY);
    }

    /** More columns instead of a taller page - the window is wide, not tall. */
    private Ui fitted(int width, int available) {
        Ui tree = build(width, 2);
        for (int columns = 3; columns <= 5 && tree.height() > available; columns++) {
            tree = build(width, columns);
        }
        return tree;
    }

    private Ui build(int width, int columns) {
        List<Ui> parts = new ArrayList<>();
        parts.add(header(width));
        parts.add(Kit.section("Crop Milestones", crops(width, columns)));
        parts.add(Kit.section("Composter", composter(width, columns)));
        SkyblockProfile profile = getProfile();
        if (com.teslamaps.profileviewer.PvExtras.greenhouse() && profile != null) {
            parts.add(Kit.section("Greenhouse", com.teslamaps.profileviewer.data.Greenhouse.build(profile.getMemberData(), width)));
        }
        if (jacobs != null) parts.add(jacobsSection(width, columns));
        return Ui.col(10, parts);
    }

    private Ui header(int width) {
        long experience = asLong(garden.get("garden_experience"));
        GardenRegistry.Level level = GardenRegistry.level(experience);
        boolean overflow = level.level() >= level.max();
        int color = overflow ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT_AQUA;

        int plots = garden.has("unlocked_plots_ids") && garden.get("unlocked_plots_ids").isJsonArray()
                ? garden.getAsJsonArray("unlocked_plots_ids").size() : 0;
        JsonObject upgrades = object(garden, "garden_upgrades");
        JsonObject commissions = object(garden, "commission_data");

        return Ui.col(5,
                new Ui.SpaceBetween(width,
                        Ui.row(5, Ui.V.CENTER,
                                new Ui.Icon(Kit.icon("oak_sapling"), 12, false, null),
                                new Ui.Label("Garden Level " + level.level(), color)),
                        new Ui.Label(plots + "/" + GardenRegistry.PLOTS_TOTAL + " plots", NeuStyle.TEXT_GRAY)),
                Kit.bar(width, level.progress()),
                Kit.tileRow(width, List.of(
                        new Kit.TileData("Growth Speed", String.valueOf(asInt(get(upgrades, "GROWTH_SPEED"))), NeuStyle.TEXT_GREEN),
                        new Kit.TileData("Yield", String.valueOf(asInt(get(upgrades, "YIELD"))), NeuStyle.TEXT_GREEN),
                        new Kit.TileData("Visitors Served", String.valueOf(asInt(get(commissions, "unique_npcs_served"))), NeuStyle.TEXT_AQUA),
                        new Kit.TileData("Visits", Kit.compact(asLong(get(commissions, "total_completed"))), NeuStyle.TEXT))));
    }

    private Ui crops(int width, int columns) {
        JsonObject collected = object(garden, "resources_collected");
        JsonObject upgrades = object(garden, "crop_upgrade_levels");
        // the icon says which crop it is, a half cut name says nothing. 96 keeps "Tier 10"
        // plus the amount inside the cell - at 78 the tier label was scissored away
        columns = Math.max(1, Math.min(columns, width / 96));
        int cellWidth = Kit.cellWidth(width, columns, 12);

        List<Ui> rows = new ArrayList<>();
        for (GardenRegistry.Crop crop : GardenRegistry.CROPS) {
            long amount = asLong(get(collected, crop.key()));
            GardenRegistry.Milestone milestone = GardenRegistry.milestone(crop.key(), amount);
            int color = milestone.maxed() ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT_AQUA;
            int bodyWidth = cellWidth - 16 - 6;

            // name and amount live in the tooltip, the cell shows the icon, the tier and the bar
            Ui body = Ui.col(2,
                    new Ui.SpaceBetween(bodyWidth,
                            new Ui.Label("Tier " + milestone.tier(), color),
                            new Ui.Label(Kit.compact(amount), NeuStyle.TEXT_GRAY, Ui.SMALL)),
                    Kit.bar(bodyWidth, milestone.progress(), color));

            List<String> tooltip = List.of(
                    "§a" + crop.display(),
                    "§7Milestone §f" + milestone.tier(),
                    "§7Collected §f" + com.teslamaps.profileviewer.ui.Kit.dots(amount),
                    "§7Crop upgrade §f" + asInt(get(upgrades, crop.key())) + "§7/§89");

            rows.add(new Ui.Tip(Ui.row(6, Ui.V.CENTER,
                    new Ui.Icon(Kit.icon(crop.icon()), 16, false, null), body), tooltip));
        }
        return Kit.grid(rows, columns, 12, 6);
    }

    private Ui composter(int width, int columns) {
        JsonObject composter = object(garden, "composter_data");
        JsonObject upgrades = object(composter, "upgrades");

        List<Ui> rows = new ArrayList<>();
        columns = Math.max(1, Math.min(columns, width / 105));
        int cellWidth = Kit.cellWidth(width, columns, 12);
        for (int i = 0; i < COMPOSTER_KEYS.size(); i++) {
            int level = asInt(get(upgrades, COMPOSTER_KEYS.get(i).toLowerCase()));
            if (level == 0) level = asInt(get(upgrades, COMPOSTER_KEYS.get(i)));
            boolean maxed = level >= COMPOSTER_MAX;
            rows.add(new Ui.SpaceBetween(cellWidth,
                    new Ui.Label(COMPOSTER_NAMES.get(i), NeuStyle.TEXT),
                    new Ui.Label(level + "/" + COMPOSTER_MAX, maxed ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT_AQUA)));
        }

        return Ui.col(8,
                Kit.tileRow(width, List.of(
                        new Kit.TileData("Organic Matter", Kit.compact(asLong(get(composter, "organic_matter"))), NeuStyle.TEXT_GREEN),
                        new Kit.TileData("Fuel", Kit.compact(asLong(get(composter, "fuel_units"))), NeuStyle.TEXT_GOLD),
                        new Kit.TileData("Compost", Kit.compact(asLong(get(composter, "compost_units"))), NeuStyle.TEXT_AQUA),
                        new Kit.TileData("Items", Kit.compact(asLong(get(composter, "compost_items"))), NeuStyle.TEXT))),
                Kit.grid(rows, columns, 12, 6));
    }

    // ---------------------------------------------------------------- jacob's contests

    private record Jacobs(int diamond, int platinum, int gold, int silver, int bronze, int contests,
                          int uniqueGold, int uniqueSilver, int uniqueBronze, int doubleDrops, int farmingCap,
                          java.util.Map<String, Long> pbs) {}

    private Jacobs jacobs;

    /** Diamond and platinum never land in medals_inv, they are counted from each contest's claimed medal. */
    private static Jacobs jacobs(JsonObject member) {
        JsonObject jc = object(member, "jacobs_contest");
        if (jc == null) return null;
        JsonObject medals = object(jc, "medals_inv");
        JsonObject unique = object(jc, "unique_brackets");
        JsonObject perks = object(jc, "perks");
        JsonObject contests = object(jc, "contests");

        java.util.Map<String, Integer> earned = new java.util.HashMap<>();
        java.util.Map<String, Long> pbs = new java.util.LinkedHashMap<>();
        if (contests != null) {
            for (var entry : contests.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject contest = entry.getValue().getAsJsonObject();
                int colon = entry.getKey().indexOf(':');
                String crop = colon < 0 ? entry.getKey() : entry.getKey().substring(colon + 1);
                pbs.merge(crop, asLong(contest.get("collected")), Math::max);
                if (contest.has("claimed_medal") && contest.get("claimed_medal").isJsonPrimitive()) {
                    earned.merge(contest.get("claimed_medal").getAsString(), 1, Integer::sum);
                }
            }
        }
        return new Jacobs(
                earned.getOrDefault("diamond", asInt(get(medals, "diamond"))),
                earned.getOrDefault("platinum", asInt(get(medals, "platinum"))),
                asInt(get(medals, "gold")), asInt(get(medals, "silver")), asInt(get(medals, "bronze")),
                contests == null ? 0 : contests.size(),
                size(unique, "gold"), size(unique, "silver"), size(unique, "bronze"),
                asInt(get(perks, "double_drops")), asInt(get(perks, "farming_level_cap")), pbs);
    }

    private static int size(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonArray() ? parent.getAsJsonArray(key).size() : 0;
    }

    private Ui jacobsSection(int width, int columns) {
        Jacobs j = jacobs;
        List<Ui> parts = new ArrayList<>();
        parts.add(Kit.tileRow(width, List.of(
                new Kit.TileData("Diamond", String.valueOf(j.diamond()), NeuStyle.TEXT_AQUA),
                new Kit.TileData("Platinum", String.valueOf(j.platinum()), NeuStyle.TEXT_TURQUOISE),
                new Kit.TileData("Gold", String.valueOf(j.gold()), NeuStyle.TEXT_GOLD),
                new Kit.TileData("Silver", String.valueOf(j.silver()), NeuStyle.TEXT),
                new Kit.TileData("Bronze", String.valueOf(j.bronze()), 0xFFCD7F32))));
        parts.add(Kit.tileRow(width, List.of(
                new Kit.TileData("Contests", String.valueOf(j.contests()), NeuStyle.TEXT),
                new Kit.TileData("Unique Brackets", "§6" + j.uniqueGold() + " §f" + j.uniqueSilver() + " §c" + j.uniqueBronze(), NeuStyle.TEXT),
                new Kit.TileData("Double Drops", j.doubleDrops() + "/15", j.doubleDrops() >= 15 ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT_GREEN),
                new Kit.TileData("Farming Cap", String.valueOf(50 + j.farmingCap()), j.farmingCap() >= 10 ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT_GREEN))));

        if (!j.pbs().isEmpty()) {
            columns = Math.max(1, Math.min(columns + 1, width / 80));
            int cellWidth = Kit.cellWidth(width, columns, 12);
            List<Ui> rows = new ArrayList<>();
            for (GardenRegistry.Crop crop : GardenRegistry.CROPS) {
                Long pb = j.pbs().get(crop.key());
                if (pb == null) continue;
                rows.add(new Ui.Tip(Ui.row(6, Ui.V.CENTER,
                        new Ui.Icon(Kit.icon(crop.icon()), 12, false, null),
                        new Ui.SpaceBetween(cellWidth - 18,
                                new Ui.Label(Kit.clip(crop.display(), cellWidth - 60), NeuStyle.TEXT_GRAY, Ui.SMALL),
                                new Ui.Label(Kit.compact(pb), NeuStyle.TEXT))),
                        List.of("§a" + crop.display(), "§7Contest PB §f" + Kit.dots(pb))));
            }
            if (!rows.isEmpty()) parts.add(Kit.grid(rows, columns, 12, 4));
        }
        return Kit.section("Jacob's Contests", Ui.col(6, parts));
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject()
                ? parent.getAsJsonObject(key) : null;
    }

    private static com.google.gson.JsonElement get(JsonObject parent, String key) {
        return parent != null && parent.has(key) ? parent.get(key) : null;
    }
}
