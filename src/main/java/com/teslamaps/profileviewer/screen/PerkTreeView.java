package com.teslamaps.profileviewer.screen;

import com.teslamaps.profileviewer.data.LegacyText;
import com.teslamaps.profileviewer.data.PerkRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a Heart of the Mountain / Forest tree the way the game shows it: tier 1 at the bottom,
 * every node the vanilla item hypixel uses for that node type and unlock state.
 */
public class PerkTreeView {
    private static final int CELL = 18;

    public record Icons(String tierLocked, String tierUnlocked, String tierUnlocking,
                        String coreMax, String coreUnlocked, String coreLeveling, String coreLocked,
                        String abilityUnlocked, String abilityLocked,
                        String nodeMax, String nodeUnlocked, String nodeLocked) {}

    public static final Icons MINING = new Icons(
            "red_stained_glass_pane", "green_stained_glass_pane", "yellow_stained_glass_pane",
            "diamond_block", "copper_block", "redstone_block", "bedrock",
            "redstone_block", "coal_block",
            "diamond", "emerald", "coal");

    public static final Icons FORAGING = new Icons(
            "red_stained_glass_pane", "green_stained_glass_pane", "yellow_stained_glass_pane",
            "oak_wood", "stripped_birch_wood", "stripped_oak_wood", "stripped_pale_oak_wood",
            "cherry_sapling", "pale_oak_sapling",
            "oak_log", "stripped_oak_log", "pale_oak_button");

    private static final Map<String, ItemStack> icons = new HashMap<>();
    private static final Map<String, ItemStack> nodes = new HashMap<>();

    /** Returns the stack under the cursor so the caller can show its tooltip. */
    public static ItemStack render(GuiGraphicsExtractor ctx, List<PerkRegistry.Perk> perks,
                                   Map<String, Integer> levels, int treeLevel, Icons icons,
                                   int x, int y, int mouseX, int mouseY) {
        if (perks.isEmpty()) return ItemStack.EMPTY;

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = 0;
        int maxTier = 0;
        for (PerkRegistry.Perk perk : perks) {
            minX = Math.min(minX, perk.x());
            maxX = Math.max(maxX, perk.x());
            maxY = Math.max(maxY, perk.y());
            if ("TIER".equals(perk.type())) maxTier = Math.max(maxTier, PerkRegistry.tierNumber(perk.name()));
        }
        if (maxTier == 0) maxTier = 10;

        Map<Long, PerkRegistry.Perk> byPosition = new HashMap<>();
        for (PerkRegistry.Perk perk : perks) byPosition.put(key(perk.x() - minX, perk.y()), perk);

        Font font = Minecraft.getInstance().font;
        ItemStack hovered = ItemStack.EMPTY;

        for (int row = 0; row <= maxY; row++) {
            // tier one sits at the bottom in game, so rows are drawn upside down
            int drawRow = maxY - row;
            for (int column = 0; column <= maxX - minX; column++) {
                PerkRegistry.Perk perk = byPosition.get(key(column, row));
                if (perk == null || "SPACER".equals(perk.type())) continue;

                int sx = x + column * CELL;
                int sy = y + drawRow * CELL;

                int level = perk.id() == null ? -1 : levels.getOrDefault(perk.id(), -1);
                ItemStack stack = node(perk, level, treeLevel, maxTier, icons);
                ctx.item(stack, sx + 1, sy + 1);

                if (level > 1 && !"TIER".equals(perk.type())) {
                    String text = String.valueOf(level);
                    ctx.text(font, text, sx + 17 - font.width(text), sy + 9, 0xFFFFFFFF);
                }

                if (mouseX >= sx && mouseX < sx + 17 && mouseY >= sy && mouseY < sy + 17) {
                    ctx.fill(sx + 1, sy + 1, sx + 16, sy + 16, 0x40FFFFFF);
                    hovered = stack;
                }
            }
        }

        return hovered;
    }

    public static int width(List<PerkRegistry.Perk> perks) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        for (PerkRegistry.Perk perk : perks) {
            minX = Math.min(minX, perk.x());
            maxX = Math.max(maxX, perk.x());
        }
        return perks.isEmpty() ? 0 : (maxX - minX + 1) * CELL;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static ItemStack node(PerkRegistry.Perk perk, int level, int treeLevel, int maxTier, Icons icons) {
        String id = iconId(perk, level, treeLevel, maxTier, icons);
        String cacheKey = perk.type() + ":" + perk.name() + ":" + perk.id() + ":" + level + ":" + treeLevel + ":" + id;

        ItemStack cached = nodes.get(cacheKey);
        if (cached != null) return cached;

        ItemStack stack = item(id).copy();
        List<String> tooltip = PerkRegistry.tooltip(perk, level, treeLevel);
        if (!tooltip.isEmpty()) {
            stack.set(DataComponents.CUSTOM_NAME, LegacyText.parse(tooltip.getFirst()));
            List<Component> lore = new ArrayList<>();
            for (int i = 1; i < tooltip.size(); i++) lore.add(LegacyText.parse(tooltip.get(i)));
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }

        nodes.put(cacheKey, stack);
        return stack;
    }

    private static String iconId(PerkRegistry.Perk perk, int level, int treeLevel, int maxTier, Icons icons) {
        return switch (perk.type() == null ? "" : perk.type()) {
            case "TIER" -> {
                int tier = PerkRegistry.tierNumber(perk.name());
                if (treeLevel >= tier) yield icons.tierUnlocked();
                yield treeLevel + 1 >= tier ? icons.tierUnlocking() : icons.tierLocked();
            }
            case "CORE" -> {
                if (treeLevel >= maxTier) yield icons.coreMax();
                if (treeLevel > 0) yield icons.coreUnlocked();
                yield level > 0 ? icons.coreLeveling() : icons.coreLocked();
            }
            case "ABILITY" -> level > 0 ? icons.abilityUnlocked() : icons.abilityLocked();
            default -> {
                if (level < 0) yield icons.nodeLocked();
                if (perk.max() != null && level >= perk.max()) yield icons.nodeMax();
                yield icons.nodeUnlocked();
            }
        };
    }

    private static ItemStack item(String id) {
        return icons.computeIfAbsent(id, key -> {
            Identifier identifier = Identifier.tryParse(key.contains(":") ? key : "minecraft:" + key);
            if (identifier == null) return new ItemStack(Items.PAPER);
            return BuiltInRegistries.ITEM.get(identifier)
                    .map(holder -> new ItemStack(holder.value()))
                    .orElse(new ItemStack(Items.PAPER));
        });
    }
}
