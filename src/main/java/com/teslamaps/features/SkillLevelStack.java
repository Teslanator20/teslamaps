package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.utils.SkillXp;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Writes the skill level where the stack size would go in the skills menu, overflow included.
 * The items themselves are all stacks of one, so vanilla draws no number there at all.
 */
public class SkillLevelStack {

    public static void render(GuiGraphicsExtractor ctx) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.skillLevelStackSize) return;

        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        if (!"Your Skills".equals(StringUtil.stripTrim(cs.getTitle().getString()))) return;

        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int left = acc.getX(), top = acc.getY();

        for (Slot slot : cs.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            String skill = SkillTracker.skillOf(StringUtil.stripTrim(stack.getHoverName().getString()));
            if (skill == null) continue;

            long total = SkillTracker.totalXp(skill);
            if (total <= 0) continue;   // never opened the menu for it, so no number is better than a wrong one
            int level = SkillXp.calculate(total, SkillTracker.cap(skill)).level();
            if (level <= 0) continue;

            String text = String.valueOf(level);
            // bottom right of the slot, the same corner and drop shadow vanilla uses for counts
            int x = left + slot.x + 17 - mc.font.width(text);
            int y = top + slot.y + 9;
            ctx.pose().pushMatrix();
            ctx.pose().translate(0f, 0f);
            ctx.text(mc.font, text, x, y, level > SkillTracker.cap(skill) ? 0xFF55FFFF : 0xFFFFFFFF);
            ctx.pose().popMatrix();
        }
    }
}
