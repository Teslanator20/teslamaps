package com.teslamaps.features;

// Skyblocker's CatacombsLevelAdder + SkyblockLevelAdder
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DungeonLevelSlotText {

    private record Label(int slot, String text) {}

    private static final int CREAM = 0xFFEAC864;
    private static final Pattern NAME_LEVEL = Pattern.compile(".*?(?:(?: (\\d+)| ([IVXLC]+))(?: ✯)?)?");
    private static final Pattern SB_LEVEL = Pattern.compile("Level:?\\s*\\[?(\\d+)");

    private static List<Label> labels = List.of();
    private static int ticks = 0;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("DungeonLevelSlotText")
                .when(() -> cfg().skillLevelStackSize && cfg().dungeonLevelSlotText)
                .tick(DungeonLevelSlotText::tick);
    }

    private static void tick() {
        if (++ticks % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            if (!labels.isEmpty()) labels = List.of();
            return;
        }
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        int menu = title.startsWith("Dungeoneering") ? 0
                : title.startsWith("Dungeon Classes") ? 1
                : title.startsWith("Ready Up") ? 2
                : title.startsWith("SkyBlock Menu") ? 3 : -1;
        if (menu < 0) {
            if (!labels.isEmpty()) labels = List.of();
            return;
        }
        List<Label> out = new ArrayList<>();
        List<Slot> slots = cs.getMenu().slots;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            String text = switch (menu) {
                case 0 -> (i == 12 || (i >= 29 && i <= 33)) ? nameLevel(stack) : null;
                case 1 -> (i >= 11 && i <= 15) ? bracketLevel(stack) : null;
                case 2 -> (i >= 29 && i <= 33) ? bracketLevel(stack) : null;
                default -> i == 22 ? sbLevel(stack) : null;
            };
            if (text != null) out.add(new Label(i, text));
        }
        labels = out;
    }

    private static String nameLevel(ItemStack stack) {
        Matcher m = NAME_LEVEL.matcher(StringUtil.stripTrim(stack.getHoverName().getString()));
        if (!m.matches()) return null;
        if (m.group(1) != null) return m.group(1);
        if (m.group(2) != null) {
            int v = roman(m.group(2));
            return v > 0 ? String.valueOf(v) : null;
        }
        return "0";
    }

    private static String bracketLevel(ItemStack stack) {
        String name = StringUtil.stripTrim(stack.getHoverName().getString());
        if (!name.startsWith("[Lvl ")) return null;
        int end = name.indexOf(']');
        if (end < 6) return null;
        String level = name.substring(5, end);
        for (int i = 0; i < level.length(); i++) if (!Character.isDigit(level.charAt(i))) return null;
        return level;
    }

    private static String sbLevel(ItemStack stack) {
        List<String> lore = ItemUtil.loreLines(stack);
        if (lore.isEmpty()) return null;
        Matcher m = SB_LEVEL.matcher(StringUtil.stripTrim(lore.get(0)));
        return m.find() ? m.group(1) : null;
    }

    private static int roman(String s) {
        int result = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = switch (s.charAt(i)) {
                case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50; case 'C' -> 100;
                default -> -1;
            };
            if (v < 0) return -1;
            result += v < prev ? -v : v;
            prev = v;
        }
        return result;
    }

    public static void render(GuiGraphicsExtractor ctx) {
        if (labels.isEmpty()) return;
        TeslaMapsConfig c = cfg();
        if (!c.skillLevelStackSize || !c.dungeonLevelSlotText) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int left = acc.getX(), top = acc.getY();
        List<Slot> slots = cs.getMenu().slots;
        for (Label l : labels) {
            if (l.slot >= slots.size()) continue;
            Slot slot = slots.get(l.slot);
            ctx.text(mc.font, l.text, left + slot.x + 1, top + slot.y + 9, CREAM);
        }
    }
}
