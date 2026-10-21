package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// F7/M7 melody terminal helper: announces the terminal opening and the 33/67% progress to party
// chat (driven by the lime-terracotta progress marker). Ported from Odin's MelodyMessage.
public class MelodyMessage {
    private static final String TITLE = "Click the button on time!";

    private static boolean announced = false;
    private static int lastClayRow = -1;

    private static boolean inMelody() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof com.teslamaps.dungeon.termsim.TermSimGUI) return false;
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return false;
        return TITLE.equals(ChatFormatting.stripFormatting(cs.getTitle().getString()));
    }

    public static void tick() {
        if (!inMelody()) { announced = false; lastClayRow = -1; return; }
        if (!announced) {
            announced = true;
            TeslaMapsConfig c = TeslaMapsConfig.get();
            if (c.melodyMessage && Minecraft.getInstance().getConnection() != null) {
                String text = (c.melodyMessageText == null || c.melodyMessageText.isBlank()) ? "Melody Terminal start!" : c.melodyMessageText;
                com.teslamaps.features.ChatQueue.command("pc " + text);
            }
        }
    }

    // Called from the container-slot packet hook. The lime terracotta marks how far the melody has filled.
    public static void onSlotUpdate(int slot, ItemStack item) {
        if (item == null || item.isEmpty() || item.getItem() != Items.LIME_TERRACOTTA) return;
        if (!TeslaMapsConfig.get().melodyProgress) return;
        if (!inMelody()) return;

        int row = slot / 9;
        if (row == lastClayRow) return;
        lastClayRow = row;
        if (row < 2 || row > 3) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        String prefix = c.melodyProgressText == null || c.melodyProgressText.isBlank() ? "Melody" : c.melodyProgressText.trim();
        String pct = prefix + " " + (c.melodyProgressFormat == 1 ? (row - 1) + "/3" : (row == 2 ? "33%" : "67%"));
        if (Minecraft.getInstance().getConnection() != null) {
            com.teslamaps.features.ChatQueue.command("pc " + pct);
        }
    }

    public static void reset() { announced = false; lastClayRow = -1; }
}
