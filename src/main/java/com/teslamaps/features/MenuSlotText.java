package com.teslamaps.features;

// Ported from Skyblocker's slottext adders: Collection, BestiaryLevel, CropMilestones, GardenUpgrades,
// ChipLevel, MinionLevel, PotionLevel, CommunityShop, PowerStonesGuide, SkyblockGuide, EssenceShop, YourEssence
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.NumberUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MenuSlotText {

    private enum Pos { BOTTOM_RIGHT, TOP_RIGHT, BOTTOM_LEFT }

    private record Label(int slot, String text, int color, Pos pos) {}

    private static final int CREAM = 0xFFFFDDC1, GOLD = 0xFFE5B80B, LIGHT_ORANGE = 0xFFFAB387,
            LIGHT_YELLOW = 0xFFF9E2AF, LIGHT_PURPLE = 0xFFCBA6F7, LIGHT_RED = 0xFFFF7276,
            LIGHT_GREEN = 0xFF90EE90, CLAIM = 0xFFA6E3A1;

    private static final Pattern T_COLLECTIONS = Pattern.compile("^\\w+ Collections.*");
    private static final Pattern T_BESTIARY = Pattern.compile("(?:\\(\\d+/\\d+\\) )?(?:Bestiary|Fishing) ➜ .+");
    private static final Pattern T_GARDEN = Pattern.compile("^(?:Crop|Greenhouse) Upgrades.*");
    private static final Pattern T_GUIDE = Pattern.compile("^(?:\\(\\d+/\\d+\\)\\s+)?Guide ➜ \\w+.*");

    private static final Pattern COLLECTION = Pattern.compile("^[\\w -]+ ([IVXLCDM]+)$");
    private static final Pattern BESTIARY = Pattern.compile("^[\\w '-]+ ([IVXLCDM]+)$");
    private static final Pattern TIER = Pattern.compile("Current Tier: (\\d+)/(\\d+)");
    private static final Pattern CHIP = Pattern.compile("Level (\\d+)/?(\\d+)?");
    private static final Pattern MINION = Pattern.compile(".* Minion ([IVXLCDM]+)");
    private static final Pattern LEARNED = Pattern.compile("Learned: (?:Yes|Not Yet) ([✖✔])");
    private static final Pattern GUIDE = Pattern.compile("^([✖✔])\\s*.+");
    private static final Pattern ESSENCE_LEVEL = Pattern.compile("^[\\w ]+ ([IVXLCDM]+)$");
    private static final Pattern YOUR_X_ESSENCE = Pattern.compile("Your \\w+ Essence: ([\\d,]+)");
    private static final Pattern OWN_ESSENCE = Pattern.compile("You currently own ([\\d,]+)");

    private static List<Label> labels = List.of();
    private static int ticks;
    private static int communityPage = -1;
    private static Feature feature;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("MenuSlotText").section("Inventory")
                .when(() -> cfg().menuSlotText)
                .tick(MenuSlotText::tick)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Slot Text");
                    b.toggle("Menu Slot Text", () -> c.menuSlotText, v -> c.menuSlotText = v)
                            .desc("Small numbers and marks on slots, readable without hovering.");
                    b.toggle("  Levels: Collections, Bestiary, Crops, Garden, Chips", () -> c.mstLevels, v -> c.mstLevels = v)
                            .desc("Gold once maxed.");
                    b.toggle("  Minion Tier (everywhere)", () -> c.mstMinion, v -> c.mstMinion = v);
                    b.toggle("  Potion Level (everywhere)", () -> c.mstPotion, v -> c.mstPotion = v);
                    b.toggle("  Community Shop, Power Stones, SkyBlock Guide", () -> c.mstShopGuide, v -> c.mstShopGuide = v)
                            .desc("Upgrade level, Max, running or claimable. Power Stones Guide and Guide entries get a tick or cross.");
                    b.toggle("  Essence Shop Level, Essence Amounts", () -> c.mstEssence, v -> c.mstEssence = v);
                });
    }

    private static void tick() {
        if (++ticks % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) {
            if (!labels.isEmpty()) labels = List.of();
            communityPage = -1;
            return;
        }
        TeslaMapsConfig c = cfg();
        String t = StringUtil.stripTrim(cs.getTitle().getString());
        int menu = menuOf(t, c);
        if (menu != 7) communityPage = -1;

        List<Label> out = new ArrayList<>();
        List<Slot> slots = cs.getMenu().slots;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            boolean own = slot.container instanceof Inventory;
            String name = StringUtil.stripTrim(stack.getHoverName().getString());

            if (c.mstMinion && stack.is(Items.PLAYER_HEAD)) {
                Matcher m = MINION.matcher(name);
                if (m.matches()) {
                    int lvl = roman(m.group(1));
                    if (lvl > 0) out.add(new Label(i, String.valueOf(lvl), CREAM, Pos.TOP_RIGHT));
                }
            }
            if (c.mstPotion && isPotion(stack) && !name.contains("Healer") && !name.contains("Class Passives")) {
                CustomData d = stack.get(DataComponents.CUSTOM_DATA);
                if (d != null) {
                    int lvl = d.copyTag().getIntOr("potion_level", -1);
                    if (lvl >= 0) out.add(new Label(i, String.valueOf(lvl), CREAM, Pos.BOTTOM_RIGHT));
                }
            }
            if (own || menu < 0) continue;
            Label l = menuLabel(menu, i, stack, name);
            if (l != null) out.add(l);
        }
        labels = out;
    }

    private static int menuOf(String t, TeslaMapsConfig c) {
        if (c.mstLevels) {
            if (T_COLLECTIONS.matcher(t).matches()) return 0;
            if (T_BESTIARY.matcher(t).matches()) return 1;
            if (t.startsWith("Crop Milestones")) return CropMilestoneMenu.handlesMilestones() ? -1 : 2;
            if (T_GARDEN.matcher(t).matches()) return t.startsWith("Crop Upgrades") && CropMilestoneMenu.handlesUpgrades() ? -1 : 3;
            if (t.equals("Manage Chips")) return 4;
        }
        if (c.mstShopGuide) {
            if (t.startsWith("Power Stones Guide")) return 5;
            if (T_GUIDE.matcher(t).matches()) return 6;
            if (t.startsWith("Community Shop")) return 7;
        }
        if (c.mstEssence) {
            if (t.endsWith("Essence Shop")) return 8;
            if (t.startsWith("Your Essence") || t.startsWith("Essence Guide")) return 9;
        }
        return -1;
    }

    private static Label menuLabel(int menu, int i, ItemStack stack, String name) {
        switch (menu) {
            case 0 -> {
                Matcher m = COLLECTION.matcher(name);
                if (!m.matches()) return null;
                int lvl = roman(m.group(1));
                if (lvl < 0) return null;
                return br(i, String.valueOf(lvl), ItemUtil.loreContains(stack, "Progress to ") ? CREAM : GOLD);
            }
            case 1 -> {
                boolean grid = (i >= 10 && i <= 16) || (i >= 19 && i <= 25) || (i >= 28 && i <= 34) || (i >= 37 && i <= 43);
                if (!grid || stack.is(Items.DYE.gray())) return null;
                Matcher m = BESTIARY.matcher(name);
                if (!m.matches()) return null;
                int lvl = roman(m.group(1));
                if (lvl < 0) return null;
                return br(i, String.valueOf(lvl), ItemUtil.loreContains(stack, "Overall Progress: 100%") ? GOLD : CREAM);
            }
            case 2 -> {
                int sp = name.lastIndexOf(' ');
                if (sp < 0) return null;
                String num = name.substring(sp + 1);
                if (num.isEmpty() || !allDigits(num)) return null;
                return br(i, num, ItemUtil.loreContains(stack, "Max tier reached!") ? GOLD : CREAM);
            }
            case 3 -> {
                for (String l : ItemUtil.loreLines(stack)) {
                    Matcher m = TIER.matcher(StringUtil.stripTrim(l));
                    if (m.find()) return br(i, m.group(1), m.group(1).equals(m.group(2)) ? GOLD : CREAM);
                }
                return null;
            }
            case 4 -> {
                if (i < 18 || i > 35 || stack.is(Items.DYE.gray())) return null;
                List<String> lore = ItemUtil.loreLines(stack);
                if (lore.isEmpty()) return null;
                Matcher m = CHIP.matcher(StringUtil.stripTrim(lore.get(0)));
                if (!m.matches()) return null;
                String lvl = m.group(1), max = m.group(2);
                int color = CREAM;
                if (lvl.equals(max)) {
                    color = switch (max) {
                        case "10" -> 0xFF1D8DE8;
                        case "15" -> 0xFFA40BE2;
                        case "20" -> GOLD;
                        default -> CREAM;
                    };
                }
                return br(i, lvl, color);
            }
            case 5 -> {
                for (String l : ItemUtil.loreLines(stack)) {
                    Matcher m = LEARNED.matcher(StringUtil.stripTrim(l));
                    if (m.find()) return mark(i, m.group(1));
                }
                return null;
            }
            case 6 -> {
                if (i < 18 || i > 44) return null;
                Matcher m = GUIDE.matcher(name);
                return m.matches() ? mark(i, m.group(1)) : null;
            }
            case 7 -> {
                if (i >= 10 && i <= 14 && stack.is(Items.STAINED_GLASS_PANE.lime())) {
                    communityPage = i - 10;
                    return null;
                }
                if (communityPage != 1 || i < 18 || i > 44) return null;
                int sp = name.lastIndexOf(' ');
                int lvl = roman(name.substring(sp + 1));
                if (lvl <= 0) return null;
                List<String> lore = ItemUtil.loreLines(stack);
                if (lore.isEmpty()) return null;
                return switch (StringUtil.stripTrim(lore.get(lore.size() - 1))) {
                    case "Maxed out!" -> new Label(i, "Max", LIGHT_ORANGE, Pos.BOTTOM_LEFT);
                    case "Currently upgrading!", "Click to instantly upgrade!" -> new Label(i, "⏰", LIGHT_YELLOW, Pos.BOTTOM_LEFT);
                    case "Click to claim!" -> new Label(i, "✅", CLAIM, Pos.BOTTOM_LEFT);
                    default -> new Label(i, String.valueOf(lvl), LIGHT_PURPLE, Pos.BOTTOM_LEFT);
                };
            }
            case 8 -> {
                Matcher m = ESSENCE_LEVEL.matcher(name);
                if (m.matches()) {
                    int lvl = roman(m.group(1));
                    if (lvl < 0) return null;
                    if (!ItemUtil.loreContains(stack, "UNLOCKED")) lvl--;
                    return br(i, String.valueOf(lvl), CREAM);
                }
                for (String l : ItemUtil.loreLines(stack)) {
                    Matcher em = YOUR_X_ESSENCE.matcher(StringUtil.stripTrim(l));
                    if (em.find()) return br(i, NumberUtil.formatSuffixed(NumberUtil.parseCommaInt(em.group(1))), CREAM);
                }
                return null;
            }
            case 9 -> {
                if (!name.contains("Essence")) return null;
                List<String> lore = ItemUtil.loreLines(stack);
                if (lore.isEmpty()) return null;
                Matcher m = OWN_ESSENCE.matcher(StringUtil.stripTrim(lore.get(0)));
                if (!m.find()) {
                    if (lore.size() < 3) return null;
                    m = YOUR_X_ESSENCE.matcher(StringUtil.stripTrim(lore.get(lore.size() - 3)));
                    if (!m.find()) return null;
                }
                return br(i, NumberUtil.formatSuffixed(NumberUtil.parseCommaInt(m.group(1))), CREAM);
            }
            default -> { return null; }
        }
    }

    private static Label br(int slot, String text, int color) {
        return new Label(slot, text, color, Pos.BOTTOM_RIGHT);
    }

    private static Label mark(int slot, String symbol) {
        return symbol.equals("✖") ? br(slot, "✘", LIGHT_RED) : br(slot, "✔", LIGHT_GREEN);
    }

    private static boolean isPotion(ItemStack stack) {
        return stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
    }

    private static boolean allDigits(String s) {
        for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }

    private static int roman(String s) {
        if (s == null || s.isEmpty()) return -1;
        int result = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = switch (s.charAt(i)) {
                case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50;
                case 'C' -> 100; case 'D' -> 500; case 'M' -> 1000;
                default -> -1;
            };
            if (v < 0) return -1;
            result += v < prev ? -v : v;
            prev = Math.max(prev, v);
        }
        return result;
    }

    public static void render(GuiGraphicsExtractor ctx) {
        if (labels.isEmpty() || feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int left = acc.getX(), top = acc.getY();
        List<Slot> slots = cs.getMenu().slots;
        for (Label l : labels) {
            if (l.slot >= slots.size()) continue;
            Slot slot = slots.get(l.slot);
            int x = switch (l.pos) {
                case BOTTOM_LEFT -> left + slot.x;
                default -> left + slot.x + 17 - mc.font.width(l.text);
            };
            int y = l.pos == Pos.TOP_RIGHT ? top + slot.y : top + slot.y + 9;
            ctx.text(mc.font, l.text, x, y, l.color);
        }
    }
}
