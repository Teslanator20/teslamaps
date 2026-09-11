package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Heart of the Forest, the foraging counterpart to the mining page. */
public class ForagingPage extends ProfileViewerPage {
    private static final int SECTION = 120;

    // cumulative hotf xp per tier, same shape as the mining one
    private static final long[] HOTF_XP = {
            0, 3_000, 9_000, 25_000, 60_000, 100_000, 150_000, 210_000, 290_000, 400_000,
    };

    @Override
    public boolean isFixedLayout() {
        return true;
    }

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
        Font tr = Minecraft.getInstance().font;
        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        JsonObject member = profile.getMemberData();
        int left = x - 8;
        int top = y - 8;

        JsonObject skillTree = member.has("skill_tree") ? member.getAsJsonObject("skill_tree") : null;
        JsonObject nodes = skillTree != null && skillTree.has("nodes")
                && skillTree.getAsJsonObject("nodes").has("foraging")
                ? skillTree.getAsJsonObject("nodes").getAsJsonObject("foraging") : null;

        int tier = nodes != null ? asInt(nodes.get("center_of_the_forest")) : 0;
        long xp = skillTree != null && skillTree.has("experience")
                ? asLong(skillTree.getAsJsonObject("experience").get("foraging")) : 0;

        int cx = left + 14;
        int cy = top + 10;

        ctx.text(tr, "Heart of the Forest", cx, cy, NeuStyle.TEXT_GREEN);
        cy += 12;

        ctx.text(tr, "Tier " + tier, cx, cy, NeuStyle.TEXT);
        String xpText = formatXp(xp);
        ctx.text(tr, xpText, cx + SECTION - tr.width(xpText), cy, NeuStyle.TEXT_GRAY);
        cy += 10;
        NeuStyle.bar(ctx, cx, cy, SECTION, progress(xp, tier));
        cy += 16;

        if (skillTree != null && skillTree.has("tokens_spent")) {
            JsonObject spent = skillTree.getAsJsonObject("tokens_spent");
            int forest = 0;
            for (Map.Entry<String, JsonElement> entry : spent.entrySet()) {
                if (entry.getKey().startsWith("forest")) forest += asInt(entry.getValue());
            }
            row(ctx, tr, cx, cy, "Tokens spent:", String.valueOf(forest), NeuStyle.TEXT);
            cy += 12;
        }

        JsonObject core = member.has("foraging_core") ? member.getAsJsonObject("foraging_core") : null;
        if (core != null) {
            row(ctx, tr, cx, cy, "Trees today:", String.valueOf(asInt(core.get("daily_trees_cut"))), NeuStyle.TEXT);
            cy += 11;
            row(ctx, tr, cx, cy, "Gifts today:", String.valueOf(asInt(core.get("daily_gifts"))), NeuStyle.TEXT);
            cy += 16;

            if (core.has("whispers")) {
                ctx.text(tr, "Whispers", cx, cy, NeuStyle.TEXT_GREEN);
                cy += 12;
                JsonObject whispers = core.getAsJsonObject("whispers");
                for (Map.Entry<String, JsonElement> entry : whispers.entrySet()) {
                    if (!entry.getValue().isJsonObject()) continue;
                    long total = asLong(entry.getValue().getAsJsonObject().get("total"));
                    if (total <= 0) continue;
                    row(ctx, tr, cx, cy, capitalize(entry.getKey()), formatXp(total), NeuStyle.TEXT_GRAY);
                    cy += 10;
                }
            }
        }

        // middle: personal bests and the fish family
        int mx = left + 150;
        int my = top + 10;

        JsonObject foraging = member.has("foraging") ? member.getAsJsonObject("foraging") : null;
        if (foraging != null) {
            JsonObject starlyn = foraging.has("starlyn") ? foraging.getAsJsonObject("starlyn") : null;
            if (starlyn != null && starlyn.has("personal_bests")) {
                ctx.text(tr, "Personal Bests", mx, my, NeuStyle.TEXT_GREEN);
                my += 12;
                JsonObject bests = starlyn.getAsJsonObject("personal_bests");
                for (Map.Entry<String, JsonElement> entry : bests.entrySet()) {
                    row(ctx, tr, mx, my, capitalize(entry.getKey()), formatXp(asLong(entry.getValue())),
                            NeuStyle.TEXT_GRAY);
                    my += 10;
                }
                my += 6;
            }

            if (foraging.has("fish_family") && foraging.get("fish_family").isJsonArray()) {
                int found = foraging.getAsJsonArray("fish_family").size();
                row(ctx, tr, mx, my, "Fish family:", String.valueOf(found), NeuStyle.TEXT);
                my += 12;
            }

            if (foraging.has("tree_gifts")) {
                row(ctx, tr, mx, my, "Tree gifts:",
                        String.valueOf(foraging.getAsJsonObject("tree_gifts").size()), NeuStyle.TEXT);
            }
        }

        // right: the perks that are levelled
        int px = left + 286;
        int py = top + 10;
        ctx.text(tr, "Perks", px, py, NeuStyle.TEXT_GREEN);
        py += 12;

        if (nodes != null) {
            List<Map.Entry<String, JsonElement>> perks = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : nodes.entrySet()) {
                if (entry.getKey().startsWith("toggle_")) continue;
                if (entry.getKey().equals("center_of_the_forest")) continue;
                if (asInt(entry.getValue()) <= 0) continue;
                perks.add(entry);
            }
            perks.sort((a, b) -> Integer.compare(asInt(b.getValue()), asInt(a.getValue())));

            int shown = 0;
            for (Map.Entry<String, JsonElement> entry : perks) {
                if (py > top + NeuStyle.PANEL_HEIGHT - 24) {
                    ctx.text(tr, "+" + (perks.size() - shown) + " more", px, py, NeuStyle.TEXT_DARK);
                    break;
                }
                row(ctx, tr, px, py, capitalize(entry.getKey()), String.valueOf(asInt(entry.getValue())),
                        NeuStyle.TEXT_GRAY);
                py += 10;
                shown++;
            }
        } else {
            ctx.text(tr, "Not in the api", px, py, NeuStyle.TEXT_GRAY);
        }
    }

    private void row(GuiGraphicsExtractor ctx, Font tr, int x, int y, String label, String value, int color) {
        ctx.text(tr, label, x, y, NeuStyle.TEXT_GRAY);
        ctx.text(tr, value, x + SECTION - tr.width(value), y, color);
    }

    private static double progress(long xp, int tier) {
        if (tier >= HOTF_XP.length) return 1;
        long base = HOTF_XP[Math.max(0, tier - 1)];
        long next = HOTF_XP[Math.min(HOTF_XP.length - 1, tier)];
        if (next <= base) return 1;
        return Math.max(0, Math.min(1, (double) (xp - base) / (next - base)));
    }

    private static String capitalize(String key) {
        String[] words = key.replace('_', ' ').toLowerCase().split(" ");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
