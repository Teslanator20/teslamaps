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

    private static final IdentityHashMap<ItemStack, Integer> cache = new IdentityHashMap<>();
    private static final IdentityHashMap<ItemStack, String> tierCache = new IdentityHashMap<>();
    private static final String LOCKED = "You haven't unlocked this Family yet!";

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
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) { closed(); return; }
        if (TeslaMapsConfig.get().slayerRewardsHighlight && slayerRewards(cs)) { drawRewards(ctx, cs); return; }
        if (!isWhitelisted(cs)) return;

        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        int done = TeslaMapsConfig.parseColor(c.colorProgressDone) & 0x00FFFFFF;
        int red = TeslaMapsConfig.parseColor(c.colorProgressUnfinished) & 0x00FFFFFF;

        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            if (c.progressHighlightTier) {
                String tier = tier(item);
                if (!tier.isEmpty()) {
                    net.minecraft.client.gui.Font font = mc.font;
                    ctx.text(font, tier, sx + 17 - font.width(tier), sy + 9, 0xFFFFFFFF);
                }
            }
            int state = state(item);
            int col;
            if (state == 1) col = done;
            else if (state == 2 && c.progressHighlightRed) col = red;
            else continue;

            ctx.fill(sx, sy, sx + 16, sy + 16, 0x66000000 | col);
            ctx.fill(sx, sy, sx + 16, sy + 1, 0xFF000000 | col);
            ctx.fill(sx, sy + 15, sx + 16, sy + 16, 0xFF000000 | col);
            ctx.fill(sx, sy, sx + 1, sy + 16, 0xFF000000 | col);
            ctx.fill(sx + 15, sy, sx + 16, sy + 16, 0xFF000000 | col);
        }
    }

    private static Object rewardsScreen;
    private static boolean rewardsMenu;
    private static final IdentityHashMap<ItemStack, Boolean> claimable = new IdentityHashMap<>();

    // Skyblocker's RewardsHighlighter: "... Slayer LVL Rewards"
    private static boolean slayerRewards(AbstractContainerScreen<?> cs) {
        if (cs != rewardsScreen) {
            rewardsScreen = cs;
            rewardsMenu = StringUtil.strip(cs.getTitle().getString()).trim().endsWith("Slayer LVL Rewards");
            claimable.clear();
        }
        return rewardsMenu;
    }

    private static void drawRewards(GuiGraphicsExtractor ctx, AbstractContainerScreen<?> cs) {
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        if (claimable.size() > 256) claimable.clear();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty() || !claimable.computeIfAbsent(item, ProgressHighlight::canClaim)) continue;
            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            ctx.fill(sx, sy, sx + 16, sy + 16, 0x6655FF55);
        }
    }

    private static boolean canClaim(ItemStack item) {
        ItemLore lore = item.get(DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) return false;
        return StringUtil.strip(lore.lines().getLast().getString()).trim().equals("Click to claim rewards!");
    }

    // 0 = no progress line, 1 = done, 2 = unfinished
    private static int state(ItemStack item) {
        Integer cached = cache.get(item);
        if (cached != null) return cached;
        if (cache.size() > 512) cache.clear();

        int state = 0;
        ItemLore lore = item.get(DataComponents.LORE);
        if (lore != null) {
            for (Component cmp : lore.lines()) {
                String l = StringUtil.strip(cmp.getString()).trim();
                if (l.contains(LOCKED)) { state = 2; continue; }
                if (!isProgressLine(l)) continue;
                double p = percent(l);
                if (p >= 100) { state = 1; break; }
                if (p >= 0) state = 2;
            }
        }
        cache.put(item, state);
        return state;
    }

    // bestiary family tier as a number, locked = 0; empty when the item is no family
    private static String tier(ItemStack item) {
        String cached = tierCache.get(item);
        if (cached != null) return cached;
        if (tierCache.size() > 512) tierCache.clear();

        String out = "";
        boolean family = false;
        ItemLore lore = item.get(DataComponents.LORE);
        if (lore != null) {
            for (Component cmp : lore.lines()) {
                String l = StringUtil.strip(cmp.getString());
                if (l.contains(LOCKED)) { out = "0"; break; }
                if (l.contains("Overall Progress:") && l.indexOf('%') > 0) family = true;
            }
        }
        if (family) {
            String name = StringUtil.stripTrim(item.getHoverName().getString());
            int sp = name.lastIndexOf(' ');
            int t = sp < 0 ? 0 : roman(name.substring(sp + 1));
            if (t > 0) out = String.valueOf(t);
        }
        tierCache.put(item, out);
        return out;
    }

    private static int roman(String s) {
        if (s.isEmpty()) return 0;
        int result = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = switch (s.charAt(i)) {
                case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50; case 'C' -> 100;
                default -> -1;
            };
            if (v < 0) return 0;
            result += v < prev ? -v : v;
            prev = v;
        }
        return result;
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
