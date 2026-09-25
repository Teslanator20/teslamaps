package com.teslamaps.features;

// Ported from SkyHanni's AnvilCombineHelper.kt + Skyblocker's AnvilHelper.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

public final class AnvilHelper {

    private static final int LEFT = 29, RIGHT = 33, RESULT = 13;
    private static final int GREEN = 0x8055FF55, RED = 0xA0FF5555;

    private static Feature feature;
    private static Set<Integer> matches = Set.of();
    private static boolean mismatch;
    private static boolean inAnvil;
    private static int timer;

    private AnvilHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("AnvilHelper").section("Inventory").when(() -> cfg().anvilHelper)
                .tick(AnvilHelper::tick)
                .config("Inventory", b -> {
                    b.label("Anvil Helper");
                    b.toggle("Anvil Helper", () -> cfg().anvilHelper, v -> cfg().anvilHelper = v)
                            .desc("Marks inventory items that combine with the one in the anvil, warns on unequal books.");
                    b.toggle("  Mark Matching Items (green)", () -> cfg().anvilHelperMatches, v -> cfg().anvilHelperMatches = v);
                    b.toggle("  Warn On Unequal Books (red)", () -> cfg().anvilHelperMismatch, v -> cfg().anvilHelperMismatch = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        inAnvil = mc.screen instanceof AbstractContainerScreen<?> cs
                && "Anvil".equals(StringUtil.stripTrim(cs.getTitle().getString()));
        if (!inAnvil) {
            matches = Set.of();
            mismatch = false;
            return;
        }
        if (timer-- > 0) return;
        timer = 3;
        var slots = ((AbstractContainerScreen<?>) mc.screen).getMenu().slots;
        if (slots.size() <= RIGHT) return;
        ItemStack left = slots.get(LEFT).getItem(), right = slots.get(RIGHT).getItem();
        String lk = key(left), rk = key(right);

        Set<Integer> m = new HashSet<>();
        if (cfg().anvilHelperMatches && (lk == null) != (rk == null)) {
            String want = lk != null ? lk : rk;
            for (Slot s : slots) {
                if (!(s.container instanceof Inventory) || s.getItem().isEmpty()) continue;
                if (want.equals(key(s.getItem()))) m.add(s.index);
            }
        }
        matches = m;
        mismatch = cfg().anvilHelperMismatch && lk != null && rk != null
                && lk.startsWith("BOOK:") && rk.startsWith("BOOK:") && !lk.equals(rk);
    }

    private static String key(ItemStack st) {
        if (st == null || st.isEmpty()) return null;
        String id = ItemUtil.skyblockId(st);
        if (id.isEmpty()) return null;
        if (!id.equals("ENCHANTED_BOOK")) return id;
        CustomData d = st.get(DataComponents.CUSTOM_DATA);
        if (d == null) return id;
        CompoundTag ench = d.copyTag().getCompoundOrEmpty("enchantments");
        TreeMap<String, Integer> sorted = new TreeMap<>();
        for (String k : ench.keySet()) sorted.put(k, ench.getIntOr(k, 0));
        return "BOOK:" + sorted;
    }

    /** Behind the item icon, from SlotBackgrounds. */
    public static void drawHighlight(GuiGraphicsExtractor ctx, Slot slot) {
        if (!inAnvil || feature == null || !feature.active()) return;
        if (mismatch && slot.index == RESULT && !(slot.container instanceof Inventory)) {
            ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, RED);
        } else if (matches.contains(slot.index)) {
            ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, GREEN);
        }
    }
}
