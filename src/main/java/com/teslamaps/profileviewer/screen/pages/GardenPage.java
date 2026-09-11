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
        if (garden == null || garden.has("error")) {
            ctx.text(net.minecraft.client.Minecraft.getInstance().font, "No garden data",
                    x, y + 6, NeuStyle.TEXT_GRAY);
            return;
        }

        drawTree(ctx, build(width), x, y, mouseX, mouseY);
    }

    private Ui build(int width) {
        List<Ui> parts = new ArrayList<>();
        parts.add(header(width));
        parts.add(Kit.section("Crop Milestones", crops(width)));
        parts.add(Kit.section("Composter", composter(width)));
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

    private Ui crops(int width) {
        JsonObject collected = object(garden, "resources_collected");
        JsonObject upgrades = object(garden, "crop_upgrade_levels");
        int columns = 2;
        int cellWidth = Kit.cellWidth(width, columns, 12);

        List<Ui> rows = new ArrayList<>();
        for (GardenRegistry.Crop crop : GardenRegistry.CROPS) {
            long amount = asLong(get(collected, crop.key()));
            GardenRegistry.Milestone milestone = GardenRegistry.milestone(crop.key(), amount);
            int color = milestone.maxed() ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT_AQUA;
            int bodyWidth = cellWidth - 16 - 6;

            Ui body = Ui.col(2,
                    new Ui.SpaceBetween(bodyWidth,
                            new Ui.Label(crop.display(), NeuStyle.TEXT),
                            Ui.row(6, Ui.V.CENTER,
                                    new Ui.Label("Tier " + milestone.tier(), color),
                                    new Ui.Label(Kit.compact(amount), NeuStyle.TEXT_GRAY))),
                    Kit.bar(bodyWidth, milestone.progress(), color));

            List<String> tooltip = List.of(
                    "§a" + crop.display(),
                    "§7Milestone §f" + milestone.tier(),
                    "§7Collected §f" + String.format("%,d", amount),
                    "§7Crop upgrade §f" + asInt(get(upgrades, crop.key())) + "§7/§89");

            rows.add(new Ui.Tip(Ui.row(6, Ui.V.CENTER,
                    new Ui.Icon(Kit.icon(crop.icon()), 16, false, null), body), tooltip));
        }
        return Kit.grid(rows, columns, 12, 6);
    }

    private Ui composter(int width) {
        JsonObject composter = object(garden, "composter_data");
        JsonObject upgrades = object(composter, "upgrades");

        List<Ui> rows = new ArrayList<>();
        int cellWidth = Kit.cellWidth(width, 2, 12);
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
                Kit.grid(rows, 2, 12, 6));
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject()
                ? parent.getAsJsonObject(key) : null;
    }

    private static com.google.gson.JsonElement get(JsonObject parent, String key) {
        return parent != null && parent.has(key) ? parent.get(key) : null;
    }
}
