package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonObject;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import com.teslamaps.utils.SkyblockItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Trophy fishing as a head grid, one column per fish and one head per tier. */
public class TrophyFishPage extends ProfileViewerPage {
    private static final int BRONZE = 0xFFCD7F32;
    private static final int SILVER = 0xFFC0C0C0;
    private static final int GOLD = 0xFFFFD700;
    private static final int DIAMOND = 0xFF55FFFF;

    private record Fish(String key, String id, String display) {}

    private static final List<Fish> FISH = List.of(
            new Fish("sulphur_skitter", "SULPHUR_SKITTER_BRONZE", "Sulphur Skitter"),
            new Fish("obfuscated_fish_1", "OBFUSCATED_FISH_1_BRONZE", "Obfuscated 1"),
            new Fish("steaming_hot_flounder", "STEAMING_HOT_FLOUNDER_BRONZE", "Steaming-Hot Flounder"),
            new Fish("gusher", "GUSHER_BRONZE", "Gusher"),
            new Fish("blobfish", "BLOBFISH_BRONZE", "Blobfish"),
            new Fish("obfuscated_fish_2", "OBFUSCATED_FISH_2_BRONZE", "Obfuscated 2"),
            new Fish("slugfish", "SLUGFISH_BRONZE", "Slugfish"),
            new Fish("flyfish", "FLYFISH_BRONZE", "Flyfish"),
            new Fish("obfuscated_fish_3", "OBFUSCATED_FISH_3_BRONZE", "Obfuscated 3"),
            new Fish("lava_horse", "LAVA_HORSE_BRONZE", "Lava Horse"),
            new Fish("mana_ray", "MANA_RAY_BRONZE", "Mana Ray"),
            new Fish("volcanic_stonefish", "VOLCANIC_STONEFISH_BRONZE", "Volcanic Stonefish"),
            new Fish("vanille", "VANILLE_BRONZE", "Vanille"),
            new Fish("skeleton_fish", "SKELETON_FISH_BRONZE", "Skeleton Fish"),
            new Fish("moldfin", "MOLDFIN_BRONZE", "Moldfin"),
            new Fish("soul_fish", "SOUL_FISH_BRONZE", "Soul Fish"),
            new Fish("karate_fish", "KARATE_FISH_BRONZE", "Karate Fish"),
            new Fish("golden_fish", "GOLDEN_FISH_BRONZE", "Golden Fish"));

    private static final Map<String, ItemStack> icons = new HashMap<>();
    private static final String[] TIER_SUFFIXES = {"_bronze", "_silver", "_gold", "_diamond"};
    private static final String[] TIER_NAMES = {"Bronze", "Silver", "Gold", "Diamond"};

    private static ItemStack icon(String id) {
        ItemStack known = icons.get(id);
        if (known != null) return known;
        icons.put(id, new ItemStack(Items.TROPICAL_FISH));
        SkyblockItems.resolve(id, stack -> {
            if (!stack.isEmpty()) icons.put(id, stack);
        });
        return icons.get(id);
    }

    /** One fish with its per tier counts, bronze first. */
    private record Caught(String key, String display, int[] counts, int total, String[] ids, String[] corners) {}

    private List<Caught> caught = List.of();

    @Override
    public void init(com.teslamaps.profileviewer.screen.ProfileViewerScreen parent,
                     com.teslamaps.profileviewer.data.SkyblockProfiles profiles) {
        super.init(parent, profiles);
        caught = buildCaught();
        rankLines = buildRank();
    }

    // Ported from NEU's trophy/TrophyFishPage.java armorHelmets + getTrophiesPerTier
    private static final String[] RANK_NAMES = {"§aNovice Fisher", "§9Adept Fisher", "§5Expert Fisher", "§6Master Fisher"};
    private static final int[] RANK_NEEDED = {15, 18, 18, 18};
    private List<net.minecraft.network.chat.Component> rankLines = List.of();

    private List<net.minecraft.network.chat.Component> buildRank() {
        SkyblockProfile profile = getProfile();
        JsonObject trophyFish = profile != null && profile.getMemberData().has("trophy_fish")
                && profile.getMemberData().get("trophy_fish").isJsonObject()
                ? profile.getMemberData().getAsJsonObject("trophy_fish") : null;
        if (trophyFish == null) return List.of();

        int[] perTier = new int[4];
        for (Fish fish : FISH) {
            int highest = 0;
            for (int t = 0; t < 4; t++) if (asInt(trophyFish.get(fish.key() + TIER_SUFFIXES[t])) > 0) highest = t + 1;
            for (int t = 0; t < highest; t++) perTier[t]++;
        }
        com.google.gson.JsonArray rewards = trophyFish.has("rewards") && trophyFish.get("rewards").isJsonArray()
                ? trophyFish.getAsJsonArray("rewards") : new com.google.gson.JsonArray();

        int rank = 0;
        boolean[] done = new boolean[4];
        for (int t = 0; t < 4; t++) {
            boolean claimed = t < rewards.size() && !rewards.get(t).isJsonNull();
            done[t] = claimed || perTier[t] >= RANK_NEEDED[t];
            if (done[t]) rank = t + 1;
        }

        StringBuilder tiers = new StringBuilder();
        for (int t = 0; t < 4; t++) {
            if (t > 0) tiers.append("  §8|  ");
            tiers.append(RANK_NAMES[t], 0, 2).append(RANK_NAMES[t].substring(2).split(" ")[0]).append(' ')
                    .append(done[t] ? "§a✔" : "§c" + perTier[t] + "/" + RANK_NEEDED[t]);
        }
        String head = "§7Trophy Fisher: " + (rank == 0 ? "§8None" : RANK_NAMES[rank - 1]);
        if (rank < 4) {
            int missing = Math.max(0, RANK_NEEDED[rank] - perTier[rank]);
            head += "  §7Next: " + RANK_NAMES[rank] + " §7- §f" + missing + " §7more "
                    + TIER_NAMES[rank].toLowerCase(java.util.Locale.ROOT) + " fish";
        }
        return List.of(com.teslamaps.profileviewer.data.LegacyText.parse(head),
                com.teslamaps.profileviewer.data.LegacyText.parse(tiers.toString()));
    }

    /** Known fish plus any new key the api carries, most caught first. */
    private List<Caught> buildCaught() {
        SkyblockProfile profile = getProfile();
        JsonObject trophyFish = profile != null && profile.getMemberData().has("trophy_fish")
                && profile.getMemberData().get("trophy_fish").isJsonObject()
                ? profile.getMemberData().getAsJsonObject("trophy_fish") : null;
        if (trophyFish == null) return List.of();

        Map<String, String> names = new java.util.LinkedHashMap<>();
        for (Fish fish : FISH) names.put(fish.key(), fish.display());
        for (String key : trophyFish.keySet()) {
            if (key.equals("total_caught") || key.equals("rewards") || key.startsWith("last_caught")) continue;
            if (!trophyFish.get(key).isJsonPrimitive() || !trophyFish.get(key).getAsJsonPrimitive().isNumber()) continue;
            String base = key;
            for (String suffix : TIER_SUFFIXES) {
                if (key.endsWith(suffix)) base = key.substring(0, key.length() - suffix.length());
            }
            names.putIfAbsent(base, com.teslamaps.profileviewer.ui.Kit.prettify(base));
        }

        List<Caught> out = new ArrayList<>();
        for (Map.Entry<String, String> entry : names.entrySet()) {
            int[] counts = new int[4];
            int total = 0;
            String[] ids = new String[4];
            String[] corners = new String[4];
            for (int t = 0; t < 4; t++) {
                counts[t] = asInt(trophyFish.get(entry.getKey() + TIER_SUFFIXES[t]));
                total += counts[t];
                ids[t] = (entry.getKey() + TIER_SUFFIXES[t]).toUpperCase(java.util.Locale.ROOT);
                corners[t] = counts[t] > 0 ? com.teslamaps.profileviewer.ui.Kit.compact(counts[t]) : null;
            }
            // the plain key is the running total hypixel keeps next to the tiers
            total = Math.max(total, asInt(trophyFish.get(entry.getKey())));
            out.add(new Caught(entry.getKey(), entry.getValue(), counts, total, ids, corners));
        }
        out.sort((a, b) -> Integer.compare(b.total(), a.total()));
        return out;
    }

    @Override
    public boolean isFixedLayout() {
        return true;
    }

    @Override
    public String getTabName() {
        return "Trophy Fish";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.TROPICAL_FISH);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        Font tr = Minecraft.getInstance().font;
        int left = x - 8;
        int top = y - 8;

        JsonObject trophyFish = profile.getMemberData().has("trophy_fish")
                && profile.getMemberData().get("trophy_fish").isJsonObject()
                ? profile.getMemberData().getAsJsonObject("trophy_fish") : null;

        ctx.text(tr, "Trophy Fish", left + 10, top + 8, NeuStyle.TEXT_GREEN);
        if (trophyFish == null) {
            ctx.text(tr, "No trophy fish data", left + 10, top + 24, NeuStyle.TEXT_GRAY);
            return;
        }

        // header totals: caught overall, unique species, one total per tier
        int totalCaught = asInt(trophyFish.get("total_caught"));
        int unique = 0;
        int[] tierTotals = new int[4];
        for (Caught fish : caught) {
            boolean any = false;
            for (int t = 0; t < 4; t++) {
                tierTotals[t] += fish.counts()[t];
                any |= fish.counts()[t] > 0;
            }
            if (any) unique++;
        }

        String totals = "Total: " + com.teslamaps.profileviewer.ui.Kit.dots(totalCaught) + "   Unique: " + unique + "/" + caught.size();
        ctx.text(tr, totals, left + NeuStyle.PANEL_WIDTH - 10 - tr.width(totals), top + 8, NeuStyle.TEXT);

        int summaryY = top + 21;
        int sx = left + 10;
        sx = summary(ctx, tr, "Bronze", tierTotals[0], sx, summaryY, BRONZE);
        sx = summary(ctx, tr, "Silver", tierTotals[1], sx, summaryY, SILVER);
        sx = summary(ctx, tr, "Gold", tierTotals[2], sx, summaryY, GOLD);
        summary(ctx, tr, "Diamond", tierTotals[3], sx, summaryY, DIAMOND);

        // one column per fish, diamond on top like the ingame trophy menu
        int gridTop = top + 38;
        int count = Math.max(1, caught.size());
        int slot = Math.max(12, Math.min(22, (NeuStyle.PANEL_WIDTH - 20 + 2) / count - 2));
        int stride = slot + 2;
        int gridX = left + (NeuStyle.PANEL_WIDTH - (count * stride - 2)) / 2;
        List<String> hoverLines = null;
        for (int i = 0; i < caught.size(); i++) {
            Caught fish = caught.get(i);
            int cx = gridX + i * stride;
            for (int t = 3; t >= 0; t--) {
                int cy = gridTop + (3 - t) * stride;
                com.teslamaps.profileviewer.screen.ItemGrid.slot(ctx, cx, cy, slot);
                com.teslamaps.profileviewer.screen.ItemGrid.item(ctx, icon(fish.ids()[t]), cx, cy, slot);
                if (fish.counts()[t] == 0) {
                    ctx.fill(cx + 1, cy + 1, cx + slot - 1, cy + slot - 1, 0xA0000000);
                } else {
                    com.teslamaps.profileviewer.screen.ItemGrid.smallCount(ctx, fish.corners()[t], cx, cy, slot, 0xFFFFFFFF);
                }
                if (mouseX >= cx && mouseX < cx + slot && mouseY >= cy && mouseY < cy + slot) {
                    ctx.fill(cx + 1, cy + 1, cx + slot - 1, cy + slot - 1, 0x40FFFFFF);
                    hoverLines = List.of(
                            "§b" + fish.display(),
                            "§7" + TIER_NAMES[t] + ": §f" + com.teslamaps.profileviewer.ui.Kit.dots(fish.counts()[t]),
                            "§7Total: §f" + com.teslamaps.profileviewer.ui.Kit.dots(fish.total()));
                }
            }
        }

        if (extra(com.teslamaps.config.TeslaMapsConfig.get().pvTrophyRank)) {
            int rankY = gridTop + 4 * stride + 6;
            for (int i = 0; i < rankLines.size(); i++) ctx.text(tr, rankLines.get(i), left + 10, rankY + i * 11, -1);
        }

        if (hoverLines != null && parent != null) {
            List<net.minecraft.network.chat.Component> parsed = new ArrayList<>(hoverLines.size());
            for (String line : hoverLines) parsed.add(com.teslamaps.profileviewer.data.LegacyText.parse(line));
            parent.setHoveredLines(parsed);
        }
    }

    private int summary(GuiGraphicsExtractor ctx, Font tr, String label, int value, int x, int y, int color) {
        String text = label + ": " + com.teslamaps.profileviewer.ui.Kit.dots(value);
        ctx.text(tr, text, x, y, color);
        return x + tr.width(text) + 14;
    }
}
