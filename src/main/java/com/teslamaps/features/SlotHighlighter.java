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
    private static final java.util.IdentityHashMap<ItemStack, Boolean> salvage = new java.util.IdentityHashMap<>();
    private static long salvageAt;
    private static final int NONE = 0, LOW = 1, MAX = 2, JUNK = 4;

    public static boolean shouldRender() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        boolean quality = c.salvageQuality && c.section("Inventory");
        if (!c.salvageHelper && !c.salvageMuseumGuard && !c.sellableHighlighter &&!c.showSelectedPet && !c.highlightDisabledPotions && !quality) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return false;
        if (quality) return true;
        String t = strip(cs.getTitle().getString());
        return ((c.salvageHelper || c.salvageMuseumGuard) && t.startsWith("Salvage Items"))
                || (c.sellableHighlighter && isSellMenu(t, c))
                || (c.showSelectedPet && isPetsMenu(t))
                || (c.highlightDisabledPotions && t.startsWith("Toggle Potion Effects"));
    }

    private static final java.util.regex.Pattern PETS_PAGE = java.util.regex.Pattern.compile("\\(\\d+/\\d+\\) Pets");

    private static boolean isSellMenu(String t, TeslaMapsConfig c) {
        return t.equals("Ophelia") || t.equals("Booster Cookie") || (c.sellableExtended && t.equals("Trades"));
    }

    private static boolean isPetsMenu(String t) { return t.equals("Pets") || PETS_PAGE.matcher(t).matches(); }

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        String t = strip(cs.getTitle().getString());
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (kinds.size() > 2000) kinds.clear();

        if (c.salvageQuality && c.section("Inventory")) renderQuality(ctx, acc, cs, c);

        if ((c.salvageHelper || c.salvageMuseumGuard) && t.startsWith("Salvage Items")) {
            if (c.salvageHelper) PriceManager.ensureFresh();
            for (Slot slot : cs.getMenu().slots) {
                if (c.salvageMuseumGuard && MuseumDonations.notDonated(slot.getItem())) {
                    int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
                    ctx.fill(sx, sy, sx + 16, sy + 16, 0x80FF2020);
                    continue;
                }
                if (!c.salvageHelper || slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
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
        } else if (c.highlightDisabledPotions && t.startsWith("Toggle Potion Effects")) {
            for (Slot slot : cs.getMenu().slots) {
                if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
                if (slot.getItem().isEmpty()) continue;
                for (String line : com.teslamaps.utils.ItemUtil.loreLines(slot.getItem())) {
                    if (line.contains("Currently: DISABLED")) {
                        int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
                        ctx.fill(sx, sy, sx + 16, sy + 16, 0x80FF3333);
                        break;
                    }
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

    private static long lastSalvageWarn;

    /** Salvage menu: an inventory item that can't be salvaged, or an undonated museum item. Ctrl overrides. */
    public static boolean blockSalvageClick(AbstractContainerScreen<?> screen, Slot slot) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        boolean others = c.salvageHelper && c.salvageBlockOthers;
        boolean museum = c.salvageMuseumGuard && c.salvageMuseumBlock;
        if (!others && !museum) return false;
        if (slot == null || !(slot.container instanceof net.minecraft.world.entity.player.Inventory)) return false;
        ItemStack st = slot.getItem();
        if (st.isEmpty() || !strip(screen.getTitle().getString()).startsWith("Salvage Items")) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasControlDown()) return false;
        String why = null;
        if (museum && MuseumDonations.notDonated(st)) why = "is not donated to the museum yet";
        else if (others && !canSalvage(st)) why = "can't be salvaged";
        if (why == null) return false;
        long now = System.currentTimeMillis();
        if (now - lastSalvageWarn > 1500 && mc.player != null) {
            lastSalvageWarn = now;
            mc.player.playSound(net.minecraft.sounds.SoundEvents.ANVIL_LAND, 0.6f, 1.0f);
            mc.player.sendSystemMessage(Component.literal("§c[TeslaMaps] §e" + strip(st.getHoverName().getString())
                    + " §c" + why + ". §7Hold ctrl to override."));
        }
        return true;
    }

    // Skytils' stopClickingNonSalvageable: salvageable = has a base stat boost
    private static boolean canSalvage(ItemStack st) {
        CustomData d = st.get(DataComponents.CUSTOM_DATA);
        if (d == null) return true;
        var tag = d.copyTag();
        if (tag.getString("id").isEmpty()) return true;
        if (tag.contains("baseStatBoostPercentage") || tag.contains("attributes")) return true;
        String name = strip(st.getHoverName().getString());
        if (name.contains("Salvage") || name.contains("Essence")) return true;
        for (String l : com.teslamaps.utils.ItemUtil.loreLines(st)) {
            if (l.contains("DUNGEON") && !l.contains("DUNGEON ITEM")) return true;
        }
        return false;
    }

    private static boolean isSpawnedPet(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (String line : com.teslamaps.utils.ItemUtil.loreLines(stack)) if (line.trim().equals("Click to despawn!")) return true;
        return false;
    }

    private static void fillCyan(GuiGraphicsExtractor ctx, HandledScreenAccessor acc, Slot slot) {
        int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
        ctx.fill(sx, sy, sx + 16, sy + 16, 0x8000FFFF);
    }

    private static boolean isSalvageable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        long now = System.currentTimeMillis();
        if (now - salvageAt > 1000 || salvage.size() > 2000) { salvage.clear(); salvageAt = now; }
        Boolean cached = salvage.get(stack);
        if (cached != null) return cached;
        boolean v = computeSalvageable(stack);
        salvage.put(stack, v);
        return v;
    }

    private static boolean computeSalvageable(ItemStack stack) {
        boolean dungeon = false;
        for (String l : com.teslamaps.utils.ItemUtil.loreLines(stack)) {
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
        return com.teslamaps.utils.ItemUtil.skyblockId(stack);
    }

    private static String strip(String s) {
        return s == null ? "" : com.teslamaps.utils.StringUtil.stripTrim(s);
    }
}
