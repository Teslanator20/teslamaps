package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.Set;

public class SlotHighlighter {

    private static final int YELLOW = 0x80FFFF00;
    private static final Set<String> SELLABLE_IDS = Set.of(
            "DEFUSE_KIT", "TRAINING_WEIGHTS", "DUNGEON_LORE_PAPER", "REVIVE_STONE");

    private static final Set<String> JUNK_NAMES = Set.of(
            "Defuse Kit", "Lever", "Torch", "Stone Button", "Tripwire Hook", "Journal Entry",
            "Training Weights", "Mimic Fragment", "Healing 8 Splash Potion", "Healing VIII Splash Potion",
            "Premium Flesh", "Decoy", "Trap", "Inflatable Jerry");
    private static final java.util.regex.Pattern COUNT_SUFFIX = java.util.regex.Pattern.compile(" x\\d+$");

    // quality lookups copy the custom data tag, so each stack is judged once
    private static final java.util.IdentityHashMap<ItemStack, Integer> kinds = new java.util.IdentityHashMap<>();
    private static final int NONE = 0, LOW = 1, MAX = 2, JUNK = 4;

    public static boolean shouldRender() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        boolean quality = c.salvageQuality && c.section("Inventory");
        if (!c.salvageHelper && !c.sellableHighlighter && !c.showSelectedPet && !quality) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return false;
        if (quality) return true;
        String t = strip(cs.getTitle().getString());
        return (c.salvageHelper && t.startsWith("Salvage Items"))
                || (c.sellableHighlighter && isSellMenu(t, c))
                || (c.showSelectedPet && isPetsMenu(t));
    }

    private static boolean isSellMenu(String t, TeslaMapsConfig c) {
        return t.equals("Ophelia") || t.equals("Booster Cookie") || (c.sellableExtended && t.equals("Trades"));
    }

    private static boolean isPetsMenu(String t) { return t.equals("Pets") || t.matches("\\(\\d+/\\d+\\) Pets"); }

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        String t = strip(cs.getTitle().getString());
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (kinds.size() > 2000) kinds.clear();

        if (c.salvageQuality && c.section("Inventory")) renderQuality(ctx, acc, cs, c);

        if (c.salvageHelper && t.startsWith("Salvage Items")) {
            PriceManager.ensureFresh();
            for (Slot slot : cs.getMenu().slots) {
                if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
                if (isSalvageable(slot.getItem())) fillSlot(ctx, acc, slot);
            }
        } else if (c.sellableHighlighter && isSellMenu(t, c)) {
            int col = TeslaMapsConfig.parseColor(c.colorSellable);
            for (Slot slot : cs.getMenu().slots) {
                if (!(slot.container instanceof net.minecraft.world.entity.player.Inventory)) continue;
                ItemStack st = slot.getItem();
                if (isSellable(st) || (c.sellableExtended && (kind(st) & JUNK) != 0)) {
                    int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
                    ctx.fill(sx, sy, sx + 16, sy + 16, col);
                }
            }
        } else if (c.showSelectedPet && isPetsMenu(t)) {
            for (Slot slot : cs.getMenu().slots) {
                if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
                if (isSpawnedPet(slot.getItem())) { fillCyan(ctx, acc, slot); break; }
            }
        }
    }

    private static void renderQuality(GuiGraphicsExtractor ctx, HandledScreenAccessor acc, AbstractContainerScreen<?> cs, TeslaMapsConfig c) {
        int low = TeslaMapsConfig.parseColor(c.colorStatBoostLow), max = TeslaMapsConfig.parseColor(c.colorStatBoostMax);
        for (Slot slot : cs.getMenu().slots) {
            // worn armor is not for sale
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory && slot.getContainerSlot() >= 36) continue;
            int k = kind(slot.getItem()) & 3;
            if (k == NONE) continue;
            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            ctx.fill(sx, sy, sx + 16, sy + 16, k == MAX ? max : low);
        }
    }

    private static int kind(ItemStack st) {
        if (st.isEmpty()) return NONE;
        Integer k = kinds.get(st);
        if (k != null) return k;
        int v = NONE;
        String name = strip(st.getHoverName().getString());
        if (JUNK_NAMES.contains(COUNT_SUFFIX.matcher(name).replaceAll(""))) v |= JUNK;
        CustomData d = st.get(DataComponents.CUSTOM_DATA);
        if (d != null && !name.contains("✪")) {
            var tag = d.copyTag();
            if (!tag.getString("id").orElse("").equals("ICE_SPRAY_WAND")) {
                var boost = tag.getInt("baseStatBoostPercentage");
                if (boost.isPresent()) {
                    v |= boost.get() == 50 ? MAX : LOW;
                    if (boost.get() != 50 && tag.getInt("upgrade_level").isEmpty()) v |= JUNK;
                }
            }
        }
        kinds.put(st, v);
        return v;
    }

    private static boolean isSpawnedPet(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return false;
        for (Component line : lore.lines()) if (strip(line.getString()).equals("Click to despawn!")) return true;
        return false;
    }

    private static void fillCyan(GuiGraphicsExtractor ctx, HandledScreenAccessor acc, Slot slot) {
        int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
        ctx.fill(sx, sy, sx + 16, sy + 16, 0x8000FFFF);
    }

    private static boolean isSalvageable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return false;
        boolean dungeon = false;
        for (Component line : lore.lines()) {
            String l = strip(line.getString());
            if (l.contains("DUNGEON") && !l.contains("DUNGEON ITEM")) { dungeon = true; break; }
        }
        if (!dungeon) return false;
        double price = PriceManager.getPrice(skyblockId(stack));
        return price > 0 && price < 100_000;
    }

    private static boolean isSellable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String id = skyblockId(stack);
        if (id.equals("POTION")) return strip(stack.getHoverName().getString()).contains("Healing VIII");
        return SELLABLE_IDS.contains(id);
    }

    private static void fillSlot(GuiGraphicsExtractor ctx, HandledScreenAccessor acc, Slot slot) {
        int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
        ctx.fill(sx, sy, sx + 16, sy + 16, YELLOW);
    }

    private static String skyblockId(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) return "";
        return d.copyTag().getString("id").orElse("");
    }

    private static String strip(String s) {
        return com.teslamaps.utils.StringUtil.stripTrim(s);
    }
}
