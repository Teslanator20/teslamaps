// Ported from SkyHanni's HotxHandler.kt (LGPL-2.1), see NOTICE.md
package com.teslamaps.features.foraging;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/** Heart of the Forest: perk highlighting, level/token stack sizes and the rotating perk readout. */
public class HotF {

    private static final String INVENTORY = "Heart of the Forest";
    private static final String RESET_ITEM = "Reset Heart of the Forest";
    private static final Pattern NOT_UNLOCKED = Pattern.compile("Requires.*|.*Forest!|Click to unlock!");
    private static final Pattern LEVEL = Pattern.compile("Level (?<level>\\d+)(?:/(?<max>\\d+))?.*");
    private static final Pattern TOKENS = Pattern.compile("Tokens of the Forest: (?<token>\\d+)");
    private static final String CURRENT_EFFECT = "Your Current Effect";

    public static boolean inInventory() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen instanceof AbstractContainerScreen<?> cs
                && Foraging.strip(cs.getTitle().getString()).contains(INVENTORY);
    }

    public static boolean shouldRender() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.section("Foraging")
                && (c.hotfHighlight || c.hotfLevelStack || c.hotfTokenStack || c.hotfPerkDisplay)
                && inInventory();
    }

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;

        int enabledColor = TeslaMapsConfig.parseColor(c.colorHotfEnabled) & 0x00FFFFFF;
        int disabledColor = TeslaMapsConfig.parseColor(c.colorHotfDisabled) & 0x00FFFFFF;
        int lockedColor = TeslaMapsConfig.parseColor(c.colorHotfLocked) & 0x00FFFFFF;

        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;

            String name = Foraging.strip(item.getHoverName().getString()).trim();
            List<String> lore = lore(item);
            if (lore.isEmpty()) continue;

            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;

            if (name.equals(INVENTORY)) {
                if (c.hotfTokenStack) {
                    int tokens = readTokens(lore);
                    if (tokens > 0) stackTip(ctx, mc, sx, sy, "§b" + tokens);
                }
                continue;
            }
            if (name.equals(RESET_ITEM)) continue;

            readRotatingPerk(c, name, lore);

            boolean locked = NOT_UNLOCKED.matcher(lore.get(lore.size() - 1).trim()).matches();
            boolean enabled = !locked && isEnabled(lore);

            if (c.hotfHighlight) {
                highlight(ctx, sx, sy, locked ? lockedColor : enabled ? enabledColor : disabledColor);
            }
            if (c.hotfLevelStack && enabled) {
                int[] level = readLevel(lore);
                if (level != null && level[0] != 0 && level[0] != level[1]) {
                    stackTip(ctx, mc, sx, sy, "§e" + level[0]);
                }
            }
        }
    }

    private static void highlight(GuiGraphicsExtractor ctx, int sx, int sy, int col) {
        ctx.fill(sx, sy, sx + 16, sy + 16, 0x66000000 | col);
        ctx.fill(sx, sy, sx + 16, sy + 1, 0xFF000000 | col);
        ctx.fill(sx, sy + 15, sx + 16, sy + 16, 0xFF000000 | col);
        ctx.fill(sx, sy, sx + 1, sy + 16, 0xFF000000 | col);
        ctx.fill(sx + 15, sy, sx + 16, sy + 16, 0xFF000000 | col);
    }

    private static void stackTip(GuiGraphicsExtractor ctx, Minecraft mc, int sx, int sy, String text) {
        int w = mc.font.width(text);
        ctx.text(mc.font, text, sx + 17 - w, sy + 9, 0xFFFFFFFF);
    }

    private static boolean isEnabled(List<String> lore) {
        for (String line : lore) {
            String l = line.trim();
            if (l.equals("ENABLED") || l.equals("SELECTED")) return true;
        }
        return false;
    }

    /** @return {level, maxLevel} or null. */
    private static int[] readLevel(List<String> lore) {
        Matcher m = LEVEL.matcher(lore.get(0).trim());
        if (!m.matches()) return null;
        int level = Integer.parseInt(m.group("level"));
        int max = m.group("max") != null ? Integer.parseInt(m.group("max")) : -1;
        return new int[] { level, max };
    }

    private static int readTokens(List<String> lore) {
        for (String line : lore) {
            Matcher m = TOKENS.matcher(line.trim());
            if (m.matches()) return Integer.parseInt(m.group("token"));
        }
        return 0;
    }

    // the perk items list the rolled effect right under "Your Current Effect"
    private static void readRotatingPerk(TeslaMapsConfig c, String name, List<String> lore) {
        boolean lottery = name.equals("Lottery");
        if (!lottery && !name.equals("Beekeeper")) return;

        int index = -1;
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).trim().equals(CURRENT_EFFECT)) { index = i; break; }
        }
        if (index < 0) return;

        StringBuilder wrapped = new StringBuilder();
        for (int i = index + 1; i < lore.size(); i++) {
            String line = lore.get(i).trim();
            if (line.isEmpty()) break;
            if (!wrapped.isEmpty()) wrapped.append(' ');
            wrapped.append(line);
        }
        Matcher m = HotfPerks.ROTATING_LINE.matcher(wrapped.toString());
        if (!m.matches()) return;
        String effect = m.group("perk");

        String display = lottery ? HotfPerks.matchLottery(effect) : HotfPerks.matchBeekeeper(effect);
        if (display == null) return;
        if (lottery) c.hotfLotteryPerk = display;
        else c.hotfBeekeeperPerk = display;
    }

    private static List<String> lore(ItemStack item) {
        ItemLore lore = item.get(DataComponents.LORE);
        if (lore == null) return List.of();
        List<String> out = new ArrayList<>();
        for (Component line : lore.lines()) out.add(Foraging.strip(line.getString()));
        return out;
    }
}
