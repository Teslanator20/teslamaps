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

// Highlights menu items whose lore reports a progress line at 100%, only in the menus listed below -
// elsewhere (skill menus, SkyBlock Level menu) the same lore shape means something else
public class ProgressHighlight {

    private static final IdentityHashMap<ItemStack, Boolean> cache = new IdentityHashMap<>();

    private static final String[] MENUS = {
            "Ways to Level Up",
            "Bestiary",
            "Search Result", // /be <mob>
    };

    // reachable from a whitelisted menu, but there the same lore shape means something else
    private static final String[] BLOCKED = {
            "SkyBlock Menu",
            "SkyBlock Leveling",
            "Your Skills",
            "Your Equipment",
    };

    private static final String[] PROGRESS_WORDS = {
            "progress",
            "completed:",
            "completion:",
    };

    public static void render(GuiGraphicsExtractor ctx) {
        if (!TeslaMapsConfig.get().progressHighlight) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) { closed(); return; }
        if (!isWhitelisted(cs)) return;

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

    private static Object lastScreen;
    private static String lastTitle = "";
    private static boolean whitelisted;
    private static long closedAt;

    // a menu swap can blank the screen for a moment, so the parent flag only expires after a while
    private static void closed() {
        lastScreen = null;
        lastTitle = "";
        if (!whitelisted) return;
        if (closedAt == 0) closedAt = System.currentTimeMillis();
        else if (System.currentTimeMillis() - closedAt > 3000) { whitelisted = false; closedAt = 0; }
    }

    // sub menus drop the parent name ("Bestiary ➜ Fishing" -> "Fishing ➜ Backwater Bayou", "Ways to
    // Level Up" -> "Combat"), so any menu opened out of a whitelisted one stays whitelisted
    private static boolean isWhitelisted(AbstractContainerScreen<?> cs) {
        String title = StringUtil.strip(cs.getTitle().getString());
        if (cs == lastScreen && title.equals(lastTitle)) return whitelisted;

        boolean parent = whitelisted;
        closedAt = 0;
        lastScreen = cs;
        lastTitle = title;
        whitelisted = !matches(title, BLOCKED) && (matches(title, MENUS) || parent);
        return whitelisted;
    }

    private static boolean matches(String title, String[] names) {
        for (String menu : names) {
            if (title.contains(menu)) return true;
        }
        return false;
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
