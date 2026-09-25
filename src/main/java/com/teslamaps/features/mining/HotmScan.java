package com.teslamaps.features.mining;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Reads the mineshaft chance perks out of the Heart of the Mountain menu, so the pity
 * chance readout does not need a hand set number.
 * Surveyor gives 0.75% per level, Core of the Mountain gives a flat 10% at tier 9.
 */
public class HotmScan {

    private static final Pattern TITLE = Pattern.compile("Heart of the Mountain.*");
    private static final Pattern LEVEL = Pattern.compile("Level (\\d+)(?:/\\d+)?.*");

    private static Screen lastScreen = null;
    private static boolean done = false;
    private static int ticks = 0;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != lastScreen) {
            lastScreen = mc.screen;
            done = false;
            ticks = 0;
        }
        if (done) return;
        // Hypixel fills the menu async, so one look on open finds an empty GUI
        if (++ticks % 10 != 0) return;

        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        if (!TITLE.matcher(StringUtil.stripTrim(cs.getTitle().getString())).matches()) return;

        int surveyor = -1, core = -1, buff = -1;
        for (Slot slot : cs.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            String name = StringUtil.stripTrim(stack.getHoverName().getString());
            if (name.equals("Surveyor")) surveyor = level(stack);
            else if (name.equals("Core of the Mountain") || name.equals("Peak of the Mountain")) core = level(stack);
            else if (name.equals("Powder Buff")) buff = level(stack);
        }
        if (buff >= 0 && buff != TeslaMapsConfig.get().hotmPowderBuffLevel) {
            TeslaMapsConfig.get().hotmPowderBuffLevel = buff;
            TeslaMapsConfig.save();
        }
        if (surveyor < 0 && core < 0) return;
        done = true;

        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (surveyor >= 0) c.mineshaftSurveyorLevel = surveyor;
        if (core >= 0) c.mineshaftCoreLevel = core;

        float bonus = c.mineshaftSurveyorLevel * 0.75f + (c.mineshaftCoreLevel >= 9 ? 10f : 0f);
        if (bonus == c.mineshaftChanceBonus) {
            TeslaMapsConfig.save();
            return;
        }
        c.mineshaftChanceBonus = bonus;
        TeslaMapsConfig.save();

        if (mc.player != null && c.mineshaftPityDisplay) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Mineshaft chance bonus §e"
                    + String.format("%.2f", bonus) + "%§7 (Surveyor §e" + c.mineshaftSurveyorLevel
                    + "§7, Core §e" + c.mineshaftCoreLevel + "§7)"));
        }
    }

    /** Perk level off the first lore line, 0 while the perk is still locked. */
    private static int level(ItemStack stack) {
        for (String line : ItemUtil.loreLines(stack)) {
            Matcher m = LEVEL.matcher(StringUtil.stripTrim(line));
            if (m.matches()) return Integer.parseInt(m.group(1));
        }
        return 0;
    }
}
