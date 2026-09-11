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
    private static final int ICON = 16;
    private static final int CELL = ICON + 2;

    public record Icons(String tierLocked, String tierUnlocked, String tierUnlocking,
                        String coreMax, String coreUnlocked, String coreLeveling, String coreLocked,
                        String abilitySelected, String abilityUnlocked, String abilityLocked,
                        String nodeMax, String nodeUnlocked, String nodeLocked, String nodeDisabled) {}

    public static final Icons MINING = new Icons(
            "red_stained_glass_pane", "green_stained_glass_pane", "yellow_stained_glass_pane",
            "diamond_block", "copper_block", "redstone_block", "bedrock",
            "emerald_block", "redstone_block", "coal_block",
            "diamond", "emerald", "coal", "redstone");

    public static final Icons FORAGING = new Icons(
            "red_stained_glass_pane", "green_stained_glass_pane", "yellow_stained_glass_pane",
            "oak_wood", "stripped_birch_wood", "stripped_oak_wood", "stripped_pale_oak_wood",
            "oak_sapling", "cherry_sapling", "pale_oak_sapling",
            "oak_log", "stripped_oak_log", "pale_oak_button", "stripped_mangrove_log");

    private static final Map<String, ItemStack> icons = new HashMap<>();
    private static final Map<String, ItemStack> nodes = new HashMap<>();

    /** Returns the stack under the cursor so the caller can show its tooltip. */
    public static ItemStack render(GuiGraphicsExtractor ctx, List<PerkRegistry.Perk> perks,
                                   Map<String, Integer> levels, int treeLevel, Icons icons,
                                   java.util.Set<String> disabled,
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
                boolean off = perk.id() != null && disabled.contains(perk.id());
                ItemStack stack = node(perk, level, treeLevel, maxTier, icons, off);
                ctx.pose().pushMatrix();
                ctx.pose().translate(sx, sy);
                ctx.pose().scale(ICON / 16f, ICON / 16f);
                ctx.item(stack, 0, 0);
                ctx.pose().popMatrix();

                if (level > 1 && !"TIER".equals(perk.type())) {
                    String text = String.valueOf(level);
                    ctx.pose().pushMatrix();
                    ctx.pose().translate(sx + ICON - font.width(text) * 0.75f, sy + ICON - font.lineHeight * 0.75f);
                    ctx.pose().scale(0.75f, 0.75f);
                    ctx.text(font, text, 0, 0, 0xFFFFFFFF);
                    ctx.pose().popMatrix();
                }

                if (mouseX >= sx && mouseX < sx + ICON && mouseY >= sy && mouseY < sy + ICON) {
                    ctx.fill(sx, sy, sx + ICON, sy + ICON, 0x40FFFFFF);
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

    public static int height(List<PerkRegistry.Perk> perks) {
        int maxY = 0;
        for (PerkRegistry.Perk perk : perks) maxY = Math.max(maxY, perk.y());
        return perks.isEmpty() ? 0 : (maxY + 1) * CELL;
    }

    /** The tree as a layout component, so pages can place it without hand computed offsets. */
    public static com.teslamaps.profileviewer.ui.Ui component(List<PerkRegistry.Perk> perks,
                                                              Map<String, Integer> levels,
                                                              int treeLevel, Icons icons,
                                                              java.util.Set<String> disabled) {
        return new com.teslamaps.profileviewer.ui.Ui() {
            @Override
            public int width() {
                return PerkTreeView.width(perks);
            }

            @Override
            public int height() {
                return PerkTreeView.height(perks);
            }

            @Override
            public void render(GuiGraphicsExtractor ctx, int x, int y, int mouseX, int mouseY) {
                ItemStack hovered = PerkTreeView.render(ctx, perks, levels, treeLevel, icons, disabled,
                        x, y, mouseX, mouseY);
                if (!hovered.isEmpty()) com.teslamaps.profileviewer.ui.Ui.hoveredStack = hovered;
            }
        };
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static ItemStack node(PerkRegistry.Perk perk, int level, int treeLevel, int maxTier,
                                  Icons icons, boolean disabled) {
        String id = disabled && isPlainNode(perk) ? icons.nodeDisabled() : iconId(perk, level, treeLevel, maxTier, icons);
        String cacheKey = perk.type() + ":" + perk.name() + ":" + perk.id() + ":" + level + ":" + treeLevel
                + ":" + disabled + ":" + id;

        ItemStack cached = nodes.get(cacheKey);
        if (cached != null) return cached;

        ItemStack stack = item(id).copy();
        List<String> tooltip = new ArrayList<>(PerkRegistry.tooltip(perk, level, treeLevel));
        if (disabled && !tooltip.isEmpty()) {
            tooltip.removeIf(line -> line.endsWith("§lENABLED"));
            tooltip.add("§c§lDISABLED");
        }
        if (!tooltip.isEmpty()) {
            stack.set(DataComponents.CUSTOM_NAME, LegacyText.parse(tooltip.getFirst()));
            List<Component> lore = new ArrayList<>();
            for (int i = 1; i < tooltip.size(); i++) lore.add(LegacyText.parse(tooltip.get(i)));
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }

        nodes.put(cacheKey, stack);
        return stack;
    }

    private static boolean isPlainNode(PerkRegistry.Perk perk) {
        String type = perk.type();
        return !"TIER".equals(type) && !"CORE".equals(type) && !"ABILITY".equals(type);
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
                if (treeLevel <= 0) yield icons.coreLocked();
                yield treeLevel == 1 ? icons.coreUnlocked() : icons.coreLeveling();
            }
            case "ABILITY" -> level > 0 ? icons.abilityUnlocked() : icons.abilityLocked();
            default -> {
                if (level < 0) yield icons.nodeLocked();
                if (perk.max() != null && level >= perk.max()) yield icons.nodeMax();
                yield level > 0 ? icons.nodeUnlocked() : icons.nodeLocked();
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
