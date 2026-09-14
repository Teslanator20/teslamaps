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

/** Crimson Isle: faction standing, kuudra runs and the dojo tests. */
public class CrimsonIslePage extends ProfileViewerPage {
    private static final int MAGE_COLOR = 0xFFAA00AA;
    private static final int BARBARIAN_COLOR = 0xFFFF5500;

    private static final List<String> KUUDRA_KEYS = List.of("none", "hot", "burning", "fiery", "infernal");
    private static final List<String> KUUDRA_NAMES = List.of("Basic", "Hot", "Burning", "Fiery", "Infernal");
    private static final List<Integer> KUUDRA_COLORS = List.of(
            NeuStyle.TEXT, NeuStyle.TEXT_GOLD, 0xFFFF8800, NeuStyle.TEXT_RED, 0xFFAA0000);

    private static final List<String> DOJO_KEYS = List.of(
            "dojo_points_mob_kb", "dojo_points_wall_jump", "dojo_points_archer", "dojo_points_sword_swap",
            "dojo_points_snake", "dojo_points_lock_head", "dojo_points_fireball");
    private static final List<String> DOJO_NAMES = List.of(
            "Force", "Stamina", "Mastery", "Discipline", "Swiftness", "Control", "Tenacity");

    @Override
    public String getTabName() {
        return "Crimson";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.MAGMA_CREAM);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        JsonObject nether = object(profile.getMemberData(), "nether_island_player_data");
        if (nether == null) {
            ctx.text(net.minecraft.client.Minecraft.getInstance().font, "No Crimson Isle data",
                    x, y + 6, NeuStyle.TEXT_GRAY);
            return;
        }

        int available = NeuStyle.PANEL_HEIGHT - y - 6;
        drawTree(ctx, w -> fitted(nether, w, available), width, x, y, mouseX, mouseY);
    }

    private Ui fitted(JsonObject nether, int width, int available) {
        Ui tree = build(nether, width, 2);
        for (int columns = 3; columns <= 4 && tree.height() > available; columns++) {
            tree = build(nether, width, columns);
        }
        return tree;
    }

    private Ui build(JsonObject nether, int width, int columns) {
        return Ui.col(10,
                header(nether, width),
                Kit.section("Kuudra Completions", kuudra(nether, width)),
                Kit.section("Dojo", dojo(nether, width, columns)));
    }

    private Ui header(JsonObject nether, int width) {
        String faction = nether.has("selected_faction") && nether.get("selected_faction").isJsonPrimitive()
                ? nether.get("selected_faction").getAsString() : "none";
        boolean mage = faction.equalsIgnoreCase("mages");
        int color = mage ? MAGE_COLOR : BARBARIAN_COLOR;

        int mages = asInt(get(nether, "mages_reputation"));
        int barbarians = asInt(get(nether, "barbarians_reputation"));
        int reputation = mage ? mages : barbarians;

        return Ui.col(5,
                new Ui.SpaceBetween(width,
                        Ui.row(6, Ui.V.CENTER,
                                new Ui.Label("Crimson Isle", NeuStyle.TEXT, Ui.SUBTITLE),
                                new Ui.Label(Kit.prettify(faction), color)),
                        new Ui.Label(Kit.compact(reputation) + " reputation", NeuStyle.TEXT_GRAY)),
                Kit.bar(width, reputation / 12000.0, color),
                Kit.tileRow(width, List.of(
                        new Kit.TileData("Mages Rep", Kit.compact(mages), MAGE_COLOR),
                        new Kit.TileData("Barbarian Rep", Kit.compact(barbarians), BARBARIAN_COLOR),
                        new Kit.TileData("Belt", belt(totalDojoPoints(nether)), NeuStyle.TEXT_GOLD),
                        new Kit.TileData("Dojo Points", String.valueOf(totalDojoPoints(nether)), NeuStyle.TEXT_AQUA))));
    }

    private Ui kuudra(JsonObject nether, int width) {
        JsonObject tiers = object(nether, "kuudra_completed_tiers");
        List<Kit.TileData> tiles = new ArrayList<>();
        int total = 0;

        for (int i = 0; i < KUUDRA_KEYS.size(); i++) {
            int runs = asInt(get(tiers, KUUDRA_KEYS.get(i)));
            total += runs;
            tiles.add(new Kit.TileData(KUUDRA_NAMES.get(i), String.valueOf(runs),
                    runs > 0 ? KUUDRA_COLORS.get(i) : NeuStyle.TEXT_GRAY));
        }
        tiles.add(new Kit.TileData("Total", String.valueOf(total), NeuStyle.TEXT));

        return Kit.tileRow(width, tiles);
    }

    private Ui dojo(JsonObject nether, int width, int columns) {
        JsonObject dojo = object(nether, "dojo");
        if (dojo == null) return new Ui.Label("No dojo data", NeuStyle.TEXT_GRAY);

        columns = Math.max(1, Math.min(columns, width / 120));
        int cellWidth = Kit.cellWidth(width, columns, 12);
        List<Ui> rows = new ArrayList<>();

        for (int i = 0; i < DOJO_KEYS.size(); i++) {
            int points = asInt(get(dojo, DOJO_KEYS.get(i)));
            String rank = rank(points);
            int color = rankColor(rank);

            rows.add(Ui.col(2,
                    new Ui.SpaceBetween(cellWidth,
                            new Ui.Label(DOJO_NAMES.get(i), NeuStyle.TEXT),
                            Ui.row(6, Ui.V.CENTER,
                                    new Ui.Label(String.valueOf(points), NeuStyle.TEXT_GRAY),
                                    new Ui.Label(rank, color))),
                    Kit.bar(cellWidth, points / 1000.0, color)));
        }

        return Kit.grid(rows, columns, 12, 6);
    }

    private static int totalDojoPoints(JsonObject nether) {
        JsonObject dojo = object(nether, "dojo");
        if (dojo == null) return 0;
        int total = 0;
        for (String key : DOJO_KEYS) total += asInt(get(dojo, key));
        return total;
    }

    private static String rank(int points) {
        if (points >= 1000) return "S";
        if (points >= 800) return "A";
        if (points >= 600) return "B";
        if (points >= 400) return "C";
        if (points >= 200) return "D";
        return points > 0 ? "F" : "-";
    }

    private static int rankColor(String rank) {
        return switch (rank) {
            case "S" -> NeuStyle.TEXT_GOLD;
            case "A" -> NeuStyle.TEXT_GREEN;
            case "B" -> NeuStyle.TEXT_AQUA;
            case "C" -> NeuStyle.TEXT;
            default -> NeuStyle.TEXT_GRAY;
        };
    }

    private static String belt(int points) {
        if (points >= 7000) return "Black";
        if (points >= 6000) return "Brown";
        if (points >= 5000) return "Blue";
        if (points >= 4000) return "Green";
        if (points >= 3000) return "Yellow";
        if (points >= 2000) return "White";
        return "None";
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject()
                ? parent.getAsJsonObject(key) : null;
    }

    private static com.google.gson.JsonElement get(JsonObject parent, String key) {
        return parent != null && parent.has(key) ? parent.get(key) : null;
    }
}
