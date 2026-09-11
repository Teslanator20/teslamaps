package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.ItemGrid;
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

/**
 * Heart of the Mountain. Hypixel moved this out of mining_core into skill_tree, so the
 * level is the core_of_the_mountain node and the xp sits in skill_tree.experience.mining.
 */
public class MiningPage extends ProfileViewerPage {
    private static final int SECTION = 120;

    // cumulative hotm xp per tier
    private static final long[] HOTM_XP = {
            0, 3_000, 9_000, 25_000, 60_000, 100_000, 150_000, 210_000, 290_000, 400_000,
    };

    private static final int MITHRIL_COLOR = 0xFF55FFFF;
    private static final int GEMSTONE_COLOR = 0xFFFF55FF;
    private static final int GLACITE_COLOR = 0xFF5555FF;

    @Override
    public boolean isFixedLayout() {
        return true;
    }

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
        Font tr = Minecraft.getInstance().font;
        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        JsonObject member = profile.getMemberData();
        int left = x - 8;
        int top = y - 8;

        JsonObject skillTree = member.has("skill_tree") ? member.getAsJsonObject("skill_tree") : null;
        JsonObject miningNodes = skillTree != null && skillTree.has("nodes")
                && skillTree.getAsJsonObject("nodes").has("mining")
                ? skillTree.getAsJsonObject("nodes").getAsJsonObject("mining") : null;

        int hotmLevel = miningNodes != null ? asInt(miningNodes.get("core_of_the_mountain")) : 0;
        long hotmXp = skillTree != null && skillTree.has("experience")
                ? asLong(skillTree.getAsJsonObject("experience").get("mining")) : 0;

        // left column: level, xp, tokens
        int cx = left + 14;
        int cy = top + 10;

        ctx.text(tr, "Heart of the Mountain", cx, cy, NeuStyle.TEXT_AQUA);
        cy += 12;

        String tierText = "Tier " + hotmLevel;
        ctx.text(tr, tierText, cx, cy, NeuStyle.TEXT);
        String xpText = formatXp(hotmXp);
        ctx.text(tr, xpText, cx + SECTION - tr.width(xpText), cy, NeuStyle.TEXT_GRAY);
        cy += 10;

        NeuStyle.bar(ctx, cx, cy, SECTION, hotmProgress(hotmXp, hotmLevel));
        cy += 14;

        if (skillTree != null && skillTree.has("tokens_spent")) {
            int spent = asInt(skillTree.getAsJsonObject("tokens_spent").get("mountain"));
            row(ctx, tr, cx, cy, "Tokens spent:", String.valueOf(spent), NeuStyle.TEXT);
            cy += 11;
        }

        JsonObject miningCore = member.has("mining_core") ? member.getAsJsonObject("mining_core") : null;
        if (miningCore != null) {
            cy += 6;
            ctx.text(tr, "Powder", cx, cy, NeuStyle.TEXT_AQUA);
            cy += 12;
            cy = powder(ctx, tr, cx, cy, miningCore, "Mithril", "mithril", MITHRIL_COLOR);
            cy = powder(ctx, tr, cx, cy, miningCore, "Gemstone", "gemstone", GEMSTONE_COLOR);
            cy = powder(ctx, tr, cx, cy, miningCore, "Glacite", "glacite", GLACITE_COLOR);
        }

        // middle column: glacite and crystals
        int gx = left + 150;
        int gy = top + 10;

        JsonObject glacite = member.has("glacite_player_data") ? member.getAsJsonObject("glacite_player_data") : null;
        if (glacite != null) {
            ctx.text(tr, "Glacite Tunnels", gx, gy, NeuStyle.TEXT_AQUA);
            gy += 12;
            row(ctx, tr, gx, gy, "Mineshafts:", String.valueOf(asInt(glacite.get("mineshafts_entered"))), NeuStyle.TEXT);
            gy += 11;

            if (glacite.has("corpses_looted")) {
                JsonObject corpses = glacite.getAsJsonObject("corpses_looted");
                int total = 0;
                for (Map.Entry<String, JsonElement> entry : corpses.entrySet()) total += asInt(entry.getValue());
                row(ctx, tr, gx, gy, "Corpses:", String.valueOf(total), NeuStyle.TEXT);
                gy += 11;
                for (Map.Entry<String, JsonElement> entry : corpses.entrySet()) {
                    row(ctx, tr, gx + 6, gy, capitalize(entry.getKey()), String.valueOf(asInt(entry.getValue())),
                            NeuStyle.TEXT_GRAY);
                    gy += 10;
                }
            }

            if (glacite.has("fossils_donated")) {
                row(ctx, tr, gx, gy, "Fossils:",
                        glacite.getAsJsonArray("fossils_donated").size() + " / 11", NeuStyle.TEXT);
                gy += 11;
            }
            row(ctx, tr, gx, gy, "Fossil dust:", String.valueOf((int) asLong(glacite.get("fossil_dust"))), NeuStyle.TEXT);
        }

        // right: the perks as a slot grid, disabled ones tinted like in the menu
        int px = left + 244;
        ctx.text(tr, "Heart of the Mountain", px, top + 8, NeuStyle.TEXT_AQUA);
        int py = top + 20;

        ItemStack hovered = ItemStack.EMPTY;
        if (miningNodes != null) {
            List<String> perks = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : miningNodes.entrySet()) {
                if (entry.getKey().startsWith("toggle_")) continue;
                if (entry.getKey().equals("core_of_the_mountain")) continue;
                perks.add(entry.getKey());
            }
            perks.sort((a, b) -> Integer.compare(asInt(miningNodes.get(b)), asInt(miningNodes.get(a))));

            int columns = 9;
            for (int i = 0; i < perks.size() && i < columns * 7; i++) {
                String perk = perks.get(i);
                int level = asInt(miningNodes.get(perk));
                boolean enabled = !miningNodes.has("toggle_" + perk)
                        || miningNodes.get("toggle_" + perk).getAsBoolean();

                int sx = px + (i % columns) * ItemGrid.SLOT;
                int sy = py + (i / columns) * ItemGrid.SLOT;

                ctx.fill(sx, sy, sx + 17, sy + 17, 0x60000000);
                ctx.fill(sx, sy, sx + 17, sy + 1, 0x40FFFFFF);
                ctx.fill(sx, sy, sx + 1, sy + 17, 0x40FFFFFF);

                ItemStack stack = perkStack(perk, level, enabled);
                ctx.item(stack, sx + 1, sy + 1);
                if (!enabled) ctx.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x50AA0000);

                if (level > 1) {
                    String levelText = String.valueOf(level);
                    ctx.text(tr, levelText, sx + 17 - tr.width(levelText), sy + 9, 0xFFFFFFFF);
                }

                if (mouseX >= sx && mouseX < sx + 17 && mouseY >= sy && mouseY < sy + 17) {
                    ctx.fill(sx + 1, sy + 1, sx + 16, sy + 16, 0x40FFFFFF);
                    hovered = stack;
                }
            }
        } else {
            ctx.text(tr, "Not in the api", px, py, NeuStyle.TEXT_GRAY);
        }

        hoverStack(hovered);
    }

    private static final Map<String, ItemStack> perkStacks = new java.util.HashMap<>();

    private static ItemStack perkStack(String perk, int level, boolean enabled) {
        String key = perk + ":" + level + ":" + enabled;
        ItemStack cached = perkStacks.get(key);
        if (cached != null) return cached;

        ItemStack stack = new ItemStack(perkIcon(perk));
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                com.teslamaps.profileviewer.data.LegacyText.parse(
                        (level > 0 ? "§a" : "§7") + capitalize(perk)));

        List<net.minecraft.network.chat.Component> lore = new ArrayList<>();
        lore.add(com.teslamaps.profileviewer.data.LegacyText.parse(
                level > 0 ? "§7Level §e" + level : "§cNot unlocked"));
        if (!enabled) lore.add(com.teslamaps.profileviewer.data.LegacyText.parse("§cDisabled"));
        stack.set(net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(lore));

        perkStacks.put(key, stack);
        return stack;
    }

    /** Best effort icons; the real menu slots are not exposed by the api. */
    private static net.minecraft.world.item.Item perkIcon(String perk) {
        return switch (perk) {
            case "mining_speed", "speedy_mineman", "seasoned_mineman" -> Items.GOLDEN_PICKAXE;
            case "mining_fortune", "fortunate_mineman" -> Items.DIAMOND_PICKAXE;
            case "titanium_insanium" -> Items.IRON_BLOCK;
            case "pickobulus" -> Items.SNOWBALL;
            case "efficient_miner" -> Items.NETHERITE_PICKAXE;
            case "mole" -> Items.DIRT;
            case "gem_lover" -> Items.EMERALD;
            case "professional", "mining_master" -> Items.BOOK;
            case "old_school" -> Items.STONE_PICKAXE;
            case "sky_mall" -> Items.EMERALD_BLOCK;
            case "crystalline" -> Items.AMETHYST_SHARD;
            case "dead_mans_chest", "gifts_from_the_departed" -> Items.CHEST;
            case "keep_it_cool" -> Items.ICE;
            case "metal_head" -> Items.IRON_HELMET;
            case "no_stone_unturned" -> Items.COBBLESTONE;
            case "powder_buff" -> Items.GLOWSTONE_DUST;
            case "steady_hand" -> Items.LEATHER_BOOTS;
            case "strong_arm" -> Items.IRON_SWORD;
            case "surveyor" -> Items.MAP;
            case "warm_heart" -> Items.MAGMA_CREAM;
            case "daily_powder" -> Items.REDSTONE;
            default -> Items.PAPER;
        };
    }

    private int powder(GuiGraphicsExtractor ctx, Font tr, int x, int y, JsonObject core,
                       String label, String key, int color) {
        long current = asLong(core.get("powder_" + key));
        long spent = asLong(core.get("powder_spent_" + key));
        ctx.text(tr, label, x, y, color);
        String value = formatXp(current);
        ctx.text(tr, value, x + SECTION - tr.width(value), y, NeuStyle.TEXT);
        y += 10;
        if (spent > 0) {
            String spentText = "spent " + formatXp(spent);
            ctx.text(tr, spentText, x + SECTION - tr.width(spentText), y, NeuStyle.TEXT_DARK);
            y += 10;
        }
        return y;
    }

    private void row(GuiGraphicsExtractor ctx, Font tr, int x, int y, String label, String value, int color) {
        ctx.text(tr, label, x, y, NeuStyle.TEXT_GRAY);
        ctx.text(tr, value, x + SECTION - tr.width(value), y, color);
    }

    private static double hotmProgress(long xp, int level) {
        if (level >= HOTM_XP.length) return 1;
        long base = HOTM_XP[Math.max(0, level - 1)];
        long next = HOTM_XP[Math.min(HOTM_XP.length - 1, level)];
        if (next <= base) return 1;
        return Math.max(0, Math.min(1, (double) (xp - base) / (next - base)));
    }

    private static String capitalize(String key) {
        String[] words = key.replace('_', ' ').split(" ");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
