package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.utils.StringUtil;
import java.util.IdentityHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

// Highlights any menu item whose lore reports a progress line at 100%, in every container
public class ProgressHighlight {

    private static final IdentityHashMap<ItemStack, Boolean> cache = new IdentityHashMap<>();

    private static final String[] PROGRESS_WORDS = {
            "progress",
            "completed:",
            "completion:",
    };

    public static void render(GuiGraphicsExtractor ctx) {
        if (!TeslaMapsConfig.get().progressHighlight) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;

        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int col = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorProgressDone) & 0x00FFFFFF;

        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            if (!isComplete(item)) continue;

            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            ctx.fill(sx, sy, sx + 16, sy + 16, 0x66000000 | col);
            ctx.fill(sx, sy, sx + 16, sy + 1, 0xFF000000 | col);
            ctx.fill(sx, sy + 15, sx + 16, sy + 16, 0xFF000000 | col);
            ctx.fill(sx, sy, sx + 1, sy + 16, 0xFF000000 | col);
            ctx.fill(sx + 15, sy, sx + 16, sy + 16, 0xFF000000 | col);
        }
    }

    private static boolean isComplete(ItemStack item) {
        Boolean cached = cache.get(item);
        if (cached != null) return cached;
        if (cache.size() > 512) cache.clear();

        boolean done = false;
        ItemLore lore = item.get(DataComponents.LORE);
        if (lore != null) {
            for (Component cmp : lore.lines()) {
                String l = StringUtil.strip(cmp.getString()).trim();
                if (!isProgressLine(l)) continue;
                if (percent(l) >= 100) { done = true; break; }
            }
        }
        cache.put(item, done);
        return done;
    }

    private static boolean isProgressLine(String line) {
        String l = line.toLowerCase();
        for (String word : PROGRESS_WORDS) {
            if (l.contains(word)) return true;
        }
        return false;
    }

    // "Families Completed: 45/45 (100%)", "Overall Progress: 100%", "Progress to 8 Slots: 100%"
    private static double percent(String line) {
        double best = -1;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) != '%') continue;
            int end = i;
            int start = end;
            while (start > 0 && (Character.isDigit(line.charAt(start - 1)) || line.charAt(start - 1) == '.')) start--;
            while (start < end && line.charAt(start) == '.') start++;
            if (start == end) continue;
            try {
                best = Math.max(best, Double.parseDouble(line.substring(start, end)));
            } catch (NumberFormatException ignored) {
            }
        }
        return best;
    }
}
