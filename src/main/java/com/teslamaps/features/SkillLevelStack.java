package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkillXp;
import com.teslamaps.utils.StringUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Writes the skill level where the stack size would go in the skills menu, overflow included.
 * The items themselves are all stacks of one, so vanilla draws no number there at all.
 * Until the xp of a skill is known the level comes straight from the item name.
 */
public class SkillLevelStack {

    private record Label(int slot, String text, int color) {}

    private static List<Label> labels = List.of();
    private static int ticks = 0;

    public static void tick() {
        if (++ticks % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)
                || !"Your Skills".equals(StringUtil.stripTrim(cs.getTitle().getString()))) {
            if (!labels.isEmpty()) labels = List.of();
            return;
        }
        List<Label> out = new ArrayList<>();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            String name = StringUtil.stripTrim(stack.getHoverName().getString());
            String skill = SkillTracker.skillOf(name);
            if (skill == null || name.equals("Dungeoneering")) continue;

            long total = SkillTracker.totalXp(skill);
            if (total > 0) {
                int level = SkillXp.calculate(total, SkillTracker.cap(skill)).level();
                if (level <= 0) continue;
                out.add(new Label(slot.index, String.valueOf(level),
                        level > SkillTracker.cap(skill) ? 0xFF55FFFF : 0xFFFFFFFF));
                continue;
            }

            int space = name.lastIndexOf(' ');
            int level = space < 0 ? 0 : parseLevel(name.substring(space + 1));
            if (level < 0) continue;
            boolean maxed = ItemUtil.loreContains(stack, "Max Skill level reached!");
            out.add(new Label(slot.index, String.valueOf(level), maxed ? 0xFFFFAA00 : 0xFFFFFFFF));
        }
        labels = out;
    }

    private static int parseLevel(String s) {
        if (s.isEmpty()) return -1;
        if (Character.isDigit(s.charAt(0))) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; }
        }
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
        if (labels.isEmpty() || !TeslaMapsConfig.get().skillLevelStackSize || !SlotText.shown(SlotText.SKILL)) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;

        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int left = acc.getX(), top = acc.getY();
        List<Slot> slots = cs.getMenu().slots;
        for (Label l : labels) {
            if (l.slot >= slots.size()) continue;
            Slot slot = slots.get(l.slot);
            int x = left + slot.x + 17 - mc.font.width(l.text);
            int y = top + slot.y + 9;
            ctx.text(mc.font, l.text, x, y, l.color);
        }
    }

    public static void register() {
        com.teslamaps.registry.Feature.of("SkillLevelStack")
                .when(() -> TeslaMapsConfig.get().skillLevelStackSize)
                .tick(SkillLevelStack::tick);
    }
}
