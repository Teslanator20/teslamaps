package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.profileviewer.data.InventoryDecoder;
import com.teslamaps.profileviewer.data.LegacyText;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.ItemGrid;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import com.teslamaps.profileviewer.ui.Kit;
import com.teslamaps.profileviewer.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The Rift, laid out like schrumbo's page: motes, souls, timecharms, porthals and the rift gear. */
public class RiftPage extends ProfileViewerPage {

    private static final int MOTE = 0xFFB070FF;
    private static final int ENIGMA_MAX = 52;

    private record Timecharm(String id, String name, String icon) {}

    private static final List<Timecharm> TIMECHARMS = List.of(
            new Timecharm("wyldly_supreme", "Supreme Timecharm", "spruce_leaves"),
            new Timecharm("chicken_n_egg", "Chicken N Egg Timecharm", "soul_sand"),
            new Timecharm("mirrored", "Mirrorverse Timecharm", "glass"),
            new Timecharm("citizen", "SkyBlock Citizen Timecharm", "emerald"),
            new Timecharm("lazy_living", "Living Timecharm", "lapis_ore"),
            new Timecharm("slime", "Globulate Timecharm", "slime_block"),
            new Timecharm("vampiric", "Vampiric Timecharm", "redstone_block"),
            new Timecharm("mountain", "Celestial Timecharm", "snow_block"));

    private List<InventoryDecoder.Slot> armor = List.of();
    private List<InventoryDecoder.Slot> equipment = List.of();
    private List<InventoryDecoder.Slot> items = List.of();

    @Override
    public void init(com.teslamaps.profileviewer.screen.ProfileViewerScreen parent,
                     com.teslamaps.profileviewer.data.SkyblockProfiles profiles) {
        super.init(parent, profiles);
        // decoding per frame rebuilt every slot object each frame and flooded the item cache
        SkyblockProfile profile = getProfile();
        JsonObject inventory = profile == null ? null : object(object(profile.getMemberData(), "rift"), "inventory");
        armor = InventoryDecoder.decode(inventory, "inv_armor");
        equipment = InventoryDecoder.decode(inventory, "equipment_contents");
        items = InventoryDecoder.decode(inventory, "inv_contents");
    }

    @Override
    public String getTabName() {
        return "Rift";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.ENDER_EYE);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        SkyblockProfile profile = getProfile();
        if (profile == null) return;
        drawTree(ctx, w -> build(profile.getMemberData(), w), width, x, y, mouseX, mouseY);
    }

    private Ui build(JsonObject member, int width) {
        JsonObject rift = object(member, "rift");
        JsonObject stats = object(object(member, "player_stats"), "rift");
        JsonObject currencies = object(member, "currencies");

        if (rift == null) return new Ui.Label("No rift data", NeuStyle.TEXT_GRAY);

        long motes = asLong(get(currencies, "motes_purse"));
        long lifetime = asLong(get(stats, "lifetime_motes_earned"));
        long visits = asLong(get(stats, "visits"));

        Ui gear = Kit.section("Rift Gear", gear(rift));
        int left = Math.max(150, width - gear.width() - 12);

        return Ui.col(9,
                Kit.tileRow(width, List.of(
                        new Kit.TileData("Motes Purse", Kit.compact(motes), MOTE),
                        new Kit.TileData("Lifetime Motes", Kit.compact(lifetime), NeuStyle.TEXT),
                        new Kit.TileData("Rift Visits", Kit.compact(visits), NeuStyle.TEXT_GREEN),
                        new Kit.TileData("Burgers", asInt(get(object(rift, "castle"), "grubber_stacks")) + "/5", NeuStyle.TEXT))),
                Ui.row(12, Ui.V.TOP,
                        Ui.col(8,
                                Kit.section("Progress", progress(rift, left)),
                                Kit.section("Vampire Slayer", vampire(member, left)),
                                timecharmSection(rift),
                                porthalSection(rift, left)),
                        gear));
    }

    /** Collection style rows with bars: souls, eyes, cats, montezuma. */
    private Ui progress(JsonObject rift, int width) {
        int souls = size(object(rift, "enigma"), "found_souls");
        int eyes = size(object(rift, "wither_cage"), "killed_eyes");
        int cats = size(object(rift, "dead_cats"), "found_cats");
        boolean montezuma = object(object(rift, "dead_cats"), "montezuma") != null;

        return Ui.col(4,
                progressRow(width, "Enigma Souls", souls, ENIGMA_MAX),
                progressRow(width, "Wither Eyes", eyes, 7),
                progressRow(width, "Dead Cats", cats, 9),
                new Ui.SpaceBetween(width,
                        new Ui.Label("Montezuma", NeuStyle.TEXT_GRAY),
                        new Ui.Label(montezuma ? "Owned" : "Not found",
                                montezuma ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT_DARK)));
    }

    private Ui progressRow(int width, String label, int have, int max) {
        boolean done = have >= max;
        int color = done ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT_AQUA;
        return Ui.col(2,
                new Ui.SpaceBetween(width,
                        new Ui.Label(label, NeuStyle.TEXT_GRAY),
                        new Ui.Label(have + "/" + max, color)),
                Kit.bar(width, max > 0 ? have / (double) max : 0, color));
    }

    // the rift's own slayer, five levels with their own xp brackets
    private static final long[] VAMPIRE_XP = {20, 75, 240, 840, 2400};

    /** Same look as the slayers on the extra page: level, xp bar, tiers in the tooltip. */
    private Ui vampire(JsonObject member, int width) {
        JsonObject boss = object(object(object(member, "slayer"), "slayer_bosses"), "vampire");
        if (boss == null) return new Ui.Label("Never fought a vampire", NeuStyle.TEXT_GRAY);

        long xp = asLong(get(boss, "xp"));
        int level = 0;
        for (long threshold : VAMPIRE_XP) if (xp >= threshold) level++;
        boolean maxed = level >= VAMPIRE_XP.length;
        double progress = maxed ? 1
                : (xp - (level > 0 ? VAMPIRE_XP[level - 1] : 0))
                        / (double) (VAMPIRE_XP[level] - (level > 0 ? VAMPIRE_XP[level - 1] : 0));

        List<String> tooltip = new ArrayList<>();
        tooltip.add("§cRiftstalker Slayer");
        tooltip.add("§7Level §f" + level + "§7/§f5");
        tooltip.add("§7Total XP: §f" + String.format("%,d", xp));
        long total = 0;
        for (int tier = 0; tier < 5; tier++) {
            int kills = asInt(get(boss, "boss_kills_tier_" + tier));
            int attempts = asInt(get(boss, "boss_attempts_tier_" + tier));
            if (kills == 0 && attempts == 0) continue;
            total += kills;
            tooltip.add("§7Tier " + (tier + 1) + ": §f" + String.format("%,d", kills) + " kills"
                    + (attempts > kills ? " §8(" + String.format("%,d", attempts) + " started)" : ""));
        }
        tooltip.add("§7Bosses killed: §f" + String.format("%,d", total));

        int color = maxed ? NeuStyle.TEXT_GOLD : 0xFFFF5555;
        return new Ui.Tip(Ui.col(2,
                new Ui.SpaceBetween(width,
                        Ui.row(5, Ui.V.CENTER,
                                new Ui.Label("Riftstalker", 0xFFFF5555),
                                new Ui.Label(String.valueOf(level), maxed ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT)),
                        new Ui.Label(Kit.compact(xp) + " XP", NeuStyle.TEXT_GRAY)),
                Kit.bar(width, progress, color)), tooltip);
    }

    private Ui timecharmSection(JsonObject rift) {
        Map<String, Long> secured = new HashMap<>();
        JsonArray trophies = array(object(rift, "gallery"), "secured_trophies");
        if (trophies != null) {
            for (JsonElement element : trophies) {
                if (!element.isJsonObject()) continue;
                JsonObject trophy = element.getAsJsonObject();
                if (!trophy.has("type")) continue;
                secured.put(trophy.get("type").getAsString(), asLong(get(trophy, "timestamp")));
            }
        }

        List<Ui> cells = new ArrayList<>();
        for (Timecharm charm : TIMECHARMS) {
            Long timestamp = secured.get(charm.id());
            // secured charms sit in a lit slot, missing ones stay dark
            Ui icon = new Ui.Frame(20, 20,
                    new Ui.Icon(Kit.icon(timestamp != null ? charm.icon() : "gray_dye"), 16, false, null),
                    NeuStyle::slimBox, Ui.H.CENTER, Ui.V.CENTER);
            List<String> tooltip = timestamp != null
                    ? List.of("§a" + charm.name(), "§7Secured: §f" + date(timestamp))
                    : List.of("§7" + charm.name(), "§cNot secured");
            cells.add(new Ui.Tip(icon, tooltip));
        }
        return Kit.section("Timecharms  " + secured.size() + "/" + TIMECHARMS.size(),
                Ui.row(3, Ui.V.TOP, cells));
    }

    private Ui porthalSection(JsonObject rift, int width) {
        JsonArray boundaries = array(rift, "lifetime_purchased_boundaries");
        int count = boundaries == null ? 0 : boundaries.size();
        if (count == 0) {
            return Kit.section("Porthals", new Ui.Label("None unlocked", NeuStyle.TEXT_DARK));
        }

        // wrap into as many lines as needed instead of clipping the whole list to one
        var font = net.minecraft.client.Minecraft.getInstance().font;
        List<Ui> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (JsonElement element : boundaries) {
            String name = Kit.prettify(element.getAsString());
            String appended = line.isEmpty() ? name : line + " §8- §a" + name;
            if (!line.isEmpty() && font.width(com.teslamaps.utils.StringUtil.strip(appended)) > width) {
                lines.add(label(line.toString()));
                line = new StringBuilder(name);
            } else {
                line = new StringBuilder(appended);
            }
        }
        if (!line.isEmpty()) lines.add(label(line.toString()));
        return Kit.section("Porthals  " + count, Ui.col(2, lines));
    }

    private Ui label(String legacy) {
        net.minecraft.network.chat.Component parsed = LegacyText.parse("§a" + legacy);
        var font = net.minecraft.client.Minecraft.getInstance().font;
        return new Ui() {
            @Override
            public int width() {
                return font.width(parsed);
            }

            @Override
            public int height() {
                return font.lineHeight;
            }

            @Override
            public void render(net.minecraft.client.gui.GuiGraphicsExtractor ctx, int x, int y,
                               int mouseX, int mouseY) {
                ctx.text(font, parsed, x, y, -1);
            }
        };
    }

    private Ui gear(JsonObject rift) {
        return Ui.row(12, Ui.V.TOP,
                labeled("Armor", column(reversed(armor), 4)),
                labeled("Equipment", column(reversed(equipment), 4)),
                labeled("Inventory", grid(items)));
    }

    private Ui labeled(String title, Ui content) {
        return Ui.col(2, new Ui.Label(title, NeuStyle.TEXT_GRAY, Ui.SMALL), content);
    }

    private List<InventoryDecoder.Slot> reversed(List<InventoryDecoder.Slot> slots) {
        List<InventoryDecoder.Slot> out = new ArrayList<>(slots);
        java.util.Collections.reverse(out);
        return out;
    }

    private Ui column(List<InventoryDecoder.Slot> slots, int count) {
        List<Ui> cells = new ArrayList<>();
        for (int i = 0; i < count; i++) cells.add(slot(i < slots.size() ? slots.get(i) : null));
        return Ui.col(2, cells);
    }

    private Ui grid(List<InventoryDecoder.Slot> slots) {
        boolean any = false;
        for (InventoryDecoder.Slot slot : slots) any |= slot != null && !slot.isEmpty();
        if (!any) return new Ui.Label("Empty", NeuStyle.TEXT_GRAY);

        List<Ui> rows = new ArrayList<>();
        for (int i = 0; i < slots.size(); i += 9) {
            List<Ui> cells = new ArrayList<>();
            for (int c = i; c < Math.min(i + 9, slots.size()); c++) cells.add(slot(slots.get(c)));
            rows.add(Ui.row(2, Ui.V.TOP, cells));
        }
        return Ui.col(2, rows);
    }

    private Ui slot(InventoryDecoder.Slot slot) {
        Ui inner = slot == null || slot.isEmpty()
                ? new Ui.Spacer(16, 16)
                : new Ui.Icon(ItemGrid.stackFor(slot), 16, true, slot.count > 1 ? String.valueOf(slot.count) : null);
        return new Ui.Frame(20, 20, inner, NeuStyle::slimBox, Ui.H.CENTER, Ui.V.CENTER);
    }

    private static String date(long millis) {
        if (millis <= 0) return "-";
        var day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();
        return day.getDayOfMonth() + " "
                + day.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + day.getYear();
    }

    private static int size(JsonObject parent, String key) {
        JsonArray array = array(parent, key);
        return array == null ? 0 : array.size();
    }

    private static JsonArray array(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonArray()
                ? parent.getAsJsonArray(key) : null;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject()
                ? parent.getAsJsonObject(key) : null;
    }

    private static JsonElement get(JsonObject parent, String key) {
        return parent != null && parent.has(key) ? parent.get(key) : null;
    }
}
